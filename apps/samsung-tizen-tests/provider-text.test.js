/*
  Sinopses que chegam com os acentos ja destruidos.

  Vistas numa lista real: "O tenente Marion Cobretti est? no centro de uma
  s?rie de assassinatos" e "carteiras de motoristas ca?adas e carros apreendidos
  por viola??es de tr?nsito". O fornecedor converte o catalogo de uma codificacao
  de um byte sem cuidado, e cada letra acentuada chega como interrogacao. Nao ha
  decodificacao que desfaca isto -- os bytes desaparecem antes de nos chegarem.

  A regra e extraida da fonte e corrida aqui, porque o app.js inteiro precisa de
  um DOM que este teste nao quer montar so para verificar uma expressao regular.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'app.js');
var source = fs.readFileSync(APP, 'utf8');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

/* A funcao real, extraida da fonte em vez de reescrita: uma copia aqui passaria
   no teste mesmo que a do app deixasse de existir. */
var body = source.slice(source.indexOf('function usableSynopsis'));
body = body.slice(0, body.indexOf('\n    }') + 6);
/* eslint-disable no-new-func */
var usableSynopsis = new Function('return ' + body.trim())();

process.stdout.write('O que o fornecedor estragou fica escondido\n');

check('uma sinopse com acentos perdidos e escondida',
    usableSynopsis('o exterm?nio com o n?mero de homic?dios aumentando') === null);
check('e a segunda que foi vista tambem',
    usableSynopsis('carteiras ca?adas por viola??es de tr?nsito') === null);

process.stdout.write('\nO que esta bom continua a aparecer\n');

check('uma sinopse com acentos a serio passa',
    usableSynopsis('Irreverente comédia da HBO que traz as histórias da Flórida.') !== null);
/* O caso que uma regra grosseira destruiria: contar toda a interrogacao
   deitaria fora um paragrafo perfeitamente legivel. */
check('uma pergunta a serio sobrevive',
    usableSynopsis('Quem matou o pai dele? Ninguem sabe.') !== null);
check('e outra tambem',
    usableSynopsis('Sera que ela consegue? O tempo dira.') !== null);
/* Uma sozinha pode ser gralha de quem escreveu, e deitar fora uma sinopse boa
   e um mal por si. */
check('uma marca sozinha e tolerada',
    usableSynopsis('O filme conta a hist?ria de um homem comum.') !== null);
check('texto vazio nao e dano',
    usableSynopsis('') === null && usableSynopsis(null) === null);

process.stdout.write('\nA regra esta ligada aos ecras\n');

check('a sinopse do detalhe passa pela regra',
    source.indexOf("escapeHtml(usableSynopsis(details.plot) || t('noSynopsis'))") >= 0);
check('o cartao de partilha tambem',
    (source.match(/description: usableSynopsis\(details\.plot\)/g) || []).length === 2);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
