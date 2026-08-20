/*
  As prateleiras por serviço na Home.

  O Windows mostra "tudo na Netflix" como uma fileira própria. Este teste
  verifica que a TV monta as mesmas, a partir das categorias que a lista nomeia,
  e que os títulos delas entram na lista que a hidratação de arte percorre — sem
  isso as prateleiras apareceriam sem capa.
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
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
        keymanager: {
            getDataAliasList: function () { return []; },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function () { throw { name: 'NotFoundError' }; },
            removeData: function () {}
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

/*
  Uma lista sintética que arquiva por serviço: duas categorias nomeando Netflix
  e HBO, mais uma de gênero para que o teste veja as duas coisas conviverem.
*/
function seed(window) {
    var source = { id: 'src', name: 'Fonte sintética', type: 'XTREAM', channelCount: 54, createdAt: 1, updatedAt: null };
    var items = [];
    var index;
    function movie(prefix, categoryId, rating, year) {
        return window.BuroDomain.createItem({
            sourceId: 'src', providerItemId: prefix, name: 'Filme ' + prefix,
            categoryId: categoryId, contentType: 'MOVIE', rating: rating, year: year,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: prefix }
        });
    }
    function series(id, name, order) {
        return window.BuroDomain.createItem({
            id: id, sourceId: 'src', providerItemId: id, name: name,
            categoryId: 'cs', contentType: 'SERIES', rating: 8, year: 2020, sortOrder: order,
            locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: id }
        });
    }
    function episode(id, parentId, name, number) {
        return window.BuroDomain.createItem({
            id: id, sourceId: 'src', providerItemId: id, name: name,
            categoryId: parentId, contentType: 'EPISODE',
            locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: id, season: 1, episode: number }
        });
    }
    var parentA = series('series:home-parent-a', 'Série Alfa', 1);
    var parentB = series('series:home-parent-b', 'Série Beta', 2);
    var episodeAOld = episode('episode:home-a-old', parentA.id, 'Alfa anterior', 1);
    var episodeANew = episode('episode:home-a-new', parentA.id, 'Alfa mais recente', 2);
    var episodeB = episode('episode:home-b', parentB.id, 'Beta recente', 1);
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [
        { id: 'c1', sourceId: 'src', providerCategoryId: '1', name: 'Filmes | Netflix', contentType: 'MOVIE', sortOrder: 0 },
        { id: 'c2', sourceId: 'src', providerCategoryId: '2', name: 'Filmes | HBO Max', contentType: 'MOVIE', sortOrder: 1 },
        { id: 'c3', sourceId: 'src', providerCategoryId: '3', name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 2 },
        { id: 'cs', sourceId: 'src', providerCategoryId: '4', name: 'Séries', contentType: 'SERIES', sortOrder: 3 }
    ];
    for (index = 0; index < 18; index += 1) {
        items.push(movie('n' + index, 'c1', 8, 2019));
        items.push(movie('h' + index, 'c2', 7, 2018));
        items.push(movie('a' + index, 'c3', 6, 2017));
    }
    items.push(parentA, parentB, episodeAOld, episodeANew, episodeB);
    window.__homeEpisodeFixtures = {
        parentA: parentA, parentB: parentB, episodeAOld: episodeAOld,
        episodeANew: episodeANew, episodeB: episodeB
    };
    return new Promise(function (resolve, reject) {
        window.BuroStorage.putBatch('items', items, resolve, reject);
    });
}

function railKeys(window) {
    return Array.prototype.slice.call(
        window.document.querySelectorAll('.home-rail[data-home-rail]')
    ).map(function (rail) { return rail.getAttribute('data-home-rail'); });
}

async function run() {
    var window = loadApp();
    var keys;
    var netflixRail;

    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000).catch(function () {
        throw new Error('onboarding não chegou a Perfis; screen=' + window.BuroApp.state.screen +
            '; texto=' + window.document.body.textContent.replace(/\s+/g, ' ').trim().substring(0, 180));
    });
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
        return data && data.kind === 'home' && data.result && data.loading === false;
    }, 8000);

    process.stdout.write('A varredura junta os títulos por serviço\n');
    (function () {
        var result = window.BuroApp.state.screenData.result;
        check('cada serviço nomeado pela lista ganha um balde',
            Object.keys(result.byService).sort().join(',') === 'HBO,Netflix');
        check('a categoria de gênero não vira serviço',
            !Object.prototype.hasOwnProperty.call(result.byService, 'Ação'));
        check('os títulos do serviço são os daquela categoria',
            result.byService.Netflix.length === 18 &&
            result.byService.Netflix.every(function (item) { return item.categoryId === 'c1'; }));
    }());

    process.stdout.write('A Home desenha uma prateleira por serviço\n');
    keys = railKeys(window);
    check('a prateleira da Netflix existe', keys.indexOf('service-Netflix') >= 0);
    check('a prateleira da HBO existe', keys.indexOf('service-HBO') >= 0);
    check('elas ficam depois das editoriais, não antes',
        keys.indexOf('service-Netflix') > keys.indexOf('top-rated') &&
        keys.indexOf('service-HBO') > keys.indexOf('top-rated'));
    check('cada prateleira de serviço carrega a marca do serviço',
        window.document.querySelectorAll('.home-rail-heading .provider-badge').length >= 2);

    process.stdout.write('Os títulos delas entram na hidratação de arte\n');
    (function () {
        var result = window.BuroApp.state.screenData.result;
        var hydrated = {};
        var covered;
        window.BuroApp.state.items.forEach(function (item) { hydrated[item.id] = true; });
        covered = result.byService.Netflix.every(function (item) { return hydrated[item.id]; });
        check('todo título de prateleira de serviço está entre os itens conhecidos', covered);
    }());

    process.stdout.write('Um título não aparece em duas prateleiras\n');
    netflixRail = window.BuroApp.state.screenData.result.byService.Netflix;
    check('a prateleira tem itens', netflixRail.length > 0);
    (function () {
        var seen = {};
        var duplicated = false;
        Array.prototype.slice.call(window.document.querySelectorAll('.home-rail [data-id]')).forEach(function (card) {
            var id = card.getAttribute('data-id');
            if (seen[id]) { duplicated = true; }
            seen[id] = true;
        });
        check('nenhum título é desenhado duas vezes na Home', !duplicated);
    }());

    process.stdout.write('A Home representa progresso de episódio pela série\n');
    (function () {
        var fixture = window.__homeEpisodeFixtures;
        var profileId = window.BuroApp.state.activeProfile.id;
        var continueCards;
        var alpha;
        [fixture.episodeAOld, fixture.episodeANew, fixture.episodeB].forEach(function (item) {
            if (!window.BuroApp.state.items.some(function (known) { return known.id === item.id; })) {
                window.BuroApp.state.items.push(item);
            }
        });
        window.BuroApp.state.progress = [
            { id: 'progress:a-old', profileId: profileId, itemId: fixture.episodeAOld.id,
                positionMs: 25000, durationMs: 100000, completed: false, updatedAt: 1000 },
            { id: 'progress:a-new', profileId: profileId, itemId: fixture.episodeANew.id,
                positionMs: 50000, durationMs: 100000, completed: false, updatedAt: 3000 },
            { id: 'progress:b', profileId: profileId, itemId: fixture.episodeB.id,
                positionMs: 40000, durationMs: 100000, completed: false, updatedAt: 2000 }
        ];
        window.BuroApp.render();
        continueCards = Array.prototype.slice.call(
            window.document.querySelectorAll('.home-rail[data-home-rail="continue"] .media-card')
        );
        alpha = continueCards[0];
        check('episódios da mesma série viram um único card da série mais recente',
            continueCards.length === 2 &&
            continueCards.map(function (card) { return card.getAttribute('data-id'); }).join(',') ===
                fixture.parentA.id + ',' + fixture.parentB.id &&
            continueCards.every(function (card) { return card.getAttribute('data-action') === 'series-details'; }));
        check('a barra usa o episódio incompleto mais recente sem alterar a série persistida',
            alpha && alpha.querySelector('.media-progress i').getAttribute('style').indexOf('50.00%') >= 0 &&
            typeof fixture.parentA._libraryProgressItemId === 'undefined');

        window.BuroApp.state.progress[1].completed = true;
        window.BuroApp.render();
        continueCards = Array.prototype.slice.call(
            window.document.querySelectorAll('.home-rail[data-home-rail="continue"] .media-card')
        );
        check('concluir o episódio mais recente revela o anterior e reordena as séries',
            continueCards.length === 2 &&
            continueCards.map(function (card) { return card.getAttribute('data-id'); }).join(',') ===
                fixture.parentB.id + ',' + fixture.parentA.id &&
            continueCards[1].querySelector('.media-progress i').getAttribute('style').indexOf('25.00%') >= 0);

        window.BuroApp.state.progress[0].completed = true;
        window.BuroApp.render();
        continueCards = Array.prototype.slice.call(
            window.document.querySelectorAll('.home-rail[data-home-rail="continue"] .media-card')
        );
        check('a série sai de Continuar quando não resta episódio incompleto',
            continueCards.length === 1 && continueCards[0].getAttribute('data-id') === fixture.parentB.id);
    }());

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
