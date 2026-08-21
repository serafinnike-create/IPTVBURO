/*
  As capas da prateleira de catálogo.

  A arte chega depois do desenho: o catálogo é lido do banco, a prateleira
  aparece, e só então as capas são pedidas ao provedor. Sem alguém redesenhando
  quando elas chegam, ficam guardadas em memória e nunca aparecem — que foi
  exatamente o defeito visto na TV, com cartões de texto e nenhuma capa.
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

/* Uma fonte Xtream com uma categoria e três filmes gravados. */
function seed(window, artwork) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 3, createdAt: 1, updatedAt: null };
    var items = [1, 2, 3].map(function (index) {
        var item = window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: String(index), name: 'Filme ' + index,
            categoryId: 'c1', contentType: 'MOVIE', year: 2024, rating: 8,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: String(index) }
        });
        item.id = 'item-' + index;
        return item;
    });
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [{
        id: 'c1', sourceId: 's1', providerCategoryId: '1',
        name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 0
    }];
    /* Sem segredo, `hydrateCategoryArtwork` desiste antes de pedir. */
    return new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, function () {
            window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success) {
                success([], artwork);
            };
            window.BuroStorage.putBatch('items', items, resolve, reject);
        }, reject);
    });
}

async function reachShell(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000);
    activate(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 8000);
    window.document.getElementById('profile-name').value = 'Casa';
    activate(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 8000);
}

async function run() {
    var window;

    process.stdout.write('A capa aparece depois de chegar\n');
    window = loadApp();
    await reachShell(window);
    await seed(window, {
        'item-1': 'https://art.test/um.jpg',
        'item-2': 'https://art.test/dois.jpg',
        'item-3': 'https://art.test/tres.jpg'
    });
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);
    check('a prateleira desenha os títulos que o banco tem',
        window.document.querySelectorAll('.media-card').length === 3);

    /*
      O ponto do teste: a arte chega depois, e a tela tem de se redesenhar
      sozinha. Sem isso ela fica guardada em memória e nunca vira imagem.
    */
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card img').length === 3;
    }, 8000);
    check('cada cartão ganha a sua capa quando ela chega',
        window.document.querySelectorAll('.media-card img').length === 3);
    check('a capa é a que o provedor mandou para aquele título',
        window.document.querySelector('.media-card img').getAttribute('src') === 'https://art.test/um.jpg');
    check('o cartão é marcado como tendo arte, para o CSS diferenciá-lo',
        window.document.querySelectorAll('.media-card.has-art').length === 3);

    process.stdout.write('Sem arte, o cartão continua legível\n');
    window.close();
    window = loadApp();
    await reachShell(window);
    await seed(window, {});
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 3;
    }, 8000);
    check('um provedor sem arte não deixa a prateleira vazia',
        window.document.querySelectorAll('.media-card').length === 3);
    check('e o cartão não finge ter imagem',
        window.document.querySelectorAll('.media-card img').length === 0 &&
        window.document.querySelectorAll('.media-card.has-art').length === 0);
    check('o título continua sendo lido no cartão',
        window.document.querySelector('.media-card h3').textContent.indexOf('Filme') === 0);
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
    process.exitCode = 1;
});
