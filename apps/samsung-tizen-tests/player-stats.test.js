/*
  As leituras da reproducao, para diagnosticar uma lista sem sair da TV.

  Uma imagem que trava com bitrate alto e problema de rede; a mesma imagem com
  bitrate baixo e o provedor entregando menos do que promete. Sem os numeros as
  duas se parecem, e o cliente liga dizendo so que "esta travando".

  O que este teste guarda e sobretudo a **ausencia**: o AVPlay nao e uniforme
  entre modelos, e o que a TV nao devolver nao pode virar um tracinho na tela.
  Uma linha "Bitrate: —" parece um numero que deveria estar la e nao esta, e faz
  a pessoa duvidar do aplicativo em vez da lista.
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

/* `avplay` finge o que o firmware desta TV imaginaria expoe. */
function loadApp(avplay) {
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
    if (avplay) { window.webapis = { avplay: avplay }; }
    scripts.forEach(function (script) { window.eval(fs.readFileSync(path.join(APP_DIR, script), 'utf8')); });
    window.BuroApp.init();
    return window;
}

function readings(window) {
    return Array.prototype.slice.call(
        window.document.querySelectorAll('#player-menu [data-player-option]')
    ).map(function (button) { return button.textContent; });
}

async function openStats(window) {
    window.BuroApp._setCurrentPlaybackForTest({
        itemId: 'x', title: 'Filme de teste', contentType: 'MOVIE', positionMs: 1000, durationMs: 600000
    });
    window.document.body.classList.add('playing');
    window.BuroApp._openPlayerMenu('STATS');
    await waitFor(function () { return readings(window).length > 0; }, 4000);
}

/* Uma TV que responde tudo. */
var generous = {
    getStreamingProperty: function (name) {
        if (name === 'CURRENT_BANDWIDTH') { return '4200000'; }
        return '';
    },
    getCurrentStreamInfo: function () {
        return [{ type: 'VIDEO', extra_info: JSON.stringify({ Width: 1920, Height: 1080, Codec: 'H264' }) }];
    }
};

/* Uma TV que nao expoe nada disto — o caso comum nos modelos antigos. */
var silent = {};

/* Uma TV que lanca em vez de devolver vazio. */
var hostile = {
    getStreamingProperty: function () { throw new Error('nao suportado'); },
    getCurrentStreamInfo: function () { throw new Error('nao suportado'); }
};

async function run() {
    var window;
    var lines;

    process.stdout.write('Uma TV que informa mostra os numeros\n');
    window = loadApp(generous);
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openStats(window);
    lines = readings(window).join(' | ');
    check('mostra a resolucao que a TV informou',
        lines.indexOf('1920') >= 0 && lines.indexOf('1080') >= 0);
    check('mostra o codec',
        lines.indexOf('H264') >= 0);
    /* 4.200.000 bits viram 4200 kbps: o numero que se compara com o que o
       provedor promete. */
    check('converte a banda para kbps',
        lines.indexOf('4200') >= 0);
    check('e nomeia o titulo que esta tocando',
        lines.indexOf('Filme de teste') >= 0);
    window.close();

    process.stdout.write('Uma TV que nao informa diz isso, e nao mostra tracinhos\n');
    window = loadApp(silent);
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openStats(window);
    lines = readings(window).join(' | ');
    /*
      O ponto do teste. Sem leitura, a linha nao aparece vazia — e a tela
      explica que o silencio e da TV.
    */
    check('nenhuma linha de resolucao, codec ou taxa aparece vazia',
        lines.indexOf('Resolução:') < 0 &&
        lines.indexOf('Codec:') < 0 &&
        lines.indexOf('Taxa:') < 0);
    check('e a tela explica que a TV nao informa',
        lines.indexOf(window.BuroI18n.t('playerStatsUnavailable')) >= 0);
    window.close();

    process.stdout.write('Uma TV que lanca erro nao derruba o menu\n');
    /* Alguns firmwares lancam em vez de devolver vazio. Uma excecao aqui levaria
       o menu inteiro embora, no momento em que a pessoa foi procurar ajuda. */
    window = loadApp(hostile);
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openStats(window);
    check('o menu abre mesmo assim',
        readings(window).length > 0);
    check('e cai na mesma explicacao',
        readings(window).join(' | ').indexOf(window.BuroI18n.t('playerStatsUnavailable')) >= 0);
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
    process.stderr.write('Falha na suite de leituras: ' + error.stack + '\n');
    process.exit(1);
});
