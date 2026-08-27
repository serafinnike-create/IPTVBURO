/*
  A estrela dourada significa alguma coisa?

  O usuario pediu a faixa: nota baixa em branco, nota alta em amarelo. O risco
  do porte nao e a cor — e a escala. Um provedor manda 0-5, outro manda 0-10, as
  vezes na mesma conta, e um corte fixo em 4 marcaria como alta uma nota 4 de 10,
  que e ruim.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var APP = fs.readFileSync(path.join(APP_DIR, 'js', 'app.js'), 'utf8');
var CSS = fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

/*
  A funcao e interna ao modulo, entao o teste a recorta do proprio texto do app
  e a avalia. Assim ela nao pode divergir em silencio de uma copia reescrita
  aqui — o que aconteceria se eu colasse a regra e alguem mudasse o app.
*/
function loadRatingIsHigh() {
    var start = APP.indexOf('function ratingIsHigh');
    var end = APP.indexOf('\n    }', start);
    if (start < 0 || end < 0) { return null; }
    /* eslint-disable no-new-func */
    return new Function('return (' + APP.substring(start, end + 6) + ');')();
}

var ratingIsHigh = loadRatingIsHigh();

check('a funcao de faixa existe no app', typeof ratingIsHigh === 'function');
if (typeof ratingIsHigh !== 'function') {
    process.stdout.write('\nsem ratingIsHigh, o resto nao pode ser medido\n');
    process.exit(1);
}

process.stdout.write('A faixa vale nas duas escalas que os provedores mandam\n');
check('numa escala de 5, quatro para cima acende',
    ratingIsHigh(4) && ratingIsHigh(4.5) && ratingIsHigh(5));
check('e abaixo de quatro nao',
    !ratingIsHigh(3.9) && !ratingIsHigh(2.9) && !ratingIsHigh(1));
/*
  O caso que um corte fixo erraria. Com um `>= 4` cru, um 4 de 10 sairia dourado
  ao lado de um 4.8 de 5 — duas notas muito diferentes com o mesmo destaque.
  Comparar a fracao e o que separa as duas.
*/
check('numa escala de 10, seis e sete e meio continuam apagados',
    !ratingIsHigh(6) && !ratingIsHigh(7.9));
check('e oito para cima acende, que e a mesma fracao de quatro em cinco',
    ratingIsHigh(8) && ratingIsHigh(9.4) && ratingIsHigh(10));

process.stdout.write('Sem nota nao ha estrela para colorir\n');
check('zero, nulo, vazio e texto nao acendem',
    !ratingIsHigh(0) && !ratingIsHigh(null) && !ratingIsHigh(undefined) &&
    !ratingIsHigh('') && !ratingIsHigh('otimo'));
check('negativo tambem nao', !ratingIsHigh(-3));

process.stdout.write('A linha do cartao passou a devolver HTML, e escapa por dentro\n');
/*
  `mediaMetadata` devolve marcacao agora, por causa do span da estrela. Quem
  desenha o cartao nao pode mais escapar a saida inteira, senao as tags
  apareceriam como texto na tela. O escape mudou de lugar, e este par de
  verificacoes guarda a troca — que e invisivel ate alguem ver um `<span>`
  escrito num cartao.
*/
check('o consumidor entrega a linha ja pronta, sem escapar de novo',
    APP.indexOf("'</h3><p>' + metadata + '</p>'") >= 0 &&
    APP.indexOf('escapeHtml(metadata)') === -1);
check('a nota entra escapada dentro da funcao',
    APP.indexOf("escapeHtml(Number(item.rating).toFixed(1))") >= 0);
check('as duas marcas existem no app',
    APP.indexOf('rating-high') >= 0 && APP.indexOf('rating-plain') >= 0);

check('o CSS pinta so a nota alta e deixa a comum herdar a cor da linha',
    /\.rating-high\s*\{[^}]*var\(--gold\)/.test(CSS) &&
    /\.rating-plain\s*\{[^}]*inherit/.test(CSS));

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
