/* Large-library parity tests. Synthetic fixtures only. */
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

function loadApp(factory, preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = factory || new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences || {
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () {
                return Object.keys(secureData).map(function (name) { return { name: name }; });
            },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) {
                if (!secureData[alias.name]) { throw { name: 'NotFoundError' }; }
                return secureData[alias.name];
            },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    return window;
}

function item(id, sourceId, categoryId, contentType, name) {
    return {
        id: id, sourceId: sourceId, categoryId: categoryId, contentType: contentType,
        name: name, sortOrder: 0, addedAt: 1000
    };
}

async function run() {
    var window = loadApp();
    var state;
    var source;
    var profile;
    var otherProfile;
    var favoriteItems = [];
    var favoriteRows = [];
    var progressItems = [];
    var progressRows = [];
    var index;
    var kind;
    var next;
    var parent;
    var episode;
    var progressBar;

    await waitFor(function () { return window.BuroApp.state.ready; }, 4000);
    state = window.BuroApp.state;
    source = { id: 'source-library-large', name: 'Biblioteca grande', type: 'REMOTE_M3U' };
    profile = { id: 'profile-library-large', name: 'Sala', avatarKey: 'gold', isKids: false, sourceId: source.id };
    otherProfile = { id: 'profile-library-other', name: 'Outro', avatarKey: 'moon', isKids: false, sourceId: source.id };
    state.sources = [source];
    state.profiles = [profile, otherProfile];
    state.activeSource = source;
    state.activeProfile = profile;
    state.categories = [
        { id: 'cat-library-live', sourceId: source.id, contentType: 'LIVE', name: 'Ao vivo' },
        { id: 'cat-library-movie', sourceId: source.id, contentType: 'MOVIE', name: 'Filmes' },
        { id: 'cat-library-series', sourceId: source.id, contentType: 'SERIES', name: 'Séries' }
    ];

    for (index = 0; index < 230; index += 1) {
        kind = ['MOVIE', 'SERIES', 'LIVE'][index % 3];
        favoriteItems.push(item(
            kind.toLowerCase() + ':favorite-' + index, source.id,
            kind === 'MOVIE' ? 'cat-library-movie' : (kind === 'SERIES' ? 'cat-library-series' : 'cat-library-live'),
            kind, 'Favorito ' + ('000' + index).slice(-3)
        ));
        favoriteRows.push({
            id: 'favorite-row-' + index, profileId: profile.id,
            itemId: favoriteItems[favoriteItems.length - 1].id, createdAt: index + 1
        });
    }
    favoriteRows.push({ id: 'favorite-other-profile', profileId: otherProfile.id, itemId: favoriteItems[0].id, createdAt: 99999 });
    favoriteRows.push({ id: 'favorite-orphan', profileId: profile.id, itemId: 'movie:removed', createdAt: 999999 });
    state.items = favoriteItems.slice();
    state.favorites = favoriteRows.slice();
    state.progress = [];
    state.screen = 'SHELL';
    state.section = 'MY_BURO';
    state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('Minha BURO em catálogo grande\n');
    check('favoritos limitam o DOM a 40 cards e o conjunto aos 200 do Android',
        window.document.querySelectorAll('.media-card').length === 40 &&
        window.document.querySelector('.library-pagination').textContent.indexOf('Página 1 de 5') >= 0 &&
        window.document.querySelector('.library-pagination').textContent.indexOf('/ 200') >= 0);
    check('favoritos seguem a inclusão mais recente, não a ordem acidental do catálogo',
        window.document.querySelector('.media-card h3').textContent === 'Favorito 229');
    check('favorito órfão não consome uma das 200 posições de conteúdo válido',
        window.document.querySelector('.library-pagination').textContent.indexOf('/ 200') >= 0 &&
        window.document.body.textContent.indexOf('movie:removed') === -1);
    check('filtro por tipo continua disponível antes da paginação',
        window.document.querySelectorAll('[data-action="library-filter"]').length === 4);

    window.BuroApp._activate(window.document.querySelector('[data-action="library-filter"][data-kind="SERIES"]'));
    check('mudar o filtro reinicia na primeira página e pagina somente o tipo escolhido',
        window.document.querySelector('.library-pagination').textContent.indexOf('Página 1 de 2') >= 0 &&
        Array.prototype.every.call(window.document.querySelectorAll('.media-card .badge'), function (badge) {
            return badge.textContent.indexOf('SERIES') >= 0;
        }));
    window.BuroApp._activate(window.document.querySelector('[data-action="library-filter"][data-kind="ALL"]'));
    for (index = 0; index < 4; index += 1) {
        next = window.document.querySelector('[data-action="library-page-next"]');
        window.BuroApp._activate(next);
    }
    check('a quinta página alcança o favorito 30 e não expõe os 30 mais antigos além do limite Android',
        window.document.body.textContent.indexOf('Favorito 030') >= 0 &&
        window.document.body.textContent.indexOf('Favorito 029') === -1 &&
        window.document.querySelectorAll('.media-card').length === 40);
    check('a última página devolve o foco ao botão Página anterior',
        !window.document.querySelector('[data-action="library-page-next"]') &&
        window.document.querySelector('[data-action="library-page-previous"]').classList.contains('focused'));

    state.favorites = state.favorites.filter(function (row) {
        var number = Number(String(row.id).replace('favorite-row-', ''));
        return row.profileId !== profile.id || number < 160;
    });
    window.BuroApp.render();
    check('reduzir a coleção limita a página antiga à nova última página válida',
        window.document.querySelector('.library-pagination').textContent.indexOf('Página 4 de 4') >= 0 &&
        window.document.querySelectorAll('.media-card').length === 40);

    parent = item('series:progress-parent', source.id, 'cat-library-series', 'SERIES', 'Série em andamento');
    episode = item('episode:progress-latest', source.id, parent.id, 'EPISODE', 'Episódio isolado');
    episode.locator = { season: 2, episode: 4 };
    progressItems.push(parent, episode);
    progressRows.push({
        id: 'progress-episode', profileId: profile.id, itemId: episode.id,
        positionMs: 60000, durationMs: 120000, completed: false, updatedAt: 10000
    });
    for (index = 0; index < 75; index += 1) {
        progressItems.push(item('movie:progress-' + index, source.id, 'cat-library-movie', 'MOVIE',
            (index >= 70 ? 'Concluído ' : 'Progresso ') + ('00' + index).slice(-2)));
        progressRows.push({
            id: 'progress-row-' + index, profileId: profile.id, itemId: 'movie:progress-' + index,
            positionMs: index >= 70 ? 120000 : 30000, durationMs: 120000,
            completed: index >= 70, updatedAt: index + 1000
        });
    }
    state.items = favoriteItems.concat(progressItems);
    state.progress = progressRows;
    state.section = 'CONTINUE_WATCHING';
    state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('Continuar e Histórico equivalentes ao Android\n');
    check('Continuar mantém exatamente os vinte registros incompletos mais recentes do Android',
        window.document.querySelectorAll('.media-card').length === 20 &&
        !window.document.querySelector('.library-pagination') &&
        window.document.body.textContent.indexOf('Concluído') === -1);
    check('progresso de episódio apresenta a série reconhecível e abre detalhes da série',
        window.document.querySelector('.media-card h3').textContent === 'Série em andamento' &&
        window.document.querySelector('.media-card').getAttribute('data-action') === 'series-details' &&
        window.document.body.textContent.indexOf('Episódio isolado') === -1);
    progressBar = window.document.querySelector('.media-card .media-progress i');
    check('a série usa o progresso real do episódio mais recente sem alterar a identidade persistida',
        progressBar && progressBar.getAttribute('style').indexOf('50.00%') >= 0 &&
        state.items.filter(function (row) { return row.id === parent.id; })[0]._libraryProgressItemId === undefined);

    state.section = 'HISTORY';
    state.screenData = null;
    window.BuroApp.render();
    check('Histórico aplica o limite Android de 60 e mostra 40 registros na primeira página',
        window.document.querySelectorAll('.media-card').length === 40 &&
        window.document.querySelector('.library-pagination').textContent.indexOf('Página 1 de 2') >= 0 &&
        window.document.querySelector('.library-pagination').textContent.indexOf('/ 60') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="library-page-next"]'));
    check('a segunda página do Histórico mostra somente os vinte registros restantes',
        window.document.querySelectorAll('.media-card').length === 20 &&
        window.document.querySelector('[data-action="library-page-previous"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="library-filter"][data-kind="SERIES"]'));
    check('filtro do Histórico reinicia a página e conserva a série derivada do episódio',
        window.document.querySelectorAll('.media-card').length === 1 &&
        window.document.querySelector('.media-card h3').textContent === 'Série em andamento' &&
        !window.document.querySelector('.library-pagination'));
    window.close();

    process.stdout.write('Hidratação do pai da série no boot\n');
    var factory = new fakeIndexedDb.IDBFactory();
    var seedWindow = loadApp(factory);
    var seedProfile = { id: 'profile-parent-hydration', name: 'Boot', avatarKey: 'gold', isKids: false, sourceId: null };
    var seedEpisode = item('episode:000-parent-hydration', 'source-seed', 'series:zz-parent-hydration', 'EPISODE', 'Episódio salvo');
    var seedParent = item('series:zz-parent-hydration', 'source-seed', 'cat-seed', 'SERIES', 'Série salva');
    var writes = [];
    await waitFor(function () { return seedWindow.BuroApp.state.ready; }, 4000);
    writes.push(call(seedWindow, seedWindow.BuroStorage.put, ['profiles', seedProfile]));
    writes.push(call(seedWindow, seedWindow.BuroStorage.put, ['items', seedEpisode]));
    writes.push(call(seedWindow, seedWindow.BuroStorage.put, ['items', seedParent]));
    writes.push(call(seedWindow, seedWindow.BuroStorage.put, ['progress', {
        id: 'progress-parent-hydration', profileId: seedProfile.id, itemId: seedEpisode.id,
        positionMs: 30000, durationMs: 90000, completed: false, updatedAt: 5000
    }]));
    for (index = 0; index < 121; index += 1) {
        writes.push(call(seedWindow, seedWindow.BuroStorage.put, ['items', item(
            'movie:' + ('000' + index).slice(-3) + '-seed', 'source-seed', 'cat-seed', 'MOVIE', 'Amostra ' + index
        )]));
    }
    await Promise.all(writes);
    seedWindow.close();
    var reloaded = loadApp(factory, {
        language: 'pt-BR', languageSelected: true, acceptedLegal: true,
        activeProfileId: seedProfile.id, section: 'CONTINUE_WATCHING'
    });
    await waitFor(function () { return reloaded.BuroApp.state.ready; }, 4000);
    check('boot busca o pai da série quando apenas o episódio cabia na amostra inicial',
        reloaded.BuroApp.state.items.some(function (row) { return row.id === seedEpisode.id; }) &&
        reloaded.BuroApp.state.items.some(function (row) { return row.id === seedParent.id; }));
    reloaded.close();

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
