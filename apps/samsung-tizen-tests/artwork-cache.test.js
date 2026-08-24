/*
  As capas guardadas no pendrive ligado à USB da TV.

  Pedido do usuário: "e possivel adicionar sistema de chace igual no app do
  windows, porem sistema que user ativa nas configuracaoes onde user e obrigado
  deixar pendrive ou hd prlugado na usb da tv".

  O que este teste guarda acima de tudo é o caso de quem **não** tem pendrive,
  que é a maioria: nada pode piorar para essa pessoa. Depois, os limites — um
  cache sem teto enche o pendrive alheio, e um endereço com credencial não pode
  ser baixado só porque é uma imagem.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

/* Um pendrive de mentira, que registra o que foi baixado e apagado. */
function makeWindow(options) {
    var settings = options || {};
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var files = {};
    window.__downloads = [];
    window.__deleted = [];
    window.__downloadCallbacks = [];
    window.__files = files;
    window.__now = 1000;
    window.Date.now = function () { return window.__now; };
    window.tizen = {
        DownloadRequest: function (url, destination, name) {
            this.url = url; this.destination = destination; this.name = name;
        },
        download: {
            start: function (request, callbacks) {
                window.__downloads.push({ url: request.url, name: request.name });
                if (settings.deferred) {
                    window.__downloadCallbacks.push({ request: request, callbacks: callbacks });
                    return window.__downloads.length;
                }
                files[request.name] = { name: request.name, fileSize: settings.fileSize || 40000 };
                if (callbacks && callbacks.oncompleted) {
                    callbacks.oncompleted(1, request.destination + '/' + request.name);
                }
                return 1;
            }
        }
    };
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'));
    /* O modulo de USB, no estado que o teste pede. */
    window.BuroUsb = {
        hasStorage: function () { return Boolean(settings.usb); },
        mountedStorages: function () { return settings.usb ? [{ label: 'removable_a' }] : []; },
        resolveTarget: function (label, folder, success, failure) {
            if (!settings.usb) { failure({ code: 'STORAGE_UNAVAILABLE' }); return; }
            success({
                resolve: function (name) {
                    if (name === 'capas') { return settings.folderExists === false ? null : directory(); }
                    if (files[name]) { return { name: name, fileSize: files[name].fileSize,
                        toURI: function () { return 'file:///usb/capas/' + name; } }; }
                    throw new Error('not found');
                },
                createDirectory: function () { return directory(); }
            });
        }
    };
    function directory() {
        return {
            fullPath: '/usb/IPTV BURO/capas',
            resolve: function (name) {
                if (!files[name]) { throw new Error('not found'); }
                return { name: name, fileSize: files[name].fileSize,
                    toURI: function () { return 'file:///usb/capas/' + name; } };
            },
            listFiles: function (success) {
                success(Object.keys(files).map(function (name) {
                    return { name: name, fileSize: files[name].fileSize, isDirectory: false };
                }));
            },
            deleteFile: function (fullPath, success) {
                var name = String(fullPath).split('/').pop();
                window.__deleted.push(name);
                delete files[name];
                if (success) { success(); }
            }
        };
    }
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'artwork-cache.js'), 'utf8'));
    window.__completeNextDownload = function (ok) {
        var next = window.__downloadCallbacks.shift();
        if (!next) { throw new Error('nenhum download pendente'); }
        if (ok === false) {
            next.callbacks.onfailed(1, 'NETWORK');
            return;
        }
        files[next.request.name] = { name: next.request.name, fileSize: settings.fileSize || 40000 };
        next.callbacks.oncompleted(1, next.request.destination + '/' + next.request.name);
    };
    window.__progressNextDownload = function (received, total, advanceMs) {
        var next = window.__downloadCallbacks[0];
        if (!next) { throw new Error('nenhum download pendente'); }
        window.__now += Number(advanceMs) || 0;
        next.callbacks.onprogress(1, received, total);
    };
    return window;
}

process.stdout.write('Sem pendrive, nada muda e nada quebra\n');
(function () {
    var window = makeWindow({ usb: false });
    var cache = window.BuroArtworkCache;
    var attached = null;
    cache.attach(512, function (ok) { attached = ok; });
    check('ligar sem pendrive responde que nao deu, em vez de falhar',
        attached === false);
    /*
      O ponto: quem nao tem pendrive nao pode ficar pior. Pedir uma capa nao faz
      nada, e ler uma devolve nulo para o app cair na URL de sempre.
    */
    check('pedir uma copia nao tenta baixar nada',
        (function () { cache.remember('item-1', 'https://cdn.test/a.jpg'); return window.__downloads.length === 0; }()));
    check('e ler uma capa devolve nulo, para o app usar a URL de sempre',
        cache.localUrl('item-1') === null);
    check('o painel sabe dizer que falta o pendrive',
        cache.status().hasStorage === false && cache.status().ready === false);
    window.close();
}());

process.stdout.write('Com pendrive, a capa e guardada e relida de la\n');
(function () {
    var window = makeWindow({ usb: true });
    var cache = window.BuroArtworkCache;
    cache.attach(512, function () {});
    cache.remember('item-1', 'https://cdn.test/duna.jpg');
    check('a capa foi baixada para o pendrive',
        window.__downloads.length === 1 && window.__downloads[0].url === 'https://cdn.test/duna.jpg');
    check('o arquivo leva o id do titulo, e nao o nome do filme',
        window.__downloads[0].name === 'item-1.img');
    check('e passa a ser lida do disco, sem rede',
        cache.localUrl('item-1') === 'file:///usb/capas/item-1.img');
    /* Baixar de novo o que ja se tem e o oposto do proposito do cache. */
    check('a mesma capa nao e baixada duas vezes',
        (function () { cache.remember('item-1', 'https://cdn.test/duna.jpg'); return window.__downloads.length === 1; }()));
    check('o painel conta o que esta guardado',
        cache.status().count === 1 && cache.status().bytes === 40000);
    window.close();
}());

process.stdout.write('O cache respeita o limite escolhido\n');
(function () {
    /*
      Capas de 1,5 MB com o limite minimo de 64 MB.

      O tamanho e realista de proposito: um arquivo acima de `MAX_FILE_BYTES` e
      descartado por outro motivo — nao e capa, e pagina de erro — e nesse caso
      o cache nunca cresce, entao o teste nao mediria o limite que quer medir.
    */
    var window = makeWindow({ usb: true, fileSize: 1536 * 1024 });
    var cache = window.BuroArtworkCache;
    var index;
    cache.attach(64, function () {});
    for (index = 0; index < 60; index += 1) {
        cache.remember('capa-' + index, 'https://cdn.test/' + index + '.jpg');
    }
    /*
      O pendrive e do usuario e pode ter outras coisas: um cache sem teto
      encheria o aparelho de outra pessoa.
    */
    /* 64 MB divididos por 1,5 MB dao 42 capas e meia, entao o cache tem de
       parar por volta dai — e nao nas sessenta pedidas. */
    check('parar de baixar quando o limite e alcancado',
        window.__downloads.length > 30 && window.__downloads.length < 50);
    check('e o total guardado nao passa do limite escolhido',
        cache.status().bytes <= 64 * 1024 * 1024 + 1536 * 1024);
    check('o limite pedido e respeitado dentro do que faz sentido',
        cache.safeLimitMb(4) === cache.limits().minimum &&
        cache.safeLimitMb(999999) === cache.limits().maximum &&
        cache.safeLimitMb('abacaxi') === cache.limits().fallback);
    window.close();
}());

process.stdout.write('Uma capa com credencial nao e baixada, nem para o pendrive\n');
(function () {
    var window = makeWindow({ usb: true });
    var cache = window.BuroArtworkCache;
    cache.attach(512, function () {});
    /*
      Gravar num pendrive removivel e pior do que gravar no banco: o aparelho
      sai da TV e vai para outro computador. A peneira aqui e a mesma que decide
      se a URL podia ir para o disco.
    */
    cache.remember('x', 'http://host/movie/usuario/senha/1.jpg');
    cache.remember('y', 'https://cdn.test/a.jpg?token=abc');
    cache.remember('z', 'https://user:senha@cdn.test/a.jpg');
    check('caminho autenticado, token e credencial embutida sao recusados',
        window.__downloads.length === 0);
    check('mas uma capa publica com tamanho na query passa',
        (function () { cache.remember('w', 'https://cdn.test/a.jpg?w=300'); return window.__downloads.length === 1; }()));
    window.close();
}());

process.stdout.write('Uma resposta grande demais nao fica no pendrive\n');
(function () {
    /* 5 MB para uma capa: e uma pagina de erro ou um video servido no lugar. */
    var window = makeWindow({ usb: true, fileSize: 5 * 1024 * 1024 });
    var cache = window.BuroArtworkCache;
    cache.attach(512, function () {});
    cache.remember('grande', 'https://cdn.test/enorme.jpg');
    check('o arquivo e apagado em vez de contar como capa',
        cache.status().count === 0 && window.__deleted.indexOf('grande.img') >= 0);
    window.close();
}());

process.stdout.write('Desligar preserva; apagar e uma acao a parte\n');
(function () {
    var window = makeWindow({ usb: true });
    var cache = window.BuroArtworkCache;
    cache.attach(512, function () {});
    cache.remember('item-1', 'https://cdn.test/a.jpg');
    cache.detach();
    check('desligar nao apaga nada do pendrive',
        window.__deleted.length === 0);
    check('e o app volta a usar a URL de sempre',
        cache.localUrl('item-1') === null && cache.status().ready === false);
    cache.attach(512, function () {});
    check('religar reencontra a capa que ja estava la',
        cache.localUrl('item-1') === 'file:///usb/capas/item-1.img');
    cache.clear();
    check('apagar, quando pedido, remove os arquivos',
        cache.status().count === 0 && window.__deleted.indexOf('item-1.img') >= 0);
    window.close();
}());

process.stdout.write('Um id estranho nunca vira nome de arquivo\n');
(function () {
    var window = makeWindow({ usb: true });
    var cache = window.BuroArtworkCache;
    cache.attach(512, function () {});
    cache.remember('../../etc/passwd', 'https://cdn.test/a.jpg');
    cache.remember('nome com espaco', 'https://cdn.test/b.jpg');
    check('travessia de diretorio e nome invalido sao recusados',
        window.__downloads.length === 0);
    window.close();
}());

process.stdout.write('Preenchimento completo mostra progresso e respeita pausa\n');
(function () {
    var window = makeWindow({ usb: true, deferred: true });
    var cache = window.BuroArtworkCache;
    var snapshots = [];
    cache.attach(512, function () {});
    cache.watch(function (status) { snapshots.push(status); });
    cache.fill([
        { id: 'a', url: 'https://cdn.test/a.jpg' },
        { id: 'b', url: 'https://cdn.test/b.jpg' },
        { id: 'c', url: 'https://cdn.test/c.jpg' },
        { id: 'd', url: 'https://cdn.test/d.jpg' },
        { id: 'd', url: 'https://cdn.test/repetida.jpg' },
        { id: 'privada', url: 'https://cdn.test/e.jpg?token=segredo' }
    ]);
    check('a varredura deduplica e exclui URL privada do total',
        cache.status().total === 4 && window.__downloads.length === 2);
    check('duas capas por vez deixam a fila responsiva',
        cache.status().active === 2 && cache.status().pending === 4);
    window.__progressNextDownload(65536, 131072, 500);
    check('o progresso da plataforma vira velocidade real em memoria',
        cache.status().bytesPerSecond === 131072);
    cache.pause();
    check('pausar esconde uma velocidade que ja nao esta sendo medida',
        cache.status().bytesPerSecond === null);
    window.__completeNextDownload(true);
    window.__completeNextDownload(true);
    check('pausar deixa as duas restantes na fila',
        cache.status().paused === true && cache.status().done === 2 &&
        cache.status().pending === 2 && window.__downloads.length === 2);
    check('a porcentagem real fica em cinquenta', cache.status().percent === 50);
    cache.resume();
    check('continuar inicia somente o que faltava', window.__downloads.length === 4);
    window.__progressNextDownload(131072, 262144, 500);
    check('continuar reinicia a janela da velocidade sem reaproveitar valor antigo',
        cache.status().bytesPerSecond === 262144);
    window.__completeNextDownload(true);
    window.__completeNextDownload(false);
    check('conclusao chega a cem e conserva a falha recuperavel',
        cache.status().complete === true && cache.status().percent === 100 &&
        cache.status().done === 4 && cache.status().failed === 1);
    check('conclusao remove a velocidade da transferencia encerrada',
        cache.status().bytesPerSecond === null);
    check('observadores receberam o progresso', snapshots.length >= 6);
    cache.fill([
        { id: 'a', url: 'https://cdn.test/a.jpg' },
        { id: 'b', url: 'https://cdn.test/b.jpg' },
        { id: 'd', url: 'https://cdn.test/d.jpg' }
    ]);
    check('atualizar confere o que existe sem baixar de novo',
        cache.status().total === 3 && cache.status().done === 2 && window.__downloads.length === 5);
    window.__completeNextDownload(true);
    window.close();
}());

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
