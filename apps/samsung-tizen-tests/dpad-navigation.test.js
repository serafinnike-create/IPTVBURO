/* Contract tests for the Samsung Web application. No private provider data. */
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
            if (Date.now() - started > timeoutMs) { reject(new Error('timeout: ' + predicate.toString())); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function loadApp(preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    var launchedAppControls = [];
    var runtimeReadyMessages = [];
    window.console.info = function () {
        runtimeReadyMessages.push(Array.prototype.join.call(arguments, ' '));
    };
    window.__scrollIntoViewCalls = [];
    window.HTMLElement.prototype.scrollIntoView = function (options) {
        window.__scrollIntoViewCalls.push({
            section: this.getAttribute('data-section') || '',
            action: this.getAttribute('data-action') || '',
            inline: options && options.inline
        });
    };
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    if (preferences) {
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences));
    }
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
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
        application: {
            getCurrentApplication: function () { return { exit: function () {} }; },
            launchAppControl: function (control, id, success) { launchedAppControls.push(control); if (success) { success(); } }
        }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();

    /* A primeira etapa do boot é síncrona e pode ser verificada sem temporização. */
    window.__bootFirstFrame = (function () {
        var message = window.document.querySelector('.boot-message');
        var progress = window.document.querySelector('.boot-progress');
        var panel = window.document.querySelector('.boot-panel');
        return {
            present: Boolean(window.document.querySelector('.boot-screen')),
            cinematic: Boolean(window.document.querySelector('.boot-backdrop')),
            panel: Boolean(window.document.querySelector('.boot-panel')),
            mark: Boolean(window.document.querySelector('.boot-mark')),
            spinner: Boolean(window.document.querySelector('.boot-indicator')),
            dots: window.document.querySelectorAll('.boot-dot').length,
            message: message ? message.textContent : '',
            live: panel && panel.getAttribute('role') === 'status' &&
                panel.getAttribute('aria-live') === 'polite' && panel.getAttribute('aria-atomic') === 'true',
            progress: progress ? {
                role: progress.getAttribute('role'),
                now: progress.getAttribute('aria-valuenow'),
                min: progress.getAttribute('aria-valuemin'),
                max: progress.getAttribute('aria-valuemax')
            } : null
        };
    }());

    window.__secureData = secureData;
    window.__launchedAppControls = launchedAppControls;
    window.__runtimeReadyMessages = runtimeReadyMessages;
    return window;
}

function press(window, keyCode) {
    var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
    event = new window.KeyboardEvent('keyup', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
}

function hold(window, keyCode, durationMs) {
    var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
    window.document.dispatchEvent(event);
    return new Promise(function (resolve) {
        setTimeout(function () {
            event = new window.KeyboardEvent('keyup', { bubbles: true, cancelable: true });
            Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
            window.document.dispatchEvent(event);
            resolve();
        }, durationMs);
    });
}

async function run() {
    var languageWindow = loadApp();
    var window;
    var parsed;
    var localDump;
    var closeCount = 0;
    var seekForward = 0;
    var seekBackward = 0;
    var selectedTracks = [];
    var selectedSpeeds = [];
    var selectedDisplayModes = [];
    var silentSubtitleValues = [];
    var downloadRequests = [];
    var initialSeek = 0;
    var openedPlaybackUrl = null;
    var playerPauseCount = 0;
    var playerPlayCount = 0;
    var originalNetworkJson;
    var movieDetails;
    var seriesDetails;
    var liveSchedule;
    var bootFrame;
    var hydratedFavoriteItem;
    var avListener;

    process.stdout.write('Seleção inicial de idioma\n');
    check('a primeira execução começa no seletor de idioma',
        Boolean(languageWindow.document.querySelector('.language-screen')));
    check('os cinco idiomas aparecem em lista vertical',
        languageWindow.document.querySelectorAll('[data-action="select-language"]').length === 5);
    check('o foco semântico acompanha o foco visual desde o seletor inicial',
        languageWindow.document.activeElement === languageWindow.document.querySelector('.focusable.focused') &&
        languageWindow.document.activeElement.getAttribute('tabindex') === '0');
    press(languageWindow, 13);
    check('a escolha do idioma inicia o boot',
        Boolean(languageWindow.document.querySelector('.boot-screen')));
    languageWindow.close();

    window = loadApp({ language: 'pt-BR', languageSelected: true });

    process.stdout.write('Contratos de domínio e fontes\n');
    check('identidade é estável',
        window.BuroDomain.contentIdentity({ contentType: 'MOVIE', providerItemId: '42', name: 'A' }) === 'movie:42');
    parsed = window.BuroM3u.parse('#EXTM3U\n#EXTINF:-1 group-title="News" tvg-logo="https://images.public.test/channel.png?sig=synthetic",Public Channel\nhttps://example.test/live.m3u8', 'source-public');
    check('parser M3U cria item', parsed.entries.length === 1 && parsed.entries[0].item.name === 'Public Channel');
    check('URL de stream não entra no metadado persistível',
        JSON.stringify(window.BuroM3u.metadata(parsed)).indexOf('example.test') === -1);
    check('artwork M3U fica no resultado transitório e não no metadado persistível',
        parsed.entries[0].artworkUrl.indexOf('images.public.test') >= 0 &&
        JSON.stringify(window.BuroM3u.metadata(parsed)).indexOf('images.public.test') === -1);
    check('item M3U recebe ordem e data local para compor a Home sem mudar sua identidade',
        parsed.entries[0].item.sortOrder === 0 && Number(parsed.entries[0].item.addedAt) > 0 &&
        window.BuroDomain.contentIdentity(parsed.entries[0].item) === 'live:0');
    check('Xtream normaliza endpoint',
        window.BuroXtream.normalizeServer('https://provider.test/base/player_api.php?x=1') === 'https://provider.test/base');
    check('Xtream resolve episódio somente no momento do play',
        window.BuroXtream.resolvePlayback(
            { server: 'https://provider.test', username: 'u', password: 'p' },
            { kind: 'xtream', contentType: 'EPISODE', providerItemId: '9', extension: 'mkv' }
        ) === 'https://provider.test/series/u/p/9.mkv');
    originalNetworkJson = window.BuroNetwork.json;
    var timestampedXtreamItem;
    window.BuroNetwork.json = function (options, success) {
        success([{ stream_id: 11, name: 'Filme datado', added: '1700000000', year: '2024', rating: '8.4' }]);
    };
    window.BuroXtream.loadItems(
        { server: 'https://provider.test', username: 'u', password: 'p' }, 'source-public', 'MOVIE',
        { id: 'category-public', providerCategoryId: '1' },
        function (items) { timestampedXtreamItem = items[0]; }, function () {}
    );
    window.BuroNetwork.json = originalNetworkJson;
    check('Xtream converte a data do provedor para milissegundos e preserva a ordem da categoria',
        timestampedXtreamItem.addedAt === 1700000000000 && timestampedXtreamItem.sortOrder === 0);
    check('proteção reconhece categoria adulta por palavra inteira',
        window.BuroGuard.looksAdult('Filmes | Adultos') && !window.BuroGuard.looksAdult('Drama familiar'));
    check('perfil Kids remove categoria adulta sem depender do PIN',
        !window.BuroGuard.categoryVisible({ id: 'adult', name: 'XXX' }, { hiddenCategoryIds: [] }, true));
    check('PIN exige exatamente quatro números',
        window.BuroGuard.validPin('1234') && !window.BuroGuard.validPin('12345') && !window.BuroGuard.validPin('12a4'));
    check('retomada usa os mesmos limites do domínio Android',
        window.BuroDomain.resumeDecision({ positionMs: 30000, durationMs: 120000, completed: false }, true).kind === 'resume' &&
        window.BuroDomain.resumeDecision({ positionMs: 29000, durationMs: 120000, completed: false }, true).kind === 'start' &&
        window.BuroDomain.resumeDecision({ positionMs: 60000, durationMs: 120000, completed: false }, false).kind === 'start' &&
        window.BuroDomain.playbackCompleted(540000, 600000));
    (function () {
        var items = [
            { id: 'z', name: 'Zeta', genre: 'Drama / Ação', year: 2020, rating: 7.1 },
            { id: 'a', name: 'Alpha', genre: 'Ação', year: 2024, rating: 8.8 },
            { id: 'n', name: 'Sem dados', genre: null, year: null, rating: null }
        ];
        var filtered = window.BuroDomain.applyCatalogueFilter(items, { genre: 'ação', year: null, sort: 'rating-desc' });
        check('filtros de catálogo compartilham gênero, ano e ordenação determinística',
            window.BuroDomain.availableGenres(items).length === 2 &&
            window.BuroDomain.availableYears(items)[0] === 2024 &&
            filtered.length === 2 && filtered[0].id === 'a');
    }());
    originalNetworkJson = window.BuroNetwork.json;
    window.BuroNetwork.json = function (options, success) {
        success({ info: {
            name: 'Synthetic Movie', plot: 'Authorized fixture', genre: 'Drama', rating: '8.2',
            youtube_trailer: 'https://www.youtube.com/watch?v=AbCdEf12345'
        } });
    };
    window.BuroXtream.loadMovieDetails(
        { server: 'https://provider.test', username: 'u', password: 'p' },
        { name: 'Fallback', locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '4' } },
        function (details) { movieDetails = details; }, function () {}
    );
    window.BuroNetwork.json = originalNetworkJson;
    check('detalhes Xtream são normalizados sem persistir URL privada',
        movieDetails.title === 'Synthetic Movie' && movieDetails.rating === 8.2 &&
        movieDetails.youtubeTrailerId === 'AbCdEf12345' &&
        JSON.stringify(movieDetails).indexOf('provider.test') === -1);
    window.BuroNetwork.json = function (options, success) {
        success({ info: { name: 'Synthetic Series', youtube_trailer: 'https://youtu.be/ZyXwVu98765' } });
    };
    window.BuroXtream.loadSeriesDetails(
        { server: 'https://provider.test', username: 'u', password: 'p' },
        { name: 'Fallback', locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: '5' } },
        function (details) { seriesDetails = details; }, function () {}
    );
    window.BuroNetwork.json = originalNetworkJson;
    check('detalhes de série usam o mesmo saneamento estrito de trailer',
        seriesDetails.title === 'Synthetic Series' && seriesDetails.youtubeTrailerId === 'ZyXwVu98765');
    var heroMovieDetails;
    var heroSeriesDetails;
    var heroNetworkController = { abort: function () {} };
    window.BuroNetwork.json = function (options, success) {
        if (options.url.indexOf('get_vod_info') >= 0) {
            success({ info: {
                plot: 'Filme autorizado', genre: 'Drama', duration: '01:42:00', rating: '8.6',
                movie_image: 'https://images.public.test/movie.jpg',
                backdrop_path: ['https://images.public.test/movie-backdrop.jpg']
            } });
        } else {
            success({ info: {
                plot: 'Série autorizada', genre: 'Aventura', episode_run_time: '48 min', rating: '9.1',
                cover: 'https://images.public.test/series.jpg',
                backdrop_path: 'https://images.public.test/series-backdrop.jpg'
            } });
        }
        return heroNetworkController;
    };
    var returnedMovieController = window.BuroXtream.loadHeroDetails(
        { server: 'https://provider.test', username: 'u', password: 'p' },
        { contentType: 'MOVIE', name: 'Filme', locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '8' } },
        function (details) { heroMovieDetails = details; }, function () {}
    );
    var returnedSeriesController = window.BuroXtream.loadHeroDetails(
        { server: 'https://provider.test', username: 'u', password: 'p' },
        { contentType: 'SERIES', name: 'Série', locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: '9' } },
        function (details) { heroSeriesDetails = details; }, function () {}
    );
    window.BuroNetwork.json = originalNetworkJson;
    check('Hero Xtream mapeia filme e série, aceita backdrops escalar ou lista e preserva cancelamento',
        heroMovieDetails.synopsis === 'Filme autorizado' && heroMovieDetails.duration === '01:42:00' &&
        heroMovieDetails.backdropUrl.indexOf('movie-backdrop.jpg') >= 0 &&
        heroSeriesDetails.synopsis === 'Série autorizada' && heroSeriesDetails.duration === '48 min' &&
        heroSeriesDetails.backdropUrl.indexOf('series-backdrop.jpg') >= 0 &&
        returnedMovieController === heroNetworkController && returnedSeriesController === heroNetworkController);
    window.BuroNetwork.json = function (options, success) {
        success({ epg_listings: [{ title: window.btoa('News'), description: window.btoa('Public fixture'), start_timestamp: '100', stop_timestamp: '200' }] });
    };
    window.BuroXtream.loadLiveEpg(
        { server: 'https://provider.test', username: 'u', password: 'p' },
        { locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: '7' } },
        function (schedule) { liveSchedule = schedule; }, function () {}
    );
    window.BuroNetwork.json = originalNetworkJson;
    check('EPG Xtream decodifica título e mantém somente metadados',
        liveSchedule[0].title === 'News' && liveSchedule[0].startEpochSeconds === 100 && JSON.stringify(liveSchedule).indexOf('provider.test') === -1);
    check('proteção de vídeo reconhece as mesmas variantes de alto risco do Android',
        window.BuroDomain.hasHighRiskVideoTag('[4K] Filme') &&
        window.BuroDomain.hasHighRiskVideoTag('Filme H.265 HDR') &&
        !window.BuroDomain.hasHighRiskVideoTag('Filme 1080p H264'));
    check('prefixo de compatibilidade remove qualidade e tags do provedor',
        window.BuroDomain.compatibilityTitlePrefix('[L1] Filme teste [4K] DUAL') === 'Filme teste DUAL');

    process.stdout.write('Tela de carregamento\n');
    bootFrame = window.__bootFirstFrame;
    (function () {
        var languages = ['pt-BR', 'en', 'de', 'it', 'es'];
        var stepKeys = [
            'legalBody', 'legalBodyTwo', 'legalBodyThree', 'legalPrivacy', 'legalAccept',
            'bootProfiles', 'bootCatalogue', 'bootArtwork', 'bootReady', 'bootStageLabel',
            'catalogueLayout', 'layoutPoster', 'layoutCompact', 'layoutList', 'catalogueSort',
            'sortProvider', 'sortTitleAsc', 'sortTitleDesc', 'sortYearDesc', 'sortYearAsc',
            'sortRatingDesc', 'filterGenre', 'filterYear', 'filterAll', 'clearFilters',
            'noFilterResults', 'season', 'manageSource', 'sourceUpdated', 'sourceDeleted',
            'deleteSource', 'confirmDeleteSource', 'deleteSourceWarning', 'audioTracks',
            'subtitleTracks', 'subtitlesOff', 'noAudioTracks', 'noSubtitleTracks', 'playerMenuHint', 'playbackSpeed',
            'playerLockHint', 'playerControlsLocked', 'playerUnlockHint',
            'playbackProgress', 'trailerProgress',
            'playbackSourceUnavailableError', 'playerAspectRatio', 'playerScaleOriginal',
            'playerScaleFill', 'playerScaleAuto',
            'loadingCatalogue', 'couldNotLoad', 'catalogueLoadError', 'detailsLoadError',
            'noCategories', 'noCategoriesBody', 'noItems', 'noItemsBody', 'noEpisodes', 'noEpisodesBody',
            'resumeQuestion', 'resumeFrom', 'resumeSavedAt', 'startOver',
            'seriesWatchEpisode', 'seriesContinueEpisode',
            'searchIdle', 'searchIdleBody', 'searchWorking', 'searchEmpty', 'searchEmptyBody',
            'searchLoadError', 'searchPage', 'pageOf', 'previousPage', 'nextPage',
            'homeReleases', 'homeNewClassics', 'homeRecentlyAdded', 'homeTopRated',
            'homeFeaturedMovies', 'homeFeaturedSeries', 'homeHeroSynopsis', 'homeLoading',
            'homeLoadingBody', 'homeLoadError', 'homeCachedWarning', 'homeNoItems',
            'catalogueSyncTitle', 'catalogueSyncRunning', 'catalogueSyncComplete',
            'catalogueSyncCancelled', 'catalogueSyncError', 'catalogueSyncReady',
            'catalogueSyncCancel', 'catalogueSyncResume', 'catalogueSyncStarted',
            'catalogueSyncCompleted', 'catalogueSyncCancelledToast',
            'trailer', 'trailerLoading', 'trailerPlaying', 'trailerPlayingMuted',
            'trailerPaused', 'trailerEnded', 'trailerUnavailable', 'trailerHint',
            'metadata', 'tmdbTitle', 'tmdbBody', 'tmdbSharedLabel', 'tmdbSharedHint',
            'criticsGuideButton', 'criticsGuideTitle', 'criticsGuideIntro',
            'criticsGuideStep1Title', 'criticsGuideStep1Body', 'criticsGuideStep2Title',
            'criticsGuideStep2Body', 'criticsGuideStep3Title', 'criticsGuideStep3Body',
            'criticsGuideStep4Title', 'criticsGuideStep4Body', 'criticsGuideOpenSite',
            'tmdbGuideButton', 'tmdbGuideTitle', 'tmdbGuideIntro', 'tmdbGuideStepAccount',
            'tmdbGuideStepSettings', 'tmdbGuideStepRequest', 'tmdbGuideStepCopy', 'tmdbGuideOpenSite',
            'tmdbProfileLabel', 'tmdbProfileHint', 'tmdbKeyLabel', 'tmdbKeyHint', 'tmdbClear',
            'tmdbChecking', 'tmdbSaved', 'tmdbCleared', 'tmdbKeyInvalid', 'tmdbKeyRejected',
            'tmdbUnavailable', 'tmdbSecureError', 'tmdbAttribution', 'personLoading',
            'personMetadataHint', 'personFilmography', 'personNoFilmography', 'personInLibrary',
            'share', 'shareTitle', 'shareScan', 'shareSafe', 'shareQrUnavailable', 'shareLink',
            'sharedOpening', 'sharedMissingTitle', 'sharedMissingBody', 'sharedRetry',
            'sharedDismiss', 'sharedResolveError',
            'profilePhoto', 'chooseProfilePhoto', 'removeProfilePhoto', 'profilePhotoHint',
            'profilePhotoUsb', 'profilePhotoLoading', 'profilePhotoReading', 'profilePhotoError',
            'profilePhotoErrorBody', 'profilePhotoEmpty', 'profilePhotoEmptyBody', 'profilePhotoPickerHint',
            'downloadSeries', 'downloadSeason', 'downloadSeriesConfirmTitle', 'downloadSeasonConfirmTitle',
            'downloadBatchConfirmOne', 'downloadBatchConfirmMany', 'downloadBatchStarted',
            'downloadBatchPartial', 'downloadBatchFailed', 'downloadBatchNoChange',
            'downloadInterrupted',
            'localM3u', 'usbM3uHint', 'usbM3uLoading', 'usbM3uEmpty', 'usbM3uError',
            'favoritesEmpty', 'continueEmpty', 'historyEmpty', 'playerNow', 'playerNext',
            'reminderNotInLibrary',
            'discoverIntro', 'discoverKeep', 'discoverSkip', 'discoverDetails', 'discoverExhausted',
            'discoverAgain', 'discoverCounter', 'discoverLoading', 'discoverNeedsCatalogue',
            'settingsVersion', 'settingsLegal', 'settingsLanguageHint', 'languageCurrent',
            'activeProfile', 'chooseProfile',
            'settingOn', 'settingOff', 'subtitleHint', 'subtitleSizeSmall', 'subtitleSizeMedium', 'subtitleSizeLarge',
            'subtitleSizeHuge', 'subtitleColourWhite', 'subtitleColourYellow',
            'subtitleColourGrey', 'subtitleColourGreen', 'subtitleColourCyan',
            'subscriptions', 'subscriptionsBrowse', 'subscriptionsMovies', 'subscriptionsSeries',
            'subscriptionsThisWeek', 'subscriptionsUpcoming', 'subscriptionsRegion',
            'subscriptionsLoading', 'subscriptionsServices', 'subscriptionsUnavailable',
            'subscriptionsEmpty', 'subscriptionsUpcomingShelf', 'subscriptionsWhere',
            'subscriptionsBack', 'subscriptionsAvailable', 'subscriptionsLoadingOffers',
            'subscriptionsSeeMore', 'subscriptionsAllOn', 'subscriptionsLoadingMore', 'subscriptionsMoreFailed',
            'subscriptionsUnknown', 'subscriptionsSynopsis', 'offerLibrary', 'offerSubscription',
            'offerAds', 'offerFree', 'offerRent', 'offerBuy', 'externalOpenUnavailable'
        ];
        var missing = [];
        var previous = window.BuroI18n.language();
        languages.forEach(function (language) {
            window.BuroI18n.setLanguage(language);
            stepKeys.forEach(function (key) {
                var value = window.BuroI18n.t(key);
                if (!value || value === key) { missing.push(language + '.' + key); }
            });
        });
        window.BuroI18n.setLanguage(previous);
        check('mensagens de carregamento e catálogo existem nos cinco idiomas', missing.length === 0);
        check('Descobrir possui textos próprios nos cinco idiomas',
            languages.every(function (language) {
                window.BuroI18n.setLanguage(language);
                return window.BuroI18n.t('discover') !== 'discover' &&
                    window.BuroI18n.t('discoverIntro') !== 'discoverIntro' &&
                    window.BuroI18n.t('discoverKeep') !== 'discoverKeep' &&
                    window.BuroI18n.t('discoverSkip') !== 'discoverSkip' &&
                    window.BuroI18n.t('discoverDetails') !== 'discoverDetails' &&
                    window.BuroI18n.t('discoverCounter').indexOf('{current}') >= 0;
            }));
        window.BuroI18n.setLanguage(previous);
    }());

    /*
      Capturado no primeiro quadro após init(), antes de o armazenamento
      responder: é o estado que o usuário vê enquanto o catálogo carrega.
    */
    check('a tela de carregamento aparece antes do shell', bootFrame.present);
    check('o boot usa a arte cinematográfica original do Android', bootFrame.cinematic);
    check('o conteúdo fica no painel central equivalente ao Android', bootFrame.panel);
    check('a tela usa a marca circular vetorial equivalente ao Windows', bootFrame.mark);
    check('há indicador circular em vez de porcentagem inventada', bootFrame.spinner);
    check('as quatro etapas universais são representadas', bootFrame.dots === 4);
    check('a etapa em curso é descrita ao usuário',
        Boolean(bootFrame.message) && bootFrame.message !== 'bootProfiles');
    check('o primeiro estágio expõe progresso real de 25 por cento',
        bootFrame.progress && bootFrame.progress.role === 'progressbar' &&
        bootFrame.progress.now === '25' && bootFrame.progress.min === '0' && bootFrame.progress.max === '100');
    check('mudanças do boot formam uma única região de status educada', bootFrame.live);

    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('items', { id: 'movie:favorite-only', sourceId: 'source-public', categoryId: 'movies', contentType: 'MOVIE', name: 'Favorite only' }, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.get('items', 'movie:favorite-only', function (item) { hydratedFavoriteItem = item; resolve(); }, reject);
    });
    check('item persistido pode ser hidratado diretamente por identidade',
        hydratedFavoriteItem && hydratedFavoriteItem.name === 'Favorite only');

    process.stdout.write('Onboarding e D-pad\n');
    await waitFor(function () { return window.document.querySelector('[data-action="legal-accept"]'); }, 2500);
    check('abre no aviso legal com a marca real e card lateral como o Android',
        Boolean(window.document.querySelector('.legal-brand-mark img[src="icon.png"]')) &&
        Boolean(window.document.querySelector('.legal-card')));
    check('o aviso separa as três responsabilidades e destaca a privacidade',
        window.document.querySelectorAll('.legal-copy p').length === 3 &&
        window.document.querySelector('.legal-copy').textContent.indexOf('DRM') >= 0 &&
        window.document.querySelector('.legal-privacy').textContent.indexOf('cifradas') >= 0);
    check('o único aceite recebe foco inicial e não exige um checkbox diferente do Android',
        window.document.querySelectorAll('[data-action="legal-accept"]').length === 1 &&
        window.document.querySelector('[data-action="legal-accept"]').classList.contains('focused') &&
        !window.document.querySelector('[data-action="legal-toggle"]'));
    check('o foco real do documento acompanha o aceite legal',
        window.document.activeElement === window.document.querySelector('[data-action="legal-accept"]'));
    press(window, 13);
    check('um ENTER registra o aceite e avança para perfis',
        window.BuroApp.state.preferences.acceptedLegal &&
        Boolean(window.document.querySelector('[data-action="profile-form"]')));
    press(window, 13);
    check('campos do perfil possuem label associado e estados customizados acessíveis',
        window.document.querySelector('label[for="profile-name"]') &&
        window.document.querySelector('[data-action="kids-toggle"]').getAttribute('role') === 'checkbox' &&
        window.document.querySelector('[data-action="kids-toggle"]').getAttribute('aria-checked') === 'false' &&
        window.document.querySelector('[data-action="profile-avatar"].selected').getAttribute('aria-pressed') === 'true');
    window.document.getElementById('profile-name').value = 'Sala';
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    await waitFor(function () { return window.document.querySelector('.shell'); }, 2500);
    check('cria perfil e abre o shell', Boolean(window.document.querySelector('.shell')));
    check('o runtime anuncia uma vez que o shell terminou o boot',
        window.__runtimeReadyMessages.length === 1 &&
        window.__runtimeReadyMessages[0] === 'IPTVBURO_RUNTIME_READY screen=SHELL version=3.0.1' &&
        window.document.getElementById('app').getAttribute('data-runtime-ready') === '3.0.1');
    check('o marcador de prontidão não expõe perfil, fonte nem credencial',
        window.__runtimeReadyMessages[0].indexOf('Sala') === -1 &&
        window.__runtimeReadyMessages[0].indexOf('source-') === -1 &&
        window.__runtimeReadyMessages[0].indexOf('password') === -1);
    /* Descobrir entrou como décima terceira seção e usa somente o catálogo
       autorizado; Lembretes é a décima quarta, ao lado de Histórico. */
    /* Quinze: Assinaturas passou a aparecer sempre, como no aplicativo do
       Windows, em vez de só quando havia chave do TMDb. */
    check('shell contém todas as quinze seções', window.document.querySelectorAll('.nav-list [data-action="section"]').length === 15);
    check('a navegação principal usa a BURO Ribbon',
        Boolean(window.document.querySelector('.buro-ribbon')) && !window.document.querySelector('.nav-rail'));
    check('Ribbon e tela corrente expõem landmark, título e destino atual',
        window.document.querySelector('.main-pane').getAttribute('aria-labelledby') === 'screen-title' &&
        window.document.querySelector('.nav-item[aria-current="page"]').getAttribute('role') === 'button' &&
        !window.document.getElementById('app').hasAttribute('aria-live'));
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="HOME"]'));
    check('novas renderizações do shell não repetem o marcador de prontidão',
        window.__runtimeReadyMessages.length === 1);
    var ribbonPath = [];
    for (var ribbonStep = 0; ribbonStep < 20; ribbonStep += 1) {
        var ribbonFocus = window.document.querySelector('.nav-list .focused');
        if (ribbonFocus) { ribbonPath.push(ribbonFocus.getAttribute('data-section')); }
        if (ribbonFocus && ribbonFocus.getAttribute('data-section') === 'SETTINGS') { break; }
        press(window, 39);
    }
    check('D-pad alcança Fontes e Configurações mesmo fora da largura inicial da Ribbon',
        ribbonPath.indexOf('SOURCES') >= 0 &&
        ribbonPath.indexOf('SETTINGS') === ribbonPath.indexOf('SOURCES') + 1);
    check('o destino fora da largura é revelado pela rolagem programática da Ribbon',
        window.__scrollIntoViewCalls.some(function (call) {
            return call.section === 'SETTINGS' && call.inline === 'nearest';
        }));
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="HOME"]'));
    window.document.querySelector('.nav-list [data-section="SEARCH"]').click();
    var pointerReachedSearch = window.BuroApp.state.section === 'SEARCH';
    window.document.querySelector('.nav-list [data-section="HOME"]').click();
    check('clique, mouse e touch usam os mesmos controles recém-renderizados do D-pad',
        pointerReachedSearch && window.BuroApp.state.section === 'HOME');
    check('a Home vazia mantém a Living Home cinematográfica do Android',
        Boolean(window.document.querySelector('.living-hero')) &&
        window.document.querySelectorAll('.demo-media-card').length === 14 &&
        window.document.querySelectorAll('[data-demo-rail]').length === 3);
    check('os três trilhos demonstrativos repetem a composição 4/4/6 do Android',
        window.document.querySelectorAll('[data-demo-rail="demo:rail:continue"] .demo-media-card').length === 4 &&
        window.document.querySelectorAll('[data-demo-rail="demo:rail:live"] .demo-media-card').length === 4 &&
        window.document.querySelectorAll('[data-demo-rail="demo:rail:editorial"] .demo-media-card').length === 6 &&
        window.document.querySelectorAll('.demo-card-copy small').length === 14);
    check('continuidade e ao vivo mostram progresso e o editorial usa pôster',
        window.document.querySelectorAll('.demo-card-progress').length === 8 &&
        window.document.querySelectorAll('.demo-media-card.poster').length === 6);
    check('a Living Home deixa explícito que os cards são demonstração visual',
        window.document.querySelector('.demo-notice').textContent.indexOf(window.BuroI18n.t('demoNotice')) >= 0);
    check('o aviso da Home vazia participa do fluxo da barra superior sem cobrir sino ou plataforma',
        window.document.querySelector('.demo-notice').parentElement === window.document.querySelector('.topbar'));
    var demoTranslationKeys = ['demoPrismTitle', 'demoLiveTitle', 'demoFrequencyTitle', 'demoStoryNoPlayback'];
    check('os novos trilhos e detalhes têm texto real nos cinco idiomas',
        window.BuroI18n.supported().every(function (language) {
            window.BuroI18n.setLanguage(language);
            return demoTranslationKeys.every(function (key) { return window.BuroI18n.t(key) !== key; });
        }));
    window.BuroI18n.setLanguage('pt-BR');
    window.BuroApp._activate(window.document.querySelector('[data-id="demo:continue:prism-city"]'));
    check('cada card abre seu próprio detalhe fictício sem ação de reprodução',
        window.BuroApp.state.screenData.demoId === 'demo:continue:prism-city' &&
        window.document.querySelector('.demo-story h2').textContent === window.BuroI18n.t('demoPrismTitle') &&
        window.document.querySelector('.demo-no-playback').textContent === window.BuroI18n.t('demoStoryNoPlayback') &&
        !window.document.querySelector('[data-action="play"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="back"]'));
    check('voltar do detalhe demonstrativo restaura os três trilhos da Home',
        window.BuroApp.state.section === 'HOME' && !window.BuroApp.state.screenData &&
        window.document.querySelectorAll('[data-demo-rail]').length === 3);
    check('o player possui timeline e atalhos visíveis para áudio e legenda',
        Boolean(window.document.querySelector('.player-timeline')) &&
        Boolean(window.document.getElementById('player-audio-label')) &&
        Boolean(window.document.getElementById('player-subtitle-label')));
    check('timeline e diálogos do player possuem nome, papel e valores semânticos',
        window.document.getElementById('player-timeline').getAttribute('role') === 'progressbar' &&
        window.document.getElementById('player-timeline').getAttribute('aria-label') === window.BuroI18n.t('playbackProgress') &&
        window.document.getElementById('player-error-panel').getAttribute('role') === 'alertdialog' &&
        window.document.getElementById('player-menu').getAttribute('role') === 'dialog');

    process.stdout.write('Gerenciamento de perfis\n');
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="PROFILES"]'));
    check('a área de perfis oferece edição separada da troca de perfil',
        Boolean(window.document.querySelector('[data-action="profile-edit"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-edit"]'));
    window.document.getElementById('profile-name').value = 'Sala principal';
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-avatar"][data-avatar="ember"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="kids-toggle"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    await waitFor(function () {
        return window.BuroApp.state.screen === 'SHELL' && window.BuroApp.state.section === 'PROFILES';
    }, 1000);
    check('nome, avatar e modo Kids podem ser editados com o controle remoto',
        window.BuroApp.state.profiles[0].name === 'Sala principal' &&
        window.BuroApp.state.profiles[0].avatarKey === 'ember' &&
        window.BuroApp.state.profiles[0].isKids === true);
    check('a fonte automática aparece como preferência explícita',
        Boolean(window.document.body.textContent.indexOf(window.BuroI18n.t('manageProfiles')) >= 0));

    /* A TV não tem seletor de arquivos: fotos chegam por um USB montado. */
    var profilePhotoData = 'data:image/jpeg;base64,SlBFRw==';
    var originalProfilePhotoResize = window.BuroProfilePhoto.resize;
    window.tizen.filesystem = {
        listStorages: function (success) {
            success([{ label: 'removable_profile_fixture', state: 'MOUNTED' }]);
        },
        addStorageStateChangeListener: function () { return 1; },
        resolve: function (label, success) {
            success({
                isDirectory: true,
                listFiles: function (done) {
                    done([{
                        name: 'perfil.jpg', fileSize: 64, isDirectory: false,
                        fullPath: '/private/usb/perfil.jpg',
                        toURI: function () { return 'file:///usb/perfil.jpg'; },
                        openStream: function (mode, opened) {
                            opened({ readBase64: function () { return 'SlBFRw=='; }, close: function () {} });
                        }
                    }]);
                }
            });
        }
    };
    window.BuroUsb.watch(function () {});
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-edit"]'));
    check('editor oferece foto somente quando existe USB montado',
        Boolean(window.document.querySelector('[data-action="profile-photo-choose"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-photo-choose"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="profile-photo-select"]'); }, 1000);
    check('seletor mostra a imagem sem expor o caminho físico como texto',
        window.document.querySelectorAll('[data-action="profile-photo-select"]').length === 1 &&
        window.document.body.textContent.indexOf('/private/usb/') === -1);
    window.BuroProfilePhoto.resize = function (source, success) {
        if (/^data:image\/jpeg;base64,/.test(source)) { success(profilePhotoData); }
    };
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-photo-select"]'));
    await waitFor(function () { return window.BuroApp.state.screen === 'PROFILE_FORM'; }, 1000);
    check('seleção retorna ao formulário preservando o rascunho do perfil',
        window.document.getElementById('profile-name').value === 'Sala principal' &&
        Boolean(window.document.querySelector('.profile-photo-preview img')));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    await waitFor(function () { return window.BuroApp.state.screen === 'SHELL'; }, 1000);
    var storedPhotoProfile = await new Promise(function (resolve, reject) {
        window.BuroStorage.get('profiles', window.BuroApp.state.profiles[0].id, resolve, reject);
    });
    check('foto redimensionada persiste no perfil e aparece no card e na Ribbon',
        storedPhotoProfile.photoDataUrl === profilePhotoData &&
        window.BuroApp.state.profiles[0].photoDataUrl === profilePhotoData &&
        Boolean(window.document.querySelector('.profile-card .avatar img')) &&
        Boolean(window.document.querySelector('.ribbon-avatar img')));
    check('perfil persistido não contém caminho do USB', JSON.stringify(storedPhotoProfile).indexOf('file:///') === -1);
    window.BuroProfilePhoto.resize = originalProfilePhotoResize;
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-edit"]'));
    check('foto personalizada pode ser removida no mesmo editor',
        Boolean(window.document.querySelector('[data-action="profile-photo-remove"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-photo-remove"]'));
    check('remover restaura imediatamente o avatar escolhido no rascunho',
        window.BuroApp.state.screenData.photoDataUrl === null &&
        !window.document.querySelector('.profile-photo-preview img'));
    window.BuroApp._activate(window.document.querySelector('[data-action="back"]'));

    window.BuroApp._activate(window.document.querySelector('[data-action="profile-form"]'));
    window.document.getElementById('profile-name').value = 'Quarto';
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    await waitFor(function () { return window.BuroApp.state.profiles.length === 2; }, 1000);
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="PROFILES"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-edit"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-delete"]'));
    check('a exclusão exige uma segunda confirmação',
        window.document.querySelector('[data-action="profile-delete"]').textContent === window.BuroI18n.t('confirmDeleteProfile'));
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-delete"]'));
    await waitFor(function () { return window.BuroApp.state.profiles.length === 1; }, 1000);
    check('perfil é excluído sem permitir que o app fique sem perfil',
        window.BuroApp.state.profiles.length === 1 && !window.document.querySelector('[data-action="profile-delete"]'));

    process.stdout.write('Importação M3U local por USB\n');
    var usbPlaylistText = '#EXTM3U\n#EXTINF:-1 tvg-id="usb-local" group-title="Filmes",Filme do USB\n' +
        'https://media.public.test/usb-local.mp4';
    window.tizen.filesystem.resolve = function (label, success, failure) {
        if (label !== 'removable_profile_fixture') { if (failure) { failure({ name: 'NotFoundError' }); } return; }
        success({
            isDirectory: true,
            listFiles: function (done) {
                done([{
                    name: 'minha-lista.m3u', fileSize: usbPlaylistText.length, isDirectory: false,
                    fullPath: '/private/usb/minha-lista.m3u',
                    openStream: function (mode, opened) {
                        opened({ read: function () { return usbPlaylistText; }, close: function () {} });
                    }
                }]);
            }
        });
    };
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SOURCES"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-add"]'));
    check('adicionar fonte oferece M3U do USB somente com volume montado',
        Boolean(window.document.querySelector('[data-action="source-usb-m3u"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-usb-m3u"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="source-usb-m3u-select"]'); }, 1000);
    check('seletor M3U exibe nome e tamanho sem caminho físico',
        window.document.body.textContent.indexOf('minha-lista.m3u') >= 0 &&
        window.document.body.textContent.indexOf('/private/usb/') === -1);
    window.BuroApp._activate(window.document.querySelector('[data-action="source-usb-m3u-select"]'));
    await waitFor(function () {
        return window.BuroApp.state.sources.some(function (source) { return source.type === 'LOCAL_M3U'; });
    }, 1000);
    var localUsbSource = window.BuroApp.state.sources.filter(function (source) { return source.type === 'LOCAL_M3U'; })[0];
    var localUsbSecret = window.BuroStorage.secureGet(localUsbSource.id);
    check('arquivo USB cria fonte local e importa o catálogo pelo mesmo parser M3U',
        localUsbSource.name === 'minha-lista' && localUsbSource.channelCount === 1 &&
        window.BuroApp.state.items.some(function (item) { return item.sourceId === localUsbSource.id && item.name === 'Filme do USB'; }));
    check('KeyManager conserva apenas seletor opaco e nenhum caminho ou conteúdo M3U',
        /^usb-playlist-[a-z0-9]+$/.test(localUsbSecret.playlistToken) &&
        JSON.stringify(localUsbSecret).indexOf('/private/') === -1 &&
        JSON.stringify(localUsbSecret).indexOf('media.public.test') === -1 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('minha-lista.m3u') === -1);
    var localUsbItemIds = {};
    window.BuroApp.state.items.forEach(function (item) { if (item.sourceId === localUsbSource.id) { localUsbItemIds[item.id] = true; } });
    await new Promise(function (resolve, reject) { window.BuroStorage.deleteSourceData(localUsbSource.id, resolve, reject); });
    window.BuroStorage.secureRemove(localUsbSource.id);
    window.BuroApp.state.sources = window.BuroApp.state.sources.filter(function (source) { return source.id !== localUsbSource.id; });
    window.BuroApp.state.categories = window.BuroApp.state.categories.filter(function (category) { return category.sourceId !== localUsbSource.id; });
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) { return !localUsbItemIds[item.id]; });
    window.BuroApp.state.activeProfile.sourceId = null;
    window.BuroApp.state.activeSource = null;
    window.BuroApp.render();

    process.stdout.write('Filtros da biblioteca por tipo\n');
    var libraryProfileId = window.BuroApp.state.activeProfile.id;
    var libraryFixtureItems = [
        { id: 'movie:library-filter', sourceId: 'source-public', contentType: 'MOVIE', name: 'Filme da biblioteca' },
        { id: 'series:library-filter', sourceId: 'source-public', contentType: 'SERIES', name: 'Série da biblioteca' },
        { id: 'live:library-filter', sourceId: 'source-public', contentType: 'LIVE', name: 'Canal da biblioteca' }
    ];
    Array.prototype.push.apply(window.BuroApp.state.items, libraryFixtureItems);
    window.BuroApp.state.favorites.push(
        { id: 'fav-filter-movie', profileId: libraryProfileId, itemId: 'movie:library-filter' },
        { id: 'fav-filter-series', profileId: libraryProfileId, itemId: 'series:library-filter' },
        { id: 'fav-filter-live', profileId: libraryProfileId, itemId: 'live:library-filter' }
    );
    window.BuroApp.state.progress.push(
        { id: 'progress-filter-movie', profileId: libraryProfileId, itemId: 'movie:library-filter', completed: false, updatedAt: 1000 },
        { id: 'progress-filter-series', profileId: libraryProfileId, itemId: 'series:library-filter', completed: false, updatedAt: 2000 }
    );
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="MY_BURO"]'));
    check('Minha BURO oferece Todos, Filmes, Séries e Ao Vivo quando os tipos existem',
        window.document.querySelectorAll('[data-action="library-filter"]').length === 4);
    window.BuroApp._activate(window.document.querySelector('[data-action="library-filter"][data-kind="SERIES"]'));
    check('filtro de favoritos mostra somente o tipo escolhido',
        window.document.querySelectorAll('.media-card').length === 1 &&
        window.document.querySelector('.media-card').getAttribute('data-id') === 'series:library-filter');
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="CONTINUE_WATCHING"]'));
    check('Continuar assistindo mantém a ordem real de atualização mais recente',
        window.document.querySelector('.media-card').getAttribute('data-id') === 'series:library-filter');
    window.BuroApp._activate(window.document.querySelector('[data-action="library-filter"][data-kind="MOVIE"]'));
    check('Continuar assistindo filtra Filmes e Séries pelo D-pad',
        window.document.querySelectorAll('.media-card').length === 1 &&
        window.document.querySelector('.media-card').getAttribute('data-id') === 'movie:library-filter');
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="HISTORY"]'));
    check('Histórico possui o mesmo seletor por tipo',
        window.document.querySelectorAll('[data-action="library-filter"]').length === 3);
    var fixtureIds = { 'movie:library-filter': true, 'series:library-filter': true, 'live:library-filter': true };
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) { return !fixtureIds[item.id]; });
    window.BuroApp.state.favorites = window.BuroApp.state.favorites.filter(function (row) { return !fixtureIds[row.itemId]; });
    window.BuroApp.state.progress = window.BuroApp.state.progress.filter(function (row) { return !fixtureIds[row.itemId]; });

    var offlineFiles = {};
    var downloadCallbacks = {};
    var offlineOpenedUrl = null;
    var offlineInitialSeek = -1;
    window.tizen.filesystem.resolve = function (label, success, failure) {
        if (offlineFiles[label]) {
            success({ isDirectory: false, toURI: function () { return offlineFiles[label]; } });
            return;
        }
        if (String(label).indexOf('/IPTV BURO') >= 0) {
            if (/\/IPTV BURO$/.test(String(label))) {
                success({ moveTo: function (source, destination, overwrite, done) { done(); } });
            } else if (failure) { failure({ name: 'NotFoundError' }); }
            return;
        }
        success({
            resolve: function () { throw { name: 'NotFoundError' }; },
            createDirectory: function (name) { return { fullPath: label + '/' + name }; }
        });
    };
    var nextDownloadId = 1;
    window.tizen.download = {
        start: function (request, callbacks) {
            var id = nextDownloadId; nextDownloadId += 1;
            downloadRequests.push(request); downloadCallbacks[id] = callbacks; return id;
        },
        pause: function () {}, resume: function () {}, cancel: function () {}
    };
    window.tizen.DownloadRequest = function (url, destination, fileName) {
        this.url = url; this.destination = destination; this.fileName = fileName;
    };
    await new Promise(function (resolve, reject) {
        window.BuroDownloads.start(
            { contentType: 'MOVIE', providerItemId: '501', name: 'Filme offline' },
            function () { return 'https://public.test/movie.mp4'; }, resolve, reject
        );
    });
    await new Promise(function (resolve, reject) {
        window.BuroDownloads.start(
            { contentType: 'EPISODE', providerItemId: '601', name: 'Episódio offline' },
            function () { return 'https://public.test/episode.mp4'; }, resolve, reject
        );
    });
    downloadCallbacks[1].oncompleted(1, 'removable_profile_fixture/IPTV BURO/movie-501.mp4.part');
    offlineFiles['removable_profile_fixture/IPTV BURO/movie-501.mp4'] =
        'file:///removable_profile_fixture/IPTV%20BURO/movie-501.mp4';
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="DOWNLOADS"]'));
    check('Downloads oferece Todos, Filmes, Séries e modo Compacto',
        window.document.querySelectorAll('[data-action="download-filter"]').length === 3 &&
        Boolean(window.document.querySelector('[data-action="download-compact"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="download-filter"][data-kind="EPISODE"]'));
    check('filtro de Downloads separa episódios de filmes',
        window.document.querySelectorAll('.download-row').length === 1 &&
        window.document.querySelector('.download-row').textContent.indexOf('Episódio offline') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="download-compact"]'));
    check('modo Compacto reduz a lista sem alterar a fila',
        window.document.querySelector('.download-list').classList.contains('compact') &&
        window.BuroDownloads.list().length === 2);
    window.BuroApp._activate(window.document.querySelector('[data-action="download-filter"][data-kind="MOVIE"]'));
    check('download concluido oferece Assistir e Remover pelo D-pad',
        Boolean(window.document.querySelector('[data-action="download-play"]')) &&
        Boolean(window.document.querySelector('[data-action="download-remove"]')));
    window.webapis = { avplay: {
        getState: function () { return 'READY'; },
        open: function (url) { offlineOpenedUrl = url; }, setListener: function () {},
        setDisplayRect: function () {}, setDisplayMethod: function () {},
        setSilentSubtitle: function () {},
        prepareAsync: function (done) { done(); }, play: function () {}, stop: function () {}, close: function () {},
        seekTo: function (position, done) { offlineInitialSeek = position; done(); },
        getDuration: function () { return 120000; }
    } };
    window.BuroApp._activate(window.document.querySelector('[data-action="download-play"]'));
    check('Assistir resolve a URI local tarde e abre AVPlay sem consultar a fonte',
        offlineOpenedUrl === 'file:///removable_profile_fixture/IPTV%20BURO/movie-501.mp4' &&
        window.document.body.classList.contains('playing') &&
        JSON.stringify(window.BuroDownloads.list()).indexOf('file:') === -1 &&
        (window.localStorage.getItem('iptvburo.downloads.v1') || '').indexOf('file:') === -1);
    press(window, 10009);
    window.BuroApp.state.progress.push({
        id: 'progress-offline-501', profileId: libraryProfileId, itemId: 'movie:501',
        positionMs: 30000, durationMs: 120000, completed: false, updatedAt: Date.now()
    });
    window.BuroApp.render();
    window.BuroApp._activate(window.document.querySelector('[data-action="download-play"]'));
    check('download offline reutiliza a decisao Continuar ou Recomecar',
        window.BuroApp.state.screen === 'RESUME_PROMPT' &&
        window.document.body.textContent.indexOf('00:30') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="resume-continue"]'));
    check('Continuar offline aplica a posicao salva antes de reproduzir', offlineInitialSeek === 30000);
    press(window, 10009);
    window.BuroApp.state.progress = window.BuroApp.state.progress.filter(function (row) {
        return row.id !== 'progress-offline-501';
    });
    window.BuroDownloads.remove('movie:501');
    window.BuroDownloads.remove('episode:601');
    window.tizen.filesystem.listStorages = function (success) {
        success([{ label: 'removable_profile_fixture', state: 'REMOVED' }]);
    };
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });

    process.stdout.write('TMDb seguro por perfil\n');
    window.tizen.application.getAppInfo = function () { return { version: '3.0.1' }; };
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SETTINGS"]'));
    var settingsLanguages = Array.prototype.slice.call(
        window.document.querySelectorAll('.settings-language-option[data-action="language"]'));
    var currentSettingsLanguage = window.document.querySelector('.settings-language-option.selected');
    check('Configurações mostra versão instalada e aviso legal equivalente ao Android',
        window.document.querySelector('.settings-about-card').textContent.indexOf('3.0.1') >= 0 &&
        window.document.querySelector('.settings-about-card').textContent.indexOf(window.BuroI18n.t('settingsLegal')) >= 0);
    check('idiomas aparecem em cinco linhas legíveis em vez de códigos isolados',
        settingsLanguages.length === 5 &&
        settingsLanguages.map(function (row) { return row.querySelector('strong').textContent; }).join('|') ===
            'Português (Brasil)|English|Deutsch|Italiano|Español');
    check('idioma atual informa seleção visual e semântica',
        currentSettingsLanguage && currentSettingsLanguage.getAttribute('data-language') === 'pt-BR' &&
        currentSettingsLanguage.getAttribute('aria-pressed') === 'true' &&
        currentSettingsLanguage.textContent.indexOf(window.BuroI18n.t('languageCurrent')) >= 0);
    check('Configurações mostra o perfil ativo e uma ação direta para trocá-lo',
        window.document.querySelector('.settings-active-profile').textContent.indexOf(window.BuroApp.state.activeProfile.name) >= 0 &&
        window.document.querySelector('.settings-active-profile').getAttribute('data-section') === 'PROFILES');
    window.BuroApp._activate(window.document.querySelector('.settings-active-profile'));
    check('ação do perfil ativo abre o seletor navegável por D-pad',
        window.BuroApp.state.section === 'PROFILES' && Boolean(window.document.querySelector('[data-action="select-profile"]')));
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SETTINGS"]'));
    check('preferências booleanas expõem papel switch e estado atual',
        window.document.querySelector('[data-action="toggle-setting"]').getAttribute('role') === 'switch' &&
        /^(true|false)$/.test(window.document.querySelector('[data-action="toggle-setting"]').getAttribute('aria-checked')));
    (function () {
        var expectedSubtitleLabels = {
            'pt-BR': ['Pequeno', 'Médio', 'Grande', 'Muito grande', 'Branco', 'Amarelo', 'Cinza', 'Verde', 'Ciano'],
            en: ['Small', 'Medium', 'Large', 'Very large', 'White', 'Yellow', 'Grey', 'Green', 'Cyan'],
            de: ['Klein', 'Mittel', 'Groß', 'Sehr groß', 'Weiß', 'Gelb', 'Grau', 'Grün', 'Cyan'],
            it: ['Piccolo', 'Medio', 'Grande', 'Molto grande', 'Bianco', 'Giallo', 'Grigio', 'Verde', 'Ciano'],
            es: ['Pequeño', 'Mediano', 'Grande', 'Muy grande', 'Blanco', 'Amarillo', 'Gris', 'Verde', 'Cian']
        };
        var subtitleKeys = [
            'subtitleSizeSmall', 'subtitleSizeMedium', 'subtitleSizeLarge', 'subtitleSizeHuge',
            'subtitleColourWhite', 'subtitleColourYellow', 'subtitleColourGrey',
            'subtitleColourGreen', 'subtitleColourCyan'
        ];
        var previousLanguage = window.BuroI18n.language();
        var translated = Object.keys(expectedSubtitleLabels).every(function (language) {
            window.BuroI18n.setLanguage(language);
            return subtitleKeys.map(function (key) { return window.BuroI18n.t(key); }).join('|') ===
                expectedSubtitleLabels[language].join('|');
        });
        window.BuroI18n.setLanguage(previousLanguage);
        check('as nove opções de legenda repetem os rótulos Android nos cinco idiomas', translated);
    }());
    var subtitleSizeChoices = Array.prototype.slice.call(
        window.document.querySelectorAll('[data-action="subtitle-size-select"]'));
    var subtitleColourChoices = Array.prototype.slice.call(
        window.document.querySelectorAll('[data-action="subtitle-colour-select"]'));
    check('Configurações mostra as quatro dimensões e cinco cores de uma vez como Android',
        subtitleSizeChoices.length === 4 && subtitleColourChoices.length === 5 &&
        subtitleSizeChoices.map(function (row) { return row.textContent.trim(); }).join('|') ===
            'Pequeno|Médio|Grande|Muito grande' &&
        subtitleColourChoices.map(function (row) { return row.textContent.trim(); }).join('|') ===
            'Branco|Amarelo|Cinza|Verde|Ciano');
    check('a aparência atual da legenda possui seleção visual e semântica única',
        window.document.querySelectorAll('[data-action="subtitle-size-select"][aria-pressed="true"]').length === 1 &&
        window.document.querySelector('[data-action="subtitle-size-select"][aria-pressed="true"]').getAttribute('data-value') === 'medium' &&
        window.document.querySelectorAll('[data-action="subtitle-colour-select"][aria-pressed="true"]').length === 1 &&
        window.document.querySelector('[data-action="subtitle-colour-select"][aria-pressed="true"]').getAttribute('data-value') === 'white' &&
        window.document.querySelector('[data-action="toggle-setting"][data-property="subtitleBackground"] p').textContent === 'Ligado');
    window.BuroApp._activate(window.document.querySelector('[data-action="subtitle-size-select"][data-value="huge"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="subtitle-colour-select"][data-value="cyan"]'));
    check('escolhas diretas atualizam a preferência e continuam selecionadas após render',
        window.BuroApp.state.preferences.subtitleSize === 'huge' &&
        window.BuroApp.state.preferences.subtitleColour === 'cyan' &&
        window.document.querySelector('[data-action="subtitle-size-select"][data-value="huge"]').getAttribute('aria-pressed') === 'true' &&
        window.document.querySelector('[data-action="subtitle-colour-select"][data-value="cyan"]').getAttribute('aria-pressed') === 'true' &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('"subtitleSize":"huge"') >= 0 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('"subtitleColour":"cyan"') >= 0);
    check('Configuracoes oferece o guia OMDb presente no Android e Windows',
        Boolean(window.document.querySelector('[data-action="critics-settings"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="critics-settings"]'));
    check('tela OMDb explica as notas e oferece ajuda para obter a chave',
        Boolean(window.document.getElementById('critics-key')) &&
        Boolean(window.document.querySelector('[data-action="critics-guide"]')) &&
        window.document.querySelector('[data-action="critics-guide"]').textContent.indexOf(
            window.BuroI18n.t('criticsGuideButton')) >= 0);
    var criticsGuideDraftKey = 'criticGuideDraft1234';
    window.document.getElementById('critics-key').value = criticsGuideDraftKey;
    window.BuroApp._activate(window.document.querySelector('[data-action="critics-guide"]'));
    check('guia OMDb apresenta quatro etapas e quatro ilustracoes locais',
        window.BuroApp.state.screen === 'CRITICS_GUIDE' &&
        window.document.querySelectorAll('.critics-guide-step').length === 4 &&
        window.document.querySelectorAll('.critics-guide-diagram[aria-hidden="true"]').length === 4);
    check('guia OMDb nao expoe nem persiste o rascunho da chave',
        window.document.body.textContent.indexOf(criticsGuideDraftKey) === -1 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(criticsGuideDraftKey) === -1);
    window.BuroApp._activate(window.document.querySelector('[data-action="critics-guide-open"]'));
    var criticsGuideAppControl = window.__launchedAppControls[window.__launchedAppControls.length - 1];
    check('guia OMDb abre somente o emissor HTTPS oficial da chave',
        criticsGuideAppControl &&
        criticsGuideAppControl.operation === 'http://tizen.org/appcontrol/operation/view' &&
        criticsGuideAppControl.uri === 'https://www.omdbapi.com/apikey.aspx');
    press(window, 10009);
    check('RETURN fecha o guia OMDb e restaura o rascunho somente em memoria',
        window.BuroApp.state.screen === 'CRITICS_SETTINGS' &&
        window.document.getElementById('critics-key').value === criticsGuideDraftKey &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(criticsGuideDraftKey) === -1);
    press(window, 10009);
    check('Configurações expõe TMDb como capability opcional e inicialmente não configurada',
        Boolean(window.document.querySelector('[data-action="tmdb-settings"]')) &&
        window.document.querySelector('[data-action="tmdb-settings"]').textContent.indexOf(window.BuroI18n.t('notConfigured')) >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-settings"]'));
    check('tela TMDb separa chave da casa e chave do perfil ativo',
        Boolean(window.document.getElementById('tmdb-key-shared')) &&
        Boolean(window.document.getElementById('tmdb-key-profile')) &&
        window.document.querySelector('label[for="tmdb-key-shared"]') &&
        window.document.querySelector('label[for="tmdb-key-profile"]'));
    check('configuração TMDb oferece o mesmo guia de chave existente no Android',
        Boolean(window.document.querySelector('[data-action="tmdb-guide"]')) &&
        window.document.querySelector('[data-action="tmdb-guide"]').textContent.indexOf(window.BuroI18n.t('tmdbGuideButton')) >= 0);
    var guideDraftKey = 'guideDraft1234567890';
    window.document.getElementById('tmdb-key-profile').value = guideDraftKey;
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-guide"]'));
    check('guia TMDb apresenta as quatro etapas e ilustrações locais',
        window.BuroApp.state.screen === 'TMDB_GUIDE' &&
        window.document.querySelectorAll('.tmdb-guide-step').length === 4 &&
        window.document.querySelectorAll('.tmdb-guide-diagram[aria-hidden="true"]').length === 4);
    check('guia não coloca o rascunho da chave no texto nem no armazenamento comum',
        window.document.body.textContent.indexOf(guideDraftKey) === -1 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(guideDraftKey) === -1);
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-guide-open"]'));
    var guideAppControl = window.__launchedAppControls[window.__launchedAppControls.length - 1];
    check('site do guia abre somente o cadastro HTTPS oficial pelo ApplicationControl',
        guideAppControl && guideAppControl.operation === 'http://tizen.org/appcontrol/operation/view' &&
        guideAppControl.uri === 'https://www.themoviedb.org/signup');
    press(window, 10009);
    check('RETURN fecha o guia e preserva o rascunho somente em memória',
        window.BuroApp.state.screen === 'TMDB_SETTINGS' &&
        window.document.getElementById('tmdb-key-profile').value === guideDraftKey &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(guideDraftKey) === -1);
    var originalValidateTmdbKey = window.BuroTmdb.validateKey;
    window.BuroTmdb.validateKey = function (value, success) { success(value); return { abort: function () {} }; };
    var syntheticTmdbKey = '1234567890abcdef1234567890abcdef';
    window.document.getElementById('tmdb-key-profile').value = syntheticTmdbKey;
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-save"][data-scope="profile"]'));
    check('chave validada vai ao KeyManager e nunca ao localStorage',
        Object.keys(window.__secureData).some(function (name) {
            return name.indexOf('tmdb-profile-') >= 0 && window.__secureData[name].indexOf(syntheticTmdbKey) >= 0;
        }) && (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(syntheticTmdbKey) === -1);

    var originalLoadSubscriptionShelves = window.BuroTmdb.loadShelves;
    var originalLoadSubscriptionTitle = window.BuroTmdb.loadSubscriptionTitle;
    var originalLoadServiceCatalogue = window.BuroTmdb.loadServiceCatalogue;
    var subscriptionCalls = [];
    var expandedCatalogueCall = null;
    window.BuroTmdb.loadShelves = function (key, region, kind, locale, progress, success) {
        var titles = [{
            tmdbId: 42, isSeries: kind === 'SERIES', title: kind === 'SERIES' ? 'Série externa' : 'Favorite only',
            year: null, rating: 8.2, overview: 'Sinopse da prateleira',
            posterUrl: 'https://image.tmdb.org/t/p/w342/subscription.jpg'
        }];
        if (kind === 'MOVIES') {
            titles.push({
                tmdbId: 84, isSeries: false, title: 'Filme futuro', year: 2099,
                releaseDate: '2099-06-15', rating: 7.9, overview: 'Ainda não entrou no catálogo',
                posterUrl: 'https://image.tmdb.org/t/p/w342/future.jpg'
            });
        }
        subscriptionCalls.push({ key: key, region: region, kind: kind, locale: locale });
        progress(1, 1, 1);
        success([{ providerId: 8, providerName: 'Netflix',
            providerLogoUrl: 'https://image.tmdb.org/t/p/w92/netflix.jpg', titles: titles }]);
        return { abort: function () {} };
    };
    window.BuroTmdb.loadSubscriptionTitle = function (key, title, region, locale, success) {
        success({
            details: {
                tmdbId: title.tmdbId, title: title.title, plot: 'Sinopse detalhada pública',
                posterUrl: 'https://image.tmdb.org/t/p/w342/subscription-detail.jpg',
                backdropUrl: 'https://image.tmdb.org/t/p/w1280/subscription-backdrop.jpg',
                rating: 8.6, duration: 121, genre: 'Drama', youtubeTrailerId: 'Trailer98765',
                castMembers: [{ id: 7, name: 'Ana Exemplo', character: 'Lia',
                    photoUrl: 'https://image.tmdb.org/t/p/w185/ana-subscription.jpg' }]
            },
            offers: [
                { providerId: 8, providerName: 'Netflix', type: 'subscription',
                    providerLogoUrl: 'https://image.tmdb.org/t/p/w92/netflix.jpg',
                    url: 'https://www.netflix.com/search?q=Favorite%20only', requiresAttribution: true },
                { providerId: 9, providerName: 'Plex', type: 'ads',
                    providerLogoUrl: 'https://image.tmdb.org/t/p/w92/plex.jpg',
                    url: 'https://www.themoviedb.org/movie/42/watch', requiresAttribution: true },
                { providerId: 10, providerName: 'Serviço inválido', type: 'buy',
                    providerLogoUrl: 'https://evil.test/logo.jpg?token=secret',
                    url: 'https://evil.test/watch/42', requiresAttribution: true }
            ],
            unknown: false
        });
        return { abort: function () {} };
    };
    window.BuroTmdb.loadServiceCatalogue = function (key, providerId, region, kind, locale, success, failure) {
        expandedCatalogueCall = {
            key: key, providerId: providerId, region: region, kind: kind, locale: locale,
            success: success, failure: failure, aborted: false
        };
        return { abort: function () { expandedCatalogueCall.aborted = true; } };
    };

    press(window, 10009);
    check('Assinaturas surge como capability somente depois de configurar TMDb',
        window.document.querySelectorAll('.nav-list [data-action="section"]').length === 15 &&
        Boolean(window.document.querySelector('.nav-list [data-section="SUBSCRIPTIONS"]')));
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SUBSCRIPTIONS"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="subscription-title"]'); }, 1000);
    check('Assinaturas replica os quatro filtros e cinco regiões navegáveis por D-pad',
        window.document.querySelectorAll('[data-action="subscription-filter"]').length === 4 &&
        window.document.querySelectorAll('[data-action="subscription-region"]').length === 5 &&
        subscriptionCalls[0].kind === 'MOVIES' && subscriptionCalls[0].region === 'BR' &&
        window.document.querySelector('[data-action="subscription-filter"].primary').getAttribute('aria-pressed') === 'true' &&
        window.document.querySelector('[data-action="subscription-region"].primary').getAttribute('aria-pressed') === 'true');
    check('prateleira mostra a marca publica sem substituir o nome textual',
        window.document.querySelector('.subscription-shelves .subscription-provider-logo') &&
        window.document.querySelector('.subscription-shelves .subscription-provider-logo').getAttribute('src') ===
            'https://image.tmdb.org/t/p/w92/netflix.jpg' &&
        window.document.querySelector('.subscription-shelves .section-heading').textContent.indexOf('Netflix') >= 0 &&
        window.document.querySelectorAll('.subscription-shelves .subscription-card-logo').length === 2);
    var subscriptionCallsAfterFirstVisit = subscriptionCalls.length;
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="HOME"]'));
    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SUBSCRIPTIONS"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="subscription-title"]'); }, 1000);
    check('reabrir Assinaturas no mesmo dia usa as prateleiras públicas sem outra consulta',
        subscriptionCalls.length === subscriptionCallsAfterFirstVisit &&
        window.BuroApp.state.screenData.shelves.length === 1 &&
        !window.BuroApp.state.screenData.loading);
    var expandService = window.document.querySelector('[data-action="subscription-expand"]');
    check('cada serviço real termina com Ver mais e Em breve não inventa catálogo',
        expandService && expandService.getAttribute('data-provider') === '8' &&
        !window.document.querySelector('[data-provider="coming-soon"]'));
    if (expandService) {
        var subscriptionShelfContent = window.document.querySelector('.content.scrollable');
        var subscriptionShelfRow = expandService.closest('.subscription-row');
        subscriptionShelfContent.scrollTop = 73;
        subscriptionShelfRow.scrollLeft = 31;
        window.BuroApp._activate(expandService);
        check('Ver mais abre imediatamente os títulos da prateleira enquanto busca o restante',
            expandedCatalogueCall && expandedCatalogueCall.providerId === 8 && expandedCatalogueCall.kind === 'MOVIES' &&
            window.BuroApp.state.screenData.expanded.loading &&
            window.document.querySelectorAll('.subscription-expanded-grid [data-action="subscription-title"]').length === 2);
        check('Ver mais abre no topo com Voltar focado em vez de herdar um índice distante',
            window.document.querySelector('[data-action="subscription-expanded-back"]').classList.contains('focused') &&
            window.document.querySelector('.content.scrollable').scrollTop === 0);
        check('grade ampla conserva a marca no cabecalho e em cada card',
            window.document.querySelector('.subscription-expanded .subscription-provider-logo') &&
            window.document.querySelector('.subscription-expanded .subscription-provider-logo').getAttribute('src') ===
                'https://image.tmdb.org/t/p/w92/netflix.jpg' &&
            window.document.querySelectorAll('.subscription-expanded-grid .subscription-card-logo').length === 2);
        expandedCatalogueCall.success(Array.from({ length: 45 }, function (_, index) {
            return {
                tmdbId: 1000 + index, isSeries: false, title: 'Catálogo amplo ' + index,
                year: 2026, posterUrl: 'https://image.tmdb.org/t/p/w342/expanded-' + index + '.jpg'
            };
        }));
        check('resposta ampla substitui a grade sem deixar mais de cem títulos no DOM',
            !window.BuroApp.state.screenData.expanded.loading &&
            window.document.querySelectorAll('.subscription-expanded-grid [data-action="subscription-title"]').length === 45);
        var expandedOrigin = window.document.querySelector('.subscription-expanded-grid [data-action="subscription-title"]');
        window.document.querySelector('.content.scrollable').scrollTop = 137;
        window.BuroApp._activate(expandedOrigin);
        await waitFor(function () { return window.document.querySelector('[data-action="subscription-back"]'); }, 1000);
        check('detalhe externo abre no topo e entrega foco ao botão Voltar',
            window.document.querySelector('[data-action="subscription-back"]').classList.contains('focused') &&
            window.document.querySelector('.content.scrollable').scrollTop === 0);
        window.BuroApp._activate(window.document.querySelector('[data-action="subscription-back"]'));
        check('Voltar do detalhe restaura a mesma grade ampla',
            window.BuroApp.state.screenData.expanded &&
            window.document.querySelectorAll('.subscription-expanded-grid [data-action="subscription-title"]').length === 45 &&
            window.document.querySelector('.subscription-expanded-grid [data-action="subscription-title"]').classList.contains('focused') &&
            window.document.querySelector('.content.scrollable').scrollTop === 137);
        press(window, 10009);
        check('RETURN fecha a grade ampla e devolve as prateleiras do mesmo filtro',
            !window.BuroApp.state.screenData.expanded &&
            window.document.querySelectorAll('.subscription-shelves [data-action="subscription-title"]').length === 2 &&
            window.document.querySelector('[data-action="subscription-expand"]').classList.contains('focused') &&
            window.document.querySelector('.content.scrollable').scrollTop === 73 &&
            window.document.querySelector('.subscription-row').scrollLeft === 31);
        window.BuroApp._activate(window.document.querySelector('[data-action="subscription-expand"]'));
        expandedCatalogueCall.failure({ code: 'NETWORK_ERROR' });
        check('falha conserva os títulos iniciais e uma explicação visível na grade',
            window.BuroApp.state.screenData.expanded.error &&
            window.document.querySelectorAll('.subscription-expanded-grid [data-action="subscription-title"]').length === 2 &&
            window.document.body.textContent.indexOf('Os títulos iniciais continuam disponíveis') >= 0);
        press(window, 10009);
        window.BuroApp._activate(window.document.querySelector('[data-action="subscription-expand"]'));
        var closedExpandedCall = expandedCatalogueCall;
        press(window, 10009);
        closedExpandedCall.success([{ tmdbId: 9999, isSeries: false, title: 'Resposta atrasada' }]);
        check('fechar cancela a consulta e resposta atrasada não reabre outro catálogo',
            closedExpandedCall.aborted && !window.BuroApp.state.screenData.expanded &&
            window.document.body.textContent.indexOf('Resposta atrasada') === -1);
    }
    var duplicatedShelf = {
        providerId: 18,
        providerName: 'Segundo serviço',
        providerLogoUrl: null,
        titles: window.BuroApp.state.screenData.shelves[0].titles.slice()
    };
    window.BuroApp.state.screenData.shelves.push(duplicatedShelf);
    window.BuroApp.render();
    var subscriptionRows = window.document.querySelectorAll('.subscription-row');
    var shelfOrigin = subscriptionRows[1].querySelector('[data-action="subscription-title"]');
    window.document.querySelector('.content.scrollable').scrollTop = 91;
    subscriptionRows[0].scrollLeft = 7;
    subscriptionRows[1].scrollLeft = 24;
    window.BuroApp._activate(shelfOrigin);
    await waitFor(function () { return window.document.querySelector('[data-action="subscription-local"]'); }, 1000);
    check('título de prateleira abre com Ribbon e cabeçalho preservados pelo foco no Voltar',
        window.document.querySelector('[data-action="subscription-back"]').classList.contains('focused') &&
        window.document.querySelector('.content.scrollable').scrollTop === 0);
    check('detalhe cruza o catálogo inteiro e apresenta biblioteca local junto às ofertas externas',
        window.document.querySelector('[data-action="subscription-local"]').getAttribute('data-id') === 'movie:favorite-only' &&
        window.document.querySelectorAll('.subscription-offer').length === 4);
    check('cada oferta externa mostra atribuição JustWatch, marca pública segura e nenhum preço inventado',
        window.document.querySelectorAll('.subscription-offer small').length === 3 &&
        Array.prototype.every.call(window.document.querySelectorAll('.subscription-offer small'), function (node) {
            return node.textContent === 'Streaming data provided by JustWatch';
        }) && window.document.querySelectorAll('.subscription-offer-logo').length === 2 &&
        Array.prototype.every.call(window.document.querySelectorAll('.subscription-offer-logo'), function (node) {
            return node.getAttribute('src').indexOf('https://image.tmdb.org/t/p/w92/') === 0;
        }) && window.document.body.innerHTML.indexOf('https://evil.test/logo.jpg') === -1 &&
        window.document.querySelector('.subscription-offers').textContent.indexOf('R$') === -1);
    var appControlsBeforeOffer = window.__launchedAppControls.length;
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-offer"][data-url^="https://www.netflix.com"]'));
    check('oferta confiável usa ApplicationControl do Tizen',
        window.__launchedAppControls.length === appControlsBeforeOffer + 1 &&
        window.__launchedAppControls[appControlsBeforeOffer].operation === 'http://tizen.org/appcontrol/operation/view' &&
        window.__launchedAppControls[appControlsBeforeOffer].uri.indexOf('https://www.netflix.com/') === 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-offer"][data-url^="https://evil.test"]'));
    check('oferta com domínio não autorizado é bloqueada antes de sair do app',
        window.__launchedAppControls.length === appControlsBeforeOffer + 1);
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-trailer"]'));
    check('detalhe externo reutiliza o trailer incorporado e seguro',
        !window.document.getElementById('trailer-overlay').hidden &&
        window.document.getElementById('trailer-frame').src.indexOf('youtube-nocookie.com/embed/Trailer98765') >= 0 &&
        window.document.getElementById('trailer-overlay').getAttribute('role') === 'dialog' &&
        window.document.getElementById('trailer-timeline').getAttribute('role') === 'progressbar' &&
        window.document.getElementById('app').getAttribute('aria-hidden') === 'true');
    press(window, 10009);
    check('fechar o trailer devolve a árvore acessível ao aplicativo',
        !window.document.getElementById('app').hasAttribute('aria-hidden'));
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-back"]'));
    check('Voltar do título restaura card, rolagem vertical e prateleira horizontal',
        window.document.querySelector('.subscription-row .focused').closest('section').getAttribute('data-provider') === '18' &&
        window.document.querySelector('.content.scrollable').scrollTop === 91 &&
        window.document.querySelectorAll('.subscription-row')[1].scrollLeft === 24);
    window.BuroApp._activate(window.document.querySelectorAll('[data-action="subscription-title"]')[1]);
    await waitFor(function () {
        return window.BuroApp.state.screenData.selected &&
            window.BuroApp.state.screenData.selected.title === 'Filme futuro' &&
            !window.BuroApp.state.screenData.selectionLoading;
    }, 1000);
    var subscriptionReminder = window.document.querySelector('[data-action="subscription-reminder"]');
    check('detalhe externo oferece lembrete mesmo antes de o título existir na lista',
        subscriptionReminder && !window.document.querySelector('[data-action="subscription-local"]') &&
        subscriptionReminder.getAttribute('aria-pressed') === 'false' &&
        subscriptionReminder.textContent.indexOf('Lembrete') >= 0);
    window.BuroApp._activate(subscriptionReminder);
    await waitFor(function () {
        return window.BuroApp.state.reminders.some(function (row) {
            return row.title === 'Filme futuro' && row.identity === 'movie:filme futuro:2099';
        });
    }, 1000);
    subscriptionReminder = window.document.querySelector('[data-action="subscription-reminder"]');
    check('guardar lembrete externo persiste só identidade pública e atualiza o estado do botão',
        subscriptionReminder && subscriptionReminder.getAttribute('aria-pressed') === 'true' &&
        window.BuroApp.state.reminders.some(function (row) {
            return row.title === 'Filme futuro' && row.contentType === 'MOVIE' &&
                row.releaseDate === '2099-06-15' &&
                row.artworkUrl === 'https://image.tmdb.org/t/p/w342/subscription-detail.jpg' &&
                !Object.prototype.hasOwnProperty.call(row, 'url') &&
                !Object.prototype.hasOwnProperty.call(row, 'tmdbKey');
        }));
    window.BuroApp._activate(subscriptionReminder);
    await waitFor(function () {
        return !window.BuroApp.state.reminders.some(function (row) { return row.title === 'Filme futuro'; });
    }, 1000);
    check('segundo clique remove o lembrete externo sem sair da tela',
        window.document.querySelector('[data-action="subscription-reminder"]').getAttribute('aria-pressed') === 'false' &&
        window.BuroApp.state.section === 'SUBSCRIPTIONS' && window.BuroApp.state.screenData.selected);
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-back"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-filter"][data-kind="SERIES"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-region"][data-region="DE"]'));
    check('filtro e região recarregam a descoberta e a região fica persistida',
        subscriptionCalls.some(function (call) { return call.kind === 'SERIES'; }) &&
        subscriptionCalls.some(function (call) { return call.kind === 'SERIES' && call.region === 'DE'; }) &&
        window.BuroApp.state.preferences.tmdbRegion === 'DE');

    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SETTINGS"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-settings"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="tmdb-clear"][data-scope="profile"]'));
    check('remoção limpa somente a chave segura do perfil',
        !Object.keys(window.__secureData).some(function (name) { return name.indexOf('tmdb-profile-') >= 0; }));
    press(window, 10009);
    /* A guia continua ali sem chave — sumir de uma instalação e existir na outra
       fazia a mesma função parecer defeito. Quem entra encontra a explicação e o
       caminho para configurar, em vez de ser desviado sem aviso. */
    check('Assinaturas continua alcançável sem a chave TMDb',
        window.document.querySelectorAll('.nav-list [data-action="section"]').length === 15 &&
        Boolean(window.document.querySelector('.nav-list [data-section="SUBSCRIPTIONS"]')));
    window.BuroTmdb.loadShelves = originalLoadSubscriptionShelves;
    window.BuroTmdb.loadSubscriptionTitle = originalLoadSubscriptionTitle;
    window.BuroTmdb.loadServiceCatalogue = originalLoadServiceCatalogue;
    window.BuroTmdb.validateKey = originalValidateTmdbKey;

    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="HOME"]'));
    /*
      Leva o foco para fora da Ribbon e depois confere que RETURN o traz de volta
      ao destino atual.

      O número de setas não é fixo de propósito: contar passos amarrava o teste à
      quantidade de seções, e quando Lembretes entrou as mesmas catorze setas
      passaram a parar no botão de perfil — ainda dentro da Ribbon. O teste
      continuava verde por acidente em vez de exercitar a volta do conteúdo,
      então agora ele anda até de fato sair da Ribbon e falha se não conseguir.
    */
    for (var left = 0; left < 40; left += 1) { press(window, 37); }
    for (var right = 0; right < 60; right += 1) {
        if (!window.document.querySelector('.buro-ribbon .focused')) { break; }
        press(window, 39);
    }
    check('o foco chega ao conteúdo, fora da Ribbon',
        !window.document.querySelector('.buro-ribbon .focused'));
    press(window, 10009);
    check('RETURN devolve o foco do conteúdo ao destino selecionado da Ribbon',
        window.document.querySelector('.nav-list [data-section="HOME"]').classList.contains('focused'));

    /*
      Sem tizen.filesystem nesta suíte, o USB nunca está montado — que é
      exatamente o estado da maioria das TVs. A seção precisa explicar o que
      falta em vez de mostrar uma lista vazia sem motivo.
    */
    (function () {
        var sections = Array.prototype.slice.call(
            window.document.querySelectorAll('[data-action="section"]')
        );
        var downloads = sections.filter(function (node) {
            return node.getAttribute('data-section') === 'DOWNLOADS';
        })[0];
        check('a seção Downloads existe no menu', Boolean(downloads));
        if (!downloads) { return; }
        window.BuroApp._activate(downloads);
        check('sem USB, a seção Downloads explica que falta um dispositivo',
            window.document.body.textContent.indexOf(window.BuroI18n.t('usbRequired')) >= 0);
        check('sem USB, o botão Baixar não é oferecido',
            !window.document.querySelector('[data-action="download"]'));
    }());

    window.BuroApp._activate(window.document.querySelector('.nav-list [data-section="SEARCH"]'));
    var originalSearchPage = window.BuroStorage.searchPage;
    var automaticSearchCalls = 0;
    var automaticSearchInput = window.document.getElementById('search-query');
    window.BuroStorage.searchPage = function () {
        automaticSearchCalls += 1;
        originalSearchPage.apply(window.BuroStorage, arguments);
    };
    automaticSearchInput.value = 'Favorite';
    automaticSearchInput.dispatchEvent(new window.Event('input', { bubbles: true }));
    await new Promise(function (resolve) { window.setTimeout(resolve, 100); });
    automaticSearchInput.value = 'Favorite only';
    automaticSearchInput.dispatchEvent(new window.Event('input', { bubbles: true }));
    await new Promise(function (resolve) { window.setTimeout(resolve, 220); });
    check('digitação não consulta o catálogo antes dos 300 ms do Android', automaticSearchCalls === 0);
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.query === 'Favorite only' &&
            !window.BuroApp.state.screenData.searching;
    }, 1000);
    check('pesquisa automática combina a última digitação em uma única consulta', automaticSearchCalls === 1);
    window.BuroStorage.searchPage = originalSearchPage;
    check('a pesquisa encontra item persistido que não estava na amostra inicial',
        window.document.body.textContent.indexOf('Favorite only') >= 0);
    var pagedSearchRows = [];
    for (var searchIndex = 0; searchIndex < 45; searchIndex += 1) {
        pagedSearchRows.push({
            id: 'movie:paged-' + ('00' + searchIndex).slice(-2), sourceId: 'source-public',
            contentType: 'MOVIE', name: 'Paged Result ' + ('00' + searchIndex).slice(-2)
        });
    }
    await Promise.all(pagedSearchRows.map(function (row) {
        return new Promise(function (resolve, reject) { window.BuroStorage.put('items', row, resolve, reject); });
    }));
    window.document.getElementById('search-query').value = 'Paged Result';
    window.BuroApp._activate(window.document.querySelector('[data-action="search-run"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="search-next"]'); }, 1000);
    check('busca pagina o catálogo sem materializar todos os resultados',
        window.document.querySelectorAll('.media-card').length === 40 && window.BuroApp.state.screenData.page === 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="search-next"]'));
    await waitFor(function () { return window.document.querySelector('[data-action="search-previous"]') && !window.document.querySelector('.search-loading'); }, 1000);
    check('próxima página mostra os resultados restantes e permite voltar',
        window.document.querySelectorAll('.media-card').length === 5 && window.BuroApp.state.screenData.page === 1);
    window.BuroApp._activate(window.document.querySelector('[data-action="search-previous"]'));
    await waitFor(function () { return window.BuroApp.state.screenData.page === 0 && !window.BuroApp.state.screenData.searching; }, 1000);
    window.document.getElementById('search-query').value = 'Título que não existe';
    window.BuroApp._activate(window.document.querySelector('[data-action="search-run"]'));
    await waitFor(function () { return !window.BuroApp.state.screenData.searching; }, 1000);
    check('zero resultados tem mensagem própria, sem cartão genérico de erro',
        window.document.body.textContent.indexOf(window.BuroI18n.t('searchEmpty')) >= 0 &&
        !window.document.querySelector('.media-card'));

    var originalSearchPageForStale = window.BuroStorage.searchPage;
    var pendingSearches = [];
    window.BuroStorage.searchPage = function (query, predicate, cursor, limit, success, failure) {
        pendingSearches.push({ success: success, failure: failure });
    };
    window.document.getElementById('search-query').value = 'Busca antiga';
    window.BuroApp._activate(window.document.querySelector('[data-action="search-run"]'));
    window.document.getElementById('search-query').value = 'Busca nova';
    window.BuroApp._activate(window.document.querySelector('[data-action="search-run"]'));
    pendingSearches[0].success({ rows: [{ id: 'old', name: 'Resultado antigo', contentType: 'MOVIE' }], hasMore: false });
    pendingSearches[1].failure({ code: 'DATABASE_REQUEST_FAILED' });
    check('resposta de busca antiga é descartada e falha atual oferece retry',
        window.document.body.textContent.indexOf('Resultado antigo') === -1 &&
        Boolean(window.document.querySelector('[data-action="search-retry"]')));
    window.BuroStorage.searchPage = originalSearchPageForStale;

    process.stdout.write('Living Home com catálogo real\n');
    var homeYear = new Date().getFullYear();
    var homeNow = Date.now();
    window.BuroApp.state.sources = [{ id: 'source-home', name: 'Fonte de teste', type: 'REMOTE_M3U' }];
    window.BuroApp.state.activeSource = window.BuroApp.state.sources[0];
    window.BuroApp.state.categories = [
        { id: 'cat-home-movies', sourceId: 'source-home', contentType: 'MOVIE', name: 'Filmes' },
        { id: 'cat-home-series', sourceId: 'source-home', contentType: 'SERIES', name: 'Séries' },
        { id: 'cat-home-live', sourceId: 'source-home', contentType: 'LIVE', name: 'Ao vivo' },
        { id: 'cat-home-hidden', sourceId: 'source-home', contentType: 'MOVIE', name: 'Oculta' },
        { id: 'cat-home-adult', sourceId: 'source-home', contentType: 'MOVIE', name: 'Adultos XXX' }
    ];
    window.BuroApp.state.items = [
        { id: 'movie:home-hero', sourceId: 'source-home', categoryId: 'cat-home-movies', contentType: 'MOVIE',
            name: 'Filme destaque', year: homeYear, rating: 8.2, addedAt: homeNow - 5000, sortOrder: 1 },
        { id: 'movie:home-two', sourceId: 'source-home', categoryId: 'cat-home-movies', contentType: 'MOVIE',
            name: 'Filme dois', year: homeYear - 2, rating: 7.1, addedAt: homeNow - 4000, sortOrder: 2 },
        { id: 'series:home', sourceId: 'source-home', categoryId: 'cat-home-series', contentType: 'SERIES',
            name: 'Série casa', year: homeYear - 1, rating: 8.8, addedAt: homeNow - 3000, sortOrder: 1 },
        { id: 'movie:home-classic', sourceId: 'source-home', categoryId: 'cat-home-movies', contentType: 'MOVIE',
            name: 'Clássico recém-chegado', year: homeYear - 20, rating: 6.9, addedAt: homeNow - 1000, sortOrder: 3 },
        { id: 'movie:home-recent', sourceId: 'source-home', categoryId: 'cat-home-movies', contentType: 'MOVIE',
            name: 'Filme recém-chegado', year: homeYear - 5, rating: 6.5, addedAt: homeNow - 2000, sortOrder: 4 },
        { id: 'live:home', sourceId: 'source-home', categoryId: 'cat-home-live', contentType: 'LIVE',
            name: 'Canal casa', addedAt: homeNow, sortOrder: 1 }
    ];
    var fullHomeItem = {
        id: 'movie:home-full-db', sourceId: 'source-home', categoryId: 'cat-home-movies', contentType: 'MOVIE',
        name: 'Destaque do catálogo inteiro', year: homeYear, rating: 9.9, addedAt: homeNow, sortOrder: 99
    };
    var excludedHomeItems = [
        { id: 'movie:home-hidden', sourceId: 'source-home', categoryId: 'cat-home-hidden', contentType: 'MOVIE',
            name: 'Destaque oculto', year: homeYear, rating: 10.5, addedAt: homeNow + 2000 },
        { id: 'movie:home-adult', sourceId: 'source-home', categoryId: 'cat-home-adult', contentType: 'MOVIE',
            name: 'Destaque adulto', year: homeYear, rating: 10.4, addedAt: homeNow + 1000 },
        { id: 'movie:home-other-source', sourceId: 'source-other', contentType: 'MOVIE',
            name: 'Destaque de outra fonte', year: homeYear, rating: 11, addedAt: homeNow + 3000 }
    ];
    await Promise.all(window.BuroApp.state.categories.map(function (row) {
        return new Promise(function (resolve, reject) { window.BuroStorage.put('categories', row, resolve, reject); });
    }));
    await Promise.all(window.BuroApp.state.items.concat([fullHomeItem]).concat(excludedHomeItems).map(function (row) {
        return new Promise(function (resolve, reject) { window.BuroStorage.put('items', row, resolve, reject); });
    }));
    window.BuroApp.state.preferences.hiddenCategoryIds = ['cat-home-hidden'];
    window.BuroApp.state.activeProfile.isKids = true;
    window.BuroApp.state.favorites = [{
        id: 'favorite:home', profileId: window.BuroApp.state.activeProfile.id, itemId: 'movie:home-two'
    }];
    window.BuroApp.state.progress = [{
        id: 'progress:home', profileId: window.BuroApp.state.activeProfile.id, itemId: 'movie:home-two',
        completed: false, updatedAt: Date.now(), positionMs: 30000, durationMs: 120000
    }];
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('a Home mostra o cache imediatamente enquanto consulta o catálogo inteiro',
        window.document.querySelector('.real-home-hero h2').textContent === 'Filme destaque' &&
        Boolean(window.document.querySelector('.home-status.loading')));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            !window.BuroApp.state.screenData.loading;
    }, 1000);
    check('a Home escolhe destaque presente somente no IndexedDB completo, fora da amostra do boot',
        window.document.querySelector('.real-home-hero h2').textContent === 'Destaque do catálogo inteiro' &&
        window.BuroApp.state.items.some(function (row) { return row.id === fullHomeItem.id; }));
    check('a consulta integral respeita fonte ativa, categorias ocultas e perfil Kids',
        window.document.body.textContent.indexOf('Destaque oculto') === -1 &&
        window.document.body.textContent.indexOf('Destaque adulto') === -1 &&
        window.document.body.textContent.indexOf('Destaque de outra fonte') === -1);
    check('a composição replica continuidade, lançamentos, clássicos e recentes do Android',
        Boolean(window.document.querySelector('[data-home-rail="continue"]')) &&
        Boolean(window.document.querySelector('[data-home-rail="releases-current"]')) &&
        Boolean(window.document.querySelector('[data-home-rail="releases-previous"]')) &&
        Boolean(window.document.querySelector('[data-home-rail="new-classics"]')) &&
        Boolean(window.document.querySelector('[data-home-rail="recent"]')));
    check('Minha BURO e Ao Vivo permanecem destinos próprios e não substituem os trilhos editoriais da Home',
        !window.document.querySelector('[data-home-rail="favorites"]') &&
        !window.document.querySelector('[data-home-rail="live"]') &&
        window.document.body.textContent.indexOf('Canal casa') === -1);
    check('cards da Home mostram o progresso real de retomada',
        parseFloat(window.document.querySelector('[data-id="movie:home-two"] .media-progress i').style.width) === 25);
    var originalWindowSetTimeout = window.setTimeout;
    var homeRotationCallback;
    window.setTimeout = function (callback, delay) {
        if (delay === 10000) { homeRotationCallback = callback; return 4242; }
        return originalWindowSetTimeout(callback, delay);
    };
    window.BuroApp.render();
    window.setTimeout = originalWindowSetTimeout;
    var firstHomeHero = window.document.querySelector('.real-home-hero h2').textContent;
    homeRotationCallback();
    check('hero diário gira entre destaques sem tirar o foco da Ribbon',
        window.document.querySelector('.real-home-hero h2').textContent !== firstHomeHero &&
        window.document.querySelector('[data-action="section"][data-section="HOME"]').classList.contains('focused'));

    var originalHomeFold = window.BuroStorage.fold;
    window.BuroStorage.fold = function (storeName, reducer, initial, success, failure) {
        window.setTimeout(function () { failure({ code: 'DATABASE_REQUEST_FAILED' }); }, 5);
    };
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () { return window.BuroApp.state.screenData && window.BuroApp.state.screenData.error; }, 1000);
    check('falha ao recompor a Home conserva o cache visível e oferece Retry pelo D-pad',
        Boolean(window.document.querySelector('.home-cache-warning [data-action="home-retry"]')) &&
        Boolean(window.document.querySelector('.real-home-hero')));
    window.BuroStorage.fold = originalHomeFold;
    window.BuroApp._activate(window.document.querySelector('[data-action="home-retry"]'));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            !window.BuroApp.state.screenData.loading;
    }, 1000);

    var pendingHomeFold;
    window.BuroStorage.fold = function (storeName, reducer, initial, success, failure) {
        pendingHomeFold = { reducer: reducer, result: initial, success: success };
    };
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () { return Boolean(pendingHomeFold); }, 1000);
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="LIVE"]'));
    pendingHomeFold.success(pendingHomeFold.reducer(pendingHomeFold.result, {
        id: 'movie:home-late', sourceId: 'source-home', categoryId: 'cat-home-movies',
        contentType: 'MOVIE', name: 'Home atrasada', year: homeYear, rating: 10, addedAt: homeNow + 1000
    }));
    check('resposta atrasada da Home não substitui a seção aberta pelo usuário',
        window.BuroApp.state.section === 'LIVE' && window.document.body.textContent.indexOf('Home atrasada') === -1);
    window.BuroStorage.fold = originalHomeFold;
    await Promise.all(excludedHomeItems.map(function (row) {
        return new Promise(function (resolve, reject) { window.BuroStorage.remove('items', row.id, resolve, reject); });
    }));
    window.BuroApp.state.preferences.hiddenCategoryIds = [];
    window.BuroApp.state.activeProfile.isKids = false;

    process.stdout.write('Estados assíncronos de catálogo\n');
    var originalLoadItems = window.BuroXtream.loadItems;
    var catalogueRequests = [];
    var asyncCategory = { id: 'cat-async-empty', sourceId: 'source-home', providerCategoryId: 'empty', contentType: 'MOVIE', name: 'Categoria assíncrona' };
    var staleCategory = { id: 'cat-async-stale', sourceId: 'source-home', providerCategoryId: 'stale', contentType: 'MOVIE', name: 'Categoria atrasada' };
    window.BuroApp.state.categories.push(asyncCategory, staleCategory);
    window.BuroApp.state.sources[0].type = 'XTREAM';
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', {
            server: 'https://provider.test', username: 'synthetic', password: 'synthetic'
        }, resolve, reject);
    });
    window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success, failure) {
        catalogueRequests.push({ category: category, success: success, failure: failure });
    };
    window.BuroApp.state.section = 'MOVIES'; window.BuroApp.state.screenData = null; window.BuroApp.render();
    window.BuroApp._openCategory('cat-async-empty');
    check('abrir categoria mostra skeleton imediatamente, sem depender de toast',
        Boolean(window.document.querySelector('.catalogue-loading')) &&
        window.document.querySelectorAll('.catalogue-skeleton-card').length === 8 &&
        window.document.querySelector('.catalogue-loading').getAttribute('role') === 'status' &&
        window.document.querySelector('.catalogue-skeleton-row').getAttribute('aria-hidden') === 'true' &&
        window.document.querySelector('.main-pane').getAttribute('aria-busy') === 'true');
    await waitFor(function () { return catalogueRequests.length === 1; }, 1000);
    catalogueRequests[0].failure({ code: 'NETWORK_ERROR' });
    check('falha de categoria permanece visível e oferece nova tentativa por D-pad',
        Boolean(window.document.querySelector('.catalogue-error, .empty-state')) &&
        Boolean(window.document.querySelector('[data-action="catalogue-retry"]')) &&
        window.document.querySelector('.catalogue-error').getAttribute('role') === 'alert' &&
        window.document.querySelector('.main-pane').getAttribute('aria-busy') === 'false');
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-retry"]'));
    await waitFor(function () { return catalogueRequests.length === 2; }, 1000);
    catalogueRequests[1].success([], {});
    await waitFor(function () { return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'category'; }, 1000);
    check('resposta vazia é apresentada como categoria vazia, não como erro',
        window.document.body.textContent.indexOf(window.BuroI18n.t('noItems')) >= 0 &&
        !window.document.querySelector('[data-action="catalogue-retry"]'));
    check('categoria Xtream carregada oferece atualização explícita pelo D-pad',
        Boolean(window.document.querySelector('[data-action="category-refresh"]')));

    var categoryKeep = { id: 'movie:category-keep', sourceId: 'source-home', categoryId: asyncCategory.id, contentType: 'MOVIE', name: 'Título estável' };
    var categoryRemove = { id: 'movie:category-remove', sourceId: 'source-home', categoryId: asyncCategory.id, contentType: 'MOVIE', name: 'Título removido' };
    window.BuroApp._activate(window.document.querySelector('[data-action="category-refresh"]'));
    await waitFor(function () { return catalogueRequests.length === 3; }, 1000);
    catalogueRequests[2].success([categoryKeep, categoryRemove], {});
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'category' &&
            window.BuroApp.state.screenData.items.length === 2;
    }, 1000);
    var categoryFavorite = { id: 'favorite:category-remove', profileId: window.BuroApp.state.activeProfile.id, itemId: categoryRemove.id };
    var categoryProgress = { id: 'progress:category-remove', profileId: window.BuroApp.state.activeProfile.id, itemId: categoryRemove.id, positionMs: 30000, durationMs: 120000 };
    window.BuroApp.state.favorites.push(categoryFavorite);
    window.BuroApp.state.progress.push(categoryProgress);
    await new Promise(function (resolve, reject) { window.BuroStorage.put('favorites', categoryFavorite, resolve, reject); });
    await new Promise(function (resolve, reject) { window.BuroStorage.put('progress', categoryProgress, resolve, reject); });
    var categoryKeepUpdated = Object.assign({}, categoryKeep, { name: 'Título estável atualizado' });
    window.BuroApp._activate(window.document.querySelector('[data-action="category-refresh"]'));
    await waitFor(function () { return catalogueRequests.length === 4; }, 1000);
    catalogueRequests[3].success([categoryKeepUpdated], {});
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'category' &&
            window.BuroApp.state.screenData.items[0] && window.BuroApp.state.screenData.items[0].name === 'Título estável atualizado';
    }, 1000);
    var removedCategoryItem;
    await new Promise(function (resolve, reject) {
        window.BuroStorage.get('items', categoryRemove.id, function (row) { removedCategoryItem = row; resolve(); }, reject);
    });
    check('atualização substitui a categoria inteira e remove somente item ausente',
        !removedCategoryItem && window.BuroApp.state.items.some(function (row) { return row.id === categoryKeep.id; }));
    check('referências do item removido são limpas junto da mesma transação',
        !window.BuroApp.state.favorites.some(function (row) { return row.id === categoryFavorite.id; }) &&
        !window.BuroApp.state.progress.some(function (row) { return row.id === categoryProgress.id; }));

    window.BuroApp._activate(window.document.querySelector('[data-action="category-refresh"]'));
    await waitFor(function () { return catalogueRequests.length === 5; }, 1000);
    catalogueRequests[4].failure({ code: 'NETWORK_ERROR' });
    check('falha de atualização mantém o cache visível com aviso e Retry',
        window.BuroApp.state.screenData.kind === 'category' && window.BuroApp.state.screenData.refreshError &&
        window.document.body.textContent.indexOf('Título estável atualizado') >= 0 &&
        Boolean(window.document.querySelector('.category-refresh-warning [data-action="category-refresh"]')));

    window.BuroApp.state.screenData = null; window.BuroApp.render();
    window.BuroApp._openCategory('cat-async-stale');
    await waitFor(function () { return catalogueRequests.length === 6; }, 1000);
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="HOME"]'));
    catalogueRequests[5].success([{
        id: 'movie:late-response', sourceId: 'source-home', categoryId: 'cat-async-stale',
        contentType: 'MOVIE', name: 'Resposta atrasada'
    }], {});
    var latePersisted;
    await new Promise(function (resolve, reject) {
        window.BuroStorage.get('items', 'movie:late-response', function (row) { latePersisted = row; resolve(); }, reject);
    });
    check('resposta atrasada não substitui a Home nem repopula catálogo após sair da tela',
        window.BuroApp.state.section === 'HOME' && !latePersisted &&
        window.document.body.textContent.indexOf('Resposta atrasada') === -1);
    window.BuroXtream.loadItems = originalLoadItems;
    window.BuroStorage.secureRemove('source-home');
    window.BuroApp.state.sources[0].type = 'REMOTE_M3U';

    process.stdout.write('Descobrir no catálogo autorizado\n');
    var discoverRows = [
        { id: 'movie:discover-new', sourceId: 'source-home', categoryId: 'cat-discover', contentType: 'MOVIE', name: 'Filme mais novo', year: 2025, rating: 7.5 },
        { id: 'series:discover-top', sourceId: 'source-home', categoryId: 'cat-discover', contentType: 'SERIES', name: 'Série melhor nota', year: 2023, rating: 9.6 },
        { id: 'live:discover', sourceId: 'source-home', categoryId: 'cat-discover', contentType: 'LIVE', name: 'Canal autorizado' },
        { id: 'movie:discover-hidden', sourceId: 'source-home', categoryId: 'cat-hidden', contentType: 'MOVIE', name: 'Título oculto', year: 2026, rating: 10 },
        { id: 'movie:discover-locked', sourceId: 'source-home', categoryId: 'cat-locked', contentType: 'MOVIE', name: 'Título bloqueado', year: 2026, rating: 10 },
        { id: 'movie:discover-other', sourceId: 'source-other', categoryId: 'cat-other', contentType: 'MOVIE', name: 'Outra fonte', year: 2026, rating: 10 }
    ];
    window.BuroApp.state.categories.push(
        { id: 'cat-discover', sourceId: 'source-home', contentType: 'MOVIE', name: 'Catálogo autorizado' },
        { id: 'cat-hidden', sourceId: 'source-home', contentType: 'MOVIE', name: 'Oculta' },
        { id: 'cat-locked', sourceId: 'source-home', contentType: 'MOVIE', name: 'Bloqueada' }
    );
    window.BuroApp.state.preferences.hiddenCategoryIds = ['cat-hidden'];
    window.BuroApp.state.preferences.lockedCategoryIds = ['cat-locked'];
    window.BuroApp.state.preferences.parentalPin = { salt: 'fixture', hash: 'fixture' };
    await Promise.all(discoverRows.map(function (row) {
        return new Promise(function (resolve, reject) { window.BuroStorage.put('items', row, resolve, reject); });
    }));
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="DISCOVER"]'));
    check('Descobrir apresenta estado de carregamento durante a leitura por cursor',
        Boolean(window.document.querySelector('.search-loading')));
    await waitFor(function () { return window.document.querySelector('.discover-intro'); }, 1000);
    check('Descobrir apresenta uma carta por vez, a próxima em profundidade e uma mão finita',
        window.document.querySelectorAll('.discover-card.current').length === 1 &&
        window.document.querySelectorAll('.discover-card.next').length <= 1 &&
        window.BuroApp.state.screenData.deck.length <= 15 &&
        Boolean(window.document.querySelector('.discover-counter')) &&
        window.document.querySelectorAll('.home-rail-heading').length === 0);
    check('a próxima carta fica fora da árvore acessível até assumir o primeiro plano',
        !window.document.querySelector('.discover-card.next') ||
        window.document.querySelector('.discover-card.next').getAttribute('aria-hidden') === 'true');
    check('Descobrir oferece Pular, Guardar e Detalhes pelo D-pad',
        Boolean(window.document.querySelector('[data-action="discover-skip"]')) &&
        Boolean(window.document.querySelector('[data-action="discover-keep"]')) &&
        Boolean(window.document.querySelector('[data-action="discover-details"]')));
    check('Descobrir respeita fonte, visibilidade, PIN, histórico e exclui TV ao vivo',
        window.document.body.textContent.indexOf('Título oculto') === -1 &&
        window.document.body.textContent.indexOf('Título bloqueado') === -1 &&
        window.document.body.textContent.indexOf('Outra fonte') === -1 &&
        window.document.body.textContent.indexOf('Canal autorizado') === -1 &&
        window.document.body.textContent.indexOf('Filme dois') === -1);
    var skippedDiscoverId = window.document.querySelector('.discover-card.current').getAttribute('data-id');
    var discoverLengthBeforeSkip = window.BuroApp.state.screenData.deck.length;
    window.BuroApp._activate(window.document.querySelector('[data-action="discover-skip"]'));
    check('Pular avança a carta sem criar favorito',
        window.BuroApp.state.screenData.deck.length === discoverLengthBeforeSkip - 1 &&
        !window.BuroApp.state.favorites.some(function (row) { return row.itemId === skippedDiscoverId; }) &&
        window.document.querySelector('.discover-card.current').getAttribute('data-id') !== skippedDiscoverId);
    var keptDiscoverId = window.document.querySelector('.discover-card.current').getAttribute('data-id');
    window.BuroApp._activate(window.document.querySelector('[data-action="discover-keep"]'));
    await waitFor(function () {
        return window.BuroApp.state.favorites.some(function (row) { return row.itemId === keptDiscoverId; }) &&
            window.document.querySelector('.discover-card.current') &&
            window.document.querySelector('.discover-card.current').getAttribute('data-id') !== keptDiscoverId;
    }, 1000);
    check('Guardar persiste o favorito e só então avança a carta',
        window.BuroApp.state.favorites.some(function (row) { return row.itemId === keptDiscoverId; }));
    var detailedDiscoverId = window.document.querySelector('.discover-card.current').getAttribute('data-id');
    window.BuroApp._activate(window.document.querySelector('[data-action="discover-details"]'));
    check('Detalhes abre a página normal sem julgar a carta',
        window.BuroApp.state.section === 'MOVIES' && window.BuroApp.state.screenData.kind === 'movie');
    press(window, 10009);
    await waitFor(function () { return window.BuroApp.state.section === 'DISCOVER' && window.document.querySelector('.discover-intro'); }, 1000);
    check('RETURN restaura a mesma carta e o foco em Detalhes',
        window.document.querySelector('.discover-card.current').getAttribute('data-id') === detailedDiscoverId &&
        window.document.querySelector('[data-action="discover-details"]').classList.contains('focused'));
    while (window.document.querySelector('[data-action="discover-skip"]')) {
        window.BuroApp._activate(window.document.querySelector('[data-action="discover-skip"]'));
    }
    check('ao terminar a mão a tela anuncia o fim e oferece uma nova rodada',
        !window.document.querySelector('.discover-card.current') &&
        Boolean(window.document.querySelector('[data-action="discover-again"]')) &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('discoverExhausted')) >= 0);
    check('decisões de Descobrir permanecem somente na memória da sessão',
        window.localStorage.getItem('iptvburo.preferences.v1').indexOf(skippedDiscoverId) === -1);
    window.BuroApp._activate(window.document.querySelector('[data-action="discover-again"]'));
    check('Nova rodada volta ao estado de carregamento explícito', Boolean(window.document.querySelector('.search-loading')));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'discover' &&
            !window.BuroApp.state.screenData.loading;
    }, 1000);
    check('a rodada seguinte não oferece novamente o que já foi julgado na sessão',
        !window.BuroApp.state.screenData.deck.some(function (row) { return row.id === skippedDiscoverId; }));
    window.BuroApp.state.preferences.hiddenCategoryIds = [];
    window.BuroApp.state.preferences.lockedCategoryIds = [];
    window.BuroApp.state.preferences.parentalPin = null;
    window.BuroApp.state.section = 'HOME'; window.BuroApp.state.screenData = null; window.BuroApp.render();
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'home' &&
            !window.BuroApp.state.screenData.loading;
    }, 1000);

    var originalLoadMovieDetails = window.BuroXtream.loadMovieDetails;
    var originalLoadTmdbTitle = window.BuroTmdb.loadTitle;
    var originalLoadTmdbPerson = window.BuroTmdb.loadPerson;
    window.BuroApp.state.sources[0].type = 'XTREAM';
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', {
            server: 'https://provider.test', username: 'synthetic', password: 'synthetic'
        }, resolve, reject);
    });
    var movieDetailRequests = [];
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave(window.BuroTmdb.profileSecretId(window.BuroApp.state.activeProfile.id),
            { apiKey: syntheticTmdbKey }, resolve, reject);
    });
    window.BuroTmdb.loadTitle = function (key, item, isSeries, locale, success) {
        success({
            tmdbId: 42, title: 'Título TMDb', plot: 'Sinopse TMDb', backdropUrl: 'https://image.tmdb.org/t/p/w1280/tmdb.jpg',
            posterUrl: 'https://image.tmdb.org/t/p/w342/tmdb.jpg', genre: 'Aventura', duration: 122,
            youtubeTrailerId: 'TmdbTrailer9', castMembers: [
                { id: 7, name: 'Ana Exemplo', character: 'Lia', photoUrl: 'https://image.tmdb.org/t/p/w185/ana.jpg' },
                { id: 8, name: 'Bruno Exemplo', character: 'Caio', photoUrl: null }
            ]
        });
        return { abort: function () {} };
    };
    window.BuroXtream.loadMovieDetails = function (secret, item, success, failure) {
        movieDetailRequests.push({ success: success, failure: failure });
    };
    var homeBeforeLocalDetails = window.BuroApp.state.screenData;
    var homeDetailOrigin = window.document.querySelector('.real-home-hero [data-action="movie-details"]');
    var homeScrollBeforeDetails = window.document.querySelector('.content');
    homeScrollBeforeDetails.scrollTop = 480;
    window.BuroApp._activate(homeDetailOrigin);
    check('detalhes de filme exibem estado de carregamento próprio', Boolean(window.document.querySelector('.catalogue-loading')));
    movieDetailRequests[0].failure({ code: 'NETWORK_ERROR' });
    check('falha nos detalhes oferece retry sem voltar silenciosamente ao catálogo',
        Boolean(window.document.querySelector('[data-action="catalogue-retry"]')) && window.BuroApp.state.screenData.kind === 'catalogue-error');
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-retry"]'));
    var detailedMovieId = window.BuroApp.state.screenData.parent.id;
    window.BuroApp.state.progress.push({
        id: 'progress:details-movie', profileId: window.BuroApp.state.activeProfile.id, itemId: detailedMovieId,
        completed: false, updatedAt: Date.now() + 1000, positionMs: 2700000, durationMs: 5400000
    });
    movieDetailRequests[1].success({
        title: 'Filme destaque', plot: 'Fixture pública', genre: 'Drama', director: 'Diretora Teste',
        cast: 'Ana Exemplo, Bruno Exemplo; Ana Exemplo', duration: '5400', releaseDate: '2025',
        country: 'Brasil', rating: 8.7, youtubeTrailerId: 'AbCdEf12345'
    }, 'https://images.public.test/poster.jpg?sig=synthetic',
    'https://images.public.test/backdrop.jpg?sig=synthetic');
    check('backdrop remoto aparece no detalhe com pôster de fallback e somente na memória da sessão',
        window.document.querySelector('.detail-art img').getAttribute('src').indexOf('backdrop.jpg') >= 0 &&
        window.document.querySelector('.detail-art img').getAttribute('data-artwork-fallback').indexOf('poster.jpg') >= 0 &&
        JSON.stringify(window.BuroApp.state.items).indexOf('images.public.test') === -1 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('images.public.test') === -1);
    check('detalhe de filme replica fatos e progresso assistido do Android',
        window.document.querySelectorAll('.detail-fact').length === 4 &&
        window.document.querySelector('.detail-fact.rating').textContent.indexOf('8.7') >= 0 &&
        parseFloat(window.document.querySelector('.detail-watch-progress i').style.width) === 50 &&
        window.document.body.textContent.indexOf('1:30:00') >= 0);
    check('créditos e elenco ganham seções próprias sem duplicar nomes',
        window.document.querySelector('.detail-credit-card').textContent.indexOf('Diretora Teste') >= 0 &&
        window.document.querySelectorAll('.cast-chip').length === 2);
    check('TMDb enriquece o elenco com foto e personagem sem substituir dados válidos da fonte',
        window.document.querySelector('.cast-chip img').src.indexOf('image.tmdb.org') >= 0 &&
        window.document.querySelector('.cast-chip small').textContent === 'Lia' &&
        window.document.body.textContent.indexOf('Fixture pública') >= 0 &&
        window.document.body.textContent.indexOf('Sinopse TMDb') === -1);
    window.BuroApp.state.screenData.details.critics = {
        hasAny: true, tomatometer: 83, imdbRating: 8.7, metascore: 39
    };
    window.BuroApp.render();
    var criticMarks = Array.prototype.slice.call(window.document.querySelectorAll('.critic-mark'));
    check('notas da critica identificam RT IMDb e MC pelas mesmas marcas do Windows',
        criticMarks.length === 3 && criticMarks.map(function (mark) { return mark.textContent; }).join('|') === 'RT|IMDb|MC' &&
        criticMarks[0].style.backgroundColor === 'rgb(250, 50, 10)' &&
        criticMarks[1].style.backgroundColor === 'rgb(245, 197, 24)' &&
        criticMarks[2].style.backgroundColor === 'rgb(255, 104, 116)');
    check('cada selo conserva nome e valor completos para leitores de tela',
        Array.prototype.every.call(window.document.querySelectorAll('.critic-score'), function (cell) {
            return cell.getAttribute('role') === 'group';
        }) && criticMarks.every(function (mark) { return mark.getAttribute('aria-hidden') === 'true'; }) &&
        Array.prototype.slice.call(window.document.querySelectorAll('.critic-score')).map(function (cell) {
            return cell.getAttribute('aria-label');
        }).join('|') === 'Tomatometer: 83%|IMDb: 8.7/10|Metascore: 39');
    check('trailer fornecido pela fonte aparece como ação própria nos detalhes',
        Boolean(window.document.querySelector('[data-action="trailer"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="trailer"]'));
    check('trailer abre incorporado no domínio YouTube sem cookies',
        !window.document.getElementById('trailer-overlay').hidden &&
        window.document.getElementById('trailer-frame').src.indexOf(
            'https://www.youtube-nocookie.com/embed/AbCdEf12345') === 0);
    press(window, 10009);
    check('RETURN fecha somente o trailer e conserva a tela de detalhes',
        window.document.getElementById('trailer-overlay').hidden &&
        window.BuroApp.state.screenData.kind === 'movie');
    check('filme oferece Compartilhar mesmo quando a reprodução e o trailer são ações separadas',
        Boolean(window.document.querySelector('[data-action="share"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="share"]'));
    check('Compartilhar abre QR local navegável e mantém a recomendação pública',
        window.BuroApp.state.screen === 'SHARE' && Boolean(window.document.querySelector('.share-qr')) &&
        window.document.querySelector('[data-share-url]').getAttribute('data-share-url').indexOf(
            'https://iptvburo.pages.dev/t/?') === 0);
    check('QR de compartilhamento não contém fonte, usuário, senha ou URL do stream',
        ['provider.test', 'synthetic', 'username=', 'password=', '.m3u8'].every(function (secret) {
            return window.document.querySelector('[data-share-url]').getAttribute('data-share-url').indexOf(secret) === -1;
        }));
    press(window, 10009);
    check('RETURN do QR restaura os mesmos detalhes do título',
        window.BuroApp.state.screen === 'SHELL' && window.BuroApp.state.screenData.kind === 'movie' &&
        window.BuroApp.state.screenData.parent.id === detailedMovieId);
    var localCreditTitle = window.BuroApp.state.screenData.parent.name;
    var localCreditYear = window.BuroApp.state.screenData.parent.year || null;
    window.BuroTmdb.loadPerson = function (key, name, locale, success) {
        success({
            id: 7, name: name, photoUrl: 'https://image.tmdb.org/t/p/w342/person.jpg',
            biography: 'Biografia pública para a TV.', birthday: '1990-01-02', placeOfBirth: 'Brasil',
            credits: [{ id: 42, isSeries: false, title: localCreditTitle,
                year: localCreditYear,
                posterUrl: 'https://image.tmdb.org/t/p/w185/credit.jpg', character: 'Lia', popularity: 10 },
            { id: 77, isSeries: true, title: 'Série externa', year: 2026,
                posterUrl: 'https://image.tmdb.org/t/p/w185/external-credit.jpg', character: 'Maya', popularity: 9 }]
        });
        return { abort: function () {} };
    };
    window.BuroApp._activate(window.document.querySelector('[data-action="person"]'));
    check('selecionar integrante abre retrato, biografia e filmografia por D-pad',
        window.BuroApp.state.screen === 'PERSON' && Boolean(window.document.querySelector('.person-portrait')) &&
        window.document.body.textContent.indexOf('Biografia pública para a TV.') >= 0 &&
        window.document.querySelectorAll('.person-credit').length === 2);
    await waitFor(function () { return window.document.querySelector('[data-action="person-local"]'); }, 1000);
    check('filmografia cruza todo o IndexedDB e marca título disponível no catálogo',
        window.document.querySelector('[data-action="person-local"]').getAttribute('data-id') === detailedMovieId &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('personInLibrary')) >= 0);
    var subscriptionTitleDuringPerson = window.BuroTmdb.loadSubscriptionTitle;
    window.BuroTmdb.loadSubscriptionTitle = function (key, title, region, locale, success) {
        success({ details: { tmdbId: title.tmdbId, title: title.title, plot: 'Crédito externo público' },
            offers: [], unknown: true });
        return { abort: function () {} };
    };
    window.BuroApp._activate(window.document.querySelector('[data-action="person-credit"][data-id="77"]'));
    check('crédito externo da filmografia abre o mesmo detalhe de Assinaturas',
        window.BuroApp.state.screen === 'SHELL' && window.BuroApp.state.section === 'SUBSCRIPTIONS' &&
        window.BuroApp.state.screenData.selected.tmdbId === 77 &&
        Boolean(window.document.querySelector('[data-action="subscription-back"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="subscription-back"]'));
    check('Voltar do crédito externo restaura a pessoa e sua filmografia',
        window.BuroApp.state.screen === 'PERSON' &&
        Boolean(window.document.querySelector('[data-action="person-credit"][data-id="77"]')));
    window.BuroTmdb.loadSubscriptionTitle = subscriptionTitleDuringPerson;
    press(window, 10009);
    check('RETURN da pessoa restaura os mesmos detalhes de filme',
        window.BuroApp.state.screen === 'SHELL' && window.BuroApp.state.screenData.kind === 'movie' &&
        window.BuroApp.state.screenData.parent.id === detailedMovieId);
    /* A chave foi acrescentada artificialmente já depois de abrir esta Home,
       apenas para exercitar pessoa/filmografia. Removê-la antes de voltar evita
       introduzir na mesma asserção uma consulta pública que não existia na
       fotografia de origem. */
    window.BuroTmdb.remove('profile', window.BuroApp.state.activeProfile.id);
    press(window, 10009);
    var restoredHomeDetailOrigin = window.document.querySelector('.content .focused');
    check('RETURN do detalhe local reutiliza o mesmo modelo da Home sem outro cursor',
        window.BuroApp.state.section === 'HOME' &&
        window.BuroApp.state.screenData === homeBeforeLocalDetails &&
        !window.BuroApp.state.screenData.loading);
    check('RETURN do detalhe local conserva a rolagem vertical da Home',
        window.document.querySelector('.content').scrollTop === 480);
    check('RETURN do detalhe local restaura o mesmo destaque da Home',
        restoredHomeDetailOrigin &&
        restoredHomeDetailOrigin.getAttribute('data-action') === homeDetailOrigin.getAttribute('data-action') &&
        restoredHomeDetailOrigin.getAttribute('data-id') === homeDetailOrigin.getAttribute('data-id'));
    window.BuroXtream.loadMovieDetails = originalLoadMovieDetails;
    window.BuroTmdb.loadTitle = originalLoadTmdbTitle;
    window.BuroTmdb.loadPerson = originalLoadTmdbPerson;
    window.BuroStorage.secureRemove('source-home');
    window.BuroApp.state.sources[0].type = 'REMOTE_M3U';

    process.stdout.write('Detalhes e guia ao vivo\n');
    var guideNow = Math.floor(Date.now() / 1000);
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = {
        kind: 'live', parent: { id: 'live:guide', name: 'Canal teste', sourceId: 'source-home', contentType: 'LIVE' },
        schedule: [
            { title: 'Programa encerrado', startEpochSeconds: guideNow - 7200, endEpochSeconds: guideNow - 3600 },
            { title: 'Programa atual', description: 'Ao vivo agora', startEpochSeconds: guideNow - 300, endEpochSeconds: guideNow + 300 },
            { title: 'Próximo programa', startEpochSeconds: guideNow + 300, endEpochSeconds: guideNow + 1800 }
        ]
    };
    window.BuroApp.render();
    check('guia marca AGORA pelo intervalo real, não pela primeira posição',
        window.document.querySelectorAll('.epg-row[aria-current="true"]').length === 1 &&
        window.document.querySelector('.epg-row[aria-current="true"] strong').textContent === 'Programa atual' &&
        window.document.querySelector('.epg-row').classList.contains('past'));
    check('programa atual aparece no hero com intervalo e progresso temporal',
        window.document.querySelector('.live-now strong').textContent === 'Programa atual' &&
        parseFloat(window.document.querySelector('.epg-progress i').style.width) >= 49 &&
        parseFloat(window.document.querySelector('.epg-progress i').style.width) <= 51);

    process.stdout.write('Foco dos seletores de catálogo\n');
    var scopeFixtureIds = ['cat-scope-action', 'cat-scope-drama', 'cat-scope-netflix'];
    window.BuroApp.state.categories.push(
        { id: scopeFixtureIds[0], sourceId: 'source-home', contentType: 'MOVIE', name: 'Filmes | Ação' },
        { id: scopeFixtureIds[1], sourceId: 'source-home', contentType: 'MOVIE', name: 'Filmes | Drama' },
        { id: scopeFixtureIds[2], sourceId: 'source-home', contentType: 'MOVIE', name: 'Filmes | Netflix' }
    );
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    var scopeGenreChip = window.document.querySelector('[data-action="catalogue-scope-genre"]');
    var scopeFocusables = Array.prototype.slice.call(window.document.querySelectorAll('.focusable:not([disabled])'));
    var scopeFocusedIndex = scopeFocusables.indexOf(window.document.querySelector('.focusable.focused'));
    var scopeGenreIndex = scopeFocusables.indexOf(scopeGenreChip);
    while (scopeFocusedIndex < scopeGenreIndex) { press(window, 40); scopeFocusedIndex += 1; }
    while (scopeFocusedIndex > scopeGenreIndex) { press(window, 38); scopeFocusedIndex -= 1; }
    press(window, 13);
    check('ENTER no gênero conserva o foco no mesmo seletor',
        window.document.querySelector('[data-action="catalogue-scope-genre"]').classList.contains('focused'));
    var firstScopeGenre = window.document.querySelector('[data-action="catalogue-scope-genre"] strong').textContent;
    press(window, 13);
    check('ENTER consecutivo avança o gênero sem exigir voltar pelo D-pad',
        window.document.querySelector('[data-action="catalogue-scope-genre"]').classList.contains('focused') &&
        window.document.querySelector('[data-action="catalogue-scope-genre"] strong').textContent !== firstScopeGenre);
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-scope-reset"]'));
    window.BuroApp.state.categories = window.BuroApp.state.categories.filter(function (category) {
        return scopeFixtureIds.indexOf(category.id) === -1;
    });

    process.stdout.write('Filtros de catálogo e temporadas\n');
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = {
        kind: 'category', contentType: 'MOVIE', category: { id: 'cat-filter', name: 'Filmes teste' },
        items: [
            { id: 'movie:zeta', sourceId: 'source-home', contentType: 'MOVIE', name: 'Zeta', genre: 'Drama', year: 2020, rating: 6 },
            { id: 'movie:alpha', sourceId: 'source-home', contentType: 'MOVIE', name: 'Alpha', genre: 'Ação', year: 2024, rating: 9 },
            { id: 'movie:beta', sourceId: 'source-home', contentType: 'MOVIE', name: 'Beta', genre: 'Ação', year: 2020, rating: 7 }
        ]
    };
    window.BuroApp.state.progress.push({
        id: 'progress:episode:1', profileId: window.BuroApp.state.activeProfile.id, itemId: 'episode:1',
        completed: false, updatedAt: Date.now(), positionMs: 60000, durationMs: 120000
    });
    window.BuroApp.render();
    check('categoria oferece layout, ordenação, gênero e ano pelo D-pad',
        Boolean(window.document.querySelector('[data-action="catalogue-layout"]')) &&
        Boolean(window.document.querySelector('[data-action="catalogue-sort"]')) &&
        Boolean(window.document.querySelector('[data-action="catalogue-genre"]')) &&
        Boolean(window.document.querySelector('[data-action="catalogue-year"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-sort"]'));
    check('ordenação A–Z reorganiza os cards sem alterar o catálogo persistido',
        window.document.querySelector('.media-card h3').textContent === 'Alpha' &&
        window.BuroApp.state.screenData.items[0].name === 'Zeta');
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-layout"]'));
    check('seletor muda de capas para o layout compacto',
        Boolean(window.document.querySelector('.catalogue-layout-compact')));
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-genre"]'));
    check('filtro de gênero reduz somente a apresentação',
        window.document.querySelectorAll('.media-card').length === 2 &&
        window.BuroApp.state.screenData.items.length === 3);
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-reset"]'));
    check('limpar filtros restaura todos os títulos', window.document.querySelectorAll('.media-card').length === 3);

    var pagedCatalogueRows = [];
    var catalogueIndex;
    for (catalogueIndex = 0; catalogueIndex < 450; catalogueIndex += 1) {
        pagedCatalogueRows.push({
            id: 'movie:catalogue-page-' + catalogueIndex, sourceId: 'source-home', contentType: 'MOVIE',
            name: 'Filme paginado ' + ('000' + catalogueIndex).slice(-3)
        });
    }
    window.BuroApp.state.screenData = {
        kind: 'category', contentType: 'MOVIE', category: { id: 'cat-paged', name: 'Catálogo extenso' },
        items: pagedCatalogueRows, cataloguePage: 0
    };
    window.BuroApp.render();
    check('categoria extensa limita o DOM aos mesmos 200 itens por página usados no Android',
        window.document.querySelectorAll('.media-card').length === 200 &&
        window.document.body.textContent.indexOf('Página 1 de 3') >= 0 &&
        Boolean(window.document.querySelector('[data-action="category-page-next"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="category-page-next"]'));
    check('próxima página mantém 200 cards e foco repetível no controle remoto',
        window.BuroApp.state.screenData.cataloguePage === 1 &&
        window.document.querySelectorAll('.media-card').length === 200 &&
        window.document.querySelector('[data-action="category-page-next"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="category-page-next"]'));
    check('última página mostra somente o restante e deixa Voltar focado',
        window.BuroApp.state.screenData.cataloguePage === 2 &&
        window.document.querySelectorAll('.media-card').length === 50 &&
        !window.document.querySelector('[data-action="category-page-next"]') &&
        window.document.querySelector('[data-action="category-page-previous"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="category-page-previous"]'));
    check('página anterior volta sem perder nem duplicar itens do catálogo',
        window.BuroApp.state.screenData.cataloguePage === 1 &&
        window.document.querySelectorAll('.media-card').length === 200 &&
        window.BuroApp.state.screenData.items.length === 450);
    window.BuroApp.state.screenData.cataloguePage = 2;
    window.BuroApp.render();
    window.BuroApp._activate(window.document.querySelector('[data-action="catalogue-sort"]'));
    check('alterar ordenação reinicia na primeira página sem mutar os 450 itens',
        window.BuroApp.state.screenData.cataloguePage === 0 &&
        window.document.querySelectorAll('.media-card').length === 200 &&
        window.BuroApp.state.screenData.items.length === 450);

    var originalCategoryPage = window.BuroStorage.categoryPage;
    var progressiveCalls = 0;
    var progressiveRows = pagedCatalogueRows.map(function (item, index) {
        item.categoryId = 'cat-progressive';
        item.sortOrder = index;
        return item;
    });
    window.BuroStorage.categoryPage = function (sourceId, categoryId, cursor, limit, success) {
        var start = progressiveCalls === 0 ? 200 : 400;
        var end = progressiveCalls === 0 ? 400 : 450;
        progressiveCalls += 1;
        window.setTimeout(function () {
            success({
                rows: progressiveRows.slice(start, end),
                hasMore: end < progressiveRows.length,
                nextCursor: end < progressiveRows.length ? [sourceId, categoryId, end - 1, progressiveRows[end - 1].id] : null,
                totalCount: progressiveRows.length
            });
        }, 0);
    };
    window.BuroApp.state.screenData = {
        kind: 'category', contentType: 'MOVIE',
        category: { id: 'cat-progressive', sourceId: 'source-home', name: 'Catálogo progressivo' },
        items: progressiveRows.slice(0, 200), cataloguePage: 0, catalogueHasMore: true,
        catalogueNextCursor: ['source-home', 'cat-progressive', 199, progressiveRows[199].id],
        catalogueTotalCount: 450
    };
    window.BuroApp.render();
    check('categoria paginada informa 200 de 450 sem colocar os demais na memória',
        window.BuroApp.state.screenData.items.length === 200 &&
        window.document.querySelector('[data-action="category-load-more"]').parentNode.textContent.indexOf('200 / 450') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="category-load-more"]'));
    await waitFor(function () { return window.BuroApp.state.screenData.items.length === 400; }, 1000);
    check('Carregar mais acrescenta um bloco e abre a próxima página mantendo 200 cards no DOM',
        window.BuroApp.state.screenData.cataloguePage === 1 &&
        window.document.querySelectorAll('.media-card').length === 200 &&
        window.document.querySelector('[data-action="category-load-more"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="category-load-more"]'));
    await waitFor(function () { return window.BuroApp.state.screenData.items.length === 450; }, 1000);
    check('último bloco encerra o cursor, mostra 50 cards e preserva navegação para trás',
        window.BuroApp.state.screenData.cataloguePage === 2 &&
        window.document.querySelectorAll('.media-card').length === 50 &&
        !window.document.querySelector('[data-action="category-load-more"]') &&
        window.document.querySelector('[data-action="category-page-previous"]').classList.contains('focused'));
    check('blocos progressivos não repetem identidades',
        new Set(window.BuroApp.state.screenData.items.map(function (item) { return item.id; })).size === 450 && progressiveCalls === 2);
    window.BuroStorage.categoryPage = originalCategoryPage;

    window.BuroApp.state.section = 'SERIES';
    window.BuroApp.state.screenData = {
        kind: 'series', parent: { id: 'series:seasons', sourceId: 'source-home', name: 'Série teste', contentType: 'SERIES' },
        details: { title: 'Série teste', plot: 'Duas temporadas', genre: 'Drama', director: 'Diretor Série',
            cast: 'Pessoa Um, Pessoa Dois', duration: '45', releaseDate: '2024', country: 'Portugal', rating: 9.1 },
        items: [
            { id: 'episode:1', sourceId: 'source-home', providerItemId: 'bulk-s1e1', contentType: 'EPISODE', name: 'Episódio 1', locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: 'bulk-s1e1', season: 1, episode: 1, extension: 'mp4' } },
            { id: 'episode:2', sourceId: 'source-home', providerItemId: 'bulk-s1e2', contentType: 'EPISODE', name: 'Episódio 2', locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: 'bulk-s1e2', season: 1, episode: 2, extension: 'mp4' } },
            { id: 'episode:3', sourceId: 'source-home', providerItemId: 'bulk-s2e1', contentType: 'EPISODE', name: 'Episódio 3', locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: 'bulk-s2e1', season: 2, episode: 1, extension: 'mp4' } }
        ]
    };
    var bulkSeriesScreenData = window.BuroApp.state.screenData;
    var bulkSeriesSource = window.BuroApp.state.sources.filter(function (source) { return source.id === 'source-home'; })[0];
    var bulkSeriesOriginalType = bulkSeriesSource.type;
    var originalBulkResolvePlayback = window.BuroXtream.resolvePlayback;
    window.tizen.filesystem.listStorages = function (success) {
        success([{ label: 'removable_profile_fixture', state: 'MOUNTED' }]);
    };
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });
    var m3uDownloadParsed = window.BuroM3u.parse(
        '#EXTM3U\n#EXTINF:-1 tvg-id="m3u-direct" group-title="Movies",Filme M3U direto\n' +
        'https://media.public.test/original.mkv?auth=memory-only', 'source-home'
    );
    var m3uDownloadItem = window.BuroM3u.metadata(m3uDownloadParsed)[0];
    window.BuroApp.state.items.push(m3uDownloadItem);
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', { url: 'https://catalog.public.test/list.m3u' }, resolve, reject);
    });
    var originalM3uDownloadNetwork = window.BuroNetwork.text;
    window.BuroNetwork.text = function (options, success) {
        success('#EXTM3U\n' +
            '#EXTINF:-1 tvg-id="different" group-title="Movies",Outro filme\nhttps://media.public.test/wrong.mp4\n' +
            '#EXTINF:-1 tvg-id="m3u-direct" group-title="Movies",Filme M3U direto\nhttps://media.public.test/correct.mkv?auth=memory-only');
    };
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = { kind: 'movie', parent: m3uDownloadItem, details: { title: m3uDownloadItem.name } };
    window.BuroApp.render();
    check('filme M3U direto oferece download apenas com extensão e USB reais',
        m3uDownloadItem.locator.extension === 'mkv' && Boolean(window.document.querySelector('[data-action="download"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="download"]'));
    await waitFor(function () {
        return window.BuroDownloads.list().some(function (entry) { return entry.id === 'movie:m3u-direct'; });
    }, 1000);
    check('download M3U relê a fonte e encontra a mesma identidade mesmo após reordenação',
        downloadRequests[downloadRequests.length - 1].url.indexOf('/correct.mkv') >= 0 &&
        downloadRequests[downloadRequests.length - 1].fileName === 'movie-m3u-direct.mkv.part');
    check('detalhe do filme troca Baixar por Cancelar enquanto a transferência está ativa',
        Boolean(window.document.querySelector('[data-action="download-cancel"][data-id="movie:m3u-direct"]')) &&
        !window.document.querySelector('[data-action="download"][data-id="' + m3uDownloadItem.id + '"]'));
    check('URL M3U continua ausente da fila e do snapshot local',
        JSON.stringify(window.BuroDownloads.list()).indexOf('media.public.test') === -1 &&
        (window.localStorage.getItem('iptvburo.downloads.v1') || '').indexOf('media.public.test') === -1);
    window.BuroDownloads.remove('movie:m3u-direct');
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) { return item.id !== m3uDownloadItem.id; });
    var m3uHlsItem = window.BuroM3u.metadata(window.BuroM3u.parse(
        '#EXTM3U\n#EXTINF:-1 tvg-id="m3u-hls" group-title="Movies",Filme HLS\n' +
        'https://media.public.test/manifest.m3u8?auth=memory-only', 'source-home'
    ))[0];
    window.BuroApp.state.items.push(m3uHlsItem);
    window.BuroApp.state.screenData = { kind: 'movie', parent: m3uHlsItem, details: { title: m3uHlsItem.name } };
    window.BuroApp.render();
    check('manifesto HLS M3U nao e oferecido como arquivo offline unico',
        m3uHlsItem.locator.extension === null && !window.document.querySelector('[data-action="download"]'));
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) { return item.id !== m3uHlsItem.id; });
    window.BuroNetwork.text = originalM3uDownloadNetwork;
    window.BuroStorage.secureRemove('source-home');
    bulkSeriesSource.type = 'XTREAM';
    window.BuroApp.state.section = 'SERIES';
    window.BuroApp.state.screenData = bulkSeriesScreenData;
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', {
            server: 'https://provider.test', username: 'synthetic', password: 'synthetic'
        }, resolve, reject);
    });
    window.BuroXtream.resolvePlayback = function (secret, locator) {
        return 'https://public.test/episode-' + locator.providerItemId + '.mp4';
    };
    bulkSeriesScreenData.items.forEach(function (episode) {
        if (!window.BuroApp.state.items.some(function (item) { return item.id === episode.id; })) {
            window.BuroApp.state.items.push(episode);
        }
    });
    window.BuroApp.render();
    var seriesPrimaryButton = window.document.querySelector('[data-action="series-primary-play"]');
    check('série oferece no topo a retomada do primeiro episódio em andamento como Windows',
        seriesPrimaryButton && seriesPrimaryButton.getAttribute('data-id') === 'episode:1' &&
        seriesPrimaryButton.textContent.indexOf('Continuar T1 E1') >= 0);
    check('série oferece download completo e por temporada somente com USB e Xtream reais',
        Boolean(window.document.querySelector('[data-action="series-download-all"]')) &&
        window.document.querySelectorAll('[data-action="series-download-season"]').length === 2);
    window.BuroApp._activate(window.document.querySelector('[data-action="series-season"][data-season="1"]'));
    check('cada episódio expandido oferece download individual como Android e Windows',
        window.document.querySelectorAll('.episode-download-item [data-action="download"]').length === 2);
    window.BuroApp._activate(window.document.querySelector('.episode-download-item [data-action="download"]'));
    await waitFor(function () { return Boolean(window.document.querySelector(
        '.episode-download-item [data-action="download-cancel"][data-id="episode:bulk-s1e1"]'));
    }, 1000);
    check('download individual troca para Cancelar sem sair da temporada',
        Number(window.BuroApp.state.screenData.expandedSeason) === 1 &&
        window.BuroDownloads.stateFor(bulkSeriesScreenData.items[0]) === 'DOWNLOADING');
    window.BuroApp._activate(window.document.querySelector(
        '.episode-download-item [data-action="download-cancel"][data-id="episode:bulk-s1e1"]'));
    check('episódio cancelado oferece nova tentativa no mesmo lugar',
        Boolean(window.document.querySelector('.episode-download-item [data-action="download-retry"]')));
    window.BuroApp._activate(window.document.querySelector('.episode-download-item [data-action="download-retry"]'));
    await waitFor(function () { return window.BuroDownloads.stateFor(bulkSeriesScreenData.items[0]) === 'DOWNLOADING'; }, 1000);
    check('nova tentativa remove o estado terminal e cria outra transferência real',
        window.BuroDownloads.list().length === 1 &&
        Boolean(window.document.querySelector('.episode-download-item [data-action="download-cancel"]')));
    window.BuroDownloads.remove('episode:bulk-s1e1');
    window.BuroApp._activate(window.document.querySelector('[data-action="series-download-season"][data-season="1"]'));
    check('download de temporada exige confirmação e informa os dois episódios efetivos',
        window.BuroApp.state.screen === 'BULK_DOWNLOAD_CONFIRM' &&
        window.document.body.textContent.indexOf('2') >= 0 &&
        window.document.querySelector('[data-action="bulk-download-confirm"]').classList.contains('focused'));
    window.BuroApp._activate(window.document.querySelector('[data-action="bulk-download-confirm"]'));
    await waitFor(function () { return window.BuroDownloads.list().length === 2; }, 1000);
    check('temporada entra na fila USB em ordem de reprodução sem expor URLs',
        window.BuroDownloads.list().map(function (entry) { return entry.id; }).join(',') ===
            'episode:bulk-s1e1,episode:bulk-s1e2' &&
        JSON.stringify(window.BuroDownloads.list()).indexOf('public.test') === -1);
    window.BuroApp._activate(window.document.querySelector('[data-action="series-download-all"]'));
    check('série inteira mantém a mesma seleção Android e inclui transferências em andamento',
        window.BuroApp.state.screenData.items.length === 3);
    window.BuroApp._activate(window.document.querySelector('[data-action="bulk-download-confirm"]'));
    await waitFor(function () { return window.BuroDownloads.list().length === 3; }, 1000);
    check('segunda ação deduplica os dois episódios ativos e adiciona apenas a temporada restante',
        window.BuroDownloads.list().map(function (entry) { return entry.id; }).join(',') ===
            'episode:bulk-s1e1,episode:bulk-s1e2,episode:bulk-s2e1');
    window.BuroDownloads.list().forEach(function (entry) { window.BuroDownloads.remove(entry.id); });
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) {
        return !bulkSeriesScreenData.items.some(function (episode) { return episode.id === item.id; });
    });
    bulkSeriesScreenData.expandedSeason = null;
    window.BuroXtream.resolvePlayback = originalBulkResolvePlayback;
    bulkSeriesSource.type = bulkSeriesOriginalType;
    window.BuroStorage.secureRemove('source-home');
    window.tizen.filesystem.listStorages = function (success) {
        success([{ label: 'removable_profile_fixture', state: 'REMOVED' }]);
    };
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });
    window.BuroApp.render();
    check('ações em lote desaparecem quando o USB deixa de ser uma capability real',
        !window.document.querySelector('[data-action="series-download-all"]') &&
        !window.document.querySelector('[data-action="series-download-season"]'));
    check('série agrupa episódios em temporadas recolhidas',
        window.document.querySelectorAll('[data-action="series-season"]').length === 2 &&
        window.document.querySelectorAll('.season-list .media-card').length === 0 &&
        window.document.querySelector('[data-action="series-season"]').getAttribute('aria-expanded') === 'false');
    check('detalhe de série apresenta fatos, créditos e elenco antes das temporadas',
        window.document.body.textContent.indexOf('2 temporadas') >= 0 &&
        window.document.body.textContent.indexOf('3 episódios') >= 0 &&
        window.document.querySelectorAll('.cast-chip').length === 2 &&
        window.document.querySelector('.detail-credit-card').textContent.indexOf('Diretor Série') >= 0 &&
        Boolean(window.document.querySelector('[data-action="share"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="series-season"][data-season="1"]'));
    check('ENTER expande somente a temporada escolhida',
        window.document.querySelectorAll('.season-list .media-card').length === 2 &&
        window.document.querySelectorAll('.season-header.expanded').length === 1 &&
        window.document.querySelector('.season-header.expanded').getAttribute('aria-expanded') === 'true');
    check('episódio mostra temporada, número e progresso de retomada',
        window.document.querySelector('[data-id="episode:1"] p').textContent === 'T1 · E1' &&
        parseFloat(window.document.querySelector('[data-id="episode:1"] .media-progress i').style.width) === 50);
    var pagedEpisodes = [];
    var episodeIndex;
    for (episodeIndex = 1; episodeIndex <= 95; episodeIndex += 1) {
        pagedEpisodes.push({
            id: 'episode:paged-' + episodeIndex, sourceId: 'source-home', contentType: 'EPISODE',
            name: 'Episódio paginado ' + episodeIndex, locator: { season: 1, episode: episodeIndex }
        });
    }
    window.BuroApp.state.screenData.items = pagedEpisodes;
    window.BuroApp.state.screenData.expandedSeason = 1;
    window.BuroApp.state.screenData.seasonPages = { 1: 0 };
    window.BuroApp.render();
    check('temporada extensa revela 40 episódios por vez como o cliente Windows',
        window.document.querySelectorAll('.season-list .media-card').length === 40 &&
        window.document.body.textContent.indexOf('Página 1 de 3') >= 0 &&
        Boolean(window.document.querySelector('[data-action="series-page-next"][data-season="1"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="series-page-next"][data-season="1"]'));
    check('navegação de episódios mantém a temporada expandida e o próximo lote limitado',
        window.BuroApp.state.screenData.expandedSeason === 1 &&
        window.BuroApp.state.screenData.seasonPages[1] === 1 &&
        window.document.querySelectorAll('.season-list .media-card').length === 40);
    window.BuroApp._activate(window.document.querySelector('[data-action="series-page-next"][data-season="1"]'));
    check('último lote de episódios mostra o restante com retorno por D-pad',
        window.BuroApp.state.screenData.seasonPages[1] === 2 &&
        window.document.querySelectorAll('.season-list .media-card').length === 15 &&
        window.document.querySelector('[data-action="series-page-previous"][data-season="1"]').classList.contains('focused'));
    window.BuroApp.state.screenData.items = [];
    window.BuroApp.render();
    check('série sem episódios apresenta estado vazio específico',
        window.document.body.textContent.indexOf(window.BuroI18n.t('noEpisodes')) >= 0 &&
        !window.document.querySelector('.season-header') &&
        !window.document.querySelector('[data-action="series-primary-play"]'));

    var cachedSeries = {
        id: 'series:cached-refresh', sourceId: 'source-home', contentType: 'SERIES', name: 'Série em cache',
        locator: { kind: 'xtream', contentType: 'SERIES', providerItemId: 'cached-series' }
    };
    var cachedEpisode = {
        id: 'episode:cached-old', sourceId: 'source-home', categoryId: cachedSeries.id,
        contentType: 'EPISODE', name: 'Episódio em cache', locator: { season: 1, episode: 1 }
    };
    var cachedFavorite = { id: 'favorite:cached-old', profileId: window.BuroApp.state.activeProfile.id, itemId: cachedEpisode.id };
    var cachedProgress = { id: 'progress:cached-old', profileId: window.BuroApp.state.activeProfile.id, itemId: cachedEpisode.id, positionMs: 30000, durationMs: 120000 };
    window.BuroApp.state.items.push(cachedSeries, cachedEpisode);
    window.BuroApp.state.favorites.push(cachedFavorite);
    window.BuroApp.state.progress.push(cachedProgress);
    await new Promise(function (resolve, reject) { window.BuroStorage.put('items', cachedEpisode, resolve, reject); });
    await new Promise(function (resolve, reject) { window.BuroStorage.put('favorites', cachedFavorite, resolve, reject); });
    await new Promise(function (resolve, reject) { window.BuroStorage.put('progress', cachedProgress, resolve, reject); });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', { server: 'https://provider.test', username: 'synthetic', password: 'synthetic' }, resolve, reject);
    });
    window.BuroApp.state.sources[0].type = 'XTREAM';
    var originalLoadSeriesEpisodes = window.BuroXtream.loadSeriesEpisodes;
    window.BuroXtream.loadSeriesEpisodes = function (secret, sourceId, item, success, failure) { failure({ code: 'NETWORK_ERROR' }); };
    var cachedSeriesButton = window.document.createElement('button');
    cachedSeriesButton.setAttribute('data-action', 'series-details');
    cachedSeriesButton.setAttribute('data-id', cachedSeries.id);
    window.BuroApp._activate(cachedSeriesButton);
    check('falha ao atualizar série mantém episódios cacheados acessíveis',
        window.BuroApp.state.screenData.kind === 'series' && window.BuroApp.state.screenData.detailsError &&
        Boolean(window.document.querySelector('[data-action="series-details-retry"]')) &&
        window.document.body.textContent.indexOf('Episódio em cache') < 0);
    /* O episódio fica dentro da temporada recolhida; a contagem prova que ele continua disponível. */
    check('aviso de cache informa a falha sem transformar a série em tela vazia',
        window.document.body.textContent.indexOf(window.BuroI18n.t('seriesCachedWarning')) >= 0 &&
        window.document.querySelector('[data-action="series-season"] span').textContent.indexOf('1') >= 0);
    var refreshedEpisode = {
        id: 'episode:cached-new', sourceId: 'source-home', categoryId: cachedSeries.id,
        contentType: 'EPISODE', name: 'Episódio atualizado', locator: { season: 2, episode: 1 }
    };
    window.BuroXtream.loadSeriesEpisodes = function (secret, sourceId, item, success) {
        success([refreshedEpisode], { title: 'Série atualizada', plot: 'Detalhes novos', cast: 'Atriz Teste', rating: 8.4 }, null);
    };
    window.BuroApp._activate(window.document.querySelector('[data-action="series-details-retry"]'));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'series' &&
            window.BuroApp.state.screenData.items[0] && window.BuroApp.state.screenData.items[0].id === refreshedEpisode.id;
    }, 1000);
    var removedCachedEpisode;
    await new Promise(function (resolve, reject) {
        window.BuroStorage.get('items', cachedEpisode.id, function (row) { removedCachedEpisode = row; resolve(); }, reject);
    });
    check('retry reconcilia episódios e remove o cache obsoleto de forma atômica',
        !removedCachedEpisode && !window.BuroApp.state.favorites.some(function (row) { return row.id === cachedFavorite.id; }) &&
        !window.BuroApp.state.progress.some(function (row) { return row.id === cachedProgress.id; }));
    check('detalhes atualizados reaparecem com elenco e avaliação',
        window.document.querySelector('.detail-hero h2').textContent === 'Série atualizada' &&
        window.document.querySelector('.detail-fact.rating').textContent.indexOf('8.4') >= 0 &&
        window.document.querySelectorAll('.cast-chip').length === 1);
    window.BuroXtream.loadSeriesEpisodes = originalLoadSeriesEpisodes;
    await new Promise(function (resolve, reject) {
        window.BuroStorage.replaceCategoryItems('source-home', cachedSeries.id, [], resolve, reject);
    });
    window.BuroStorage.secureRemove('source-home');
    window.BuroApp.state.sources[0].type = 'REMOTE_M3U';
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (row) {
        return row.id !== cachedSeries.id && row.categoryId !== cachedSeries.id;
    });

    process.stdout.write('Gerenciamento e exclusão segura de fontes\n');
    (function () {
        var source = { id: 'source-delete', name: 'Fonte antiga', type: 'REMOTE_M3U', channelCount: 1, createdAt: Date.now() };
        var category = { id: 'category-delete', sourceId: source.id, name: 'Teste', contentType: 'MOVIE' };
        var item = { id: 'movie:delete', sourceId: source.id, categoryId: category.id, contentType: 'MOVIE', name: 'Remover' };
        var favorite = { id: 'favorite:delete', profileId: window.BuroApp.state.activeProfile.id, itemId: item.id };
        var progress = { id: 'progress:delete', profileId: window.BuroApp.state.activeProfile.id, itemId: item.id };
        window.__sourceDeleteFixture = { source: source, category: category, item: item, favorite: favorite, progress: progress };
    }());
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('sources', window.__sourceDeleteFixture.source, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('categories', window.__sourceDeleteFixture.category, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('items', window.__sourceDeleteFixture.item, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('favorites', window.__sourceDeleteFixture.favorite, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('progress', window.__sourceDeleteFixture.progress, resolve, reject);
    });
    window.BuroApp.state.activeProfile.sourceId = 'source-delete';
    await new Promise(function (resolve, reject) {
        window.BuroStorage.put('profiles', window.BuroApp.state.activeProfile, resolve, reject);
    });
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-delete', { url: 'https://public.test/list.m3u' }, resolve, reject);
    });
    window.BuroApp.state.sources.push(window.__sourceDeleteFixture.source);
    window.BuroApp.state.categories.push(window.__sourceDeleteFixture.category);
    window.BuroApp.state.items.push(window.__sourceDeleteFixture.item);
    window.BuroApp.state.favorites.push(window.__sourceDeleteFixture.favorite);
    window.BuroApp.state.progress.push(window.__sourceDeleteFixture.progress);
    window.BuroApp.state.screen = 'SHELL'; window.BuroApp.state.section = 'SOURCES'; window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('cada fonte oferece seleção e gerenciamento como ações separadas',
        Boolean(window.document.querySelector('[data-action="select-source"][data-id="source-delete"]')) &&
        Boolean(window.document.querySelector('[data-action="source-manage"][data-id="source-delete"]')));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-manage"][data-id="source-delete"]'));
    window.document.getElementById('source-manage-name').value = 'Fonte renomeada';
    window.BuroApp._activate(window.document.querySelector('[data-action="source-rename"]'));
    await waitFor(function () {
        return window.BuroApp.state.sources.some(function (source) { return source.id === 'source-delete' && source.name === 'Fonte renomeada'; });
    }, 1000);
    check('renomear preserva identidade, credencial e contagem da fonte',
        window.BuroApp.state.sources.filter(function (source) { return source.id === 'source-delete'; })[0].channelCount === 1 &&
        Boolean(window.BuroStorage.secureGet('source-delete')));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-manage"][data-id="source-delete"]'));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-delete"]'));
    check('excluir fonte exige confirmação explícita',
        window.document.querySelector('[data-action="source-delete"]').textContent === window.BuroI18n.t('confirmDeleteSource'));
    window.BuroApp._activate(window.document.querySelector('[data-action="source-delete"]'));
    await waitFor(function () {
        return !window.BuroApp.state.sources.some(function (source) { return source.id === 'source-delete'; });
    }, 1000);
    var deletedSource;
    var deletedItem;
    var cleanedProfile;
    await new Promise(function (resolve, reject) { window.BuroStorage.get('sources', 'source-delete', function (row) { deletedSource = row; resolve(); }, reject); });
    await new Promise(function (resolve, reject) { window.BuroStorage.get('items', 'movie:delete', function (row) { deletedItem = row; resolve(); }, reject); });
    await new Promise(function (resolve, reject) { window.BuroStorage.get('profiles', window.BuroApp.state.activeProfile.id, function (row) { cleanedProfile = row; resolve(); }, reject); });
    var secretRemoved = false;
    try { window.BuroStorage.secureGet('source-delete'); } catch (ignoredRemovedSecret) { secretRemoved = true; }
    check('exclusão remove catálogo, referência do perfil e segredo do KeyManager',
        !deletedSource && !deletedItem && cleanedProfile.sourceId === null && secretRemoved);
    check('exclusão também limpa favoritos e progresso órfãos da memória',
        !window.BuroApp.state.favorites.some(function (row) { return row.itemId === 'movie:delete'; }) &&
        !window.BuroApp.state.progress.some(function (row) { return row.itemId === 'movie:delete'; }));

    process.stdout.write('Hero enriquecido sem bloquear a Home\n');
    var homeSource = window.BuroApp.state.sources.filter(function (source) { return source.id === 'source-home'; })[0];
    var originalLoadHeroDetails = window.BuroXtream.loadHeroDetails;
    var heroDetailRequests = [];
    var heroAbortCount = 0;
    homeSource.type = 'XTREAM';
    window.BuroApp.state.activeSource = homeSource;
    window.BuroApp.state.activeProfile.sourceId = homeSource.id;
    window.BuroApp.state.items.filter(function (item) {
        return item.sourceId === homeSource.id && (item.contentType === 'MOVIE' || item.contentType === 'SERIES');
    }).forEach(function (item, index) {
        item.locator = {
            kind: 'xtream', contentType: item.contentType, providerItemId: String(7000 + index), extension: 'mp4'
        };
    });
    await Promise.all(window.BuroApp.state.items.filter(function (item) {
        return item.sourceId === homeSource.id && item.locator;
    }).map(function (item) {
        return new Promise(function (resolve, reject) { window.BuroStorage.put('items', item, resolve, reject); });
    }));
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave(homeSource.id, {
            server: 'https://provider.test', username: 'synthetic', password: 'synthetic'
        }, resolve, reject);
    });
    window.BuroCatalogueSync.cancel();
    window.BuroHeroEnrichment.clearSource(homeSource.id);
    window.BuroXtream.loadHeroDetails = function (secret, item, success, failure) {
        var request = { item: item, success: success, failure: failure, aborted: false };
        heroDetailRequests.push(request);
        return { abort: function () {
            if (request.aborted) { return; }
            request.aborted = true;
            heroAbortCount += 1;
            failure({ code: 'NETWORK_ABORTED' });
        } };
    };
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'HOME';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    await waitFor(function () {
        return heroDetailRequests.length === 1 && window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'home' && !window.BuroApp.state.screenData.loading;
    }, 1000);
    var firstHeroScreenData = window.BuroApp.state.screenData;
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="HOME"]'));
    await waitFor(function () {
        return window.BuroApp.state.screenData !== firstHeroScreenData && window.BuroApp.state.screenData &&
            window.BuroApp.state.screenData.kind === 'home' && !window.BuroApp.state.screenData.loading;
    }, 1000);
    await new Promise(function (resolve) { window.setTimeout(resolve, 20); });
    check('a Home aparece com texto de fallback antes dos detalhes do provedor',
        window.document.querySelector('.hero-synopsis').textContent === window.BuroI18n.t('homeHeroSynopsis'));
    var heroButton = window.document.querySelector('.real-home-hero .button');
    for (var heroFocusAttempt = 0; heroFocusAttempt < 20 && !heroButton.classList.contains('focused'); heroFocusAttempt += 1) {
        press(window, 40);
    }
    var enrichedHeroId = heroDetailRequests[0].item.id;
    heroDetailRequests[0].success({
        synopsis: 'Sinopse real e autorizada do destaque.', genre: 'Drama / Aventura', duration: '1h 42min', rating: 8.7,
        artworkUrl: 'https://images.public.test/hero-poster.jpg',
        backdropUrl: 'https://images.public.test/hero-backdrop.jpg?session=only'
    });
    await waitFor(function () {
        return window.document.querySelector('.hero-synopsis') &&
            window.document.querySelector('.hero-synopsis').textContent === 'Sinopse real e autorizada do destaque.';
    }, 1000);
    var enrichedHeroImage = window.document.querySelector('.real-home-hero .hero-art img');
    check('sinopse, fatos e backdrop reais atualizam apenas o destaque ativo',
        window.document.querySelector('.hero-metadata').textContent.indexOf('Drama') >= 0 &&
        window.document.querySelector('.hero-metadata').textContent.indexOf('1h 42min') >= 0 &&
        enrichedHeroImage.src.indexOf('hero-backdrop.jpg') >= 0);
    check('a atualização assíncrona conserva o foco exato do botão do Hero',
        heroButton.classList.contains('focused') ||
        window.document.querySelector('.real-home-hero .button').classList.contains('focused'));
    enrichedHeroImage.dispatchEvent(new window.Event('error'));
    check('falha do backdrop tenta o pôster antes do fundo local',
        enrichedHeroImage.src.indexOf('hero-poster.jpg') >= 0 &&
        !enrichedHeroImage.hasAttribute('data-artwork-fallback'));
    await waitFor(function () { return heroDetailRequests.length === 2; }, 1000);
    var staleHeroId = heroDetailRequests[1].item.id;
    window.BuroApp._activate(window.document.querySelector('[data-action="section"][data-section="LIVE"]'));
    heroDetailRequests[1].success({ synopsis: 'Resposta obsoleta', backdropUrl: 'https://images.public.test/stale.jpg' });
    check('sair da Home aborta a fila e ignora resposta atrasada',
        heroAbortCount === 1 && window.BuroApp.state.section === 'LIVE' &&
        !window.BuroHeroEnrichment.get(homeSource.id, staleHeroId) && enrichedHeroId !== staleHeroId);
    check('metadados e URLs do Hero continuam fora do localStorage',
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('hero-backdrop.jpg') === -1 &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('Sinopse real') === -1);
    window.BuroXtream.loadHeroDetails = originalLoadHeroDetails;
    window.BuroStorage.secureRemove(homeSource.id);

    process.stdout.write('Lembretes abrem títulos locais\n');
    homeSource.type = 'REMOTE_M3U';
    var reminderLocalItem = window.BuroApp.state.items.filter(function (item) {
        return item.id === 'movie:home-two';
    })[0];
    var reminderFixtures = [window.BuroDomain.createReminder({
        profileId: window.BuroApp.state.activeProfile.id,
        item: reminderLocalItem,
        releaseDate: String(homeYear) + '-06-15'
    })];
    for (var reminderIndex = 0; reminderIndex < 13; reminderIndex += 1) {
        reminderFixtures.push(window.BuroDomain.createReminder({
            profileId: window.BuroApp.state.activeProfile.id,
            identity: 'movie:future reminder ' + reminderIndex + ':2099',
            title: 'Lançamento futuro ' + reminderIndex,
            contentType: 'MOVIE',
            releaseDate: '2099-12-31'
        }));
    }
    window.BuroApp.state.reminders = reminderFixtures;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'REMINDERS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('a página mostra todos os lembretes e não corta a lista no limite do trilho da Home',
        window.document.querySelectorAll('.reminder-row').length === 14);
    check('somente o lembrete que já corresponde à biblioteca vira ação de detalhes',
        window.document.querySelectorAll('[data-action="reminder-open"]').length === 1 &&
        window.document.querySelector('[data-action="reminder-open"]').getAttribute('data-id') === reminderLocalItem.id &&
        window.document.querySelectorAll('.reminder-row-static').length === 13 &&
        window.document.body.textContent.indexOf('Ainda não está na sua biblioteca') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="reminder-open"]'));
    check('abrir um lembrete local reutiliza a tela real de detalhes do filme',
        window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'movie' &&
        window.BuroApp.state.screenData.parent.id === reminderLocalItem.id &&
        window.BuroApp.state.screenData.originSection === 'REMINDERS');
    press(window, 10009);
    check('RETURN restaura a lista e o foco no mesmo lembrete',
        window.BuroApp.state.section === 'REMINDERS' && !window.BuroApp.state.screenData &&
        window.document.querySelectorAll('.reminder-row').length === 14 &&
        window.document.querySelector('[data-action="reminder-open"]').classList.contains('focused'));
    process.stdout.write('Proteção de credenciais\n');
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-safe', { username: 'alice', password: 'super-secret' }, resolve, reject);
    });
    localDump = window.localStorage.getItem('iptvburo.preferences.v1') || '';
    check('senha não entra no localStorage', localDump.indexOf('super-secret') === -1 && localDump.indexOf('alice') === -1);
    check('segredo vai ao KeyManager', Object.keys(window.__secureData).length === 1);

    process.stdout.write('Ciclo de vida AVPlay\n');
    window.webapis = { avplay: {
        getState: function () { return 'READY'; }, open: function () {}, setListener: function () {},
        setDisplayRect: function () {}, prepareAsync: function (ok, fail) { fail({ name: 'NetworkError' }); },
        stop: function () {}, close: function () { closeCount += 1; }
    } };
    window.BuroPlayer.setListeners({ onStatus: function () {}, onError: function () {} });
    window.BuroPlayer.play('https://public.test/video.m3u8');
    check('falha de prepare fecha AVPlay', closeCount >= 2 && !window.BuroPlayer.isPlaying());

    window.webapis = { avplay: {
        getState: function () { return 'READY'; }, open: function (url) { openedPlaybackUrl = url; }, setListener: function (value) { avListener = value; },
        setDisplayRect: function () {}, prepareAsync: function (ok) { ok(); },
        play: function () { playerPlayCount += 1; }, pause: function () { playerPauseCount += 1; },
        stop: function () {}, close: function () {},
        seekTo: function (position, ok) { initialSeek = position; ok(); },
        getDuration: function () { return 120000; },
        jumpForward: function (value, ok) { seekForward = value; ok(); },
        jumpBackward: function (value, ok) { seekBackward = value; ok(); },
        getTotalTrackInfo: function () { return [
            { type: 'AUDIO', index: 2, extra_info: '{"track_lang":"Português"}' },
            { type: 'AUDIO', index: 3, extra_info: '{"track_lang":"Deutsch"}' },
            { type: 'TEXT', index: 7, extra_info: '{"track_lang":"Português"}' }
        ]; },
        setSelectTrack: function (type, index) { selectedTracks.push(type + ':' + index); },
        setSilentSubtitle: function (value) { silentSubtitleValues.push(value); },
        setDisplayMethod: function (mode) { selectedDisplayModes.push(mode); },
        setSpeed: function (rate) { selectedSpeeds.push(rate); }
    } };
    window.BuroPlayer.play('https://public.test/video.m3u8');
    avListener.oncurrentplaytime(30000);
    window.BuroPlayer.seekBy(30000);
    window.BuroPlayer.seekBy(-30000);
    window.BuroPlayer.cycleAudio();
    window.BuroPlayer.cycleSubtitle();
    check('AVPlay recebe avanço e retrocesso de 30 segundos', seekForward === 30000 && seekBackward === 30000);
    check('timeline do player apresenta posição e duração sem depender do catálogo',
        window.document.getElementById('player-elapsed').textContent === '00:30' &&
        window.document.getElementById('player-duration').textContent === '02:00' &&
        parseFloat(window.document.getElementById('player-progress').style.width) === 25 &&
        window.document.getElementById('player-timeline').getAttribute('aria-valuenow') === '25' &&
        window.document.getElementById('player-timeline').getAttribute('aria-valuetext') === '00:30 / 02:00');
    check('AVPlay seleciona faixas de áudio e legenda',
        selectedTracks.indexOf('AUDIO:2') >= 0 && selectedTracks.indexOf('TEXT:7') >= 0);
    window.BuroApp.state.preferences.subtitleSize = 'huge';
    window.BuroApp.state.preferences.subtitleColour = 'cyan';
    window.BuroApp.state.preferences.subtitleBackground = false;
    avListener.onsubtitlechange(2500, '<img src=x onerror=synthetic>Legenda segura');
    check('callback AVPlay aplica tamanho, cor e fundo escolhidos na legenda',
        !window.document.getElementById('player-subtitle-cue').hidden &&
        window.document.getElementById('player-subtitle-cue').classList.contains('size-huge') &&
        window.document.getElementById('player-subtitle-cue').classList.contains('colour-cyan') &&
        window.document.getElementById('player-subtitle-cue').classList.contains('no-background'));
    check('texto de legenda usa textContent e remove markup do stream',
        window.document.getElementById('player-subtitle-text').textContent === 'Legenda segura' &&
        !window.document.querySelector('#player-subtitle-cue img') &&
        silentSubtitleValues.indexOf(true) >= 0);
    window.BuroApp.state.sources[0].type = 'XTREAM';
    window.BuroApp.state.items.filter(function (item) { return item.id === 'movie:home-two'; })[0].locator =
        { kind: 'xtream', contentType: 'MOVIE', providerItemId: '2', extension: 'mp4' };
    await new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('source-home', {
            server: 'https://provider.test', username: 'synthetic', password: 'synthetic'
        }, resolve, reject);
    });
    var playFixture = window.document.createElement('button');
    playFixture.setAttribute('data-action', 'play');
    playFixture.setAttribute('data-id', 'movie:home-two');
    window.BuroApp._activate(playFixture);
    check('título elegível abre a decisão Continuar/Recomeçar antes do AVPlay',
        window.BuroApp.state.screen === 'RESUME_PROMPT' &&
        Boolean(window.document.querySelector('[data-action="resume-continue"]')) &&
        window.document.body.textContent.indexOf('00:30') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="resume-continue"]'));
    check('Continuar pede ao AVPlay a posição salva antes de reproduzir',
        initialSeek === 30000 && window.document.body.classList.contains('playing') &&
        window.document.getElementById('player-elapsed').textContent === '00:30' &&
        window.document.getElementById('app').getAttribute('aria-hidden') === 'true');
    var playCountBeforeEnter = playerPlayCount;
    press(window, 13);
    check('ENTER cumpre o rotulo do overlay e pausa a reproducao',
        playerPauseCount === 1);
    press(window, 13);
    check('segundo ENTER retoma a reproducao pelo mesmo contrato',
        playerPlayCount === playCountBeforeEnter + 1);
    await hold(window, 13, 950);
    var lockedPlayerPanel = window.document.getElementById('player-lock-panel');
    check('ENTER longo bloqueia os controles e mostra como desbloquear',
        lockedPlayerPanel && !lockedPlayerPanel.hidden &&
        window.document.getElementById('player-overlay').classList.contains('controls-locked') &&
        lockedPlayerPanel.textContent.indexOf(window.BuroI18n.t('playerUnlockHint')) >= 0);
    var seekWhileLocked = seekForward;
    var pausesWhileLocked = playerPauseCount;
    var playsWhileLocked = playerPlayCount;
    press(window, 39);
    press(window, 10252);
    press(window, 413);
    check('D-pad e teclas de mídia ficam inertes enquanto os controles estão bloqueados',
        seekForward === seekWhileLocked && playerPauseCount === pausesWhileLocked &&
        playerPlayCount === playsWhileLocked && window.document.body.classList.contains('playing'));
    await hold(window, 13, 950);
    check('outro ENTER longo desbloqueia sem interromper a reprodução',
        lockedPlayerPanel && lockedPlayerPanel.hidden &&
        !window.document.getElementById('player-overlay').classList.contains('controls-locked') &&
        window.document.body.classList.contains('playing'));
    check('player expoe a acao contextual de Minha BURO para o titulo atual',
        !window.document.getElementById('player-favorite-label').hidden &&
        window.document.getElementById('player-favorite-label').textContent.indexOf(window.BuroI18n.t('removeFavorite')) >= 0);
    press(window, 403);
    await waitFor(function () {
        return !window.BuroApp.state.favorites.some(function (favorite) { return favorite.itemId === 'movie:home-two'; });
    }, 1000);
    check('tecla vermelha remove o favorito sem fechar o player',
        window.document.body.classList.contains('playing') &&
        window.document.getElementById('player-favorite-label').textContent.indexOf(window.BuroI18n.t('addFavorite')) >= 0);
    press(window, 403);
    await waitFor(function () {
        return window.BuroApp.state.favorites.some(function (favorite) { return favorite.itemId === 'movie:home-two'; });
    }, 1000);
    check('tecla vermelha adiciona novamente e atualiza o overlay',
        window.document.getElementById('player-favorite-label').textContent.indexOf(window.BuroI18n.t('removeFavorite')) >= 0);
    press(window, 37);
    check('seta esquerda retrocede 10 segundos como o player Android',
        seekBackward === 10000 && window.document.querySelector('.player-remote-actions').textContent.indexOf('10s') >= 0);
    press(window, 39);
    check('seta direita conserva o avanÃ§o Android de 30 segundos', seekForward === 30000);
    check('player inicia preservando a proporcao e anuncia a capability real',
        selectedDisplayModes[selectedDisplayModes.length - 1] === 'PLAYER_DISPLAY_MODE_LETTER_BOX' &&
        window.BuroPlayer.displayModes().join(',') === 'LETTER_BOX,FULL_SCREEN,AUTO_ASPECT_RATIO' &&
        !window.document.getElementById('player-aspect-label').hidden);
    press(window, 406);
    check('tecla azul preenche a tela pelo modo nativo do AVPlay',
        selectedDisplayModes[selectedDisplayModes.length - 1] === 'PLAYER_DISPLAY_MODE_FULL_SCREEN' &&
        window.document.getElementById('player-aspect-label').textContent.indexOf(window.BuroI18n.t('playerScaleFill')) >= 0);
    press(window, 406);
    check('segunda pressao azul usa proporcao automatica',
        selectedDisplayModes[selectedDisplayModes.length - 1] === 'PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO' &&
        window.document.getElementById('player-aspect-label').textContent.indexOf(window.BuroI18n.t('playerScaleAuto')) >= 0);
    press(window, 406);
    check('ciclo da tecla azul retorna ao formato original',
        selectedDisplayModes[selectedDisplayModes.length - 1] === 'PLAYER_DISPLAY_MODE_LETTER_BOX' &&
        window.document.getElementById('player-aspect-label').textContent.indexOf(window.BuroI18n.t('playerScaleOriginal')) >= 0);
    press(window, 38);
    check('seta para cima abre um menu visível com as faixas de áudio descobertas',
        !window.document.getElementById('player-menu').hidden &&
        window.document.querySelectorAll('#player-menu [data-player-option]').length === 2 &&
        window.document.getElementById('player-menu-options').getAttribute('role') === 'listbox' &&
        window.document.querySelector('#player-menu [data-player-option]').getAttribute('role') === 'option' &&
        window.document.activeElement === window.document.querySelector('#player-menu [tabindex="0"]'));
    press(window, 39);
    press(window, 13);
    check('D-pad escolhe uma faixa específica e atualiza o overlay',
        selectedTracks.indexOf('AUDIO:3') >= 0 &&
        window.document.getElementById('player-audio-label').textContent.indexOf('Deutsch') >= 0 &&
        window.document.getElementById('player-menu').hidden);
    press(window, 40);
    check('menu de legendas oferece desligar além das faixas disponíveis',
        window.document.querySelectorAll('#player-menu [data-player-option]').length === 2 &&
        window.document.getElementById('player-menu').textContent.indexOf(window.BuroI18n.t('subtitlesOff')) >= 0);
    press(window, 37);
    press(window, 13);
    check('desligar legendas fecha o menu sem encerrar a reprodução',
        window.document.getElementById('player-menu').hidden && window.BuroPlayer.isPlaying() &&
        window.document.getElementById('player-subtitle-cue').hidden);
    check('velocidade aparece somente quando AVPlay expõe a capability e o item não é ao vivo',
        window.BuroPlayer.playbackRateAvailable() && !window.document.getElementById('player-speed-label').hidden);
    press(window, 405);
    check('tecla amarela abre apenas as velocidades inteiras compatíveis com AVPlay',
        window.document.querySelectorAll('#player-menu [data-player-option]').length === 2);
    press(window, 39);
    press(window, 13);
    check('D-pad aplica 2× e atualiza o overlay sem prometer taxas fracionárias',
        selectedSpeeds.indexOf(2) >= 0 &&
        window.document.getElementById('player-speed-label').textContent.indexOf('2×') >= 0 &&
        window.BuroPlayer.playbackRates().join(',') === '1,2');
    press(window, 10009);
    check('fechar o player restaura a árvore acessível do catálogo',
        !window.document.getElementById('app').hasAttribute('aria-hidden'));
    initialSeek = -1;
    window.BuroApp._activate(playFixture);
    window.BuroApp._activate(window.document.querySelector('[data-action="resume-restart"]'));
    check('Assistir do início reproduz sem executar o seek salvo',
        initialSeek === -1 && window.document.body.classList.contains('playing'));
    press(window, 10009);

    var primarySeriesFirst = {
        id: 'episode:series-primary-1', sourceId: 'source-home', categoryId: 'series:primary',
        contentType: 'EPISODE', name: 'Primeiro episódio',
        locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: '501', season: 1, episode: 1, extension: 'mp4' }
    };
    var primarySeriesResumable = {
        id: 'episode:series-primary-2', sourceId: 'source-home', categoryId: 'series:primary',
        contentType: 'EPISODE', name: 'Episódio retomável',
        locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: '502', season: 1, episode: 2, extension: 'mp4' }
    };
    window.BuroApp.state.items.push(primarySeriesFirst, primarySeriesResumable);
    window.BuroApp.state.progress.push({
        id: 'progress:series-primary-2', profileId: window.BuroApp.state.activeProfile.id,
        itemId: primarySeriesResumable.id, positionMs: 45000, durationMs: 120000,
        completed: false, updatedAt: Date.now()
    });
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SERIES';
    window.BuroApp.state.screenData = {
        kind: 'series', parent: { id: 'series:primary', sourceId: 'source-home', contentType: 'SERIES', name: 'Série principal' },
        details: { title: 'Série principal' }, items: [primarySeriesFirst, primarySeriesResumable], expandedSeason: 1
    };
    window.BuroApp.render();
    var resumableSeriesButton = window.document.querySelector('[data-action="series-primary-play"]');
    check('ação principal ignora o primeiro não iniciado e escolhe o episódio retomável',
        resumableSeriesButton && resumableSeriesButton.getAttribute('data-id') === primarySeriesResumable.id &&
        resumableSeriesButton.textContent.indexOf('Continuar T1 E2') >= 0);
    var seriesReturnContent = window.document.querySelector('.content');
    seriesReturnContent.scrollTop = 640;
    resumableSeriesButton.focus();
    initialSeek = -1;
    openedPlaybackUrl = null;
    window.BuroApp._activate(resumableSeriesButton);
    check('Continuar série inicia diretamente o episódio e a posição anunciados',
        window.BuroApp.state.screen === 'SHELL' && initialSeek === 45000 &&
        openedPlaybackUrl && openedPlaybackUrl.indexOf('/502.mp4') >= 0 &&
        window.document.body.classList.contains('playing'));
    avListener.oncurrentplaytime(60000);
    press(window, 10009);
    var returnedSeriesButton = window.document.querySelector('[data-action="series-primary-play"]');
    check('RETURN do AVPlay atualiza imediatamente o progresso visível da série',
        parseFloat(window.document.querySelector('[data-id="' + primarySeriesResumable.id + '"] .media-progress i').style.width) === 50 &&
        returnedSeriesButton && returnedSeriesButton.textContent.indexOf('Continuar T1 E2') >= 0);
    check('recomposição pós-player conserva rolagem e foco do detalhe',
        window.document.querySelector('.content').scrollTop === 640 &&
        returnedSeriesButton.classList.contains('focused') && window.document.activeElement === returnedSeriesButton);
    window.BuroApp.state.progress = window.BuroApp.state.progress.filter(function (row) {
        return row.itemId !== primarySeriesResumable.id;
    });
    window.BuroApp.render();
    var freshSeriesButton = window.document.querySelector('[data-action="series-primary-play"]');
    check('sem retomada, a ação principal oferece o primeiro episódio da série',
        freshSeriesButton && freshSeriesButton.getAttribute('data-id') === primarySeriesFirst.id &&
        freshSeriesButton.textContent.indexOf('Assistir T1 E1') >= 0);
    var firstEpisodeCard = window.document.querySelector('[data-action="play"][data-id="' + primarySeriesFirst.id + '"]');
    var episodeReturnRow = firstEpisodeCard.parentNode.parentNode;
    window.document.querySelector('.content').scrollTop = 720;
    episodeReturnRow.scrollLeft = 180;
    firstEpisodeCard.focus();
    window.BuroApp._activate(firstEpisodeCard);
    avListener.oncurrentplaytime(110000);
    avListener.onstreamcompleted();
    var completedEpisodeCard = window.document.querySelector('[data-action="play"][data-id="' + primarySeriesFirst.id + '"]');
    var completedEpisodeRow = completedEpisodeCard.parentNode.parentNode;
    check('fim natural recompõe o episódio como assistido sem sair do detalhe',
        !window.document.body.classList.contains('playing') &&
        completedEpisodeCard.querySelector('.badge').textContent.indexOf('✓') >= 0 &&
        parseFloat(completedEpisodeCard.querySelector('.media-progress i').style.width) === 100);
    check('fim natural conserva foco, rolagem vertical e posição horizontal da temporada',
        completedEpisodeCard.classList.contains('focused') && window.document.activeElement === completedEpisodeCard &&
        window.document.querySelector('.content').scrollTop === 720 && completedEpisodeRow.scrollLeft === 180);
    window.BuroApp.state.items = window.BuroApp.state.items.filter(function (item) {
        return item.id !== primarySeriesFirst.id && item.id !== primarySeriesResumable.id;
    });

    var livePlayerItem = {
        id: 'live:player-guide', sourceId: 'source-home', categoryId: 'cat-home-live',
        contentType: 'LIVE', name: 'Canal com guia',
        locator: { kind: 'xtream', contentType: 'LIVE', providerItemId: '77', extension: 'ts' }
    };
    var livePlayerSchedule = [
        { title: 'Programa encerrado', description: 'Resumo encerrado', startEpochSeconds: guideNow - 7200, endEpochSeconds: guideNow - 3600 },
        { title: 'Programa atual', description: 'Resumo atual', startEpochSeconds: guideNow - 300, endEpochSeconds: guideNow + 300 },
        { title: 'Proximo programa', description: 'Resumo futuro', startEpochSeconds: guideNow + 300, endEpochSeconds: guideNow + 1800 }
    ];
    window.BuroApp.state.items.push(livePlayerItem);
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = { kind: 'live', parent: livePlayerItem, schedule: livePlayerSchedule };
    var livePlayFixture = window.document.createElement('button');
    livePlayFixture.setAttribute('data-action', 'play');
    livePlayFixture.setAttribute('data-id', livePlayerItem.id);
    window.BuroApp._activate(livePlayFixture);
    check('player ao vivo anuncia guia e favorito, mas nao oferece velocidade VOD',
        !window.document.getElementById('player-guide-label').hidden &&
        !window.document.getElementById('player-favorite-label').hidden &&
        window.document.getElementById('player-speed-label').hidden);
    check('overlay ao vivo mostra programa atual e o proximo como Android',
        !window.document.getElementById('player-programme').hidden &&
        window.document.getElementById('player-programme').textContent.indexOf('Programa atual') >= 0 &&
        window.document.getElementById('player-programme').textContent.indexOf('Proximo programa') >= 0);
    press(window, 404);
    check('tecla verde abre a programacao carregada sem nova consulta ao provedor',
        !window.document.getElementById('player-menu').hidden &&
        window.document.getElementById('player-menu-title').textContent === window.BuroI18n.t('programmeGuide') &&
        window.document.querySelectorAll('#player-menu [data-player-option]').length === 3 &&
        window.document.querySelector('#player-menu [aria-current="true"]').textContent.indexOf('Programa atual') >= 0);
    check('guia completo mantém programa encerrado atenuado e descrições como Android e Windows',
        window.document.querySelector('.player-guide-option.past').textContent.indexOf('Programa encerrado') >= 0 &&
        window.document.querySelector('.player-guide-option.current small').textContent === 'Resumo atual');
    check('rodapé do guia identifica o canal e nunca vaza undefined',
        window.document.getElementById('player-menu-hint').textContent.indexOf('Canal com guia') >= 0 &&
        window.document.getElementById('player-menu-hint').textContent.indexOf('undefined') === -1);
    press(window, 39);
    press(window, 13);
    check('D-pad percorre e fecha o guia sem interromper o canal',
        window.document.getElementById('player-menu').hidden && window.BuroPlayer.isPlaying());
    press(window, 10009);

    window.BuroApp.state.screenData = { kind: 'live', parent: livePlayerItem, schedule: [] };
    window.BuroApp._activate(livePlayFixture);
    check('player ao vivo conserva a ação Programação mesmo sem EPG',
        !window.document.getElementById('player-guide-label').hidden &&
        window.document.getElementById('player-guide-label').textContent.indexOf(window.BuroI18n.t('programmeGuide')) >= 0);
    press(window, 404);
    check('GREEN abre um estado vazio explicativo em vez de desviar para legendas',
        !window.document.getElementById('player-menu').hidden &&
        window.document.getElementById('player-menu-title').textContent === window.BuroI18n.t('programmeGuide') &&
        window.document.querySelector('.player-guide-empty').textContent === window.BuroI18n.t('epgUnavailable') &&
        Boolean(window.document.querySelector('[data-player-close]')));
    press(window, 13);
    check('estado vazio fecha pelo D-pad sem interromper o canal',
        window.document.getElementById('player-menu').hidden && window.BuroPlayer.isPlaying());
    press(window, 10009);

    var riskyMovie = window.BuroApp.state.items.filter(function (item) { return item.id === 'movie:home-two'; })[0];
    var compatibleMovie = {
        id: 'movie:home-two-compatible', sourceId: 'source-home', categoryId: 'cat-home-movies',
        contentType: 'MOVIE', name: 'Filme dois 1080p', sortOrder: 50,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '99', extension: 'mp4' }
    };
    riskyMovie.name = '[4K] Filme dois';
    await new Promise(function (resolve, reject) { window.BuroStorage.put('items', compatibleMovie, resolve, reject); });
    openedPlaybackUrl = null;
    window.BuroApp._activate(playFixture);
    window.BuroApp._activate(window.document.querySelector('[data-action="resume-restart"]'));
    await waitFor(function () { return Boolean(openedPlaybackUrl); }, 1000);
    check('filme 4K HDR DV ou HEVC prefere variante compatível da mesma fonte',
        openedPlaybackUrl.indexOf('/99.mp4') >= 0 &&
        window.document.getElementById('player-title').textContent === '[4K] Filme dois');
    await hold(window, 13, 950);
    window.BuroApp._playbackFailed({ code: 'PLAYBACK_CONNECTION' });
    check('erro de reprodução remove o bloqueio para deixar Retry e Voltar acessíveis',
        window.document.getElementById('player-lock-panel').hidden &&
        !window.document.getElementById('player-overlay').classList.contains('controls-locked') &&
        !window.document.getElementById('player-error-panel').hidden);
    press(window, 10009);

    window.BuroStorage.secureRemove('source-home');

    window.close();
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
