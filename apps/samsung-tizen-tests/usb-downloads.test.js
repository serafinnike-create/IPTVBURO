/*
  Contratos de download por USB. Nenhum dado de provedor real.

  O que estes testes cobrem é o que não depende de TV: as regras do ADR-008,
  o nome do arquivo, o sufixo `.part` e o comportamento quando o usuário
  arranca o pendrive. A API tizen é simulada.

  O que eles NÃO provam: que tizen.download realmente grava num USB. Isso só
  em hardware.
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

/*
  Simula a plataforma. `storages` é mutável para que um teste possa remover o
  pendrive no meio de um download, que é o caso que separa a TV de um PC.
*/
function makeWindow(options) {
    var settings = options || {};
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    if (settings.downloadSnapshot) {
        window.localStorage.setItem('iptvburo.downloads.v1', settings.downloadSnapshot);
    }
    var state = {
        storages: settings.storages || [{ label: 'removable_sda1', type: 'EXTERNAL', state: 'MOUNTED' }],
        started: [],
        paused: [],
        resumed: [],
        cancelled: [],
        listeners: [],
        callbacks: {},
        listenerIds: [],
        nextId: 1,
        files: settings.files || {},
        createdDirectories: []
    };

    window.tizen = {
        systeminfo: {
            getCapability: function (name) {
                return name === 'http://tizen.org/feature/download' && settings.downloadCapability !== false;
            }
        },
        filesystem: {
            listStorages: function (success) { success(state.storages); },
            addStorageStateChangeListener: function (onChange) {
                state.listeners.push(onChange);
                return state.listeners.length;
            },
            resolve: function (target, success, failure, mode) {
                var storedFile = state.files[String(target)];
                if (storedFile) {
                    success({
                        name: String(target).split('/').pop(), isDirectory: false,
                        fileSize: storedFile.size || 1024,
                        toURI: function () { return storedFile.uri; }
                    });
                    return;
                }
                /*
                  Só a raiz do volume entra aqui. Um caminho com barra é uma
                  subpasta — no fluxo real, a pasta do arquivo concluído — e
                  cai no ramo de diretório abaixo.
                */
                if (String(target).indexOf('removable_') === 0 && String(target).indexOf('/') === -1) {
                    var missing = !state.storages.some(function (storage) {
                        return storage.label === target && storage.state === 'MOUNTED';
                    });
                    if (missing) { failure({ name: 'NotFoundError' }); return; }
                    success({
                        fullPath: target + '/',
                        resolve: function () { throw { name: 'NotFoundError' }; },
                        createDirectory: function (name) {
                            state.createdDirectories.push(name);
                            return { fullPath: target + '/' + name };
                        }
                    });
                    return;
                }
                /*
                  Diretório do arquivo concluído. No Tizen o `moveTo` pertence
                  ao diretório e recebe nomes relativos a ele, não caminhos.
                */
                success({
                    fullPath: String(target),
                    moveTo: function (source, destination, overwrite, ok) {
                        state.renamedFrom = source;
                        state.renamedTo = destination;
                        ok();
                    }
                });
            }
        },
        download: {
            start: function (request, callbacks) {
                if (settings.startResult !== undefined) { return settings.startResult; }
                var id = state.nextId;
                state.nextId += 1;
                state.started.push(request);
                state.callbacks[id] = callbacks;
                return id;
            },
            pause: function (id) { state.paused.push(id); },
            resume: function (id) { state.resumed.push(id); },
            cancel: function (id) { state.cancelled.push(id); },
            getState: function (id) {
                var result = settings.downloadStates && settings.downloadStates[id];
                if (!result) { throw { name: 'NotFoundError' }; }
                return result;
            },
            getDownloadRequest: function (id) {
                var result = settings.downloadRequests && settings.downloadRequests[id];
                if (!result) { throw { name: 'NotFoundError' }; }
                return result;
            },
            setListener: function (id, callbacks) {
                state.listenerIds.push(id);
                state.callbacks[id] = callbacks;
            }
        },
        DownloadRequest: function (url, destination, fileName) {
            this.url = url;
            this.destination = destination;
            this.fileName = fileName;
        }
    };

    ['js/domain.js', 'js/usb.js', 'js/downloads.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });

    window.__platform = state;
    return window;
}

function run() {
    var window;
    var platform;
    var result;
    var request;
    var snapshot;

    process.stdout.write('Detecção de armazenamento USB\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    check('reconhece um volume removable_ montado', window.BuroUsb.hasStorage());
    check('o caminho físico do volume não é exposto à interface',
        JSON.stringify(window.BuroUsb.mountedStorages()).indexOf('/') === -1);

    window = makeWindow({ storages: [{ label: 'internal0', type: 'INTERNAL', state: 'MOUNTED' }] });
    window.BuroUsb.watch(function () {});
    check('armazenamento interno não conta como destino de download',
        !window.BuroUsb.hasStorage());
    check('sem USB o download fica desabilitado', !window.BuroDownloads.enabled());

    window = makeWindow({ downloadCapability: false });
    window.BuroUsb.watch(function () {});
    check('capability oficial falsa oculta downloads mesmo com a API exposta',
        !window.BuroDownloads.available() && !window.BuroDownloads.enabled());

    process.stdout.write('Regras do ADR-008\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});

    result = null;
    window.BuroDownloads.start(
        { contentType: 'LIVE', providerItemId: '7', name: 'Canal' },
        function () { return 'https://provider.test/live/u/p/7.ts'; },
        function () { result = 'iniciou'; },
        function (error) { result = error.code; }
    );
    check('TV ao vivo é recusada antes de qualquer chamada de rede',
        result === 'LIVE_NOT_DOWNLOADABLE' && platform.started.length === 0);

    check('o nome do arquivo vem da identidade de conteúdo, não da URL',
        window.BuroDownloads.safeFileName({
            contentType: 'MOVIE', providerItemId: '42', extension: 'mkv'
        }) === 'movie-42.mkv');

    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: '42', name: 'Filme', extension: 'mkv' },
        function () { return 'https://provider.test/movie/secretuser/secretpass/42.mkv'; },
        function () {}, function () {}
    );
    request = platform.started[0];
    check('a gravação começa com sufixo .part',
        request.fileName === 'movie-42.mkv.part');
    check('a URL com credenciais não aparece no nome do arquivo',
        request.fileName.indexOf('secretuser') === -1 &&
        request.fileName.indexOf('secretpass') === -1);

    snapshot = JSON.stringify(window.BuroDownloads.list());
    check('a fila visível não carrega a URL nem as credenciais',
        snapshot.indexOf('provider.test') === -1 &&
        snapshot.indexOf('secretpass') === -1);
    check('a fila expõe somente o tipo necessário aos filtros Filmes e Séries',
        window.BuroDownloads.list()[0].contentType === 'MOVIE');

    check('o arquivo é gravado numa pasta identificável no USB',
        platform.createdDirectories.indexOf('IPTV BURO') >= 0);
    var singleDownloadWindow = window;
    var singleDownloadPlatform = platform;

    process.stdout.write('Resolução assíncrona tardia\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    var releaseResolvedUrl;
    result = null;
    window.BuroDownloads.startAsync(
        { contentType: 'MOVIE', providerItemId: 'async', name: 'Filme remoto', locator: { extension: 'mkv' } },
        function (resolved) { releaseResolvedUrl = resolved; },
        function () { result = 'iniciou'; },
        function (error) { result = error.code; }
    );
    check('fila fica aguardando enquanto a fonte resolve sem URL no snapshot',
        window.BuroDownloads.list()[0].state === 'QUEUED' && platform.started.length === 0 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('provider.test') === -1);
    releaseResolvedUrl('https://provider.test/movie/secretuser/secretpass/async.mkv');
    check('resolver entrega a URL diretamente à plataforma e conserva extensão do locator',
        result === 'iniciou' && platform.started.length === 1 &&
        platform.started[0].fileName === 'movie-async.mkv.part');
    check('URL assíncrona nunca entra no snapshot nem na fila pública',
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('provider.test') === -1 &&
        JSON.stringify(window.BuroDownloads.list()).indexOf('secretpass') === -1);

    process.stdout.write('Seleção em lote de séries\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    var episodes = [
        { id: 'row-s2e1', providerItemId: 's2e1', contentType: 'EPISODE', name: 'S2E1', locator: { season: 2, episode: 1 } },
        { id: 'row-s1e2', providerItemId: 's1e2', contentType: 'EPISODE', name: 'S1E2', locator: { season: 1, episode: 2 } },
        { id: 'row-s1e1', providerItemId: 's1e1', contentType: 'EPISODE', name: 'S1E1', locator: { season: 1, episode: 1 } }
    ];
    check('fila em lote ordena por temporada e episódio apesar da chegada embaralhada',
        window.BuroDownloads.bulkCandidates(episodes).map(function (item) { return item.providerItemId; }).join(',') ===
            's1e1,s1e2,s2e1');
    window.BuroDownloads.start(episodes[1], function () { return 'https://provider.test/s1e2.mp4'; }, function () {}, function () {});
    platform.callbacks[1].oncompleted(1, 'removable_sda1/IPTV BURO/episode-s1e2.mp4.part');
    check('episódio concluído é excluído da série inteira para não baixar bytes repetidos',
        window.BuroDownloads.bulkCandidates(episodes).map(function (item) { return item.providerItemId; }).join(',') ===
            's1e1,s2e1');
    check('filtro de temporada usa a mesma regra de conclusão',
        window.BuroDownloads.bulkCandidates(episodes, 1).map(function (item) { return item.providerItemId; }).join(',') === 's1e1');
    window.BuroDownloads.start(episodes[2], function () { return 'https://provider.test/s1e1.mp4'; }, function () {}, function () {});
    check('operação em andamento continua elegível e será deduplicada pelo start',
        window.BuroDownloads.bulkCandidates(episodes, 1).map(function (item) { return item.providerItemId; }).join(',') === 's1e1');
    platform.callbacks[2].oncompleted(2, 'removable_sda1/IPTV BURO/episode-s1e1.mp4.part');
    check('temporada totalmente concluída não oferece nenhum candidato',
        window.BuroDownloads.bulkCandidates(episodes, 1).length === 0);
    window = singleDownloadWindow;
    platform = singleDownloadPlatform;

    process.stdout.write('Recuperação após reinício\n');
    snapshot = window.localStorage.getItem('iptvburo.downloads.v1');
    check('snapshot local contém somente metadados da fila e nenhum endereço ou segredo',
        snapshot.indexOf('provider.test') === -1 && snapshot.indexOf('secretpass') === -1 &&
        snapshot.indexOf('episode') === -1);
    var restoreSeed = JSON.stringify([{
        id: 'movie:resume', name: 'Retomar', contentType: 'MOVIE', fileName: 'movie-resume.mp4',
        state: 'DOWNLOADING', receivedBytes: 250, totalBytes: 1000, platformId: 41,
        bytesPerSecond: 999999, remainingSeconds: 1, rateSampleAt: 1234, rateSampleBytes: 250,
        url: 'https://provider.test/movie/secretuser/secretpass/41.mp4'
    }]);
    window = makeWindow({ downloadSnapshot: restoreSeed, downloadStates: { 41: 'PAUSED' } });
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    window.BuroDownloads.restore();
    check('reinício reconcilia o estado nativo e reinstala o listener',
        window.BuroDownloads.list().length === 1 && window.BuroDownloads.list()[0].state === 'PAUSED' &&
        platform.listenerIds.join(',') === '41');
    check('campo URL injetado no snapshot é descartado na primeira reconciliação',
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('provider.test') === -1 &&
        JSON.stringify(window.BuroDownloads.list()).indexOf('secretpass') === -1);
    check('reinicio descarta velocidade e estimativa antigas em vez de mostrar telemetria falsa',
        window.BuroDownloads.list()[0].bytesPerSecond === 0 &&
        window.BuroDownloads.list()[0].remainingSeconds === null &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('rateSampleAt') === -1);

    window = makeWindow({
        downloadSnapshot: restoreSeed,
        downloadStates: { 41: 'COMPLETED' },
        downloadRequests: { 41: {
            url: 'https://provider.test/movie/secretuser/secretpass/41.mp4',
            destination: 'removable_sda1/IPTV BURO', fileName: 'movie-resume.mp4.part'
        } }
    });
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    window.BuroDownloads.restore();
    check('operação concluída com o app fechado finaliza o arquivo .part sem persistir a request',
        window.BuroDownloads.list()[0].state === 'COMPLETED' &&
        platform.renamedFrom === 'movie-resume.mp4.part' && platform.renamedTo === 'movie-resume.mp4' &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('provider.test') === -1);

    window = makeWindow({ downloadSnapshot: restoreSeed });
    window.BuroUsb.watch(function () {});
    window.BuroDownloads.restore();
    check('operação que a plataforma perdeu vira falha recuperável em vez de progresso falso',
        window.BuroDownloads.list()[0].state === 'FAILED' &&
        window.BuroDownloads.list()[0].errorCode === 'DOWNLOAD_INTERRUPTED');

    process.stdout.write('Velocidade e tempo restante\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    var rateClock = 10000;
    window.Date.now = function () { return rateClock; };
    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: 'rate', name: 'Taxa sintetica', extension: 'mp4' },
        function () { return 'https://provider.test/movie/u/p/rate.mp4'; },
        function () {}, function () {}
    );
    platform.callbacks[1].onprogress(1, 1024 * 1024, 10 * 1024 * 1024);
    check('a primeira amostra nao inventa velocidade nem tempo restante',
        window.BuroDownloads.list()[0].bytesPerSecond === 0 &&
        window.BuroDownloads.list()[0].remainingSeconds === null);
    rateClock += 250;
    platform.callbacks[1].onprogress(1, 1536 * 1024, 10 * 1024 * 1024);
    check('intervalo menor que o piso conserva a amostra para evitar uma taxa instavel',
        window.BuroDownloads.list()[0].bytesPerSecond === 0);
    rateClock += 250;
    platform.callbacks[1].onprogress(1, 2 * 1024 * 1024, 10 * 1024 * 1024);
    check('duas amostras validas expoem velocidade recente e tempo restante coerente',
        window.BuroDownloads.list()[0].bytesPerSecond === 2 * 1024 * 1024 &&
        window.BuroDownloads.list()[0].remainingSeconds === 4);
    check('telemetria de sessao nunca entra no snapshot persistido',
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('bytesPerSecond') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('remainingSeconds') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('rateSample') === -1);
    platform.callbacks[1].onpaused(1);
    check('pausar limpa a taxa e a estimativa para nao exibir dado congelado',
        window.BuroDownloads.list()[0].state === 'PAUSED' &&
        window.BuroDownloads.list()[0].bytesPerSecond === 0 &&
        window.BuroDownloads.list()[0].remainingSeconds === null);
    window.BuroDownloads.resume('movie:rate');
    check('retomar solicita a continuacao da mesma operacao nativa', platform.resumed.join(',') === '1');
    rateClock += 5000;
    platform.callbacks[1].onprogress(1, 3 * 1024 * 1024, 10 * 1024 * 1024);
    check('a primeira amostra depois da retomada nao conta o tempo pausado',
        window.BuroDownloads.list()[0].bytesPerSecond === 0);
    rateClock += 1000;
    platform.callbacks[1].onprogress(1, 4 * 1024 * 1024, 10 * 1024 * 1024);
    check('a medicao recomeca limpa depois da retomada',
        window.BuroDownloads.list()[0].bytesPerSecond === 1024 * 1024 &&
        window.BuroDownloads.list()[0].remainingSeconds === 6);
    rateClock += 1000;
    platform.callbacks[1].onprogress(1, 1024 * 1024, 10 * 1024 * 1024);
    check('um download reiniciado ou rebobinado zera a taxa negativa',
        window.BuroDownloads.list()[0].bytesPerSecond === 0 &&
        window.BuroDownloads.list()[0].remainingSeconds === null);

    process.stdout.write('Ciclo de vida\n');
    window = singleDownloadWindow;
    platform = singleDownloadPlatform;
    platform.callbacks[1].onprogress(1, 500, 1000);
    check('o progresso é reportado em porcentagem',
        window.BuroDownloads.list()[0].percent === 50);

    platform.callbacks[1].oncompleted(1, 'removable_sda1/IPTV BURO/movie-42.mkv.part');
    check('ao concluir, o .part é renomeado para o nome final',
        platform.renamedFrom === 'movie-42.mkv.part' &&
        platform.renamedTo === 'movie-42.mkv');
    check('o estado final é concluído',
        window.BuroDownloads.list()[0].state === 'COMPLETED');

    process.stdout.write('Reproducao offline\n');
    platform.files['removable_sda1/IPTV BURO/movie-42.mkv'] = {
        uri: 'file:///removable_sda1/IPTV%20BURO/movie-42.mkv', size: 4096
    };
    var offlineUri = null;
    var offlineError = null;
    window.BuroDownloads.resolveCompletedFile('movie:42', function (uri) { offlineUri = uri; }, function (error) {
        offlineError = error.code;
    });
    check('arquivo concluido e resolvido tarde no USB para o AVPlay',
        offlineUri === 'file:///removable_sda1/IPTV%20BURO/movie-42.mkv' && offlineError === null);
    check('URI local nao entra na fila publica nem no snapshot',
        JSON.stringify(window.BuroDownloads.list()).indexOf('file:') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('file:') === -1);

    delete platform.files['removable_sda1/IPTV BURO/movie-42.mkv'];
    platform.files['removable_sda1/IPTV BURO/movie-42.mkv.part'] = {
        uri: 'file:///removable_sda1/IPTV%20BURO/movie-42.mkv.part', size: 4096
    };
    offlineUri = null;
    window.BuroDownloads.resolveCompletedFile('movie:42', function (uri) { offlineUri = uri; }, function () {});
    check('arquivo completo preservado como .part continua reproduzivel quando o rename falhou',
        offlineUri === 'file:///removable_sda1/IPTV%20BURO/movie-42.mkv.part');

    delete platform.files['removable_sda1/IPTV BURO/movie-42.mkv.part'];
    offlineError = null;
    window.BuroDownloads.resolveCompletedFile('movie:42', function () {}, function (error) { offlineError = error.code; });
    check('arquivo removido do USB falha fechado sem consultar o provedor', offlineError === 'OFFLINE_FILE_MISSING');

    process.stdout.write('USB removido durante o download\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroDownloads.watch(function () {});
    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: '99', name: 'Outro', extension: 'mp4' },
        function () { return 'https://provider.test/movie/u/p/99.mp4'; },
        function () {}, function () {}
    );
    check('o download está em andamento antes da remoção',
        window.BuroDownloads.list()[0].state === 'DOWNLOADING');

    /* O usuário arranca o pendrive. */
    platform.storages[0].state = 'REMOVED';
    platform.listeners.forEach(function (listener) { listener({ label: 'removable_sda1' }); });

    check('a remoção do USB pausa o download em vez de acumular falhas',
        window.BuroDownloads.list()[0].state === 'STORAGE_MISSING');
    check('a plataforma foi instruída a pausar', platform.paused.length === 1);

    process.stdout.write('Erros previsíveis\n');
    window = makeWindow();
    window.BuroUsb.watch(function () {});
    result = null;
    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: '7', extension: 'mp4' },
        function () { throw new Error('SOURCE_UNRESOLVED'); },
        function () { result = 'iniciou'; },
        function (error) { result = error.code; }
    );
    check('uma fonte que não resolve falha sem deixar download pendente',
        result === 'SOURCE_UNRESOLVED' &&
        window.BuroDownloads.list()[0].state === 'FAILED');

    window = makeWindow({ startResult: -1 });
    window.BuroUsb.watch(function () {});
    result = null;
    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: 'rejected', extension: 'mp4' },
        function () { return 'https://provider.test/movie/u/p/rejected.mp4'; },
        function () { result = 'iniciou'; },
        function (error) { result = error.code; }
    );
    check('rejeicao imediata da API nativa vira falha recuperavel',
        result === 'DOWNLOAD_REJECTED' &&
        window.BuroDownloads.list()[0].state === 'FAILED');

    process.stdout.write('Estados apresentados ao usuário\n');
    window = makeWindow();
    platform = window.__platform;
    window.BuroUsb.watch(function () {});
    window.BuroDownloads.start(
        { contentType: 'MOVIE', providerItemId: '11', name: 'Título', extension: 'mp4' },
        function () { return 'https://provider.test/movie/u/p/11.mp4'; },
        function () {}, function () {}
    );
    platform.callbacks[1].onprogress(1, 250, 1000);
    check('a lista informa progresso sem expor a origem',
        window.BuroDownloads.list()[0].percent === 25 &&
        JSON.stringify(window.BuroDownloads.list()).indexOf('provider.test') === -1);

    platform.callbacks[1].onfailed(1, { name: 'NetworkError' });
    check('uma falha guarda apenas o código, nunca a mensagem da plataforma',
        window.BuroDownloads.list()[0].state === 'FAILED' &&
        window.BuroDownloads.list()[0].errorCode === 'NetworkError');

    window.BuroDownloads.remove('movie:11');
    check('remover tira o item da lista', window.BuroDownloads.list().length === 0);

    check('o mesmo título não entra duas vezes na fila', (function () {
        var second = null;
        window.BuroDownloads.start(
            { contentType: 'MOVIE', providerItemId: '12', extension: 'mp4' },
            function () { return 'https://provider.test/a.mp4'; }, function () {}, function () {}
        );
        window.BuroDownloads.start(
            { contentType: 'MOVIE', providerItemId: '12', extension: 'mp4' },
            function () { return 'https://provider.test/a.mp4'; },
            function () { second = 'iniciou'; },
            function (error) { second = error.code; }
        );
        return second === 'ALREADY_QUEUED';
    }()));

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run();
