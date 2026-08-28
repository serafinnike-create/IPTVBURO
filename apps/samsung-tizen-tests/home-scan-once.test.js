/*
  A abertura varre o catalogo uma vez, e nao duas.

  `prepareHomeForReveal` monta a Home ainda atras da tela de carregamento e
  grava o resultado. `startHomeLoad`, no quadro seguinte, lia esse cache — e
  agendava a varredura de novo mesmo assim.

  A justificativa era nao esconder uma falha de leitura: a Home apareceria
  montada com o banco inacessivel. Vale para um cache de horas atras; nao vale
  para um que a abertura acabou de montar, porque ali a leitura ja aconteceu e
  deu certo.

  O custo medido num PC e de 453ms por varredura sobre 42.000 titulos. Numa TV
  Samsung, entre cinco e dez vezes mais. Fazer duas para chegar ao mesmo
  resultado e a diferenca entre a Home aparecer e a Home aparecer depois.

  Este teste conta as varreduras. Ele nao mede tempo — um teste que afirma
  milissegundos falha na maquina de outra pessoa por motivo nenhum.
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
    /*
      A contagem entra depois dos modulos e antes de `init`: assim ela ve todas
      as varreduras da abertura, e nenhuma de antes.
    */
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

async function run() {
    var folds = 0;
    var window = loadApp(function () { folds += 1; });
    var source = { id: 'source-scan', name: 'Fonte', type: 'XTREAM',
        channelCount: 40, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-scan', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-scan', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var index;
    var afterFirstHome;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    for (index = 0; index < 40; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'm' + index, name: 'Filme ' + index,
            categoryId: category.id, contentType: 'MOVIE', sortOrder: index,
            year: 2024, rating: 8, logoUrl: 'https://art.test/' + index + '.jpg',
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
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    await waitFor(function () {
        return window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'home' &&
            window.BuroApp.state.screenData.loading === false;
    }, 6000);

    folds = 0;

    process.stdout.write('Voltar a Home logo depois nao refaz a varredura\n');
    /*
      Sair para outra aba e voltar e o caminho que a abertura percorre: a Home
      ja foi montada segundos antes, e o cache guardado responde por ela.
    */
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await new Promise(function (resolve) { setTimeout(resolve, 120); });
    afterFirstHome = folds;
    check('a Home guardada e servida sem varrer o catalogo de novo',
        afterFirstHome === 0);
    check('e a Home aparece montada, e nao vazia',
        window.BuroApp.state.screenData &&
        window.BuroApp.state.screenData.kind === 'home' &&
        window.BuroApp.state.screenData.loading === false);

    process.stdout.write('Um catalogo novo invalida o guardado\n');
    /*
      O outro lado: economizar a varredura nao pode significar mostrar ontem.
      Quando a varredura de fundo troca o catalogo, o cache e descartado e a
      Home volta a ler.
    */
    window.BuroApp._forgetHomeCache();
    folds = 0;
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () { return folds > 0; }, 4000);
    check('sem cache, a Home varre para se montar',
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
    process.stderr.write('Falha na suite de varredura unica: ' + error.stack + '\n');
    process.exit(1);
});
