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
var storage = fs.readFileSync(path.join(APP_DIR, 'js', 'storage.js'), 'utf8');
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
check('sem controlos e em repeticao',
    trailer.indexOf('function bannerEmbedUrl') >= 0 &&
    trailer.indexOf('controls=0') >= 0 &&
    trailer.indexOf('loop=1') >= 0 &&
    trailer.indexOf('bannerEmbedUrl: bannerEmbedUrl') >= 0);
/*
  Calado ao arrancar, e o som a subir so depois.

  Nenhum motor deixa um video comecar sozinho com audio: pedido com som, o
  banner nao arrancava de todo -- ficava um botao de play parado por cima de
  uma imagem, que foi o que se viu no Windows com este mesmo embed. Por isso
  mute=1 aqui nao e uma escolha de gosto, e a unica forma de ele tocar.
*/
check('arranca calado, porque so assim arranca',
    trailer.indexOf('mute=1&controls=0') >= 0);
check('e o som sobe assim que ele ja esta a tocar',
    trailer.indexOf('function raiseBannerSound') >= 0 &&
    trailer.indexOf("func: 'unMute'") >= 0 &&
    trailer.indexOf('playerState === 1') >= 0 &&
    trailer.indexOf('raiseBannerSound: raiseBannerSound') >= 0 &&
    app.indexOf('BuroTrailer.raiseBannerSound(frame)') >= 0);
/*
  Uma televisão que começa a falar sozinha assim que se liga é pior que uma
  calada. O trailer arranca sempre em silêncio -- nenhum motor deixa arrancar
  com áudio -- e o som só sobe depois, e só se alguém o tiver pedido.
*/
check('o som só sobe quando foi pedido',
    app.indexOf('if (!state.preferences.bannerTrailerSound) { return; }') >= 0);
check('e há onde o pedir',
    app.indexOf("settingCard('bannerTrailerSound', 'bannerTrailerSound')") >= 0 &&
    storage.indexOf('bannerTrailerSound: false') >= 0);

/*
  O ouvinte de mensagens é retirado quando deixa de ser preciso. Isto corre a
  cada desenho da Home, e um addEventListener que ninguém tira acumula um
  ouvinte por desenho -- numa TV, que fica ligada dias a fio, é uma fuga a
  sério. O teste de resistência da suíte apanhou-a.
*/
check('o ouvinte do som não fica pendurado',
    trailer.indexOf("window.removeEventListener('message', onMessage)") >= 0);

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
    app.indexOf("(trailer ? '<span class=\"hero-trailer-stage\"") >= 0 &&
    app.indexOf("'<iframe class=\"hero-trailer\" src=\"'") >= 0);
check('o trailer fica absoluto por cima da capa',
    css.indexOf('.hero-trailer') >= 0 && css.indexOf('pointer-events: none;') >= 0);
check('o trailer ocupa uma janela integrada no canto inferior direito',
    app.indexOf('<span class="hero-trailer-stage"') >= 0 &&
    css.indexOf('.hero-trailer-stage') >= 0 &&
    /\.hero-trailer-stage\s*\{[^}]*right:\s*0;\s*bottom:\s*0;[^}]*width:\s*58%;\s*height:\s*84%/s.test(css) &&
    css.indexOf('.real-home-hero.hero-trailer-playing .hero-synopsis') >= 0);
check('cada troca conserva o trailer atômico com o item do banner',
    app.indexOf('data-trailer-item-id="\' + attr(item.id)') >= 0 &&
    app.indexOf('current.id !== itemId') >= 0 &&
    app.indexOf('data.heroTrailerPlayingId = null;') >= 0);
check('um trailer em reprodução não é cortado na rotação curta',
    app.indexOf('scheduleHomeHeroRotation(data, 60000);') >= 0 &&
    app.indexOf('function scheduleHomeHeroRotation(data, requestedDelay)') >= 0);
check('reduzir movimento mantém a capa e desliga o vídeo automático',
    app.indexOf('if (state.preferences.reducedMotion) { return null; }') >= 0);
check('o iframe so aparece depois de reproducao confirmada',
    trailer.indexOf('function observeBackgroundFrames') >= 0 &&
    trailer.indexOf("payload.event === 'onStateChange'") >= 0 &&
    trailer.indexOf('Number(payload.info) === 1') >= 0 &&
    trailer.indexOf('BACKGROUND_READY_TIMEOUT_MILLIS = 10000') >= 0 &&
    css.indexOf('.hero-trailer.trailer-ready') >= 0 &&
    app.indexOf('observeBackgroundTrailers();') >= 0);
check('Descobrir busca metadados do cartao atual e reserva uma coluna para o trailer',
    app.indexOf('function scheduleDiscoverEnrichment') >= 0 &&
    app.indexOf('BuroHeroEnrichment.start(source, [item]') >= 0 &&
    app.indexOf('discoverPreviewHtml(current)') >= 0 &&
    css.indexOf('.discover-decision-panel') >= 0 &&
    css.indexOf('.discover-preview') >= 0);

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
