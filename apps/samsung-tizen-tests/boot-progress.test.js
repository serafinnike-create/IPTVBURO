/*
  A tela de carregamento: o que ela mostra e o que ela deixa pronto.

  Relato do usuário, com captura mostrando "10/98 categorias" e a barra imóvel:
  "nao deixe porcentagem parada para user achar que travou". A barra dividia
  cem por cinco passos, então saltava de vinte em vinte e ficava parada durante
  a varredura — que é o passo longo, minutos numa lista de dezenas de milhares.

  E logo depois: "quando vou para tela de incio ainda esta preparaando tela de
  incio isso ja deveria ser feito na tela de carregamento". Quem esperou a
  varredura inteira ainda encontrava "Montando sua Home…" na primeira tela.
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

var window = loadApp();
var steps = window.BuroApp._bootSteps();
var percent = window.BuroApp._bootProgressPercent;
var sweepAt = steps.indexOf('sweep');

process.stdout.write('A barra acompanha a varredura, em vez de ficar parada\n');
check('a varredura é um passo próprio da abertura',
    sweepAt >= 0);
/*
  O ponto do teste: sem fonte a fatia do passo é preenchida de uma vez, mas com
  a varredura em curso ela tem de crescer com as categorias. Sem isso a barra
  fica imóvel durante minutos e se lê como travamento.
*/
(function () {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM' };
    var status = { state: 'RUNNING', completed: 0, total: 98, itemCount: 0 };
    var readings = [];
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroCatalogueSync.progress = function () { return status; };
    [0, 10, 49, 98].forEach(function (done) {
        status.completed = done;
        readings.push(percent({ index: sweepAt, total: steps.length }));
    });
    check('a barra anda conforme as categorias entram',
        readings[0] < readings[1] && readings[1] < readings[2] && readings[2] < readings[3]);
    check('e o começo da varredura não mostra a fatia já cheia',
        readings[0] < readings[3]);
    /* Cada passo vale uma fatia igual: a varredura terminada não pode passar do
       que o passo seguinte vale, senão a barra andaria para trás depois. */
    check('a fatia da varredura não invade a do passo seguinte',
        readings[3] <= Math.round((sweepAt + 1) / steps.length * 100));
}());

process.stdout.write('Sem varredura, a barra não trava nem mente\n');
window.BuroCatalogueSync.progress = function () { return null; };
check('o primeiro passo não começa em zero, para não parecer inerte',
    percent({ index: 0, total: steps.length }) > 0);
check('o último passo chega a cem',
    percent({ index: steps.length - 1, total: steps.length }) === 100);
check('a barra nunca passa de cem',
    percent({ index: 99, total: steps.length }) === 100);

process.stdout.write('A porcentagem aparece dentro da própria barra\n');
window.BuroApp.state.screen = 'BOOT';
window.BuroApp.state.boot = {
    step: 'sweep', index: sweepAt, total: steps.length,
    messageKey: 'bootCatalogueSweep', fraction: 0.5, previewArtwork: []
};
window.BuroApp.render();
(function () {
    var bar = window.document.querySelector('.boot-progress');
    var value = window.document.querySelector('.boot-progress-value');
    var fill = window.document.querySelector('.boot-progress-fill');
    check('a barra mostra uma porcentagem numérica visível',
        Boolean(value && /^\d+%$/.test(value.textContent)));
    check('o texto usa exatamente o valor acessível da barra',
        Boolean(bar && value && value.textContent === bar.getAttribute('aria-valuenow') + '%'));
    check('o preenchimento usa a mesma porcentagem',
        Boolean(fill && fill.style.width === value.textContent));
    check('o fundo local é composto por doze capas leves',
        window.document.querySelectorAll('.boot-backdrop.local .boot-cover-row > span').length === 12);
    check('somente duas fileiras são animadas',
        window.document.querySelectorAll('.boot-backdrop.local > .boot-cover-row').length === 2);
}());

process.stdout.write('A Home é montada na abertura, não depois dela\n');
/*
  O ponto: `home` precisa ser um passo da abertura. Sem isso quem esperou a
  varredura inteira ainda encontrava "Montando sua Home…" na primeira tela —
  o trabalho é o mesmo, o lugar dele é a tela que existe para esperar.
*/
check('montar a Home é um passo da abertura',
    steps.indexOf('home') >= 0);
check('e vem antes de a interface aparecer',
    steps.indexOf('home') < steps.indexOf('ready'));
check('a varredura vem antes da Home, que depende do catálogo',
    steps.indexOf('sweep') < steps.indexOf('home'));

process.stdout.write('A espera longa é explicada, em vez de silenciosa\n');
check('existe texto dizendo por que a primeira vez demora',
    window.BuroI18n.t('bootFirstRunNote').length > 40);
check('ele promete que a próxima abertura é mais rápida',
    /rápid/i.test(window.BuroI18n.t('bootFirstRunNote')));
/* O usuário pediu explicitamente que o texto falasse do pendrive/HD. */
check('e menciona o pendrive ou HD na USB',
    /USB|pendrive/i.test(window.BuroI18n.t('bootFirstRunNote')));
check('o texto existe nos cinco idiomas',
    ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
        return window.BuroI18n.t('bootFirstRunNote', language) &&
            window.BuroI18n.t('bootFirstRunNote', language).indexOf('bootFirstRunNote') < 0;
    }));

window.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
