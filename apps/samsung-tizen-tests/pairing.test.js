/*
  A tela que recebe a chave do celular.

  O servidor é falsificado aqui: o que este teste verifica é o comportamento da
  TV — o código na tela, o relógio que para ao sair, e o fato de a chave
  recebida passar pela mesma validação da digitada no controle.
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
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
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
        application: {
            getCurrentApplication: function () { return { exit: function () {} }; },
            launchAppControl: function (control, id, success) { if (success) { success(); } }
        }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    window.__secureData = secureData;
    return window;
}

function activate(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
}

/*
  Um servidor de pareamento sintético. Guarda o que foi pedido para que o teste
  possa afirmar o que saiu da TV, e entrega o que for combinado.
*/
function pairingServer(window, script) {
    var calls = [];
    var plan = script || {};
    window.BuroNetwork.json = function (options, success, failure) {
        var path = String(options.url || '').replace(/^https?:\/\/[^/]+/, '');
        calls.push({ path: path, body: options.body, method: options.method });
        setTimeout(function () {
            if (path === '/v1/pair/start') {
                if (plan.startFails) { failure({ code: 'NETWORK_ERROR' }); return; }
                success({ code: plan.code || '482913', kind: 'tmdb_key', expiresInSeconds: 300 });
                return;
            }
            if (path === '/v1/pair/claim') {
                if (plan.claimExpired) { failure({ code: 'HTTP_ERROR', status: 404 }); return; }
                if (plan.payload) { success({ status: 'ready', kind: 'tmdb_key', payload: plan.payload }); return; }
                success({ status: 'pending' });
                return;
            }
            /* Qualquer outra chamada é a validação do TMDb. */
            if (plan.validationFails) { failure({ code: 'TMDB_KEY_REJECTED' }); return; }
            success({ images: {} });
        }, 0);
        return { abort: function () { calls.push({ path: path, aborted: true }); } };
    };
    return calls;
}

async function reachTmdbSettings(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000);
    activate(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 8000);
    window.document.getElementById('profile-name').value = 'Casa';
    activate(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 8000);
    window.BuroApp.state.section = 'SETTINGS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="tmdb-settings"]'));
    }, 8000);
    activate(window, '[data-action="tmdb-settings"]');
    await waitFor(function () { return window.BuroApp.state.screen === 'TMDB_SETTINGS'; }, 8000);
}

async function run() {
    var window;
    var calls;

    process.stdout.write('A tela de chave oferece o caminho pelo celular\n');
    window = loadApp();
    await reachTmdbSettings(window);
    check('o botão de enviar do celular existe ao lado do guia',
        Boolean(window.document.querySelector('[data-action="pair-tmdb"]')));

    process.stdout.write('O código aparece grande na tela\n');
    calls = pairingServer(window, { code: '482913' });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.code;
    }, 8000);
    check('a TV pediu um código antes de mostrar qualquer coisa',
        calls[0].path === '/v1/pair/start' && calls[0].method === 'POST');
    check('o pedido diz que tipo de chave a TV espera',
        String(calls[0].body).indexOf('tmdb_key') >= 0);
    check('o código aparece na tela',
        window.document.querySelector('.pair-code').textContent === '482913');
    check('o endereço para digitar no celular aparece junto',
        window.document.querySelector('.pair-url').textContent.indexOf('/parear') > 0);
    check('a tela diz que está esperando, em vez de parecer travada',
        Boolean(window.document.querySelector('.pair-waiting')));

    process.stdout.write('A TV pergunta de novo enquanto ninguém enviou\n');
    await waitFor(function () {
        return calls.filter(function (call) { return call.path === '/v1/pair/claim'; }).length >= 2;
    }, 8000);
    check('perguntar repetidamente é o funcionamento normal, não erro',
        window.BuroApp.state.screen === 'PAIRING' && !window.BuroApp.state.screenData.error);
    window.close();

    process.stdout.write('Sair da tela para o relógio\n');
    window = loadApp();
    await reachTmdbSettings(window);
    calls = pairingServer(window, { code: '111222' });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.code;
    }, 8000);
    await waitFor(function () {
        return calls.filter(function (call) { return call.path === '/v1/pair/claim'; }).length >= 1;
    }, 8000);
    window.BuroApp._onKeyDown({ keyCode: 10009, preventDefault: function () {} });
    await waitFor(function () { return window.BuroApp.state.screen !== 'PAIRING'; }, 8000);
    (function () {
        var before = calls.filter(function (call) { return call.path === '/v1/pair/claim'; }).length;
        return new Promise(function (resolve) {
            setTimeout(function () {
                var after = calls.filter(function (call) { return call.path === '/v1/pair/claim'; }).length;
                check('a TV para de perguntar depois de sair da tela', after === before);
                resolve();
            }, 400);
        });
    }());
    window.close();

    process.stdout.write('A chave recebida passa pela mesma validação da digitada\n');
    window = loadApp();
    await reachTmdbSettings(window);
    calls = pairingServer(window, { code: '333444', payload: 'chave-vinda-do-celular' });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen !== 'PAIRING';
    }, 8000);
    check('a chave foi validada contra o TMDb antes de ser guardada',
        calls.some(function (call) { return call.path.indexOf('/3/configuration') >= 0; }));
    check('a chave ficou no cofre de segredos',
        JSON.stringify(window.__secureData).indexOf('chave-vinda-do-celular') >= 0);
    check('a chave não entrou em localStorage',
        window.localStorage.getItem('iptvburo.preferences.v1').indexOf('chave-vinda-do-celular') === -1);
    window.close();

    process.stdout.write('Uma chave que o TMDb recusa não é guardada\n');
    window = loadApp();
    await reachTmdbSettings(window);
    calls = pairingServer(window, { code: '555666', payload: 'chave-ruim', validationFails: true });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.error;
    }, 8000);
    check('a tela diz que a chave foi recusada',
        window.BuroApp.state.screenData.error === 'TMDB_KEY_REJECTED');
    check('nada foi gravado no cofre',
        JSON.stringify(window.__secureData).indexOf('chave-ruim') === -1);
    window.close();

    process.stdout.write('Um código vencido é dito, não escondido\n');
    window = loadApp();
    await reachTmdbSettings(window);
    calls = pairingServer(window, { code: '777888', claimExpired: true });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.error;
    }, 8000);
    check('o vencimento aparece como vencimento',
        window.BuroApp.state.screenData.error === 'PAIRING_EXPIRED');
    check('a tela oferece pedir outro código',
        Boolean(window.document.querySelector('[data-action="pair-retry"]')));
    window.close();

    process.stdout.write('Servidor fora do ar não derruba a tela\n');
    window = loadApp();
    await reachTmdbSettings(window);
    pairingServer(window, { startFails: true });
    activate(window, '[data-action="pair-tmdb"]');
    await waitFor(function () {
        return window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.error;
    }, 8000);
    check('a falha de rede vira uma mensagem, não uma tela em branco',
        Boolean(window.document.querySelector('.empty-state')));
    check('e o usuário ainda tem uma ação',
        Boolean(window.document.querySelector('[data-action="pair-retry"]')));
    window.close();

    process.stdout.write('Os textos existem nos cinco idiomas\n');
    window = loadApp();
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"], .shell'));
    }, 8000);
    check('cada idioma tem os textos do pareamento',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['pairTitle', 'pairHint', 'pairStep1', 'pairStep2', 'pairStep3',
                'pairWaiting', 'pairExpired', 'pairFailed', 'pairRetry',
                'pairFromPhone', 'pairReceived'].every(function (key) {
                var value = window.BuroI18n.t(key);
                return Boolean(value) && value !== key;
            });
        }));
    window.BuroI18n.setLanguage('pt-BR');
    window.close();

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exitCode = 1;
});
