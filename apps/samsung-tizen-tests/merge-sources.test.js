/*
  Várias assinaturas mostradas como um só catálogo.

  Quem compra uma segunda lista para tapar as falhas da primeira acaba a saltar
  entre as duas para descobrir qual tem o filme — trabalho que o aplicativo devia
  estar a fazer.

  Um scan da fonte, porque isto vive dentro de um app.js de onze mil linhas que
  precisa de um DOM inteiro. O que vale a pena fixar é que as pontas estão ligadas:
  um interruptor que guarda uma escolha e não age sobre ela foi exactamente o que
  foi entregue no Windows à primeira.
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

process.stdout.write('O interruptor existe e está ligado\n');

/* Com uma lista não há nada para juntar, e o interruptor seria uma pergunta sobre nada. */
check('só aparece com mais de uma lista',
    app.indexOf('state.sources.length > 1') >= 0);
check('o interruptor chama a acção',
    app.indexOf("action === 'toggle-merge-sources'") >= 0 &&
    app.indexOf('function toggleMergeSources') >= 0);
check('a escolha é guardada',
    app.indexOf('state.preferences.mergeEverySource = !mergeEverySource()') >= 0);
check('o rótulo está traduzido nos cinco idiomas',
    (i18n.match(/mergeSourcesTitle/g) || []).length === 5);
check('a faixa tem estilo próprio',
    css.indexOf('.merge-toggle') >= 0);

process.stdout.write('\nA escolha muda o que o catálogo mostra\n');

/*
  Uma fonte falsa já significava "todas as fontes" em todo o código a jusante,
  por isso juntar é escolher isso — e não um segundo caminho a discordar deste.
*/
check('juntar apaga o filtro de fonte',
    app.indexOf('var sourceId = mergeEverySource() ? null : (state.activeSource && state.activeSource.id);') >= 0);

/*
  Colapsar cópias do mesmo provedor é uma escolha sobre qualidades; mostrar o
  mesmo filme uma vez por assinatura é a duplicação que juntar existe para acabar.
  São duas coisas diferentes com a mesma aparência.
*/
check('os repetidos saem mesmo com o agrupamento por qualidade desligado',
    app.indexOf("state.preferences.collapseDuplicateTitles === false && !mergeEverySource()") >= 0);

/* Desligado por defeito: quem tem uma lista não ganha nada com isto. */
check('está desligado por defeito',
    app.indexOf('Boolean(state.preferences && state.preferences.mergeEverySource)') >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
