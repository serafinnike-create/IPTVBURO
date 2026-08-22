/*
  Onde cada seletor abre, em Filmes, Séries e Ao Vivo.

  A lista era acrescentada depois das duas barras, num bloco só: clicar em
  "Nota", à direita, abria a janela debaixo de "Gênero", à esquerda. Numa TV
  isso se lê como o seletor errado ter aberto — foi exatamente o relato,
  "seletor nao abre onde eu click".

  Uma janela por vez, ancorada no chip que a abriu, nas três abas.
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

function activate(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
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

/* Uma fonte com categorias de filme, série e canal. */
function seed(window) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 3, createdAt: 1, updatedAt: null };
    var items = [
        { id: 'm1', type: 'MOVIE', cat: 'c1' },
        { id: 's1i', type: 'SERIES', cat: 'c2' },
        { id: 'l1', type: 'LIVE', cat: 'c3' }
    ].map(function (row) {
        var item = window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: row.id, name: 'Título ' + row.id,
            categoryId: row.cat, contentType: row.type, year: 2024, rating: 8,
            locator: { kind: 'xtream', contentType: row.type, providerItemId: row.id }
        });
        item.id = row.id;
        return item;
    });
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [
        { id: 'c1', sourceId: 's1', providerCategoryId: '1', name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 0 },
        { id: 'c2', sourceId: 's1', providerCategoryId: '2', name: 'Series | Drama', contentType: 'SERIES', sortOrder: 0 },
        { id: 'c3', sourceId: 's1', providerCategoryId: '3', name: 'Canais | Esportes', contentType: 'LIVE', sortOrder: 0 }
    ];
    return new Promise(function (resolve, reject) {
        window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success) {
            success([], {});
        };
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, function () {
            window.BuroStorage.putBatch('items', items, resolve, reject);
        }, reject);
    });
}

async function openSection(window, section) {
    window.BuroApp.state.section = section;
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-scope-bar'));
    }, 8000);
}

/* O chip que abriu a lista, a partir da própria lista. */
function chipOwning(window) {
    var options = window.document.querySelector('.catalogue-options');
    var slot = options && options.closest('.picker-slot');
    var chip = slot && slot.querySelector('.scope-chip');
    return chip ? (chip.getAttribute('data-action') || '') : '(sem chip)';
}

async function run() {
    var window;

    process.stdout.write('A lista abre dentro do chip que foi clicado\n');
    window = loadApp();
    await reachShell(window);
    await seed(window);
    await openSection(window, 'MOVIES');

    /*
      O ponto do teste. "Nota" é o último chip da barra de cima; a janela dele
      abria debaixo de "Gênero", na outra barra.
    */
    activate(window, '[data-action="catalogue-pick-rating"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-options'));
    }, 8000);
    check('Nota abre a lista dentro do próprio chip de Nota',
        chipOwning(window) === 'catalogue-pick-rating');

    activate(window, '[data-action="catalogue-pick-genre"]');
    await waitFor(function () {
        return chipOwning(window) === 'catalogue-pick-genre';
    }, 8000);
    check('Gênero abre a lista dentro do chip de Gênero',
        chipOwning(window) === 'catalogue-pick-genre');
    check('e a lista anterior fechou: uma janela por vez',
        window.document.querySelectorAll('.catalogue-options').length === 1);

    activate(window, '[data-action="catalogue-pick-year"]');
    await waitFor(function () {
        return chipOwning(window) === 'catalogue-pick-year';
    }, 8000);
    check('Escolher ano abre a lista dentro do chip de ano',
        chipOwning(window) === 'catalogue-pick-year');

    /* Clicar de novo no mesmo chip fecha, que é como um menu se comporta. */
    activate(window, '[data-action="catalogue-pick-year"]');
    await waitFor(function () {
        return !window.document.querySelector('.catalogue-options');
    }, 8000);
    check('o mesmo chip de novo fecha a lista',
        !window.document.querySelector('.catalogue-options'));

    process.stdout.write('Vale igual em Séries e em Ao Vivo\n');
    await openSection(window, 'SERIES');
    activate(window, '[data-action="catalogue-pick-genre"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-options'));
    }, 8000);
    check('em Séries a lista também nasce no chip',
        chipOwning(window) === 'catalogue-pick-genre');

    await openSection(window, 'LIVE');
    activate(window, '[data-action="catalogue-pick-genre"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-options'));
    }, 8000);
    check('em Ao Vivo a lista também nasce no chip',
        chipOwning(window) === 'catalogue-pick-genre');
    /* Ao Vivo não tem ano nem nota: um canal não tem lançamento nem estrelas. */
    check('e Ao Vivo não oferece ano nem nota, que não filtram canal',
        !window.document.querySelector('[data-action="catalogue-pick-year"]') &&
        !window.document.querySelector('[data-action="catalogue-pick-rating"]'));

    process.stdout.write('A lista diz quando há mais do que cabe\n');
    await openSection(window, 'MOVIES');
    activate(window, '[data-action="catalogue-pick-year"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-options'));
    }, 8000);
    check('poucas opções não ganham contagem, que só faria ruído',
        window.document.querySelectorAll('[data-picker="year"]').length > 6 ||
        !window.document.querySelector('.options-count'));

    activate(window, '[data-action="catalogue-pick-rating"]');
    await waitFor(function () {
        return chipOwning(window) === 'catalogue-pick-rating';
    }, 8000);
    /* Seis notas cabem na janela; a contagem não deve aparecer aqui. */
    check('a lista de notas cabe inteira e não anuncia rolagem',
        !window.document.querySelector('.options-count'));

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

run().then(function () {
    process.exit(process.exitCode || 0);
}).catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
