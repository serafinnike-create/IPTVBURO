/*
  Lembretes dentro do aplicativo de verdade.

  `reminders.test.js` cobre a política sozinha. Aqui o app inteiro sobe num DOM,
  com IndexedDB falso, e o percurso é o que uma pessoa faz: abrir um filme,
  marcar, ver o trilho na Home, abrir a página e desmarcar.

  O que este arquivo protege e o outro não consegue: que a marca sobreviva a um
  reinício (é o ponto inteiro de um lembrete), que dois perfis não vejam a marca
  um do outro, e que nada da fonte — URL, credencial — entre no que é gravado.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
/* A ordem vem do index.html, para a suíte não quebrar quando um módulo novo
   entra no app. Ver platform-failures.test.js. */
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

function section(title) { process.stdout.write(title + '\n'); }

function waitFor(predicate, timeout) {
    var deadline = Date.now() + (timeout || 2000);
    return new Promise(function (resolve, reject) {
        (function attempt() {
            var value;
            try { value = predicate(); } catch (error) { value = null; }
            if (value) { resolve(value); return; }
            if (Date.now() > deadline) { reject(new Error('timeout')); return; }
            setTimeout(attempt, 10);
        }());
    });
}

/* Um app carregado do zero, como numa TV que acabou de ligar. O IndexedDB é
   passado de fora para que um segundo boot reencontre o que o primeiro gravou. */
function bootApp(factory, storedPreferences) {
    var dom = new JSDOM(fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8'), {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.indexedDB = factory;
    window.localStorage.clear();
    if (storedPreferences) {
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(storedPreferences));
    }
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    return window;
}

function press(window, keyCode) {
    window.BuroApp._onKeyDown({ keyCode: keyCode, preventDefault: function () {} });
}

/* Semeia catálogo e perfil direto no banco: o caminho de onboarding já é
   coberto por dpad-navigation, e repeti-lo aqui só tornaria o teste frágil. */
function seed(factory) {
    return new Promise(function (resolve, reject) {
        var request = factory.open('iptvburo.catalog', 3);
        request.onupgradeneeded = function (event) {
            var database = event.target.result;
            var store;
            database.createObjectStore('profiles', { keyPath: 'id' });
            database.createObjectStore('sources', { keyPath: 'id' });
            store = database.createObjectStore('categories', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('bySourceType', ['sourceId', 'contentType'], { unique: false });
            store = database.createObjectStore('items', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('byCategory', ['sourceId', 'categoryId'], { unique: false });
            store.createIndex('byType', ['sourceId', 'contentType'], { unique: false });
            store.createIndex('bySearchOrder', ['searchRank', 'searchSort', 'id'], { unique: false });
            store = database.createObjectStore('favorites', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
            store = database.createObjectStore('progress', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
            store = database.createObjectStore('reminders', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
        };
        request.onsuccess = function (event) {
            var database = event.target.result;
            var transaction = database.transaction(
                ['profiles', 'sources', 'categories', 'items'], 'readwrite'
            );
            transaction.objectStore('profiles').put({
                id: 'p1', name: 'Casa', avatarKey: 'gold', isKids: false,
                sourceId: 's1', createdAt: 1
            });
            transaction.objectStore('profiles').put({
                id: 'p2', name: 'Outro', avatarKey: 'gold', isKids: false,
                sourceId: 's1', createdAt: 2
            });
            transaction.objectStore('sources').put({
                id: 's1', name: 'Fonte', type: 'REMOTE_M3U', createdAt: 1
            });
            transaction.objectStore('categories').put({
                id: 'c1', sourceId: 's1', name: 'Filmes', contentType: 'MOVIE', sortOrder: 0
            });
            transaction.objectStore('items').put({
                id: 'i1', sourceId: 's1', categoryId: 'c1', contentType: 'MOVIE',
                name: 'Filme Marcado', providerItemId: '10', sortOrder: 0,
                searchName: 'filme marcado', searchRank: 0, searchSort: 0,
                locator: { providerItemId: '10' }
            });
            transaction.oncomplete = function () { database.close(); resolve(); };
            transaction.onerror = function () { reject(transaction.error); };
        };
        request.onerror = function () { reject(request.error); };
    });
}

/* Idioma escolhido e aviso legal aceitos: os dois gates iniciais já têm cobertura
   própria, e passar por eles aqui só atrasaria o que este arquivo testa. */
var PREFERENCES = {
    acceptedLegal: true, languageSelected: true, language: 'pt-BR',
    activeProfileId: 'p1', section: 'HOME',
    hiddenCategoryIds: [], lockedCategoryIds: []
};

(async function run() {
    /* Um único factory para os dois boots: é ele que faz o segundo app
       reencontrar o que o primeiro gravou, como o disco de uma TV. */
    var factory = new fakeIndexedDb.IDBFactory();
    var window;
    var reminderRow;
    var second;

    await seed(factory);

    window = bootApp(factory, PREFERENCES);
    window.BuroApp.init();
    await waitFor(function () { return window.document.querySelector('.shell'); }, 4000);

    section('Marcar um título');

    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.render();
    window.BuroApp._activate(window.document.querySelector('[data-action="category"][data-id="c1"]'));
    await waitFor(function () {
        return window.document.querySelector('[data-action="movie-details"][data-id="i1"]');
    }, 2000);
    window.BuroApp._activate(window.document.querySelector('[data-action="movie-details"][data-id="i1"]'));
    await waitFor(function () {
        return window.document.querySelector('[data-action="reminder"][data-id="i1"]');
    }, 2000);

    check('o detalhe do filme oferece a ação de lembrete',
        Boolean(window.document.querySelector('[data-action="reminder"][data-id="i1"]')));
    check('a ação anuncia o estado para leitores de tela',
        window.document.querySelector('[data-action="reminder"][data-id="i1"]')
            .getAttribute('aria-pressed') === 'false');

    /*
      A linha de ações segue a BuroActionBar do Android: Assistir conserva o
      rótulo, as secundárias viram glifo com legenda. Aqui isso não é só estética
      — as seis pílulas rotuladas somavam mais que a largura útil do hero, e
      `.action-row` não quebra linha nem rola, então as últimas ficavam
      inalcançáveis.
    */
    check('Assistir conserva o botão rotulado',
        Boolean(window.document.querySelector('.detail-actions [data-action="play"]')));
    check('as ações secundárias formam uma barra de glifos',
        window.document.querySelectorAll('.detail-action-bar .action-glyph').length >= 3);
    /* A legenda é decorativa: o nome do controle vive no aria-label, senão o
       leitor de tela anunciaria a mesma palavra duas vezes. */
    check('cada glifo carrega o próprio nome acessível',
        (function () {
            var glyph = window.document.querySelector('[data-action="reminder"][data-id="i1"]');
            var caption = glyph.querySelector('.action-glyph-label');
            return glyph.getAttribute('aria-label') &&
                caption.getAttribute('aria-hidden') === 'true';
        }()));
    /* Rótulo curto: "Adicionar à Minha BURO" foi escrito para a pílula larga e
       numa coluna de 128 px quebrava em duas linhas, desalinhando as legendas
       vizinhas. Só apareceu ao ver a tela renderizada. */
    check('a legenda de favoritar é curta o bastante para uma linha',
        window.document.querySelector('[data-action="favorite"] .action-glyph-label')
            .textContent.trim() === 'Favoritar');
    check('a ordem segue a referência Android',
        (function () {
            var order = Array.prototype.slice
                .call(window.document.querySelectorAll('.detail-action-bar [data-action]'))
                .map(function (node) { return node.getAttribute('data-action'); });
            return order[0] === 'favorite' && order[1] === 'reminder' &&
                order[order.length - 1] === 'share';
        }()));

    window.BuroApp._activate(window.document.querySelector('[data-action="reminder"][data-id="i1"]'));
    await waitFor(function () { return window.BuroApp.state.reminders.length === 1; }, 2000);

    reminderRow = window.BuroApp.state.reminders[0];
    check('marcar grava um lembrete para o perfil ativo', reminderRow.profileId === 'p1');
    check('o lembrete é guardado por identidade, não pelo id da linha',
        reminderRow.identity.indexOf('i1') === -1);
    check('nada da fonte entra no registro guardado',
        !/http|password|username|token/i.test(JSON.stringify(reminderRow)));

    await waitFor(function () {
        return window.document.querySelector('[data-action="reminder"][data-id="i1"]')
            .getAttribute('aria-pressed') === 'true';
    }, 2000);
    check('a ação passa a anunciar que o título está marcado', true);

    section('Onde a marca aparece');

    window.BuroApp.state.section = 'REMINDERS';
    window.BuroApp.render();
    check('a Ribbon tem um destino de Lembretes',
        Boolean(window.document.querySelector('.nav-list [data-section="REMINDERS"]')));
    check('a página lista o título marcado',
        window.document.body.textContent.indexOf('Filme Marcado') !== -1);
    /*
      A referência Android é uma lista vertical com Remover em cada linha, não
      uma grade de pôsteres — a grade fica na Home, onde o trilho serve para
      navegar. Estas duas asserções existem porque a primeira versão deste porte
      usava a grade nos dois lugares.
    */
    check('a página usa linhas verticais, como a referência Android',
        Boolean(window.document.querySelector('.reminder-list .reminder-row')) &&
        !window.document.querySelector('.reminder-list .media-card'));
    check('cada linha oferece Remover com rótulo acessível próprio',
        Boolean(window.document.querySelector('.reminder-row [data-action="reminder-remove"]')) &&
        window.document.querySelector('.reminder-row [data-action="reminder-remove"]')
            .getAttribute('aria-label').indexOf('Filme Marcado') !== -1);
    /* Sem pôster guardável — o caso comum, porque a arte com credencial é
       descartada — a inicial do título ocupa o lugar. Um símbolo fixo deixaria
       todas as linhas idênticas. */
    check('sem arte, a linha mostra a inicial do título em vez de um vazio',
        window.document.querySelector('.reminder-row-art.reminder-art-initial') &&
        window.document.querySelector('.reminder-row-art.reminder-art-initial')
            .textContent.trim() === 'F');
    /* A TV não notifica com o app fechado: a página precisa dizer isso em vez de
       oferecer um horário que nunca dispara. */
    check('a página explica que o aviso acontece ao abrir o aplicativo',
        Boolean(window.document.querySelector('.reminders-notice-hint')));
    check('a página não oferece horário de notificação',
        !/data-action="reminder-time"/.test(window.document.body.innerHTML));

    window.BuroApp.state.section = 'HOME';
    window.BuroApp.render();
    await waitFor(function () {
        return window.document.querySelector('[data-home-rail="reminders"]');
    }, 3000);
    check('a Home ganha o trilho de lembretes',
        Boolean(window.document.querySelector('[data-home-rail="reminders"]')));
    /*
      Pressionar um card leva à página de Lembretes, como no Android: o trilho
      existe para mostrar o que está por vir, e um título que ainda não saiu não
      tem o que reproduzir. A primeira versão deste porte fazia o card remover a
      marca, então um toque acidental apagava o lembrete sem confirmação.
    */
    check('um card do trilho leva à página, não remove a marca',
        (function () {
            var card = window.document.querySelector('[data-home-rail="reminders"] .reminder-card');
            return card && card.getAttribute('data-action') === 'section' &&
                card.getAttribute('data-section') === 'REMINDERS';
        }()));
    /* O selo é a mesma palavra em todo card, como no Android; o que varia por
       título é a linha de baixo. */
    check('o selo do card é o rótulo fixo de lembrete',
        window.document.querySelector('[data-home-rail="reminders"] .badge')
            .textContent.trim() === 'LEMBRETE');

    section('A marca sobrevive a um reinício');

    second = bootApp(factory, PREFERENCES);
    second.BuroApp.init();
    await waitFor(function () { return second.document.querySelector('.shell'); }, 4000);
    check('o lembrete continua lá depois de reiniciar',
        second.BuroApp.state.reminders.length === 1 &&
        second.BuroApp.state.reminders[0].identity === reminderRow.identity);

    section('Um perfil não vê a marca do outro');

    second.BuroApp.state.preferences.activeProfileId = 'p2';
    second.BuroApp.state.activeProfile = second.BuroApp.state.profiles.filter(function (row) {
        return row.id === 'p2';
    })[0];
    second.BuroApp.state.section = 'REMINDERS';
    second.BuroApp.render();
    check('o outro perfil abre a página vazia',
        second.document.body.textContent.indexOf('Filme Marcado') === -1);

    section('Desmarcar');

    second.BuroApp.state.preferences.activeProfileId = 'p1';
    second.BuroApp.state.activeProfile = second.BuroApp.state.profiles.filter(function (row) {
        return row.id === 'p1';
    })[0];
    second.BuroApp.state.section = 'MOVIES';
    second.BuroApp.render();
    second.BuroApp._activate(second.document.querySelector('[data-action="category"][data-id="c1"]'));
    await waitFor(function () {
        return second.document.querySelector('[data-action="movie-details"][data-id="i1"]');
    }, 2000);
    second.BuroApp._activate(second.document.querySelector('[data-action="movie-details"][data-id="i1"]'));
    await waitFor(function () {
        return second.document.querySelector('[data-action="reminder"][data-id="i1"]');
    }, 2000);
    second.BuroApp._activate(second.document.querySelector('[data-action="reminder"][data-id="i1"]'));
    await waitFor(function () { return second.BuroApp.state.reminders.length === 0; }, 2000);
    check('desmarcar remove o lembrete', second.BuroApp.state.reminders.length === 0);

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falharam, ' + passed + ' aprovados\n');
        process.exit(1);
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}()).catch(function (error) {
    process.stdout.write('ERRO ' + (error && error.stack || error) + '\n');
    process.exit(1);
});
