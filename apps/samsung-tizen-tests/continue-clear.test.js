/*
  Limpar a lista de Continuar assistindo sem apagar o Historico.

  As duas telas leem a mesma tabela `progress`. O Historico ja tinha o "apagar
  tudo", e ele **remove** as linhas — o que ali e o certo. Fazer o mesmo aqui
  esvaziaria o Historico junto, e quem pediu para limpar a fila de retomada nao
  pediu para esquecer o que assistiu.

  Por isso a limpeza daqui marca cada linha como concluida, que e exatamente o
  que o botao de um cartao so ja fazia. Este teste existe para que a diferenca
  nao seja perdida numa refatoracao que "unifique" as duas.
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
    var source = { id: 'source-continue-clear', name: 'Fonte', type: 'XTREAM',
        channelCount: 3, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-continue-clear', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-continue-clear', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var index;
    var rows;

    /* Esperar a tela assentar, e nao so `state.ready`: entre um e outro o boot
       ainda escreve `state.screen` dentro do tempo minimo da abertura. */
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    for (index = 0; index < 3; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'movie-' + index, name: 'Filme ' + index,
            categoryId: category.id, contentType: 'MOVIE', sortOrder: index, year: 2024,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'movie-' + index }
        }));
    }
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], items, true]);

    /* Duas linhas em andamento e uma ja concluida: a concluida existe para
       provar que a limpeza nao a toca nem a duplica. */
    window.BuroApp.state.progress = [
        { id: 'p0', profileId: profile.id, itemId: items[0].id, positionMs: 60000,
          durationMs: 600000, completed: false, updatedAt: 10 },
        { id: 'p1', profileId: profile.id, itemId: items[1].id, positionMs: 120000,
          durationMs: 600000, completed: false, updatedAt: 20 },
        { id: 'p2', profileId: profile.id, itemId: items[2].id, positionMs: 600000,
          durationMs: 600000, completed: true, updatedAt: 30 }
    ];
    await Promise.all(window.BuroApp.state.progress.map(function (row) {
        return call(window, window.BuroStorage.put, ['progress', row]);
    }));

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = items;
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'CONTINUE_WATCHING';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('O botao aparece quando ha o que limpar\n');
    check('a lista de retomada oferece limpar tudo',
        Boolean(window.document.querySelector('[data-action="continue-clear"]')));

    process.stdout.write('Limpar pede confirmacao antes\n');
    window.BuroApp._activate(window.document.querySelector('[data-action="continue-clear"]'));
    check('abre a tela de confirmacao em vez de apagar direto',
        window.BuroApp.state.screen === 'CONTINUE_CLEAR_CONFIRM' &&
        Boolean(window.document.querySelector('[data-action="continue-clear-confirm"]')));
    /* Uma acao sem volta precisa de saida: o cancelar tem de estar ali. */
    check('a confirmacao oferece cancelar',
        Boolean(window.document.querySelector('[data-action="back"]')));

    process.stdout.write('Confirmar tira os titulos da retomada\n');
    window.BuroApp._activate(window.document.querySelector('[data-action="continue-clear-confirm"]'));
    await waitFor(function () {
        return window.BuroApp.state.screen === 'SHELL' &&
            window.BuroApp.state.progress.every(function (row) { return row.completed; });
    }, 6000);
    check('nenhuma linha do perfil continua em andamento',
        window.BuroApp.state.progress.every(function (row) { return row.completed === true; }));

    process.stdout.write('O Historico sobrevive, que e a razao de nao apagar\n');
    rows = await new Promise(function (resolve, reject) {
        /* `fold` reduz com (acumulador, linha), nessa ordem. */
        window.BuroStorage.fold('progress', function (acc, row) { acc.push(row); return acc; }, [], resolve, reject);
    });
    /*
      Tres linhas antes, tres depois. Se a limpeza tivesse removido em vez de
      marcar, o Historico — que le esta mesma tabela — teria perdido os titulos.
    */
    check('as tres linhas continuam gravadas, nenhuma foi removida',
        rows.length === 3);
    check('e as posicoes foram levadas ao fim, nao zeradas',
        rows.every(function (row) {
            return row.completed === true && Number(row.positionMs) === Number(row.durationMs);
        }));

    /* A linha que ja estava concluida nao pode ter virado duas. */
    check('a linha ja concluida continua unica',
        rows.filter(function (row) { return row.id === 'p2'; }).length === 1);

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
    process.stderr.write('Falha na suite de limpar retomada: ' + error.stack + '\n');
    process.exit(1);
});
