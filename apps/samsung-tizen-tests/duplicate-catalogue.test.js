/* Paridade com o Windows: uma so ficha por filme, com a lista bruta opcional. */
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

function loadApp(preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences || {
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
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
    return window;
}

function movie(window, id, name, year) {
    return window.BuroDomain.createItem({
        id: id, sourceId: 'source-duplicates', categoryId: 'category-duplicates',
        providerItemId: id, contentType: 'MOVIE', name: name, year: year,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: id }
    });
}

function openCategory(window, items) {
    var state = window.BuroApp.state;
    state.screen = 'SHELL';
    state.section = 'MOVIES';
    state.screenData = {
        kind: 'category', contentType: 'MOVIE',
        category: {
            id: 'category-duplicates', sourceId: 'source-duplicates',
            contentType: 'MOVIE', name: 'Filmes'
        },
        items: items, cataloguePage: 0
    };
    window.BuroApp.render();
}

function key(window, keyCode) {
    window.BuroApp._onKeyDown({ keyCode: keyCode, preventDefault: function () {} });
}

function report() {
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

async function run() {
    var window = loadApp();
    var state;
    var rows;
    var control;
    var persisted;

    await waitFor(function () { return window.BuroApp.state.ready; }, 6000);
    state = window.BuroApp.state;

    process.stdout.write('A identidade de prateleira segue o contrato do Windows\n');
    check('qualidade, codec, ano, idioma e prefixo de servico sao decoracao',
        window.BuroDomain.shelfDeduplicationKey('NETFLIX | Divida de Honra [L1] HEVC (2024)') ===
        window.BuroDomain.shelfDeduplicationKey('Divida de Honra [L2] HD'));
    check('a marcacao remaster segue a mesma normalizacao compartilhada',
        window.BuroDomain.shelfDeduplicationKey('Alien Remaster') ===
        window.BuroDomain.shelfDeduplicationKey('Alien HD'));
    check('numeros de sequencias continuam distinguindo filmes',
        window.BuroDomain.shelfDeduplicationKey('Enola Holmes 2 4K') !==
        window.BuroDomain.shelfDeduplicationKey('Enola Holmes 3 HD'));
    check('subtitulos reais nao sao apagados',
        window.BuroDomain.shelfDeduplicationKey('Duna') !==
        window.BuroDomain.shelfDeduplicationKey('Duna (Parte Dois)'));
    check('remakes homonimos de anos diferentes continuam sendo dois filmes',
        window.BuroDomain.collapseShelfDuplicates([
            movie(window, 'star-born-1937', 'A Star Is Born', 1937),
            movie(window, 'star-born-2018', 'A Star Is Born', 2018)
        ]).length === 2);

    rows = [
        movie(window, 'movie-a-4k', 'NETFLIX | Filme Exemplo [L1] 4K', 2024),
        movie(window, 'movie-a-hd', 'Filme Exemplo [L2] HD', 2024),
        movie(window, 'movie-b-2', 'Enola Holmes 2 4K', 2022),
        movie(window, 'movie-b-3', 'Enola Holmes 3 HD', 2023)
    ];

    process.stdout.write('O catalogo nasce limpo, sem perder sequencias distintas\n');
    check('agrupar copias e o padrao, como no Windows', state.preferences.collapseDuplicateTitles === true);
    openCategory(window, rows);
    check('a grade conserva o primeiro exemplar e as duas sequencias',
        window.document.querySelectorAll('.media-card').length === 3 &&
        Boolean(window.document.querySelector('[data-id="movie-a-4k"]')) &&
        !window.document.querySelector('[data-id="movie-a-hd"]') &&
        Boolean(window.document.querySelector('[data-id="movie-b-2"]')) &&
        Boolean(window.document.querySelector('[data-id="movie-b-3"]')));

    process.stdout.write('Configuracoes permite recuperar e restaurar a lista bruta\n');
    state.section = 'SETTINGS';
    state.screenData = null;
    window.BuroApp.render();
    control = window.document.querySelector('[data-action="collapse-duplicates"]');
    check('o controle e visivel, focavel e anuncia que esta ativo',
        control && control.classList.contains('focusable') && control.getAttribute('aria-pressed') === 'true');
    if (control) { window.BuroApp._activate(control); }
    persisted = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    check('desligar mostra o estado e persiste a escolha',
        state.preferences.collapseDuplicateTitles === false &&
        persisted.collapseDuplicateTitles === false &&
        window.document.querySelector('[data-action="collapse-duplicates"]').getAttribute('aria-pressed') === 'false');
    openCategory(window, rows);
    check('com a opcao desligada todas as copias voltam',
        window.document.querySelectorAll('.media-card').length === 4);

    state.section = 'SETTINGS';
    state.screenData = null;
    window.BuroApp.render();
    window.BuroApp._focusAction('collapse-duplicates');
    key(window, window.BuroKeys.CODES.ENTER);
    check('ENTER do controle remoto religa e persiste a preferencia',
        state.preferences.collapseDuplicateTitles === true &&
        JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1')).collapseDuplicateTitles === true);
    openCategory(window, rows);
    check('a alteracao feita pelo D-pad atualiza a grade',
        window.document.querySelectorAll('.media-card').length === 3);

    process.stdout.write('A prateleira principal usa a mesma regra sobre todo o IndexedDB\n');
    state.sources = [{
        id: 'source-duplicates', name: 'Fonte', type: 'XTREAM',
        channelCount: rows.length, createdAt: 1, updatedAt: null
    }];
    state.activeSource = state.sources[0];
    state.categories = [{
        id: 'category-duplicates', sourceId: 'source-duplicates',
        contentType: 'MOVIE', name: 'Filmes', sortOrder: 0
    }];
    await new Promise(function (resolve, reject) {
        window.BuroStorage.putBatch('items', rows, resolve, reject);
    });
    state.screen = 'SHELL';
    state.section = 'MOVIES';
    state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return !window.document.querySelector('.search-loading') &&
            window.document.querySelectorAll('.media-card').length > 0;
    }, 6000);
    check('a aba Filmes inteira tambem mostra tres titulos e total coerente',
        window.document.querySelectorAll('.media-card').length === 3 &&
        window.document.querySelector('.catalogue-shelf-heading p').textContent.indexOf('3') >= 0);

    process.stdout.write('Os cinco idiomas explicam o recurso\n');
    check('rotulo, explicacao e acao existem em todos os idiomas',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['duplicatesLabel', 'duplicatesHint', 'duplicatesToggle'].every(function (name) {
                var value = window.BuroI18n.t(name);
                return Boolean(value) && value !== name;
            });
        }));

    window.close();
    report();
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
