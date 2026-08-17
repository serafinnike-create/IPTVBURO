/*
  O primeiro boot de uma TV nova.

  Um usuário que acabou de instalar não deve precisar fazer nada para ganhar o
  período de teste: o app se apresenta ao servidor sozinho. Mas só quando a TV
  é de fato desconhecida — o servidor responde 404 nesse caso.

  A distinção importa. Registrar diante de qualquer falha significaria tentar
  isso também com a internet caída, gastando uma requisição para nada e
  arriscando registros repetidos por um erro passageiro.

  O jsdom não traz crypto.subtle, então ele é injetado: sem isso a identidade
  do dispositivo não existe e o app pula o bloco inteiro — o teste passaria
  sem testar nada.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');
var nodeCrypto = require('node:crypto');

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

/* `answer` decide o que a rede responde; devolve as rotas chamadas. */
function bootWith(answer) {
    return new Promise(function (resolve) {
        var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
        var dom = new JSDOM(html, {
            runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
        });
        var window = dom.window;
        var secure = {};

        window.indexedDB = new fakeIndexedDb.IDBFactory();
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
            language: 'pt-BR', languageSelected: true, acceptedLegal: true
        }));
        /* O jsdom expõe `crypto` como getter; atribuir direto é ignorado. */
        Object.defineProperty(window, 'crypto', {
            value: nodeCrypto.webcrypto, configurable: true, writable: true
        });
        window.TextEncoder = TextEncoder;
        window.btoa = function (value) { return Buffer.from(value, 'binary').toString('base64'); };
        window.atob = function (value) { return Buffer.from(value, 'base64').toString('binary'); };
        window.tizen = {
            keymanager: {
                getDataAliasList: function () {
                    return Object.keys(secure).map(function (name) { return { name: name }; });
                },
                saveData: function (name, value, password, success) { secure[name] = value; success(); },
                getData: function (alias) {
                    if (!secure[alias.name]) { throw { name: 'NotFoundError' }; }
                    return secure[alias.name];
                },
                removeData: function (alias) { delete secure[alias.name]; }
            },
            tvinputdevice: { registerKey: function () {} },
            application: { getCurrentApplication: function () { return { exit: function () {} }; } }
        };

        scriptFiles().forEach(function (file) {
            window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
        });

        var routes = [];
        var bodies = [];
        window.BuroNetwork.json = function (options, success, failure) {
            routes.push(String(options.url).replace(/^https:\/\/[^/]+/, ''));
            try { bodies.push(JSON.parse(options.body)); } catch (notJson) { bodies.push(null); }
            answer(options, success, failure);
        };

        window.BuroApp.init();
        setTimeout(function () {
            var result = { routes: routes, bodies: bodies, window: window };
            resolve(result);
        }, 1600);
    });
}

function refuseWith(status, code) {
    return function (options, success, failure) {
        failure({ code: code, status: status, message: code });
    };
}

async function run() {
    process.stdout.write('TV nunca registrada\n');
    var fresh = await bootWith(refuseWith(404, 'HTTP_ERROR'));

    check('o app valida antes de qualquer outra coisa',
        fresh.routes[0] === '/v1/validate', fresh.routes.join(' -> '));
    check('uma TV desconhecida se registra sozinha',
        fresh.routes.indexOf('/v1/register') >= 0, fresh.routes.join(' -> '));
    check('o registro acontece uma vez só',
        fresh.routes.filter(function (route) { return route === '/v1/register'; }).length === 1);

    /*
      O registro precisa levar a identidade completa; a validação não, porque
      o servidor já a conhece a essa altura.
    */
    var registration = fresh.bodies[fresh.routes.indexOf('/v1/register')];
    check('o registro apresenta a chave pública e a instalação',
        Boolean(registration && registration.publicKey && registration.installationId));
    check('o registro vai assinado com um nonce',
        Boolean(registration && registration.proof && registration.nonce));
    check('nenhuma chave privada acompanha o registro',
        JSON.stringify(registration).indexOf('privateKey') === -1);
    fresh.window.close();

    process.stdout.write('Servidor inacessível\n');
    var offline = await bootWith(refuseWith(0, 'NETWORK_ERROR'));
    check('sem rede o app não tenta registrar',
        offline.routes.indexOf('/v1/register') === -1, offline.routes.join(' -> '));
    check('o app abre mesmo assim',
        offline.window.document.querySelectorAll('.focusable').length > 0);
    offline.window.close();

    process.stdout.write('Servidor com defeito\n');
    /* 500 é um problema do servidor, não uma TV desconhecida. */
    var broken = await bootWith(refuseWith(500, 'HTTP_ERROR'));
    check('um erro do servidor não dispara registro',
        broken.routes.indexOf('/v1/register') === -1, broken.routes.join(' -> '));
    broken.window.close();

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
