/* O painel de armazenamento. Mede o catálogo gravado e é a única via para apagá-lo. */
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
    if (preferences) {
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences));
    }
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
    window.__secureData = secureData;
    return window;
}

function click(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
    return element;
}

function put(window, storeName, rows) {
    return new Promise(function (resolve, reject) {
        window.BuroStorage.putBatch(storeName, rows, resolve, reject);
    });
}

/* Um catálogo sintético: uma fonte, duas categorias, três títulos. */
async function seedCatalogue(window) {
    await put(window, 'sources', [{
        id: 'source-synthetic', name: 'Fonte sintética', type: 'REMOTE_M3U',
        channelCount: 3, createdAt: 1, updatedAt: null
    }]);
    await put(window, 'categories', [
        { id: 'cat-1', sourceId: 'source-synthetic', providerCategoryId: '1', name: 'Notícias', contentType: 'LIVE', sortOrder: 0 },
        { id: 'cat-2', sourceId: 'source-synthetic', providerCategoryId: '2', name: 'Filmes', contentType: 'MOVIE', sortOrder: 1 }
    ]);
    await put(window, 'items', [1, 2, 3].map(function (index) {
        return window.BuroDomain.createItem({
            sourceId: 'source-synthetic', providerItemId: String(index),
            name: 'Título ' + index, categoryId: index === 1 ? 'cat-1' : 'cat-2',
            contentType: index === 1 ? 'LIVE' : 'MOVIE',
            locator: { kind: 'm3u', contentType: index === 1 ? 'LIVE' : 'MOVIE', providerItemId: String(index) }
        });
    }));
}

async function openStorageSettings(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    click(window, '[data-action="legal-accept"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 6000);
    click(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 6000);
    window.document.getElementById('profile-name').value = 'Casa';
    click(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 6000);
    click(window, '.nav-list [data-action="section"][data-section="SETTINGS"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="storage-settings"]'));
    }, 6000);
    click(window, '[data-action="storage-settings"]');
    await waitFor(function () { return window.BuroApp.state.screen === 'STORAGE_SETTINGS'; }, 6000);
}

async function run() {
    var window;
    var counted;
    var rows;

    process.stdout.write('O painel conta sem carregar o catálogo\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    await seedCatalogue(window);
    counted = undefined;
    window.BuroStorage.count('items', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('count devolve o número de registros gravados', counted === 3);
    counted = undefined;
    window.BuroStorage.count('categories', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('count funciona em qualquer store', counted === 2);
    window.close();

    process.stdout.write('O painel aparece nas configurações e mede o que existe\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    await seedCatalogue(window);
    await openStorageSettings(window);
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.counts;
    }, 6000);
    check('o painel abre a partir das configurações',
        window.BuroApp.state.screen === 'STORAGE_SETTINGS');
    check('a contagem medida é a do catálogo gravado',
        window.BuroApp.state.screenData.counts.items === 3 &&
        window.BuroApp.state.screenData.counts.categories === 2);
    rows = window.document.querySelectorAll('.storage-row');
    check('há uma linha para catálogo, capas e downloads', rows.length === 3);
    check('a linha do catálogo mostra títulos e categorias',
        rows[0].textContent.indexOf('3') >= 0 && rows[0].textContent.indexOf('2') >= 0);

    process.stdout.write('A tela diz a verdade sobre onde as capas ficam\n');
    /*
      O texto acompanhou o que o aplicativo passou a fazer.

      Ele dizia que a capa nao era gravada e ficava so em memoria — verdade
      ate a capa passar a ser guardada junto com o titulo, e ate existir a
      copia opcional no pendrive. Um texto que descreve o comportamento antigo
      e pior do que nenhum: manda a pessoa procurar a explicacao errada quando
      algo nao aparece.
    */
    check('o texto das capas descreve onde elas ficam de verdade',
        rows[1].textContent.indexOf('gravada') >= 0 &&
        rows[1].textContent.indexOf('pendrive') >= 0);
    check('o texto dos downloads aponta o USB, não a TV',
        rows[2].textContent.indexOf('USB') >= 0);

    process.stdout.write('Cada linha reserva espaço para o rótulo e para o número\n');
    check('o rótulo pode encolher e o número não',
        Boolean(window.document.querySelector('.storage-row > div')) &&
        Boolean(window.document.querySelector('.storage-row strong')));

    process.stdout.write('Limpar exige confirmação e preserva o que não é recuperável\n');
    click(window, '[data-action="storage-clear"]');
    check('o primeiro toque pede confirmação em vez de apagar',
        window.BuroApp.state.screenData.confirmClear === true);
    counted = undefined;
    window.BuroStorage.count('items', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('nada foi apagado antes da confirmação', counted === 3);

    click(window, '[data-action="storage-clear"]');
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.messageKey === 'storageCleared';
    }, 6000);
    counted = undefined;
    window.BuroStorage.count('items', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('o catálogo gravado foi apagado', counted === 0);
    counted = undefined;
    window.BuroStorage.count('categories', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('as categorias foram apagadas junto', counted === 0);
    counted = undefined;
    window.BuroStorage.count('sources', function (value) { counted = value; }, function () { counted = -1; });
    await waitFor(function () { return counted !== undefined; }, 4000);
    check('a fonte continua: ela não pode ser buscada de novo sozinha', counted === 1);
    check('o perfil continua', window.BuroApp.state.profiles.length === 1);
    check('o estado em memória acompanhou o que saiu do disco',
        window.BuroApp.state.items.length === 0 && window.BuroApp.state.categories.length === 0);
    check('nenhuma capa de item apagado sobrou em memória',
        window.BuroApp._cacheSizes().artwork === 0 &&
        window.BuroApp._cacheSizes().artworkOrder === 0 &&
        window.BuroApp._cacheSizes().detailBackdrop === 0 &&
        window.BuroApp._cacheSizes().detailBackdropOrder === 0);
    window.close();

    process.stdout.write('O painel existe nos cinco idiomas\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    check('cada idioma tem os textos do armazenamento',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['storageTitle', 'storageHint', 'storageCatalogue', 'storageArtworkHint',
                'storageDownloadsHint', 'storageClearHint', 'storageCleared'].every(function (key) {
                var value = window.BuroI18n.t(key);
                return Boolean(value) && value !== key;
            });
        }));
    window.BuroI18n.setLanguage('pt-BR');
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
