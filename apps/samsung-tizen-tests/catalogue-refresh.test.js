/*
  O botão "Atualizar" da barra de cima, e o que ele tem de fazer de verdade.

  Relato do usuário: "fiz varedura contia sem mostrar capas, na barra de cima
  falta botao autlizar igual no app windows". Duas coisas, e a segunda é a que
  importa: a varredura pula toda categoria completada nas últimas 24 horas,
  então mandar atualizar sem forçar não reprocessa nada e a tela fica igual —
  as capas continuavam vazias porque as linhas nunca eram regravadas.

  O aplicativo do Windows já tinha topado com isto e deixou a razão escrita em
  `refreshCatalog`: quem apertou o botão quer o que o provedor tem agora.
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

function seed(window, type) {
    var source = { id: 's1', name: 'Fonte', type: type || 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null };
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [{
        id: 'c1', sourceId: 's1', providerCategoryId: '1',
        name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 0
    }];
    return new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, resolve, reject);
    });
}

async function run() {
    var window;
    var starts;

    process.stdout.write('O botão aparece na barra de cima\n');
    window = loadApp();
    await reachShell(window);
    await seed(window, 'XTREAM');
    window.BuroApp.render();
    check('a barra de cima oferece atualizar o catálogo',
        Boolean(window.document.querySelector('[data-action="catalogue-refresh"]')));
    check('e o botão é alcançável pelo D-pad',
        window.document.querySelector('[data-action="catalogue-refresh"]')
            .classList.contains('focusable'));
    check('o botão se anuncia para quem usa leitor de tela',
        Boolean(window.document.querySelector('[data-action="catalogue-refresh"]')
            .getAttribute('aria-label')));

    /*
      O ponto do teste. Sem `force` a fila pula categoria completada há menos de
      24 horas: o usuário aperta, nada é reprocessado, e a tela fica idêntica.
    */
    process.stdout.write('Apertar força a varredura, em vez de pular o que já foi feito\n');
    starts = [];
    window.BuroCatalogueSync.start = function (source, categories, callbacks, force) {
        starts.push({ sourceId: source.id, force: force });
    };
    activate(window, '[data-action="catalogue-refresh"]');
    check('a varredura foi pedida',
        starts.length === 1 && starts[0].sourceId === 's1');
    check('e foi pedida forçada, senão o botão não faria nada',
        starts[0].force === true);

    process.stdout.write('Enquanto roda, o botão vira indicador e não aceita novo toque\n');
    (function () {
        var html;
        window.BuroCatalogueSync.progress = function () {
            return { state: 'RUNNING', completed: 7, total: 98, itemCount: 1200 };
        };
        window.BuroApp.render();
        html = window.BuroApp._refreshChipHtml();
        check('mostra quanto falta, e não um rótulo parado',
            html.indexOf('7/98') > 0);
        /* Indicador girando e não "…": um catálogo grande demora, e um ponto
           parado é indistinguível de um botão que falhou. */
        check('mostra um indicador girando',
            html.indexOf('boot-indicator') > 0);
        check('deixa de ser alvo do D-pad enquanto trabalha',
            html.indexOf('focusable') < 0 && html.indexOf('data-action') < 0);
        check('e continua se anunciando como estado, para leitor de tela',
            html.indexOf('role="status"') > 0);
    }());

    /*
      A cadeia inteira, que é o que o usuário cobrou: apertar atualizar tem de
      terminar em capa na tela.

      A linha já gravada não tem `logoUrl` — foi importada pelo código antigo,
      que punha `null` de propósito. A varredura forçada relê a categoria, o
      provedor manda `stream_icon`, e a linha é sobrescrita com a capa.
    */
    process.stdout.write('Da varredura forçada até a capa na tela\n');
    window.close();
    window = loadApp();
    await reachShell(window);
    await seed(window, 'XTREAM');
    await new Promise(function (resolve, reject) {
        var stale = window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: '1', name: 'Filme antigo',
            categoryId: 'c1', contentType: 'MOVIE', year: 2024, rating: 8,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '1' }
        });
        check('a linha do código antigo está sem capa, como na TV do usuário',
            stale.logoUrl === null);
        window.BuroStorage.putBatch('items', [stale], resolve, reject);
    });

    /* O provedor respondendo com a capa, como responde de verdade. */
    window.BuroNetwork.json = function (options, success) {
        success([{ stream_id: 1, name: 'Filme antigo', year: '2024', rating: '8',
            stream_icon: 'https://cdn.provedor.test/capas/filme.jpg' }]);
        return { abort: function () {} };
    };
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="catalogue-refresh"]'));
    }, 8000);
    activate(window, '[data-action="catalogue-refresh"]');
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card img').length === 1;
    }, 12000);
    check('depois de atualizar, o cartão aparece com a capa do provedor',
        window.document.querySelector('.media-card img').getAttribute('src') ===
            'https://cdn.provedor.test/capas/filme.jpg');

    process.stdout.write('Sem fonte Xtream não há o que atualizar\n');
    window.close();
    window = loadApp();
    await reachShell(window);
    await seed(window, 'REMOTE_M3U');
    window.BuroApp.render();
    check('fonte M3U não ganha o botão',
        !window.document.querySelector('[data-action="catalogue-refresh"]'));
    window.BuroApp.state.activeSource = null;
    window.BuroApp.render();
    check('sem fonte nenhuma o botão some, em vez de falhar ao ser tocado',
        !window.document.querySelector('[data-action="catalogue-refresh"]'));
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
