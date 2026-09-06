/*
  O guia envelhece sozinho — e agora se atualiza sozinho também.

  O "agora" do guia, a barra do programa em curso e a ficha de canal ao vivo
  são calculados quando a tela é desenhada. Quem fica parado no guia via o
  relógio da barra avançar enquanto a linha "agora" não se move. O Windows
  corrigiu isso alinhando o tique do guia ao minuto (`rememberGuideClock`); o
  Tizen agora porta o mesmo contrato: um tique que só redesenha quando o
  minuto virou, e apenas sobre o guia ou a ficha de canal ao vivo.

  O que este teste guarda é sobretudo quando o tique NÃO deve redesenhar: no
  mesmo minuto, fora do guia, e durante a reprodução, quando o shell está em
  `aria-hidden` sob o player.
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

function section(title) { process.stdout.write(title + '\n'); }

function waitFor(predicate, timeout) {
    var deadline = Date.now() + (timeout || 2000);
    return new Promise(function (resolve, reject) {
        (function attempt() {
            var value;
            try { value = predicate(); } catch (error) { value = null; }
            if (value) { resolve(value); return; }
            if (Date.now() > deadline) { reject(new Error('timeout')); return; }
            setTimeout(attempt, 10);
        }());
    });
}

/* Um app carregado do zero, como numa TV que acabou de ligar. O IndexedDB é
   passado de fora, como em reminders-app. */
function bootApp(factory, storedPreferences) {
    var dom = new JSDOM(fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8'), {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.indexedDB = factory;
    window.localStorage.clear();
    if (storedPreferences) {
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(storedPreferences));
    }
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    /* O tique não testa entitlement: sem uma decisão determinística a suíte
       funcional viraria teste de data, como em reminders-app. */
    window.BuroLicense.decide = function () { return { allowed: true, trial: false, expiresAt: null }; };
    return window;
}

/* Semeia perfil, fonte e um canal ao vivo: o onboarding já tem cobertura
   própria, e repeti-lo aqui só tornaria o teste frágil. */
function seed(factory) {
    return new Promise(function (resolve, reject) {
        var request = factory.open('iptvburo.catalog', 3);
        request.onupgradeneeded = function (event) {
            var database = event.target.result;
            var store;
            database.createObjectStore('profiles', { keyPath: 'id' });
            database.createObjectStore('sources', { keyPath: 'id' });
            store = database.createObjectStore('categories', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('bySourceType', ['sourceId', 'contentType'], { unique: false });
            store = database.createObjectStore('items', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('byCategory', ['sourceId', 'categoryId'], { unique: false });
            store.createIndex('byType', ['sourceId', 'contentType'], { unique: false });
            store.createIndex('bySearchOrder', ['searchRank', 'searchSort', 'id'], { unique: false });
        };
        request.onsuccess = function (event) {
            var database = event.target.result;
            var transaction = database.transaction(
                ['profiles', 'sources', 'categories', 'items'], 'readwrite'
            );
            transaction.objectStore('profiles').put({
                id: 'p1', name: 'Casa', avatarKey: 'gold', isKids: false,
                sourceId: 's1', createdAt: 1
            });
            transaction.objectStore('sources').put({
                id: 's1', name: 'Fonte', type: 'REMOTE_M3U', createdAt: 1
            });
            transaction.objectStore('categories').put({
                id: 'c1', sourceId: 's1', name: 'Canais', contentType: 'LIVE', sortOrder: 0
            });
            transaction.objectStore('items').put({
                id: 'i1', sourceId: 's1', categoryId: 'c1', contentType: 'LIVE',
                name: 'Canal Um', providerItemId: '10', sortOrder: 0,
                searchName: 'canal um', searchRank: 0, searchSort: 0,
                locator: { providerItemId: '10' }
            });
            transaction.oncomplete = function () { database.close(); resolve(); };
            transaction.onerror = function () { reject(transaction.error); };
        };
        request.onerror = function () { reject(request.error); };
    });
}

/* Idioma escolhido e aviso legal aceitos: os gates de entrada já têm cobertura
   própria. O app abre na Home e o teste navega ao guia, como uma pessoa faria. */
var PREFERENCES = {
    acceptedLegal: true, languageSelected: true, language: 'pt-BR',
    activeProfileId: 'p1', section: 'HOME',
    hiddenCategoryIds: [], lockedCategoryIds: [], uses24HourClock: true
};

/* Um nó que só sobrevive se a tela NÃO foi redesenhada. */
function plantSentinel(window) {
    var marker = window.document.createElement('em');
    marker.className = 'test-sentinel';
    marker.textContent = 'SENTINELA';
    var host = window.document.querySelector('.content');
    if (host) { host.appendChild(marker); }
    return Boolean(host);
}

function sentinelSurvived(window) {
    return Boolean(window.document.querySelector('.test-sentinel'));
}

(async function run() {
    var factory = new fakeIndexedDb.IDBFactory();
    var window;
    var state;

    await seed(factory);

    window = bootApp(factory, PREFERENCES);
    window.BuroApp.init();
    await waitFor(function () { return window.document.querySelector('.shell'); }, 4000);
    state = window.BuroApp.state;
    state.section = 'GUIDE';
    window.BuroApp.render();
    await waitFor(function () { return window.document.querySelector('.guide-channel'); }, 4000);

    /* O relógio real do guia (setInterval de 1 s) correria contra as
       verificações: numa virada de minuto ele redesenha no meio do teste e
       derruba o marcador. Parado, só os tiques explícitos abaixo agem. */
    window.BuroApp._guideClockStop();

    section('No guia, a virada de minuto redesenha a tela');
    (function () {
        var sameMinute = Math.floor(Date.now() / 60000) * 60000;
        check('o guia foi desenhado', Boolean(window.document.querySelector('.guide-layout')));
        /* Alinha o minuto interno ao do teste: o boot selou o relógio no minuto
           em que o app iniciou, que pode ser o anterior ao desta verificação. */
        window.BuroApp._guideClockTick(sameMinute);
        check('o marcador foi plantado', plantSentinel(window));
        check('o mesmo minuto não redesenha',
            window.BuroApp._guideClockTick(sameMinute) === false && sentinelSurvived(window));
        check('o minuto seguinte redesenha',
            window.BuroApp._guideClockTick(sameMinute + 60000) === true && !sentinelSurvived(window));
    }());

    section('Fora do guia, a virada de minuto não toca na tela');
    (function () {
        state.section = 'SEARCH';
        window.BuroApp.render();
        check('a busca foi desenhada', Boolean(window.document.querySelector('#search-query')));
        check('o marcador foi plantado', plantSentinel(window));
        check('o minuto seguinte não redesenha a busca',
            window.BuroApp._guideClockTick(Math.floor(Date.now() / 60000) * 60000 + 120000) === false &&
            sentinelSurvived(window));
    }());

    section('A ficha de canal ao vivo também acompanha o minuto');
    (function () {
        var agora = Math.floor(Date.now() / 1000);
        state.section = 'LIVE';
        state.screenData = {
            kind: 'live',
            parent: { id: 'i1', name: 'Canal Um', contentType: 'LIVE', locator: {} },
            schedule: [{
                title: 'Jornal', description: '',
                startEpochSeconds: agora - 300, endEpochSeconds: agora + 300
            }],
            epgLoading: false
        };
        window.BuroApp.render();
        check('a ficha ao vivo foi desenhada',
            Boolean(window.document.querySelector('.live-detail')));
        check('o marcador foi plantado', plantSentinel(window));
        check('o minuto seguinte redesenha a ficha',
            window.BuroApp._guideClockTick(Math.floor(Date.now() / 60000) * 60000 + 180000) === true &&
            !sentinelSurvived(window));
    }());

    section('Durante a reprodução, o tique não redesenha nada');
    (function () {
        window.document.body.classList.add('playing');
        check('o marcador foi plantado', plantSentinel(window));
        check('o player protege a tela do tique',
            window.BuroApp._guideClockTick(Math.floor(Date.now() / 60000) * 60000 + 240000) === false &&
            sentinelSurvived(window));
        window.document.body.classList.remove('playing');
    }());

    process.stdout.write('\n' + passed + ' verificações passaram' +
        (failures.length ? '; ' + failures.length + ' falharam' : '') + '\n');
    if (failures.length) {
        failures.forEach(function (label) { process.stderr.write('  FALHA ' + label + '\n'); });
        process.exit(1);
    }
    /* O relógio do guia fica em setInterval dentro do jsdom (app.js), e timers
       do jsdom são timers do Node: sem encerrar aqui o processo nunca sai e a
       cadeia `node a && node b` do npm test trava no próximo arquivo. */
    process.exit(0);
}()).catch(function (error) {
    process.stderr.write('  FALHA inesperada: ' + (error && error.message || error) + '\n');
    process.exit(1);
});
