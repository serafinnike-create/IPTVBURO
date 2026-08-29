/* Contrato de início rápido e abastecimento contínuo do AVPlay Samsung. */
'use strict';

var fs = require('fs');
var path = require('path');
var player = fs.readFileSync(path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'player.js'), 'utf8');
var app = fs.readFileSync(path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'app.js'), 'utf8');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('Início rápido do player Samsung\n');
check('o AVPlay recebe a unidade oficial em segundos',
    player.indexOf("'PLAYER_BUFFER_SIZE_IN_SECOND'") >= 0);
check('a unidade inválida antiga não volta',
    player.indexOf('PLAYER_BUFFER_SIZE_IN_TIME') < 0);
/*
  A partida continua no minimo; a retomada deixou de estar.

  `PLAYER_BUFFER_FOR_PLAY` e quanto precisa chegar ANTES de a imagem aparecer, e
  quatro segundos e o minimo oficial — pedir mais ali faria a tela esperar em vez
  de encher por baixo. Isso nao mudou e nao deve mudar.

  `PLAYER_BUFFER_FOR_RESUME` mudou de proposito: e o reservatorio que decide se
  uma queda de rede vira uma pausa na tela. Um filme guarda dois minutos, um
  canal ao vivo guarda segundos, e `playback-buffering.test.js` mede os valores
  executando o player em vez de ler o texto do arquivo.
*/
check('a partida continua pedindo somente os quatro segundos mínimos',
    player.indexOf('var FAST_START_BUFFER_SECONDS = 4;') >= 0 &&
    player.indexOf("'PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', FAST_START_BUFFER_SECONDS") >= 0);
check('e a retomada distingue filme de canal ao vivo',
    player.indexOf('isLive ? LIVE_RESUME_SECONDS : ON_DEMAND_RESUME_SECONDS') >= 0);
check('o buffer é definido no estado IDLE antes de preparar',
    player.indexOf('applyFastStartBuffering(isLive);') < player.indexOf('webapis.avplay.prepareAsync'));
check('a tela de preparação possui limite e vira falha de conexão',
    player.indexOf('var PREPARE_TIMEOUT_MS = 20000;') >= 0 &&
    player.indexOf("fail({ code: 'PLAYBACK_CONNECTION' })") >= 0);
check('sucesso, erro e fechamento cancelam o temporizador',
    (player.match(/clearPrepareTimer\(\)/g) || []).length >= 4);

process.stdout.write('\nClassificação dos caminhos\n');
check('Xtream e Stalker informam se o item é ao vivo',
    (app.match(/isLiveContent\(item\.contentType\)/g) || []).length >= 3);
check('M3U também informa filme, episódio ou ao vivo',
    app.indexOf('BuroPlayer.play(url, startPositionMs, isLiveContent(item.contentType));') >= 0);
check('catch-up continua classificado como arquivo sob demanda',
    app.indexOf('resolveCatchUp(secret, locator), startPositionMs, false)') >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
