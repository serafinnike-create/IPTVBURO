/*
  Pular a abertura que a serie repete em todo episodio.

  O convite tem limites, e sao eles que este teste guarda:

  - so em episodio — um filme nao tem tema que se repita, e num canal ao vivo nao
    ha para onde saltar;
  - so dentro de uma janela: antes de trinta segundos muita serie ainda esta na
    cena que vem antes do tema, e depois de tres minutos ja e a historia;
  - so em episodio longo o bastante — noventa segundos de um episodio de cinco
    minutos seriam um terco do que existe;
  - some assim que e usado, senao convidaria a um segundo salto que ja cairia
    dentro do episodio.
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

/* Uma sessao de reproducao com a posicao que o teste quer examinar. */
function playing(window, contentType, positionMs, durationMs) {
    window.BuroApp._setCurrentPlaybackForTest({
        itemId: 'x', title: 'T', contentType: contentType,
        positionMs: positionMs, durationMs: durationMs
    });
    window.BuroApp._updateSkipIntro();
}

function inviteVisible(window) {
    var button = window.document.getElementById('player-skip-intro');
    return Boolean(button) && button.hidden === false;
}

async function run() {
    var window = loadApp();
    var hour = 45 * 60000;
    var seeked = 0;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    window.document.body.classList.add('playing');

    process.stdout.write('A janela em que o convite aparece\n');
    playing(window, 'EPISODE', 10000, hour);
    check('antes de trinta segundos nao aparece',
        !inviteVisible(window));
    playing(window, 'EPISODE', 45000, hour);
    check('dentro do primeiro minuto aparece',
        inviteVisible(window));
    playing(window, 'EPISODE', 170000, hour);
    check('perto dos tres minutos ainda aparece',
        inviteVisible(window));
    /* Depois disso ja e a historia: o botao convidaria a pular o episodio. */
    playing(window, 'EPISODE', 240000, hour);
    check('passados tres minutos some',
        !inviteVisible(window));

    process.stdout.write('So onde existe abertura para pular\n');
    playing(window, 'MOVIE', 45000, hour);
    check('um filme nao tem tema que se repita',
        !inviteVisible(window));
    playing(window, 'LIVE', 45000, 0);
    check('e num canal ao vivo nao ha para onde saltar',
        !inviteVisible(window));

    process.stdout.write('Um episodio curto nao perde um terco de si\n');
    /* Noventa segundos de um episodio de cinco minutos e uma fatia grande
       demais para um salto as cegas. */
    playing(window, 'EPISODE', 45000, 5 * 60000);
    check('episodio de cinco minutos nao oferece o salto',
        !inviteVisible(window));

    process.stdout.write('Usar o convite salta e o retira\n');
    playing(window, 'EPISODE', 45000, hour);
    (function () {
        var realSeek = window.BuroPlayer.seekBy;
        window.BuroPlayer.seekBy = function (ms) { seeked = ms; return realSeek ? true : true; };
    }());
    window.BuroApp._skipIntro();
    check('salta noventa segundos para a frente',
        seeked === 90000);
    /*
      E some. Deixa-lo na tela convidaria a um segundo salto, que ja cairia
      dentro do episodio — e o convite nao tem como saber disso.
    */
    check('e sai da tela depois de usado',
        !inviteVisible(window));

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
    process.stderr.write('Falha na suite de pular abertura: ' + error.stack + '\n');
    process.exit(1);
});
