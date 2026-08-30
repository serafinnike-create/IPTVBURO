/*
  O guia ao vivo na televisão: canais de um lado, o que está a dar do outro.

  O catálogo responde "que canais há". Isto responde "o que está a dar", que é a
  pergunta que alguém tem quando se senta com o comando.

  Um scan da fonte, porque isto vive dentro de um app.js de doze mil linhas que
  precisa de um DOM inteiro e de um fornecedor a responder a pedidos de EPG. O
  que vale a pena fixar é que as pontas estão ligadas: um ecrã que ninguém
  desenha e um botão que ninguém liga passam num teste de sintaxe.

  A aritmética — que canais ir buscar, quanto falta de um programa, o que
  pertence ao ecrã a partir de agora — está coberta por LiveGuideTest no modelo
  partilhado, que o Windows e o Android também usam.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var app = fs.readFileSync(path.join(APP_DIR, 'js', 'app.js'), 'utf8');
var i18n = fs.readFileSync(path.join(APP_DIR, 'js', 'i18n.js'), 'utf8');
var css = fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('O guia existe e alcança-se\n');

check('há uma entrada na fita de navegação',
    app.indexOf("{ section: 'GUIDE', label: 'guideTitle'") >= 0);
check('a secção desenha o guia',
    app.indexOf("state.section === 'GUIDE') { renderGuide(); }") >= 0);
check('o guia tem estilo próprio',
    css.indexOf('.guide-layout') >= 0 && css.indexOf('.guide-channel') >= 0);

process.stdout.write('\nChegar a uma linha escolhe o canal\n');

/*
  Na televisão o D-pad é como tudo se move, por isso o foco é a escolha: pedir
  uma segunda tecla para ver a programação seria o trabalho que o guia existe
  para tirar.
*/
check('a linha do canal tem acção de foco',
    app.indexOf("data-action=\"guide-focus\"") >= 0);
check('a acção está ligada',
    app.indexOf("action === 'guide-focus'") >= 0 &&
    app.indexOf('function focusGuideChannel') >= 0);
check('o botão de ver toca o canal',
    app.indexOf("action === 'guide-watch'") >= 0);

process.stdout.write('\nA programação é pedida com cuidado\n');

/* O fornecedor não tem chamada para vários canais: é um pedido por canal. */
check('o canal em foco vai buscar a sua programação',
    app.indexOf('loadGuideSchedule(channels[index])') >= 0);
/* Descer uma lista uma linha de cada vez é como se lê um guia, por isso as
   próximas linhas valem a pena ter em mãos. */
check('os vizinhos também são carregados',
    app.indexOf('GUIDE_PREFETCH_RADIUS') >= 0);
/* Uma grelha muda quando o programa seguinte começa, não ao segundo. */
check('o que já está em mãos não é pedido outra vez',
    app.indexOf('GUIDE_FRESHNESS_SECONDS') >= 0);
check('o mesmo canal não é pedido duas vezes ao mesmo tempo',
    app.indexOf('state.guideInFlight[channel.id]') >= 0);
/* Cada entrada são horas de programas de um canal, e uma noite a percorrer
   quatrocentos canais guardaria a grelha inteira. */
check('as programações guardadas têm tecto',
    app.indexOf('GUIDE_MAX_SCHEDULES') >= 0 &&
    app.indexOf('function guideRemember') >= 0);

process.stdout.write('\nO ecrã diz o que sabe\n');

check('o programa a decorrer é marcado',
    app.indexOf("'<li class=\"guide-program' + (isNow ? ' now' : '')") >= 0);
/* Uma barra num programa sem horas afirmaria que ele acabou de começar. */
check('a barra só aparece no programa a decorrer',
    app.indexOf("(isNow ? '<span class=\"guide-bar\">") >= 0);
check('o que já acabou sai da lista',
    app.indexOf('return !(fim > 0 && fim <= nowSeconds);') >= 0);
check('os rótulos estão nos cinco idiomas',
    (i18n.match(/guideTitle:/g) || []).length === 5 &&
    (i18n.match(/guideNoSchedule:/g) || []).length === 5 &&
    (i18n.match(/guideWatch:/g) || []).length === 5);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
