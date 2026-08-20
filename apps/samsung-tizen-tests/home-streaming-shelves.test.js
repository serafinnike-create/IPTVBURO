/*
  As prateleiras públicas de Assinaturas no fim da Home.

  Android e Windows reutilizam ali o catálogo TMDb já usado pela área
  Assinaturas. Esses títulos não são linhas da playlist e nunca podem virar
  reprodução direta: abrem a ficha "onde assistir" e RETURN volta ao card.
*/
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
    while (match) { files.push(match[1]); match = pattern.exec(html); }
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
            if (Date.now() - started > timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
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
        language: 'pt-BR', languageSelected: true, acceptedLegal: true, tmdbRegion: 'BR'
    }));
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
        keymanager: {
            getDataAliasList: function () { return []; },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) {
                if (!secureData[alias.name]) { throw { name: 'NotFoundError' }; }
                return secureData[alias.name];
            },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: {
            getCurrentApplication: function () { return { exit: function () {} }; },
            launchAppControl: function (control, id, success) { if (success) { success(); } }
        }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    return window;
}

function activate(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
}

function seed(window) {
    var source = { id: 'src', name: 'Fonte sintética', type: 'XTREAM', channelCount: 18, createdAt: 1 };
    var items = [];
    var index;
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [
        { id: 'local-netflix', sourceId: 'src', name: 'Filmes | Netflix', contentType: 'MOVIE', sortOrder: 0 }
    ];
    for (index = 0; index < 18; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: 'src', providerItemId: 'local-' + index, name: 'Local ' + index,
            categoryId: 'local-netflix', contentType: 'MOVIE', rating: 7, year: 2018,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'local-' + index }
        }));
    }
    return new Promise(function (resolve, reject) {
        window.BuroStorage.putBatch('items', items, resolve, reject);
    });
}

function publicShelves() {
    var netflixTitles = [
        { tmdbId: 100, title: 'Filme público', year: 2026, isSeries: false,
            posterUrl: 'https://image.tmdb.org/t/p/w342/publico.jpg' },
        /* O mesmo id não pode quebrar a Home nem desenhar duas cartas. */
        { tmdbId: 100, title: 'Filme público duplicado', year: 2026, isSeries: false,
            posterUrl: 'https://image.tmdb.org/t/p/w342/publico.jpg' }
    ];
    var id;
    for (id = 101; id <= 111; id += 1) {
        netflixTitles.push({
            tmdbId: id, title: 'Filme público ' + id, year: 2026, isSeries: false,
            posterUrl: 'https://image.tmdb.org/t/p/w342/publico-' + id + '.jpg'
        });
    }
    return [
        {
            providerId: '8', providerName: 'Netflix',
            providerLogoUrl: 'https://image.tmdb.org/t/p/w92/netflix.png',
            titles: netflixTitles
        },
        {
            providerId: '1899', providerName: 'HBO', providerLogoUrl: 'javascript:alert(1)',
            titles: [{ tmdbId: 200, title: 'Série pública', year: 2025, isSeries: true,
                posterUrl: 'javascript:alert(2)' }]
        }
    ];
}

async function run() {
    var window = loadApp();
    var loadCalls = 0;
    var selectionCalls = 0;
    var cacheWrites = 0;
    var finishShelves;
    var keys;

    await waitFor(function () { return Boolean(window.document.querySelector('[data-action="profile-form"]')); }, 8000);
    activate(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 8000);
    window.document.getElementById('profile-name').value = 'Casa';
    activate(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 8000);
    await seed(window);
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'home' && data.loading === false;
    }, 8000);

    process.stdout.write('A capability continua honesta\n');
    check('sem chave TMDb a Home não mostra catálogo externo',
        window.document.querySelectorAll('[data-home-rail^="streaming-"]').length === 0);

    window.BuroTmdb.keyForProfile = function () { return 'synthetic-key'; };
    window.BuroTmdb.readShelfCache = function () { return null; };
    window.BuroTmdb.writeShelfCache = function () { cacheWrites += 1; return true; };
    window.BuroTmdb.loadShelves = function (key, region, kind, language, progress, success) {
        loadCalls += 1;
        check('a Home pede o mesmo catálogo MOVIES regional da área Assinaturas',
            key === 'synthetic-key' && region === 'BR' && kind === 'MOVIES' && language === 'pt-BR');
        finishShelves = success;
        return { abort: function () {} };
    };
    window.BuroTmdb.loadSubscriptionTitle = function (key, title, region, language, success) {
        selectionCalls += 1;
        window.setTimeout(function () {
            success({ details: { title: title.title, plot: 'Detalhes públicos' }, offers: [], unknown: true });
        }, 0);
        return { abort: function () {} };
    };

    window.BuroApp.render();
    check('a Home local continua pronta enquanto a consulta pública está em andamento',
        Boolean(window.document.querySelector('.real-home-hero')) &&
        window.document.querySelectorAll('[data-home-rail^="streaming-"]').length === 0);
    check('uma única consulta pública foi iniciada', loadCalls === 1 && typeof finishShelves === 'function');
    finishShelves(publicShelves());
    await waitFor(function () {
        return window.document.querySelectorAll('[data-home-rail^="streaming-"]').length === 2;
    }, 8000);

    process.stdout.write('As prateleiras públicas encerram a Home\n');
    keys = Array.prototype.slice.call(window.document.querySelectorAll('[data-home-rail]'))
        .map(function (rail) { return rail.getAttribute('data-home-rail'); });
    check('Netflix e HBO ganham prateleiras públicas distintas',
        keys.indexOf('streaming-8') >= 0 && keys.indexOf('streaming-1899') >= 0);
    check('elas ficam depois do catálogo local, como nas referências',
        keys.indexOf('streaming-8') > keys.indexOf('service-Netflix'));
    check('a marca segura aparece só no cabeçalho e a hostil é descartada',
        window.document.querySelector('[data-home-rail="streaming-8"] .subscription-provider-logo') &&
        !window.document.querySelector('[data-home-rail="streaming-1899"] img'));
    check('id externo repetido é removido e o trilho respeita o teto de doze cards',
        window.document.querySelectorAll('[data-home-rail="streaming-8"] [data-action="home-subscription-title"]').length === 12);
    check('o card externo não finge ser reprodução local',
        !window.document.querySelector('[data-home-rail^="streaming-"] [data-action="play"], ' +
            '[data-home-rail^="streaming-"] [data-action="movie-details"]'));
    check('o resultado válido foi escrito no cache público já saneado pelo cliente', cacheWrites === 1);

    process.stdout.write('Abrir e voltar conserva a origem Home\n');
    activate(window, '[data-action="home-subscription-title"][data-key="movie:100"]');
    await waitFor(function () {
        return window.BuroApp.state.section === 'SUBSCRIPTIONS' &&
            window.BuroApp.state.screenData && !window.BuroApp.state.screenData.selectionLoading;
    }, 8000);
    check('o card abre a ficha onde assistir, não o player',
        selectionCalls === 1 && window.BuroApp.state.screenData.selected.title === 'Filme público' &&
        Boolean(window.document.querySelector('.subscription-detail')));
    activate(window, '[data-action="subscription-back"]');
    await waitFor(function () { return window.BuroApp.state.section === 'HOME'; }, 8000);
    check('RETURN lógico volta à Home e ao mesmo card',
        window.BuroApp.state.screenData.kind === 'home' &&
        window.document.querySelector('[data-action="home-subscription-title"][data-key="movie:100"]')
            .classList.contains('focused'));

    process.stdout.write('Firmware antigo ainda revela o fim da linha\n');
    window.HTMLElement.prototype.scrollIntoView = function () {};
    window.HTMLElement.prototype.getBoundingClientRect = function () {
        var key = this.getAttribute && this.getAttribute('data-key');
        if (this.classList && this.classList.contains('subscription-row')) {
            return { left: 64, right: 1660, top: 300, bottom: 630, width: 1596, height: 330 };
        }
        if (key === 'movie:111') {
            return { left: 1840, right: 2000, top: 310, bottom: 610, width: 160, height: 300 };
        }
        return { left: 100, right: 260, top: 310, bottom: 610, width: 160, height: 300 };
    };
    activate(window, '[data-action="home-subscription-title"][data-key="movie:111"]');
    await waitFor(function () {
        return window.BuroApp.state.section === 'SUBSCRIPTIONS' &&
            window.BuroApp.state.screenData && !window.BuroApp.state.screenData.selectionLoading;
    }, 8000);
    activate(window, '[data-action="subscription-back"]');
    await waitFor(function () { return window.BuroApp.state.section === 'HOME'; }, 8000);
    check('o card focado fora da largura desloca explicitamente sua própria prateleira',
        window.document.querySelector('[data-home-rail="streaming-8"] .subscription-row').scrollLeft > 0 &&
        window.document.querySelector('[data-key="movie:111"]').classList.contains('focused'));
    window.BuroApp.render();
    check('re-renderizar a Home não repete a consulta', loadCalls === 1);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
