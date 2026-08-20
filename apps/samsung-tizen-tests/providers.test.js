/*
  Serviços reconhecidos pelo nome da categoria.

  Porte do ProviderIdentity.kt e do CategoryLabel.kt do Windows. Os casos aqui
  são os mesmos que o app do Windows trata, porque uma TV que reconhecesse um
  conjunto diferente de serviços mostraria coisas diferentes para "só Netflix"
  em cada aparelho, sem que nenhum dos dois estivesse obviamente errado.
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
['domain', 'providers'].forEach(function (name) {
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', name + '.js'), 'utf8'));
});
var P = window.BuroProviders;

function category(id, name) { return { id: id, name: name }; }

process.stdout.write('O prefixo da seção sai do rótulo\n');
check('separador com espaço vira só a categoria',
    P.categoryLabel('Filmes | Lancamentos') === 'Lancamentos');
check('acento no prefixo não impede o corte',
    P.categoryLabel('Séries | Ação') === 'Ação');
check('prefixo sem separador também sai',
    P.categoryLabel('FILMES LANCAMENTOS') === 'LANCAMENTOS');
check('um nome sem prefixo fica como está',
    P.categoryLabel('Documentários') === 'Documentários');
check('um nome malformado do provedor não vira um chip chamado "|"',
    P.categoryLabel('Filmes |') === 'Filmes |');
check('uma palavra que não é seção não é cortada',
    P.categoryLabel('Cinema Nacional') === 'Cinema Nacional');
/* O prefixo sai uma vez só. "Canais | Filmes e Séries" nomeia uma categoria
   cujo próprio nome começa com palavra de seção; cortar duas vezes deixava
   "e Séries" na tela, um chip que se lê como fragmento porque era um. */
check('o prefixo é removido uma vez, não duas',
    P.categoryLabel('Canais | Filmes e Séries') === 'Filmes e Séries');
check('uma categoria que repete a seção no próprio nome sobrevive',
    P.categoryLabel('Filmes | Filmes de Ação') === 'Filmes de Ação');

process.stdout.write('Os serviços são reconhecidos, e só eles\n');
check('Netflix', P.identityFor('Filmes | Netflix').label === 'Netflix');
check('Prime Video pelos dois nomes',
    P.identityFor('Filmes | Amazon').label === 'Prime Video' &&
    P.identityFor('VOD Prime Video').label === 'Prime Video');
check('Disney+', P.identityFor('Series | Disney+').label === 'Disney+');
check('HBO', P.identityFor('Filmes | HBO Max').label === 'HBO');
check('Globoplay', P.identityFor('Canais | Globoplay').label === 'Globoplay');
check('Paramount+', P.identityFor('Filmes | Paramount+').label === 'Paramount+');
check('Crunchyroll', P.identityFor('Animes | Crunchyroll').label === 'Crunchyroll');
check('um gênero não vira serviço', P.identityFor('Filmes | Acao') === null);
check('lista vazia não vira serviço', P.identityFor('') === null && P.identityFor(null) === null);

process.stdout.write('"Max" é palavra inteira, não pedaço de outra\n');
check('Max sozinho é o serviço', P.identityFor('Filmes | Max').label === 'Max');
check('Cinemax não é Max', P.identityFor('Canais | Cinemax') === null);
check('Maxximum não é Max', P.identityFor('Filmes Maxximum') === null);

process.stdout.write('A separação distingue gênero de serviço\n');
(function () {
    var split = P.split([
        category('c1', 'Filmes | Lancamentos'),
        category('c2', 'Filmes | Netflix'),
        category('c3', 'Filmes | Acao'),
        category('c4', 'Filmes | Netflix 4K'),
        category('c5', 'Filmes | HBO Max')
    ]);
    check('os gêneros ficam do lado dos gêneros',
        split.genres.length === 2 &&
        split.genres[0].label === 'Lancamentos' && split.genres[1].label === 'Acao');
    check('um serviço aparece uma vez só, mesmo com qualificador',
        split.providers.length === 2);
    check('o seletor lê o nome do serviço, não o da categoria',
        split.providers[0].label === 'Netflix' && split.providers[1].label === 'HBO');
    check('hasProviders diz que esta lista arquiva por serviço',
        split.hasProviders === true);
}());

(function () {
    var split = P.split([
        category('c1', 'Filmes | Acao'),
        category('c2', 'Filmes | Drama')
    ]);
    check('uma lista só de gêneros não promete um seletor de serviço',
        split.hasProviders === false && split.providers.length === 0);
}());

process.stdout.write('Filtrar por serviço alcança todas as categorias dele\n');
(function () {
    var categories = [
        category('c1', 'Filmes | Netflix'),
        category('c2', 'Filmes | Netflix 4K'),
        category('c3', 'Filmes | Netflix Legendado'),
        category('c4', 'Filmes | HBO Max'),
        category('c5', 'Filmes | Acao')
    ];
    var ids = P.categoryIdsForLabel(categories, 'Netflix');
    check('escolher Netflix alcança as três categorias da Netflix',
        ids.length === 3 && ids.indexOf('c1') >= 0 && ids.indexOf('c2') >= 0 && ids.indexOf('c3') >= 0);
    check('e não alcança as dos outros',
        ids.indexOf('c4') === -1 && ids.indexOf('c5') === -1);
    check('um serviço ausente da lista não alcança nada',
        P.categoryIdsForLabel(categories, 'Star+').length === 0);
}());

/*
  O que o seletor de gênero precisa mostrar.

  O escopo guarda o id da categoria, porque é por id que o filtro compara. Mas o
  chip mostra texto, e passar o id para `categoryLabel` punha "category-79iyjj"
  na tela — apareceu no emulador. O rótulo tem de vir do split, que já resolveu
  o nome sem o prefixo de seção.
*/
process.stdout.write('O seletor mostra nome, e filtra por id\n');
(function () {
    var categories = [
        category('category-79iyjj', 'Canais | Jogos do Dia'),
        category('category-abc123', 'Canais | Variedades')
    ];
    var split = P.split(categories);
    var chosen = 'category-79iyjj';
    var label = null;
    split.genres.some(function (row) {
        if (row.id === chosen) { label = row.label; return true; }
        return false;
    });
    check('o id da categoria leva ao nome legível dela', label === 'Jogos do Dia');
    check('o id nunca é o que se mostra',
        label !== chosen && String(label).indexOf('category-') === -1);
    check('cada gênero carrega o id junto do rótulo, para o filtro comparar',
        split.genres.length === 2 && split.genres[0].id === 'category-79iyjj');
}());

process.stdout.write('Cada serviço traz marca e cor para a interface\n');
(function () {
    var identity = P.identityFor('Filmes | Netflix');
    check('a marca é curta o bastante para um chip',
        identity.mark.length <= 3);
    check('a cor é um hex de seis dígitos',
        /^#[0-9A-F]{6}$/i.test(identity.colour));
    check('o caminho inverso, do rótulo para a marca, dá o mesmo serviço',
        P.identityForLabel('Netflix').colour === identity.colour);
}());

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
