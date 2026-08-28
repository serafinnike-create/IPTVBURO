/*
  O aplicativo abre no Inicio, e nao onde a pessoa parou.

  A secao ficava guardada e era restaurada na abertura: quem saiu em
  Configuracoes voltava nelas dias depois, sem nenhuma pista de por que. Numa TV
  isso e pior do que num telefone — liga-se o aparelho para assistir, e a
  primeira tela deve ser a que oferece o que assistir.

  O teste guarda os dois lados: a abertura ignora a secao guardada, e a
  preferencia continua sendo gravada, porque ela ainda diz onde a pessoa estava
  dentro da mesma sessao.
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

/*
  `storedSection` finge a preferencia de uma sessao anterior — a pessoa que
  fechou o aplicativo dentro daquela secao.
*/
function loadApp(storedSection) {
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
        language: 'pt-BR', languageSelected: true, acceptedLegal: true, section: storedSection
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
    var window;
    var source = { id: 's', name: 'Fonte', type: 'XTREAM', channelCount: 2, createdAt: 1, updatedAt: null };
    var category = { id: 'c', sourceId: 's', providerCategoryId: 'm', name: 'Filmes',
        contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var items;

    process.stdout.write('Quem saiu em Configuracoes volta no Inicio\n');
    window = loadApp('SETTINGS');
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    /*
      A preferencia chegou como SETTINGS — e o estado de quem fechou o
      aplicativo ali. A abertura tem de ignora-la.
    */
    check('a preferencia guardada dizia Configuracoes',
        window.BuroApp.state.preferences.section === 'SETTINGS');
    check('mas o aplicativo abriu no Inicio',
        window.BuroApp.state.section === 'HOME');
    window.close();

    process.stdout.write('E o mesmo vale para qualquer outra secao\n');
    window = loadApp('DOWNLOADS');
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    check('sair em Downloads tambem devolve ao Inicio',
        window.BuroApp.state.section === 'HOME');

    process.stdout.write('Trocar de secao continua sendo gravado\n');
    /*
      A preferencia nao foi abandonada: ela ainda registra onde a pessoa esta,
      e e o que outras partes do aplicativo usam para voltar. O que mudou e so
      a abertura.
    */
    items = [];
    items.push(window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'm1', name: 'Filme', categoryId: 'c',
        contentType: 'MOVIE', sortOrder: 0, year: 2024,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm1' }
    }));
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], items, true]);
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = items;
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    window.BuroApp._activate(
        window.document.querySelector('[data-action="section"][data-section="MOVIES"]')
    );
    check('escolher Filmes grava a secao',
        window.BuroApp.state.preferences.section === 'MOVIES' &&
        window.BuroApp.state.section === 'MOVIES');
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
    process.stderr.write('Falha na suite de abertura: ' + error.stack + '\n');
    process.exit(1);
});
