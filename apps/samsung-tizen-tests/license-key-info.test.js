/*
  Consulta de uma chave antes do resgate.

  O endpoint informa se a chave está disponível, já pertence a esta TV ou não
  pode ser usada. A consulta usa a identidade assinada do aparelho, mas nunca
  grava/concede licença: só `/v1/redeem` pode fazer isso.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var nodeCrypto = require('node:crypto');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secure = {};
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
        }
    };
    ['js/domain.js', 'js/network.js', 'js/identity.js', 'js/license.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    return window;
}

function inspect(window, key) {
    return new Promise(function (resolve) {
        window.BuroLicense.keyInfo(key, function (value) {
            resolve({ value: value });
        }, function (error) {
            resolve({ error: error });
        });
    });
}

async function run() {
    var window = makeWindow();
    var request;
    var result;

    process.stdout.write('Contrato da consulta de chave\n');
    window.BuroNetwork.json = function (options, success) {
        request = options;
        success({ state: 'available', grantDays: 730, validUntil: null });
    };
    result = await inspect(window, '  teste-123  ');
    check('a consulta usa o endpoint de informação, nunca o de resgate',
        /\/v1\/key-info$/.test(request.url) && !/\/v1\/redeem$/.test(request.url));
    check('a chave é normalizada antes de sair da TV',
        JSON.parse(request.body).key === 'TESTE-123');
    check('a consulta leva prova de validação e omite a identidade de registro', (function () {
        var body = JSON.parse(request.body);
        return Boolean(body.deviceId && body.nonce && body.proof) &&
            !body.publicKey && !body.installationId;
    }()));
    check('o resultado disponível preserva somente os campos limitados',
        result.value && result.value.state === 'available' && result.value.grantDays === 730 &&
        Object.keys(result.value).every(function (key) {
            return ['state', 'grantDays', 'validUntil'].indexOf(key) >= 0;
        }));
    check('consultar não cria uma licença local', window.BuroLicense.snapshot() === null);

    process.stdout.write('Respostas recusadas e desconhecidas\n');
    window.BuroNetwork.json = function (options, success, failure) {
        failure({ code: 'HTTP_ERROR', status: 404, message: 'conteúdo não confiável' });
    };
    result = await inspect(window, 'NAO-EXISTE');
    check('404 é apresentado como chave desconhecida, não como falha da TV',
        result.value && result.value.state === 'unknown');

    window.BuroNetwork.json = function (options, success) {
        success({ state: 'administrator', grantDays: 999999999, secret: 'não deve passar' });
    };
    result = await inspect(window, 'MALFORMADA');
    check('um estado inventado pelo servidor é recusado',
        result.error && result.error.code === 'KEY_INFO_MALFORMED');

    window.BuroNetwork.json = function (options, success) {
        success({ state: 'available', grantDays: 999999999 });
    };
    result = await inspect(window, 'DIAS-DEMAIS');
    check('uma duração fora do limite é recusada',
        result.error && result.error.code === 'KEY_INFO_MALFORMED');

    result = await inspect(window, '');
    check('uma chave vazia não chega à rede',
        result.error && result.error.code === 'KEY_REQUIRED');

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
