/*
  Teste de navegacao por D-pad, executado fora da TV.

  Carrega index.html num DOM simulado e exercita as teclas do controle.
  Isso cobre a parte do app que NAO depende da TV: renderizacao dos cartoes,
  movimento do foco e o caminho de erro quando a AVPlay nao existe.

  Playback real continua sem cobertura aqui: a AVPlay so existe na TV.

  Estes testes moram FORA de apps/samsung-tizen de proposito. O `tizen
  build-web` copia tudo que estiver na pasta do app para dentro do .wgt,
  incluindo node_modules — o pacote passaria de 60 KB para dezenas de MB.

  Rodar:
      cd apps/samsung-tizen-tests
      npm install
      npm test
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');

    var dom = new JSDOM(html, {
        runScripts: 'outside-only',
        pretendToBeVisual: true,
        url: 'file:///app/'
    });

    var window = dom.window;

    // Mesma ordem de <script> do index.html.
    ['js/keys.js', 'js/player.js', 'js/app.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });

    window.dispatchEvent(new window.Event('load'));
    return window;
}

function press(window, keyCode) {
    var ev = new window.KeyboardEvent('keydown', { bubbles: true });
    // jsdom nao preenche o keyCode legado, que e justamente o que a TV envia.
    Object.defineProperty(ev, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(ev);
}

function focusedName(window) {
    var el = window.document.querySelector('.card.focused');
    return el ? el.querySelector('.card-name').textContent : '(nenhum)';
}

var failures = [];
var passes = 0;

function check(label, actual, expected) {
    if (actual === expected) {
        passes++;
        console.log('  ok   ' + label);
    } else {
        failures.push(label + ': esperado "' + expected + '", obtido "' + actual + '"');
        console.log('  FALHA ' + label + ' -> esperado "' + expected + '", obtido "' + actual + '"');
    }
}

var KEY = { LEFT: 37, RIGHT: 39, ENTER: 13, RETURN: 10009 };

console.log('Navegacao por D-pad');
var w = loadApp();

check('renderiza os tres cartoes',
    String(w.document.querySelectorAll('.card').length), '3');
check('foco comeca no primeiro cartao', focusedName(w), 'Big Buck Bunny');

press(w, KEY.RIGHT);
check('RIGHT avança o foco', focusedName(w), 'Tears of Steel');

press(w, KEY.RIGHT);
press(w, KEY.RIGHT);
check('foco para na borda direita (sem wrap-around)',
    focusedName(w), 'Fonte inválida');

press(w, KEY.LEFT);
check('LEFT retrocede o foco', focusedName(w), 'Tears of Steel');

press(w, KEY.LEFT);
press(w, KEY.LEFT);
check('foco para na borda esquerda', focusedName(w), 'Big Buck Bunny');

console.log('Ausencia da AVPlay');
press(w, KEY.ENTER);

check('nao fica preso no estado de playback',
    String(w.document.body.classList.contains('playing')), 'false');
check('mostra o erro ao usuario',
    String(w.document.getElementById('player-status').className.indexOf('error') > -1), 'true');

console.log('');
if (failures.length) {
    console.log(failures.length + ' falha(s), ' + passes + ' passaram');
    process.exit(1);
}
console.log('Todos os ' + passes + ' testes passaram.');
