/*
  Um avatar tem de distinguir um perfil do outro.

  Ele desenhava a inicial do nome. Numa casa com Bruno e Beatriz isso da dois
  circulos com B, e os cinco avatares padrao apareciam como a mesma letra em
  cinco cores — que e o que o usuario viu na tela de editar perfil.

  O Android resolve com um icone por chave: fogo, arvore, onda, lua. A cor passa
  a ser reforco em vez de ser a unica diferenca.
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

function avatarMarkup(window, index) {
    return window.document.querySelectorAll('.profile-card .avatar')[index];
}

async function run() {
    var window = loadApp();
    var keys = ['gold', 'ember', 'forest', 'ocean', 'moon'];
    var drawings;
    var withPhoto;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    /*
      Cinco perfis com a mesma inicial: e o caso que expoe o defeito. Se o
      avatar continuasse desenhando a letra, os cinco sairiam identicos.
    */
    window.BuroApp.state.profiles = keys.map(function (key, index) {
        return { id: 'p' + index, name: 'Buro ' + index, avatarKey: key, isKids: false, createdAt: index };
    });
    window.BuroApp.state.activeProfile = window.BuroApp.state.profiles[0];
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'PROFILES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('Cinco perfis com a mesma inicial saem diferentes\n');
    drawings = keys.map(function (key, index) {
        var avatar = avatarMarkup(window, index);
        return avatar ? avatar.innerHTML : '';
    });
    check('todos os cinco desenham alguma coisa',
        drawings.every(function (html) { return html.length > 0; }));
    check('e nenhum repete o desenho do outro',
        new Set(drawings).size === keys.length);
    /* O ponto do defeito: nenhum deles pode ser a letra B. */
    check('nenhum e a inicial do nome',
        drawings.every(function (html) { return html.indexOf('<svg') >= 0; }));

    process.stdout.write('O perfil Kids tem o proprio simbolo\n');
    /* Kids nao e uma cor entre outras: e um modo, e o simbolo tem de dizer isso
       mesmo que a chave de cor seja a mesma de outro perfil. */
    window.BuroApp.state.profiles = [
        { id: 'a', name: 'Casa', avatarKey: 'gold', isKids: false, createdAt: 1 },
        { id: 'b', name: 'Crianca', avatarKey: 'gold', isKids: true, createdAt: 2 }
    ];
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('mesma cor, simbolos diferentes',
        avatarMarkup(window, 0).innerHTML !== avatarMarkup(window, 1).innerHTML);

    process.stdout.write('Uma foto continua ganhando do simbolo\n');
    /*
      Quem escolheu uma foto escolheu aquilo; o simbolo e o que existe na
      ausencia dela, e nao um enfeite por cima.
    */
    window.BuroApp.state.profiles = [
        { id: 'c', name: 'Com foto', avatarKey: 'ocean', isKids: false, createdAt: 1,
          /* JPEG porque e o unico formato que `BuroProfilePhoto.safe`
             aceita: a foto e redimensionada e regravada como JPEG antes de
             ser guardada, entao qualquer outra coisa no campo veio de fora
             do caminho normal e e recusada de proposito. */
          photoDataUrl: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAoHBwkHBgoJCAkLCwoMDxkQDw4ODx4WFxIZJCAmJSMgIyIoLTkwKCo2KyIjMkQyNjs9QEBAJjBGS0U+Sjk/QD3/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==' }
    ];
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    withPhoto = avatarMarkup(window, 0);
    check('a foto e desenhada em vez do simbolo',
        withPhoto && withPhoto.querySelector('img') && !withPhoto.querySelector('svg'));

    process.stdout.write('Uma chave desconhecida nao deixa o circulo vazio\n');
    /* Um perfil gravado por uma versao futura, com uma chave que esta versao nao
       conhece, precisa continuar mostrando alguma coisa. */
    window.BuroApp.state.profiles = [
        { id: 'd', name: 'Zelia', avatarKey: 'chave-que-nao-existe', isKids: false, createdAt: 1 }
    ];
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('cai no simbolo padrao, e nao num circulo vazio',
        avatarMarkup(window, 0).innerHTML.length > 0);

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
    process.stderr.write('Falha na suite de avatares: ' + error.stack + '\n');
    process.exit(1);
});
