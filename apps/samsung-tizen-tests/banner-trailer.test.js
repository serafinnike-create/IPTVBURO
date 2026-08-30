/*
  O trailer no banner, e nunca um erro no primeiro ecrã.

  O banner é a primeira coisa que alguém vê, por isso um trailer que falha ali é
  pior do que trailer nenhum: uma capa que não se mexe parece deliberada, um
  rectângulo preto parece uma aplicação avariada.

  Um scan da fonte, porque o embed precisa de rede e de uma televisão. A decisão
  em si está coberta por BannerTrailerTest no modelo partilhado; o que vale a
  pena fixar aqui é que a capa continua desenhada por baixo e que uma falha é
  lembrada.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var app = fs.readFileSync(path.join(APP_DIR, 'js', 'app.js'), 'utf8');
var trailer = fs.readFileSync(path.join(APP_DIR, 'js', 'trailer.js'), 'utf8');
var enrichment = fs.readFileSync(path.join(APP_DIR, 'js', 'hero-enrichment.js'), 'utf8');
var css = fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('O trailer toca no banner\n');

check('o banner desenha o trailer',
    app.indexOf("'<iframe class=\"hero-trailer\" src=\"'") >= 0);
check('com som, sem controlos, em repeticao',
    trailer.indexOf('function bannerEmbedUrl') >= 0 &&
    trailer.indexOf('mute=0&controls=0') >= 0 &&
    trailer.indexOf('bannerEmbedUrl: bannerEmbedUrl') >= 0);
/* Vem com o resto do enriquecimento, por isso não custa um pedido a mais. */
check('o id do trailer atravessa o enriquecimento',
    enrichment.indexOf('youtubeTrailerId: cleanText(details && details.youtubeTrailerId, 32)') >= 0 &&
    enrichment.indexOf('youtubeTrailerId: value.youtubeTrailerId') >= 0);
check('e e guardado quando chega',
    app.indexOf('state.heroTrailers[item.id] =') >= 0);

process.stdout.write('\nA capa continua por baixo\n');

/*
  Desenhados os dois, e não um ou o outro: se o embed não arrancar, o que se vê é
  a capa, exactamente como estava.
*/
check('a capa e o trailer sao camadas, nao uma troca',
    app.indexOf("(primary ? '<img src=\"'") >= 0 &&
    app.indexOf("(trailer ? '<iframe class=\"hero-trailer\"") >= 0);
check('o trailer fica absoluto por cima da capa',
    css.indexOf('.hero-trailer') >= 0 && css.indexOf('pointer-events: none;') >= 0);

process.stdout.write('\nO que falha nao volta a ser tentado\n');

/* Um vídeo retirado continua retirado, e cada tentativa custa uma espera. */
check('uma falha e lembrada',
    app.indexOf('function heroTrailerRecentlyFailed') >= 0 &&
    app.indexOf('HERO_TRAILER_MEMORY_SECONDS') >= 0);
/* Um marco do futuro — relógio corrigido para trás — conta como expirado, senão
   um trailer que funciona ficava suprimido para sempre. */
check('um marco do futuro conta como expirado',
    app.indexOf('return idade >= 0 && idade < HERO_TRAILER_MEMORY_SECONDS;') >= 0);
check('um id que nao tem a forma de um id nao vai para o leitor',
    app.indexOf('BuroDomain.sanitizeYouTubeReference(videoId) ? videoId : null') >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
