/*
  O seletor de Serviço numa lista que arquiva por gênero.

  A lista do usuário arquiva "Filmes | Ação", "Filmes | Drama": categoria
  nenhuma nomeia um serviço, e o seletor ficava permanentemente desativado
  justamente na aba onde alguém pergunta "o que tem na Netflix". O índice do
  TMDb responde, e este teste verifica os dois lados — que o chip passa a ser
  clicável e que escolher um serviço filtra de verdade, por título e não por
  categoria.
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

/*
  Uma lista arquivada por gênero: nenhuma categoria nomeia um serviço.

  Três filmes, dos quais o TMDb dirá que dois estão na Netflix.
*/
function seed(window) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 3, createdAt: 1, updatedAt: null };
    var items = [
        { id: 'item-1', name: 'Duna 4K [L]', year: 2021 },
        { id: 'item-2', name: 'Outro Filme', year: 2020 },
        { id: 'item-3', name: 'Terceiro', year: 2019 }
    ].map(function (row) {
        var item = window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: row.id, name: row.name,
            categoryId: 'c1', contentType: 'MOVIE', year: row.year, rating: 8,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: row.id }
        });
        item.id = row.id;
        return item;
    });
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [{
        id: 'c1', sourceId: 's1', providerCategoryId: '1',
        name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 0
    }];
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

/* Uma chave TMDb guardada, e o que o TMDb responderia. */
function withTmdb(window, byService) {
    return new Promise(function (resolve, reject) {
        /* Assíncrono como a rede é: responder na mesma pilha faria o app
           redesenhar de dentro do próprio desenho, que é uma ordem de execução
           que nunca acontece na TV. */
        window.BuroTmdb.loadServiceTitles = function (key, region, locale, progress, success) {
            window.__serviceTitlesCalls = (window.__serviceTitlesCalls || 0) + 1;
            window.setTimeout(function () { success(byService); }, 0);
            return { abort: function () {} };
        };
        window.BuroStorage.secureSave('tmdb-shared', { apiKey: 'chave-de-teste-0123456789' },
            resolve, reject);
    });
}

async function openMovies(window) {
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return Boolean(window.document.querySelector('.catalogue-scope-bar'));
    }, 8000);
}

async function run() {
    var window;
    var options;

    process.stdout.write('Sem chave, o seletor diz que a lista não separa por serviço\n');
    window = loadApp();
    await reachShell(window);
    await seed(window);
    await openMovies(window);
    check('o chip de serviço aparece desativado',
        Boolean(window.document.querySelector('.scope-chip.disabled')));
    check('e explica o motivo em vez de sumir',
        (window.document.querySelector('.scope-chip.disabled').textContent || '')
            .indexOf('não separa por serviço') > 0);
    window.close();

    process.stdout.write('Com o índice do TMDb, o seletor passa a funcionar\n');
    window = loadApp();
    await reachShell(window);
    await seed(window);
    await withTmdb(window, {
        Netflix: [{ title: 'Duna', year: 2021 }, { title: 'Outro Filme', year: 2020 }],
        HBO: [{ title: 'Terceiro', year: 2019 }]
    });
    await openMovies(window);
    /* O índice é construído em segundo plano; a barra se redesenha ao ficar pronto. */
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="catalogue-pick-service"]'));
    }, 8000);
    check('o chip vira um botão quando o índice responde',
        Boolean(window.document.querySelector('[data-action="catalogue-pick-service"]')));
    check('e a lista não pergunta ao TMDb duas vezes pela mesma chave',
        window.__serviceTitlesCalls === 1);

    activate(window, '[data-action="catalogue-pick-service"]');
    await waitFor(function () {
        return window.document.querySelectorAll('[data-picker="service"]').length > 1;
    }, 8000);
    options = Array.prototype.map.call(
        window.document.querySelectorAll('[data-picker="service"]'),
        function (node) { return node.textContent; }
    );
    check('os serviços do índice aparecem na lista',
        options.join('|').indexOf('Netflix') >= 0 && options.join('|').indexOf('HBO') >= 0);
    /*
      A contagem no rótulo: numa lista onde o cruzamento pode ter casado pouco,
      "Netflix (2)" é o que permite julgar se vale filtrar por ele.
    */
    check('cada serviço diz quantos títulos da lista carrega',
        options.join('|').indexOf('Netflix (2)') >= 0 && options.join('|').indexOf('HBO (1)') >= 0);
    check('o que carrega mais vem primeiro',
        options.join('|').indexOf('Netflix') < options.join('|').indexOf('HBO'));

    process.stdout.write('Escolher um serviço filtra por título, não por categoria\n');
    window.BuroApp._activate(window.document.querySelector('[data-picker="service"][data-value="Netflix"]'));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 2;
    }, 8000);
    /*
      O ponto do teste: os três filmes estão na mesma categoria "Filmes | Ação".
      Um filtro por categoria devolveria os três. Só um filtro por id de título
      devolve os dois que a Netflix carrega.
    */
    check('só os títulos daquele serviço ficam na prateleira',
        window.document.querySelectorAll('.media-card').length === 2);
    check('e são exatamente os que o índice casou',
        (window.document.querySelector('.card-grid').textContent || '').indexOf('Terceiro') < 0);

    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-scope-reset"]'));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 3;
    }, 8000);
    check('limpar o filtro devolve o catálogo inteiro',
        window.document.querySelectorAll('.media-card').length === 3);
    window.close();

    process.stdout.write('O TMDb sem resposta não estraga a tela\n');
    window = loadApp();
    await reachShell(window);
    await seed(window);
    await withTmdb(window, {});
    await openMovies(window);
    check('o chip continua desativado, e a prateleira continua desenhando',
        Boolean(window.document.querySelector('.scope-chip.disabled')));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 3;
    }, 8000);
    check('os títulos aparecem mesmo sem o índice',
        window.document.querySelectorAll('.media-card').length === 3);
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

/*
  Encerra explicitamente.

  As janelas são fechadas acima, mas a construção do índice deixa uma leitura do
  IndexedDB falso em voo, e o `fake-indexeddb` continua agendando trabalho contra
  a janela já fechada — o processo imprimia os doze resultados e nunca saía,
  travando a suíte inteira no passo seguinte.
*/
run().then(function () {
    process.exit(process.exitCode || 0);
}).catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
