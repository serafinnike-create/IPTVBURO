/*
  A prateleira que o calendário traz, e leva embora.

  Porte do `SeasonalCollections` do domínio compartilhado, que o Windows e o
  Android já usavam e a TV não tinha. As janelas e os termos são de lá, então o
  que este teste guarda é sobretudo que o porte não os traiu: as três
  plataformas devem mostrar a mesma prateleira no mesmo dia.

  E o que importa mais do que mostrar: **não** mostrar. Uma noite comum de março
  tem de ver a Home comum, e não uma fileira procurando um motivo para existir.
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
['domain', 'seasonal'].forEach(function (name) {
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', name + '.js'), 'utf8'));
});
var S = window.BuroSeasonal;

/* Uma data local, sem fuso a atrapalhar: o mês é o que o calendário da pessoa
   mostra, não o UTC. */
function on(month, day) { return new Date(2026, month - 1, day, 12, 0, 0); }

function idOn(month, day) {
    var collection = S.primaryCollectionFor(on(month, day));
    return collection ? collection.id : null;
}

process.stdout.write('Na maior parte do ano não há prateleira sazonal\n');
/*
  O ponto do teste. Uma fileira temática que aparece o ano todo deixa de
  significar alguma coisa, e é ruído numa Home que já tem oito prateleiras.
*/
check('uma noite comum de março não tem coleção',
    idOn(3, 15) === null);
check('nem uma de agosto, setembro ou maio',
    idOn(8, 20) === null && idOn(9, 10) === null && idOn(5, 5) === null);

process.stdout.write('Cada janela abre e fecha na data que o domínio declara\n');
check('dezembro inteiro é Natal, até o dia 26',
    idOn(12, 1) === 'christmas' && idOn(12, 25) === 'christmas' && idOn(12, 26) === 'christmas');
/*
  A janela do Ano Novo atravessa a virada, e é por isso que a checagem não pode
  ser uma comparação simples de mês e dia: 6 de janeiro é *depois* de 27 de
  dezembro no calendário, mas *antes* na comparação numérica.
*/
check('a virada de ano atravessa dezembro e janeiro',
    idOn(12, 27) === 'new-year' && idOn(12, 31) === 'new-year' &&
    idOn(1, 1) === 'new-year' && idOn(1, 6) === 'new-year');
check('e o dia 7 de janeiro já não tem nada',
    idOn(1, 7) === null);
/* Halloween termina em 1º de novembro, o que também dá a volta no mês. */
check('o Halloween cobre a quinzena e o dia seguinte',
    idOn(10, 18) === 'halloween' && idOn(10, 31) === 'halloween' && idOn(11, 1) === 'halloween');
check('mas o começo de outubro não, senão seria só uma fileira de terror',
    idOn(10, 1) === null && idOn(10, 17) === null);

process.stdout.write('O Dia dos Namorados tem as duas datas que o produto atende\n');
/*
  O Brasil guarda 12 de junho além de 14 de fevereiro, e o aplicativo roda nos
  dois mercados: uma janela só deixaria metade dos usuários sem a prateleira.
*/
check('fevereiro, para o mercado internacional',
    idOn(2, 7) === 'valentines' && idOn(2, 14) === 'valentines' && idOn(2, 15) === 'valentines');
check('e junho, para o Dia dos Namorados brasileiro',
    idOn(6, 5) === 'valentines' && idOn(6, 12) === 'valentines' && idOn(6, 13) === 'valentines');
check('julho inteiro são as férias em família',
    idOn(7, 1) === 'school-holidays' && idOn(7, 31) === 'school-holidays');

process.stdout.write('Uma prateleira por vez\n');
/* A Home tem espaço para uma fileira sazonal; duas competiriam entre si em vez
   de destacar o dia. */
check('mesmo com janelas vizinhas, sai uma só',
    typeof S.primaryCollectionFor(on(12, 26)) === 'object' &&
    S.collectionsFor(on(12, 26)).length === 1);

process.stdout.write('O nome vem traduzido, com reserva\n');
check('português, alemão e italiano têm nome próprio',
    S.titleFor(S.primaryCollectionFor(on(12, 25)), 'pt-BR') === 'Especial de Natal' &&
    S.titleFor(S.primaryCollectionFor(on(12, 25)), 'de') === 'Weihnachtsspecial' &&
    S.titleFor(S.primaryCollectionFor(on(12, 25)), 'it') === 'Speciale Natale');
/* Uma fileira sem tradução é uma falha menor do que uma encabeçada por um
   identificador cru. */
check('um idioma sem tradução cai no inglês, não no identificador',
    S.titleFor(S.primaryCollectionFor(on(12, 25)), 'ja') === 'Christmas Special');

process.stdout.write('O casamento por nome ignora acento e caixa\n');
(function () {
    var natal = S.primaryCollectionFor(on(12, 25));
    var items = [
        { id: 'a', name: 'O GRINCH' },
        { id: 'b', name: 'Férias Natalinas' },
        { id: 'c', name: 'Christmas Vacation' },
        { id: 'd', name: 'Duna' }
    ];
    var found = S.matches(natal, items).map(function (item) { return item.id; });
    /*
      Os termos misturam português e inglês porque uma lista de provedor traz os
      dois lado a lado — e uma lista num idioma só encontra metade do que existe.
    */
    check('encontra em maiúsculas, com acento e em inglês',
        found.join(',') === 'a,b,c');
    check('e não arrasta o que não pertence à coleção',
        found.indexOf('d') < 0);
}());

process.stdout.write('Termos curtos não arrastam palavras parecidas\n');
(function () {
    var natal = S.primaryCollectionFor(on(12, 25));
    /*
      O casamento é um `indexOf` sem acento, então "natal" sozinho pegaria
      "Natalie" e "fatal". O domínio escreve os termos longos o bastante por
      isso, e o porte precisa ter copiado essa escolha.
    */
    var found = S.matches(natal, [
        { id: 'x', name: 'Natalie' },
        { id: 'y', name: 'Atração Fatal' }
    ]);
    check('"Natalie" e "Fatal" não entram na prateleira de Natal',
        found.length === 0);
}());

process.stdout.write('Sem título que case, não há prateleira\n');
check('uma lista sem nada natalino devolve vazio',
    S.matches(S.primaryCollectionFor(on(12, 25)), [{ id: 'a', name: 'Duna' }]).length === 0);
check('e uma lista vazia também, sem quebrar',
    S.matches(S.primaryCollectionFor(on(12, 25)), []).length === 0 &&
    S.matches(null, [{ id: 'a', name: 'Natal' }]).length === 0);
/* O teto existe para a fileira não virar o catálogo inteiro numa TV. */
check('o resultado respeita o teto pedido',
    S.matches(S.primaryCollectionFor(on(12, 25)), [
        { id: '1', name: 'Christmas 1' }, { id: '2', name: 'Christmas 2' },
        { id: '3', name: 'Christmas 3' }, { id: '4', name: 'Christmas 4' }
    ], 2).length === 2);

process.stdout.write('O termo curto demais foi removido, e o teste diz por que' + String.fromCharCode(10));
/*
  O domínio compartilhado avisa que "natal sozinho arrastaria Natalie e fatal" e
  mesmo assim mantém o termo — Windows e Android põem "Natalie" numa prateleira
  de Natal. Aqui `natal` saiu e `natalin` ficou: pega natalino, natalina e
  natalinas, que é o que a palavra existe para pegar.
*/
check('"natalinas" continua entrando, pelo termo sem terminação',
    S.matches(S.primaryCollectionFor(on(12, 25)),
        [{ id: 'a', name: 'Férias Natalinas' }]).length === 1);
check('e "Natalie" fica de fora, que era o defeito',
    S.matches(S.primaryCollectionFor(on(12, 25)),
        [{ id: 'b', name: 'Natalie' }]).length === 0);

window.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
