/*
  O código do aparelho, alcançável antes de existir qualquer lista.

  É assim que quem vendeu a lista encontra esta televisão no painel e a
  configura de longe. Até agora o código só aparecia na tela de Licença, que
  abre quando o teste gratuito termina — ou seja, durante os dias em que o
  cliente mais precisa de ajuda ele não tinha como achar o código, e pedir que
  configure uma lista para chegar ao botão que faz outra pessoa configurar a
  lista é um círculo.

  O que este teste guarda: o botão está na primeira tela, com zero listas, e o
  código que ele mostra é o mesmo que o servidor conhece.
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
    while (match) { files.push(match[1]); match = pattern.exec(html); }
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

function loadApp(deviceId) {
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
    /* O identificador que o servidor conhece. Substituído aqui porque o real
       depende do registro da televisão, que não existe num teste. */
    window.BuroLicense.deviceId = function () { return deviceId; };
    window.BuroLicense.fetchAssignedSource = function (done) { done(null); };
    window.BuroApp.init();
    return window;
}

async function run() {
    var window;

    process.stdout.write('O código na primeira tela\n');

    window = loadApp('SUMR-SRQG-H4BJ');
    /* Sem perfil e sem lista: exatamente o cliente que não conseguiu
       configurar nada e precisa de ajuda. */
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000);

    var button = window.document.querySelector('[data-action="device-code"]');
    check('o botão aparece antes de existir qualquer lista', Boolean(button));
    check('o botão é alcançável pelo controle remoto',
        Boolean(button) && button.classList.contains('focusable'));
    check('o rótulo vem das traduções, não escrito na tela',
        Boolean(button) && button.textContent.trim() === window.BuroI18n.t('deviceCode'));

    if (!button) {
        /* Sem o botão não há o que testar a seguir, e esperar por uma tela que
           nunca abre penduraria a suíte em vez de reportar a falha. */
        process.stdout.write('  (sem botão: o resto desta seção não pode ser verificado)\n');
    } else {
    window.BuroApp._activate(button);
    await waitFor(function () { return window.BuroApp.state.screen === 'DEVICE_CODE'; }, 3000)
        .catch(function () { check('a tela do código abre ao acionar o botão', false); });

    var shown = window.document.querySelector('.pair-code');
    check('a tela mostra o código que o servidor conhece',
        Boolean(shown) && shown.textContent.trim() === 'SUMR-SRQG-H4BJ');
    check('a tela diz para que serve o código',
        window.document.body.textContent.indexOf(window.BuroI18n.t('deviceCodeHelp')) >= 0);

    /* Numa televisão não há como copiar: o código é lido da tela e enviado por
       mensagem. Se ele não estiver desenhado grande, não se lê do sofá. */
    check('o código usa o mesmo destaque do pareamento',
        Boolean(shown) && shown.className.indexOf('pair-code') >= 0);

    window.BuroApp._onKeyDown({ keyCode: 10009, preventDefault: function () {} });
    await waitFor(function () { return window.BuroApp.state.screen !== 'DEVICE_CODE'; }, 3000)
        .catch(function () {});
    check('RETURN volta para a escolha de perfil em vez de sair do app',
        window.BuroApp.state.screen !== 'DEVICE_CODE');
    }
    window.close();

    process.stdout.write('Sem identidade ainda\n');

    window = loadApp('');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="device-code"]'));
    }, 3000).catch(function () {});
    var second = window.document.querySelector('[data-action="device-code"]');
    if (second) {
        window.BuroApp._activate(second);
        await waitFor(function () { return window.BuroApp.state.screen === 'DEVICE_CODE'; }, 3000)
            .catch(function () {});
    }
    /* Uma televisão que ainda não se registrou não tem código. Mostrar um espaço
       em branco seria pior do que explicar: quem lê em voz alta leria nada. */
    check('sem código, a tela abre mesmo assim em vez de quebrar',
        window.BuroApp.state.screen === 'DEVICE_CODE');
    check('sem código, nada em branco é oferecido para ler',
        !window.document.querySelector('.pair-code'));
    window.close();

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    } else {
        process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
    }
}

run().catch(function (error) {
    process.stdout.write('erro: ' + error.message + '\n');
    process.exitCode = 1;
});
