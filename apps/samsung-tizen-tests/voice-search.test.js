/*
  Ditar a busca pelo microfone do controle.

  Digitar "Senhor dos Aneis" num teclado de tela com as setas leva mais tempo do
  que assistir ao trailer. A TV ja tem microfone no controle; o que faltava era o
  aplicativo aceitar.

  Duas vias, e o que este teste guarda e sobretudo a **ausencia** de uma delas:

  - onde a Web Speech API existe, o botao aparece e o aplicativo escuta;
  - onde nao existe, o botao **some** — e o campo continua `type="search"`, que e
    o que faz o teclado virtual da Samsung mostrar o proprio microfone. Um botao
    que nao funciona seria pior do que nenhum: a pessoa aperta, nada acontece, e
    conclui que o aplicativo esta quebrado.

  E o silencio nao e falha: quem desistiu de falar nao precisa de aviso vermelho.
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

/*
  Um reconhecedor de mentira, com o mesmo contrato do real: guarda o que foi
  pedido e deixa o teste decidir quando — e se — a fala chega.
*/
function fakeRecognition(register) {
    return function () {
        var self = this;
        self.lang = '';
        self.continuous = true;
        self.interimResults = true;
        self.start = function () { register(self); };
        self.abort = function () { self.aborted = true; };
        return self;
    };
}

function loadApp(withSpeech, register) {
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
    if (withSpeech) { window.webkitSpeechRecognition = fakeRecognition(register); }
    scripts.forEach(function (script) { window.eval(fs.readFileSync(path.join(APP_DIR, script), 'utf8')); });
    window.BuroApp.init();
    return window;
}

function openSearch(window) {
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SEARCH';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
}

async function run() {
    var window;
    var active = null;
    var register = function (recogniser) { active = recogniser; };
    var input;

    process.stdout.write('Uma TV que sabe escutar oferece o botao\n');
    window = loadApp(true, register);
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    window.BuroApp.state.profiles = [{ id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, createdAt: 1 }];
    window.BuroApp.state.activeProfile = window.BuroApp.state.profiles[0];
    openSearch(window);

    check('o botao de ditar aparece',
        Boolean(window.document.querySelector('[data-action="search-voice"]')));
    /*
      O campo tem de ser `type="search"` de qualquer forma: e ele que faz o
      teclado virtual da Samsung mostrar o microfone do sistema, que e o caminho
      dos modelos sem a API.
    */
    check('e o campo continua sendo type=search, pelo teclado do sistema',
        window.document.getElementById('search-query').getAttribute('type') === 'search');

    process.stdout.write('Falar preenche o campo e busca\n');
    window.BuroApp._activate(window.document.querySelector('[data-action="search-voice"]'));
    check('o aparelho comeca a escutar',
        Boolean(active) && window.BuroVoice.isListening());
    /* Uma frase por vez, e sem texto parcial: numa TV o parcial aparece e some
       enquanto a pessoa fala, e se le como se o aparelho estivesse errando. */
    check('pede uma frase so, sem resultado parcial',
        active.continuous === false && active.interimResults === false);
    check('e no idioma do aplicativo',
        active.lang === 'pt-BR');

    active.onresult({ results: [[{ transcript: 'senhor dos aneis' }]] });
    await waitFor(function () {
        return window.document.getElementById('search-query') &&
            window.document.getElementById('search-query').value.length > 0;
    }, 4000);
    input = window.document.getElementById('search-query');
    /* O texto entra no campo antes de buscar: se saiu errado, a pessoa corrige
       dali em vez de recomecar. */
    check('o que foi ouvido aparece no campo',
        input.value === 'senhor dos aneis');
    check('e a busca acontece sozinha, sem pedir um segundo ENTER',
        window.BuroApp.state.screenData &&
        window.BuroApp.state.screenData.query === 'senhor dos aneis');

    process.stdout.write('Desistir de falar nao vira erro\n');
    active = null;
    openSearch(window);
    window.BuroApp._activate(window.document.querySelector('[data-action="search-voice"]'));
    /*
      `no-speech` e a pessoa que abriu a boca e nao falou, ou o silencio da sala.
      Um aviso vermelho ali seria o aplicativo repreendendo quem hesitou.
    */
    active.onerror({ error: 'no-speech' });
    await waitFor(function () { return !window.BuroVoice.isListening(); }, 4000);
    check('o silencio encerra a escuta sem alarde',
        !window.BuroVoice.isListening() &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('searchVoiceFailed')) < 0);
    window.close();

    process.stdout.write('Uma TV que nao sabe escutar nao mostra o botao\n');
    window = loadApp(false, register);
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    window.BuroApp.state.profiles = [{ id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, createdAt: 1 }];
    window.BuroApp.state.activeProfile = window.BuroApp.state.profiles[0];
    openSearch(window);
    /*
      O ponto do teste. Um botao que nao funciona e pior do que nenhum: numa TV
      a pessoa aperta, nada acontece, e conclui que o aplicativo esta quebrado.
    */
    check('sem a API, o botao nao existe',
        !window.document.querySelector('[data-action="search-voice"]'));
    check('mas o campo continua abrindo o teclado com microfone',
        window.document.getElementById('search-query').getAttribute('type') === 'search');
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
    process.stderr.write('Falha na suite de busca por voz: ' + error.stack + '\n');
    process.exit(1);
});
