/*
  Contrato do baralho Descobrir.

  Estes vetores espelham DiscoveryDeck.kt. A TV recebe os mesmos candidatos e
  deve tomar a mesma decisão sem depender de IndexedDB, rede ou DOM.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var vm = require('vm');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function candidate(id, title, genres, rating) {
    return { id: id, title: title, genres: genres || [], rating: rating == null ? null : rating };
}

var sandbox = { window: {} };
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'), sandbox);
var Domain = sandbox.BuroDomain;
var build = Domain.discoveryDeck;
var after = Domain.discoverySessionAfter;
var leaningFor = Domain.discoverySessionLeaningFor;
var score = Domain.discoveryScore;

process.stdout.write('Contrato compartilhado do baralho Descobrir\n');
check('o domínio publica o construtor puro do baralho', typeof build === 'function');
check('o domínio publica o feedback puro da sessão', typeof after === 'function' && typeof leaningFor === 'function');
check('o domínio fixa uma mão finita de quinze cartas e duas surpresas',
    Domain.DISCOVERY_DECK_SIZE === 15 && Domain.DISCOVERY_SURPRISE_SLOTS === 2);

if (typeof build === 'function' && typeof after === 'function' && typeof leaningFor === 'function' && typeof score === 'function') {
    check('sem candidatos o baralho fica vazio', build([], {}, {}, 0).length === 0);

    check('sem gosto conhecido entram primeiro a nota maior e depois o título', (function () {
        var deck = build([
            candidate('b', 'Beta', [], 8), candidate('a', 'Alfa', [], 8), candidate('c', 'Cinema', [], 9)
        ], {}, {}, 0);
        return deck.map(function (row) { return row.id; }).join(',') === 'c,a,b';
    }()));

    check('ids vazios, repetidos e já vistos nunca são oferecidos', (function () {
        var deck = build([
            candidate('', 'Sem id', [], 10), candidate('seen', 'Já visto', [], 10),
            candidate('same', 'Primeiro', [], 8), candidate('same', 'Duplicado', [], 9)
        ], { seenIds: ['seen'] }, {}, 0);
        return deck.length === 1 && deck[0].title === 'Primeiro';
    }()));

    check('um favorito vale o dobro de uma visualização do mesmo gênero',
        score(candidate('action', 'Ação', ['Ação'], 0), { favouriteGenres: ['acao'] }, {}) === 0.2 &&
        score(candidate('view', 'Drama', ['Drama'], 0), { watchedGenres: ['drama'] }, {}) === 0.1);

    check('a correspondência de gênero vence uma nota alta sem afinidade', (function () {
        var deck = build([
            candidate('liked', 'Afinidade', ['Ação'], 2), candidate('rated', 'Nota alta', ['Drama'], 10)
        ], { favouriteGenres: ['ação', 'ação'] }, {}, 0);
        return deck[0].id === 'liked';
    }()));

    check('duas vagas finais descobrem gêneros fora do gosto conhecido', (function () {
        var rows = [];
        var index;
        for (index = 0; index < 15; index += 1) {
            rows.push(candidate('action-' + index, 'Ação ' + index, ['Ação'], 10 - (index / 10)));
        }
        rows.push(candidate('doc', 'Documentário', ['Documentário'], 9.8));
        rows.push(candidate('comedy', 'Comédia', ['Comédia'], 9.7));
        var deck = build(rows, { favouriteGenres: ['ação'] }, {}, 0);
        return deck.length === 15 && deck.slice(13).every(function (row) { return row.id === 'doc' || row.id === 'comedy'; });
    }()));

    check('a semente apenas rotaciona surpresas e é reproduzível', (function () {
        var rows = [];
        var index;
        for (index = 0; index < 13; index += 1) {
            rows.push(candidate('match-' + index, 'Match ' + index, ['Drama'], 10 - index / 10));
        }
        rows.push(candidate('x', 'X', ['Comédia'], 9));
        rows.push(candidate('y', 'Y', ['Ficção'], 8));
        rows.push(candidate('z', 'Z', ['História'], 7));
        var first = build(rows, { favouriteGenres: ['drama'] }, {}, 1);
        var repeat = build(rows, { favouriteGenres: ['drama'] }, {}, 1);
        return first.map(function (row) { return row.id; }).join(',') ===
            repeat.map(function (row) { return row.id; }).join(',') && first[13].id === 'y';
    }()));

    process.stdout.write('Feedback imediato da sessão\n');
    check('guardar soma dois e pular subtrai um com gênero normalizado', (function () {
        var session = after({}, ['Ação'], 'KEPT');
        session = after(session, ['acao'], 'SKIPPED');
        return session.leaningByGenre.acao === 1;
    }()));

    check('o feedback é limitado entre menos seis e seis', (function () {
        var positive = {};
        var negative = {};
        var index;
        for (index = 0; index < 20; index += 1) {
            positive = after(positive, ['Drama'], 'KEPT');
            negative = after(negative, ['Comédia'], 'SKIPPED');
        }
        return positive.leaningByGenre.drama === 6 && negative.leaningByGenre.comedia === -6;
    }()));

    check('a inclinação mais forte da sessão fica entre menos um e um', (function () {
        var session = after(after({}, ['Ação'], 'KEPT'), ['Drama'], 'SKIPPED');
        return leaningFor(session, ['Ação']) === 1 && leaningFor(session, ['Drama']) === -0.5;
    }()));

    check('o feedback da sessão muda a próxima seleção imediatamente', (function () {
        var session = after({}, ['Comédia'], 'KEPT');
        var deck = build([
            candidate('drama', 'Drama', ['Drama'], 9), candidate('comedy', 'Comédia', ['Comédia'], 5)
        ], {}, session, 0);
        return deck[0].id === 'comedy';
    }()));

    check('construir e julgar não alteram os objetos de entrada', (function () {
        var taste = { favouriteGenres: ['Ação'], watchedGenres: [], seenIds: [] };
        var session = { leaningByGenre: { acao: 2 } };
        var original = JSON.stringify({ taste: taste, session: session });
        build([candidate('a', 'A', ['Ação'], 8)], taste, session, 0);
        after(session, ['Ação'], 'KEPT');
        return JSON.stringify({ taste: taste, session: session }) === original;
    }()));
}

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
