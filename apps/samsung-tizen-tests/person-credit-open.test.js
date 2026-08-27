/*
  Clicar num filme da filmografia tem de abrir aquele filme.

  A pagina da pessoa carrega os creditos do TMDb e desenha um por um. Um credito
  que o catalogo ja tem vira `person-local` e abre a ficha; um que ele nao tem
  virava `person-credit` e ia direto para Assinaturas.

  Direto era o defeito. O casamento com o catalogo local so era feito ao desenhar
  a lista, por nome exato — e ele erra: o provedor escreve "Duna 4K [DUB]" onde o
  TMDb escreve "Duna". O titulo estava na lista da pessoa e mesmo assim ela caia
  em "onde assistir", que numa TV sem chave TMDb e uma tela vazia.

  O aplicativo do Windows resolve com `openCredit`, que devolve um destino:
  PLAYLIST_ITEM quando o titulo esta no catalogo, SUBSCRIPTIONS quando nao esta.
  Este teste guarda os dois lados dessa pergunta.
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

function showPerson(window, credits) {
    window.BuroApp.state.screen = 'PERSON';
    window.BuroApp.state.screenData = {
        kind: 'person',
        loading: false,
        person: { id: 500, name: 'Nicolas Cage', biography: 'bio', birthday: '1964-01-07', credits: credits }
    };
    window.BuroApp.render();
}

async function run() {
    var window = loadApp();
    var source = { id: 'source-person', name: 'Fonte', type: 'XTREAM',
        channelCount: 1, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-person', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-person', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var owned;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    owned = window.BuroDomain.createItem({
        sourceId: source.id, providerItemId: 'owned', name: 'Contracara',
        categoryId: category.id, contentType: 'MOVIE', sortOrder: 0, year: 1997,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'owned' }
    });
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], [owned], true]);

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = [owned];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;

    process.stdout.write('Um credito que o catalogo tem abre a ficha do titulo\n');
    /*
      O credito e desenhado como `person-credit` — sem casamento previo — e
      mesmo assim tem de acabar na ficha: a busca acontece ao abrir, sobre o
      banco, e nao ao desenhar a lista.
    */
    showPerson(window, [
        { id: 9001, title: 'Contracara', year: 1997, isSeries: false, character: 'Castor', posterUrl: null }
    ]);
    check('o credito e clicavel',
        Boolean(window.document.querySelector('[data-action="person-credit"],[data-action="person-local"]')));

    window.BuroApp._activate(
        window.document.querySelector('[data-action="person-credit"],[data-action="person-local"]')
    );
    await waitFor(function () { return window.BuroApp.state.screen !== 'PERSON'; }, 6000);
    check('sai da pagina da pessoa',
        window.BuroApp.state.screen === 'SHELL');
    /*
      O destino e o titulo do catalogo, e nao Assinaturas. `catalogue-error` e um
      destino legitimo aqui: a ficha abriu e falhou ao buscar os detalhes porque
      nao ha servidor Xtream no teste. O que importa e nao ter ido parar em
      "onde assistir".
    */
    check('vai para o titulo, nao para Assinaturas',
        window.BuroApp.state.section !== 'SUBSCRIPTIONS');
    check('e a tela mostra o titulo do catalogo',
        window.document.body.textContent.indexOf('Contracara') >= 0);

    process.stdout.write('Um credito que o catalogo nao tem cai em Assinaturas\n');
    /*
      O outro lado. A correcao nao pode ter cortado o caminho de reserva: um
      titulo que a lista nao tem continua indo para "onde assistir", que e a
      unica resposta util para ele.
    */
    showPerson(window, [
        { id: 9002, title: 'Um filme que a lista nao tem', year: 2011, isSeries: false, character: 'Z', posterUrl: null }
    ]);
    window.BuroApp._activate(window.document.querySelector('[data-action="person-credit"]'));
    await waitFor(function () { return window.BuroApp.state.screen !== 'PERSON'; }, 6000);
    check('o credito ausente do catalogo vai para Assinaturas',
        window.BuroApp.state.section === 'SUBSCRIPTIONS');

    process.stdout.write('Um credito sem id do TMDb nao finge ser clicavel\n');
    /* Sem id nao ha o que abrir nem onde procurar: um botao ali prometeria uma
       navegacao que nao existe. */
    showPerson(window, [
        { id: null, title: 'Credito sem id', year: 2011, isSeries: false, character: 'Y', posterUrl: null }
    ]);
    check('vira texto, e nao botao',
        window.document.querySelectorAll('[data-action="person-credit"]').length === 0 &&
        window.document.querySelectorAll('div.person-credit').length === 1);

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
    process.stderr.write('Falha na suite de credito de pessoa: ' + error.stack + '\n');
    process.exit(1);
});
