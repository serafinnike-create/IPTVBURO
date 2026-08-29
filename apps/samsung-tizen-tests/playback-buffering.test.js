/*
  Um filme le minutos a frente; um canal ao vivo, nao.

  Um filme que para porque a conexao tropecou dez segundos e a falha mais visivel
  deste aplicativo, e e evitavel: um filme e um arquivo, entao o player pode estar
  minutos a frente e nao notar um corte tao curto.

  Um canal ao vivo nao tem "a frente" — o que ainda nao foi transmitido nao pode
  ser lido cedo. Um buffer grande ali nao compra nada e custa duas coisas: o canal
  comeca mais tarde, e a imagem fica atrasada, o que transforma um jogo no grito
  do vizinho chegando antes do gol.

  A distincao e o desenho inteiro. Um numero so para os dois deixaria o ao vivo
  inassistivel, ou os filmes tao fragis quanto eram.

  O Windows ja seguia `PlaybackBuffering` do dominio compartilhado; a TV era a
  unica das tres a aplicar o mesmo buffer aos dois.
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

/*
  Um AVPlay de mentira que so anota o que lhe pedem. O que importa medir sao os
  parametros de buffer, e nenhum emulador os reporta de volta.
*/
function loadPlayer() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', { runScripts: 'outside-only' });
    var window = dom.window;
    var calls = [];
    window.webapis = {
        avplay: {
            open: function () {},
            close: function () {},
            stop: function () {},
            setListener: function () {},
            setDisplayRect: function () {},
            prepareAsync: function () {},
            getState: function () { return 'IDLE'; },
            setBufferingParam: function (phase, unit, value) {
                calls.push({ phase: phase, unit: unit, value: value });
            }
        }
    };
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'player.js'), 'utf8'));
    window._calls = calls;
    return window;
}

function resumeSecondsFor(window, isLive) {
    var found = null;
    window._calls.length = 0;
    window.BuroPlayer.play('https://provider.test/x.ts', 0, isLive);
    window._calls.forEach(function (call) {
        if (call.phase === 'PLAYER_BUFFER_FOR_RESUME') { found = call.value; }
    });
    return found;
}

function startSecondsFor(window, isLive) {
    var found = null;
    window._calls.length = 0;
    window.BuroPlayer.play('https://provider.test/x.ts', 0, isLive);
    window._calls.forEach(function (call) {
        if (call.phase === 'PLAYER_BUFFER_FOR_PLAY') { found = call.value; }
    });
    return found;
}

var window = loadPlayer();

process.stdout.write('Um filme guarda minutos; um canal, segundos\n');
check('um filme le dois minutos a frente',
    resumeSecondsFor(window, false) === 120);
/*
  Ao vivo fica pequeno de proposito. Cada segundo acrescentado aqui e um segundo
  que o espectador fica atras do que esta acontecendo.
*/
check('um canal ao vivo guarda so segundos',
    resumeSecondsFor(window, true) === 2);
check('e a diferenca entre os dois e grande, nao um ajuste fino',
    resumeSecondsFor(window, false) > resumeSecondsFor(window, true) * 10);

process.stdout.write('A partida continua rapida nos dois casos\n');
/*
  A parte que se erra facil: `PLAYER_BUFFER_FOR_PLAY` e quanto precisa chegar
  ANTES de a imagem aparecer. Pedir 120 ali faria a tela esperar dois minutos
  em vez de encher por baixo — o oposto do que este ajuste existe para fazer.
*/
check('o filme comeca com o minimo, e nao com dois minutos de espera',
    startSecondsFor(window, false) === 4);
check('e o canal tambem',
    startSecondsFor(window, true) === 4);

process.stdout.write('Uma TV sem a API continua reproduzindo\n');
/*
  `setBufferingParam` nao existe em todo firmware. Uma excecao aqui levaria a
  reproducao inteira por causa de um ajuste que e melhoria, nao requisito.
*/
(function () {
    var hostile = loadPlayer();
    var threw = false;
    hostile.webapis.avplay.setBufferingParam = function () { throw new Error('nao suportado'); };
    try { hostile.BuroPlayer.play('https://provider.test/x.ts', 0, false); }
    catch (error) { threw = true; }
    check('um firmware que recusa o ajuste nao derruba a reproducao', !threw);
    hostile.close();
}());

window.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
