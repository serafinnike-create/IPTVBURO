/*
  Quando um episodio acaba, o proximo se oferece.

  Antes o player fechava e devolvia a pessoa a ficha da serie, onde ela tinha de
  achar o episodio seguinte e apertar play. Numa serie de vinte episodios essa
  procura acontecia vinte vezes.

  O que este teste guarda nao e so o encadeamento — e os limites dele:

  - a ordem e por temporada e episodio, e nao a de chegada do provedor, que vem
    desordenada com frequencia e levaria do episodio 3 ao 11;
  - o ultimo episodio nao encadeia, porque nao ha para onde ir;
  - um filme nao encadeia com coisa nenhuma;
  - a contagem para quando a pessoa mexe no controle: uma TV que decide sozinha
    por voce e pior do que uma que pergunta.
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

function episode(window, source, category, season, number, order) {
    return window.BuroDomain.createItem({
        sourceId: source.id, providerItemId: 's' + season + 'e' + number,
        name: 'Episodio ' + season + 'x' + number, categoryId: category.id,
        contentType: 'EPISODE', sortOrder: order, year: 2024,
        locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: 's' + season + 'e' + number,
            season: season, episode: number }
    });
}

function press(window, keyCode) {
    var event = new window.KeyboardEvent('keydown', { keyCode: keyCode, bubbles: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
}

async function run() {
    var window = loadApp();
    var source = { id: 'source-next', name: 'Fonte', type: 'XTREAM',
        channelCount: 4, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-next', sourceId: source.id, providerCategoryId: 'series',
        name: 'Series', contentType: 'SERIES', sortOrder: 0 };
    var profile = { id: 'profile-next', name: 'Casa', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var parent;
    var episodes;
    var panel;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    parent = window.BuroDomain.createItem({
        sourceId: source.id, providerItemId: 'serie', name: 'Serie de teste',
        categoryId: category.id, contentType: 'SERIES', sortOrder: 0, year: 2024,
        locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: 'serie' }
    });

    /*
      Deliberadamente fora de ordem, e com um salto de temporada. Uma lista
      Xtream real chega assim, e encadear na ordem de chegada iria de T1E2 para
      T2E1 pulando o T1E3.
    */
    episodes = [
        episode(window, source, category, 1, 3, 0),
        episode(window, source, category, 2, 1, 1),
        episode(window, source, category, 1, 1, 2),
        episode(window, source, category, 1, 2, 3)
    ];

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = [parent].concat(episodes);
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SERIES';
    window.BuroApp.state.screenData = {
        kind: 'series', parent: parent, items: episodes, details: { title: parent.name }
    };
    window.BuroApp.render();

    process.stdout.write('O proximo sai na ordem da serie, nao na de chegada\n');
    /*
      T1E1 é o terceiro da lista que o provedor mandou; o seguinte dele é T1E2,
      o quarto. Ler a ordem de chegada devolveria T1E2 por acaso aqui, então a
      prova está no par seguinte: depois de T1E2 vem T1E3, que chegou primeiro.
    */
    check('depois de T1E1 vem T1E2',
        window.BuroApp._nextEpisodeAfter(episodes[2].id) === episodes[3]);
    check('depois de T1E2 vem T1E3, que o provedor mandou primeiro',
        window.BuroApp._nextEpisodeAfter(episodes[3].id) === episodes[0]);
    check('e depois do fim da temporada vem a seguinte',
        window.BuroApp._nextEpisodeAfter(episodes[0].id) === episodes[1]);

    process.stdout.write('O fim da serie nao encadeia\n');
    check('o ultimo episodio nao tem proximo',
        window.BuroApp._nextEpisodeAfter(episodes[1].id) === null);

    process.stdout.write('Fora de uma serie nada encadeia\n');
    /* Um filme aberto tem `kind: 'movie'`, e a lista de episodios nem existe. */
    window.BuroApp.state.screenData = { kind: 'movie', parent: parent, details: null };
    check('um filme nao encadeia com nada',
        window.BuroApp._nextEpisodeAfter(episodes[2].id) === null);

    process.stdout.write('A contagem aparece e pode ser recusada\n');
    window.BuroApp.state.screenData = {
        kind: 'series', parent: parent, items: episodes, details: { title: parent.name }
    };
    window.BuroApp._beginNextEpisodeCountdown(episodes[3]);
    panel = window.document.getElementById('player-next-panel');
    check('o painel aparece nomeando o episodio',
        panel && !panel.hidden &&
        panel.textContent.indexOf('T1 E2') >= 0);
    /*
      Uma contagem que nao diz quanto falta e uma tela travada. O segundo
      numero muda a cada segundo, entao o teste so exige que exista um.
    */
    check('e diz quanto falta',
        /\d/.test(window.document.getElementById('player-next-countdown').textContent));

    /*
      Qualquer tecla cancela: quem pegou o controle esta decidindo por si, e o
      aplicativo nao deve continuar contando por baixo dessa decisao.
    */
    window.document.body.classList.add('playing');
    press(window, 38);
    check('uma tecla qualquer cancela a contagem',
        panel.hidden === true);

    window.document.body.classList.remove('playing');
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
    process.stderr.write('Falha na suite de proximo episodio: ' + error.stack + '\n');
    process.exit(1);
});
