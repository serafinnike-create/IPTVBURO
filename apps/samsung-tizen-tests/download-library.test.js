/* Large Download-library UI tests. Synthetic fixtures only. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var SCRIPT_FILES = (function () {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var files = [];
    var match = pattern.exec(html);
    while (match) {
        files.push(match[1]);
        match = pattern.exec(html);
    }
    return files;
}());
var passed = 0;
var failures = [];
var activeWindow = null;

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function waitFor(predicate, timeoutMs) {
    var started = Date.now();
    return new Promise(function (resolve, reject) {
        function poll() {
            if (predicate()) { resolve(); return; }
            if (Date.now() - started > timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function press(window, keyCode) {
    var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
}

function downloadEntry(index, state, platformId) {
    var episode = index % 2 === 1;
    var id = (episode ? 'episode:' : 'movie:') + 'library-' + ('000' + index).slice(-3);
    return {
        id: id,
        name: index === 87 ? 'Episódio Único de Viagem' :
            ((episode ? 'Episódio ' : 'Filme ') + ('000' + index).slice(-3)),
        contentType: episode ? 'EPISODE' : 'MOVIE',
        fileName: id.replace(':', '-') + '.mp4',
        state: state,
        receivedBytes: state === 'COMPLETED' ? 1000 : 250,
        totalBytes: 1000,
        errorCode: state === 'FAILED' ? 'NETWORK_ERROR' : null,
        platformId: platformId == null ? null : platformId
    };
}

function loadApp(snapshot, activeStates) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    var platform = { callbacks: {}, paused: [], resumed: [], cancelled: [] };
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.localStorage.setItem('iptvburo.downloads.v1', JSON.stringify(snapshot));
    window.tizen = {
        systeminfo: {
            getCapability: function (name) { return name === 'http://tizen.org/feature/download'; }
        },
        filesystem: {
            listStorages: function (success) {
                success([{ label: 'removable_test', type: 'EXTERNAL', state: 'MOUNTED' }]);
            },
            addStorageStateChangeListener: function () { return 1; },
            resolve: function (target, success) {
                success({
                    fullPath: String(target),
                    moveTo: function (source, destination, overwrite, done) { done(); }
                });
            }
        },
        download: {
            getState: function (id) { return activeStates[id]; },
            getDownloadRequest: function () { return { destination: 'removable_test/IPTV BURO', fileName: 'file.part' }; },
            setListener: function (id, callbacks) { platform.callbacks[id] = callbacks; },
            pause: function (id) { platform.paused.push(id); },
            resume: function (id) { platform.resumed.push(id); },
            cancel: function (id) { platform.cancelled.push(id); },
            start: function () { return 999; }
        },
        DownloadRequest: function (url, destination, fileName) {
            this.url = url; this.destination = destination; this.fileName = fileName;
        },
        keymanager: {
            getDataAliasList: function () {
                return Object.keys(secureData).map(function (name) { return { name: name }; });
            },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) {
                if (!secureData[alias.name]) { throw { name: 'NotFoundError' }; }
                return secureData[alias.name];
            },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    window.__downloadPlatform = platform;
    return window;
}

async function typeDownloadQuery(window, value) {
    var input = window.document.getElementById('download-query');
    var attempts;
    for (attempts = 0; attempts < 80 && window.document.activeElement !== input; attempts += 1) {
        press(window, 38);
    }
    if (window.document.activeElement !== input) { throw new Error('download search input is not D-pad reachable'); }
    input.value = value;
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    /*
      Quatro segundos, nao um.

      Este arquivo roda no meio de uma suite de cinquenta arquivos jsdom: com um
      segundo a espera estourava por lentidao da maquina, a funcao saia antes do
      redesenho e o `querySelector` seguinte recebia nulo — uma falha que nao
      existia quando o arquivo rodava sozinho. O limite continua existindo para
      que um travamento real falhe.
    */
    await waitFor(function () {
        var replacement = window.document.getElementById('download-query');
        return Boolean(replacement) && replacement !== input && replacement.value === value;
    }, 4000);
}

async function run() {
    var snapshot = [];
    var activeStates = {};
    var index;
    var platformId = 1;
    var window;
    var state;
    var firstActiveId;
    var target;
    var rateClock;

    for (index = 0; index < 70; index += 1) { snapshot.push(downloadEntry(index, 'COMPLETED', null)); }
    for (index = 70; index < 80; index += 1) { snapshot.push(downloadEntry(index, 'FAILED', null)); }
    for (index = 80; index < 90; index += 1) {
        activeStates[platformId] = index % 4 === 0 ? 'DOWNLOADING' :
            (index % 4 === 1 ? 'PAUSED' : (index % 4 === 2 ? 'QUEUED' : 'PAUSED'));
        snapshot.push(downloadEntry(index, activeStates[platformId], platformId));
        platformId += 1;
    }
    for (index = 90; index < 95; index += 1) { snapshot.push(downloadEntry(index, 'CANCELLED', null)); }
    firstActiveId = snapshot[80].id;
    window = loadApp(snapshot, activeStates);
    activeWindow = window;
    /* Aguarda o bootstrap inteiro terminar antes de forçar a seção sintética.
       `ready` fica verdadeiro antes de a leitura assíncrona de perfis concluir;
       sem este gate ela podia sobrescrever Downloads no meio da busca. */
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroUsb.hasStorage() &&
            window.BuroApp.state.screen === 'PROFILES';
    }, 4000);
    state = window.BuroApp.state;
    state.screen = 'SHELL';
    state.section = 'DOWNLOADS';
    state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('Fila grande de Downloads\n');
    check('a tela limita o DOM a 40 transferências e pagina as 95 entradas',
        window.document.querySelectorAll('.download-row').length === 40 &&
        window.document.querySelector('.download-pagination').textContent.indexOf('Página 1 de 3') >= 0 &&
        window.document.querySelector('.download-pagination').textContent.indexOf('/ 95') >= 0);
    check('trabalho ativo aparece antes de falhas e cópias concluídas como Android e Windows',
        window.document.querySelector('.download-row').getAttribute('data-download-id') === firstActiveId &&
        window.document.querySelector('.download-row small').textContent.indexOf('Concluído') === -1);
    check('ordenação de apresentação não altera a ordem persistida nem injeta metadados privados',
        JSON.parse(window.localStorage.getItem('iptvburo.downloads.v1'))[0].id === snapshot[0].id &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('_queueOrder') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('https://') === -1);

    window.BuroApp._activate(window.document.querySelector('[data-action="download-page-next"]'));
    check('a segunda página conserva 40 linhas e foco repetível no controle remoto',
        window.document.querySelectorAll('.download-row').length === 40 &&
        window.document.querySelector('[data-action="download-page-next"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="download-page-next"]'));
    check('a última página mostra 15 linhas e devolve o foco à página anterior',
        window.document.querySelectorAll('.download-row').length === 15 &&
        !window.document.querySelector('[data-action="download-page-next"]') &&
        window.document.querySelector('[data-action="download-page-previous"]').classList.contains('focused'));

    window.BuroApp._activate(window.document.querySelector('[data-action="download-filter"][data-kind="EPISODE"]'));
    check('filtro Séries reinicia a primeira página e pagina somente episódios',
        window.document.querySelector('.download-pagination').textContent.indexOf('Página 1 de 2') >= 0 &&
        Array.prototype.every.call(window.document.querySelectorAll('.download-row'), function (row) {
            return row.getAttribute('data-download-id').indexOf('episode:') === 0;
        }));
    window.BuroApp._activate(window.document.querySelector('[data-action="download-page-next"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="download-compact"]'));
    check('modo compacto preserva filtro e página sem alterar a fila',
        window.document.querySelector('.download-list').classList.contains('compact') &&
        window.document.querySelector('.download-pagination').textContent.indexOf('Página 2 de 2') >= 0 &&
        window.BuroDownloads.list().length === 95);

    process.stdout.write('Busca local e mudanças assíncronas\n');
    await typeDownloadQuery(window, 'episodio unico');
    check('busca ignora acentos, encontra o episódio e mantém o campo focado',
        window.document.querySelectorAll('.download-row').length === 1 &&
        window.document.querySelector('.download-row strong').textContent === 'Episódio Único de Viagem' &&
        window.document.activeElement.id === 'download-query');
    check('consulta de Downloads fica somente em memória e não contamina o snapshot',
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('episodio unico') === -1);
    await typeDownloadQuery(window, 'nao existe na fila');
    check('busca sem resultado mantém filtros e campo, com estado vazio próprio',
        !window.document.querySelector('.download-row') &&
        Boolean(window.document.getElementById('download-query')) &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('downloadNoMatch')) >= 0);
    await typeDownloadQuery(window, '');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="download-filter"][data-kind="ALL"]'));
    }, 4000);
    window.BuroApp._activate(window.document.querySelector('[data-action="download-filter"][data-kind="ALL"]'));

    rateClock = 100000;
    window.Date.now = function () { return rateClock; };
    window.__downloadPlatform.callbacks[snapshot[80].platformId].onprogress(
        snapshot[80].platformId, 1024 * 1024, 10 * 1024 * 1024
    );
    rateClock += 1000;
    window.__downloadPlatform.callbacks[snapshot[80].platformId].onprogress(
        snapshot[80].platformId, 2 * 1024 * 1024, 10 * 1024 * 1024
    );
    check('linha ativa mostra porcentagem, velocidade localizada e tempo restante como Windows',
        window.document.querySelector('[data-download-id="' + firstActiveId + '"] small').textContent.indexOf('20%') >= 0 &&
        window.document.querySelector('[data-download-id="' + firstActiveId + '"] small').textContent.indexOf('1,0 MB/s') >= 0 &&
        window.document.querySelector('[data-download-id="' + firstActiveId + '"] small').textContent.indexOf('8 s') >= 0);
    check('atualizacao visual nao persiste taxa, estimativa ou relogio de amostragem',
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('bytesPerSecond') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('remainingSeconds') === -1 &&
        window.localStorage.getItem('iptvburo.downloads.v1').indexOf('rateSample') === -1);

    target = window.document.querySelector('[data-download-id="' + firstActiveId + '"] [data-action="download-pause"]');
    for (index = 0; index < 80 && window.document.activeElement !== target; index += 1) { press(window, 40); }
    check('o D-pad alcança a ação da primeira transferência ativa', window.document.activeElement === target);
    window.__downloadPlatform.callbacks[snapshot[80].platformId].oncompleted(
        snapshot[80].platformId, 'removable_test/IPTV BURO/' + snapshot[80].fileName + '.part'
    );
    check('quando a transferência muda para concluída, sua nova página é aberta para não fazê-la desaparecer',
        Boolean(window.document.querySelector('[data-download-id="' + firstActiveId + '"]')) &&
        window.document.querySelector('.download-pagination').textContent.indexOf('Página 3 de 3') >= 0);
    check('estado concluído troca controles para Assistir e Remover sem duplicar a entrada',
        window.document.querySelectorAll('[data-download-id="' + firstActiveId + '"]').length === 1 &&
        Boolean(window.document.querySelector('[data-download-id="' + firstActiveId + '"] [data-action="download-play"]')) &&
        Boolean(window.document.querySelector('[data-download-id="' + firstActiveId + '"] [data-action="download-remove"]')));

    window.close();
    activeWindow = null;
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    if (activeWindow) { activeWindow.close(); activeWindow = null; }
    process.stderr.write('Falha na suíte: ' + (error && error.stack ? error.stack : error.message) + '\n');
    process.exitCode = 1;
});
