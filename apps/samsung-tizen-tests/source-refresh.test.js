/* Regression tests for transactional source refresh. Synthetic public fixtures only. */
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
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
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

function press(window, keyCode) {
    var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
}

async function run() {
    var window = loadApp();
    await waitFor(function () { return window.BuroApp.state.ready; }, 1000);

    process.stdout.write('Atualização M3U pela interface\n');
    var source = {
        id: 'source-refresh-ui', name: 'Lista atualizável', type: 'REMOTE_M3U',
        channelCount: 1, createdAt: Date.now(), updatedAt: null
    };
    var oldText = '#EXTM3U\n#EXTINF:-1 tvg-id="stable" group-title="Antiga",Canal estável\nhttps://public.test/stable.m3u8';
    var newText = '#EXTM3U\n#EXTINF:-1 tvg-id="stable" group-title="Nova",Canal estável\nhttps://public.test/stable-v2.m3u8\n' +
        '#EXTINF:-1 tvg-id="new" group-title="Nova",Canal novo\nhttps://public.test/new.m3u8';
    var oldParsed = window.BuroM3u.parse(oldText, source.id);
    var oldItem = window.BuroM3u.metadata(oldParsed)[0];
    oldItem.addedAt = 1000;
    var oldCategory = {
        id: oldItem.categoryId, sourceId: source.id, providerCategoryId: oldItem.categoryId,
        name: 'Antiga', contentType: oldItem.contentType, sortOrder: 0
    };
    var profile = { id: 'profile-refresh', name: 'Teste', avatarKey: 'gold', isKids: false, sourceId: source.id, createdAt: Date.now() };
    var favorite = { id: 'favorite-refresh-ui', profileId: profile.id, itemId: oldItem.id };
    var progress = { id: 'progress-refresh-ui', profileId: profile.id, itemId: oldItem.id, positionMs: 45000, durationMs: 120000 };
    await call(window, window.BuroStorage.put, ['sources', source]);
    await call(window, window.BuroStorage.put, ['categories', oldCategory]);
    await call(window, window.BuroStorage.put, ['items', oldItem]);
    await call(window, window.BuroStorage.put, ['favorites', favorite]);
    await call(window, window.BuroStorage.put, ['progress', progress]);
    await call(window, window.BuroStorage.secureSave, [source.id, { url: 'https://public.test/list.m3u' }]);
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [oldCategory];
    window.BuroApp.state.items = [oldItem];
    window.BuroApp.state.favorites = [favorite];
    window.BuroApp.state.progress = [progress];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SOURCES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    window.BuroApp._activate(window.document.querySelector('[data-action="source-manage"]'));
    check('gerenciamento oferece atualizar catálogo pelo D-pad',
        Boolean(window.document.querySelector('[data-action="source-refresh"]')));
    var originalNetworkText = window.BuroNetwork.text;
    window.BuroNetwork.text = function (options, success) {
        window.setTimeout(function () { success(newText); }, 5);
    };
    window.BuroApp._activate(window.document.querySelector('[data-action="source-refresh"]'));
    check('durante a atualização, ações destrutivas ficam desabilitadas',
        window.document.querySelector('[data-action="source-delete"]').disabled &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('refreshingSource')) >= 0);
    await waitFor(function () {
        return window.BuroApp.state.sources[0].channelCount === 2 &&
            window.BuroApp.state.screenData && window.BuroApp.state.screenData.refreshSuccess;
    }, 1000);
    window.BuroNetwork.text = originalNetworkText;
    var storedItems = await call(window, window.BuroStorage.all, ['items']);
    var storedCategories = await call(window, window.BuroStorage.all, ['categories']);
    var storedFavorites = await call(window, window.BuroStorage.all, ['favorites']);
    var storedProgress = await call(window, window.BuroStorage.all, ['progress']);
    check('nova fotografia substitui categoria antiga e contém os dois itens',
        storedItems.filter(function (row) { return row.sourceId === source.id; }).length === 2 &&
        storedCategories.some(function (row) { return row.sourceId === source.id && row.name === 'Nova'; }) &&
        !storedCategories.some(function (row) { return row.sourceId === source.id && row.name === 'Antiga'; }));
    check('identidade estável preserva favorito e progresso',
        storedFavorites.some(function (row) { return row.id === favorite.id; }) &&
        storedProgress.some(function (row) { return row.id === progress.id; }));
    check('atualização preserva a data de inclusão da identidade existente',
        storedItems.filter(function (row) { return row.id === oldItem.id; })[0].addedAt === 1000 &&
        storedItems.filter(function (row) { return row.id !== oldItem.id && row.sourceId === source.id; })[0].addedAt > 1000);
    check('URL de stream e segredo continuam fora do IndexedDB',
        JSON.stringify(storedItems).indexOf('public.test') === -1 &&
        JSON.stringify(storedItems).indexOf('list.m3u') === -1);

    process.stdout.write('Falha remota sem perda de catálogo\n');
    var snapshotBeforeFailure = JSON.stringify(storedItems);
    window.BuroNetwork.text = function (options, success, failure) { failure({ code: 'NETWORK_ERROR' }); };
    window.BuroApp._activate(window.document.querySelector('[data-action="source-refresh"]'));
    await waitFor(function () {
        return window.BuroApp.state.screenData && Boolean(window.BuroApp.state.screenData.refreshError);
    }, 1000);
    var itemsAfterFailure = await call(window, window.BuroStorage.all, ['items']);
    window.BuroNetwork.text = originalNetworkText;
    check('falha mantém a fotografia anterior inteira e mostra erro persistente',
        JSON.stringify(itemsAfterFailure) === snapshotBeforeFailure &&
        window.document.querySelector('.form-message.error'));

    process.stdout.write('Carregamento e recuperação do player\n');
    var languages = ['pt-BR', 'en', 'de', 'it', 'es'];
    var playbackTranslationsPresent = languages.every(function (language) {
        window.BuroI18n.setLanguage(language);
        return window.BuroI18n.t('playbackErrorTitle') !== 'playbackErrorTitle' &&
            window.BuroI18n.t('retryPlayback') !== 'retryPlayback' &&
            window.BuroI18n.t('playbackConnectionError') !== 'playbackConnectionError' &&
            window.BuroI18n.t('playbackSourceUnavailableError') !== 'playbackSourceUnavailableError' &&
            window.BuroI18n.t('playerAspectRatio') !== 'playerAspectRatio' &&
            window.BuroI18n.t('playerScaleOriginal') !== 'playerScaleOriginal' &&
            window.BuroI18n.t('playerScaleFill') !== 'playerScaleFill' &&
            window.BuroI18n.t('playerScaleAuto') !== 'playerScaleAuto' &&
            window.BuroI18n.t('preparingPlayback') !== 'preparingPlayback' &&
            window.BuroI18n.t('playingStatus') !== 'playingStatus' &&
            window.BuroI18n.t('watchedPercent') !== 'watchedPercent' &&
            window.BuroI18n.t('seriesCachedWarning') !== 'seriesCachedWarning' &&
            window.BuroI18n.t('refreshCategory') !== 'refreshCategory' &&
            window.BuroI18n.t('categoryCachedWarning') !== 'categoryCachedWarning';
    });
    window.BuroI18n.setLanguage('pt-BR');
    check('estado de erro do player existe nos cinco idiomas', playbackTranslationsPresent);
    var retryPrepareCount = 0;
    var forcedPrepareError = null;
    window.webapis = { avplay: {
        getState: function () { return 'READY'; }, open: function () {}, setListener: function () {},
        setDisplayRect: function () {}, stop: function () {}, close: function () {},
        prepareAsync: function (ok, fail) {
            retryPrepareCount += 1;
            if (retryPrepareCount === 1) { window.setTimeout(function () { fail({ name: 'NetworkError' }); }, 5); }
            else if (forcedPrepareError) { window.setTimeout(function () { fail(forcedPrepareError); }, 5); }
            else { ok(); }
        },
        play: function () {}, getDuration: function () { return 120000; }
    } };
    var playFixture = window.document.createElement('button');
    playFixture.setAttribute('data-action', 'play');
    playFixture.setAttribute('data-id', oldItem.id);
    window.BuroApp._activate(playFixture);
    check('player mostra carregamento central enquanto prepara o stream',
        !window.document.getElementById('player-waiting').hidden &&
        window.document.body.classList.contains('playing'));
    await waitFor(function () { return !window.document.getElementById('player-error-panel').hidden; }, 1000);
    check('falha permanece visível com causa e duas decisões',
        window.document.getElementById('player-error-message').textContent === window.BuroI18n.t('playbackConnectionError') &&
        window.document.querySelectorAll('[data-player-error-action]').length === 2);
    check('Retry recebe o foco inicial do controle remoto',
        window.document.getElementById('player-error-retry').classList.contains('focused'));
    press(window, 13);
    check('ENTER repete o mesmo item e retorna à reprodução',
        retryPrepareCount === 2 && window.document.getElementById('player-error-panel').hidden &&
        window.document.body.classList.contains('playing'));
    press(window, 10009);
    forcedPrepareError = { name: 'InvalidAccessError' };
    window.BuroApp._activate(playFixture);
    await waitFor(function () { return !window.document.getElementById('player-error-panel').hidden; }, 1000);
    check('fonte inexistente ou inacessível não é confundida com falha de conexão',
        window.document.getElementById('player-error-message').textContent ===
            window.BuroI18n.t('playbackSourceUnavailableError'));
    press(window, 10009);
    forcedPrepareError = null;

    process.stdout.write('Reconciliação de cache Xtream\n');
    var xtreamSource = { id: 'source-refresh-xtream', name: 'Xtream', type: 'XTREAM', channelCount: 0, createdAt: Date.now() };
    var keepCategory = { id: 'category-xtream-keep', sourceId: xtreamSource.id, name: 'Manter', contentType: 'MOVIE' };
    var removeCategory = { id: 'category-xtream-remove', sourceId: xtreamSource.id, name: 'Remover', contentType: 'MOVIE' };
    var keepItem = { id: 'movie:xtream-keep', sourceId: xtreamSource.id, categoryId: keepCategory.id, contentType: 'MOVIE', name: 'Cache válido' };
    var removeItem = { id: 'movie:xtream-remove', sourceId: xtreamSource.id, categoryId: removeCategory.id, contentType: 'MOVIE', name: 'Cache velho' };
    await call(window, window.BuroStorage.put, ['sources', xtreamSource]);
    await call(window, window.BuroStorage.put, ['categories', keepCategory]);
    await call(window, window.BuroStorage.put, ['categories', removeCategory]);
    await call(window, window.BuroStorage.put, ['items', keepItem]);
    await call(window, window.BuroStorage.put, ['items', removeItem]);
    var xtreamResult = await call(window, window.BuroStorage.replaceSourceCatalogue,
        [xtreamSource, [keepCategory], [], false]);
    var storedKeep = await call(window, window.BuroStorage.get, ['items', keepItem.id]);
    var storedRemove = await call(window, window.BuroStorage.get, ['items', removeItem.id]);
    check('Xtream preserva cache da categoria válida e remove apenas a extinta',
        storedKeep && !storedRemove && xtreamResult.removedItemIds.indexOf(removeItem.id) >= 0);

    process.stdout.write('Reconciliação transacional de episódios\n');
    var seriesCategoryId = 'series:refresh-episodes';
    var episodeKeep = { id: 'episode:refresh-keep', sourceId: xtreamSource.id, categoryId: seriesCategoryId, contentType: 'EPISODE', name: 'Episódio estável', addedAt: 1111 };
    var episodeRemove = { id: 'episode:refresh-remove', sourceId: xtreamSource.id, categoryId: seriesCategoryId, contentType: 'EPISODE', name: 'Episódio removido' };
    var episodeFavoriteKeep = { id: 'favorite-episode-keep', profileId: profile.id, itemId: episodeKeep.id };
    var episodeFavoriteRemove = { id: 'favorite-episode-remove', profileId: profile.id, itemId: episodeRemove.id };
    var episodeProgressKeep = { id: 'progress-episode-keep', profileId: profile.id, itemId: episodeKeep.id, positionMs: 30000, durationMs: 120000 };
    var episodeProgressRemove = { id: 'progress-episode-remove', profileId: profile.id, itemId: episodeRemove.id, positionMs: 20000, durationMs: 120000 };
    await call(window, window.BuroStorage.put, ['items', episodeKeep]);
    await call(window, window.BuroStorage.put, ['items', episodeRemove]);
    await call(window, window.BuroStorage.put, ['favorites', episodeFavoriteKeep]);
    await call(window, window.BuroStorage.put, ['favorites', episodeFavoriteRemove]);
    await call(window, window.BuroStorage.put, ['progress', episodeProgressKeep]);
    await call(window, window.BuroStorage.put, ['progress', episodeProgressRemove]);
    var episodeKeepUpdated = Object.assign({}, episodeKeep, { name: 'Episódio estável atualizado', addedAt: 9999 });
    var episodeNew = { id: 'episode:refresh-new', sourceId: xtreamSource.id, categoryId: seriesCategoryId, contentType: 'EPISODE', name: 'Episódio novo' };
    var episodeResult = await call(window, window.BuroStorage.replaceCategoryItems,
        [xtreamSource.id, seriesCategoryId, [episodeKeepUpdated, episodeNew]]);
    var episodeStoredKeep = await call(window, window.BuroStorage.get, ['items', episodeKeep.id]);
    var episodeStoredRemove = await call(window, window.BuroStorage.get, ['items', episodeRemove.id]);
    var episodeFavorites = await call(window, window.BuroStorage.all, ['favorites']);
    var episodeProgress = await call(window, window.BuroStorage.all, ['progress']);
    check('episódios entram como fotografia única e atualizam identidade existente',
        episodeStoredKeep.name === 'Episódio estável atualizado' && !episodeStoredRemove &&
        episodeResult.removedItemIds.indexOf(episodeRemove.id) >= 0);
    check('reconciliação de episódios também preserva a data de inclusão original',
        episodeStoredKeep.addedAt === 1111);
    check('reconciliação de episódios preserva referências válidas e limpa órfãs',
        episodeFavorites.some(function (row) { return row.id === episodeFavoriteKeep.id; }) &&
        !episodeFavorites.some(function (row) { return row.id === episodeFavoriteRemove.id; }) &&
        episodeProgress.some(function (row) { return row.id === episodeProgressKeep.id; }) &&
        !episodeProgress.some(function (row) { return row.id === episodeProgressRemove.id; }));

    window.close();
    if (failures.length) {
        process.stdout.write('\n' + failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte de atualização: ' + error.stack + '\n');
    process.exitCode = 1;
});
