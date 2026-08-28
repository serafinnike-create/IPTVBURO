/*
  A grade de Ao Vivo, na forma que o material tem.

  Os canais saiam como posteres 2:3 — o formato de cartaz de cinema. O que o
  provedor manda e um logo quadrado ou horizontal, entao cada cartao tinha a
  marca boiando no meio de um retangulo alto e vazio, e o canal sem logo virava
  um cartao alto so com texto.

  E todos diziam "IPTV BURO" na segunda linha: o nome da fonte, que nao distingue
  nada quando ha uma fonte so, e vira ruido mesmo quando ha varias.

  O que este teste guarda alem do formato: que filmes e series **nao** mudaram —
  eles tem capa 2:3 de verdade, e o poster e o certo para eles.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
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

function call(window, method, args) {
    return new Promise(function (resolve, reject) {
        method.apply(window.BuroStorage, args.concat([resolve, reject]));
    });
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match;
    var secureData = {};
    var dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/' });
    var window = dom.window;
    while ((match = pattern.exec(html)) !== null) { scripts.push(match[1]); }
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return Object.keys(secureData).map(function (name) { return { name: name }; }); },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) { return secureData[alias.name]; },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    scripts.forEach(function (script) { window.eval(fs.readFileSync(path.join(APP_DIR, script), 'utf8')); });
    window.BuroApp.init();
    return window;
}

async function run() {
    var window = loadApp();
    var source = { id: 's', name: 'IPTV BURO', type: 'XTREAM', channelCount: 4, createdAt: 1, updatedAt: null };
    var liveCategory = { id: 'cl', sourceId: 's', providerCategoryId: 'l', name: 'Canais',
        contentType: 'LIVE', sortOrder: 0 };
    var movieCategory = { id: 'cm', sourceId: 's', providerCategoryId: 'm', name: 'Filmes',
        contentType: 'MOVIE', sortOrder: 1 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var items = [];
    var card;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    /* Um canal com logo e um sem — o segundo e o que virava um retangulo alto
       so com texto. */
    items.push(window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'l1', name: 'Globo TV Morena HD', categoryId: 'cl',
        contentType: 'LIVE', sortOrder: 0, logoUrl: 'https://art.test/globo.png',
        locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: 'l1', extension: 'ts' }
    }));
    items.push(window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'l2', name: 'Brasileirao Serie A FHD', categoryId: 'cl',
        contentType: 'LIVE', sortOrder: 1,
        locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: 'l2', extension: 'ts' }
    }));
    items.push(window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'm1', name: 'Um filme', categoryId: 'cm',
        contentType: 'MOVIE', sortOrder: 0, year: 2024, rating: 8,
        logoUrl: 'https://art.test/filme.jpg',
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm1' }
    }));
    await call(window, window.BuroStorage.replaceSourceCatalogue,
        [source, [liveCategory, movieCategory], items, true]);

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [liveCategory, movieCategory];
    window.BuroApp.state.items = items;
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';

    process.stdout.write('Ao Vivo abre no formato do material\n');
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);
    check('a grade de canais e compacta, e nao de posteres',
        Boolean(window.document.querySelector('.catalogue-layout-compact')) &&
        !window.document.querySelector('.card-row.catalogue-layout-poster'));
    /*
      O nome da fonte saiu da segunda linha. Ele nao distingue nada quando ha
      uma fonte so, e num canal nao ha ano nem nota para por no lugar.
    */
    card = window.document.querySelector('.media-card[data-action="live-details"]');
    check('o cartao de canal nao repete o nome da fonte',
        card && card.textContent.indexOf('IPTV BURO') < 0);
    check('mas mostra o nome do canal',
        card && card.textContent.indexOf('Globo TV Morena HD') >= 0);

    process.stdout.write('Filmes continuam em poster, que e a forma da capa deles\n');
    /*
      A correcao nao pode ter arrastado o resto: um filme tem capa 2:3 de
      verdade, e o poster e o certo para ela.
    */
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);
    check('a grade de filmes continua em poster',
        Boolean(window.document.querySelector('.catalogue-layout-poster')));
    check('e o filme continua mostrando ano e nota',
        window.document.body.textContent.indexOf('2024') >= 0);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suite da grade de Ao Vivo: ' + error.stack + '\n');
    process.exit(1);
});
