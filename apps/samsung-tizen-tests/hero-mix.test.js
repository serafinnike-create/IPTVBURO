/*
  A mistura do banner, na TV.

  Ordenado só por posição editorial, o banner enche-se do que o catálogo tem
  mais — e passar por vinte títulos do mesmo ano e da mesma prateleira não
  ensina nada sobre o que mais lá existe.

  A regra é a mesma dos outros dois apps (HeroSelection.mixed no modelo
  partilhado), e o que este teste guarda é sobretudo que o porte não a traiu:
  as vagas são um objetivo e não uma exigência, nada se perde e nada se repete.
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

var dom = new JSDOM('<!doctype html><html><body></body></html>', { runScripts: 'outside-only' });
var window = dom.window;
['domain'].forEach(function (name) {
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', name + '.js'), 'utf8'));
});
var BuroDomain = window.BuroDomain;

var THIS_YEAR = 2026;

function item(id, year, contentType, categoryIds, addedAt) {
    return {
        id: id,
        name: 'Titulo ' + id,
        year: year,
        contentType: contentType || 'MOVIE',
        categoryIds: categoryIds || [],
        addedAtEpochSeconds: addedAt || null,
    };
}

/* Trinta lançamentos, mais um de cada coisa que a mistura procura. */
function pool() {
    var rows = [];
    var index;
    var hoje = Math.floor(Date.now() / 1000) - 3600;
    for (index = 1; index <= 3; index += 1) {
        rows.push(item('hoje' + index, THIS_YEAR, 'MOVIE', [], hoje));
    }
    for (index = 1; index <= 30; index += 1) {
        rows.push(item('novo' + index, THIS_YEAR));
    }
    rows.push(item('serie', THIS_YEAR, 'SERIES'));
    rows.push(item('velho1', THIS_YEAR - 30));
    rows.push(item('velho2', THIS_YEAR - 25));
    rows.push(item('meio1', THIS_YEAR - 8));
    rows.push(item('meio2', THIS_YEAR - 9));
    rows.push(item('anime', THIS_YEAR - 5, 'MOVIE', ['Animes | Lancamentos']));
    return rows;
}

function idsOf(rows) {
    return rows.map(function (row) { return row.id; });
}

process.stdout.write('O banner não é vinte vezes a mesma coisa\n');

var mixed = BuroDomain.mixHeroRotation(pool(), THIS_YEAR);
var front = idsOf(mixed.slice(0, 6));

/* O que chegou hoje vem à frente: é para isso que o banner serve. */
check('os lançamentos do dia vêm à frente',
    idsOf(mixed.slice(0, 3)).join(',') === 'hoje1,hoje2,hoje3');
check('leva um anime', front.indexOf('anime') >= 0);
check('leva uma série', front.indexOf('serie') >= 0);
check('leva um filme antigo', front.indexOf('velho1') >= 0);

/* E tudo o que vem depois é lançamento: o banner é sobre o que é novo, e as
   outras três vagas existem para ele não ser *só* isso. */
check('o resto são lançamentos',
    mixed.slice(6, 14).every(function (row) {
        return Number(row.year) >= THIS_YEAR - 2;
    }));

process.stdout.write('A mistura reordena, não filtra\n');

var original = pool();
var reordered = BuroDomain.mixHeroRotation(original, THIS_YEAR);
check('não perde nem repete títulos',
    reordered.length === original.length &&
    idsOf(reordered).sort().join(',') === idsOf(original).sort().join(','));

/* As vagas são um objetivo: um fornecedor pequeno sem títulos antigos tem de
   encher o banner na mesma, em vez de mostrar menos. */
var onlyNew = [];
for (var i = 1; i <= 10; i += 1) { onlyNew.push(item('novo' + i, THIS_YEAR)); }
check('um catálogo sem uma das espécies enche o banner à mesma',
    BuroDomain.mixHeroRotation(onlyNew, THIS_YEAR).length === 10);

/* Os fornecedores deixam o ano vazio a toda a hora, e tratá-lo como antigo
   enchia a vaga dos antigos com títulos de que ninguém sabe a idade. */
var withUnknown = onlyNew.concat([item('sem-ano', null)]);
check('um ano desconhecido não conta como antigo',
    BuroDomain.mixHeroRotation(withUnknown, THIS_YEAR).length === withUnknown.length);

check('uma lista curta fica como está',
    BuroDomain.mixHeroRotation([item('um', THIS_YEAR)], THIS_YEAR).length === 1);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
