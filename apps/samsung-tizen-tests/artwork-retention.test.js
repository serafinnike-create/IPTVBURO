/*
  Por que as prateleiras ficam sem capa num catálogo grande.

  Relato do usuário, repetido: "as capas nao carregam, as parteleira estao
  sempre sem capa uma ou outra que carrega". Com 42 mil títulos em 98
  categorias.

  A arte só existia em memória, com teto e descarte LRU. A varredura de fundo
  percorre as categorias em sequência e guarda a arte de cada uma; passado o
  teto, as primeiras são descartadas pelas últimas. Quando alguém abre Filmes,
  o que sobrou em memória é das últimas categorias varridas — quase nunca dos
  títulos que a prateleira está mostrando. Daí "uma ou outra".

  Este teste mede a retenção: quantos dos títulos visíveis ainda têm capa
  depois de a varredura passar por um catálogo grande.
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

var CATEGORIES = 40;
var PER_CATEGORY = 60;

/*
  Um item como a varredura o grava: com a capa no próprio registro.

  É `js/xtream.js` que decide isto — `logoUrl: storableArtwork(...)`. Aqui o
  item é montado pelo mesmo `createItem`, com a mesma URL sem query string que
  passa pela peneira de credencial.
*/
function makeItem(window, id, categoryId) {
    var item = window.BuroDomain.createItem({
        sourceId: 's1', providerItemId: id, name: 'Filme ' + id,
        categoryId: categoryId, contentType: 'MOVIE', year: 2024, rating: 7,
        logoUrl: 'https://art.test/' + id + '.jpg',
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: id }
    });
    item.id = id;
    return item;
}

/*
  Um catálogo grande, arquivado em muitas categorias — a forma da lista do
  usuário, em escala menor para o teste correr rápido.
*/
function seed(window) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null };
    var items = [];
    var categories = [];
    var c;
    var i;
    for (c = 0; c < CATEGORIES; c += 1) {
        categories.push({
            id: 'c' + c, sourceId: 's1', providerCategoryId: String(c),
            name: 'Filmes | Gênero ' + c, contentType: 'MOVIE', sortOrder: c
        });
        for (i = 0; i < PER_CATEGORY; i += 1) {
            items.push(makeItem(window, 'i' + c + '-' + i, 'c' + c));
        }
    }
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = categories;
    return new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, function () {
            window.BuroStorage.putBatch('items', items, function () { resolve(items); }, reject);
        }, reject);
    });
}

/* A varredura entregando arte de cada categoria, como faz de verdade. */
function sweepAll(window) {
    var c;
    var i;
    var artwork;
    for (c = 0; c < CATEGORIES; c += 1) {
        artwork = {};
        for (i = 0; i < PER_CATEGORY; i += 1) {
            artwork['i' + c + '-' + i] = 'https://art.test/' + c + '-' + i + '.jpg';
        }
        window.BuroApp._rememberArtworkMap(artwork);
    }
}

async function run() {
    var window;
    var total = CATEGORIES * PER_CATEGORY;
    var drawn;
    var cards;

    process.stdout.write('Depois da varredura, a prateleira ainda tem as capas dela\n');
    window = loadApp();
    await reachShell(window);
    await seed(window);

    /* A varredura passa por tudo, como faz ao abrir o app. */
    sweepAll(window);

    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);

    cards = window.document.querySelectorAll('.media-card').length;
    drawn = window.document.querySelectorAll('.media-card img').length;
    process.stdout.write('    (catálogo de ' + total + ' títulos em ' + CATEGORIES +
        ' categorias; a prateleira mostra ' + cards + ', com capa: ' + drawn + ')\n');

    /*
      O ponto do teste. A primeira página da prateleira é do começo do catálogo,
      e é justamente essa arte que o descarte LRU joga fora primeiro. Foi o que
      o usuário viu: cartões de texto com uma ou outra capa.
    */
    check('todo cartão da primeira página tem a sua capa',
        drawn === cards);

    process.stdout.write('A arte sobrevive a um catálogo maior que o teto de memória\n');
    /*
      A memória tem teto: com 2400 títulos ela não guarda todos, e não deve —
      guardar tudo numa TV é o que o teto existe para impedir. A prova de que a
      correção funciona é o descarte ter acontecido e a capa aparecer mesmo
      assim, porque ela vem do registro gravado.
    */
    check('o teto de memória continua valendo, e o descarte aconteceu',
        window.BuroApp._artworkCount() < total);
    check('a capa do primeiro título já saiu da memória',
        !window.BuroApp._artworkFor('i0-0'));
    check('e ainda assim ele é desenhado com capa, vinda do registro',
        (window.document.querySelector('.media-card img') || {}).getAttribute &&
        window.document.querySelector('.media-card img').getAttribute('src')
            .indexOf('i0-0') > 0);

    /*
      A capa passa a ser gravada, mas a preocupação que a mantinha fora do disco
      continua valendo: uma URL de provedor pode carregar usuário e senha no
      próprio caminho. O que decide é a mesma peneira dos lembretes, e ela não
      pode ser afrouxada para "fazer a capa aparecer".
    */
    process.stdout.write('Capa com credencial não é gravada, nem para aparecer\n');
    check('capa pública é gravável',
        window.BuroDomain.isStorableReminderArtwork('https://cdn.provedor.com/capas/duna.jpg'));
    check('o caminho autenticado do provedor é recusado',
        !window.BuroDomain.isStorableReminderArtwork('http://h/movie/usuario/senha/4567.jpg'));
    check('qualquer query string é recusada, é onde vive o token',
        !window.BuroDomain.isStorableReminderArtwork('https://cdn.com/a.jpg?token=abc'));
    check('usuário:senha embutido é recusado',
        !window.BuroDomain.isStorableReminderArtwork('https://user:senha@cdn.com/a.jpg'));
    check('e o item guarda nulo quando a capa não passa na peneira',
        window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: 'x', name: 'X', contentType: 'MOVIE',
            logoUrl: 'https://cdn.com/a.jpg?token=abc'
        }).logoUrl === null);

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
