/*
  Escolher um filtro nao pode dizer "nenhum titulo" antes de ler.

  O usuario escolheu 2026 e a tela respondeu "Nenhum titulo com estes filtros"
  por varios segundos, com o contador em "0 de 9056"; so entao a lista apareceu.
  A afirmacao estava errada duas vezes: o filtro tinha centenas de titulos, e o
  9056 era o total da pergunta anterior.

  A causa e uma janela de estado. `chooseCatalogueOption` limpa `scope.rows`,
  devolve `scope.loading` a falso e desenha; a carga nova so e agendada dentro
  do desenho seguinte. Nesse quadro `rows` esta vazio e `loading` esta falso, que
  antes era indistinguivel de "a leitura terminou e nao achou nada".

  `scope.rows === undefined` e o que separa os dois: nunca lido nao e o mesmo que
  lido e vazio.
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

function bodyText(window) {
    return window.document.body.textContent.replace(/\s+/g, ' ');
}

/*
  Escolher pelo mesmo caminho que o controle remoto: abrir o seletor e acionar a
  opcao. Chamar a funcao interna testaria uma porta que a pessoa nao usa — e
  aqui o que importa e justamente o quadro que o clique real deixa na tela.
*/
function pick(window, picker, value) {
    var chip = window.document.querySelector('[data-action="catalogue-pick-' + picker + '"]');
    var option;
    window.BuroApp._activate(chip);
    option = window.document.querySelector(
        '[data-action="catalogue-option"][data-picker="' + picker + '"][data-value="' + value + '"]'
    );
    if (!option) { throw new Error('opcao ' + picker + '=' + value + ' nao esta no seletor'); }
    window.BuroApp._activate(option);
}

async function run() {
    var window = loadApp();
    var source = { id: 'source-filter-loading', name: 'Fonte', type: 'XTREAM',
        channelCount: 60, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-filter-loading', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-filter-loading', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var index;
    var baseline;
    var duringLoad;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    /* Metade de 2026 e metade de 2024: o filtro por ano precisa ter uma resposta
       de verdade, senao "nenhum titulo" seria simplesmente verdade. */
    for (index = 0; index < 60; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'movie-' + index, name: 'Filme ' + index,
            categoryId: category.id, contentType: 'MOVIE', sortOrder: index,
            year: index % 2 === 0 ? 2026 : 2024, rating: 7,
            locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'movie-' + index }
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
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    /* A prateleira sem filtro precisa ter carregado antes, para que o total
       antigo exista — ele e metade do defeito. */
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 6000);
    /* O escopo e interno ao modulo, entao a medicao e pelo DOM — que e o que a
       pessoa ve, e o objeto desta prova. */
    baseline = bodyText(window);
    check('a prateleira sem filtro carrega e mostra um total',
        window.document.querySelectorAll('.media-card').length > 0 &&
        / de 60/.test(baseline));

    process.stdout.write('O quadro logo apos escolher o filtro\n');
    /*
      O momento exato do defeito: `chooseCatalogueOption` desenha de forma
      sincrona antes de a leitura comecar. Ler o DOM aqui, sem esperar nada, e
      olhar para o quadro que o usuario viu por cinco segundos.
    */
    pick(window, 'year', '2026');
    duringLoad = bodyText(window);
    check('nao afirma que nenhum titulo casa o filtro',
        duringLoad.indexOf('Nenhum título com estes filtros') === -1);
    check('mostra que esta carregando',
        Boolean(window.document.querySelector('.search-loading')));
    /*
      E nao mostra o total antigo. "0 de 9056" respondia a pergunta anterior; o
      contador some ate a contagem nova chegar.
    */
    check('nao mostra o contador da pergunta anterior',
        duringLoad.indexOf(' de 60') === -1);

    process.stdout.write('E quando a leitura chega\n');
    await waitFor(function () {
        return window.document.querySelectorAll('.media-card').length > 0;
    }, 6000);
    check('a lista filtrada aparece',
        window.document.querySelectorAll('.media-card').length > 0);
    /* Trinta dos sessenta sao de 2026, e e esse o total que o contador deve
       passar a mostrar depois da leitura nova. */
    check('e o contador passa a responder a pergunta nova',
        / de 30/.test(bodyText(window)));

    process.stdout.write('Um filtro que de fato nao casa nada continua dizendo isso\n');
    /*
      A correcao nao pode ter apagado o estado vazio — ele e certo depois da
      leitura. Um ano sem nenhum titulo tem de continuar oferecendo limpar.
    */
    pick(window, 'year', '2024');
    pick(window, 'rating', '9');
    await waitFor(function () {
        return !window.document.querySelector('.search-loading');
    }, 6000);
    check('depois de ler, o vazio verdadeiro e dito',
        bodyText(window).indexOf('Nenhum título com estes filtros') >= 0 &&
        Boolean(window.document.querySelector('[data-action="catalogue-scope-reset"]')));

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
    process.stderr.write('Falha na suite de filtro carregando: ' + error.stack + '\n');
    process.exit(1);
});
