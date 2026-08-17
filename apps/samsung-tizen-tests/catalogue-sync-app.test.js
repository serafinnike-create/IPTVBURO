/* Integration tests for background Xtream hydration in the Samsung shell. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
/* A ordem vem do index.html, para a suíte não quebrar quando um módulo novo
   entra no app. Ver platform-failures.test.js. */
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

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function waitFor(predicate, timeoutMs) {
    var started = Date.now();
    return new Promise(function (resolve, reject) {
        function poll() {
            if (predicate()) { resolve(); return; }
            if (Date.now() - started >= timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function call(window, method, args) {
    return new Promise(function (resolve, reject) {
        method.apply(window.BuroStorage, args.concat([resolve, reject]));
    });
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true, section: 'SOURCES'
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return Object.keys(secureData).map(function (name) { return { name: name }; }); },
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
    SCRIPT_FILES.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.init();
    return window;
}

async function run() {
    var window = loadApp();
    var source = { id: 'source-background', name: 'Fonte completa', type: 'XTREAM', channelCount: 0, createdAt: Date.now() };
    var profile = { id: 'profile-background', name: 'Sala', avatarKey: 'gold', isKids: false, sourceId: source.id, createdAt: Date.now() };
    var categories = [
        { id: 'cat-live', sourceId: source.id, contentType: 'LIVE', providerCategoryId: '3', name: 'Ao vivo', sortOrder: 0 },
        { id: 'cat-series', sourceId: source.id, contentType: 'SERIES', providerCategoryId: '2', name: 'Séries', sortOrder: 0 },
        { id: 'cat-movies', sourceId: source.id, contentType: 'MOVIE', providerCategoryId: '1', name: 'Filmes', sortOrder: 0 }
    ];
    var pending = [];
    var aborts = 0;
    var year = new Date().getFullYear();

    await waitFor(function () { return window.BuroApp.state.ready; }, 1200);
    await call(window, window.BuroStorage.put, ['sources', source]);
    await call(window, window.BuroStorage.put, ['profiles', profile]);
    await Promise.all(categories.map(function (category) {
        return call(window, window.BuroStorage.put, ['categories', category]);
    }));
    await call(window, window.BuroStorage.secureSave, [source.id, {
        server: 'https://provider.test', username: 'fixture', password: 'private-fixture'
    }]);
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = categories;
    window.BuroApp.state.items = [];
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SOURCES';
    window.BuroApp.state.screenData = null;

    window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success, failure) {
        var request = { category: category, success: success, failure: failure, aborted: false };
        pending.push(request);
        return { abort: function () {
            if (request.aborted) { return; }
            request.aborted = true; aborts += 1; failure({ code: 'NETWORK_ABORTED' });
        } };
    };

    process.stdout.write('Integração do catálogo em segundo plano\n');
    window.BuroApp.render();
    window.BuroApp._activate(window.document.querySelector('[data-action="select-source"]'));
    await waitFor(function () { return pending.length === 1; }, 500);
    check('selecionar fonte inicia a fila automaticamente e prioriza filmes', pending[0].category.id === 'cat-movies');
    check('Fontes apresenta progresso real sem bloquear a navegação',
        window.document.querySelector('.source-sync-state.running') &&
        !window.document.querySelector('[data-action="source-manage"]').disabled);

    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="HOME"]'));
    check('Home mostra progresso e oferece pausa pelo D-pad',
        window.document.querySelector('.catalogue-sync-banner') &&
        window.document.querySelector('[data-action="catalogue-sync-cancel"].focusable'));
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-sync-cancel"]'));
    await waitFor(function () {
        return window.BuroCatalogueSync.progress(source, categories).state === 'CANCELLED';
    }, 500);
    check('pausa da Home aborta a requisição e troca a ação para Continuar',
        aborts === 1 && window.document.querySelector('[data-action="catalogue-sync-resume"]'));

    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-sync-resume"]'));
    await waitFor(function () { return pending.length === 2; }, 500);
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="MOVIES"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="category"][data-id="cat-movies"]'));
    await new Promise(function (resolve) { setTimeout(resolve, 20); });
    check('abrir a categoria em hidratação reutiliza a requisição em curso',
        pending.length === 2 && window.BuroApp.state.screenData.kind === 'catalogue-loading');
    pending[1].success([{
        id: 'movie:background', sourceId: source.id, categoryId: 'cat-movies', contentType: 'MOVIE',
        name: 'Filme completo', year: year, rating: 9.2, addedAt: Date.now(), sortOrder: 0,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '11', extension: 'mp4' }
    }], { 'movie:background': 'https://images.public.test/movie.jpg' });
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'category';
    }, 500);
    check('conclusão da categoria substitui o skeleton sem segundo download',
        window.document.body.textContent.indexOf('Filme completo') >= 0 &&
        pending.filter(function (request) { return request.category.id === 'cat-movies'; }).length === 2);

    await waitFor(function () { return pending.length === 3; }, 500);
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="HOME"]'));
    pending[2].success([{
        id: 'series:background', sourceId: source.id, categoryId: 'cat-series', contentType: 'SERIES',
        name: 'Série completa', year: year - 1, rating: 8.8, addedAt: Date.now(), sortOrder: 0,
        locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: '22', extension: 'mp4' }
    }], {});
    await waitFor(function () { return pending.length === 4; }, 500);
    pending[3].success([{
        id: 'live:background', sourceId: source.id, categoryId: 'cat-live', contentType: 'LIVE',
        name: 'Canal completo', sortOrder: 0,
        locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: '33', extension: 'ts' }
    }], {});
    await waitFor(function () {
        return window.BuroCatalogueSync.progress(source, categories).state === 'COMPLETE' &&
            window.BuroApp.state.sources[0].channelCount === 3;
    }, 1000);
    check('conclusão atualiza a contagem persistida da fonte', window.BuroApp.state.sources[0].channelCount === 3);
    var stored = await call(window, window.BuroStorage.byIndex, ['items', 'bySource', source.id]);
    check('as três verticais ficam persistidas após transações por categoria',
        stored.length === 3 && stored.some(function (item) { return item.contentType === 'MOVIE'; }) &&
        stored.some(function (item) { return item.contentType === 'SERIES'; }) &&
        stored.some(function (item) { return item.contentType === 'LIVE'; }));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            window.document.body.textContent.indexOf('Filme completo') >= 0;
    }, 1000);
    check('Home é recomposta do IndexedDB completo ao terminar a fila',
        !window.document.querySelector('.catalogue-sync-banner') &&
        window.document.body.textContent.indexOf('Filme completo') >= 0);
    check('checkpoint de integração continua sem usuário, senha ou servidor', (function () {
        var raw = window.localStorage.getItem(window.BuroCatalogueSync._checkpointKey) || '';
        return raw.indexOf('private-fixture') < 0 && raw.indexOf('provider.test') < 0 && raw.indexOf('fixture') < 0;
    }()));

    window.close();
    process.stdout.write('\nResultado: ' + passed + ' passaram, ' + failures.length + ' falharam.\n');
    if (failures.length) { process.exitCode = 1; }
}

run().catch(function (error) {
    process.stderr.write(error.stack + '\n');
    process.exitCode = 1;
});
