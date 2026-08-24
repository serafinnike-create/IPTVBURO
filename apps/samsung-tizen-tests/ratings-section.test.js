/*
  O painel de notas do público na ficha do título.

  Duas coisas guardadas aqui. A primeira é quando o painel não deve aparecer: o
  TMDb responde 0.0 com zero votos para títulos que guarda mas ninguém avaliou,
  e "0%" se lê como veredito e não como a falta de um. A segunda é de quem é o
  número — no aplicativo do Windows este mesmo slot buscava um logotipo que
  acabou sendo a marca da Netflix, desenhada ao lado das palavras "Nota TMDb".
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var SCRIPT_FILES = (function () {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var files = [];
    var match = pattern.exec(html);
    while (match) {
        files.push(match[1]);
        match = pattern.exec(html);
    }
    return files;
}());

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return []; },
            saveData: function (name, value, password, success) { success(); },
            getData: function () { throw { name: 'NotFoundError' }; },
            removeData: function () {}
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    return window;
}

var window = loadApp();
var section = window.BuroApp._ratingsSection;

process.stdout.write('O painel aparece quando a nota vale alguma coisa\n');
(function () {
    var html = section({ tmdbRating: 7.6, tmdbVoteCount: 12438 });
    check('a nota vira porcentagem, que é como se lê de relance',
        html.indexOf('76%') > 0);
    /*
      A marca é escrita e não é um logotipo buscado da rede: um caminho de
      imagem pode silenciosamente virar o de outra empresa, e foi o que
      aconteceu no Windows.
    */
    check('a marca diz TMDb, em texto',
        html.indexOf('>TMDb<') > 0);
    check('e o painel não busca imagem nenhuma para isso',
        html.indexOf('<img') < 0 && html.indexOf('image.tmdb.org') < 0);
    check('a contagem de votos acompanha a nota',
        html.indexOf('12 mil') > 0);
    check('o painel se anuncia para quem usa leitor de tela',
        html.indexOf('aria-label') > 0 && html.indexOf('role="group"') > 0);
}());

process.stdout.write('Poucos votos não são uma nota\n');
check('dezenove votos não desenham painel',
    section({ tmdbRating: 9.5, tmdbVoteCount: 19 }) === '');
check('vinte votos já desenham',
    section({ tmdbRating: 9.5, tmdbVoteCount: 20 }).indexOf('95%') > 0);
check('a contagem exata aparece abaixo de mil',
    section({ tmdbRating: 8, tmdbVoteCount: 340 }).indexOf('340 votos') > 0);

process.stdout.write('Sem nota, o painel some em vez de dizer zero\n');
/*
  O ponto do teste: o TMDb responde 0.0 com zero votos para títulos que guarda
  mas ninguém avaliou. "0%" se lê como veredito.
*/
check('nota zero não vira "0%"',
    section({ tmdbRating: 0, tmdbVoteCount: 0 }) === '');
check('nota zero com muitos votos também não',
    section({ tmdbRating: 0, tmdbVoteCount: 5000 }) === '');
check('nota do provedor não se apresenta como nota TMDb',
    section({ rating: 9.9, voteCount: 5000 }) === '');
check('ficha sem os campos não quebra',
    section({}) === '' && section(null) === '' && section(undefined) === '');
check('valores inválidos não viram NaN na tela',
    section({ tmdbRating: 'oito', tmdbVoteCount: 'muitos' }) === '');

window.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
