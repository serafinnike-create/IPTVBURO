/* Full-catalogue seasonal rail integration. Synthetic catalogue only. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');
var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function waitFor(predicate, timeoutMs) {
    var started = Date.now();
    return new Promise(function (resolve, reject) {
        function poll() {
            if (predicate()) { resolve(); return; }
            if (Date.now() - started > timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function call(window, method, args) {
    return new Promise(function (resolve, reject) {
        method.apply(window.BuroStorage, args.concat([resolve, reject]));
    });
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match;
    var secureData = {};
    var dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/' });
    var window = dom.window;
    while ((match = pattern.exec(html)) !== null) { scripts.push(match[1]); }
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return Object.keys(secureData).map(function (name) { return { name: name }; }); },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) { return secureData[alias.name]; },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    scripts.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.init();
    return window;
}

async function run() {
    var window = loadApp();
    var source = { id: 'source-seasonal-app', name: 'Catálogo sazonal', type: 'REMOTE_M3U',
        channelCount: 32, createdAt: Date.now(), updatedAt: null };
    var category = { id: 'category-seasonal-movies', sourceId: source.id, providerCategoryId: 'movies',
        name: 'Filmes', contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'profile-seasonal-app', name: 'Teste', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    var items = [];
    var christmas;
    var rail;
    var seasonalCard;
    var headings;
    var index;

    /*
      Esperar a tela assentar, e nao so state.ready.

      finishInitialization marca ready e so entao chama completeReveal, que
      escreve state.screen dentro de um setTimeout — a permanencia minima da
      abertura. Entre um e outro o app se diz pronto e ainda vai trocar de tela,
      e o estado montado nessa janela e sobrescrito.

      Os 4s tambem eram curtos: sob a carga da suite completa esta suite falhou
      duas vezes, e passou sempre que rodou isolada.
    */
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    for (index = 0; index < 31; index += 1) {
        items.push(window.BuroDomain.createItem({
            sourceId: source.id, providerItemId: 'ordinary-' + index,
            name: 'Filme comum ' + ('0' + index).slice(-2), categoryId: category.id,
            contentType: 'MOVIE', sortOrder: index, year: 2020, rating: 5
        }));
    }
    items.push(window.BuroDomain.createItem({
        sourceId: source.id, providerItemId: 'grinch-deep', name: 'O Grinch — Especial de Natal',
        categoryId: category.id, contentType: 'MOVIE', sortOrder: 99, year: 2000, rating: 5
    }));
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], items, true]);
    christmas = window.BuroSeasonal.primaryCollectionFor(new window.Date(2026, 11, 10, 12, 0, 0));
    window.BuroSeasonal.primaryCollectionFor = function () { return christmas; };
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    /* A amostra inicial também não contém o título: só a varredura integral pode achá-lo. */
    window.BuroApp.state.items = items.slice(0, 10);
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            window.BuroApp.state.screenData.loading === false;
    }, 5000);

    rail = window.document.querySelector('[data-home-rail="seasonal-christmas"]');
    seasonalCard = rail && rail.querySelector('[data-id="' + items[31].id + '"]');
    check('a varredura encontra título sazonal depois dos 24 itens editoriais', Boolean(seasonalCard));
    check('a fileira usa título localizado e selo explicativo',
        rail && rail.textContent.indexOf('Especial de Natal') >= 0 &&
        rail.textContent.indexOf(window.BuroI18n.t('seasonalBadge')) >= 0);
    headings = Array.prototype.slice.call(window.document.querySelectorAll('[data-home-rail]')).map(function (row) {
        return row.getAttribute('data-home-rail');
    });
    check('a coleção fica antes das fileiras editoriais comuns',
        headings[0] === 'seasonal-christmas' && headings.length > 1);
    check('o card sazonal continua sendo uma ação normal alcançável pelo D-pad',
        seasonalCard && seasonalCard.classList.contains('focusable') && seasonalCard.getAttribute('data-action') === 'movie-details');
    /*
      A contagem sai para o ecrã porque este teste já falhou duas vezes na suíte
      inteira e passou sempre sozinho, mesmo com os nove ficheiros anteriores
      corridos à frente. Sem o número, uma falha dessas não diz se a Home
      materializou tudo ou se nem chegou a desenhar.
    */
    var cardCount = window.document.querySelectorAll('.media-card').length;
    check('a Home continua limitada e não materializa os 32 títulos no DOM (' + cardCount + '/' + items.length + ')',
        cardCount > 0 && cardCount < items.length);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
        process.exitCode = 1; return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte sazonal do app: ' + error.stack + '\n');
    process.exitCode = 1;
});
