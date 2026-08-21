/*
  O progresso do download na barra superior.

  Pedido do usuário: "barra de dawlaond deve aparecer na barra superior com
  tempo velocidade". `js/downloads.js` já media velocidade e estimativa; o que
  faltava era a TV mostrar. Uma cópia para o USB leva dezenas de minutos, e sem
  isto o único lugar onde ela aparecia era a tela de Downloads.

  O que este teste guarda é sobretudo quando o chip NÃO deve aparecer: um chip
  parado em "0%" ocupa a barra sem dizer nada.
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

/* Uma fila de downloads, como `BuroDownloads.list()` a descreveria. */
function withQueue(window, enabled, entries) {
    window.BuroDownloads.enabled = function () { return enabled; };
    window.BuroDownloads.list = function () { return entries; };
    return window.BuroApp._downloadChipHtml();
}

var window = loadApp();

process.stdout.write('Baixando, o chip mostra quanto falta e a que velocidade\n');
(function () {
    var html = withQueue(window, true, [{
        id: 'd1', name: 'Um Filme', state: 'RUNNING', percent: 42,
        bytesPerSecond: 3 * 1048576, remainingSeconds: 630
    }]);
    check('a porcentagem aparece', html.indexOf('42%') > 0);
    check('a velocidade aparece em MB/s', html.indexOf('3.0 MB/s') > 0);
    check('o tempo restante aparece em minutos',
        html.indexOf('faltam 11 min') > 0 || html.indexOf('faltam 10 min') > 0);
    /* A barra é o que se lê de relance, sem precisar ler número nenhum. */
    check('a barra de progresso acompanha a porcentagem',
        html.indexOf('width:42%') > 0);
    check('o chip leva à tela de Downloads',
        html.indexOf('data-section="DOWNLOADS"') > 0);
    check('e se anuncia por extenso para leitores de tela',
        html.indexOf('aria-label="Baixando Um Filme, 42 por cento"') > 0);
}());

process.stdout.write('Sem download em curso, o chip não ocupa a barra\n');
check('fila vazia não desenha chip',
    withQueue(window, true, []) === '');
/*
  O ponto do teste: só na fila não basta. Um chip em "0%" para um item que
  ainda não começou ocuparia a barra sem dizer nada.
*/
check('item só enfileirado não desenha chip',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'QUEUED', percent: 0 }]) === '');
check('item pausado não desenha chip',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'PAUSED', percent: 30 }]) === '');
check('sem USB, não há download e não há chip',
    withQueue(window, false, [{ id: 'd1', name: 'X', state: 'RUNNING', percent: 42 }]) === '');

process.stdout.write('A fila atrás do que está baixando é contada\n');
(function () {
    var html = withQueue(window, true, [
        { id: 'd1', name: 'Um', state: 'RUNNING', percent: 10, bytesPerSecond: 0, remainingSeconds: null },
        { id: 'd2', name: 'Dois', state: 'QUEUED', percent: 0 },
        { id: 'd3', name: 'Três', state: 'QUEUED', percent: 0 }
    ]);
    check('o chip diz quantos esperam atrás', html.indexOf('+2 na fila') > 0);
    /* Velocidade zero é "ainda não sei", não "parado": omitir diz mais. */
    check('sem velocidade medida, o chip não inventa um número',
        html.indexOf('MB/s') < 0 && html.indexOf('kB/s') < 0);
}());

process.stdout.write('As unidades acompanham a grandeza\n');
check('abaixo de 0,1 MB/s a leitura passa a kB/s',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'RUNNING', percent: 5,
        bytesPerSecond: 60000, remainingSeconds: null }]).indexOf('kB/s') > 0);
check('acima de 10 MB/s a casa decimal some',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'RUNNING', percent: 5,
        bytesPerSecond: 25 * 1048576, remainingSeconds: null }]).indexOf('25 MB/s') > 0);
check('menos de um minuto é contado em segundos',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'RUNNING', percent: 90,
        bytesPerSecond: 1048576, remainingSeconds: 12 }]).indexOf('faltam 12s') > 0);
check('mais de uma hora é contado em horas',
    withQueue(window, true, [{ id: 'd1', name: 'X', state: 'RUNNING', percent: 2,
        bytesPerSecond: 1048576, remainingSeconds: 7200 }]).indexOf('faltam 2 h') > 0);

process.stdout.write('Um módulo de downloads com defeito não derruba a barra\n');
check('list() que lança não impede a barra de desenhar',
    (function () {
        window.BuroDownloads.enabled = function () { return true; };
        window.BuroDownloads.list = function () { throw new Error('falha'); };
        return window.BuroApp._downloadChipHtml() === '';
    }()));

window.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
