/*
  As capas da prateleira de catálogo.

  A arte chega depois do desenho: o catálogo é lido do banco, a prateleira
  aparece, e só então as capas são pedidas ao provedor. Sem alguém redesenhando
  quando elas chegam, ficam guardadas em memória e nunca aparecem — que foi
  exatamente o defeito visto na TV, com cartões de texto e nenhuma capa.
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

/* Uma fonte Xtream com uma categoria e três filmes gravados. */
function seed(window, artwork) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 3, createdAt: 1, updatedAt: null };
    var items = [];
    var index;
    for (index = 1; index <= (window.__seedCount || 3); index += 1) { items.push(index); }
    items = items.map(function (index) {
        var item = window.BuroDomain.createItem({
            sourceId: 's1', providerItemId: String(index), name: 'Filme ' + index,
            categoryId: 'c1', contentType: 'MOVIE', year: 2024, rating: 8,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: String(index) }
        });
        item.id = 'item-' + index;
        return item;
    });
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.categories = [{
        id: 'c1', sourceId: 's1', providerCategoryId: '1',
        name: 'Filmes | Ação', contentType: 'MOVIE', sortOrder: 0
    }];
    /* Sem segredo, `hydrateCategoryArtwork` desiste antes de pedir. */
    return new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, function () {
            window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success) {
                success([], artwork);
            };
            window.BuroStorage.putBatch('items', items, resolve, reject);
        }, reject);
    });
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

async function run() {
    var window;
    var blockSize;

    process.stdout.write('A capa aparece depois de chegar\n');
    window = loadApp();
    await reachShell(window);
    await seed(window, {
        'item-1': 'https://art.test/um.jpg',
        'item-2': 'https://art.test/dois.jpg',
        'item-3': 'https://art.test/tres.jpg'
    });
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);
    check('a prateleira desenha os títulos que o banco tem',
        window.document.querySelectorAll('.media-card').length === 3);

    /*
      O ponto do teste: a arte chega depois, e a tela tem de se redesenhar
      sozinha. Sem isso ela fica guardada em memória e nunca vira imagem.
    */
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card img').length === 3;
    }, 8000);
    check('cada cartão ganha a sua capa quando ela chega',
        window.document.querySelectorAll('.media-card img').length === 3);
    check('a capa é a que o provedor mandou para aquele título',
        window.document.querySelector('.media-card img').getAttribute('src') === 'https://art.test/um.jpg');
    check('o cartão é marcado como tendo arte, para o CSS diferenciá-lo',
        window.document.querySelectorAll('.media-card.has-art').length === 3);

    /*
      O bloco: a prateleira não monta o catálogo inteiro de uma vez.

      Duzentos cartões prendiam o controle da TV enquanto o DOM era montado. Dez
      por vez desenha na hora, e quem chega ao fim pede o próximo.
    */
    process.stdout.write('A prateleira cresce em blocos\n');
    window.close();
    window = loadApp();
    /* Dois blocos e sobra, para ver o crescimento e o fim da lista. O tamanho
       do bloco é lido do app: fixá-lo aqui faria este teste quebrar toda vez
       que ele fosse ajustado para encher a fileira da TV. */
    blockSize = window.BuroApp._catalogueBlockSize();
    window.__seedCount = blockSize * 2 + 3;
    await reachShell(window);
    await seed(window, {});
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 8000);
    check('o primeiro bloco é um bloco, e não o catálogo inteiro',
        window.document.querySelectorAll('.media-card').length === blockSize);
    check('a contagem diz quanto já veio e quanto existe',
        (window.document.querySelector('.catalogue-shelf-heading p').textContent || '')
            .indexOf(String(blockSize * 2 + 3)) > 0);
    check('havendo mais, a prateleira oferece carregar',
        Boolean(window.document.querySelector('[data-action="catalogue-shelf-more"]')));

    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-shelf-more"]'));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === blockSize * 2;
    }, 8000);
    check('carregar mais acrescenta ao que já estava, sem recomeçar',
        window.document.querySelectorAll('.media-card').length === blockSize * 2);

    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-shelf-more"]'));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === blockSize * 2 + 3;
    }, 8000);
    check('o último bloco traz só o que resta',
        window.document.querySelectorAll('.media-card').length === blockSize * 2 + 3);
    check('sem mais nada para vir, o botão sai da tela',
        !window.document.querySelector('[data-action="catalogue-shelf-more"]'));

    /*
      Escolher um filtro enquanto a prateleira ainda carrega.

      Numa lista de quarenta mil títulos a primeira carga leva segundos, que é
      exatamente quando alguém estende a mão para o seletor. A carga em
      andamento respondia à pergunta antiga, e a nova era recusada por já haver
      uma em curso: a antiga terminava, escrevia o resultado sem filtro, e nada
      tentava de novo.

      A sobreposição que provoca o defeito depende de um redesenho extra chegar
      no meio da carga — `service-selector.test.js` a produz de forma confiável,
      porque a construção do índice de serviços redesenha por conta própria.
      Aqui fica a versão simples: escolher durante a carga tem de valer, e a
      tela não pode ficar presa em "carregando".
    */
    process.stdout.write('Um filtro escolhido durante a carga vale mesmo assim\n');
    window.close();
    window = loadApp();
    window.__seedCount = 6;
    await reachShell(window);
    await seed(window, {});
    /*
      A leitura é atrasada de propósito para garantir a sobreposição.

      Sem isso a carga termina antes de o seletor abrir e o teste passa mesmo
      com o defeito presente — que foi o que aconteceu na primeira versão dele.
      Na TV o atraso é real: são dezenas de milhares de linhas.
    */
    (function () {
        var realCountWhere = window.BuroStorage.countWhere;
        window.BuroStorage.countWhere = function (store, matcher, success, failure) {
            return realCountWhere(store, matcher, function (total) {
                window.setTimeout(function () { success(total); }, 120);
            }, failure);
        };
    }());
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    /* Sem esperar a prateleira: o ponto é escolher com a carga em voo. */
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="catalogue-pick-year"]'));
    }, 8000);
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-pick-year"]'));
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-picker="year"][data-value="2024"]'));
    }, 8000);
    window.BuroApp._activate(window.document.querySelector('[data-picker="year"][data-value="2024"]'));
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 6;
    }, 8000);
    check('a prateleira carrega com o filtro que foi escolhido',
        window.document.querySelectorAll('.media-card').length === 6);
    check('e a carga não fica presa em "carregando"',
        !window.document.querySelector('.search-loading'));

    process.stdout.write('Sem arte, o cartão continua legível\n');
    window.close();
    window = loadApp();
    await reachShell(window);
    await seed(window, {});
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length === 3;
    }, 8000);
    check('um provedor sem arte não deixa a prateleira vazia',
        window.document.querySelectorAll('.media-card').length === 3);
    check('e o cartão não finge ter imagem',
        window.document.querySelectorAll('.media-card img').length === 0 &&
        window.document.querySelectorAll('.media-card.has-art').length === 0);
    check('o título continua sendo lido no cartão',
        window.document.querySelector('.media-card h3').textContent.indexOf('Filme') === 0);
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

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exitCode = 1;
});
