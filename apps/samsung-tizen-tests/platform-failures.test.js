/*
  O que acontece quando a TV nega alguma coisa.

  Estes casos não aparecem numa TV de demonstração: memória cheia, privilégio
  recusado, preferências corrompidas por um desligamento na tomada. Numa TV de
  cliente aparecem — e a diferença entre um app quebrado e um app honesto é a
  mensagem que ele mostra.

  A regra que estes testes protegem: cada causa tem a sua mensagem. Dizer
  "verifique a conexão" quando o armazenamento está cheio faz o usuário
  reiniciar o roteador à toa e desistir.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');

/*
  A lista de scripts vem do próprio index.html, na ordem em que a TV os carrega.

  Mantê-la escrita à mão aqui já quebrou a suíte toda vez que um módulo novo
  entrou no app: o teste falhava com "X is not defined", que parece um bug do
  app e não um teste desatualizado.
*/
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

function settle(milliseconds) {
    return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
}

/* `options.indexedDb: false` simula a TV recusando armazenamento; `keymanager:
   false`, o privilégio negado; `preferences`, o que já estava gravado. */
function loadApp(options) {
    var settings = options || {};
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secure = {};
    var uncaught = [];

    window.addEventListener('error', function (event) { uncaught.push(String(event.message)); });

    if (settings.indexedDb !== false) {
        window.indexedDB = new fakeIndexedDb.IDBFactory();
    }
    window.localStorage.setItem(
        'iptvburo.preferences.v1',
        settings.rawPreferences !== undefined
            ? settings.rawPreferences
            : JSON.stringify(settings.preferences || { language: 'pt-BR', languageSelected: true })
    );

    window.tizen = {
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    if (settings.keymanager !== false) {
        window.tizen.keymanager = {
            getDataAliasList: function () {
                return Object.keys(secure).map(function (name) { return { name: name }; });
            },
            saveData: function (name, value, password, success) { secure[name] = value; success(); },
            getData: function (alias) {
                if (!secure[alias.name]) { throw { name: 'NotFoundError' }; }
                return secure[alias.name];
            },
            removeData: function (alias) { delete secure[alias.name]; }
        };
    }

    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    window.__uncaught = uncaught;
    return window;
}

function screenText(window) {
    return window.document.getElementById('app').textContent.replace(/\s+/g, ' ').trim();
}

function press(window, keyCode) {
    var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
}

async function run() {
    var window;

    process.stdout.write('A TV recusa armazenamento\n');
    window = loadApp({ indexedDb: false });
    await settle(1500);
    check('o app não quebra sem IndexedDB', window.__uncaught.length === 0);
    check('a mensagem aponta o armazenamento, não a internet',
        screenText(window).indexOf(window.BuroI18n.t('storageUnavailable')) >= 0);
    check('a falha de armazenamento não é confundida com falha de conexão',
        screenText(window).indexOf(window.BuroI18n.t('sourceError')) === -1);
    check('o usuário ainda tem uma ação disponível',
        Boolean(window.document.querySelector('[data-action="retry"]')));
    window.close();

    process.stdout.write('A TV recusa o cofre de segredos\n');
    window = loadApp({ keymanager: false });
    await settle(1500);
    check('o app abre mesmo sem KeyManager', window.__uncaught.length === 0);
    check('a navegação continua utilizável',
        window.document.querySelectorAll('.focusable').length > 0);
    window.close();

    process.stdout.write('Estado local corrompido\n');
    /* Um desligamento na tomada durante a gravação deixa exatamente isto. */
    window = loadApp({ rawPreferences: '{"language":"pt-BR",' });
    await settle(1500);
    check('preferências corrompidas não impedem o app de abrir',
        window.__uncaught.length === 0 && window.document.querySelectorAll('.focusable').length > 0);
    window.close();

    process.stdout.write('Uso agressivo do controle\n');
    window = loadApp({});
    await settle(1400);
    (function () {
        var before = window.document.querySelectorAll('.focusable').length;
        var index;
        /* O usuário segurando o D-pad: a TV entrega centenas de eventos. */
        for (index = 0; index < 500; index += 1) {
            press(window, index % 2 ? 39 : 40);
        }
        check('segurar o D-pad não derruba a tela',
            window.__uncaught.length === 0 &&
            window.document.querySelectorAll('.focusable').length === before);
    }());
    (function () {
        var index;
        for (index = 0; index < 40; index += 1) { press(window, 10009); }
        check('RETURN repetido na primeira tela não trava o app',
            window.__uncaught.length === 0);
    }());
    window.close();

    process.stdout.write('Mensagens de falha\n');
    window = loadApp({});
    await settle(1400);
    (function () {
        /*
          Cada código tem de dizer algo diferente. Um mapa que devolve o mesmo
          texto genérico para tudo passaria despercebido sem esta conferência.
        */
        var codes = ['INDEXED_DB_UNAVAILABLE', 'NETWORK_TIMEOUT', 'AUTH_REJECTED',
            'SERVER_URL_INVALID', 'M3U_HEADER_REQUIRED', 'SECURE_STORE_UNAVAILABLE'];
        var languages = ['pt-BR', 'en', 'de', 'it', 'es'];
        var previous = window.BuroI18n.language();
        var distinctEverywhere = true;
        var translatedEverywhere = true;

        languages.forEach(function (language) {
            var seen = {};
            window.BuroI18n.setLanguage(language);
            codes.forEach(function (code) {
                var message = window.BuroApp._friendlyError({ code: code });
                if (!message || message === code) { translatedEverywhere = false; }
                if (seen[message]) { distinctEverywhere = false; }
                seen[message] = true;
            });
        });
        window.BuroI18n.setLanguage(previous);

        check('cada causa tem a sua própria mensagem', distinctEverywhere);
        check('as mensagens de falha existem nos cinco idiomas', translatedEverywhere);
    }());
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
