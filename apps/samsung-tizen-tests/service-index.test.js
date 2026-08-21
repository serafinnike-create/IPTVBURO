/*
  Quais títulos da lista do usuário cada serviço carrega.

  Porte do ServiceTitleIndex.kt do Windows. O que este teste guarda é sobretudo
  o que não pode acontecer: um filtro de serviço que confunde dois filmes de
  mesmo nome é pior do que um que perde um deles.
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
['domain', 'service-index'].forEach(function (name) {
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', name + '.js'), 'utf8'));
});
var S = window.BuroServiceIndex;

process.stdout.write('O nome é reduzido ao que dá para comparar\n');
check('os marcadores de qualidade do provedor saem',
    S.normalisedForMatching('72 Horas em Miami 4K [DV][HDR]') === '72 horas em miami');
check('acento e pontuação não impedem o casamento',
    S.normalisedForMatching('Ação & Reação!') === S.normalisedForMatching('Acao e Reacao') ||
    S.normalisedForMatching('Ação & Reação!') === 'acao reacao');
check('o ano entre parênteses sai do nome',
    S.normalisedForMatching('Duna (2021)') === 'duna');
check('dublado e legendado não distinguem títulos',
    S.normalisedForMatching('Duna [L]') === S.normalisedForMatching('Duna Legendado'));

process.stdout.write('O índice casa nome e ano\n');
(function () {
    var index = S.build(
        {
            Netflix: [{ title: 'Duna', year: 2021 }, { title: 'Outro Filme', year: 2020 }],
            HBO: [{ title: 'Duna', year: 1984 }]
        },
        [
            { id: 'm1', name: 'Duna 4K [L]', year: 2021 },
            { id: 'm2', name: 'Duna', year: 1984 },
            { id: 'm3', name: 'Outro Filme', year: 2020 }
        ]
    );
    check('cada serviço recebe os títulos que carrega',
        index.idsFor('Netflix').sort().join(',') === 'm1,m3');
    /*
      O ponto do teste: "Duna" de 2021 e "Duna" de 1984 são filmes diferentes.
      Sem exigir o ano dos dois lados, o filtro juntaria os dois em silêncio.
    */
    check('duas versões do mesmo nome não são confundidas',
        index.idsFor('HBO').join(',') === 'm2' && !index.has('Netflix', 'm2'));
    check('a contagem diz quanto casou, para julgar a cobertura',
        index.countFor('Netflix') === 2 && index.countFor('HBO') === 1);
    check('os serviços vêm do que carrega mais para o que carrega menos',
        index.services().join(',') === 'Netflix,HBO');
}());

process.stdout.write('Sem casamento, o filtro não é oferecido\n');
check('lista vazia dá índice vazio',
    S.build({ Netflix: [{ title: 'Duna', year: 2021 }] }, []).isEmpty());
check('sem resposta do TMDb o índice fica vazio',
    S.build({}, [{ id: 'm1', name: 'Duna', year: 2021 }]).isEmpty());
check('nada em comum dá índice vazio',
    S.build(
        { Netflix: [{ title: 'Um Filme', year: 2021 }] },
        [{ id: 'm1', name: 'Outro', year: 2021 }]
    ).isEmpty());

process.stdout.write('Um título sem ano é deixado de fora\n');
(function () {
    var index = S.build(
        { Netflix: [{ title: 'Sem Ano', year: null }, { title: 'Com Ano', year: 2022 }] },
        [{ id: 'm1', name: 'Sem Ano', year: null }, { id: 'm2', name: 'Com Ano', year: 2022 }]
    );
    check('sem ano dos dois lados, o título não entra',
        !index.has('Netflix', 'm1'));
    check('e o que tem ano continua entrando',
        index.has('Netflix', 'm2'));
}());

process.stdout.write('Entradas malformadas não derrubam o índice\n');
check('nulos e objetos vazios são ignorados',
    S.build(
        { Netflix: [null, {}, { title: '', year: 2021 }, { title: 'Bom', year: 2021 }] },
        [null, {}, { id: 'm1', name: 'Bom', year: 2021 }]
    ).idsFor('Netflix').join(',') === 'm1');
check('o índice vazio responde sem quebrar',
    S.empty().isEmpty() && S.empty().services().length === 0 &&
    S.empty().countFor('Netflix') === 0 && S.empty().idsFor('Netflix').length === 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
