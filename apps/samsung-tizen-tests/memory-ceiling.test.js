/*
  Os caches em memória param de crescer.

  Uma sessão de TV dura horas e o catálogo não muda nesse tempo. Os caches de
  arte só eram limpos quando um título saía do catálogo, então numa sessão
  normal eles só cresciam — uma entrada por título que passou pela tela.

  O custo não é o texto da URL. É que a TV responde à pressão de memória com
  coleta de lixo agressiva, e o usuário vê isso como a interface travando e o
  controle demorando a responder, sem nenhuma mensagem de erro.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition, detail) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else {
        failures.push(label + (detail ? ' — ' + detail : ''));
        process.stdout.write('  FALHA ' + label + (detail ? ' — ' + detail : '') + '\n');
    }
}

function scriptFiles() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var files = [];
    var match = pattern.exec(html);
    while (match) { files.push(match[1]); match = pattern.exec(html); }
    return files;
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
    scriptFiles().forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    return window;
}

function settle(milliseconds) {
    return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
}

async function run() {
    var window = loadApp();
    await settle(1600);

    process.stdout.write('Teto dos caches de arte\n');

    var sizes = window.BuroApp._cacheSizes;
    check('o app expõe os tamanhos dos caches para inspeção',
        typeof sizes === 'function');
    if (typeof sizes !== 'function') {
        window.close();
        process.exitCode = 1;
        return;
    }

    /* Uma sessão longa: muito mais títulos do que qualquer teto razoável. */
    var index;
    for (index = 0; index < 5000; index += 1) {
        window.BuroApp._rememberArtwork('movie:' + index,
            'https://images.test/poster/' + index + '.jpg');
        window.BuroApp._rememberDetailBackdrop('movie:' + index,
            'https://images.test/backdrop/' + index + '.jpg');
    }

    var after = sizes();
    check('o cache de pôsteres para de crescer',
        after.artwork <= 800, after.artwork + ' entradas apos 5000 titulos');
    check('o cache de fundos para de crescer',
        after.detailBackdrop <= 800, after.detailBackdrop + ' entradas apos 5000 titulos');

    /*
      O índice de ordem tem de encolher junto. Se ele crescesse sozinho, o
      vazamento continuaria — só teria mudado de lugar.
    */
    check('o índice de ordem acompanha o cache de pôsteres',
        after.artworkOrder === after.artwork,
        after.artworkOrder + ' na ordem contra ' + after.artwork + ' no cache');
    check('o índice de ordem acompanha o cache de fundos',
        after.detailBackdropOrder === after.detailBackdrop,
        after.detailBackdropOrder + ' na ordem contra ' + after.detailBackdrop);

    process.stdout.write('O que fica é o mais recente\n');
    /*
      Descartar o que está na tela seria pior que não ter cache: a imagem
      sumiria e voltaria a ser buscada enquanto o usuário olha para ela.
    */
    check('o título mais recente continua em cache',
        window.BuroApp._artworkFor('movie:4999') ===
            'https://images.test/poster/4999.jpg');
    check('o mais antigo foi descartado',
        !window.BuroApp._artworkFor('movie:0'));

    process.stdout.write('Revisitar promove\n');
    /* Reabrir um título recoloca-o no fim da fila de descarte. */
    var survivor = 'movie:4300';
    window.BuroApp._rememberArtwork(survivor, 'https://images.test/poster/4300.jpg');
    for (index = 5000; index < 5400; index += 1) {
        window.BuroApp._rememberArtwork('movie:' + index,
            'https://images.test/poster/' + index + '.jpg');
    }
    check('um título revisitado sobrevive ao descarte',
        Boolean(window.BuroApp._artworkFor(survivor)));

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
