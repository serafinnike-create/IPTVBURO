/*
  A linha "IPTV BURO — Na sua biblioteca", em Assinaturas.

  Tres defeitos no mesmo cartao:

  - **nao abria o filme.** `openSubscriptionLocal` lia `state.items`, que e a
    amostra que o boot carregou; mas quem casou o titulo varre o banco inteiro.
    O item quase sempre existe no banco e quase nunca na amostra, entao o toque
    caia num `return` mudo;
  - **nao tinha marca.** As outras linhas mostram o logo que o TMDb entrega; o
    proprio aplicativo nao esta nesse catalogo, e ficava sem nada;
  - **demorava cinco a dez segundos.** Cada titulo aberto varria as 42.000 linhas
    para responder uma pergunta so.
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
    if (onFold) {
        (function () {
            var realFold = window.BuroStorage.fold;
            window.BuroStorage.fold = function (storeName) {
                if (storeName === 'items') { onFold(); }
                return realFold.apply(window.BuroStorage, arguments);
            };
        }());
    }
    window.BuroApp.init();
    return window;
}

async function run() {
    var folds = 0;
    var window = loadApp(function () { folds += 1; });
    var source = { id: 's', name: 'Fonte', type: 'XTREAM', channelCount: 2, createdAt: 1, updatedAt: null };
    var category = { id: 'c', sourceId: 's', providerCategoryId: 'm', name: 'Filmes',
        contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var owned;
    var offer;
    var afterSecond;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    owned = window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'm1', name: 'La Captura', categoryId: 'c',
        contentType: 'MOVIE', sortOrder: 0, year: 2025,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm1' }
    });
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], [owned], true]);

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    /*
      `state.items` fica **vazio** de proposito: e o estado real de um catalogo
      grande, onde a amostra do boot nao contem o titulo que o casamento achou
      no banco. Era exatamente essa a diferenca que fazia o cartao nao abrir.
    */
    window.BuroApp.state.items = [];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SUBSCRIPTIONS';
    window.BuroApp.state.screenData = {
        kind: 'subscriptions', filter: 'MOVIES', region: 'BR', shelves: [], loading: false,
        selected: { tmdbId: 700, isSeries: false, title: 'La Captura', year: 2025 },
        selectionLoading: false,
        selection: { details: { title: 'La Captura' }, offers: [], unknown: false, localItem: null }
    };
    window.BuroApp.render();

    process.stdout.write('O casamento com a biblioteca acontece\n');
    folds = 0;
    window.BuroApp._matchSubscriptionLocal(window.BuroApp.state.screenData.selected);
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="subscription-local"]'));
    }, 6000);
    check('a linha "Na sua biblioteca" aparece',
        Boolean(window.document.querySelector('[data-action="subscription-local"]')));
    /* A marca do proprio aplicativo, que faltava. */
    check('e com a marca do aplicativo, e nao sem nada',
        Boolean(window.document.querySelector('.subscription-offer-mark')));

    process.stdout.write('O segundo titulo nao varre o catalogo de novo\n');
    /*
      Era isto que custava cinco a dez segundos por titulo aberto. O indice e
      montado uma vez e responde as perguntas seguintes.
    */
    folds = 0;
    window.BuroApp._matchSubscriptionLocal({ tmdbId: 701, isSeries: false, title: 'Outro filme', year: 2024 });
    await new Promise(function (resolve) { setTimeout(resolve, 120); });
    afterSecond = folds;
    check('o indice guardado responde sem nova leitura',
        afterSecond === 0);

    process.stdout.write('Acionar a linha abre o titulo, mesmo fora da amostra\n');
    window.BuroApp._matchSubscriptionLocal(window.BuroApp.state.screenData.selected);
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="subscription-local"]'));
    }, 6000);
    offer = window.document.querySelector('[data-action="subscription-local"]');
    check('a amostra continua vazia, como num catalogo grande',
        window.BuroApp.state.items.length === 0);
    window.BuroApp._activate(offer);
    /*
      O item e lido do banco pelo id. Sem isso o toque caia num `return` mudo — o
      cartao prometia o filme e nao fazia nada.
    */
    await waitFor(function () {
        return window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind !== 'subscriptions';
    }, 6000);
    check('sai de Assinaturas em vez de engolir o toque',
        window.BuroApp.state.screenData.kind !== 'subscriptions');
    check('e o item foi trazido do banco para o estado',
        window.BuroApp.state.items.length === 1 &&
        window.BuroApp.state.items[0].name === 'La Captura');

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
    process.stderr.write('Falha na suite de biblioteca em Assinaturas: ' + error.stack + '\n');
    process.exit(1);
});
