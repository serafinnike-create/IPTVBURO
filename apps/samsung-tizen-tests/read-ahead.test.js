/*
  Ler dois minutos à frente da imagem, num filme mas nunca num canal.

  Um filme que para porque a ligação tropeçou dez segundos é evitável: um filme é
  um ficheiro, então o leitor pode estar minutos à frente e nem dar pela falha. Um
  canal ao vivo não tem "à frente" para ler, e o mesmo buffer ali não compra nada
  e custa um arranque mais tardio.

  Um scan da fonte, porque o buffer é definido através de uma API da Tizen que só
  existe na televisão. O que vale a pena fixar é que o valor chega ao leitor, que
  os números batem certo com as outras plataformas, e que o desconhecido fica do
  lado seguro.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var player = fs.readFileSync(path.join(APP_DIR, 'js', 'player.js'), 'utf8');
var app = fs.readFileSync(path.join(APP_DIR, 'js', 'app.js'), 'utf8');
var shared = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'packages', 'domain-model', 'src', 'commonMain',
        'kotlin', 'com', 'lucasserafin94', 'iptvburo', 'domain', 'model', 'PlaybackBuffering.kt'),
    'utf8'
);

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function sharedNumber(name) {
    var match = shared.match(new RegExp('const val ' + name + ' = ([0-9_]+)'));
    return match ? Number(match[1].replace(/_/g, '')) : null;
}

function playerNumber(name) {
    var match = player.match(new RegExp('var ' + name + ' = ([0-9]+);'));
    return match ? Number(match[1]) : null;
}

process.stdout.write('Os mesmos números das outras plataformas\n');

/*
  Sem isto, os números vivem em dois ficheiros que ninguém compara: uma televisão
  que aguentasse uma queda pior do que o computador seria o produto a discutir
  consigo próprio.
*/
check('o buffer de filme é o mesmo do Windows e do Android',
    playerNumber('ON_DEMAND_BUFFER_MS') === sharedNumber('ON_DEMAND_MILLIS'));
check('o buffer de ao vivo é o mesmo',
    playerNumber('LIVE_BUFFER_MS') === sharedNumber('LIVE_MILLIS'));
check('um filme lê muito mais à frente do que um canal',
    playerNumber('ON_DEMAND_BUFFER_MS') > playerNumber('LIVE_BUFFER_MS') * 10);

process.stdout.write('\nO buffer chega ao leitor\n');

check('o leitor aplica o tamanho antes de preparar o fluxo',
    player.indexOf('applyReadAhead(isLive !== false)') >= 0 &&
    player.indexOf('function applyReadAhead') >= 0);
/* Uma televisão sem esta API tem de reproduzir na mesma. */
check('a ausência da API não impede a reprodução',
    player.indexOf('if (webapis.avplay.setBufferingParam)') >= 0);
check('o que tem de chegar antes de começar fica pequeno',
    playerNumber('PLAY_BUFFER_MS') < playerNumber('ON_DEMAND_BUFFER_MS') / 10);

process.stdout.write('\nCada caminho diz se é ao vivo\n');

check('o caminho principal passa o tipo do item',
    app.indexOf('isLiveContent(item.contentType)') >= 0);
check('o catch-up é lido como ficheiro, não como emissão',
    app.indexOf('resolveCatchUp(secret, locator), startPositionMs, false)') >= 0);
/*
  Desconhecido conta como ao vivo, que é o lado seguro: um filme tratado como
  canal fica apenas com o buffer menor que já tinha, enquanto um canal tratado
  como filme começaria dois minutos mais tarde.
*/
check('o desconhecido fica do lado seguro',
    app.indexOf("contentType !== 'MOVIE' && contentType !== 'SERIES'") >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
