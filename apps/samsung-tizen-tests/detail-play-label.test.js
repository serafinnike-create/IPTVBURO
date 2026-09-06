/*
  O botão principal da ficha de filme diz o que vai fazer.

  O Windows rotula o botão com a decisão de retomada — “Continuar de 12:03”
  quando há progresso, “Assistir novamente” quando o título foi concluído — em
  vez de um “Assistir” genérico que esconde se a posição vai ser conservada.
  O Tizen agora usa o mesmo contrato, decidido pela mesma função do player
  (`BuroDomain.resumeDecision`), para o rótulo nunca prometer uma posição que
  o toque não vai cumprir. O prompt de retomada no toque continua: é a
  adaptação de TV contra o ENTER acidental.

  O que este teste guarda é sobretudo quando o rótulo NÃO deve mudar: sem
  progresso, ou com uma posição abaixo do limiar de retomada, o botão segue
  “Assistir”.
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

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return []; },
            saveData: function (name, value, password, success) { success(); },
            getData: function () { throw { name: 'NotFoundError' }; },
            removeData: function () {}
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    return window;
}

var window = loadApp();
window.BuroApp.state.activeProfile = { id: 'p1', name: 'Ana' };
window.BuroDownloads.list = function () { return []; };
window.BuroDownloads.enabled = function () { return true; };

var MOVIE = { id: 'm1', name: 'Um Filme', contentType: 'MOVIE' };

function progressEntry(overrides) {
    var entry = {
        id: 'r1', profileId: 'p1', itemId: 'm1',
        positionMs: 723000, durationMs: 5400000, updatedAt: 1
    };
    Object.keys(overrides || {}).forEach(function (key) { entry[key] = overrides[key]; });
    return entry;
}

process.stdout.write('Sem progresso, o botão continua Assistir\n');
(function () {
    window.BuroApp.state.progress = [];
    check('o rótulo é o genérico', window.BuroApp._detailPlayLabel(MOVIE) === 'Assistir');
    var html = window.BuroApp._detailActionsHtml(MOVIE, false, [], null);
    check('o botão principal leva o rótulo genérico', html.indexOf('>Assistir</button>') > 0);
    check('nenhuma promessa de posição aparece', html.indexOf('Continuar de') < 0);
}());

process.stdout.write('Com progresso retomável, o botão diz de onde volta\n');
(function () {
    window.BuroApp.state.progress = [progressEntry()];
    check('o rótulo carrega o horário', window.BuroApp._detailPlayLabel(MOVIE) === 'Continuar de 12:03');
    var html = window.BuroApp._detailActionsHtml(MOVIE, false, [], null);
    check('o botão principal carrega o horário', html.indexOf('>Continuar de 12:03</button>') > 0);
}());

process.stdout.write('Concluído, o botão convida a ver de novo\n');
(function () {
    window.BuroApp.state.progress = [progressEntry({ completed: true, positionMs: 5400000 })];
    check('o rótulo é o de rever', window.BuroApp._detailPlayLabel(MOVIE) === 'Assistir novamente');
    var html = window.BuroApp._detailActionsHtml(MOVIE, false, [], null);
    check('o botão principal convida a rever', html.indexOf('>Assistir novamente</button>') > 0);
    check('não promete continuar', html.indexOf('Continuar de') < 0);
}());

process.stdout.write('Posição abaixo do limiar de retomada não muda o rótulo\n');
(function () {
    window.BuroApp.state.progress = [progressEntry({ positionMs: 10000 })];
    check('o rótulo segue o genérico', window.BuroApp._detailPlayLabel(MOVIE) === 'Assistir');
}());

process.stdout.write('A série conserva o rótulo de episódio\n');
(function () {
    window.BuroApp.state.progress = [];
    var html = window.BuroApp._detailActionsHtml(
        { id: 's1', name: 'Uma Série', contentType: 'SERIES' }, false, [], null);
    check('sem episódios, nenhum botão principal de série', html.indexOf('series-primary-play') < 0);
}());

process.stdout.write('O rótulo existe nos cinco idiomas\n');
(function () {
    var expectations = {
        'pt-BR': 'Continuar de 12:03',
        'en': 'Resume from 12:03',
        'de': 'Fortsetzen ab 12:03',
        'it': 'Riprendi da 12:03',
        'es': 'Continuar desde 12:03'
    };
    window.BuroApp.state.progress = [progressEntry()];
    Object.keys(expectations).forEach(function (language) {
        window.BuroI18n.setLanguage(language);
        check(language + ' rotula a retomada',
            window.BuroApp._detailPlayLabel(MOVIE) === expectations[language]);
    });
    window.BuroI18n.setLanguage('pt-BR');
}());

process.stdout.write('\n' + passed + ' verificações passaram' +
    (failures.length ? '; ' + failures.length + ' falharam' : '') + '\n');
if (failures.length) {
    failures.forEach(function (label) { process.stderr.write('  FALHA ' + label + '\n'); });
    process.exit(1);
}
/* O app deixa timers de interface vivos dentro do jsdom (app.js), e timers do
   jsdom são timers do Node: sem encerrar aqui o processo nunca sai e a cadeia
   `node a && node b` do npm test trava no próximo arquivo. */
process.exit(0);
