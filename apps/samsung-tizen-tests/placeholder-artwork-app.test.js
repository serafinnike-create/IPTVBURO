/* Full-catalogue generic-cover detection in the real Samsung shell. */
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
    var generic = 'https://art.test/provider-generic.jpg';
    var genuine = 'https://art.test/real-title.jpg';
    var source = { id: 'source-placeholder-app', name: 'Fonte Xtream', type: 'XTREAM',
        channelCount: 31, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-placeholder-movies', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-placeholder-app', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var index;
    var genericCard;
    var genuineCard;

    /*
      Esperar `state.ready` nao basta, e essa era a causa do timeout.

      `finishInitialization` marca `ready` e so entao chama `completeReveal`, que
      escreve `state.screen` dentro de um `setTimeout` — a permanencia minima da
      tela de abertura, 900ms ou 1600ms conforme haja fonte. Entre uma coisa e
      outra existe uma janela em que o app se diz pronto e ainda vai trocar de
      tela.

      O teste montava o estado dentro dessa janela: a Home era desenhada e
      passava, e entao o temporizador pendente disparava e sobrescrevia
      `state.screen` com PROFILES, calculado da lista de perfis *vazia* que o
      boot leu antes de o teste criar a sua. A aba de Filmes nunca era
      desenhada, e o `waitFor` seguinte expirava esperando cartoes numa tela que
      nao estava mais no ar.

      Esperar a tela de fato assentar fecha a janela. E o que as outras suites ja
      fazem — `dpad-navigation` e `catalogue-refresh` esperam por
      `state.screen === 'SHELL'`, nunca por `ready`.
    */
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);
    for (index = 0; index < 30; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'generic-' + index, name: 'Título legível ' + index,
            categoryId: category.id, contentType: 'MOVIE', sortOrder: index,
            year: 2024, rating: 7, logoUrl: generic,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'generic-' + index }
        }));
    }
    items.push(window.BuroDomain.createItem({
        sourceId: source.id, providerItemId: 'genuine', name: 'Capa verdadeira',
        categoryId: category.id, contentType: 'MOVIE', sortOrder: 30,
        year: 2024, rating: 7, logoUrl: genuine,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'genuine' }
    }));
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
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            window.BuroApp.state.screenData.loading === false;
    }, 5000);

    check('a contagem transitória não permanece no cache diário da Home',
        !window.BuroApp.state.screenData.result.artworkScan);
    check('o Hero não desenha a capa genérica compartilhada',
        !window.document.querySelector('.real-home-hero img[src="' + generic + '"]'));

    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    /*
      A prateleira carrega por blocos, entao esperar os 31 de uma vez nunca
      terminava.

      `CATALOGUE_BLOCK_SIZE` e 21: `loadCatalogueShelf` le um bloco, desenha, e
      deixa o resto atras de "carregar mais". A condicao original — contar
      cartoes ate bater com `items.length` — pedia um segundo bloco que so chega
      quando alguem pede, e o `waitFor` expirava com a tela correta na frente
      dele.

      Aqui a espera e pelo que a tela promete: o primeiro bloco desenhado, com a
      capa genuina entre os cartoes. Nao ha numero de bloco escrito no teste, de
      forma que mudar `CATALOGUE_BLOCK_SIZE` continua sendo uma decisao de
      produto e nao uma quebra de suite.
    */
    await waitFor(function () {
        return window.document.querySelector('.media-card[data-id="' + items[30].id + '"]');
    }, 6000);
    genericCard = window.document.querySelector('.media-card[data-id="' + items[0].id + '"]');
    genuineCard = window.document.querySelector('.media-card[data-id="' + items[30].id + '"]');
    check('o card genérico vira placeholder de texto legível',
        genericCard && !genericCard.querySelector('img') && genericCard.textContent.indexOf(items[0].name) >= 0);
    /* Vale para os cartoes desenhados, sejam quantos o bloco trouxer: nenhum
       deles pode estar mostrando a imagem repetida. */
    check('nenhum título desenhado usa a imagem repetida',
        window.document.querySelectorAll('.media-card').length > 0 &&
        window.document.querySelectorAll('.media-card img[src="' + generic + '"]').length === 0);
    check('a capa genuína do mesmo catálogo continua visível',
        genuineCard && genuineCard.querySelector('img') && genuineCard.querySelector('img').getAttribute('src') === genuine);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
        process.exitCode = 1; return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte de capa genérica: ' + error.stack + '\n');
    process.exit(1);
});
