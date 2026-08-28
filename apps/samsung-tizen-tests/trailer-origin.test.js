/*
  O trailer que o YouTube recusava.

  "Video player configuration error — Error 153" e recusa por origem: com
  `enablejsapi=1` o player confere quem o incorporou, e um widget Tizen carregado
  de `file://` tem origem `null`. O parametro `origin` nunca era enviado, entao a
  maioria dos trailers nao carregava.

  A correcao tem duas metades, e este teste guarda as duas:

  - onde a pagina tem origem de verdade, ela e enviada e a API continua servindo
    os controles por tecla;
  - onde nao tem, o embed vai **sem** `enablejsapi` — o YouTube nao tem o que
    recusar e o trailer toca. Perde-se o controle por tecla, e por isso os
    controles do proprio YouTube sao ligados no lugar: sao a unica forma de
    pausar o que a tecla ja nao pausa.

  Um controle que funciona sobre um video que nao carrega nao vale nada, e era
  isso que havia antes.
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

/* `origin` decide de onde a pagina foi carregada. */
function loadTrailer(origin) {
    var dom = new JSDOM('<!doctype html><html><body><div id="app"></div></body></html>', {
        runScripts: 'outside-only',
        url: origin || 'about:blank'
    });
    var window = dom.window;
    /*
      Sem origem utilizavel: jsdom nao deixa redefinir `location.origin`, entao o
      caso e montado com `about:blank`, cuja origem e a string "null" — o mesmo
      que um widget carregado de `file://` entrega.
    */
    ['domain', 'trailer'].forEach(function (name) {
        window.eval(fs.readFileSync(path.join(APP_DIR, 'js', name + '.js'), 'utf8'));
    });
    return window;
}

/* A funcao e interna ao modulo; o teste a recorta do proprio texto para nao
   manter uma copia que possa divergir. */
function embedUrlFrom(window) {
    var source = fs.readFileSync(path.join(APP_DIR, 'js', 'trailer.js'), 'utf8');
    var start = source.indexOf('    function embedUrl(id) {');
    var end = source.indexOf('\n    }', start);
    var body = source.substring(start, end + 6);
    /* eslint-disable no-new-func */
    return new window.Function('pageOrigin', 'return (' + body + ');');
}

function urlFor(window, id) {
    var make = embedUrlFrom(window);
    var origin = window.location.origin;
    var usable = origin && origin !== 'null' && origin.indexOf('http') === 0 ? origin : null;
    return make(function () { return usable; })(id);
}

var withOrigin = loadTrailer('https://iptvburo.test/');
var withoutOrigin = loadTrailer(null);

process.stdout.write('Com origem, a API e usada e a origem viaja junto\n');
(function () {
    var url = urlFor(withOrigin, 'abc123');
    check('o embed pede a API',
        url.indexOf('enablejsapi=1') >= 0);
    /*
      O parametro que faltava. Sem ele o YouTube nao tem como validar quem
      incorporou, e responde 153.
    */
    check('e manda a origem da pagina',
        url.indexOf('origin=' + encodeURIComponent('https://iptvburo.test')) >= 0);
    /* Com a API, o overlay desenha os proprios controles. */
    check('os controles do YouTube ficam desligados',
        url.indexOf('controls=0') >= 0);
}());

process.stdout.write('Sem origem, o embed larga a API para o trailer tocar\n');
(function () {
    var url = urlFor(withoutOrigin, 'abc123');
    /*
      O ponto da correcao. `file://` da origem "null", que o YouTube recusa —
      entao nao se pede a API, e nao ha o que recusar.
    */
    check('nao pede a API',
        url.indexOf('enablejsapi') < 0);
    check('e nao inventa uma origem',
        url.indexOf('origin=') < 0);
    /*
      Sem a API as teclas nao respondem, entao os controles do proprio YouTube
      sao ligados: alguem precisa poder pausar.
    */
    check('mas liga os controles do YouTube no lugar',
        url.indexOf('controls=1') >= 0);
    check('e continua comecando sozinho e mudo',
        url.indexOf('autoplay=1') >= 0 && url.indexOf('mute=1') >= 0);
}());

process.stdout.write('O modulo diz quando as teclas tem a quem falar\n');
/* O overlay usa isto para nao prometer controles inertes. */
check('com origem, a API esta disponivel',
    withOrigin.BuroTrailer.apiAvailable() === true);
check('sem origem, nao esta',
    withoutOrigin.BuroTrailer.apiAvailable() === false);

process.stdout.write('O host continua sendo o de sempre\n');
/* A correcao nao pode ter trocado o dominio: `youtube-nocookie` e o que nao
   deixa rastro de publicidade, e e o unico que o modulo confia nas respostas. */
check('o embed continua no youtube-nocookie',
    urlFor(withOrigin, 'x').indexOf('https://www.youtube-nocookie.com/embed/') === 0 &&
    urlFor(withoutOrigin, 'x').indexOf('https://www.youtube-nocookie.com/embed/') === 0);

withOrigin.close();
withoutOrigin.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
