/* Pré-visualização da chave na tela de ativação Samsung. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');
var nodeCrypto = require('node:crypto');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function delay(ms) { return new Promise(function (resolve) { setTimeout(resolve, ms); }); }

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
    var secure = {};
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var match;
    var scripts = [];
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    Object.defineProperty(window, 'crypto', {
        value: nodeCrypto.webcrypto, configurable: true, writable: true
    });
    window.TextEncoder = TextEncoder;
    window.btoa = function (value) { return Buffer.from(value, 'binary').toString('base64'); };
    window.atob = function (value) { return Buffer.from(value, 'base64').toString('binary'); };
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return []; },
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
    while ((match = pattern.exec(html))) { scripts.push(match[1]); }
    scripts.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroLicense.validate = function (done, failed) {
        failed({ code: 'NETWORK_ERROR', status: 0 });
    };
    window.BuroApp.init();
    return window;
}

function type(window, value) {
    var input = window.document.getElementById('licence-key');
    input.value = value;
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
}

async function run() {
    var window = loadApp();
    var inspections = [];
    var redemptions = [];

    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"], .shell'));
    }, 8000);
    window.BuroLicense.keyInfo = function (key, done, failed) {
        inspections.push({ key: key, done: done, failed: failed });
    };
    window.BuroLicense.redeem = function (key, done) {
        redemptions.push(key);
        done({ state: 'ACTIVE' });
    };
    window.BuroApp.state.screen = 'LICENCE';
    window.BuroApp.state.screenData = {};
    window.BuroApp.render();

    process.stdout.write('Consulta automática sem consumir a chave\n');
    type(window, '  teste-123  ');
    await delay(120);
    check('a TV aguarda o fim da digitação antes de consultar', inspections.length === 0);
    await waitFor(function () { return inspections.length === 1; }, 1200);
    check('a consulta recebe a chave normalizada', inspections[0].key === 'TESTE-123');
    check('nenhum resgate acontece durante a consulta', redemptions.length === 0);

    inspections[0].done({ state: 'available', grantDays: 730, validUntil: null });
    await waitFor(function () {
        return Boolean(window.document.querySelector('.licence-key-state'));
    }, 1000);
    check('a tela mostra por quantos dias a chave vale',
        window.document.querySelector('.licence-key-state').textContent.indexOf('730') >= 0);
    check('a chave digitada sobrevive à atualização da tela',
        window.document.getElementById('licence-key').value === 'TESTE-123');
    check('o resgate continua sendo uma ação separada e explícita',
        !window.document.querySelector('[data-action="licence-redeem"]').disabled && redemptions.length === 0);

    window.BuroApp._activate(window.document.querySelector('[data-action="licence-redeem"]'));
    check('somente o botão de confirmação resgata a chave',
        redemptions.length === 1 && redemptions[0] === 'TESTE-123');

    process.stdout.write('Resposta atrasada não substitui a chave atual\n');
    inspections = [];
    window.BuroApp.state.screen = 'LICENCE';
    window.BuroApp.state.screenData = {};
    window.BuroApp.render();
    type(window, 'PRIMEIRA');
    await waitFor(function () { return inspections.length === 1; }, 1200).catch(function () {
        throw new Error('timeout na primeira consulta atrasada: ' + inspections.length);
    });
    type(window, 'SEGUNDA');
    await waitFor(function () { return inspections.length === 2; }, 1200).catch(function () {
        throw new Error('timeout na segunda consulta atrasada: ' + inspections.length);
    });
    inspections[1].done({ state: 'available', grantDays: 365, validUntil: null });
    await waitFor(function () {
        var node = window.document.querySelector('.licence-key-state');
        return node && node.textContent.indexOf('365') >= 0;
    }, 1000).catch(function () {
        throw new Error('timeout ao mostrar 365 dias: ' + window.document.body.textContent);
    });
    inspections[0].done({ state: 'in_use', grantDays: 730, validUntil: null });
    await delay(50);
    check('a resposta antiga é ignorada',
        window.document.getElementById('licence-key').value === 'SEGUNDA' &&
        window.document.querySelector('.licence-key-state').textContent.indexOf('365') >= 0);

    process.stdout.write('Textos localizados\n');
    check('os cinco idiomas explicam todos os estados da chave',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['licenceKeyAvailable', 'licenceKeyYours', 'licenceKeyInUse',
                'licenceKeyExpired', 'licenceKeyUnknown', 'licenceKeyInspecting'].every(function (key) {
                var value = window.BuroI18n.t(key);
                return Boolean(value) && value !== key;
            });
        }));

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
    process.exit(1);
});
