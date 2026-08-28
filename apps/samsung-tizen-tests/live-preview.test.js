/*
  A previa do canal em foco.

  E o que as listas de IPTV fazem: a lista de um lado, o canal em foco tocando
  pequeno do outro. Sem isso a unica forma de saber o que esta passando e abrir o
  canal inteiro e voltar.

  O risco e o motivo de quase tudo o que este teste guarda. Cada abertura e uma
  sessao no provedor, e ha provedores que limitam conexoes simultaneas e derrubam
  a conta por excesso. Entao:

  - **desligada por padrao** — quem nao sabe se a sua aguenta nao e exposto ao
    risco sem escolher;
  - **com atraso** — atravessar a lista com o D-pad nao pode abrir um fluxo por
    canal;
  - **cancelada ao mover** — o que estava a caminho morre antes de comecar;
  - **nunca por cima de uma reproducao** — dois fluxos e o dobro do custo, e o
    audio brigaria.
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
    /* O player e substituido por um que so anota: o AVPlay nao existe fora da
       TV, e o que este teste mede sao as **aberturas**, nao o video. */
    window._plays = [];
    window._stops = 0;
    window.BuroPlayer.play = function (url) { window._plays.push(url); return true; };
    window.BuroPlayer.stop = function () { window._stops += 1; };
    return window;
}

async function run() {
    var window = loadApp();
    var source = { id: 's', name: 'Fonte', type: 'XTREAM', channelCount: 2, createdAt: 1, updatedAt: null };
    var category = { id: 'c', sourceId: 's', providerCategoryId: 'l', name: 'Canais',
        contentType: 'LIVE', sortOrder: 0 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var channels = [];
    var index;
    var cards;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    for (index = 0; index < 2; index += 1) {
        channels.push(window.BuroDomain.createItem({
            sourceId: 's', providerItemId: 'l' + index, name: 'Canal ' + index, categoryId: 'c',
            contentType: 'LIVE', sortOrder: index,
            locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: 'l' + index, extension: 'ts' }
        }));
    }
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], channels, true]);
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, resolve, reject);
    });

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = channels;
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelectorAll('[data-action="live-details"]').length > 0;
    }, 8000);

    process.stdout.write('Desligada por padrao, nada abre\n');
    /*
      Cada abertura e uma sessao no provedor. Ligar sozinho exporia ao risco quem
      nao sabe se a conta dele aguenta.
    */
    check('a preferencia comeca desligada',
        window.BuroApp.state.preferences.livePreview !== true);
    cards = window.document.querySelectorAll('[data-action="live-details"]');
    window.BuroApp._schedulePreview(cards[0]);
    await new Promise(function (resolve) { setTimeout(resolve, 2200); });
    check('e passar pelos canais nao abre fluxo nenhum',
        window._plays.length === 0);

    process.stdout.write('Ligada, a previa espera o foco parar\n');
    window.BuroApp.state.preferences.livePreview = true;
    window.BuroApp._schedulePreview(cards[0]);
    /* Nada acontece de imediato: o atraso e o que impede a enxurrada. */
    check('nao abre no instante em que o foco chega',
        window._plays.length === 0);
    await waitFor(function () { return window._plays.length > 0; }, 4000);
    check('e abre depois de o foco ficar parado',
        window._plays.length === 1);
    /* A URL e montada com as credenciais na hora, como na reproducao normal. */
    check('com a URL do provedor resolvida',
        window._plays[0].indexOf('provider.test') >= 0 &&
        window._plays[0].indexOf('/live/') >= 0);

    process.stdout.write('Mover o foco cancela o que estava a caminho\n');
    /*
      O ponto do teste. Atravessar vinte canais nao pode abrir vinte fluxos: so
      aquele onde a pessoa parar.
    */
    window._plays = [];
    window.BuroApp._schedulePreview(cards[1]);
    window.BuroApp._schedulePreview(cards[0]);
    window.BuroApp._schedulePreview(cards[1]);
    await waitFor(function () { return window._plays.length > 0; }, 4000);
    check('tres movimentos rapidos abrem um fluxo so',
        window._plays.length === 1);

    process.stdout.write('Uma reproducao de verdade tem prioridade\n');
    /* Dois fluxos ao mesmo tempo custam o dobro, e o audio brigaria. */
    window._plays = [];
    window.document.body.classList.add('playing');
    window.BuroApp._schedulePreview(cards[0]);
    await new Promise(function (resolve) { setTimeout(resolve, 2200); });
    check('com o player aberto, a previa nao abre nada',
        window._plays.length === 0);
    window.document.body.classList.remove('playing');

    process.stdout.write('Um item que nao e canal nao vira previa\n');
    /* Um filme na lista de favoritos, por exemplo: nao ha "o que esta passando"
       para mostrar. */
    window._plays = [];
    window.BuroApp._schedulePreview(
        window.document.querySelector('[data-action="section"]')
    );
    await new Promise(function (resolve) { setTimeout(resolve, 2200); });
    check('focar um item de menu nao abre fluxo',
        window._plays.length === 0);

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
    process.stderr.write('Falha na suite de previa: ' + error.stack + '\n');
    process.exit(1);
});
