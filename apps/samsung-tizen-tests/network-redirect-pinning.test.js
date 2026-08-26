/*
  Um servidor Xtream comprometido pode responder a uma chamada autenticada com um 302 para
  qualquer host. XMLHttpRequest segue isso sozinho, sem nada equivalente ao
  followRedirects(false) que o cliente Kotlin usa — testado dinamicamente contra um servidor
  malicioso simulado, que confirmou o navegador obedece o redirecionamento silenciosamente.

  BuroNetwork agora recusa um redirecionamento cross-host quando o chamador pede pinHost: true,
  que é exatamente o que xtream.js pede em toda chamada a player_api.php (a única que carrega
  usuário e senha na própria URL). Sem pinHost, o comportamento de sempre continua — necessário
  para uma lista M3U remota, que pode legitimamente estar atrás de um CDN.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function loadNetwork(responseUrl) {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;

    function FakeXhr() { this.readyState = 0; this.status = 0; this.responseURL = responseUrl; }
    FakeXhr.prototype.open = function () {};
    FakeXhr.prototype.setRequestHeader = function () {};
    FakeXhr.prototype.send = function () {
        var self = this;
        setTimeout(function () {
            self.readyState = 4;
            self.status = 200;
            self.responseText = '{"user_info":{"auth":1}}';
            if (self.onreadystatechange) { self.onreadystatechange(); }
        }, 0);
    };
    FakeXhr.prototype.abort = function () {};
    window.XMLHttpRequest = FakeXhr;
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'network.js'), 'utf8'));
    return window;
}

function run() {
    process.stdout.write('Redirecionamento cross-host, com pinHost pedido\n');
    var pinnedWindow = loadNetwork('https://attacker.invalid/steal?u=fixture&p=segredo');
    var pinnedResult = null;
    pinnedWindow.BuroNetwork.json(
        { url: 'https://provider.test/player_api.php?username=fixture&password=segredo', pinHost: true },
        function (payload) { pinnedResult = { ok: true, payload: payload }; },
        function (error) { pinnedResult = { ok: false, error: error }; },
    );

    process.stdout.write('Redirecionamento cross-host, sem pinHost (lista M3U remota)\n');
    var unpinnedWindow = loadNetwork('https://cdn.example.net/playlist.m3u');
    var unpinnedResult = null;
    unpinnedWindow.BuroNetwork.text(
        { url: 'https://provider.test/playlist.m3u' },
        function (text) { unpinnedResult = { ok: true, text: text }; },
        function (error) { unpinnedResult = { ok: false, error: error }; },
    );

    process.stdout.write('Mesmo host, com pinHost pedido (o caso comum)\n');
    var sameHostWindow = loadNetwork('https://provider.test/player_api.php?username=fixture&password=segredo');
    var sameHostResult = null;
    sameHostWindow.BuroNetwork.json(
        { url: 'https://provider.test/player_api.php?username=fixture&password=segredo', pinHost: true },
        function (payload) { sameHostResult = { ok: true, payload: payload }; },
        function (error) { sameHostResult = { ok: false, error: error }; },
    );

    process.stdout.write('responseURL ausente (runtime sem XHR nível 2), com pinHost pedido\n');
    var noResponseUrlWindow = loadNetwork('');
    var noResponseUrlResult = null;
    noResponseUrlWindow.BuroNetwork.json(
        { url: 'https://provider.test/player_api.php?username=fixture&password=segredo', pinHost: true },
        function (payload) { noResponseUrlResult = { ok: true, payload: payload }; },
        function (error) { noResponseUrlResult = { ok: false, error: error }; },
    );

    return new Promise(function (resolve) {
        setTimeout(function () {
            check('um redirecionamento para outro host é recusado quando pinHost é pedido',
                pinnedResult && pinnedResult.ok === false && pinnedResult.error.code === 'UNEXPECTED_REDIRECT');
            check('sem pinHost, o redirecionamento cross-host continua funcionando (lista remota)',
                unpinnedResult && unpinnedResult.ok === true);
            check('mesmo host permanece aceito com pinHost pedido',
                sameHostResult && sameHostResult.ok === true && sameHostResult.payload.user_info.auth === 1);
            check('sem responseURL, a checagem não bloqueia (falha aberta, não fechada)',
                noResponseUrlResult && noResponseUrlResult.ok === true);
            resolve();
        }, 20);
    });
}

run().then(function () {
    if (failures.length) {
        process.stdout.write('\nResultado: ' + passed + ' passaram, ' + failures.length + ' falharam.\n');
        process.exitCode = 1;
        return;
    }
    process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
});
