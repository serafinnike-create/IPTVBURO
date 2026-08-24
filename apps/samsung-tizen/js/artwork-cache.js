/*
  As capas guardadas no pendrive ou HD ligado à USB da TV.

  Pedido do usuário: "e possivel adicionar sistema de chace igual no app do
  windows, porem sistema que user ativa nas configuracaoes onde user e obrigado
  deixar pendrive ou hd prlugado na usb da tv".

  Por que num USB e não na TV: o armazenamento interno de um aplicativo Tizen é
  pequeno e compartilhado, e um catálogo de dezenas de milhares de títulos tem
  capas demais para caber ali. O USB é o único lugar com espaço, e é removível —
  então tudo aqui trata a ausência dele como o caso normal, não como erro.

  Sem USB o comportamento é exatamente o de antes: a capa vem da URL gravada no
  item e do cache de memória. Este módulo só acrescenta uma cópia local, que
  abre mais rápido e sobrevive a uma rede lenta.

  O que é gravado é sempre uma imagem já aprovada pela peneira de credencial de
  `BuroDomain.isStorableReminderArtwork` — a mesma que decide se a URL podia ir
  para o banco. Um endereço que não pode ser persistido também não é baixado.
*/
var BuroArtworkCache = (function () {
    'use strict';

    /* A mesma pasta dos downloads: quem abre o pendrive no computador encontra
       tudo do aplicativo num lugar só. */
    var FOLDER_NAME = 'IPTV BURO';
    var SUBFOLDER = 'capas';

    /*
      Quanto o cache pode ocupar, em megabytes.

      O padrão é modesto de propósito: o pendrive é do usuário e pode ter outras
      coisas. Ele escolhe nas Configurações, e o teto real é o menor entre a
      escolha e o espaço livre — encher um pendrive alheio seria pior do que
      guardar menos capas.
    */
    var DEFAULT_LIMIT_MB = 512;
    var MIN_LIMIT_MB = 64;
    var MAX_LIMIT_MB = 8192;

    /* Uma capa típica tem algumas dezenas de kB; isto é o teto por arquivo, para
       uma resposta defeituosa não consumir o cache inteiro sozinha. */
    var MAX_FILE_BYTES = 2 * 1024 * 1024;

    /*
      Quantas cópias podem estar em voo.

      Uma TV modesta trava se dezenas de downloads começam juntos, e a fila
      existe para o cache nunca competir com a reprodução: capa é conforto, o
      vídeo é a razão do aparelho.
    */
    var MAX_PARALLEL = 2;

    var state = {
        directory: null,
        /* Nome de arquivo por id de título, do que já está no disco. */
        stored: {},
        bytes: 0,
        limitMb: DEFAULT_LIMIT_MB,
        enabled: false,
        active: 0,
        queue: [],
        pending: {},
        paused: false,
        total: 0,
        done: 0,
        failed: 0,
        /* Telemetria efemera: mede somente bytes, nunca conserva a URL. */
        bytesPerSecond: null,
        rateWindowAt: 0,
        rateWindowBytes: 0,
        generation: 0,
        listeners: []
    };

    function resetTransferMetrics() {
        state.bytesPerSecond = null;
        state.rateWindowAt = Date.now();
        state.rateWindowBytes = 0;
    }

    /*
      A plataforma informa o total recebido por arquivo. Somamos apenas o delta
      de cada capa e publicamos uma media curta no maximo duas vezes por segundo:
      redes de TV oscilam, mas redesenhar a tela em cada pacote custaria mais do
      que a informacao vale.
    */
    function observeTransferProgress(job, received) {
        var current;
        var previous;
        var elapsed;
        var now;
        if (!job || job.generation !== state.generation) { return; }
        current = Math.max(0, Number(received) || 0);
        previous = Math.max(0, Number(job.receivedBytes) || 0);
        job.receivedBytes = current;
        state.rateWindowBytes += Math.max(0, current - previous);
        now = Date.now();
        elapsed = now - state.rateWindowAt;
        if (elapsed < 500) { return; }
        state.bytesPerSecond = state.rateWindowBytes > 0 ?
            Math.round(state.rateWindowBytes * 1000 / elapsed) : null;
        state.rateWindowAt = now;
        state.rateWindowBytes = 0;
        notify();
    }

    function notify() {
        state.listeners.slice().forEach(function (listener) {
            try { listener(status()); } catch (ignoredListener) { /* observador opcional */ }
        });
    }

    function watch(listener) {
        if (typeof listener !== 'function') { return function () {}; }
        state.listeners.push(listener);
        return function () {
            var index = state.listeners.indexOf(listener);
            if (index >= 0) { state.listeners.splice(index, 1); }
        };
    }

    function clean(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /* O limite pedido, dentro do que faz sentido. Um valor absurdo vindo de
       preferências corrompidas não deve virar um cache sem teto. */
    function safeLimitMb(value) {
        var number = Math.floor(Number(value));
        if (!isFinite(number)) { return DEFAULT_LIMIT_MB; }
        return Math.max(MIN_LIMIT_MB, Math.min(MAX_LIMIT_MB, number));
    }

    function limitBytes() {
        return safeLimitMb(state.limitMb) * 1024 * 1024;
    }

    /*
      O nome do arquivo de um título.

      Vem do id, que já é um hash estável, e não do nome do filme: um título com
      acento, barra ou dois-pontos daria um nome inválido em alguns sistemas de
      arquivos, e o pendrive é lido também noutros aparelhos.
    */
    function fileNameFor(itemId) {
        var id = clean(itemId);
        if (!/^[A-Za-z0-9._-]{1,120}$/.test(id)) { return null; }
        return id + '.img';
    }

    /* Disponível quer dizer: existe API, existe volume montado, e o usuário
       ligou a função. As três coisas, sempre verificadas em tempo de execução —
       o pendrive pode sair a qualquer momento. */
    function enabled() {
        return Boolean(state.enabled && state.directory &&
            typeof BuroUsb !== 'undefined' && BuroUsb.hasStorage());
    }

    /*
      Prepara a pasta no volume montado.

      Falha em silêncio: sem USB o aplicativo funciona como sempre funcionou, e
      um erro na tela sobre um pendrive que a pessoa nem quis usar seria ruído.
    */
    function attach(limitMb, done) {
        var storages;
        state.limitMb = safeLimitMb(limitMb);
        state.enabled = true;
        state.paused = false;
        if (typeof BuroUsb === 'undefined' || !BuroUsb.hasStorage()) {
            state.directory = null;
            if (done) { done(false); }
            return;
        }
        storages = BuroUsb.mountedStorages();
        if (!storages.length) {
            state.directory = null;
            if (done) { done(false); }
            return;
        }
        BuroUsb.resolveTarget(storages[0].label, FOLDER_NAME, function (root) {
            var directory = null;
            try { directory = root.resolve(SUBFOLDER); }
            catch (notThere) { directory = null; }
            if (!directory) {
                try { directory = root.createDirectory(SUBFOLDER); }
                catch (unwritable) { directory = null; }
            }
            state.directory = directory;
            if (directory) { indexExisting(directory); }
            notify();
            pump();
            if (done) { done(Boolean(directory)); }
        }, function () {
            state.directory = null;
            notify();
            if (done) { done(false); }
        });
    }

    /* Desliga sem apagar nada: quem desativa hoje pode reativar amanhã e
       encontrar as capas onde deixou. Apagar é uma ação separada e explícita. */
    function detach() {
        state.enabled = false;
        state.generation += 1;
        state.active = 0;
        state.directory = null;
        state.queue = [];
        state.pending = {};
        state.paused = false;
        state.total = 0;
        state.done = 0;
        state.failed = 0;
        resetTransferMetrics();
        notify();
    }

    /*
      Lê o que já está no pendrive.

      Sem isto o aplicativo baixaria de novo o que ja tem a cada abertura, que e
      o oposto do proposito. Também é aqui que o tamanho ocupado é medido.
    */
    function indexExisting(directory) {
        state.stored = {};
        state.bytes = 0;
        try {
            directory.listFiles(function (files) {
                (files || []).forEach(function (file) {
                    var name = clean(file && file.name);
                    var id = name.replace(/\.img$/, '');
                    if (name === id || file.isDirectory) { return; }
                    state.stored[id] = name;
                    state.bytes += Number(file.fileSize) || 0;
                });
                notify();
            }, function () {});
        } catch (ignoredList) { /* Um volume ilegível é tratado como vazio. */ }
    }

    /* A URI local de uma capa já guardada, ou nulo. É isto que o `<img>` recebe
       em vez de ir à rede. */
    function localUrl(itemId) {
        var name = state.stored[clean(itemId)];
        var file;
        if (!enabled() || !name) { return null; }
        try {
            file = state.directory.resolve(name);
            return file && file.toURI ? file.toURI() : null;
        } catch (ignoredResolve) {
            /* O arquivo sumiu por baixo: esquece, para nao insistir nele. */
            delete state.stored[clean(itemId)];
            return null;
        }
    }

    /*
      Pede uma cópia local de uma capa.

      Não faz nada quando já existe, quando o cache está cheio, quando o
      endereço não passa na peneira de credencial, ou quando a função está
      desligada. Todos esses casos são normais, então nenhum vira erro.
    */
    function remember(itemId, url) {
        var id = clean(itemId);
        var name = fileNameFor(id);
        if (!enabled() || !name || state.stored[id] || state.pending[id]) { return; }
        if (!clean(url) || typeof BuroDomain === 'undefined' ||
                !BuroDomain.isStorableReminderArtwork(url)) { return; }
        if (state.bytes >= limitBytes()) { return; }
        if (!state.active && !state.queue.length) { resetTransferMetrics(); }
        state.pending[id] = true;
        state.total += 1;
        state.queue.push({ id: id, name: name, url: String(url), generation: state.generation,
            receivedBytes: 0 });
        notify();
        pump();
    }

    /*
      Repassa tudo o que a sessão já conhece, como o preenchimento explícito do
      Android. Itens que já estão no pendrive contam como concluídos e não são
      baixados outra vez; URLs privadas nem entram na fila.
    */
    function fill(entries) {
        var accepted = [];
        var known = {};
        if (!enabled()) { return false; }
        /* Uma nova varredura durante a fila atual apenas continua o trabalho.
           Misturar duas listas faria o total deixar de representar o progresso
           que a pessoa ve na tela. */
        if (state.active || state.queue.length) {
            state.paused = false;
            notify();
            pump();
            return true;
        }
        (entries || []).forEach(function (entry) {
            var id = clean(entry && entry.id);
            var url = clean(entry && entry.url);
            var name = fileNameFor(id);
            if (!name || known[id] || !url || typeof BuroDomain === 'undefined' ||
                    !BuroDomain.isStorableReminderArtwork(url)) { return; }
            known[id] = true;
            accepted.push({ id: id, name: name, url: url, generation: state.generation,
                receivedBytes: 0 });
        });
        state.total = accepted.length;
        state.done = 0;
        state.failed = 0;
        state.paused = false;
        resetTransferMetrics();
        accepted.forEach(function (job) {
            if (state.stored[job.id]) { state.done += 1; return; }
            if (state.pending[job.id]) { return; }
            state.pending[job.id] = true;
            state.queue.push(job);
        });
        notify();
        pump();
        return true;
    }

    function pump() {
        var next;
        if (state.paused) { return; }
        while (state.active < MAX_PARALLEL && state.queue.length) {
            next = state.queue.shift();
            if (!state.pending[next.id]) { continue; }
            state.active += 1;
            fetchOne(next);
        }
    }

    function finish(job, failed) {
        if (job.generation !== state.generation) { return; }
        state.active = Math.max(0, state.active - 1);
        delete state.pending[job.id];
        state.done += 1;
        if (failed) { state.failed += 1; }
        if (!state.active && !state.queue.length) {
            state.paused = false;
            resetTransferMetrics();
        }
        notify();
        pump();
    }

    function fetchOne(job) {
        var request;
        if (!enabled()) { finish(job, true); return; }
        /* O tamanho real só aparece depois do download. Antes de iniciar o
           próximo, respeita o teto que os anteriores já consumiram. */
        if (state.bytes >= limitBytes()) { finish(job, false); return; }
        try {
            request = new tizen.DownloadRequest(job.url, state.directory.fullPath, job.name);
            tizen.download.start(request, {
                onprogress: function (identifier, received) {
                    observeTransferProgress(job, received);
                },
                oncompleted: function (identifier, fullPath) {
                    var file = null;
                    var size = 0;
                    try {
                        file = state.directory.resolve(job.name);
                        size = Number(file && file.fileSize) || 0;
                    } catch (ignoredSize) { size = 0; }
                    /* Uma resposta grande demais nao fica: seria uma pagina de
                       erro ou um video servido no lugar da capa. */
                    if (size > MAX_FILE_BYTES) { removeFile(job.name); finish(job, true); return; }
                    state.stored[job.id] = job.name;
                    state.bytes += size;
                    finish(job, false);
                    fullPath = null;
                },
                onfailed: function () { finish(job, true); },
                oncanceled: function () { finish(job, true); }
            });
        } catch (ignoredStart) { finish(job, true); }
    }

    function pause() {
        if (!enabled()) { return false; }
        state.paused = true;
        resetTransferMetrics();
        notify();
        return true;
    }

    function resume() {
        if (!enabled()) { return false; }
        state.paused = false;
        resetTransferMetrics();
        notify();
        pump();
        return true;
    }

    function removeFile(name) {
        try { state.directory.deleteFile(state.directory.fullPath + '/' + name, function () {}, function () {}); }
        catch (ignoredDelete) { /* Nada a fazer: o arquivo fica e a contagem se
                                   corrige na proxima leitura da pasta. */ }
    }

    /*
      Apaga tudo o que o cache guardou.

      Ação explícita das Configurações, porque o pendrive é do usuário: só ele
      decide quando liberar o espaço.
    */
    function clear(done) {
        var names = Object.keys(state.stored);
        if (!state.directory) {
            state.generation += 1;
            state.active = 0;
            state.stored = {}; state.bytes = 0; state.queue = []; state.pending = {};
            state.paused = false; state.total = 0; state.done = 0; state.failed = 0;
            resetTransferMetrics();
            notify();
            if (done) { done(); }
            return;
        }
        names.forEach(function (id) { removeFile(state.stored[id]); });
        state.stored = {};
        state.bytes = 0;
        state.generation += 1;
        state.active = 0;
        state.queue = [];
        state.pending = {};
        state.paused = false;
        state.total = 0;
        state.done = 0;
        state.failed = 0;
        resetTransferMetrics();
        notify();
        if (done) { done(); }
    }

    /* O que a tela de Configurações mostra. */
    function status() {
        var pending = state.queue.length + state.active;
        return {
            enabled: state.enabled,
            ready: enabled(),
            hasStorage: typeof BuroUsb !== 'undefined' && BuroUsb.hasStorage(),
            count: Object.keys(state.stored).length,
            bytes: state.bytes,
            limitMb: safeLimitMb(state.limitMb),
            pending: pending,
            active: state.active,
            total: state.total,
            done: state.done,
            failed: state.failed,
            paused: state.paused,
            running: pending > 0 && !state.paused,
            bytesPerSecond: pending > 0 && !state.paused && state.bytesPerSecond > 0 ?
                state.bytesPerSecond : null,
            complete: state.total > 0 && state.done >= state.total && pending === 0,
            percent: state.total > 0 ? Math.min(100, Math.floor(state.done * 100 / state.total)) : null
        };
    }

    return {
        attach: attach,
        detach: detach,
        remember: remember,
        fill: fill,
        pause: pause,
        resume: resume,
        watch: watch,
        localUrl: localUrl,
        clear: clear,
        status: status,
        safeLimitMb: safeLimitMb,
        limits: function () {
            return { minimum: MIN_LIMIT_MB, maximum: MAX_LIMIT_MB, fallback: DEFAULT_LIMIT_MB };
        },
        /* Só para os testes: o estado interno não tem sintoma observável de
           fora, e um cache que se enche sem limite só aparece quando o pendrive
           lota. */
        _stateForTesting: function () { return state; }
    };
}());
