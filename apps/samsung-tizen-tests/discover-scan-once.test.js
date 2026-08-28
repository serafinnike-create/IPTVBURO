/*
  O Descobrir nao varre o catalogo a cada visita.

  Cada entrada na aba lia as 42.000 linhas inteiras — 460ms num PC, varios
  segundos numa TV. Sair para a Home e voltar refazia tudo para chegar a mesma
  leitura.

  O que se guarda e a **leitura**, e nao o baralho: `buildDiscoverDeck` continua
  rodando a cada visita, entao o gosto aprendido e as cartas ja vistas seguem
  mudando o que sai. Este teste guarda essa distincao — um cache que congelasse
  o baralho transformaria o Descobrir numa lista fixa.
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

function loadApp(onFold) {
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
    (function () {
        var realFold = window.BuroStorage.fold;
        window.BuroStorage.fold = function (storeName) {
            if (storeName === 'items') { onFold(); }
            return realFold.apply(window.BuroStorage, arguments);
        };
    }());
    window.BuroApp.init();
    return window;
}

function openDiscover(window) {
    window.BuroApp.state.section = 'DISCOVER';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
}

function leaveDiscover(window) {
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
}

async function run() {
    var folds = 0;
    var window = loadApp(function () { folds += 1; });
    var source = { id: 'source-discover', name: 'Fonte', type: 'XTREAM',
        channelCount: 30, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-discover', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-discover', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var index;
    var firstDeck;
    var afterReturn;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    for (index = 0; index < 30; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'm' + index, name: 'Filme ' + index,
            categoryId: category.id, contentType: 'MOVIE', sortOrder: index,
            year: 2024, rating: 7 + (index % 3), logoUrl: 'https://art.test/' + index + '.jpg',
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm' + index }
        }));
    }
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], items, true]);

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = items;
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';

    process.stdout.write('A primeira visita le o catalogo\n');
    folds = 0;
    openDiscover(window);
    await waitFor(function () {
        return window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'discover' &&
            window.BuroApp.state.screenData.loading === false;
    }, 8000);
    check('a aba le o banco para se montar',
        folds > 0);
    check('e o baralho sai com cartas',
        (window.BuroApp.state.screenData.deck || []).length > 0);
    firstDeck = window.BuroApp.state.screenData.deck.length;

    process.stdout.write('Voltar logo depois nao le de novo\n');
    leaveDiscover(window);
    /*
      Zerar a contagem depois de a Home terminar, e nao antes.

      Sair para a Home tambem le o banco — e a Home montando-se, nao o
      Descobrir. Contar a partir do momento em que ela ja terminou isola a
      leitura que este teste mede.
    */
    await waitFor(function () {
        return window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'home' &&
            window.BuroApp.state.screenData.loading === false;
    }, 8000);
    folds = 0;
    openDiscover(window);
    await waitFor(function () {
        return window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'discover' &&
            window.BuroApp.state.screenData.loading === false;
    }, 8000);
    afterReturn = folds;
    check('a leitura guardada e reaproveitada',
        afterReturn === 0);
    /*
      E o baralho continua sendo montado: e a diferenca entre guardar a leitura
      e congelar a tela. Sem isto o Descobrir viraria uma lista fixa depois da
      primeira visita.
    */
    check('mas o baralho e montado de novo, e nao servido congelado',
        window.BuroApp.state.screenData.deck &&
        window.BuroApp.state.screenData.deck.length > 0 &&
        window.BuroApp.state.screenData.deck.length <= firstDeck);

    process.stdout.write('Um catalogo novo invalida o guardado\n');
    /* Economizar a leitura nao pode significar mostrar o catalogo de ontem. */
    window.BuroApp._forgetHomeCache();
    leaveDiscover(window);
    folds = 0;
    openDiscover(window);
    await waitFor(function () { return folds > 0; }, 8000);
    check('sem cache, a aba volta a ler',
        folds > 0);

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
    process.stderr.write('Falha na suite do Descobrir: ' + error.stack + '\n');
    process.exit(1);
});
