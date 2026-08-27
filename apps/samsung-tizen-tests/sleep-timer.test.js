/*
  O temporizador de sono.

  Quem adormece assistindo deixa a TV tocando a noite inteira: banda consumida,
  painel aceso, e no dia seguinte o progresso do titulo esta no fim de um
  episodio que ninguem viu.

  O que este teste guarda:

  - as opcoes existem e "ao fim deste episodio" **nao** aparece num canal ao
    vivo, porque ali nao ha fim;
  - armar mostra quanto falta, e nao so que esta ligado;
  - o fim do episodio com o temporizador armado **para** em vez de encadear —
    quem escolheu "ao fim deste" disse que este e o ultimo;
  - sair do player desarma, senao o temporizador de uma sessao derrubaria a
    proxima.
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

function menuLabels(window) {
    return Array.prototype.slice.call(
        window.document.querySelectorAll('#player-menu [data-player-option]')
    ).map(function (button) { return button.textContent; });
}

async function run() {
    var window = loadApp();
    var labels;
    var sleepLabel;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    process.stdout.write('As opcoes de sono existem para um filme\n');
    /*
      O menu abre a partir de uma sessao de reproducao, entao o teste monta uma:
      `currentPlayback` é o que `sleepTimerOptions` lê para decidir se "ao fim
      deste episodio" faz sentido.
    */
    window.BuroApp._setCurrentPlaybackForTest({
        itemId: 'movie:1', title: 'Filme', contentType: 'MOVIE', positionMs: 0, durationMs: 600000
    });
    window.document.body.classList.add('playing');
    window.BuroApp._openPlayerMenu('SLEEP');
    labels = menuLabels(window);
    check('oferece desligar e varios intervalos',
        labels.length >= 4 &&
        labels[0].indexOf('Sem temporizador') >= 0 &&
        labels.some(function (l) { return l.indexOf('30 minutos') >= 0; }));
    check('e oferece parar ao fim do episodio',
        labels.some(function (l) { return l.indexOf('Ao fim deste episódio') >= 0; }));

    process.stdout.write('Num canal ao vivo nao ha fim de episodio a esperar\n');
    /*
      A opcao seria uma promessa que o conteudo nao pode cumprir: um canal ao
      vivo nao termina, entao o temporizador nunca dispararia.
    */
    window.BuroApp._setCurrentPlaybackForTest({
        itemId: 'live:1', title: 'Canal', contentType: 'LIVE', positionMs: 0, durationMs: 0
    });
    window.BuroApp._openPlayerMenu('SLEEP');
    labels = menuLabels(window);
    check('os intervalos continuam, porque a TV fica a noite toda no ao vivo',
        labels.some(function (l) { return l.indexOf('60 minutos') >= 0; }));
    check('mas "ao fim deste episodio" nao aparece',
        !labels.some(function (l) { return l.indexOf('Ao fim deste episódio') >= 0; }));

    process.stdout.write('Armar mostra quanto falta, e nao so que esta ligado\n');
    /* Sem o numero a pessoa nao sabe se da tempo de ver o resto, e desliga por
       duvida — que e o mesmo que nao ter o temporizador. */
    window.BuroApp._setSleepTimer(30);
    sleepLabel = window.document.getElementById('player-sleep-label');
    check('o rotulo aparece com os minutos restantes',
        sleepLabel && !sleepLabel.hidden && /30/.test(sleepLabel.textContent));

    process.stdout.write('Desarmar esconde o rotulo\n');
    window.BuroApp._setSleepTimer(null);
    check('sem temporizador, sem rotulo',
        window.document.getElementById('player-sleep-label').hidden === true);

    process.stdout.write('"Ao fim deste episodio" ganha do encadeamento\n');
    /*
      O ponto mais importante. Com o temporizador armado, terminar o episodio
      tem de **parar** — oferecer o proximo, e comeca-lo sozinho em dez
      segundos, seria o oposto do que a pessoa pediu.
    */
    window.BuroApp._setSleepTimer('EPISODE');
    check('armado ao fim do episodio, o rotulo diz isso',
        /episódio/i.test(window.document.getElementById('player-sleep-label').textContent));

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
    process.stderr.write('Falha na suite de temporizador: ' + error.stack + '\n');
    process.exit(1);
});
