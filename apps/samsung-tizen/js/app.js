/* IPTV BURO Samsung shell. Views never access network or IndexedDB directly. */
var BuroApp = (function () {
    'use strict';

    var root;
    var toast;
    var overlay;
    var playerStatus;
    var playerTitle;
    var playerProgramme;
    var playerProgress;
    var playerTimeline;
    var playerElapsed;
    var playerDuration;
    var playerAudioLabel;
    var playerSubtitleLabel;
    var playerFavoriteLabel;
    var playerGuideLabel;
    var playerSubtitleCue;
    var playerSubtitleText;
    var playerSpeedLabel;
    var playerAspectLabel;
    var playerLockLabel;
    var playerLockPanel;
    var playerLockTitle;
    var playerLockHint;
    var playerReturnLabel;
    var playerRemoteActions;
    var playerMenu;
    var playerMenuTitle;
    var playerMenuOptions;
    var playerMenuHint;
    var playerWaiting;
    var playerWaitingLabel;
    var playerErrorPanel;
    var playerErrorTitle;
    var playerErrorMessage;
    var playerErrorRetry;
    var playerErrorBack;
    var playerErrorActive = false;
    var playerErrorFocus = 0;
    var playerMenuState = null;
    var playerControlsTimer = null;
    var playerEnterTimer = null;
    var playerEnterPressActive = false;
    var playerEnterLongPress = false;
    var playerControlsLocked = false;
    var playerSubtitleTimer = null;
    var focusables = [];
    var focusIndex = 0;
    var toastTimer = null;
    var initialized = false;
    var runtimeReadyReported = false;
    var bootStartedAt = 0;
    var playlistMemory = {};
    var artworkMemory = {};
    var detailBackdropMemory = {};
    var artworkRequests = {};
    var seriesDetailsMemory = {};
    var tmdbDetailsMemory = {};
    var tmdbDetailOrder = [];
    var tmdbTitleRequest = null;
    var criticsRequest = null;
    var tmdbPersonRequest = null;
    var personReturnData = null;
    var subscriptionReturnData = null;
    var subscriptionRequest = null;
    var pendingSharedTitle = null;
    var sharedTitleNoticeVisible = false;
    var sharedTitleResolving = false;
    var sharedTitleResolveId = 0;
    var homeRequestId = 0;
    var homeHeroTimer = null;
    var homeEnrichmentTimer = null;
    var discoverRequestId = 0;
    var discoverSessionKey = null;
    var discoverSessionTaste = { leaningByGenre: {} };
    var discoverJudgedIds = {};
    var discoverReturnData = null;
    var catalogueRequestId = 0;
    var searchRequestId = 0;
    var searchDebounceTimer = null;
    var downloadSearchTimer = null;
    var sourceRefreshRequestId = 0;
    var SEARCH_PAGE_SIZE = 40;
    var SEARCH_DEBOUNCE_MILLIS = 300;
    var CATALOGUE_PAGE_SIZE = 200;
    var EPISODE_PAGE_SIZE = 40;
    var CATEGORY_SETTINGS_PAGE_SIZE = 40;
    var LIBRARY_PAGE_SIZE = 40;
    var FAVORITES_LIMIT = 200;
    var CONTINUE_WATCHING_LIMIT = 20;
    var HISTORY_LIMIT = 60;
    var DOWNLOAD_PAGE_SIZE = 40;
    var DOWNLOAD_SEARCH_DEBOUNCE_MILLIS = 200;
    var currentPlayback = null;
    var playbackResolveRequestId = 0;
    /*
      Sessões de portal Stalker, por fonte, só em memória.

      O token vale dez minutos e é credencial de acesso: gravá-lo junto com o
      catálogo o deixaria sobreviver ao app fechado sem necessidade nenhuma.
      Quando expira, refazemos o handshake a partir do segredo guardado.
    */
    var stalkerSessions = {};
    var HOME_CATALOG_LIMIT = 120;
    var HOME_RAIL_LIMIT = 12;
    var HOME_HERO_LIMIT = 10;
    var HOME_HERO_ROTATION_MILLIS = 10000;
    var PLAYER_LOCK_HOLD_MILLIS = 800;
    var BOOT_MINIMUM_MILLIS = 900;
    var BOOT_POSTER_REVEAL_MILLIS = 1600;
    var MAX_PROFILES = 5;
    var AVATAR_KEYS = ['gold', 'ember', 'forest', 'ocean', 'moon'];
    var SUPPORTED_LANGUAGES = [
        { tag: 'pt-BR', name: 'Português (Brasil)' },
        { tag: 'en', name: 'English' },
        { tag: 'de', name: 'Deutsch' },
        { tag: 'it', name: 'Italiano' },
        { tag: 'es', name: 'Español' }
    ];
    var APP_VERSION_FALLBACK = '3.0.1';
    var TMDB_SIGNUP_URL = 'https://www.themoviedb.org/signup';
    var OMDB_API_KEY_URL = 'https://www.omdbapi.com/apikey.aspx';
    var CATALOGUE_LAYOUTS = ['poster', 'compact', 'list'];
    var CATALOGUE_SORTS = ['provider', 'title-asc', 'title-desc', 'year-desc', 'year-asc', 'rating-desc'];
    var libraryFilters = { MY_BURO: 'ALL', CONTINUE_WATCHING: 'ALL', HISTORY: 'ALL' };
    var libraryPages = { MY_BURO: 0, CONTINUE_WATCHING: 0, HISTORY: 0 };
    /* Gênero e serviço escolhidos em cada aba de catálogo, preservados ao ir e
       voltar de uma categoria. */
    var catalogueScopes = {};
    var downloadFilter = 'ALL';
    var downloadCompact = false;
    var downloadPage = 0;
    var downloadQuery = '';
    var followFocusedDownloadOnRender = false;
    var state = {
        ready: false,
        busy: false,
        preferences: null,
        profiles: [],
        sources: [],
        categories: [],
        items: [],
        favorites: [],
        progress: [],
        reminders: [],
        /* O aviso de lembretes é mostrado uma vez por abertura: a TV não notifica
           com o app fechado, então a entrada é o único momento em que ele cabe. */
        reminderNoticeShown: false,
        unlockedCategoryIds: {},
        activeProfile: null,
        activeSource: null,
        section: 'HOME',
        screen: 'BOOT',
        screenData: null,
        backStack: []
    };

    var NAVIGATION = [
        { section: 'HOME', label: 'home', icon: 'H' },
        { section: 'LIVE', label: 'live', icon: 'TV' },
        { section: 'MOVIES', label: 'movies', icon: 'M' },
        { section: 'SERIES', label: 'series', icon: 'S' },
        { section: 'DISCOVER', label: 'discover', icon: 'D' },
        { section: 'SEARCH', label: 'search', icon: '?' },
        { section: 'MY_BURO', label: 'myBuro', icon: 'B' },
        { section: 'CONTINUE_WATCHING', label: 'continueWatching', icon: '>' },
        { section: 'HISTORY', label: 'history', icon: '@' },
        /* Ao lado de Histórico, e não dentro de Configurações: o destino responde
           "o que eu marquei", que é uma pergunta de biblioteca. Mesma escolha do
           Android. */
        { section: 'REMINDERS', label: 'reminders', icon: '!' },
        { section: 'DOWNLOADS', label: 'downloads', icon: '#' },
        { section: 'PROFILES', label: 'profiles', icon: 'P' },
        { section: 'SOURCES', label: 'sources', icon: '+' },
        { section: 'SETTINGS', label: 'settings', icon: '*' }
    ];

    function navigationEntries() {
        var entries = NAVIGATION.slice();
        var profileId = state.activeProfile && state.activeProfile.id;
        if (state.preferences && BuroTmdb.keyForProfile(profileId)) {
            entries.splice(9, 0, { section: 'SUBSCRIPTIONS', label: 'subscriptions', icon: '$' });
        }
        return entries;
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function attr(value) { return escapeHtml(value); }
    function t(key) { return BuroI18n.t(key); }

    function safeArtworkUrl(value) {
        var url = BuroDomain.trim(value);
        if (!url || url.length > 4096 || !/^https?:\/\/[^\s]+$/i.test(url)) { return null; }
        if (/^https?:\/\/[^\/\s]*@/i.test(url)) { return null; }
        return url;
    }

    function safeProviderLogoUrl(value) {
        var url = BuroDomain.trim(value);
        return /^https:\/\/image\.tmdb\.org\/t\/p\/w92\/[A-Za-z0-9._\/-]{1,240}$/.test(url) &&
            url.indexOf('..') < 0 ? url : null;
    }

    function subscriptionProviderLogo(value) {
        var logo = safeProviderLogoUrl(value);
        return logo ? '<img class="subscription-provider-logo" src="' + attr(logo) + '" alt="" aria-hidden="true">' : '';
    }

    function subscriptionCardLogo(value) {
        var logo = safeProviderLogoUrl(value);
        return logo ? '<i class="subscription-card-logo" aria-hidden="true"><img src="' + attr(logo) + '" alt=""></i>' : '';
    }

    function subscriptionOfferLogo(value) {
        var logo = safeProviderLogoUrl(value);
        return logo ? '<img class="subscription-offer-logo" src="' + attr(logo) + '" alt="" aria-hidden="true">' : '';
    }

    /*
      Teto para os caches de URL de imagem.

      Estes mapas guardam uma entrada por título que passou pela tela, e só
      eram limpos quando um item saía do catálogo. Numa sessão de TV — que dura
      horas, com o catálogo estável — isso significa crescer e nunca soltar.

      O problema não é o texto da URL: é que cada `<img>` que o navegador ainda
      considera alcançável mantém o bitmap decodificado vivo, e um pôster
      500x750 ocupa 1,5 MB decodificado mesmo pesando 40 KB em disco. A TV
      responde à pressão de memória com coleta de lixo agressiva, que aparece
      para o usuário como a interface travando e o controle atrasando.

      Oitocentos cobre bem mais do que se navega numa sessão — o descarte é do
      que ficou para trás, não do que está na tela.
    */
    var ARTWORK_MEMORY_LIMIT = 800;
    var artworkOrder = [];
    var detailBackdropOrder = [];

    /* LRU: reinserir promove a entrada, então o que sai é o menos recente. */
    function rememberUrl(store, order, itemId, url, limit) {
        var position = order.indexOf(itemId);
        if (position >= 0) { order.splice(position, 1); }
        store[itemId] = url;
        order.push(itemId);
        while (order.length > limit) { delete store[order.shift()]; }
    }

    function forgetUrl(store, order, itemId) {
        var position = order.indexOf(itemId);
        if (position >= 0) { order.splice(position, 1); }
        delete store[itemId];
    }

    function rememberArtwork(itemId, value) {
        var url = safeArtworkUrl(value);
        if (itemId && url) {
            rememberUrl(artworkMemory, artworkOrder, itemId, url, ARTWORK_MEMORY_LIMIT);
        }
    }

    function rememberArtworkMap(values) {
        Object.keys(values || {}).forEach(function (itemId) { rememberArtwork(itemId, values[itemId]); });
    }

    function rememberM3uArtwork(entries) {
        (entries || []).forEach(function (entry) {
            if (entry && entry.item) { rememberArtwork(entry.item.id, entry.artworkUrl); }
        });
    }

    function artworkHtml(item, className) {
        var url = item && artworkMemory[item.id];
        return url ? '<span class="' + (className || 'media-art') + '"><img src="' + attr(url) + '" alt=""></span>' : '';
    }

    function heroArtworkHtml(item, enrichment) {
        var stored = item && safeArtworkUrl(artworkMemory[item.id]);
        var backdrop = safeArtworkUrl(enrichment && enrichment.backdropUrl);
        var poster = safeArtworkUrl(enrichment && enrichment.artworkUrl) || stored;
        var primary = backdrop || poster;
        var fallback = backdrop && poster && backdrop !== poster ? poster : null;
        if (!primary) { return ''; }
        return '<span class="hero-art"><img src="' + attr(primary) + '"' +
            (fallback ? ' data-artwork-fallback="' + attr(fallback) + '"' : '') + ' alt=""></span>';
    }

    function rememberDetailBackdrop(itemId, value) {
        var url = safeArtworkUrl(value);
        if (itemId && url) {
            rememberUrl(detailBackdropMemory, detailBackdropOrder, itemId, url,
                ARTWORK_MEMORY_LIMIT);
        }
    }

    function detailArtworkHtml(item) {
        var poster = item && safeArtworkUrl(artworkMemory[item.id]);
        var backdrop = item && safeArtworkUrl(detailBackdropMemory[item.id]);
        if (!backdrop && !poster) { return ''; }
        return '<span class="detail-art"><img src="' + attr(backdrop || poster) + '"' +
            (backdrop && poster && backdrop !== poster ? ' data-artwork-fallback="' + attr(poster) + '"' : '') +
            ' alt=""></span>';
    }

    function finishArtworkRequest(key, category) {
        artworkRequests[key] = 'done';
        if (state.screen === 'SHELL' && state.screenData && state.screenData.kind === 'category' &&
                state.screenData.category.id === category.id) { render(); }
        else if (state.screen === 'SHELL' && state.section === 'HOME' && state.screenData &&
                state.screenData.kind === 'home') { render(); }
    }

    function hydrateCategoryArtwork(category) {
        var source = state.sources.filter(function (row) { return row.id === category.sourceId; })[0];
        var key = category.sourceId + ':' + category.id;
        var secret;
        if (!source || artworkRequests[key] ||
                (window.BuroCatalogueSync && BuroCatalogueSync.contains(category.sourceId, category.id))) { return; }
        artworkRequests[key] = 'loading';
        try { secret = BuroStorage.secureGet(source.id); }
        catch (ignoredSecret) { delete artworkRequests[key]; return; }
        if (source.type === 'XTREAM') {
            BuroXtream.loadItems(secret, source.id, category.contentType, category, function (items, artwork) {
                rememberArtworkMap(artwork);
                finishArtworkRequest(key, category);
            }, function () { delete artworkRequests[key]; });
        } else if (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') {
            readM3uSource(source, secret, function (text) {
                var parsed;
                try { parsed = BuroM3u.parse(text, source.id); }
                catch (ignoredParse) { delete artworkRequests[key]; return; }
                playlistMemory[source.id] = parsed.entries;
                rememberM3uArtwork(parsed.entries);
                finishArtworkRequest(key, category);
            }, function () { delete artworkRequests[key]; });
        } else { delete artworkRequests[key]; }
    }

    function savePreferences() {
        BuroStorage.savePreferences(state.preferences);
        applyPreferences();
    }

    function applyPreferences() {
        var body = document.body;
        BuroI18n.setLanguage(state.preferences.language);
        document.documentElement.setAttribute('lang', state.preferences.language || 'pt-BR');
        body.classList.toggle('reduced-motion', Boolean(state.preferences.reducedMotion));
        body.classList.toggle('high-contrast', Boolean(state.preferences.highContrast));
        body.classList.toggle('reduced-transparency', Boolean(state.preferences.reducedTransparency));
        if (playerTimeline) { playerTimeline.setAttribute('aria-label', t('playbackProgress')); }
        var trailerTimeline = document.getElementById('trailer-timeline');
        if (trailerTimeline) { trailerTimeline.setAttribute('aria-label', t('trailerProgress')); }
    }

    function showToast(message, isError) {
        if (toastTimer) { window.clearTimeout(toastTimer); }
        toast.textContent = message;
        toast.className = isError ? 'toast error' : 'toast';
        toast.setAttribute('role', isError ? 'alert' : 'status');
        toast.setAttribute('aria-live', isError ? 'assertive' : 'polite');
        toast.hidden = false;
        toastTimer = window.setTimeout(function () { toast.hidden = true; }, 4200);
    }

    function formatPlaybackTime(valueMs) {
        var totalSeconds = Math.max(0, Math.floor((Number(valueMs) || 0) / 1000));
        var hours = Math.floor(totalSeconds / 3600);
        var minutes = Math.floor((totalSeconds % 3600) / 60);
        var seconds = totalSeconds % 60;
        function two(value) { return value < 10 ? '0' + value : String(value); }
        return hours > 0 ? hours + ':' + two(minutes) + ':' + two(seconds) : two(minutes) + ':' + two(seconds);
    }

    function updatePlayerTimeline(positionMs, durationMs) {
        var position = Math.max(0, Number(positionMs) || 0);
        var duration = Math.max(0, Number(durationMs) || 0);
        var percentage = duration > 0 ? Math.min(100, position / duration * 100) : 0;
        if (playerElapsed) { playerElapsed.textContent = formatPlaybackTime(position); }
        if (playerDuration) { playerDuration.textContent = duration > 0 ? formatPlaybackTime(duration) : t('live'); }
        if (playerProgress) { playerProgress.style.width = percentage.toFixed(2) + '%'; }
        if (playerTimeline) {
            playerTimeline.setAttribute('aria-valuenow', String(Math.round(percentage)));
            playerTimeline.setAttribute('aria-valuetext', formatPlaybackTime(position) + ' / ' +
                (duration > 0 ? formatPlaybackTime(duration) : t('live')));
        }
    }

    function showPlayerControls() {
        if (!overlay) { return; }
        updatePlayerProgrammeLabel();
        if (playerControlsLocked) {
            overlay.classList.add('controls-hidden');
            return;
        }
        overlay.classList.remove('controls-hidden');
        if (playerControlsTimer) { window.clearTimeout(playerControlsTimer); }
        playerControlsTimer = window.setTimeout(function () {
            if (document.body.classList.contains('playing') && !playerErrorActive) { overlay.classList.add('controls-hidden'); }
        }, 5500);
    }

    function cancelPlayerEnterPress() {
        if (playerEnterTimer) { window.clearTimeout(playerEnterTimer); playerEnterTimer = null; }
        playerEnterPressActive = false;
        playerEnterLongPress = false;
    }

    function resetPlayerControlsLock() {
        cancelPlayerEnterPress();
        playerControlsLocked = false;
        if (overlay) { overlay.classList.remove('controls-locked'); }
        if (playerLockPanel) { playerLockPanel.hidden = true; }
    }

    function setPlayerControlsLocked(locked) {
        playerControlsLocked = Boolean(locked);
        overlay.classList.toggle('controls-locked', playerControlsLocked);
        if (playerLockPanel) { playerLockPanel.hidden = !playerControlsLocked; }
        if (playerControlsLocked) {
            closePlayerMenu();
            if (playerControlsTimer) { window.clearTimeout(playerControlsTimer); playerControlsTimer = null; }
            overlay.classList.add('controls-hidden');
        } else {
            overlay.classList.remove('controls-hidden');
            showPlayerControls();
        }
    }

    function beginPlayerEnterPress() {
        if (playerEnterPressActive) { return; }
        playerEnterPressActive = true;
        playerEnterLongPress = false;
        playerEnterTimer = window.setTimeout(function () {
            playerEnterTimer = null;
            playerEnterLongPress = true;
            setPlayerControlsLocked(!playerControlsLocked);
        }, PLAYER_LOCK_HOLD_MILLIS);
    }

    function finishPlayerEnterPress() {
        var wasLocked;
        if (!playerEnterPressActive) { return; }
        wasLocked = playerControlsLocked;
        if (playerEnterTimer) { window.clearTimeout(playerEnterTimer); playerEnterTimer = null; }
        playerEnterPressActive = false;
        if (!playerEnterLongPress && !wasLocked) { BuroPlayer.togglePause(); }
        playerEnterLongPress = false;
    }

    function clearPlayerSubtitle() {
        if (playerSubtitleTimer) { window.clearTimeout(playerSubtitleTimer); playerSubtitleTimer = null; }
        if (playerSubtitleText) { playerSubtitleText.textContent = ''; }
        if (playerSubtitleCue) { playerSubtitleCue.hidden = true; }
    }

    function cleanSubtitleText(value) {
        return String(value || '')
            .replace(/<br\s*\/?\s*>/gi, '\n')
            .replace(/<[^>]*>/g, '')
            .replace(/&nbsp;/gi, ' ')
            .replace(/&amp;/gi, '&')
            .replace(/&lt;/gi, '<')
            .replace(/&gt;/gi, '>')
            .replace(/&quot;/gi, '"')
            .replace(/&#39;|&apos;/gi, "'")
            .replace(/\r/g, '')
            .substring(0, 1200);
    }

    function showPlayerSubtitle(value, durationMs) {
        var text = cleanSubtitleText(value);
        var size = ['small', 'medium', 'large', 'huge'].indexOf(state.preferences.subtitleSize) >= 0 ?
            state.preferences.subtitleSize : 'medium';
        var colour = ['white', 'yellow', 'grey', 'green', 'cyan'].indexOf(state.preferences.subtitleColour) >= 0 ?
            state.preferences.subtitleColour : 'white';
        var duration = Math.max(500, Math.min(15000, Number(durationMs) || 4000));
        clearPlayerSubtitle();
        if (!text || !playerSubtitleCue || !playerSubtitleText) { return; }
        playerSubtitleCue.className = 'player-subtitle-cue size-' + size + ' colour-' + colour +
            (state.preferences.subtitleBackground ? '' : ' no-background');
        playerSubtitleText.textContent = text;
        playerSubtitleCue.hidden = false;
        playerSubtitleTimer = window.setTimeout(clearPlayerSubtitle, duration);
    }

    function preparePlayerOverlay() {
        resetPlayerControlsLock();
        clearPlayerError();
        closePlayerMenu();
        clearPlayerSubtitle();
        updatePlayerTimeline(0, 0);
        playerAudioLabel.textContent = '▲ ' + t('audioTrack');
        playerSubtitleLabel.textContent = '▼ ' + t('subtitleTrack');
        refreshPlayerContextLabels();
        playerSpeedLabel.hidden = !currentPlayback || currentPlayback.contentType === 'LIVE' || !BuroPlayer.playbackRateAvailable();
        playerSpeedLabel.textContent = 'YELLOW ' + t('playbackSpeed') + ': 1×';
        playerAspectLabel.hidden = !BuroPlayer.displayModeAvailable();
        playerAspectLabel.textContent = 'BLUE ' + t('playerAspectRatio') + ': ' + t('playerScaleOriginal');
        playerLockLabel.textContent = t('playerLockHint');
        playerLockTitle.textContent = t('playerControlsLocked');
        playerLockHint.textContent = t('playerUnlockHint');
        playerReturnLabel.textContent = 'RETURN ' + t('back');
        playerRemoteActions.textContent = '◀ 10s · ENTER ' + t('playPause') + ' · 30s ▶';
        playerWaiting.hidden = false;
        playerWaitingLabel.textContent = t('loading');
        overlay.setAttribute('aria-busy', 'true');
        showPlayerControls();
    }

    function playerSchedule() {
        return currentPlayback && currentPlayback.contentType === 'LIVE' &&
            Array.isArray(currentPlayback.schedule) ? currentPlayback.schedule : [];
    }

    function updatePlayerProgrammeLabel() {
        var schedule = playerSchedule();
        var nowSeconds = Math.floor(Date.now() / 1000);
        var current = null;
        var next = null;
        var parts = [];
        if (!playerProgramme) { return; }
        schedule.forEach(function (program) {
            if (!program || !program.title) { return; }
            var start = Number(program.startEpochSeconds);
            if (epgIsNow(program, nowSeconds)) { current = program; }
            else if (start >= nowSeconds && (!next || start < Number(next.startEpochSeconds))) { next = program; }
        });
        if (current) { parts.push(t('playerNow').replace('{title}', String(current.title))); }
        if (next) { parts.push(t('playerNext').replace('{title}', String(next.title))); }
        playerProgramme.textContent = parts.join(' · ');
        playerProgramme.hidden = !parts.length;
    }

    function refreshPlayerContextLabels() {
        var canFavorite = Boolean(currentPlayback && !currentPlayback.offline &&
            state.activeProfile && findItemAndSource(currentPlayback.itemId).item);
        var isLive = Boolean(currentPlayback && currentPlayback.contentType === 'LIVE');
        if (playerFavoriteLabel) {
            playerFavoriteLabel.hidden = !canFavorite;
            playerFavoriteLabel.textContent = 'RED ' + t(canFavorite && isFavorite(currentPlayback.itemId) ?
                'removeFavorite' : 'addFavorite');
        }
        if (playerGuideLabel) {
            playerGuideLabel.hidden = !isLive;
            playerGuideLabel.textContent = 'GREEN ' + t('programmeGuide');
        }
    }

    function updatePlayerErrorFocus() {
        [playerErrorRetry, playerErrorBack].forEach(function (button, index) {
            button.classList.toggle('focused', index === playerErrorFocus);
        });
        if (playerErrorFocus === 0) { playerErrorRetry.focus(); } else { playerErrorBack.focus(); }
    }

    function clearPlayerError() {
        playerErrorActive = false;
        playerErrorFocus = 0;
        if (overlay) { overlay.classList.remove('playback-error'); }
        if (playerErrorPanel) { playerErrorPanel.hidden = true; }
    }

    function playbackFailureText(error) {
        var code = error && (error.code || error.name || error.message);
        if (code === 'PLAYBACK_CONNECTION') { return t('playbackConnectionError'); }
        if (code === 'PLAYBACK_SOURCE_UNAVAILABLE') { return t('playbackSourceUnavailableError'); }
        if (code === 'PLAYBACK_UNSUPPORTED') { return t('playbackUnsupportedError'); }
        return t('playbackUnknownError');
    }

    function playerStatusText(code, value) {
        var values = {
            PREPARING: 'preparingPlayback', RESUMING: 'resumingPlayback', PLAYING: 'playingStatus',
            PAUSED: 'pausedStatus', ENDED: 'playbackEnded', SUBTITLES_ON: 'subtitlesOn',
            SUBTITLES_OFF: 'subtitlesOff', NO_AUDIO_TRACKS: 'noAudioTracks',
            NO_SUBTITLE_TRACKS: 'noSubtitleTracks', UNAVAILABLE: 'unavailable'
        };
        if (code === 'BUFFERING') { return t('loading') + (value == null ? '' : ' ' + value + '%'); }
        if (code === 'SEEK_FORWARD') { return '+30s'; }
        if (code === 'SEEK_BACK') { return '-10s'; }
        if (code === 'AUDIO_SELECTED') { return t('audioTrack') + ' ' + value; }
        if (code === 'SUBTITLE_SELECTED') { return t('subtitleTrack') + ' ' + value; }
        if (code === 'SPEED') { return t('playbackSpeed') + ' ' + value + '×'; }
        if (code === 'DISPLAY_MODE') { return t('playerAspectRatio') + ': ' + displayModeLabel(value); }
        return t(values[code] || 'loading');
    }

    function displayModeLabel(mode) {
        return t({
            LETTER_BOX: 'playerScaleOriginal',
            FULL_SCREEN: 'playerScaleFill',
            AUTO_ASPECT_RATIO: 'playerScaleAuto'
        }[mode] || 'playerScaleOriginal');
    }

    function cyclePlayerDisplayMode() {
        if (BuroPlayer.cycleDisplayMode()) {
            playerAspectLabel.textContent = 'BLUE ' + t('playerAspectRatio') + ': ' +
                displayModeLabel(BuroPlayer.displayMode());
        }
    }

    function showPlayerError(error) {
        resetPlayerControlsLock();
        playerErrorActive = true;
        playerErrorFocus = 0;
        playerWaiting.hidden = true;
        overlay.hidden = false;
        overlay.classList.remove('controls-hidden');
        overlay.classList.add('playback-error');
        playerErrorTitle.textContent = t('playbackErrorTitle');
        playerErrorMessage.textContent = playbackFailureText(error);
        playerErrorRetry.textContent = t('retryPlayback');
        playerErrorBack.textContent = t('back');
        playerErrorPanel.hidden = false;
        overlay.setAttribute('aria-busy', 'false');
        updatePlayerErrorFocus();
    }

    function retryPlayback() {
        var playback = currentPlayback;
        var position;
        if (!playback) { stopPlayback(); return; }
        position = playback.contentType === 'LIVE' ? 0 : Number(playback.positionMs) || 0;
        clearPlayerError();
        beginPlayback(playback.itemId, position);
    }

    function handlePlayerErrorKey(keyCode) {
        var K = BuroKeys.CODES;
        if (keyCode === K.RETURN) { stopPlayback(); return true; }
        if (keyCode === K.LEFT || keyCode === K.UP) { playerErrorFocus = 0; updatePlayerErrorFocus(); return true; }
        if (keyCode === K.RIGHT || keyCode === K.DOWN) { playerErrorFocus = 1; updatePlayerErrorFocus(); return true; }
        if (keyCode === K.ENTER) {
            if (playerErrorFocus === 0) { retryPlayback(); } else { stopPlayback(); }
            return true;
        }
        return false;
    }

    function closePlayerMenu() {
        playerMenuState = null;
        if (playerMenu) { playerMenu.hidden = true; }
        if (overlay) { overlay.classList.remove('menu-open'); }
    }

    function renderPlayerMenu() {
        var guide;
        if (!playerMenuState || !playerMenu) { return; }
        guide = playerMenuState.type === 'GUIDE';
        playerMenu.classList.toggle('guide', guide);
        playerMenuTitle.textContent = playerMenuState.type === 'AUDIO' ? t('audioTracks') :
            (playerMenuState.type === 'TEXT' ? t('subtitleTracks') :
                (playerMenuState.type === 'GUIDE' ? t('programmeGuide') : t('playbackSpeed')));
        playerMenuOptions.setAttribute('role', guide ? 'list' : 'listbox');
        playerMenuOptions.setAttribute('aria-label', playerMenuTitle.textContent);
        if (guide && playerMenuState.empty) {
            playerMenuOptions.innerHTML = '<p class="player-guide-empty">' + escapeHtml(t('epgUnavailable')) + '</p>' +
                '<button class="player-menu-option focused player-guide-close" tabindex="0" data-player-close="true">' +
                escapeHtml(t('back')) + '</button>';
        } else if (guide) {
            playerMenuOptions.innerHTML = playerMenuState.options.map(function (option, index) {
                return '<button class="player-menu-option player-guide-option ' +
                    (index === playerMenuState.position ? 'focused ' : '') + (option.selected ? 'selected current ' : '') +
                    (option.past ? 'past' : '') + '" role="listitem"' +
                    (option.selected ? ' aria-current="true"' : '') + ' tabindex="' +
                    (index === playerMenuState.position ? '0' : '-1') + '" data-player-option="' + index + '">' +
                    '<time>' + escapeHtml(option.time) + '</time><span><strong>' + escapeHtml(option.title) + '</strong>' +
                    (option.description ? '<small>' + escapeHtml(option.description) + '</small>' : '') + '</span></button>';
            }).join('');
        } else {
            playerMenuOptions.innerHTML = playerMenuState.options.map(function (option, index) {
            return '<button class="player-menu-option ' + (index === playerMenuState.position ? 'focused ' : '') +
                (option.selected ? 'selected' : '') + '" role="option" aria-selected="' +
                (option.selected ? 'true' : 'false') + '" tabindex="' +
                (index === playerMenuState.position ? '0' : '-1') + '" data-player-option="' + index + '">' +
                escapeHtml(option.label) + '</button>';
            }).join('');
        }
        playerMenuHint.textContent = guide && currentPlayback ? currentPlayback.title + ' · ' + t('playerMenuHint') : t('playerMenuHint');
        Array.prototype.slice.call(playerMenuOptions.querySelectorAll('[data-player-option]')).forEach(function (button) {
            button.addEventListener('click', function () {
                playerMenuState.position = Number(button.getAttribute('data-player-option')) || 0;
                choosePlayerMenuOption();
            });
        });
        Array.prototype.slice.call(playerMenuOptions.querySelectorAll('[data-player-close]')).forEach(function (button) {
            button.addEventListener('click', closePlayerMenu);
        });
        var focusedOption = playerMenuOptions.querySelector('[tabindex="0"]');
        if (focusedOption && focusedOption.focus) {
            try { focusedOption.focus(); } catch (ignoredMenuFocus) {}
            if (focusedOption.scrollIntoView) { focusedOption.scrollIntoView({ block: 'nearest' }); }
        }
    }

    function openPlayerMenu(type) {
        var nowSeconds = Math.floor(Date.now() / 1000);
        var options = type === 'SPEED' ? BuroPlayer.playbackRates().map(function (rate) {
            return { index: rate, label: rate + '×', selected: rate === BuroPlayer.playbackRate(), speed: true };
        }) : (type === 'GUIDE' ? playerSchedule().slice(0, 100).map(function (program, index) {
            return {
                index: index,
                label: String(program.title || ''), title: String(program.title || ''),
                description: String(program.description || ''),
                time: epgClock(program, false) + '–' + epgClock(program, true),
                selected: epgIsNow(program, nowSeconds),
                past: Number(program.endEpochSeconds) > 0 && Number(program.endEpochSeconds) <= nowSeconds,
                guide: true
            };
        }) : BuroPlayer.trackOptions(type));
        var selected = 0;
        if (type === 'SPEED' && (!currentPlayback || currentPlayback.contentType === 'LIVE')) { return; }
        if (type === 'TEXT') {
            options.unshift({ index: null, label: t('subtitlesOff'), selected: false, off: true });
        }
        if (type === 'SPEED' && !options.length) { return; }
        if (!options.length && type !== 'GUIDE') {
            playerStatus.textContent = type === 'AUDIO' ? t('noAudioTracks') : t('noSubtitleTracks');
            return;
        }
        options.some(function (option, index) {
            if (option.selected) { selected = index; return true; }
            return false;
        });
        playerMenuState = { type: type, options: options, position: selected, empty: type === 'GUIDE' && !options.length };
        playerMenu.hidden = false;
        overlay.classList.add('menu-open');
        renderPlayerMenu();
        showPlayerControls();
    }

    function movePlayerMenu(offset) {
        var length = playerMenuState && playerMenuState.options.length;
        if (!length) { return; }
        playerMenuState.position = (playerMenuState.position + offset + length) % length;
        renderPlayerMenu();
    }

    function choosePlayerMenuOption() {
        var option;
        var selected;
        if (!playerMenuState) { return; }
        if (playerMenuState.empty) { closePlayerMenu(); return; }
        option = playerMenuState.options[playerMenuState.position];
        if (option.guide) { closePlayerMenu(); return; }
        selected = option.speed ? BuroPlayer.setPlaybackRate(option.index) :
            (option.off ? BuroPlayer.disableSubtitles() : BuroPlayer.selectTrack(playerMenuState.type, option.index));
        if (selected) {
            if (playerMenuState.type === 'AUDIO') { playerAudioLabel.textContent = '▲ ' + t('audioTrack') + ': ' + option.label; }
            else if (playerMenuState.type === 'TEXT') { playerSubtitleLabel.textContent = '▼ ' + t('subtitleTrack') + ': ' + option.label; }
            else { playerSpeedLabel.textContent = 'YELLOW ' + t('playbackSpeed') + ': ' + option.label; }
            closePlayerMenu();
        }
    }

    /*
      Traduz um código de falha para algo acionável.

      Cada causa tem a sua mensagem porque a saída é diferente: armazenamento
      cheio não se resolve mexendo na internet, e mandar o usuário checar a
      conexão nesse caso o faz reiniciar o roteador à toa.

      As mensagens vêm do i18n; escrevê-las aqui em português deixaria as
      outras quatro línguas com texto solto em telas de erro.
    */
    function friendlyError(error) {
        var code = error && (error.code || error.message || error.name);
        var known = {
            SECURE_STORE_UNAVAILABLE: 'secureStoreUnavailable',
            INDEXED_DB_UNAVAILABLE: 'storageUnavailable',
            STORAGE_WRITE_FAILED: 'storageFull',
            CREDENTIALS_REQUIRED: 'credentialsRequired',
            SERVER_URL_INVALID: 'serverUrlInvalid',
            AUTH_REJECTED: 'authRejected',
            M3U_HEADER_REQUIRED: 'playlistInvalid',
            MALFORMED_JSON: 'sourceError',
            RESPONSE_TOO_LARGE: 'responseTooLarge',
            NETWORK_TIMEOUT: 'networkTimeout',
            NETWORK_ERROR: 'networkError',
            HTTP_ERROR: 'httpError',
            PIN_FORMAT_INVALID: 'pinFormat',
            WEB_CRYPTO_UNAVAILABLE: 'pinCryptoError',
            SECURE_RANDOM_UNAVAILABLE: 'pinCryptoError',
            /* Stalker/Ministra. Um portal que recusa o MAC e um portal
               inalcançável pedem conselhos muito diferentes, então cada causa
               mantém a sua própria mensagem em vez de virar "não deu certo". */
            UNAUTHORISED: 'stalkerErrorUnauthorised',
            PORTAL_BLOCKED: 'stalkerErrorBlocked',
            PORTAL_URL_INVALID: 'stalkerErrorInvalid',
            MAC_INVALID: 'stalkerErrorInvalid',
            SESSION_EXPIRED: 'stalkerErrorExpired',
            MALFORMED: 'stalkerErrorMalformed',
            MALFORMED_CATEGORIES: 'stalkerErrorMalformed',
            MALFORMED_CATALOG: 'stalkerErrorMalformed',
            MALFORMED_PLAYBACK: 'stalkerErrorMalformed',
            TRANSPORT_UNAVAILABLE: 'stalkerErrorNetwork'
        };
        return t(known[code] || 'sourceError');
    }

    /*
      Uma transação por bloco, não por item — ver BuroStorage.putBatch. Com um
      catálogo de dezenas de milhares de canais, a diferença é a TV responder
      ou ficar parada com o controle morto.
    */
    function putMany(storeName, values, done, failed) {
        BuroStorage.putBatch(storeName, values, function () { done(); }, failed);
    }

    function removeMany(storeName, values, done, failed) {
        var index = 0;
        function next() {
            if (index >= values.length) { done(); return; }
            BuroStorage.remove(storeName, values[index].id, function () { index += 1; next(); }, failed);
        }
        next();
    }

    function loadInitialData(done, failure) {
        var names = ['profiles', 'sources', 'categories', 'favorites', 'progress', 'reminders'];
        var result = {};
        var pending = names.length + 1;
        var failed = false;
        function complete(name, rows) {
            result[name] = rows || [];
            pending -= 1;
            if (!pending && !failed) {
                hydrateReferencedItems(result, done, function (error) {
                    if (!failed) { failed = true; failure(error); }
                });
            }
        }
        names.forEach(function (name) {
            BuroStorage.all(name, function (rows) {
                complete(name, rows);
            }, function (error) {
                if (!failed) { failed = true; failure(error); }
            });
        });
        /*
          A Home precisa apenas de uma amostra inicial. Ler todo o catálogo aqui
          tornava o boot proporcional ao tamanho da lista; categorias completas
          continuam sendo abertas pelo índice `byCategory` quando solicitadas.
        */
        BuroStorage.take('items', HOME_CATALOG_LIMIT, function (rows) {
            complete('items', rows);
        }, function (error) {
            if (!failed) { failed = true; failure(error); }
        });
    }

    function refreshActiveReferences() {
        var profileId = state.preferences.activeProfileId;
        var sourceId;
        state.activeProfile = null;
        state.profiles.forEach(function (profile) {
            if (profile.id === profileId) { state.activeProfile = profile; }
        });
        if (!state.activeProfile && state.profiles.length) { state.activeProfile = state.profiles[0]; }
        if (state.activeProfile) {
            state.preferences.activeProfileId = state.activeProfile.id;
            sourceId = state.activeProfile.sourceId;
        }
        state.activeSource = null;
        state.sources.forEach(function (source) {
            if (source.id === sourceId) { state.activeSource = source; }
        });
        if (!state.activeSource && state.sources.length) { state.activeSource = state.sources[0]; }
    }

    /* Mesmos estados universais usados pelo Android: perfil, catálogo, arte e pronto. */
    var BOOT_STEPS = ['profiles', 'catalogue', 'artwork', 'ready'];

    function bootProgress(stepName, messageKey) {
        var index = 0;
        while (index < BOOT_STEPS.length && BOOT_STEPS[index] !== stepName) { index += 1; }
        state.boot = {
            step: stepName,
            index: index,
            total: BOOT_STEPS.length,
            messageKey: messageKey
        };
        if (state.screen === 'BOOT') { render(); }
    }

    function finishInitialization() {
        var targetScreen;
        var minimum;
        var remaining;
        bootProgress('ready', 'bootReady');
        state.ready = true;
        if (!state.preferences.acceptedLegal) { targetScreen = 'LEGAL'; }
        else if (!state.profiles.length) { targetScreen = 'PROFILES'; }
        else { targetScreen = 'SHELL'; }
        minimum = state.sources.length ? BOOT_POSTER_REVEAL_MILLIS : BOOT_MINIMUM_MILLIS;
        remaining = Math.max(0, minimum - (Date.now() - bootStartedAt));
        window.setTimeout(function () {
            state.screen = targetScreen;
            if (targetScreen === 'SHELL') { state.section = state.preferences.section || 'HOME'; }
            render();
            if (targetScreen === 'SHELL') {
                window.setTimeout(function () { startActiveSourceHydration(false); }, 0);
                /* Depois do shell aparecer, não durante o boot: um aviso sobre a
                   tela de carregamento seria lido como erro. */
                window.setTimeout(showReminderNoticeOnce, 1200);
            }
        }, remaining);
    }

    function initializeData() {
        if (window.BuroHeroEnrichment) { BuroHeroEnrichment.cancel(); }
        if (window.BuroTrailer) { BuroTrailer.close(); }
        if (tmdbTitleRequest && tmdbTitleRequest.abort) { tmdbTitleRequest.abort(); }
        if (tmdbPersonRequest && tmdbPersonRequest.abort) { tmdbPersonRequest.abort(); }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        tmdbTitleRequest = null;
        tmdbPersonRequest = null;
        subscriptionRequest = null;
        personReturnData = null;
        subscriptionReturnData = null;
        bootStartedAt = Date.now();
        state.ready = false;
        state.preferences = BuroStorage.loadPreferences();
        applyPreferences();
        if (!state.preferences.languageSelected) {
            state.screen = 'LANGUAGE';
            render();
            return;
        }
        state.screen = 'BOOT';
        bootProgress('profiles', 'bootProfiles');
        BuroStorage.open(function () {
            bootProgress('catalogue', 'bootCatalogue');
            loadInitialData(function (data) {
                state.profiles = data.profiles;
                state.sources = data.sources;
                state.categories = data.categories;
                state.items = data.items;
                state.favorites = data.favorites;
                state.progress = data.progress;
                state.reminders = data.reminders || [];
                refreshActiveReferences();
                bootProgress('artwork', 'bootArtwork');
                /* Um quadro permite que o WebView pinte a arte antes de liberar o shell. */
                window.setTimeout(finishInitialization, 16);
            }, function (error) {
                state.ready = true;
                state.screen = 'ERROR';
                state.screenData = friendlyError(error);
                render();
            });
        }, function (error) {
            state.ready = true;
            state.screen = 'ERROR';
            state.screenData = friendlyError(error);
            render();
        });
    }

    function safeProfilePhoto(value) {
        return BuroProfilePhoto.safe(value);
    }

    function profileAvatarContent(profile) {
        var photo = safeProfilePhoto(profile && profile.photoDataUrl);
        return photo ? '<img src="' + attr(photo) + '" alt="">' : escapeHtml(profile.name.charAt(0).toUpperCase());
    }

    function focusLabel(profile) {
        var avatarKey = AVATAR_KEYS.indexOf(profile.avatarKey) >= 0 ? profile.avatarKey : 'gold';
        return '<div class="avatar ' + avatarKey + ' ' + (profile.isKids ? 'kids' : '') + '">' +
            profileAvatarContent(profile) + '</div><strong>' +
            escapeHtml(profile.name) + '</strong><small>' + (profile.isKids ? t('kidsProfile') : 'BURO') + '</small>';
    }

    function renderBoot() {
        var boot = state.boot || { index: 0, total: BOOT_STEPS.length, messageKey: 'bootProfiles' };
        var progressValue = Math.min(100, Math.round(((boot.index + 1) / boot.total) * 100));
        var progressLabel = t(boot.messageKey);
        var dots = BOOT_STEPS.map(function (step, index) {
            var status = index < boot.index ? 'complete' : (index === boot.index ? 'active' : '');
            return '<span class="boot-dot ' + status + '" aria-hidden="true"></span>';
        }).join('');
        root.innerHTML = '<main class="boot-screen">' +
            '<div class="boot-backdrop" aria-hidden="true"><span></span><span></span><span></span><span></span></div>' +
            '<div class="boot-vignette" aria-hidden="true"></div>' +
            '<section class="boot-panel" role="status" aria-live="polite" aria-atomic="true">' +
            '<div class="boot-mark" aria-hidden="true"><span>B</span></div>' +
            '<p class="boot-brand">IPTV BURO</p>' +
            '<p class="boot-message">' + progressLabel + '</p>' +
            '<p class="boot-stage">' + t('bootStageLabel') + '</p>' +
            '<div class="boot-indicator" aria-hidden="true"></div>' +
            '<div class="boot-progress" role="progressbar" aria-label="' + attr(progressLabel) +
            '" aria-valuemin="0" aria-valuemax="100" aria-valuenow="' + progressValue + '">' +
            '<span style="width: ' + progressValue + '%"></span></div>' +
            '<div class="boot-dots" aria-hidden="true">' + dots + '</div>' +
            '</section>' +
            '</main>';
    }

    function renderLanguageSelection() {
        var rows = SUPPORTED_LANGUAGES.map(function (language) {
            return '<button class="language-option focusable" data-action="select-language" data-language="' +
                attr(language.tag) + '">' + escapeHtml(language.name) + '</button>';
        }).join('');
        root.innerHTML = '<main class="language-screen"><section class="language-panel">' +
            '<p class="language-brand">IPTV&nbsp;&nbsp;BURO</p>' +
            '<h1>Escolha seu idioma</h1><p class="language-subtitle">Choose your language</p>' +
            '<div class="language-list">' + rows + '</div>' +
            '<p class="language-hint">Você poderá alterar isso em Configurações.</p>' +
            '</section></main>';
    }

    function renderLegal() {
        root.innerHTML = '<main class="gate-screen legal-screen">' +
            '<section class="legal-brand" aria-label="IPTV BURO"><div class="legal-brand-mark">' +
            '<img src="icon.png" alt=""></div><span>IPTV</span><strong>BURO</strong></section>' +
            '<section class="legal-card"><h1>' + t('legalTitle') + '</h1>' +
            '<div class="legal-copy"><p>' + t('legalBody') + '</p><p>' + t('legalBodyTwo') +
            '</p><p>' + t('legalBodyThree') + '</p></div>' +
            '<p class="legal-privacy">' + t('legalPrivacy') + '</p>' +
            '<button class="button primary focusable legal-accept" data-action="legal-accept">' +
            t('legalAccept') + '</button></section></main>';
    }

    function renderProfiles() {
        var management = state.screen === 'SHELL';
        var cards = state.profiles.map(function (profile) {
            var card = '<button class="profile-card focusable" data-action="select-profile" data-id="' + attr(profile.id) + '">' +
                focusLabel(profile) + '</button>';
            if (!management) { return card; }
            return '<div class="profile-entry">' + card +
                '<button class="profile-edit focusable" data-action="profile-edit" data-id="' + attr(profile.id) + '">' +
                t('editProfile') + '</button></div>';
        }).join('');
        if (state.profiles.length < MAX_PROFILES) {
            cards += '<button class="profile-card focusable" data-action="profile-form"><div class="avatar gold">+</div>' +
                '<strong>' + t('addProfile') + '</strong><small>' + state.profiles.length + '/' + MAX_PROFILES + '</small></button>';
        }
        if (management) {
            shell('<p class="profile-help">' + t('manageProfiles') + '</p><div class="profile-row">' + cards + '</div>', t('profiles'), true);
        } else {
            root.innerHTML = '<main class="gate-screen"><div class="brand-mark">B</div><h1>' + t('profiles') +
                '</h1><div class="profile-row">' + cards + '</div></main>';
        }
    }

    function renderProfileForm() {
        var data = state.screenData || {};
        var avatarKey = AVATAR_KEYS.indexOf(data.avatarKey) >= 0 ? data.avatarKey : 'gold';
        var photo = safeProfilePhoto(data.photoDataUrl);
        var avatarChoices = AVATAR_KEYS.map(function (key) {
            return '<button class="avatar-choice avatar ' + key + ' focusable ' + (avatarKey === key ? 'selected' : '') +
                '" data-action="profile-avatar" data-avatar="' + key + '">' +
                escapeHtml((data.profileName || 'B').charAt(0).toUpperCase()) + '</button>';
        }).join('');
        var sourceChoices = '<button class="source-choice focusable ' + (!data.sourceId ? 'selected' : '') +
            '" data-action="profile-source" data-id="">' + t('anySource') + '</button>' +
            state.sources.map(function (source) {
                return '<button class="source-choice focusable ' + (data.sourceId === source.id ? 'selected' : '') +
                    '" data-action="profile-source" data-id="' + attr(source.id) + '">' + escapeHtml(source.name) + '</button>';
            }).join('');
        var deleteButton = data.editingId && state.profiles.length > 1 ?
            '<button class="button danger focusable" data-action="profile-delete">' +
                (data.confirmDelete ? t('confirmDeleteProfile') : t('deleteProfile')) + '</button>' : '';
        root.innerHTML = '<main class="gate-screen profile-form-screen"><div class="form-panel profile-form-panel"><h2>' +
            (data.editingId ? t('editProfile') : t('addProfile')) + '</h2>' +
            '<div class="field"><label>' + t('profileName') + '</label><input id="profile-name" class="focusable" maxlength="40" value="' + attr(data.profileName || '') + '"></div>' +
            '<div class="field profile-photo-field"><label>' + t('profilePhoto') + '</label><div class="profile-photo-controls"><div class="avatar profile-photo-preview ' +
            avatarKey + '">' + (photo ? '<img src="' + attr(photo) + '" alt="">' :
                escapeHtml((data.profileName || 'B').charAt(0).toUpperCase())) + '</div><div><div class="action-row">' +
            (BuroUsb.hasStorage() ? '<button class="button ghost focusable" data-action="profile-photo-choose">' + t('chooseProfilePhoto') + '</button>' : '') +
            (photo ? '<button class="button ghost focusable" data-action="profile-photo-remove">' + t('removeProfilePhoto') + '</button>' : '') +
            '</div><p class="form-message">' + (BuroUsb.hasStorage() ? t('profilePhotoHint') : t('profilePhotoUsb')) +
            '</p></div></div></div><div class="field"><label>' + t('avatar') + '</label><div class="avatar-choice-row">' + avatarChoices + '</div></div>' +
            '<div class="legal-check focusable ' + (data.kids ? 'checked' : '') + '" data-action="kids-toggle"><span class="check-box"></span><span>' + t('kidsProfile') + '</span></div>' +
            '<div class="field"><label>' + t('profileSource') + '</label><div class="source-choice-row">' + sourceChoices + '</div></div>' +
            '<div class="action-row"><button class="button primary focusable" data-action="profile-save">' + t('save') + '</button>' +
            '<button class="button ghost focusable" data-action="back">' + t('cancel') + '</button>' + deleteButton + '</div></div></main>';
    }

    function safeUsbPhotoPreview(value) {
        var uri = String(value || '');
        return /^(file|filesystem):\/\//i.test(uri) && uri.length <= 2048 ? uri : null;
    }

    function renderProfilePhotoPicker() {
        var data = state.screenData || { loading: true, images: [] };
        var body;
        if (data.loading) {
            body = '<div class="catalogue-loading"><span class="boot-indicator"></span><p>' +
                t(data.reading ? 'profilePhotoReading' : 'profilePhotoLoading') + '</p></div>';
        } else if (data.error) {
            body = emptyState('!', t('profilePhotoError'), t('profilePhotoErrorBody'), 'profile-photo-retry', t('retry'));
        } else if (!data.images.length) {
            body = emptyState('USB', t('profilePhotoEmpty'), t('profilePhotoEmptyBody'), '', '');
        } else {
            body = '<div class="profile-photo-grid">' + data.images.map(function (image) {
                var preview = safeUsbPhotoPreview(BuroUsb.imagePreviewUrl(image.key));
                return '<button class="profile-photo-choice focusable" data-action="profile-photo-select" data-key="' +
                    attr(image.key) + '">' + (preview ? '<img src="' + attr(preview) + '" alt="">' : '<span>IMG</span>') +
                    '<strong>' + escapeHtml(image.name) + '</strong></button>';
            }).join('') + '</div>';
        }
        root.innerHTML = '<main class="gate-screen profile-photo-screen"><section class="profile-photo-panel"><h1>' +
            t('chooseProfilePhoto') + '</h1><p>' + t('profilePhotoPickerHint') + '</p>' + body +
            '<button class="button ghost focusable" data-action="back">‹ ' + t('back') + '</button></section></main>';
    }

    function renderResumePrompt() {
        var data = state.screenData || {};
        var item = data.offlineDownloadId ? BuroDownloads.list().filter(function (entry) {
            return entry.id === data.offlineDownloadId && entry.state === 'COMPLETED';
        })[0] : findItemAndSource(data.itemId).item;
        if (!item) { goBack(); return; }
        root.innerHTML = '<main class="resume-screen"><section class="resume-panel">' + artworkHtml(item, 'resume-art') +
            '<span class="hero-kicker">IPTV BURO</span><h1>' + t('resumeQuestion') + '</h1><h2>' +
            escapeHtml(item.name) + '</h2><p>' + t('resumeSavedAt').replace('{time}', formatPlaybackTime(data.positionMs)) +
            '</p><div class="action-row"><button class="button primary focusable" data-action="resume-continue">' +
            t('resumeFrom').replace('{time}', formatPlaybackTime(data.positionMs)) + '</button>' +
            '<button class="button ghost focusable" data-action="resume-restart">' + t('startOver') +
            '</button><button class="button ghost focusable" data-action="back">' + t('cancel') + '</button></div></section></main>';
    }

    function renderBulkDownloadConfirm() {
        var data = state.screenData || {};
        var count = (data.items || []).length;
        var season = data.season == null ? null : Number(data.season);
        var title = season == null ? t('downloadSeriesConfirmTitle') :
            t('downloadSeasonConfirmTitle').replace('{season}', season);
        var body = t(count === 1 ? 'downloadBatchConfirmOne' : 'downloadBatchConfirmMany')
            .replace('{count}', count);
        if (!count || !BuroDownloads.enabled()) { goBack(); return; }
        root.innerHTML = '<main class="resume-screen"><section class="resume-panel"><span class="hero-kicker">USB · IPTV BURO</span><h1>' +
            escapeHtml(title) + '</h1><h2>' + escapeHtml(data.title || '') + '</h2><p>' + escapeHtml(body) +
            '</p><div class="action-row"><button class="button primary focusable" data-action="bulk-download-confirm">' +
            t('download') + '</button><button class="button ghost focusable" data-action="back">' + t('cancel') +
            '</button></div></section></main>';
    }

    function navHtml() {
        return navigationEntries().map(function (entry) {
            return '<li class="nav-item focusable ' + (state.section === entry.section ? 'selected' : '') +
                '" role="button"' + (state.section === entry.section ? ' aria-current="page"' : '') +
                ' data-action="section" data-section="' + entry.section + '"><span class="nav-icon">' + entry.icon +
                '</span><span>' + t(entry.label) + '</span></li>';
        }).join('');
    }

    function shell(content, title, scrollable, topbarExtra) {
        var profileName = state.activeProfile ? state.activeProfile.name : t('profiles');
        root.innerHTML = '<div class="shell"><nav class="buro-ribbon" aria-label="IPTV BURO"><div class="nav-brand">' +
            '<span>IPTV</span><strong>BURO</strong></div><ul class="nav-list">' + navHtml() + '</ul>' +
            '<button class="ribbon-profile focusable" data-action="section" data-section="PROFILES" aria-label="' +
            attr(profileName) + '"><span class="ribbon-avatar ' + (state.activeProfile && state.activeProfile.isKids ? 'kids' : '') + '">' +
            profileAvatarContent(state.activeProfile || { name: profileName }) + '</span><strong>' + escapeHtml(profileName) + '</strong></button></nav>' +
            '<main class="main-pane" aria-labelledby="screen-title"><header class="topbar"><h1 id="screen-title">' + escapeHtml(title) +
            '</h1>' + (topbarExtra || '') + notificationBellHtml() + '<span class="platform-chip">Samsung Tizen</span></header><section class="content ' + (scrollable ? 'scrollable' : '') + '">' +
            sharedTitleNoticeHtml() + content + '</section><div class="bottom-hint">' + t('useArrows') + '</div></main></div>';
    }

    function sharedTitleNoticeHtml() {
        var title;
        if (!sharedTitleNoticeVisible || !pendingSharedTitle) { return ''; }
        title = pendingSharedTitle.title;
        return '<aside class="shared-link-notice" role="alertdialog" aria-labelledby="shared-link-title" aria-describedby="shared-link-body">' +
            '<span class="shared-link-symbol" aria-hidden="true">↗</span><div><h2 id="shared-link-title">' +
            t('sharedMissingTitle') + '</h2><p id="shared-link-body">' +
            escapeHtml(t('sharedMissingBody').replace('{title}', title)) + '</p></div><div class="shared-link-actions">' +
            '<button class="button primary focusable" data-action="shared-retry">' + t('sharedRetry') + '</button>' +
            '<button class="button ghost focusable" data-action="shared-dismiss">' + t('sharedDismiss') + '</button></div></aside>';
    }

    function emptyState(symbol, title, body, action, label) {
        return '<div class="empty-state"><div class="empty-symbol">' + symbol + '</div><h2>' + escapeHtml(title) +
            '</h2><p>' + escapeHtml(body) + '</p>' + (action ? '<button class="button primary focusable" data-action="' + action + '">' +
            escapeHtml(label) + '</button>' : '') + '</div>';
    }

    function homeRail(title, items, key, service) {
        var identity = service ? BuroProviders.identityForLabel(service) : null;
        if (!items.length) { return ''; }
        return '<section class="home-rail" data-home-rail="' + attr(key || '') + '"><div class="section-heading home-rail-heading">' +
            (identity ? '<h2>' + providerBadge(identity) + escapeHtml(title) + '</h2>' :
                '<h2>' + escapeHtml(title) + '</h2>') + '<p>' +
            items.length + '</p></div>' +
            /* Lembretes trazem entradas próprias, não linhas do catálogo: parte
               delas não tem item para `mediaCards` desenhar. */
            (key === 'reminders' ? reminderCardsHtml(items) : mediaCards(items.slice(0, 12))) +
            '</section>';
    }

    /*
      Os lembretes deste perfil, prontos para virar cards.

      Um lembrete guarda identidade, não id de linha, então o item do catálogo é
      reencontrado aqui — e pode não existir, que é o caso normal de um título
      que ainda vai sair. Quando existe, o card é o mesmo dos outros trilhos e
      abre os detalhes; quando não existe, ainda assim aparece, com a contagem no
      lugar da ação, senão o lembrete mais importante seria o único invisível.

      Um item que o controle parental esconde é descartado: um lembrete não pode
      virar uma porta lateral para conteúdo bloqueado num perfil Kids.
    */
    function reminderCards() {
        var identities = {};
        var resolved = [];
        state.items.forEach(function (item) {
            var identity = BuroDomain.contentIdentity(item);
            if (!state.activeSource || item.sourceId !== state.activeSource.id) { return; }
            if (identity && !identities[identity]) { identities[identity] = item; }
        });
        BuroDomain.sortReminders(profileReminders()).forEach(function (reminder) {
            var item = identities[reminder.identity];
            if (item && !itemVisible(item)) { return; }
            resolved.push({ reminder: reminder, item: item || null });
        });
        return resolved;
    }

    function reminderItemCanOpen(item) {
        if (!item || !state.activeSource || item.sourceId !== state.activeSource.id) { return false; }
        if (item.contentType === 'MOVIE') { return true; }
        return item.contentType === 'SERIES' && state.activeSource.type === 'XTREAM';
    }

    /* Dias que faltam, ou null quando não há data utilizável. */
    function reminderCountdown(reminder) {
        var digest = BuroDomain.reminderDigest([reminder]);
        if (digest.releasedToday.length) { return 0; }
        if (digest.upcoming.length) { return digest.upcoming[0].days; }
        return null;
    }

    /*
      O que este lembrete está esperando, dito como a lista diz.

      Lê os mesmos três casos em que a política separa um digest, para que a
      linha e o aviso nunca discordem sobre se algo já saiu. Mesma função do
      `statusLabel` no Android.
    */
    function reminderStatusLabel(reminder) {
        var days = reminderCountdown(reminder);
        if (days === null) { return t('reminderStatusWaiting'); }
        if (days === 0) { return t('reminderStatusReleased'); }
        return t(days === 1 ? 'reminderInDay' : 'reminderInDays').replace('{count}', days);
    }

    /*
      A arte de um lembrete, ou a inicial do título.

      Vazio com muito mais frequência do que parece: o pôster hospedado pelo
      provedor é descartado na entrada do banco quando carrega credencial, e um
      lembrete sobrevive à lista de onde veio. Então este é o caso comum, não o
      raro — e por isso usa a inicial do título em vez de um símbolo fixo: toda
      linha teria o mesmo símbolo, e uma lista de ícones idênticos não diz nada
      sobre qual título é qual. Mesma decisão do Android.
    */
    function reminderArtHtml(reminder, className) {
        var initial = BuroDomain.trim(reminder.title).charAt(0).toUpperCase() || '?';
        if (reminder.artworkUrl) {
            return '<span class="' + className + ' reminder-art" style="background-image:url(' +
                attr(reminder.artworkUrl) + ')"></span>';
        }
        return '<span class="' + className + ' reminder-art reminder-art-initial">' +
            escapeHtml(initial) + '</span>';
    }

    /*
      Os cards do trilho da Home.

      O selo é sempre a mesma palavra, como no Android; o que muda por título é a
      linha de baixo, com a data de estreia ou o estado. Pressionar qualquer card
      leva à página de Lembretes — inclusive um que tem item no catálogo — porque
      o trilho existe para mostrar o que está por vir, e um título que ainda não
      saiu não tem o que reproduzir.
    */
    function reminderCardsHtml(entries) {
        return '<div class="card-row catalogue-layout-poster">' + entries.map(function (entry) {
            var reminder = entry.reminder;
            return '<button class="media-card focusable poster reminder-card' +
                (reminder.artworkUrl ? ' has-art' : '') +
                '" data-action="section" data-section="REMINDERS">' +
                reminderArtHtml(reminder, 'media-art') +
                '<span class="badge">' + escapeHtml(t('reminderBadge')) + '</span><h3>' +
                escapeHtml(reminder.title) + '</h3><p>' +
                escapeHtml(reminder.releaseDate || reminderStatusLabel(reminder)) +
                '</p></button>';
        }).join('') + '</div>';
    }

    /*
      A página de lembretes: uma linha por título, não uma grade de pôsteres.

      A referência Android é uma lista vertical com arte pequena à esquerda,
      título e estado no meio e Remover à direita. Quando a identidade já
      corresponde a um filme ou série local, a parte principal também abre os
      detalhes; um lançamento ainda ausente continua visível, mas não finge ser
      clicável. A grade fica na Home, onde o trilho é de navegação.
    */
    function reminderRowsHtml(entries) {
        return '<div class="reminder-list">' + entries.map(function (entry) {
            var reminder = entry.reminder;
            var item = entry.item;
            var openable = reminderItemCanOpen(item);
            var status = reminderStatusLabel(reminder) +
                (openable ? '' : ' · ' + t(item ? 'unavailable' : 'reminderNotInLibrary'));
            var summary = reminderArtHtml(reminder, 'reminder-row-art') +
                '<div class="reminder-row-text"><h3>' + escapeHtml(reminder.title) + '</h3><p>' +
                escapeHtml(status) + '</p></div>';
            return '<div class="reminder-row">' +
                (openable ? '<button class="reminder-row-open focusable" data-action="reminder-open" data-id="' +
                    attr(item.id) + '" aria-label="' + attr(t('viewDetails') + ': ' + reminder.title) + '">' +
                    summary + '</button>' : '<div class="reminder-row-open reminder-row-static">' +
                    summary + '</div>') +
                '<button class="button ghost focusable" data-action="reminder-remove" data-id="' +
                attr(reminder.id) + '" aria-label="' +
                attr(t('reminderRemove') + ': ' + reminder.title) + '">' +
                t('reminderRemove') + '</button></div>';
        }).join('') + '</div>';
    }

    function localEditorialDay() {
        var now = new Date();
        var start = Date.UTC(now.getFullYear(), 0, 0);
        var today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
        return now.getFullYear() * 400 + Math.floor((today - start) / 86400000);
    }

    function homeEditorialRank(item) {
        return parseInt(BuroDomain.stableHash((item && item.id || '') + ':' + localEditorialDay()), 36) || 0;
    }

    function homeTitleKey(item) {
        return BuroDomain.foldAccents(item && item.name || '').replace(/\[[^\]]*\]|\([^)]*\)/g, ' ')
            .replace(/\b(4k|uhd|fhd|hd|sd|dub|dual|legendado|dublado)\b/g, ' ')
            .replace(/[^a-z0-9]+/g, ' ').replace(/^\s+|\s+$/g, '');
    }

    function seasonalHomeTerms(date) {
        var month = date.getMonth() + 1;
        var day = date.getDate();
        if (month === 12 && day <= 26) {
            return ['natal', 'natalino', 'christmas', 'xmas', 'papai noel', 'santa claus', 'weihnacht', 'noel', 'renas', 'reindeer', 'grinch', 'presepe'];
        }
        if ((month === 12 && day >= 27) || (month === 1 && day <= 6)) {
            return ['ano novo', 'new year', 'reveillon', 'silvester', 'capodanno', 'contagem regressiva', 'countdown', 'meia noite', 'midnight'];
        }
        if ((month === 10 && day >= 18) || (month === 11 && day <= 1)) {
            return ['halloween', 'terror', 'horror', 'assombrada', 'assombrado', 'haunted', 'zumbi', 'zombie', 'vampiro', 'vampire', 'bruxa', 'witch', 'poltergeist', 'exorcis'];
        }
        if ((month === 2 && day >= 7 && day <= 15) || (month === 6 && day >= 5 && day <= 13)) {
            return ['romance', 'romantic', 'romantico', 'amor', 'love', 'paixao', 'namorad', 'valentine', 'casament', 'wedding', 'coracao'];
        }
        if (month === 7) {
            return ['familia', 'family', 'animacao', 'animation', 'infantil', 'kids', 'aventura', 'adventure', 'ferias', 'desenho', 'cartoon'];
        }
        return [];
    }

    function catalogueVisibilitySnapshot() {
        var sourceId = state.activeSource && state.activeSource.id;
        var visibility = {};
        /* Qual serviço cada categoria nomeia, resolvido uma vez por varredura em
           vez de uma vez por item: a Home percorre dezenas de milhares de linhas
           e o casamento de provedor roda uma expressão regular por chamada. */
        var categoryService = {};
        var kids = Boolean(state.activeProfile && state.activeProfile.isKids);
        state.categories.forEach(function (category) {
            var identity;
            if (!sourceId || category.sourceId === sourceId) {
                visibility[category.id] = BuroGuard.categoryVisible(category, state.preferences, kids) &&
                    (!BuroGuard.requiresPin(category, state.preferences) || Boolean(state.unlockedCategoryIds[category.id]));
                identity = BuroProviders.identityFor(category.name);
                if (identity) { categoryService[category.id] = identity.label; }
            }
        });
        return { sourceId: sourceId, categoryVisibility: visibility, categoryService: categoryService };
    }

    function snapshotItemVisible(snapshot, item) {
        if (snapshot.sourceId && item.sourceId !== snapshot.sourceId) { return false; }
        if (item.categoryId && Object.prototype.hasOwnProperty.call(snapshot.categoryVisibility, item.categoryId)) {
            return Boolean(snapshot.categoryVisibility[item.categoryId]);
        }
        return true;
    }

    function homeAccumulator() {
        var snapshot = catalogueVisibilitySnapshot();
        return {
            count: 0,
            sourceId: snapshot.sourceId,
            categoryVisibility: snapshot.categoryVisibility,
            currentYear: new Date().getFullYear(),
            currentReleases: [], previousReleases: [], recent: [], topRated: [], movies: [], series: [],
            categoryService: snapshot.categoryService,
            /* Uma prateleira por serviço que a lista nomeia — o que o Windows
               mostra como "tudo na Netflix". Montado durante a mesma varredura
               porque uma segunda passada pelo catálogo custaria o dobro. */
            byService: {}
        };
    }

    function collectHome(result, item) {
        var type = item && item.contentType;
        var service;
        var orderCompare = function (left, right) {
            return Number(left.sortOrder) - Number(right.sortOrder) || String(left.id).localeCompare(String(right.id));
        };
        if (!item || ['MOVIE', 'SERIES'].indexOf(type) < 0 || !snapshotItemVisible(result, item)) { return result; }
        result.count += 1;
        if (Number(item.year) === result.currentYear) {
            rankedInsert(result.currentReleases, item, function (left, right) {
                return Number(right.rating) - Number(left.rating) || Number(right.addedAt) - Number(left.addedAt) || orderCompare(left, right);
            }, 18);
        } else if (Number(item.year) === result.currentYear - 1) {
            rankedInsert(result.previousReleases, item, function (left, right) {
                return Number(right.rating) - Number(left.rating) || Number(right.addedAt) - Number(left.addedAt) || orderCompare(left, right);
            }, 18);
        }
        rankedInsert(result.recent, item, function (left, right) {
            return Number(right.addedAt) - Number(left.addedAt) || orderCompare(left, right);
        }, 36);
        if (Number(item.rating) > 0) {
            rankedInsert(result.topRated, item, function (left, right) {
                return Number(right.rating) - Number(left.rating) || Number(right.year) - Number(left.year) || orderCompare(left, right);
            }, 36);
        }
        rankedInsert(type === 'MOVIE' ? result.movies : result.series, item, orderCompare, 24);
        service = result.categoryService[item.categoryId];
        if (service) {
            if (!result.byService[service]) { result.byService[service] = []; }
            rankedInsert(result.byService[service], item, function (left, right) {
                return Number(right.rating) - Number(left.rating) ||
                    Number(right.addedAt) - Number(left.addedAt) || orderCompare(left, right);
            }, 24);
        }
        return result;
    }

    function homeResultItems(result) {
        var rows = [];
        var known = {};
        ['currentReleases', 'previousReleases', 'recent', 'topRated', 'movies', 'series'].forEach(function (key) {
            (result[key] || []).forEach(function (item) {
                if (!known[item.id]) { known[item.id] = true; rows.push(item); }
            });
        });
        /* As prateleiras de serviço entram aqui também: sem isto os títulos
           delas apareceriam sem capa, porque é esta lista que a hidratação de
           arte percorre. */
        Object.keys(result.byService || {}).forEach(function (service) {
            (result.byService[service] || []).forEach(function (item) {
                if (!known[item.id]) { known[item.id] = true; rows.push(item); }
            });
        });
        return rows;
    }

    function takeHomeItems(rows, consumedIds, consumedTitles, predicate) {
        var selected = [];
        (rows || []).some(function (item) {
            var titleKey = homeTitleKey(item);
            if (!consumedIds[item.id] && (!titleKey || !consumedTitles[titleKey]) && (!predicate || predicate(item))) {
                consumedIds[item.id] = true;
                if (titleKey) { consumedTitles[titleKey] = true; }
                selected.push(item);
            }
            return selected.length >= HOME_RAIL_LIMIT;
        });
        return selected;
    }

    function homeModel(data) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var sourceId = state.activeSource && state.activeSource.id;
        var result = data.result || homeAccumulator();
        var catalog = homeResultItems(result);
        var byId = {};
        var continued = [];
        var continuedIds = {};
        var consumedIds = {};
        var consumedTitles = {};
        var seasonalTerms = seasonalHomeTerms(new Date());
        var seasonal;
        var heroCandidates;
        var lead;
        var rotation;
        var rails = [];
        var currentYear = result.currentYear || new Date().getFullYear();
        var hero;
        state.items.concat(catalog).forEach(function (item) {
            if ((!sourceId || item.sourceId === sourceId) && itemVisible(item)) { byId[item.id] = item; }
        });
        state.progress.filter(function (entry) {
            return entry.profileId === profileId && !entry.completed;
        }).sort(function (a, b) {
            return Number(b.updatedAt) - Number(a.updatedAt);
        }).forEach(function (entry) {
            if (byId[entry.itemId] && !continuedIds[entry.itemId]) {
                continuedIds[entry.itemId] = true;
                continued.push(byId[entry.itemId]);
            }
        });
        seasonal = seasonalTerms.length ? catalog.filter(function (item) {
            var name = BuroDomain.foldAccents(item.name || '');
            return seasonalTerms.some(function (term) { return name.indexOf(BuroDomain.foldAccents(term)) >= 0; });
        }) : [];
        heroCandidates = seasonal.concat(result.currentReleases || []).concat(result.topRated || [], result.movies || [], result.series || [])
            .filter(function (item, index, rows) {
                return !continuedIds[item.id] && rows.map(function (row) { return row.id; }).indexOf(item.id) === index;
            });
        lead = seasonal.filter(function (item) { return !continuedIds[item.id]; })[0] ||
            (result.currentReleases || []).filter(function (item) { return !continuedIds[item.id]; })[0] ||
            (result.topRated || []).filter(function (item) { return !continuedIds[item.id] && Number(item.rating) >= 7; })[0] ||
            heroCandidates[0] || catalog[0] || continued[0];
        rotation = (lead ? [lead] : []).concat(heroCandidates.filter(function (item) {
            return !lead || item.id !== lead.id;
        }).sort(function (left, right) {
            return homeEditorialRank(left) - homeEditorialRank(right);
        })).slice(0, HOME_HERO_LIMIT);
        hero = rotation[0];
        continued.forEach(function (item) {
            consumedIds[item.id] = true;
            if (homeTitleKey(item)) { consumedTitles[homeTitleKey(item)] = true; }
        });
        if (hero) {
            delete consumedIds[hero.id];
            if (homeTitleKey(hero)) { delete consumedTitles[homeTitleKey(hero)]; }
            consumedIds[hero.id] = true;
            if (homeTitleKey(hero)) { consumedTitles[homeTitleKey(hero)] = true; }
        }
        rails.push({ key: 'continue', title: t('continueWatching'), items: continued.filter(function (item) { return !hero || item.id !== hero.id; }).slice(0, 12) });
        /*
          Logo depois de Continuar assistindo, e montado a partir dos próprios
          lembretes em vez das linhas do catálogo: o sentido de um título que
          ainda vai sair é justamente não estar no catálogo, então filtrá-lo
          contra os itens já consumidos descartaria exatamente os lembretes que
          valem a pena mostrar. Mesma decisão do Android.
        */
        rails.push({ key: 'reminders', title: t('homeReminders'), items: reminderCards().slice(0, 12) });
        rails.push({ key: 'releases-current', title: t('homeReleases').replace('{year}', currentYear),
            items: takeHomeItems(result.currentReleases, consumedIds, consumedTitles) });
        rails.push({ key: 'releases-previous', title: t('homeReleases').replace('{year}', currentYear - 1),
            items: takeHomeItems(result.previousReleases, consumedIds, consumedTitles) });
        rails.push({ key: 'new-classics', title: t('homeNewClassics'), items: takeHomeItems(result.recent, consumedIds, consumedTitles,
            function (item) { return !Number(item.year) || Number(item.year) <= currentYear - 15; }) });
        rails.push({ key: 'recent', title: t('homeRecentlyAdded'), items: takeHomeItems(result.recent, consumedIds, consumedTitles) });
        rails.push({ key: 'top-rated', title: t('homeTopRated'), items: takeHomeItems(result.topRated, consumedIds, consumedTitles) });
        rails.push({ key: 'movies', title: t('homeFeaturedMovies'), items: takeHomeItems(result.movies, consumedIds, consumedTitles) });
        rails.push({ key: 'series', title: t('homeFeaturedSeries'), items: takeHomeItems(result.series, consumedIds, consumedTitles) });
        /*
          Uma prateleira por serviço, no fim: "tudo na Netflix", como o Windows.

          Depois das editoriais de propósito. As de cima respondem "o que vejo
          agora"; estas respondem "o que tem naquele serviço", que é uma pergunta
          de quem já desceu a Home procurando algo específico.

          Ordenadas pelo tamanho do acervo, porque um serviço com trinta títulos
          é mais útil no topo do que um com dois.
        */
        Object.keys(result.byService || {}).sort(function (left, right) {
            return (result.byService[right] || []).length - (result.byService[left] || []).length ||
                left.localeCompare(right);
        }).forEach(function (service) {
            rails.push({
                key: 'service-' + service, title: service, service: service,
                items: takeHomeItems(result.byService[service], consumedIds, consumedTitles)
            });
        });
        return { hero: hero, rotation: rotation, rails: rails.filter(function (rail) { return rail.items.length; }) };
    }

    function renderRealHome(data) {
        var model = homeModel(data);
        var hero = model.hero;
        var heroIndex;
        var action;
        var metadata;
        var enrichment;
        var synopsis;
        var rating;
        var content;
        if (!hero) {
            return emptyState('B', t('noItems'), t('homeNoItems'), '', '');
        }
        heroIndex = BuroDomain.clamp(Number(data.heroIndex) || 0, 0, Math.max(0, model.rotation.length - 1));
        hero = model.rotation[heroIndex] || hero;
        data.heroIndex = heroIndex;
        data.heroRotation = model.rotation;
        enrichment = state.activeSource ? BuroHeroEnrichment.get(state.activeSource.id, hero.id) : null;
        if (enrichment) { rememberArtwork(hero.id, enrichment.artworkUrl); }
        action = hero.contentType === 'MOVIE' ? 'movie-details' :
            (hero.contentType === 'SERIES' ? 'series-details' :
                (hero.contentType === 'LIVE' ? 'live-details' : 'play'));
        metadata = [];
        if (enrichment && enrichment.genre) { metadata.push(enrichment.genre.split(/[,|/]/)[0]); }
        if (Number(hero.year) > 0) { metadata.push(String(Number(hero.year))); }
        if (enrichment && enrichment.duration) { metadata.push(enrichment.duration); }
        rating = enrichment && Number(enrichment.rating) > 0 ? Number(enrichment.rating) : Number(hero.rating);
        if (rating > 0) { metadata.push('★ ' + rating.toFixed(1)); }
        if (!metadata.length) { metadata.push(state.activeSource ? state.activeSource.name : 'IPTV BURO'); }
        synopsis = enrichment && enrichment.synopsis ? enrichment.synopsis : t('homeHeroSynopsis');
        content = '<div class="hero real-home-hero">' + heroArtworkHtml(hero, enrichment) +
            '<span class="hero-kicker">' + t('featured') + '</span><h2>' +
            escapeHtml(hero.name) + '</h2><p class="hero-metadata">' +
            escapeHtml(metadata.join(' · ')) + '</p><p class="hero-synopsis">' + escapeHtml(synopsis) +
            '</p><button class="button primary focusable" data-action="' + action +
            '" data-id="' + attr(hero.id) + '">' + (action === 'play' ? t('watch') : t('viewDetails')) + '</button></div>';
        model.rails.forEach(function (rail) { content += homeRail(rail.title, rail.items, rail.key, rail.service); });
        return content;
    }

    function loadHome(requestId) {
        BuroStorage.fold('items', collectHome, homeAccumulator(), function (result) {
            var all;
            if (requestId !== homeRequestId || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                    !state.screenData || state.screenData.requestId !== requestId) { return; }
            all = homeResultItems(result);
            mergeItems(all);
            state.screenData = { kind: 'home', loading: false, result: result, heroIndex: 0, requestId: requestId };
            focusIndex = 0;
            render();
        }, function (error) {
            var current;
            if (requestId !== homeRequestId || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                    !state.screenData || state.screenData.requestId !== requestId) { return; }
            current = state.screenData;
            current.loading = false;
            current.error = friendlyError(error);
            focusIndex = 0;
            render();
        });
    }

    function startHomeLoad() {
        var requestId = ++homeRequestId;
        var fallback = homeAccumulator();
        state.items.forEach(function (item) { collectHome(fallback, item); });
        state.screenData = { kind: 'home', loading: true, result: fallback, heroIndex: 0, requestId: requestId };
        window.setTimeout(function () { loadHome(requestId); }, 0);
    }

    function scheduleHomeHeroRotation(data) {
        if (homeHeroTimer) { window.clearTimeout(homeHeroTimer); homeHeroTimer = null; }
        if (state.preferences.reducedMotion || !data.heroRotation || data.heroRotation.length <= 1) { return; }
        homeHeroTimer = window.setTimeout(function () {
            var current = focusables[focusIndex];
            var action = current && current.getAttribute('data-action');
            var id = current && current.getAttribute('data-id');
            var section = current && current.getAttribute('data-section');
            var heroFocused = current && current.parentNode && current.parentNode.classList &&
                current.parentNode.classList.contains('real-home-hero');
            var preferred;
            if (state.screen !== 'SHELL' || state.section !== 'HOME' || state.screenData !== data) { return; }
            if (heroFocused) { scheduleHomeHeroRotation(data); return; }
            data.heroIndex = ((Number(data.heroIndex) || 0) + 1) % data.heroRotation.length;
            render();
            focusables.some(function (element, index) {
                if (element.getAttribute('data-action') === action && element.getAttribute('data-id') === id &&
                        element.getAttribute('data-section') === section) { preferred = index; return true; }
                return false;
            });
            if (preferred != null) { focusIndex = preferred; applyFocus(); }
        }, HOME_HERO_ROTATION_MILLIS);
    }

    function scheduleHomeHeroEnrichment(data) {
        var source = state.activeSource;
        var sync;
        var candidates = data && data.heroRotation || [];
        var requestId = data && data.requestId;
        if (homeEnrichmentTimer) { window.clearTimeout(homeEnrichmentTimer); homeEnrichmentTimer = null; }
        if (!source || source.type !== 'XTREAM' || !candidates.length) { return; }
        sync = catalogueSyncStatus(source);
        if (sync && sync.state === 'RUNNING') { return; }
        homeEnrichmentTimer = window.setTimeout(function () {
            homeEnrichmentTimer = null;
            if (state.screen !== 'SHELL' || state.section !== 'HOME' || state.screenData !== data ||
                    data.requestId !== requestId || !state.activeSource || state.activeSource.id !== source.id) { return; }
            try {
                BuroHeroEnrichment.start(source, candidates, {
                    getSecret: function (sourceId) { return BuroStorage.secureGet(sourceId); },
                    onItem: function (item, enrichment) {
                        var current;
                        if (!enrichment || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                                state.screenData !== data || !state.activeSource || state.activeSource.id !== source.id) { return; }
                        rememberArtwork(item.id, enrichment.artworkUrl);
                        current = data.heroRotation && data.heroRotation[Number(data.heroIndex) || 0];
                        if (current && current.id === item.id) { render(); }
                    }
                });
            } catch (ignoredHeroEnrichment) { /* O Hero conserva arte e texto de fallback. */ }
        }, 0);
    }

    function renderHome() {
        var data = state.screenData;
        var content;
        var topbarExtra = '';
        var year = new Date().getFullYear();
        if (state.screenData && state.screenData.kind === 'demo-story') {
            content = '<div class="demo-story"><span class="hero-kicker">' + t('demoBadge') + '</span>' +
                '<h2>' + t('demoHeroTitle') + '</h2><p class="demo-story-meta">' + year + ' · ' + t('demoHeroMetadata') + '</p>' +
                '<p>' + t('demoHeroSynopsis') + '</p><div class="action-row detail-actions">' +
                '<button class="button primary focusable" data-action="source-add">' + t('addSource') + '</button>' +
                '<button class="button ghost focusable" data-action="back">' + t('back') + '</button></div></div>';
            shell(content, t('home'), true);
            return;
        }
        if (!state.sources.length) {
            topbarExtra = '<div class="demo-notice">' + t('demoNotice') + '</div>';
            content = '<div class="hero living-hero"><span class="hero-kicker">' + t('demoBadge') + '</span><h2>' +
                t('demoHeroTitle') + '</h2><p class="hero-metadata">' + year + ' · ' + t('demoHeroMetadata') + '</p><p>' +
                t('demoHeroSynopsis') + '</p><button class="button primary focusable" data-action="demo-story">' +
                t('demoViewStory') + '</button></div>' +
                '<div class="section-heading demo-heading"><h2>' + t('demoContinueTitle') + '</h2><p>' + t('demoBadge') + '</p></div>' +
                '<div class="demo-card-row">' +
                demoHomeCard('paper', t('demoAmberTitle')) + demoHomeCard('forest', t('demoSignalTitle')) +
                demoHomeCard('atlas', t('demoAtlasTitle')) + '</div>';
        } else {
            if (!data || data.kind !== 'home') { startHomeLoad(); data = state.screenData; }
            if (data.loading && !(data.result && data.result.count)) {
                content = '<div class="home-loading"><span class="boot-indicator"></span><h2>' + t('homeLoading') +
                    '</h2><p>' + t('homeLoadingBody') + '</p></div>';
            } else if (data.error && !(data.result && data.result.count)) {
                content = emptyState('!', t('couldNotLoad'), t('homeLoadError'), 'home-retry', t('retry'));
            } else {
                content = (data.loading ? '<div class="home-status loading"><span class="boot-indicator"></span>' +
                    t('homeLoading') + '</div>' : '') +
                    (data.error ? '<div class="details-inline-warning home-cache-warning"><span>!</span><p>' +
                        t('homeCachedWarning') + '</p><button class="button ghost focusable" data-action="home-retry">' +
                        t('retry') + '</button></div>' : '') + renderRealHome(data);
            }
            content = catalogueSyncBanner(state.activeSource) + content;
        }
        shell(content, t('home'), Boolean(state.sources.length), topbarExtra);
        if (state.sources.length && data && data.kind === 'home' && !data.loading && !data.error) {
            scheduleHomeHeroRotation(data);
            scheduleHomeHeroEnrichment(data);
        }
    }

    function rankedInsert(rows, item, compare, limit) {
        var index = 0;
        while (index < rows.length && compare(rows[index], item) <= 0) { index += 1; }
        rows.splice(index, 0, item);
        if (rows.length > (limit || 12)) { rows.pop(); }
    }

    function discoveryGenres(item) {
        var label = item && item.genre;
        if (!label && item && item.categoryId) {
            state.categories.some(function (category) {
                if (category.id === item.categoryId) { label = category.name; return true; }
                return false;
            });
        }
        return String(label || '').split(/[,\/|;]/).map(function (part) {
            return BuroDomain.trim(part);
        }).filter(function (part) { return Boolean(part); });
    }

    function ensureDiscoverSession() {
        var key = (state.activeProfile && state.activeProfile.id || '') + ':' +
            (state.activeSource && state.activeSource.id || '');
        if (key !== discoverSessionKey) {
            discoverSessionKey = key;
            discoverSessionTaste = { leaningByGenre: {} };
            discoverJudgedIds = {};
            discoverReturnData = null;
        }
    }

    function discoverAccumulator() {
        var snapshot = catalogueVisibilitySnapshot();
        return {
            count: 0, sourceId: snapshot.sourceId, categoryVisibility: snapshot.categoryVisibility,
            movieCount: 0, seriesCount: 0, items: []
        };
    }

    function collectDiscover(result, item) {
        var type = item && item.contentType;
        if (!item || ['MOVIE', 'SERIES'].indexOf(type) < 0 || !snapshotItemVisible(result, item)) { return result; }
        result.count += 1;
        if (type === 'MOVIE' && result.movieCount < 400) {
            result.movieCount += 1;
            result.items.push(item);
        } else if (type === 'SERIES' && result.seriesCount < 400) {
            result.seriesCount += 1;
            result.items.push(item);
        }
        return result;
    }

    function buildDiscoverDeck(result) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var byId = {};
        var favouriteGenres = [];
        var watchedGenres = [];
        var seenIds = [];
        var candidates;
        (result.items || []).forEach(function (item) { byId[item.id] = item; });
        state.favorites.forEach(function (row) {
            if (row.profileId !== profileId) { return; }
            seenIds.push(row.itemId);
            if (byId[row.itemId]) { favouriteGenres = favouriteGenres.concat(discoveryGenres(byId[row.itemId])); }
        });
        state.progress.forEach(function (row) {
            if (row.profileId !== profileId) { return; }
            seenIds.push(row.itemId);
            if (byId[row.itemId]) { watchedGenres = watchedGenres.concat(discoveryGenres(byId[row.itemId])); }
        });
        Object.keys(discoverJudgedIds).forEach(function (itemId) {
            if (discoverJudgedIds[itemId]) { seenIds.push(itemId); }
        });
        candidates = (result.items || []).map(function (item) {
            return {
                id: item.id, title: item.name, genres: discoveryGenres(item), year: item.year,
                rating: item.rating, isSeries: item.contentType === 'SERIES'
            };
        });
        return BuroDomain.discoveryDeck(candidates, {
            favouriteGenres: favouriteGenres, watchedGenres: watchedGenres, seenIds: seenIds
        }, discoverSessionTaste, 0).map(function (candidate) { return byId[candidate.id]; }).filter(Boolean);
    }

    function loadDiscover() {
        var requestId = ++discoverRequestId;
        ensureDiscoverSession();
        BuroStorage.fold('items', collectDiscover, discoverAccumulator(), function (result) {
            var deck;
            if (requestId !== discoverRequestId || state.screen !== 'SHELL' || state.section !== 'DISCOVER') { return; }
            mergeItems(result.items);
            deck = buildDiscoverDeck(result);
            state.screenData = {
                kind: 'discover', loading: false, deck: deck, dealtCount: deck.length,
                catalogueCount: (result.items || []).length
            };
            focusIndex = 0;
            render();
        }, function (error) {
            if (requestId !== discoverRequestId || state.screen !== 'SHELL' || state.section !== 'DISCOVER') { return; }
            state.screenData = { kind: 'discover', loading: false, error: friendlyError(error) };
            focusIndex = 0;
            render();
        });
    }

    function renderDiscoverCard(item, layer) {
        var genres = discoveryGenres(item);
        var facts = [];
        var rating = Number(item && item.rating);
        if (item && item.year) { facts.push(String(item.year)); }
        if (genres.length) { facts.push(genres.slice(0, 2).join(' · ')); }
        if (rating > 0) { facts.push('★ ' + rating.toFixed(1)); }
        return '<article class="discover-card ' + layer + (artworkMemory[item.id] ? ' has-art' : '') +
            '" data-id="' + attr(item.id) + '" aria-label="' + attr(item.name) + '">' +
            artworkHtml(item, 'discover-art') + '<span class="badge">' + escapeHtml(item.contentType) +
            '</span><div class="discover-card-copy"><h3>' + escapeHtml(item.name) + '</h3><p>' +
            escapeHtml(facts.join('  ·  ')) + '</p></div></article>';
    }

    function renderDiscover() {
        var data = state.screenData;
        var deck;
        var current;
        var position;
        var content;
        if (!state.sources.length) {
            shell(emptyState('+', t('addSource'), t('emptyHome'), 'source-add', t('addSource')), t('discover'), false);
            return;
        }
        if (!data || data.kind !== 'discover') {
            state.screenData = { kind: 'discover', loading: true };
            window.setTimeout(loadDiscover, 0);
            data = state.screenData;
        }
        if (data.loading) {
            shell('<div class="search-loading"><span class="boot-indicator"></span><p>' + t('discoverLoading') + '</p></div>',
                t('discover'), false);
            return;
        }
        if (data.error) {
            shell(emptyState('!', t('error'), data.error, 'discover-retry', t('retry')), t('discover'), false);
            return;
        }
        deck = data.deck || [];
        content = '<div class="discover-intro"><span class="hero-kicker">IPTV BURO</span><h2>' + t('discover') +
            '</h2><p>' + t('discoverIntro') + '</p></div>';
        if (!deck.length) {
            content += Number(data.catalogueCount) > 0 ?
                emptyState('B', t('discoverExhausted'), t('discoverEmpty'), 'discover-again', t('discoverAgain')) :
                emptyState('B', t('discover'), t('discoverNeedsCatalogue'), 'discover-again', t('discoverAgain'));
            shell(content, t('discover'), true);
            return;
        }
        current = deck[0];
        position = Math.max(1, Number(data.dealtCount) - deck.length + 1);
        content += '<section class="discover-workspace" aria-label="' + attr(t('discover')) + '">' +
            '<p class="discover-counter" aria-live="polite">' +
            escapeHtml(t('discoverCounter').replace('{current}', position).replace('{total}', Math.max(position, Number(data.dealtCount) || 0))) +
            '</p><div class="discover-stage">' + (deck[1] ? renderDiscoverCard(deck[1], 'next') : '') +
            renderDiscoverCard(current, 'current') + '</div><div class="discover-actions">' +
            '<button class="discover-action skip focusable" data-action="discover-skip" data-id="' + attr(current.id) +
            '" aria-label="' + attr(t('discoverSkip')) + '"><span>×</span><strong>' + t('discoverSkip') + '</strong></button>' +
            '<button class="discover-action keep focusable" data-action="discover-keep" data-id="' + attr(current.id) +
            '" aria-label="' + attr(t('discoverKeep')) + '"><span>✓</span><strong>' + t('discoverKeep') + '</strong></button>' +
            '<button class="discover-action details focusable" data-action="discover-details" data-id="' + attr(current.id) +
            '" aria-label="' + attr(t('discoverDetails')) + '"><span>i</span><strong>' + t('discoverDetails') + '</strong></button>' +
            '</div></section>';
        shell(content, t('discover'), true);
    }

    function advanceDiscover(item, verdict) {
        var data = state.screenData;
        if (!data || data.kind !== 'discover' || !item) { return; }
        discoverJudgedIds[item.id] = true;
        discoverSessionTaste = BuroDomain.discoverySessionAfter(
            discoverSessionTaste, discoveryGenres(item), verdict
        );
        data.deck = (data.deck || []).filter(function (candidate) { return candidate.id !== item.id; });
        render();
    }

    function decideDiscover(verdict) {
        var data = state.screenData;
        var item = data && data.kind === 'discover' && data.deck && data.deck[0];
        if (!item) { return; }
        if (verdict !== 'KEPT') { advanceDiscover(item, 'SKIPPED'); return; }
        if (isFavorite(item.id)) { advanceDiscover(item, 'KEPT'); return; }
        toggleFavorite(item.id, function (added) {
            if (added && state.section === 'DISCOVER' && state.screenData === data) {
                advanceDiscover(item, 'KEPT');
            }
        });
    }

    function openDiscoverDetails(itemId) {
        var data = state.screenData;
        var found = findItemAndSource(itemId);
        if (!data || data.kind !== 'discover' || !found.item) { return; }
        discoverReturnData = data;
        if (found.item.contentType === 'MOVIE') { openMovieDetails(itemId, 'DISCOVER'); }
        else if (found.item.contentType === 'SERIES') { openSeriesById(itemId, 'DISCOVER'); }
        if (state.screenData === data) { discoverReturnData = null; }
    }

    function dealDiscoverAgain() {
        state.screenData = { kind: 'discover', loading: true };
        focusIndex = 0;
        render();
        window.setTimeout(loadDiscover, 0);
    }

    function demoHomeCard(artwork, title) {
        return '<button class="demo-media-card ' + artwork + ' focusable" data-action="demo-story">' +
            '<span class="badge">' + t('demoBadge') + '</span><strong>' + escapeHtml(title) + '</strong></button>';
    }

    function sourceCategories(contentType) {
        if (!state.activeSource) { return []; }
        return state.categories.filter(function (category) {
            return category.sourceId === state.activeSource.id && category.contentType === contentType &&
                BuroGuard.categoryVisible(category, state.preferences, Boolean(state.activeProfile && state.activeProfile.isKids));
        }).sort(function (a, b) { return (a.sortOrder || 0) - (b.sortOrder || 0); });
    }

    function categoryForItem(item) {
        var category = null;
        var parent = null;
        state.categories.forEach(function (candidate) {
            if (candidate.id === item.categoryId) { category = candidate; }
        });
        if (!category && item.contentType === 'EPISODE') {
            state.items.forEach(function (candidate) { if (candidate.id === item.categoryId) { parent = candidate; } });
            if (parent) {
                state.categories.forEach(function (candidate) {
                    if (candidate.id === parent.categoryId) { category = candidate; }
                });
            }
        }
        return category;
    }

    function itemVisible(item) {
        var category = categoryForItem(item);
        if (!BuroGuard.categoryVisible(
                category, state.preferences, Boolean(state.activeProfile && state.activeProfile.isKids))) { return false; }
        return !BuroGuard.requiresPin(category, state.preferences) || Boolean(state.unlockedCategoryIds[category.id]);
    }

    /*
      A marca de um serviço, do jeito que o Windows desenha: monograma sobre a
      cor oficial da marca. Sem logotipo baixado — a TV não guarda imagem em
      disco e um pedido de rede por chip atrasaria a fileira inteira.
    */
    function providerBadge(identity) {
        if (!identity) { return ''; }
        return '<span class="provider-badge" style="background:' + attr(identity.colour) + '" aria-hidden="true">' +
            escapeHtml(identity.mark || identity.label.charAt(0)) + '</span>';
    }

    /* O filtro de serviço/gênero vigente para esta aba, criado sob demanda. */
    function catalogueScope(contentType) {
        if (!catalogueScopes[contentType]) {
            catalogueScopes[contentType] = { genre: null, service: null };
        }
        return catalogueScopes[contentType];
    }

    /*
      Os dois seletores no topo de Filmes, Séries e Ao Vivo.

      Portado do XtreamWorkspace do Windows, inclusive a decisão de mostrar o
      seletor de Serviço desativado quando a lista não arquiva por serviço: um
      controle que aparece numa aba e some na outra é lido como defeito, e
      esconder deixava quem procura "só Netflix" sem saber se a função não
      existe, está quebrada ou não se aplica à lista dele. Dizer o motivo serve
      mais do que o silêncio.
    */
    function catalogueScopeBar(contentType, categories) {
        var scope = catalogueScope(contentType);
        var parts = BuroProviders.split(categories);
        /* `scope.genre` guarda o id da categoria, não o nome dela: o rótulo sai do
           split, que já calculou o nome sem o prefixo de seção. Passar o id
           direto para `categoryLabel` punha "category-79iyjj" na tela. */
        var genreLabel = t('filterAll');
        if (scope.genre) {
            parts.genres.some(function (row) {
                if (row.id === scope.genre) { genreLabel = row.label; return true; }
                return false;
            });
        }
        var serviceLabel = scope.service || t('filterAll');
        var serviceIdentity = scope.service ? BuroProviders.identityForLabel(scope.service) : null;
        var chips = '<button class="scope-chip focusable ' + (scope.genre ? 'selected' : '') +
            '" data-action="catalogue-scope-genre"><small>' + t('genreSelector') + '</small><strong>' +
            escapeHtml(genreLabel) + '</strong></button>';
        if (parts.hasProviders) {
            chips += '<button class="scope-chip focusable ' + (scope.service ? 'selected' : '') +
                '" data-action="catalogue-scope-service">' + providerBadge(serviceIdentity) +
                '<small>' + t('serviceSelector') + '</small><strong>' + escapeHtml(serviceLabel) + '</strong></button>';
        } else {
            chips += '<span class="scope-chip disabled"><small>' + t('serviceSelector') +
                '</small><strong>' + escapeHtml(t('servicesUnavailable')) + '</strong></span>';
        }
        if (scope.genre || scope.service) {
            chips += '<button class="scope-chip clear focusable" data-action="catalogue-scope-reset">' +
                t('clearFilters') + '</button>';
        }
        return '<div class="catalogue-scope-bar">' + chips + '</div>';
    }

    /* As categorias que sobrevivem ao filtro de serviço e de gênero. */
    function scopedCategories(contentType, categories) {
        var scope = catalogueScope(contentType);
        var allowed = null;
        if (scope.service) {
            allowed = {};
            BuroProviders.categoryIdsForLabel(categories, scope.service).forEach(function (id) {
                allowed[id] = true;
            });
        }
        return categories.filter(function (category) {
            if (allowed && !allowed[category.id]) { return false; }
            if (scope.genre && category.id !== scope.genre) { return false; }
            return true;
        });
    }

    function categoryCards(categories) {
        if (!categories.length) { return emptyState('B', t('noCategories'), t('noCategoriesBody'), '', ''); }
        return '<div class="card-row">' + categories.map(function (category) {
            var identity = BuroProviders.identityFor(category.name);
            /* O contentType repetido em toda linha não dizia nada — numa aba
               chamada Filmes toda categoria é MOVIE. O serviço, quando a lista
               nomeia um, é a informação que distingue uma categoria da outra. */
            return '<button class="category-card focusable ' + (identity ? 'has-provider' : '') +
                '" data-action="category" data-id="' + attr(category.id) + '">' +
                providerBadge(identity) + '<h3>' + escapeHtml(BuroProviders.categoryLabel(category.name)) +
                '</h3><p>' + escapeHtml(identity ? identity.label : category.name) + '</p></button>';
        }).join('') + '</div>';
    }

    function mediaCard(item, layout) {
        var poster = layout === 'poster' && (item.contentType === 'MOVIE' || item.contentType === 'SERIES');
        var favorite = isFavorite(item.id);
        var playback = playbackProgress(item._libraryProgressItemId || item.id);
        var metadata = mediaMetadata(item);
        var action = item.contentType === 'MOVIE' ? 'movie-details' :
            (item.contentType === 'SERIES' ? 'series-details' :
                (item.contentType === 'LIVE' ? 'live-details' : 'play'));
        return '<button class="media-card focusable ' + (poster ? 'poster' : '') + ' ' + layout +
            (artworkMemory[item.id] ? ' has-art' : '') + '" data-action="' + action + '" data-id="' + attr(item.id) + '">' +
            artworkHtml(item, 'media-art') + '<span class="badge">' + (favorite ? '★ · ' : '') +
            (playback && playback.completed ? '✓ · ' : '') + escapeHtml(item.contentType) + '</span><h3>' +
            escapeHtml(item.name) + '</h3><p>' + escapeHtml(metadata) + '</p>' +
            (playback ? '<span class="media-progress"><i style="width:' + playback.percent.toFixed(2) + '%"></i></span>' : '') + '</button>';
    }

    function mediaCards(items, layout) {
        layout = CATALOGUE_LAYOUTS.indexOf(layout) >= 0 ? layout : 'poster';
        items = items.filter(itemVisible);
        if (!items.length) { return emptyState('B', t('error'), t('unavailable'), '', ''); }
        return '<div class="card-row catalogue-layout-' + layout + '">' + items.map(function (item) {
            return mediaCard(item, layout);
        }).join('') + '</div>';
    }

    function mediaMetadata(item) {
        var parts = [];
        if (item.contentType === 'EPISODE' && item.locator) {
            if (Number(item.locator.season) > 0) { parts.push('T' + Number(item.locator.season)); }
            if (Number(item.locator.episode) > 0) { parts.push('E' + Number(item.locator.episode)); }
        }
        if (Number(item.year) > 0) { parts.push(String(Number(item.year))); }
        if (Number(item.rating) > 0) { parts.push('★ ' + Number(item.rating).toFixed(1)); }
        return parts.length ? parts.join(' · ') : 'IPTV BURO';
    }

    function playbackProgress(itemId) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var entry = null;
        state.progress.forEach(function (row) {
            if (row.profileId === profileId && row.itemId === itemId &&
                    (!entry || Number(row.updatedAt) > Number(entry.updatedAt))) { entry = row; }
        });
        if (!entry) { return null; }
        if (!entry.completed && Number(entry.durationMs) <= 0) { return null; }
        return {
            completed: Boolean(entry.completed),
            entry: entry,
            percent: entry.completed ? 100 : BuroDomain.clamp(
                Number(entry.durationMs) > 0 ? Number(entry.positionMs) / Number(entry.durationMs) * 100 : 0, 0, 100
            )
        };
    }

    function cycleValue(values, current) {
        var index = values.indexOf(current);
        return values[(index + 1) % values.length];
    }

    function layoutLabel(layout) {
        return t({ poster: 'layoutPoster', compact: 'layoutCompact', list: 'layoutList' }[layout] || 'layoutPoster');
    }

    function sortLabel(sort) {
        return t({
            provider: 'sortProvider', 'title-asc': 'sortTitleAsc', 'title-desc': 'sortTitleDesc',
            'year-desc': 'sortYearDesc', 'year-asc': 'sortYearAsc', 'rating-desc': 'sortRatingDesc'
        }[sort] || 'sortProvider');
    }

    function catalogueFilterBar(items, data) {
        var filter = data.catalogueFilter || { genre: null, year: null, sort: 'provider' };
        var layout = data.catalogueLayout || 'poster';
        var genres = BuroDomain.availableGenres(items);
        var years = BuroDomain.availableYears(items);
        var active = Boolean(filter.genre || filter.year != null || filter.sort !== 'provider');
        var canRefresh = data.category && state.sources.some(function (source) {
            return source.id === data.category.sourceId && source.type === 'XTREAM';
        });
        data.catalogueFilter = filter;
        data.catalogueLayout = layout;
        data.availableGenres = genres;
        data.availableYears = years;
        return '<div class="catalogue-filter-bar">' +
            (canRefresh ? '<button class="filter-chip refresh focusable" data-action="category-refresh">↻ ' +
                t('refreshCategory') + '</button>' : '') +
            '<button class="filter-chip focusable" data-action="catalogue-layout">' + t('catalogueLayout') + ': ' + layoutLabel(layout) + '</button>' +
            '<button class="filter-chip focusable ' + (filter.sort !== 'provider' ? 'selected' : '') + '" data-action="catalogue-sort">' +
                t('catalogueSort') + ': ' + sortLabel(filter.sort) + '</button>' +
            (genres.length ? '<button class="filter-chip focusable ' + (filter.genre ? 'selected' : '') + '" data-action="catalogue-genre">' +
                t('filterGenre') + ': ' + escapeHtml(filter.genre || t('filterAll')) + '</button>' : '') +
            (years.length ? '<button class="filter-chip focusable ' + (filter.year != null ? 'selected' : '') + '" data-action="catalogue-year">' +
                t('filterYear') + ': ' + escapeHtml(filter.year == null ? t('filterAll') : filter.year) + '</button>' : '') +
            (active ? '<button class="filter-chip clear focusable" data-action="catalogue-reset">' + t('clearFilters') + '</button>' : '') +
            '</div>';
    }

    function pageText(page, pageCount) {
        return t('pageOf').replace('{page}', page + 1).replace('{pages}', pageCount);
    }

    function paginationControls(actionPrefix, page, pageCount, start, end, total, extraAttributes, extraClass) {
        if (pageCount <= 1) { return ''; }
        extraAttributes = extraAttributes || '';
        return '<div class="catalogue-pagination ' + (extraClass || '') + '" aria-label="' + attr(pageText(page, pageCount)) + '">' +
            (page > 0 ? '<button class="button ghost focusable" data-action="' + actionPrefix + '-previous"' +
                extraAttributes + '>' + t('previousPage') + '</button>' : '') +
            '<p><strong>' + escapeHtml(pageText(page, pageCount)) + '</strong><span>' + (start + 1) + '–' + end +
                ' / ' + total + '</span></p>' +
            (page + 1 < pageCount ? '<button class="button primary focusable" data-action="' + actionPrefix + '-next"' +
                extraAttributes + '>' + t('nextPage') + '</button>' : '') + '</div>';
    }

    function renderFilteredCategory(data) {
        var filterBar = catalogueFilterBar(data.items || [], data);
        var filtered = BuroDomain.applyCatalogueFilter(data.items || [], data.catalogueFilter);
        var pageCount = Math.max(1, Math.ceil(filtered.length / CATALOGUE_PAGE_SIZE));
        var page = BuroDomain.clamp(Number(data.cataloguePage) || 0, 0, pageCount - 1);
        var start = page * CATALOGUE_PAGE_SIZE;
        var visible = filtered.slice(start, start + CATALOGUE_PAGE_SIZE);
        var body;
        data.cataloguePage = page;
        if (!(data.items || []).length) { body = emptyState('B', t('noItems'), t('noItemsBody'), '', ''); }
        else if (!filtered.length) { body = emptyState('B', t('noFilterResults'), t('clearFilters'), 'catalogue-reset', t('clearFilters')); }
        else {
            body = mediaCards(visible, data.catalogueLayout) + paginationControls(
                'category-page', page, pageCount, start, start + visible.length, filtered.length
            );
        }
        return (data.refreshError ? '<div class="details-inline-warning category-refresh-warning"><span>!</span><p>' +
            t('categoryCachedWarning') + '</p><button class="button ghost focusable" data-action="category-refresh">' +
            t('retry') + '</button></div>' : '') + filterBar + '<div class="catalogue-result-count">' +
            filtered.length + ' / ' + (data.items || []).length + '</div>' + body;
    }

    function catalogueAsyncTitle(data) {
        return data.category ? data.category.name : (data.parent ? data.parent.name : sectionTitle());
    }

    function renderCatalogueLoading(data) {
        var cards = '';
        var index;
        for (index = 0; index < 8; index += 1) {
            cards += '<span class="catalogue-skeleton-card"><i></i><b></b><em></em></span>';
        }
        shell('<div class="catalogue-loading" aria-live="polite"><div class="catalogue-loading-hero"><span class="boot-indicator"></span>' +
            '<div><span class="hero-kicker">IPTV BURO</span><h2>' + t('loadingCatalogue') + '</h2><p>' +
            escapeHtml(catalogueAsyncTitle(data)) + '</p></div></div><div class="catalogue-skeleton-row">' + cards + '</div></div>',
            catalogueAsyncTitle(data), false);
    }

    function renderCatalogueError(data) {
        var body = data.target === 'category' ? t('catalogueLoadError') : t('detailsLoadError');
        shell('<div class="catalogue-error">' + emptyState('!', t('couldNotLoad'), body, 'catalogue-retry', t('retry')) + '</div>',
            catalogueAsyncTitle(data), false);
    }

    function renderSeriesEpisodes(data) {
        var groups = {};
        var seasons;
        data.seasonPages = data.seasonPages || {};
        (data.items || []).forEach(function (episode) {
            var season = Number(episode.locator && episode.locator.season) || 0;
            if (!groups[season]) { groups[season] = []; }
            groups[season].push(episode);
        });
        seasons = Object.keys(groups).map(Number).sort(function (a, b) { return a - b; });
        return seasons.map(function (season) {
            var expanded = Number(data.expandedSeason) === season;
            var downloadCandidates = seriesBulkDownloadAvailable(data.parent) ?
                BuroDownloads.bulkCandidates(groups[season], season) : [];
            var pageCount = Math.max(1, Math.ceil(groups[season].length / EPISODE_PAGE_SIZE));
            var page = BuroDomain.clamp(Number(data.seasonPages[season]) || 0, 0, pageCount - 1);
            var start = page * EPISODE_PAGE_SIZE;
            var visible = groups[season].slice(start, start + EPISODE_PAGE_SIZE);
            var seasonAttribute = ' data-season="' + season + '"';
            data.seasonPages[season] = page;
            return '<button class="season-header focusable ' + (expanded ? 'expanded' : '') + '" data-action="series-season" data-season="' + season +
                '" aria-expanded="' + (expanded ? 'true' : 'false') + '">' +
                '<strong>' + t('season') + ' ' + season + '</strong><span>' + groups[season].length + ' ' + t('episodes') + ' ' + (expanded ? '▲' : '▼') +
                '</span></button>' + (downloadCandidates.length ? '<button class="button ghost focusable season-download" data-action="series-download-season" data-season="' +
                    season + '">↓ ' + t('downloadSeason').replace('{season}', season) + '</button>' : '') +
                (expanded ? episodeCards(visible, seriesBulkDownloadAvailable(data.parent)) + paginationControls(
                    'series-page', page, pageCount, start, start + visible.length, groups[season].length,
                    seasonAttribute, 'season-pagination'
                ) : '');
        }).join('');
    }

    function isFavorite(itemId) {
        var profileId = state.activeProfile && state.activeProfile.id;
        return state.favorites.some(function (favorite) {
            return favorite.profileId === profileId && favorite.itemId === itemId;
        });
    }

    function renderCatalog(contentType) {
        var titleKey = contentType === 'LIVE' ? 'live' : (contentType === 'MOVIE' ? 'movies' : 'series');
        if (state.screenData && state.screenData.kind === 'catalogue-loading' && state.screenData.contentType === contentType) {
            renderCatalogueLoading(state.screenData);
            return;
        }
        if (state.screenData && state.screenData.kind === 'catalogue-error' && state.screenData.contentType === contentType) {
            renderCatalogueError(state.screenData);
            return;
        }
        if (state.screenData && state.screenData.kind === 'live') {
            shell(renderLiveDetails(state.screenData.parent, state.screenData.schedule || []), state.screenData.parent.name, true);
            return;
        }
        if (state.screenData && state.screenData.kind === 'movie') {
            shell(renderTitleDetails(state.screenData.parent, state.screenData.details, false), state.screenData.parent.name, true);
            return;
        }
        if (state.screenData && state.screenData.kind === 'series') {
            shell(renderTitleDetails(state.screenData.parent, state.screenData.details, true, state.screenData.items || []) +
                (state.screenData.detailsError ? '<div class="details-inline-warning"><span>!</span><p>' +
                    t('seriesCachedWarning') + '</p><button class="button ghost focusable" data-action="series-details-retry" data-id="' +
                    attr(state.screenData.parent.id) + '">' + t('retry') + '</button></div>' : '') +
                '<div class="section-heading"><h2>' + t('episodes') + '</h2><p>' + (state.screenData.items || []).length +
                '</p></div><div class="season-list">' + ((state.screenData.items || []).length ? renderSeriesEpisodes(state.screenData) :
                    emptyState('B', t('noEpisodes'), t('noEpisodesBody'), '', '')) + '</div>', state.screenData.parent.name, true);
            return;
        }
        if (state.screenData && state.screenData.kind === 'category' && state.screenData.contentType === contentType) {
            shell(renderFilteredCategory(state.screenData), state.screenData.category.name, true);
            return;
        }
        (function () {
            var categories = sourceCategories(contentType);
            shell(catalogueScopeBar(contentType, categories) +
                categoryCards(scopedCategories(contentType, categories)), t(titleKey), true);
        }());
    }

    /*
      Escolher gênero ou serviço, um passo por ENTER.

      Ciclar em vez de abrir uma lista: com D-pad, um menu suspenso custa abrir,
      descer, confirmar e fechar, e a lista de serviços de uma playlist cabe em
      poucos passos. A mesma escolha que `cyclePreference` já faz no resto do app.
    */
    function cycleCatalogueScope(property) {
        var contentType = state.section === 'LIVE' ? 'LIVE' :
            (state.section === 'MOVIES' ? 'MOVIE' : 'SERIES');
        var categories = sourceCategories(contentType);
        var scope = catalogueScope(contentType);
        var parts = BuroProviders.split(categories);
        var values;
        var index;
        if (property === 'service') {
            values = [null].concat(parts.providers.map(function (row) { return row.label; }));
        } else {
            values = [null].concat(parts.genres.map(function (row) { return row.id; }));
        }
        index = values.indexOf(scope[property]);
        scope[property] = values[(index + 1) % values.length];
        /* Gênero e serviço se excluem: um título pertence a exatamente uma
           categoria, então filtrar pelos dois só poderia esvaziar a tela. */
        if (property === 'service' && scope.service) { scope.genre = null; }
        if (property === 'genre' && scope.genre) { scope.service = null; }
        /* Sem `focusIndex = 0`: `refreshFocus` reencontra o mesmo chip pelo
           data-action, então ciclar o valor deixa o foco onde estava. Zerar o
           índice mandava o foco para o primeiro chip a cada ENTER, e escolher o
           terceiro gênero exigia voltar até ele toda vez. */
        render();
    }

    function resetCatalogueScope() {
        var contentType = state.section === 'LIVE' ? 'LIVE' :
            (state.section === 'MOVIES' ? 'MOVIE' : 'SERIES');
        var scope = catalogueScope(contentType);
        scope.genre = null;
        scope.service = null;
        /* O chip "Limpar filtros" some depois de limpar, então aqui o foco não
           tem para onde voltar: cai no primeiro chip, que é o de gênero. */
        focusIndex = 0;
        render();
    }

    function detailDuration(value) {
        var numeric;
        if (value == null || value === '') { return null; }
        if (/^\d+$/.test(String(value))) {
            numeric = Number(value);
            if (numeric > 0) { return formatPlaybackTime(numeric * (numeric <= 300 ? 60000 : 1000)); }
        }
        return BuroDomain.trim(value) || null;
    }

    function detailRating(value) {
        var numeric = Number(value);
        if (!isFinite(numeric) || numeric <= 0) { return null; }
        return '★ ' + BuroDomain.clamp(numeric, 0, 10).toFixed(1);
    }

    function detailCastNames(value) {
        var seen = {};
        return String(value || '').split(/[,;|/]+/).map(function (name) {
            return BuroDomain.trim(name);
        }).filter(function (name) {
            var key = BuroDomain.foldAccents(name);
            if (!name || seen[key]) { return false; }
            seen[key] = true;
            return true;
        }).slice(0, 16);
    }

    function mergeTmdbDetails(provider, metadata) {
        var merged = {};
        provider = provider || {};
        metadata = metadata || {};
        Object.keys(provider).forEach(function (name) { merged[name] = provider[name]; });
        ['title', 'plot', 'genre', 'duration', 'releaseDate', 'rating'].forEach(function (name) {
            if ((merged[name] == null || merged[name] === '') && metadata[name] != null) { merged[name] = metadata[name]; }
        });
        merged.tmdbId = metadata.tmdbId || merged.tmdbId || null;
        merged.imdbId = metadata.imdbId || merged.imdbId || null;
        if (metadata.critics != null) { merged.critics = metadata.critics; }
        merged.castMembers = Array.isArray(metadata.castMembers) ? metadata.castMembers : (merged.castMembers || []);
        merged.youtubeTrailerId = BuroDomain.sanitizeYouTubeReference(merged.youtubeTrailerId) ||
            BuroDomain.sanitizeYouTubeReference(metadata.youtubeTrailerId);
        return merged;
    }

    function rememberTmdbDetails(itemId, metadata) {
        var index = tmdbDetailOrder.indexOf(itemId);
        if (index >= 0) { tmdbDetailOrder.splice(index, 1); }
        tmdbDetailsMemory[itemId] = metadata;
        tmdbDetailOrder.push(itemId);
        while (tmdbDetailOrder.length > 40) { delete tmdbDetailsMemory[tmdbDetailOrder.shift()]; }
    }

    function clearTmdbDetails() {
        tmdbDetailsMemory = {};
        tmdbDetailOrder = [];
    }

    function currentTitleMatches(itemId) {
        return state.screen === 'SHELL' && state.screenData &&
            (state.screenData.kind === 'movie' || state.screenData.kind === 'series') &&
            state.screenData.parent && state.screenData.parent.id === itemId;
    }

    function enrichTitleFromTmdb(item, isSeries) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var key = BuroTmdb.keyForProfile(profileId);
        var cached = tmdbDetailsMemory[item.id];
        if (!key || !item) { return; }
        if (cached) {
            if (currentTitleMatches(item.id)) {
                state.screenData.details = mergeTmdbDetails(state.screenData.details, cached);
                if (!artworkMemory[item.id]) { rememberArtwork(item.id, cached.posterUrl); }
                if (!detailBackdropMemory[item.id]) { rememberDetailBackdrop(item.id, cached.backdropUrl); }
                render();
            }
            return;
        }
        if (tmdbTitleRequest && tmdbTitleRequest.abort) { tmdbTitleRequest.abort(); }
        tmdbTitleRequest = BuroTmdb.loadTitle(key, item, isSeries, state.preferences.language, function (metadata) {
            tmdbTitleRequest = null;
            rememberTmdbDetails(item.id, metadata);
            if (!currentTitleMatches(item.id)) { return; }
            state.screenData.details = mergeTmdbDetails(state.screenData.details, metadata);
            if (!artworkMemory[item.id]) { rememberArtwork(item.id, metadata.posterUrl); }
            if (!detailBackdropMemory[item.id]) { rememberDetailBackdrop(item.id, metadata.backdropUrl); }
            render();
            enrichTitleFromCritics(item, metadata);
        }, function () { tmdbTitleRequest = null; });
    }

    /*
      A fileira da crítica, depois que o TMDb já entregou o id do IMDb.

      Vem em segundo lugar de propósito: a página já está desenhada com a nota do
      provedor quando isto sai, então uma chave ausente, um limite de uso ou uma
      TV sem rede custa a fileira e mais nada. O resultado é guardado com o
      título em memória para que voltar à página não gaste outra requisição.
    */
    function enrichTitleFromCritics(item, metadata) {
        var imdbId = metadata && metadata.imdbId;
        if (!imdbId || !BuroCritics.configured()) { return; }
        if (criticsRequest && criticsRequest.abort) { criticsRequest.abort(); }
        criticsRequest = BuroCritics.scoresFor(imdbId, function (scores) {
            var remembered = tmdbDetailsMemory[item.id];
            criticsRequest = null;
            if (!scores) { return; }
            if (remembered) { remembered.critics = scores; }
            if (!currentTitleMatches(item.id)) { return; }
            state.screenData.details = mergeTmdbDetails(state.screenData.details, { critics: scores });
            render();
        });
    }

    /*
      Cada nota traz o nome de quem a calculou. Não usamos o tomate nem a pipoca
      da Rotten Tomatoes: as marcas são delas, e um número sem a marca continua
      dizendo o que precisa dizer.
    */
    function criticsStrip(details) {
        var scores = details && details.critics;
        var cells = [];
        if (!scores || !scores.hasAny) { return ''; }
        if (scores.tomatometer != null) {
            cells.push({ kind: 'tomatometer', score: scores.tomatometer,
                label: t('tomatometer'), value: scores.tomatometer + '%' });
        }
        if (scores.imdbRating != null) {
            cells.push({ kind: 'imdb', score: scores.imdbRating,
                label: t('imdbScore'), value: scores.imdbRating.toFixed(1) + '/10' });
        }
        if (scores.metascore != null) {
            cells.push({ kind: 'metascore', score: scores.metascore,
                label: t('metascore'), value: String(scores.metascore) });
        }
        return '<section class="detail-critics" aria-label="' + attr(t('criticsSection')) + '">' +
            cells.map(function (cell) {
                var mark = BuroCritics.markFor(cell.kind, cell.score);
                return '<div class="critic-score" role="group" aria-label="' + attr(cell.label + ': ' + cell.value) + '">' +
                    (mark ? '<span class="critic-mark" aria-hidden="true" style="background-color:' +
                        attr(mark.accent) + ';color:' + attr(mark.ink) + '">' + escapeHtml(mark.initials) + '</span>' : '') +
                    '<span class="critic-copy"><strong>' + escapeHtml(cell.value) + '</strong><small>' +
                    escapeHtml(cell.label) + '</small></span></div>';
            }).join('') + '</section>';
    }

    function detailFact(value, className) {
        return value ? '<span class="detail-fact ' + (className || '') + '">' + escapeHtml(value) + '</span>' : '';
    }

    function detailProgress(item) {
        var playback = playbackProgress(item.id);
        var percent;
        if (!playback) { return ''; }
        percent = BuroDomain.clamp(playback.percent, 0, 100);
        return '<div class="detail-watch-progress"><div><i style="width:' + percent.toFixed(2) + '%"></i></div><span>' +
            escapeHtml(t('watchedPercent').replace('{percent}', Math.round(percent))) + '</span></div>';
    }

    function shareYear(item, details) {
        var fromDetails = Number(String(details && details.releaseDate || '').substring(0, 4));
        var fromItem = Number(item && item.year);
        var embedded = /\((\d{4})\)/.exec(String(details && details.title || item && item.name || ''));
        if (fromDetails >= 1888 && fromDetails <= 2100) { return fromDetails; }
        if (fromItem >= 1888 && fromItem <= 2100) { return fromItem; }
        return embedded && Number(embedded[1]) >= 1888 && Number(embedded[1]) <= 2100 ? Number(embedded[1]) : null;
    }

    function openTitleShare(itemId) {
        var data = state.screenData;
        var item = data && data.parent;
        var details = data && data.details || {};
        var metadata;
        var payload;
        if (!item || item.id !== itemId || (data.kind !== 'movie' && data.kind !== 'series')) { return; }
        metadata = tmdbDetailsMemory[item.id] || {};
        payload = BuroShare.build({
            kind: data.kind === 'series' ? 'SERIES' : 'MOVIE',
            title: details.title || item.name, year: shareYear(item, details),
            artworkUrl: metadata.posterUrl || artworkMemory[item.id], description: details.plot
        });
        if (payload) { pushScreen('SHARE', payload); }
    }

    function renderShare() {
        var value = state.screenData;
        var qr = value && value.qr;
        var heading;
        if (!value) { goBack(); return; }
        heading = value.title + (value.year ? ' (' + value.year + ')' : '');
        shell('<div class="share-page"><div class="share-copy"><span class="hero-kicker">IPTV BURO</span><h2>' +
            escapeHtml(heading) + '</h2>' + (value.description ? '<p class="share-description">' +
            escapeHtml(value.description) + '</p>' : '') + '<p>' + t('shareScan') + '</p><div class="share-safety">' +
            t('shareSafe') + '</div><button class="button ghost focusable" data-action="back">‹ ' + t('back') +
            '</button></div><div class="share-code">' + (qr && qr.svg ? qr.svg : '<div class="share-qr-error">!</div>') +
            '<strong>' + (qr && qr.svg ? t('shareTitle') : t('shareQrUnavailable')) + '</strong><small>' + t('shareLink') +
            '</small><code data-share-url="' + attr(qr && qr.url || value.webUrl) + '">' +
            escapeHtml(qr && qr.url || value.webUrl) + '</code></div></div>', t('shareTitle'), true);
    }

    function requestedAppControlUri() {
        var application;
        var requested;
        try {
            if (!window.tizen || !tizen.application || !tizen.application.getCurrentApplication) { return ''; }
            application = tizen.application.getCurrentApplication();
            if (!application || !application.getRequestedAppControl) { return ''; }
            requested = application.getRequestedAppControl();
            return requested && requested.appControl ? String(requested.appControl.uri || '') : '';
        } catch (ignoredAppControl) { return ''; }
    }

    function receiveRequestedAppControl(rawOverride) {
        var raw = typeof rawOverride === 'string' ? rawOverride : requestedAppControlUri();
        var value = BuroShare.parseIncoming(raw);
        if (!value) { return false; }
        pendingSharedTitle = value;
        sharedTitleNoticeVisible = false;
        sharedTitleResolveId += 1;
        resolvePendingSharedTitle();
        return true;
    }

    function sharedTitleCandidate(snapshot, wanted, found, item) {
        if (found || !item || item.sourceId !== snapshot.sourceId) { return found; }
        if (item.contentType !== 'MOVIE' && item.contentType !== 'SERIES') { return found; }
        if (item.contentType === 'SERIES' && (!state.activeSource || state.activeSource.type !== 'XTREAM')) { return found; }
        if (!snapshotItemVisible(snapshot, item)) { return found; }
        return BuroShare.identity(item.contentType, item.name, item.year) === wanted.identity ? item : found;
    }

    /*
      Resolve contra o catalogo local completo, nao contra a amostra que a Home
      mantem em memoria. A identidade recebida nunca e usada como id do
      provedor, stream ou URL: ela serve somente para comparar nomes locais.
    */
    function resolvePendingSharedTitle() {
        var wanted = pendingSharedTitle;
        var snapshot;
        var requestId;
        if (!wanted || sharedTitleResolving || !state.ready || state.screen !== 'SHELL' ||
                !state.preferences || !state.preferences.acceptedLegal || !state.activeProfile || !state.activeSource) { return; }
        snapshot = catalogueVisibilitySnapshot();
        requestId = sharedTitleResolveId;
        sharedTitleResolving = true;
        BuroStorage.fold('items', function (found, item) {
            return sharedTitleCandidate(snapshot, wanted, found, item);
        }, null, function (found) {
            sharedTitleResolving = false;
            if (requestId !== sharedTitleResolveId || pendingSharedTitle !== wanted) {
                resolvePendingSharedTitle();
                return;
            }
            if (state.screen !== 'SHELL') { return; }
            if (!found) {
                sharedTitleNoticeVisible = true;
                render();
                return;
            }
            pendingSharedTitle = null;
            sharedTitleNoticeVisible = false;
            mergeItems([found]);
            showToast(t('sharedOpening').replace('{title}', wanted.title), false);
            if (found.contentType === 'SERIES') { openSeriesById(found.id, state.section); }
            else { openMovieDetails(found.id, state.section); }
        }, function () {
            sharedTitleResolving = false;
            if (requestId !== sharedTitleResolveId || pendingSharedTitle !== wanted) {
                resolvePendingSharedTitle();
                return;
            }
            sharedTitleNoticeVisible = true;
            if (state.screen === 'SHELL') { render(); }
            showToast(t('sharedResolveError'), true);
        });
    }

    function retryPendingSharedTitle() {
        if (!pendingSharedTitle) { return; }
        sharedTitleNoticeVisible = false;
        sharedTitleResolveId += 1;
        resolvePendingSharedTitle();
    }

    /*
      Titulos parecidos, tirados do proprio catalogo.

      Do catalogo e nao do TMDb de proposito: sem chave configurada a pagina de
      detalhes fica com hero e cinco botoes e mais nada, e foi exatamente assim
      que ela apareceu na TV de teste. Genero em comum e o sinal mais barato que
      existe aqui — ja esta em memoria, nao custa rede e nao depende de chave.

      Tambem e o que da ao D-pad para onde descer: com so os botoes de acao, a
      tela nao rolava porque nao havia nada abaixo para receber o foco.
    */
    function similarTitles(item, details, limit) {
        var genreText = String((details && details.genre) || item.genre || '');
        var wanted = {};
        var scored = [];
        var maximum = Number(limit) || 12;
        genreText.split(/[,\/|;]/).forEach(function (part) {
            var clean = BuroDomain.foldAccents(BuroDomain.trim(part));
            if (clean) { wanted[clean] = true; }
        });
        if (!Object.keys(wanted).length) { return []; }
        state.items.forEach(function (candidate) {
            var shared = 0;
            if (candidate.id === item.id) { return; }
            if (candidate.contentType !== item.contentType) { return; }
            if (!itemVisible(candidate)) { return; }
            String(candidate.genre || '').split(/[,\/|;]/).forEach(function (part) {
                var clean = BuroDomain.foldAccents(BuroDomain.trim(part));
                if (clean && wanted[clean]) { shared += 1; }
            });
            if (!shared) { return; }
            scored.push({ item: candidate, shared: shared, rating: Number(candidate.rating) || 0 });
        });
        scored.sort(function (left, right) {
            return (right.shared - left.shared) || (right.rating - left.rating) ||
                String(left.item.name).localeCompare(String(right.item.name));
        });
        return scored.slice(0, maximum).map(function (row) { return row.item; });
    }

    function renderTitleDetails(item, details, isSeries, episodes) {
        var facts = [];
        var cast;
        var castMembers;
        var supporting = '';
        var trailerId;
        var seasonIds = {};
        var related;
        var episodeRows = episodes || [];
        details = details || { title: item.name };
        trailerId = BuroDomain.sanitizeYouTubeReference(details.youtubeTrailerId);
        if (details.releaseDate || item.year) { facts.push(details.releaseDate || String(item.year)); }
        if (detailDuration(details.duration)) { facts.push(detailDuration(details.duration)); }
        if (details.genre || item.genre) { facts.push(details.genre || item.genre); }
        if (detailRating(details.rating != null ? details.rating : item.rating)) {
            facts.push(detailRating(details.rating != null ? details.rating : item.rating));
        }
        if (isSeries && episodeRows.length) {
            episodeRows.forEach(function (episode) {
                seasonIds[Number(episode.locator && episode.locator.season) || 0] = true;
            });
            facts.push(t('seasonCount').replace('{count}', Object.keys(seasonIds).length));
            facts.push(t('episodeCount').replace('{count}', episodeRows.length));
        }
        cast = detailCastNames(details.cast);
        castMembers = Array.isArray(details.castMembers) ? details.castMembers : [];
        if (details.director || details.country) {
            supporting += '<section class="detail-credit-card"><h3>' + t('credits') + '</h3>' +
                (details.director ? '<p><strong>' + t('director') + '</strong> ' + escapeHtml(details.director) + '</p>' : '') +
                (details.country ? '<p><strong>' + t('country') + '</strong> ' + escapeHtml(details.country) + '</p>' : '') + '</section>';
        }
        if (castMembers.length || cast.length) {
            supporting += '<section class="detail-cast"><h3>' + t('castTitle') + '</h3><div>' +
                (castMembers.length ? castMembers : cast.map(function (name) { return { name: name }; })).map(function (member) {
                    var photo = safeArtworkUrl(member.photoUrl);
                    return '<button class="cast-chip focusable" data-action="person" data-name="' + attr(member.name) + '">' +
                        (photo ? '<img src="' + attr(photo) + '" alt="">' : '<i>' + escapeHtml(member.name.charAt(0).toUpperCase()) + '</i>') +
                        '<span><strong>' + escapeHtml(member.name) + '</strong>' +
                        (member.character ? '<small>' + escapeHtml(member.character) + '</small>' : '') + '</span></button>';
                }).join('') + '</div></section>';
        }
        /* Sem chave, a pagina fica com o que a lista mandou: sem elenco, sem
           sinopse, sem arte. Dizer isso aqui, com o caminho ao lado, e melhor
           do que deixar o usuario achar que o app e assim mesmo. */
        if (!BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id)) {
            supporting += '<div class="detail-metadata-hint"><p>' + escapeHtml(t('detailNoMetadata')) +
                '</p><button class="button ghost focusable" data-action="tmdb-settings">' +
                t('detailAddKey') + '</button></div>';
        }
        related = similarTitles(item, details, 12);
        if (related.length) {
            supporting += '<section class="detail-related"><h3>' + t('similarTitles') + '</h3>' +
                mediaCards(related, 'poster') + '</section>';
        }
        return '<div class="detail-page"><div class="detail-hero">' + detailArtworkHtml(item) +
            '<span class="hero-kicker">' + (isSeries ? t('series') : t('movies')) +
            '</span><h2>' + escapeHtml(details.title || item.name) + '</h2>' +
            (facts.length ? '<div class="detail-facts">' + facts.map(function (fact) {
                return detailFact(fact, /^★/.test(fact) ? 'rating' : '');
            }).join('') + '</div>' : '') + criticsStrip(details) +
            '<p>' + escapeHtml(details.plot || t('noSynopsis')) + '</p>' + detailProgress(item) +
            detailActionsHtml(item, isSeries, episodeRows, trailerId) + '</div>' +
            (supporting ? '<div class="detail-support">' + supporting + '</div>' : '') + '</div>';
    }

    function epgClock(program, end) {
        var value = Number(end ? program.endEpochSeconds : program.startEpochSeconds);
        var date;
        if (value > 0) {
            date = new Date(value * 1000);
            return ('0' + date.getHours()).slice(-2) + ':' + ('0' + date.getMinutes()).slice(-2);
        }
        value = end ? program.end : program.start;
        return value && String(value).length >= 16 ? String(value).substring(11, 16) : '—';
    }

    function epgIsNow(program, nowSeconds) {
        var start = Number(program.startEpochSeconds);
        var end = Number(program.endEpochSeconds);
        return start > 0 && end > start && nowSeconds >= start && nowSeconds < end;
    }

    function epgProgress(program, nowSeconds) {
        var start = Number(program.startEpochSeconds);
        var end = Number(program.endEpochSeconds);
        if (!epgIsNow(program, nowSeconds)) { return 0; }
        return BuroDomain.clamp((nowSeconds - start) / (end - start) * 100, 0, 100);
    }

    function renderLiveDetails(item, schedule) {
        var nowSeconds = Math.floor(Date.now() / 1000);
        var current = null;
        var rows = schedule.map(function (program) {
            var isNow = epgIsNow(program, nowSeconds);
            var isPast = Number(program.endEpochSeconds) > 0 && Number(program.endEpochSeconds) <= nowSeconds;
            if (isNow) { current = program; }
            return '<div class="epg-row ' + (isNow ? 'current' : '') + (isPast ? ' past' : '') + '"' +
                (isNow ? ' aria-current="true"' : '') + '><time>' + escapeHtml(epgClock(program, false)) +
                '<small>' + escapeHtml(epgClock(program, true)) + '</small></time><div><strong>' + escapeHtml(program.title) +
                '</strong><p>' + escapeHtml(program.description || '') + '</p>' +
                (isNow ? '<div class="epg-progress"><i style="width:' + epgProgress(program, nowSeconds).toFixed(2) + '%"></i></div>' : '') +
                '</div><span>' + (isNow ? t('now') : '') + '</span></div>';
        }).join('');
        return '<div class="detail-hero live-detail">' + artworkHtml(item, 'detail-art') +
            '<span class="hero-kicker">' + t('live') + '</span><h2>' + escapeHtml(item.name) + '</h2>' +
            (current ? '<div class="live-now"><span>' + t('now') + '</span><div><strong>' + escapeHtml(current.title) + '</strong><p>' +
                escapeHtml(epgClock(current, false) + '–' + epgClock(current, true)) +
                (current.description ? ' · ' + current.description : '') + '</p></div></div>' : '') +
            '<div class="action-row detail-actions"><button class="button primary focusable" data-action="play" data-id="' +
            attr(item.id) + '">' + t('watchLive') + '</button><button class="button ghost focusable" data-action="favorite" data-id="' +
            attr(item.id) + '">' + (isFavorite(item.id) ? t('removeFavorite') : t('addFavorite')) +
            '</button></div></div><div class="section-heading"><h2>' + t('programmeGuide') + '</h2><p>' + schedule.length +
            '</p></div><div class="epg-list">' + (rows || '<p class="form-message">' + t('epgUnavailable') + '</p>') + '</div>';
    }

    function renderSearch() {
        var query = state.screenData && state.screenData.query ? state.screenData.query : '';
        var matches = state.screenData && state.screenData.matches ? state.screenData.matches : [];
        var loading = Boolean(state.screenData && state.screenData.searching);
        var error = state.screenData && state.screenData.error;
        var page = state.screenData && Number(state.screenData.page) || 0;
        var hasMore = Boolean(state.screenData && state.screenData.hasMore);
        var body = '';
        if (loading) { body = '<div class="search-loading"><span class="boot-indicator"></span><p>' + t('searchWorking') + '</p></div>'; }
        else if (error) { body = '<div class="catalogue-error">' + emptyState('!', t('couldNotLoad'), t('searchLoadError'), 'search-retry', t('retry')) + '</div>'; }
        else if (!query) { body = emptyState('?', t('searchIdle'), t('searchIdleBody'), '', ''); }
        else if (!matches.length) { body = emptyState('?', t('searchEmpty'), t('searchEmptyBody'), '', ''); }
        else {
            body = '<div class="section-heading"><h2>' + escapeHtml(query) + '</h2><p>' +
                t('searchPage').replace('{page}', page + 1) + '</p></div>' + mediaCards(matches) +
                '<div class="search-pagination">' +
                (page > 0 ? '<button class="button ghost focusable" data-action="search-previous">' + t('previousPage') + '</button>' : '') +
                (hasMore ? '<button class="button primary focusable" data-action="search-next">' + t('nextPage') + '</button>' : '') +
                '</div>';
        }
        shell('<div class="form-panel"><div class="field-row"><div class="field"><label>' + t('search') +
            '</label><input id="search-query" class="focusable" maxlength="80" value="' + attr(query) + '"></div>' +
            '<button class="button primary focusable" data-action="search-run">' + t('search') + '</button></div></div>' +
            body,
            t('search'), true);
    }

    function runSearch() {
        var input = document.getElementById('search-query');
        var query = BuroDomain.trim(input ? input.value : '');
        clearSearchDebounce();
        runSearchPage(query, 0);
    }

    function runSearchPage(query, page, pageCursors) {
        var requestId = ++searchRequestId;
        var previous = state.screenData;
        var cursors = pageCursors || (previous && previous.kind === 'search' && previous.query === query ?
            previous.pageCursors : null) || { 0: null };
        page = Math.max(0, Number(page) || 0);
        if (!query) { state.screenData = { kind: 'search', query: '', matches: [], page: 0 }; render(); return; }
        if (page > 0 && !cursors[page]) { page = 0; cursors = { 0: null }; }
        state.screenData = { kind: 'search', query: query, matches: [], searching: true, page: page,
            pageCursors: cursors, requestId: requestId };
        focusIndex = 0;
        render();
        BuroStorage.searchPage(query, itemVisible, cursors[page], SEARCH_PAGE_SIZE, function (result) {
            var matches = result.rows || [];
            if (requestId !== searchRequestId || state.section !== 'SEARCH' || !state.screenData ||
                    state.screenData.requestId !== requestId) { return; }
            if (result.hasMore && result.nextCursor) { cursors[page + 1] = result.nextCursor; }
            else { delete cursors[page + 1]; }
            mergeItems(matches);
            state.screenData = { kind: 'search', query: query, matches: matches, searching: false,
                page: page, pageCursors: cursors, hasMore: Boolean(result.hasMore) };
            focusIndex = 0;
            render();
        }, function (error) {
            if (requestId !== searchRequestId || state.section !== 'SEARCH' || !state.screenData ||
                    state.screenData.requestId !== requestId) { return; }
            state.screenData = { kind: 'search', query: query, matches: [], searching: false,
                page: page, pageCursors: cursors, error: friendlyError(error) };
            focusIndex = 0;
            render();
        });
    }

    function changeSearchPage(offset) {
        var data = state.screenData || {};
        runSearchPage(data.query || '', Math.max(0, (Number(data.page) || 0) + offset), data.pageCursors);
    }

    function renderMyBuro() {
        var profileId = state.activeProfile && state.activeProfile.id;
        var byItemId = {};
        var rows;
        state.items.forEach(function (item) { byItemId[item.id] = item; });
        rows = state.favorites.filter(function (favorite) {
            return favorite.profileId === profileId && Boolean(byItemId[favorite.itemId]);
        }).sort(function (left, right) {
            var timeOrder = Number(right.createdAt) - Number(left.createdAt);
            return timeOrder || String(right.id || '').localeCompare(String(left.id || ''));
        }).slice(0, FAVORITES_LIMIT);
        var items = rows.map(function (favorite) {
            return byItemId[favorite.itemId];
        }).filter(function (item) {
            return Boolean(item) && itemVisible(item);
        });
        shell(libraryContent('MY_BURO', items, 'B', t('myBuro'), t('favoritesEmpty')), t('myBuro'), true);
    }

    /*
      A página de lembretes.

      O Android traz aqui o interruptor do aviso diário e a escolha do horário.
      Nenhum dos dois foi portado, e não por falta de tempo: o config.xml declara
      `background-support="disable"`, então nada roda com o app fechado e uma TV
      desligada não avisa ninguém. Um seletor de horário que não dispara seria
      uma promessa falsa, então a página diz em voz alta o que a TV realmente faz.
    */
    function renderReminders() {
        var entries = reminderCards();
        var content;
        if (!entries.length) {
            content = emptyState('!', t('remindersEmpty'), t('remindersEmptyBody'), '', '');
        } else {
            content = '<p class="profile-help">' + escapeHtml(t('remindersSubtitle')) + '</p>' +
                reminderRowsHtml(entries) +
                '<p class="reminders-notice-hint">' + escapeHtml(t('remindersNoNotice')) + '</p>';
        }
        shell(content, t('reminders'), true);
    }

    /*
      O aviso que substitui a notificação diária.

      Mostrado uma vez por abertura do app, porque abrir o app é o único momento
      em que a TV pode dizer alguma coisa. Silencioso quando não há nada dentro do
      horizonte — um lembrete para daqui a seis meses fica guardado sem virar
      aviso, exatamente como no Android.
    */
    function reminderNoticeText() {
        var digest = BuroDomain.reminderDigest(profileReminders());
        var parts = [];
        function line(count, one, many) {
            if (!count) { return; }
            parts.push(t(count === 1 ? one : many).replace('{count}', count));
        }
        if (!digest.total) { return ''; }
        line(digest.releasedToday.length, 'reminderNoticeReleased', 'reminderNoticeReleasedMany');
        line(digest.upcoming.length, 'reminderNoticeSoon', 'reminderNoticeSoonMany');
        line(digest.waiting.length, 'reminderNoticeWaiting', 'reminderNoticeWaitingMany');
        return parts.join(' · ');
    }

    function showReminderNoticeOnce() {
        var message;
        if (state.reminderNoticeShown) { return; }
        state.reminderNoticeShown = true;
        /* O mesmo digest alimenta o brinde e o sino: o brinde passa, o sino fica
           para quem não estava olhando a tela naquele segundo. */
        refreshNotificationDigest();
        message = reminderNoticeText();
        if (message) { showToast(t('reminderNoticeTitle') + ': ' + message, false); }
        render();
    }

    /*
      O sino, com a contagem do que ainda não foi lido.

      Zero não desenha marcador nenhum em vez de desenhar um "0": um contador a
      zero chama atenção para a ausência de novidade, que é o contrário do que
      um sino serve para dizer.
    */
    function notificationBellHtml() {
        var unread = BuroNotifications.unreadCount(profileNotifications());
        return '<button class="topbar-bell focusable" data-action="notifications" aria-label="' +
            attr(t('notificationsOpen') + (unread ? ' · ' + t('notificationsUnread').replace('{count}', unread) : '')) +
            '"><span class="bell-glyph" aria-hidden="true">!</span>' +
            (unread ? '<span class="bell-badge">' + escapeHtml(String(Math.min(99, unread))) + '</span>' : '') +
            '</button>';
    }

    function profileNotifications() {
        return BuroNotifications.sanitize(state.preferences && state.preferences.notifications);
    }

    function saveNotifications(rows) {
        state.preferences.notifications = BuroNotifications.sanitize(rows);
        savePreferences();
    }

    function notificationKindLabel(kind) {
        if (kind === 'NEW_EPISODE') { return t('notificationKindEpisode'); }
        if (kind === 'NEW_SEASON') { return t('notificationKindSeason'); }
        return t('notificationKindReminder');
    }

    /*
      Constrói o digest do dia a partir dos lembretes do perfil.

      Roda a cada abertura porque a TV não roda nada com o app fechado — ver
      `renderReminders`. O id é a data, então reconstruir não duplica: quem já
      leu o aviso de hoje continua com ele lido.
    */
    function refreshNotificationDigest() {
        var reminders = profileReminders();
        var digest;
        var isoDate;
        var body;
        var current;
        if (!state.preferences) { return; }
        digest = BuroDomain.reminderDigest(reminders);
        if (!digest.releasedToday.length && !digest.upcoming.length) { return; }
        isoDate = localEditorialDay();
        body = reminderNoticeText();
        if (!body) { return; }
        current = BuroNotifications.add(profileNotifications(), {
            id: BuroNotifications.reminderDigestId(isoDate),
            kind: 'REMINDER',
            title: t('reminderNoticeTitle'),
            body: body,
            createdAt: Date.now(),
            read: false
        });
        if (current.length !== profileNotifications().length) { saveNotifications(current); }
    }

    function renderNotifications() {
        var rows = BuroNotifications.newestFirst(profileNotifications());
        var unread = BuroNotifications.unreadCount(rows);
        var content;
        if (!rows.length) {
            content = emptyState('!', t('notificationsEmpty'), t('notificationsEmptyBody'), '', '');
        } else {
            content = '<div class="section-heading"><h2>' + t('notificationsTitle') + '</h2><p>' +
                (unread ? escapeHtml(t('notificationsUnread').replace('{count}', unread)) : '') + '</p></div>' +
                '<div class="notice-list">' + rows.map(function (row) {
                    return '<div class="notice-row ' + (row.read ? 'read' : 'unread') + '">' +
                        '<div><span class="notice-kind">' + escapeHtml(notificationKindLabel(row.kind)) +
                        '</span><strong>' + escapeHtml(row.title) + '</strong>' +
                        (row.body ? '<p>' + escapeHtml(row.body) + '</p>' : '') + '</div>' +
                        '<button class="button ghost focusable" data-action="notification-remove" data-id="' +
                        attr(row.id) + '" aria-label="' + attr(t('notificationsRemove')) + '">×</button></div>';
                }).join('') + '</div>' +
                (unread ? '<div class="action-row"><button class="button primary focusable" data-action="notifications-read">' +
                    t('notificationsMarkAllRead') + '</button></div>' : '');
        }
        shell(content + '<p class="form-note privacy">' + escapeHtml(t('notificationsBackground')) + '</p>',
            t('notificationsTitle'), true);
    }

    function markNotificationsRead() {
        saveNotifications(BuroNotifications.markAllRead(profileNotifications()));
        render();
    }

    function removeNotification(id) {
        saveNotifications(BuroNotifications.remove(profileNotifications(), id));
        render();
    }

    function transientLibraryItem(item, progressItemId) {
        var copy = {};
        Object.keys(item || {}).forEach(function (key) { copy[key] = item[key]; });
        copy._libraryProgressItemId = progressItemId;
        return copy;
    }

    function progressItems(history) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var rows = state.progress.filter(function (entry) { return entry.profileId === profileId; })
            .sort(function (a, b) { return Number(b.updatedAt) - Number(a.updatedAt); });
        var byItemId = {};
        var seen = {};
        if (!history) {
            rows = rows.filter(function (entry) { return !entry.completed; });
        }
        rows = rows.slice(0, history ? HISTORY_LIMIT : CONTINUE_WATCHING_LIMIT);
        state.items.forEach(function (item) { byItemId[item.id] = item; });
        return rows.map(function (entry) {
            var item = byItemId[entry.itemId];
            var parent;
            if (!item) { return null; }
            if (item.contentType === 'EPISODE' && item.categoryId) {
                parent = byItemId[item.categoryId];
                if (parent && parent.contentType === 'SERIES') { item = parent; }
            }
            if (seen[item.id]) { return null; }
            seen[item.id] = true;
            return transientLibraryItem(item, entry.itemId);
        }).filter(function (item) { return Boolean(item) && itemVisible(item); });
    }

    function libraryKind(item) {
        if (!item) { return 'UNKNOWN'; }
        return item.contentType === 'EPISODE' ? 'SERIES' : item.contentType;
    }

    function filterLibraryItems(items, kind) {
        return kind === 'ALL' ? items : items.filter(function (item) { return libraryKind(item) === kind; });
    }

    function libraryFilterBar(section, items) {
        var selected = libraryFilters[section] || 'ALL';
        var kinds = ['MOVIE', 'SERIES', 'LIVE'].filter(function (kind) {
            return items.some(function (item) { return libraryKind(item) === kind; });
        });
        var labels = { MOVIE: 'movies', SERIES: 'series', LIVE: 'live' };
        if (selected !== 'ALL' && kinds.indexOf(selected) < 0) {
            selected = 'ALL'; libraryFilters[section] = 'ALL';
        }
        if (kinds.length < 2) { return ''; }
        return '<div class="catalogue-filter-bar library-filter-bar"><button class="filter-chip focusable ' +
            (selected === 'ALL' ? 'selected' : '') + '" data-action="library-filter" data-section="' +
            attr(section) + '" data-kind="ALL">' + t('filterAll') + '</button>' + kinds.map(function (kind) {
                return '<button class="filter-chip focusable ' + (selected === kind ? 'selected' : '') +
                    '" data-action="library-filter" data-section="' + attr(section) + '" data-kind="' + kind + '">' +
                    t(labels[kind]) + '</button>';
            }).join('') + '</div>';
    }

    function libraryContent(section, items, symbol, title, emptyMessage) {
        var filterBar;
        var filtered;
        var pageCount;
        var page;
        var start;
        var visible;
        if (!items.length) { libraryPages[section] = 0; return emptyState(symbol, title, emptyMessage, '', ''); }
        filterBar = libraryFilterBar(section, items);
        filtered = filterLibraryItems(items, libraryFilters[section]);
        pageCount = Math.max(1, Math.ceil(filtered.length / LIBRARY_PAGE_SIZE));
        page = BuroDomain.clamp(Number(libraryPages[section]) || 0, 0, pageCount - 1);
        libraryPages[section] = page;
        start = page * LIBRARY_PAGE_SIZE;
        visible = filtered.slice(start, start + LIBRARY_PAGE_SIZE);
        return filterBar + mediaCards(visible) + paginationControls(
            'library-page', page, pageCount, start, start + visible.length, filtered.length,
            ' data-section="' + attr(section) + '"', 'library-pagination'
        );
    }

    /*
      Um estado por linha, em texto: numa TV o usuário lê de longe, e um ícone
      colorido sozinho não diz se o download parou por escolha dele ou porque o
      pendrive saiu.
    */
    function downloadStateLabel(entry) {
        var labels = {
            QUEUED: 'downloadQueued',
            DOWNLOADING: 'loading',
            PAUSED: 'downloadPaused',
            COMPLETED: 'downloadCompleted',
            FAILED: 'downloadFailed',
            CANCELLED: 'downloadCancelled',
            STORAGE_MISSING: 'usbRemoved'
        };
        if (entry.state === 'FAILED' && entry.errorCode === 'DOWNLOAD_INTERRUPTED') {
            return t('downloadInterrupted');
        }
        return t(labels[entry.state] || 'downloadQueued');
    }

    function localizedDownloadNumber(value, digits) {
        var rendered = Number(value).toFixed(digits);
        return BuroI18n.language() === 'en' ? rendered : rendered.replace('.', ',');
    }

    function formatDownloadRate(bytesPerSecond) {
        var rate = Math.max(0, Number(bytesPerSecond) || 0);
        var kibibyte = 1024;
        var mebibyte = kibibyte * 1024;
        if (rate >= mebibyte) {
            return localizedDownloadNumber(rate / mebibyte, 1) + ' MB/s';
        }
        if (rate >= kibibyte) { return Math.round(rate / kibibyte) + ' kB/s'; }
        return Math.floor(rate) + ' B/s';
    }

    function formatDownloadDuration(secondsValue) {
        var seconds = Math.max(0, Math.floor(Number(secondsValue) || 0));
        var hours;
        var minutes;
        if (seconds < 60) { return seconds + ' s'; }
        if (seconds < 3600) { return Math.floor(seconds / 60) + ' min'; }
        hours = Math.floor(seconds / 3600);
        minutes = Math.floor((seconds % 3600) / 60);
        return hours + ' h' + (minutes ? ' ' + minutes + ' min' : '');
    }

    function downloadProgressLabel(entry) {
        var parts = [downloadStateLabel(entry)];
        if (entry.state !== 'DOWNLOADING') { return parts[0]; }
        parts.push(Math.max(0, Math.min(100, Number(entry.percent) || 0)) + '%');
        if (Number(entry.bytesPerSecond) > 0) {
            parts.push(formatDownloadRate(entry.bytesPerSecond));
            if (Number(entry.remainingSeconds) > 0) {
                parts.push(formatDownloadDuration(entry.remainingSeconds));
            }
        }
        return parts.join(' · ');
    }

    /* Mesma prioridade visual de Android/Windows: trabalho que ainda exige
       atenção fica antes das cópias já concluídas. A ordem original da fila é
       o desempate explícito, sem depender da estabilidade de Array.sort no
       Web Runtime de TVs antigas. */
    function downloadStateRank(stateValue) {
        if (stateValue === 'QUEUED' || stateValue === 'DOWNLOADING' || stateValue === 'PAUSED' ||
                stateValue === 'STORAGE_MISSING') { return 0; }
        if (stateValue === 'FAILED' || stateValue === 'CANCELLED') { return 1; }
        if (stateValue === 'COMPLETED') { return 3; }
        return 2;
    }

    function orderedDownloadEntries(entries) {
        return (entries || []).map(function (entry, index) {
            entry._queueOrder = index;
            return entry;
        }).sort(function (left, right) {
            return downloadStateRank(left.state) - downloadStateRank(right.state) ||
                Number(left._queueOrder) - Number(right._queueOrder);
        });
    }

    function focusedDownloadId() {
        var focused = focusables[focusIndex];
        var row;
        if (!focused || !focused.closest) { return null; }
        row = focused.closest('.download-row');
        return row ? row.getAttribute('data-download-id') : null;
    }

    function downloadRowActions(entry) {
        if (entry.state === 'DOWNLOADING') {
            return '<button class="button ghost focusable" data-action="download-pause" data-id="' +
                attr(entry.id) + '">' + t('downloadPause') + '</button>' +
                '<button class="button ghost focusable" data-action="download-cancel" data-id="' +
                attr(entry.id) + '">' + t('downloadCancel') + '</button>';
        }
        if (entry.state === 'PAUSED' || entry.state === 'STORAGE_MISSING') {
            return '<button class="button ghost focusable" data-action="download-resume" data-id="' +
                attr(entry.id) + '">' + t('downloadResume') + '</button>' +
                '<button class="button ghost focusable" data-action="download-cancel" data-id="' +
                attr(entry.id) + '">' + t('downloadCancel') + '</button>';
        }
        if (entry.state === 'COMPLETED') {
            return '<button class="button primary focusable" data-action="download-play" data-id="' +
                attr(entry.id) + '">' + t('watch') + '</button>' +
                '<button class="button ghost focusable" data-action="download-remove" data-id="' +
                attr(entry.id) + '">' + t('downloadRemove') + '</button>';
        }
        return '<button class="button ghost focusable" data-action="download-remove" data-id="' +
            attr(entry.id) + '">' + t('downloadRemove') + '</button>';
    }

    function renderDownloads() {
        var entries = orderedDownloadEntries(BuroDownloads.list());
        var body;
        var hasMovies;
        var hasEpisodes;
        var shown;
        var needle;
        var pageCount;
        var start;
        var visible;
        var focusedId;
        var focusedPosition = -1;
        var toolbar;
        var shouldFollowFocused = followFocusedDownloadOnRender;

        followFocusedDownloadOnRender = false;

        if (!BuroDownloads.enabled()) {
            shell(emptyState('#', t('downloads'), t('usbRequired'), '', ''), t('downloads'), true);
            return;
        }
        if (!entries.length) {
            downloadPage = 0;
            shell(emptyState('#', t('downloads'), t('noDownloads'), '', ''), t('downloads'), true);
            return;
        }

        hasMovies = entries.some(function (entry) { return entry.contentType === 'MOVIE'; });
        hasEpisodes = entries.some(function (entry) { return entry.contentType === 'EPISODE'; });
        if ((downloadFilter === 'MOVIE' && !hasMovies) || (downloadFilter === 'EPISODE' && !hasEpisodes)) {
            downloadFilter = 'ALL';
        }
        shown = entries.filter(function (entry) {
            return downloadFilter === 'ALL' || entry.contentType === downloadFilter;
        });
        needle = BuroDomain.foldAccents(downloadQuery);
        if (needle) {
            shown = shown.filter(function (entry) {
                return BuroDomain.foldAccents(entry.name).indexOf(needle) >= 0;
            });
        }
        focusedId = shouldFollowFocused ? focusedDownloadId() : null;
        if (focusedId) {
            shown.some(function (entry, index) {
                if (entry.id === focusedId) { focusedPosition = index; return true; }
                return false;
            });
        }
        if (focusedPosition >= 0) { downloadPage = Math.floor(focusedPosition / DOWNLOAD_PAGE_SIZE); }
        pageCount = Math.max(1, Math.ceil(shown.length / DOWNLOAD_PAGE_SIZE));
        downloadPage = BuroDomain.clamp(Number(downloadPage) || 0, 0, pageCount - 1);
        start = downloadPage * DOWNLOAD_PAGE_SIZE;
        visible = shown.slice(start, start + DOWNLOAD_PAGE_SIZE);
        toolbar = '<div class="catalogue-filter-bar download-filter-bar">' +
            (hasMovies && hasEpisodes ? '<button class="filter-chip focusable ' + (downloadFilter === 'ALL' ? 'selected' : '') +
                '" data-action="download-filter" data-kind="ALL">' + t('filterAll') + '</button>' +
                '<button class="filter-chip focusable ' + (downloadFilter === 'MOVIE' ? 'selected' : '') +
                '" data-action="download-filter" data-kind="MOVIE">' + t('movies') + '</button>' +
                '<button class="filter-chip focusable ' + (downloadFilter === 'EPISODE' ? 'selected' : '') +
                '" data-action="download-filter" data-kind="EPISODE">' + t('series') + '</button>' : '') +
            '<button class="filter-chip focusable ' + (downloadCompact ? 'selected' : '') +
            '" data-action="download-compact">' + t('layoutCompact') + '</button></div>' +
            '<div class="download-search"><label for="download-query">' + t('downloadSearch') + '</label>' +
            '<input id="download-query" class="focusable" type="text" maxlength="80" value="' +
            attr(downloadQuery) + '" placeholder="' + attr(t('downloadSearchHint')) + '"></div>' +
            '<p class="download-hint">' + escapeHtml(t('downloadHint')) + '</p>';
        if (!shown.length) {
            downloadPage = 0;
            body = toolbar + emptyState('#', t('downloads'), t('downloadNoMatch'), '', '');
        } else {
            body = toolbar + '<div class="download-list ' + (downloadCompact ? 'compact' : '') + '">' +
                visible.map(function (entry) {
                    return '<div class="download-row" data-download-id="' + attr(entry.id) + '"><div class="download-info">' +
                        '<strong>' + escapeHtml(entry.name) + '</strong>' +
                        '<small>' + escapeHtml(downloadProgressLabel(entry)) + '</small>' +
                        '<div class="download-track"><div class="download-fill" style="width:' +
                        entry.percent + '%"></div></div></div>' +
                        '<div class="download-actions">' + downloadRowActions(entry) + '</div></div>';
                }).join('') + '</div>' + paginationControls(
                    'download-page', downloadPage, pageCount, start, start + visible.length, shown.length,
                    '', 'download-pagination'
                );
        }

        shell(body, t('downloads'), true);
    }

    function renderProgressSection(history) {
        var items = progressItems(history);
        var title = history ? t('history') : t('continueWatching');
        var section = history ? 'HISTORY' : 'CONTINUE_WATCHING';
        shell(libraryContent(section, items, '>', title, t(history ? 'historyEmpty' : 'continueEmpty')), title, true);
    }

    function renderSources() {
        var cards = state.sources.map(function (source) {
            var sync = catalogueSyncStatus(source);
            var syncLine = sync && sync.total ? '<span class="source-sync-state ' + sync.state.toLowerCase() + '">' +
                escapeHtml(catalogueSyncText(sync)) + '</span>' : '';
            return '<div class="source-entry"><button class="source-card focusable" data-action="select-source" data-id="' + attr(source.id) + '">' +
                '<span class="source-kind">' + escapeHtml(source.type) + '</span><h3>' + escapeHtml(source.name) + '</h3>' +
                '<span class="source-count">' + Number(source.channelCount || 0) + '</span>' + syncLine + '</button>' +
                '<button class="source-manage focusable" data-action="source-manage" data-id="' + attr(source.id) + '">' +
                t('manageSource') + '</button></div>';
        }).join('');
        cards += '<button class="source-card focusable" data-action="source-add"><span class="source-kind">+</span><h3>' +
            t('addSource') + '</h3><span class="source-count">M3U · Xtream · Stalker</span></button>';
        shell('<div class="card-row">' + cards + '</div>', t('sources'), true);
    }

    function renderSourceManage() {
        var data = state.screenData || {};
        var source = state.sources.filter(function (row) { return row.id === data.sourceId; })[0];
        var sync;
        var syncPanel = '';
        if (!source) {
            root.innerHTML = emptyState('!', t('error'), t('sourceError'), 'back', t('back'));
            return;
        }
        if (data.sourceName == null) { data.sourceName = source.name; }
        var disabled = data.refreshing ? ' disabled' : '';
        var refreshMessage = data.refreshing ? t('refreshingSource') :
            (data.refreshError ? data.refreshError : (data.refreshSuccess ? t('sourceRefreshed') : ''));
        sync = catalogueSyncStatus(source);
        if (sync && sync.total) {
            syncPanel = catalogueSyncBanner(source);
            if (sync.state === 'COMPLETE') {
                syncPanel = '<section class="catalogue-sync-complete"><span>✓</span><div><strong>' +
                    t('catalogueSyncTitle') + '</strong><p>' + escapeHtml(catalogueSyncText(sync)) + '</p></div></section>';
            }
        }
        shell('<div class="form-panel source-manage-panel"><h2>' + t('manageSource') + '</h2>' +
            '<div class="source-summary"><span>' + escapeHtml(source.type) + '</span><strong>' +
            Number(source.channelCount || 0) + '</strong></div>' +
            syncPanel +
            '<div class="field"><label>' + t('sourceName') + '</label><input id="source-manage-name" class="focusable" maxlength="80" value="' +
            attr(data.sourceName) + '"' + disabled + '></div><div class="form-message' + (data.refreshError ? ' error' : '') + '">' +
            escapeHtml(refreshMessage) + '</div><p class="delete-warning">' + t('deleteSourceWarning') + '</p>' +
            '<div class="action-row"><button class="button primary focusable" data-action="source-refresh"' + disabled + '>' +
            t('refreshSource') + '</button><button class="button ghost focusable" data-action="source-rename"' + disabled + '>' + t('save') + '</button>' +
            '<button class="button ghost focusable" data-action="back"' + disabled + '>' + t('cancel') + '</button>' +
            '<button class="button danger focusable" data-action="source-delete"' + disabled + '>' +
            (data.confirmDelete ? t('confirmDeleteSource') : t('deleteSource')) + '</button></div></div>', t('manageSource'), true);
    }

    function renderSourceChoice() {
        var localM3u = BuroUsb.hasStorage() ? '<button class="choice-card focusable" data-action="source-usb-m3u">' +
            '<span class="choice-icon">USB</span><h3>' + t('localM3u') + '</h3></button>' : '';
        shell('<div class="choice-row"><button class="choice-card focusable" data-action="source-form" data-type="XTREAM">' +
            '<span class="choice-icon">X</span><h3>' + t('xtream') + '</h3></button>' +
            '<button class="choice-card focusable" data-action="source-form" data-type="REMOTE_M3U">' +
            '<span class="choice-icon">M3U</span><h3>' + t('remoteM3u') + '</h3></button>' + localM3u +
            '<button class="choice-card focusable" data-action="source-form" data-type="STALKER">' +
            '<span class="choice-icon">S</span><h3>' + t('stalker') + '</h3></button></div>', t('addSource'), false);
    }

    function renderUsbM3uPicker() {
        var data = state.screenData || {};
        var body;
        if (data.loading) {
            body = '<div class="search-loading"><span class="boot-indicator"></span><p>' + t('usbM3uLoading') + '</p></div>';
        } else if (data.error) {
            body = emptyState('!', t('usbM3uError'), t('usbM3uHint'), 'source-usb-m3u-retry', t('retry'));
        } else if (!(data.files || []).length) {
            body = emptyState('USB', t('usbM3uEmpty'), t('usbM3uHint'), 'back', t('back'));
        } else {
            body = '<p class="form-message">' + t('usbM3uHint') + '</p><div class="source-file-list">' +
                data.files.map(function (file) {
                    return '<button class="source-card focusable" data-action="source-usb-m3u-select" data-key="' +
                        attr(file.key) + '"><span>USB · M3U</span><strong>' + escapeHtml(file.name) + '</strong><small>' +
                        Math.max(1, Math.ceil(Number(file.size) / 1024)) + ' KB</small></button>';
                }).join('') + '</div>';
        }
        shell(body, t('localM3u'), true);
    }

    function loadUsbM3uFiles() {
        if (state.screen !== 'SOURCE_USB_M3U') { return; }
        state.screenData = { loading: true, files: [] }; render();
        BuroUsb.listPlaylists(function (files) {
            if (state.screen !== 'SOURCE_USB_M3U') { return; }
            state.screenData = { loading: false, files: files }; render();
        }, function () {
            if (state.screen !== 'SOURCE_USB_M3U') { return; }
            state.screenData = { loading: false, files: [], error: true }; render();
        });
    }

    function openUsbM3uPicker() {
        pushScreen('SOURCE_USB_M3U', { loading: true, files: [] });
        loadUsbM3uFiles();
    }

    function sourceFormTitle(type) {
        if (type === 'XTREAM') { return t('xtream'); }
        if (type === 'STALKER') { return t('stalker'); }
        return t('remoteM3u');
    }

    function renderSourceForm(type) {
        var fields = '<div class="field"><label>' + t('sourceName') + '</label><input id="source-name" class="focusable" maxlength="80"></div>';
        var notes = '';
        if (type === 'XTREAM') {
            fields += '<div class="field"><label>' + t('serverUrl') + '</label><input id="source-server" class="focusable" inputmode="url"></div>' +
                '<div class="field-row"><div class="field"><label>' + t('username') + '</label><input id="source-username" class="focusable" autocomplete="off"></div>' +
                '<div class="field"><label>' + t('password') + '</label><input id="source-password" type="password" class="focusable" autocomplete="off"></div></div>';
        } else if (type === 'STALKER') {
            /* O MAC é a credencial da assinatura, então ele entra junto com o
               portal e sai daqui mascarado — ver stalkerPrivacy. O usuário e a
               senha ficam abaixo do aviso de "opcional" porque a maioria dos
               portais não pede nenhum dos dois. */
            fields += '<p class="form-note">' + escapeHtml(t('stalkerBody')) + '</p>' +
                '<div class="field"><label>' + t('stalkerPortal') + '</label><input id="source-portal" class="focusable" inputmode="url" placeholder="' +
                attr(t('stalkerPortalHint')) + '"></div>' +
                '<div class="field"><label>' + t('stalkerMac') + '</label><input id="source-mac" class="focusable" autocomplete="off" maxlength="24" placeholder="' +
                attr(t('stalkerMacHint')) + '"><small id="source-mac-hint" class="field-hint"></small></div>' +
                '<div class="section-heading form-optional"><h3>' + t('stalkerOptional') + '</h3><p>' +
                escapeHtml(t('stalkerOptionalBody')) + '</p></div>' +
                '<div class="field-row"><div class="field"><label>' + t('stalkerUsername') + '</label><input id="source-username" class="focusable" autocomplete="off"></div>' +
                '<div class="field"><label>' + t('stalkerPassword') + '</label><input id="source-password" type="password" class="focusable" autocomplete="off"></div></div>';
            notes = '<p class="form-note privacy">' + escapeHtml(t('stalkerPrivacy')) + '</p>' +
                '<p id="source-http-warning" class="form-note warning" hidden>' + escapeHtml(t('stalkerHttpWarning')) + '</p>';
        } else {
            fields += '<div class="field"><label>' + t('playlistUrl') + '</label><input id="source-playlist" class="focusable" inputmode="url"></div>';
        }
        shell('<div class="form-panel"><h2>' + sourceFormTitle(type) + '</h2>' + fields + notes +
            '<div id="source-form-message" class="form-message"></div><div class="action-row"><button class="button primary focusable" data-action="source-connect" data-type="' +
            type + '">' + t('connect') + '</button><button class="button ghost focusable" data-action="back">' + t('cancel') +
            '</button></div></div>', t('addSource'), true);
    }

    function settingCard(key, property) {
        return '<button class="setting-card focusable ' + (state.preferences[property] ? 'on' : '') + '" data-action="toggle-setting" data-property="' +
            property + '"><div><h3>' + t(key) + '</h3><p>' + t(state.preferences[property] ? 'settingOn' : 'settingOff') +
            '</p></div><span class="toggle"><span></span></span></button>';
    }

    function applicationVersion() {
        var info;
        try {
            if (window.tizen && window.tizen.application && window.tizen.application.getAppInfo) {
                info = window.tizen.application.getAppInfo();
                if (info && info.version) { return String(info.version); }
            }
        } catch (ignored) {}
        return APP_VERSION_FALLBACK;
    }

    function subtitleChoice(action, value, labelKey, selected, colour) {
        var sample = colour ?
            '<span class="subtitle-colour-sample colour-' + attr(value) + '" aria-hidden="true"></span>' :
            '<span class="subtitle-size-sample size-' + attr(value) + '" aria-hidden="true"></span>';
        return '<button class="subtitle-choice focusable ' + (selected ? 'selected' : '') +
            '" data-action="' + attr(action) + '" data-value="' + attr(value) + '">' + sample +
            '<strong>' + escapeHtml(t(labelKey)) + '</strong></button>';
    }

    /*
      Android e Windows deixam as opções de legenda visíveis ao mesmo tempo.
      Na TV, esconder a próxima opção atrás de ENTER tornava "Muito grande" oito
      movimentos distante (voltar ao chip + ciclar). As duas linhas cabem em
      1920 px, cada escolha é um destino D-pad e a cor é mostrada de verdade.
    */
    function subtitleSettingsPanel() {
        var size = state.preferences.subtitleSize;
        var colour = state.preferences.subtitleColour;
        var sizes = [
            ['small', 'subtitleSizeSmall'], ['medium', 'subtitleSizeMedium'],
            ['large', 'subtitleSizeLarge'], ['huge', 'subtitleSizeHuge']
        ];
        var colours = [
            ['white', 'subtitleColourWhite'], ['yellow', 'subtitleColourYellow'],
            ['grey', 'subtitleColourGrey'], ['green', 'subtitleColourGreen'],
            ['cyan', 'subtitleColourCyan']
        ];
        return '<section class="subtitle-settings-card"><h3>' + t('subtitleSettings') + '</h3><p>' +
            t('subtitleHint') + '</p><h4>' + t('subtitleSize') + '</h4><div class="subtitle-choice-row">' +
            sizes.map(function (option) {
                return subtitleChoice('subtitle-size-select', option[0], option[1], size === option[0], false);
            }).join('') + '</div><h4>' + t('subtitleColour') + '</h4><div class="subtitle-choice-row">' +
            colours.map(function (option) {
                return subtitleChoice('subtitle-colour-select', option[0], option[1], colour === option[0], true);
            }).join('') + '</div><div class="subtitle-background-row">' +
            settingCard('subtitleBackground', 'subtitleBackground') + '</div></section>';
    }

    /*
      Estado da licença em uma linha, com o motivo do bloqueio quando houver.

      Motivos distintos e não uma mensagem só: uma assinatura vencida precisa
      de pagamento e uma revalidação precisa de internet. Dizer "pague" a quem
      está sem conexão é como se perde uma venda.
    */
    function licenceStatusText(decision) {
        var reasons = {
            TRIAL_ENDED: 'licenceTrialEnded',
            EXPIRED: 'licenceExpired',
            REVOKED: 'licenceRevoked',
            UNREGISTERED: 'licenceUnregistered',
            NEEDS_VERIFICATION: 'licenceNeedsVerification',
            UNAVAILABLE: 'licenceUnavailable'
        };
        if (decision.allowed) {
            return t(decision.trial ? 'licenceTrial' : 'licenceActive');
        }
        return t(reasons[decision.reason] || 'licenceUnavailable');
    }

    function licenceSection() {
        var decision = BuroLicense.decide();
        var deviceId = BuroLicense.deviceId() ||
            (BuroIdentity.publicSummary() ? BuroIdentity.publicSummary().deviceId : '');
        return '<div class="section-heading"><h2>' + t('licence') + '</h2></div>' +
            '<div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="licence-activate">' +
            '<div><h3>' + escapeHtml(licenceStatusText(decision)) + '</h3><p>' +
            escapeHtml(t('licenceDeviceId') + ': ' + (deviceId || '—')) +
            '</p></div><strong>›</strong></button></div>';
    }

    /*
      A chave é digitada com o D-pad, então o campo aceita o teclado da TV e o
      código do aparelho fica visível na mesma tela: quem compra precisa
      informá-lo, e obrigar a anotar noutro lugar antes é um passo a mais.
    */
    function renderLicenceActivation() {
        var decision = BuroLicense.decide();
        var deviceId = BuroLicense.deviceId() ||
            (BuroIdentity.publicSummary() ? BuroIdentity.publicSummary().deviceId : '');
        var pending = state.screenData && state.screenData.busy;

        root.innerHTML = '<main class="gate-screen"><div class="brand-mark">B</div>' +
            '<h1>' + t('licence') + '</h1>' +
            '<p class="gate-copy">' + escapeHtml(licenceStatusText(decision)) + '</p>' +
            '<div class="licence-device"><small>' + escapeHtml(t('licenceDeviceId')) + '</small>' +
            '<strong>' + escapeHtml(deviceId || '—') + '</strong></div>' +
            '<p class="licence-hint">' + escapeHtml(t('licenceBuyHint')) + '</p>' +
            '<div class="field"><label>' + t('licenceKey') + '</label>' +
            '<input id="licence-key" class="focusable" maxlength="32" autocomplete="off"></div>' +
            '<div class="action-row">' +
            '<button class="button primary focusable" data-action="licence-redeem"' +
            (pending ? ' disabled' : '') + '>' +
            (pending ? t('licenceChecking') : t('licenceRedeem')) + '</button>' +
            '<button class="button ghost focusable" data-action="back">' + t('back') + '</button>' +
            '</div></main>';
    }

    function redeemLicenceKey() {
        var field = document.getElementById('licence-key');
        var value = field ? field.value : '';
        if (!value) { showToast(t('licenceKeyInvalid'), true); return; }

        state.screenData = { busy: true };
        render();
        BuroLicense.redeem(value, function () {
            state.screenData = null;
            showToast(t('licenceRedeemed'));
            render();
        }, function (error) {
            state.screenData = null;
            showToast(licenceError(error), true);
            render();
        });
    }

    function licenceError(error) {
        var code = error && error.code;
        var known = {
            KEY_REQUIRED: 'licenceKeyInvalid',
            LICENSE_KEY_MISSING: 'licenceUnavailable',
            LICENSE_SIGNATURE_INVALID: 'licenceUnavailable',
            LICENSE_UNSIGNED: 'licenceUnavailable',
            LICENSE_NONCE_MISMATCH: 'licenceUnavailable',
            NETWORK_TIMEOUT: 'licenceOffline',
            LICENSE_UNREACHABLE: 'licenceOffline',
            HTTP_ERROR: 'licenceKeyInvalid'
        };
        return t(known[code] || 'licenceKeyInvalid');
    }

    function renderSettings() {
        var tmdbConfiguration = BuroTmdb.configuration(state.activeProfile && state.activeProfile.id);
        var manageableCategories = categoriesForSettings();
        var subtitleSettings = '';
        var activeProfile = state.activeProfile;
        var version = applicationVersion();
        var languages = SUPPORTED_LANGUAGES.map(function (language) {
            var selected = state.preferences.language === language.tag;
            return '<button class="settings-language-option focusable ' + (selected ? 'selected' : '') +
                '" data-action="language" data-language="' + attr(language.tag) + '"><span><strong>' +
                escapeHtml(language.name) + '</strong><small>' + escapeHtml(language.tag) + '</small></span>' +
                (selected ? '<em>' + t('languageCurrent') + '</em>' : '') + '</button>';
        }).join('');
        if (BuroPlayer.styledSubtitlesAvailable()) {
            subtitleSettings = '<div class="section-heading"><h2>' + t('subtitleSettings') + '</h2></div>' +
                subtitleSettingsPanel();
        }
        shell('<section class="settings-about-card"><div><h2>IPTV BURO</h2><p>' +
            escapeHtml(t('settingsVersion').replace('{version}', version)) + '</p></div><p>' +
            escapeHtml(t('settingsLegal')) + '</p></section>' +
            licenceSection() +
            '<div class="section-heading"><h2>' + t('metadata') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable ' + (tmdbConfiguration.effective ? 'on' : '') +
            '" data-action="tmdb-settings"><div><h3>' + t('tmdbTitle') + '</h3><p>' +
            (tmdbConfiguration.effective ? t('configured') : t('notConfigured')) + '</p></div><strong>TMDb</strong></button>' +
            '<button class="setting-card focusable ' + (BuroCritics.configured() ? 'on' : '') +
            '" data-action="critics-settings"><div><h3>' + t('criticsTitle') + '</h3><p>' +
            (BuroCritics.configured() ? t('configured') : t('notConfigured')) + '</p></div><strong>OMDb</strong></button></div>' +
            '<div class="section-heading"><h2>' + t('contentProtection') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="parental-form"><div><h3>' + t('parentalPin') +
            '</h3><p>' + (state.preferences.parentalPin ? t('configured') : t('notConfigured')) + '</p></div><strong>PIN</strong></button>' +
            '<button class="setting-card focusable ' + (state.preferences.parentalPin && state.preferences.lockAdultCategories ? 'on' : '') +
            '" data-action="toggle-adult-lock"><div><h3>' + t('lockAdult') + '</h3><p>' +
            (state.preferences.parentalPin ? t(state.preferences.lockAdultCategories ? 'settingOn' : 'settingOff') : t('pinRequired')) +
            '</p></div><span class="toggle"><span></span></span></button>' +
            '<button class="setting-card focusable" data-action="category-settings"><div><h3>' + t('categoryControl') +
            '</h3><p>' + manageableCategories.length + '</p></div><strong>›</strong></button></div>' +
            subtitleSettings +
            '<div class="section-heading"><h2>' + t('storageTitle') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="storage-settings"><div><h3>' +
            t('storageTitle') + '</h3><p>' + escapeHtml(t('storageHint')) + '</p></div><strong>›</strong></button></div>' +
            '<div class="section-heading"><h2>' + t('settings') + '</h2></div><div class="settings-grid">' +
            settingCard('reducedMotion', 'reducedMotion') + settingCard('highContrast', 'highContrast') +
            settingCard('removeTransparency', 'reducedTransparency') + '</div>' +
            '<div class="section-heading settings-language-heading"><div><h2>' + t('language') + '</h2><p>' +
            t('settingsLanguageHint') + '</p></div></div><div class="settings-language-list">' + languages + '</div>' +
            '<div class="section-heading"><h2>' + t('activeProfile') + '</h2></div>' +
            '<button class="settings-active-profile focusable" data-action="section" data-section="PROFILES">' +
            '<span class="settings-profile-avatar ' + (activeProfile && activeProfile.isKids ? 'kids' : '') + '">' +
            profileAvatarContent(activeProfile || { name: t('profiles') }) + '</span><span><strong>' +
            escapeHtml(activeProfile ? activeProfile.name : t('profiles')) + '</strong><small>' + t('chooseProfile') +
            '</small></span><b>›</b></button>', t('settings'), true);
    }

    function renderTmdbSettings() {
        var profile = state.activeProfile;
        var configuration = BuroTmdb.configuration(profile && profile.id);
        var draft = state.screenData || {};
        var message = state.screenData && state.screenData.messageKey ?
            '<p class="form-message ' + (state.screenData.error ? 'error' : '') + '">' +
            escapeHtml(t(state.screenData.messageKey)) + '</p>' : '';
        shell('<div class="form-panel tmdb-settings-panel"><h2>' + t('tmdbTitle') + '</h2><p class="form-message">' +
            t('tmdbBody') + '</p><div class="tmdb-guide-action"><button class="button ghost focusable" data-action="tmdb-guide">?' +
            '<span>' + t('tmdbGuideButton') + '</span></button></div><section class="tmdb-key-scope"><h3>' + t('tmdbSharedLabel') + '</h3><p>' +
            t('tmdbSharedHint') + '</p><div class="field"><label>' + t('tmdbKeyLabel') + '</label>' +
            '<input id="tmdb-key-shared" class="focusable" type="password" autocomplete="off" maxlength="256" value="' +
            attr(draft.tmdbDraftShared || '') + '" placeholder="' +
            attr(configuration.shared ? t('configured') : t('tmdbKeyHint')) + '"></div><div class="action-row">' +
            '<button class="button primary focusable" data-action="tmdb-save" data-scope="shared">' + t('save') + '</button>' +
            (configuration.shared ? '<button class="button ghost focusable" data-action="tmdb-clear" data-scope="shared">' + t('tmdbClear') + '</button>' : '') +
            '</div></section><section class="tmdb-key-scope"><h3>' +
            escapeHtml(t('tmdbProfileLabel').replace('{profile}', profile ? profile.name : '—')) + '</h3><p>' +
            t('tmdbProfileHint') + '</p><div class="field"><label>' + t('tmdbKeyLabel') + '</label>' +
            '<input id="tmdb-key-profile" class="focusable" type="password" autocomplete="off" maxlength="256" value="' +
            attr(draft.tmdbDraftProfile || '') + '" placeholder="' +
            attr(configuration.profile ? t('configured') : t('tmdbKeyHint')) + '"></div><div class="action-row">' +
            '<button class="button primary focusable" data-action="tmdb-save" data-scope="profile">' + t('save') + '</button>' +
            (configuration.profile ? '<button class="button ghost focusable" data-action="tmdb-clear" data-scope="profile">' + t('tmdbClear') + '</button>' : '') +
            '</div></section>' + message + '<p class="tmdb-attribution">' + t('tmdbAttribution') + '</p></div>', t('tmdbTitle'), true);
    }

    /*
      A chave do OMDb, uma só e compartilhada.

      Diferente do TMDb, não há escopo por perfil: a chave não escolhe idioma nem
      região, ela só destrava três números públicos, então um segundo escopo seria
      complexidade sem ganho.
    */
    /*
      O que esta TV guarda do catálogo.

      O Android traz aqui um controle de orçamento em gigabytes, porque lá as
      capas são gravadas em disco e ocupam espaço de verdade. Nesta TV elas não
      são: `rememberArtwork` mantém no máximo ARTWORK_MEMORY_LIMIT URLs em
      memória e nenhum byte de imagem é escrito. Um controle de gigabytes aqui
      não governaria nada, então a tela mede o que existe — quantos títulos e
      categorias estão gravados — e oferece a única ação real: apagá-los.

      Vídeo baixado fica no USB e já tem a sua própria tela em Downloads.
    */
    function storageCounts(done) {
        BuroStorage.count('items', function (items) {
            BuroStorage.count('categories', function (categories) {
                done({ items: items, categories: categories });
            }, function () { done(null); });
        }, function () { done(null); });
    }

    function measureStorage() {
        var draft = state.screenData || {};
        if (state.screen !== 'STORAGE_SETTINGS') { return; }
        draft.measuring = true;
        draft.counts = null;
        state.screenData = draft;
        render();
        storageCounts(function (counts) {
            if (state.screen !== 'STORAGE_SETTINGS') { return; }
            state.screenData.measuring = false;
            state.screenData.counts = counts;
            render();
        });
    }

    function storageRow(title, value, hint) {
        return '<div class="storage-row"><div><h3>' + escapeHtml(title) + '</h3>' +
            (hint ? '<p>' + escapeHtml(hint) + '</p>' : '') + '</div><strong>' +
            escapeHtml(value) + '</strong></div>';
    }

    function renderStorageSettings() {
        var draft = state.screenData || {};
        var counts = draft.counts;
        var catalogueValue = draft.measuring || !counts ? t('storageMeasuring') :
            t('storageItems').replace('{count}', counts.items) + ' · ' +
            t('storageCategories').replace('{count}', counts.categories);
        var downloadCount = BuroDownloads.available() ? BuroDownloads.list().length : 0;
        var message = draft.messageKey ?
            '<p class="form-message ' + (draft.error ? 'error' : '') + '">' +
            escapeHtml(t(draft.messageKey)) + '</p>' : '';
        shell('<div class="form-panel tmdb-settings-panel"><h2>' + t('storageTitle') + '</h2>' +
            '<p class="form-message">' + escapeHtml(t('storageHint')) + '</p>' +
            '<div class="storage-list">' +
            storageRow(t('storageCatalogue'), catalogueValue, '') +
            storageRow(t('storageArtwork'), String(artworkOrder.length), t('storageArtworkHint')) +
            storageRow(t('storageDownloads'), String(downloadCount), t('storageDownloadsHint')) +
            '</div><p class="form-note privacy">' + escapeHtml(t('storageClearHint')) + '</p>' +
            '<div class="action-row"><button class="button ghost focusable" data-action="storage-measure">' +
            t('storageRefresh') + '</button>' +
            '<button class="button ' + (draft.confirmClear ? 'primary' : 'ghost') +
            ' focusable" data-action="storage-clear">' +
            (draft.confirmClear ? t('storageClearConfirm') : t('storageClearCatalogue')) + '</button></div>' +
            message + '</div>', t('storageTitle'), true);
    }

    /*
      Apaga o catálogo gravado, uma fonte por vez.

      Fontes, perfis e favoritos ficam: o que sai é o que pode ser buscado de
      novo. Confirmação em dois toques, como excluir fonte e excluir perfil.
    */
    function clearStoredCatalogue() {
        var draft = state.screenData || {};
        if (!draft.confirmClear) {
            draft.confirmClear = true;
            state.screenData = draft;
            render();
            return;
        }
        BuroStorage.clearCatalogue(function () {
            state.categories = [];
            state.items = [];
            /* Todo cache em memória é indexado por id de item, e os itens
               acabaram de sair: deixar qualquer um deles cheio guardaria arte de
               títulos que não existem mais. */
            clearTmdbDetails();
            BuroCritics.clear();
            artworkMemory = {};
            artworkOrder.length = 0;
            detailBackdropMemory = {};
            detailBackdropOrder.length = 0;
            seriesDetailsMemory = {};
            Object.keys(artworkRequests).forEach(function (key) { delete artworkRequests[key]; });
            /* A sincronização em curso aponta para categorias que não existem
               mais; cada fonte perde o seu progresso junto com o catálogo. */
            state.sources.forEach(function (source) {
                BuroCatalogueSync.clearSource(source.id);
                BuroHeroEnrichment.clearSource(source.id);
            });
            state.screenData = { messageKey: 'storageCleared', error: false };
            render();
            measureStorage();
        }, function () {
            state.screenData = { messageKey: 'storageClearFailed', error: true };
            render();
        });
    }

    function renderCriticsSettings() {
        var configured = BuroCritics.configured();
        var draft = state.screenData || {};
        var message = draft.messageKey ?
            '<p class="form-message ' + (draft.error ? 'error' : '') + '">' +
            escapeHtml(t(draft.messageKey)) + '</p>' : '';
        shell('<div class="form-panel tmdb-settings-panel"><h2>' + t('criticsTitle') + '</h2>' +
            '<p class="form-message">' + escapeHtml(configured ? t('criticsConfigured') : t('criticsAbsent')) +
            '</p><div class="tmdb-guide-action"><button class="button ghost focusable" data-action="critics-guide">?' +
            '<span>' + t('criticsGuideButton') + '</span></button></div><div class="field"><label>' + t('criticsField') + '</label>' +
            '<input id="critics-key" class="focusable" type="password" autocomplete="off" maxlength="64" value="' +
            attr(draft.criticsDraft || '') + '" placeholder="' +
            attr(configured ? t('configured') : t('criticsHint')) + '"><small class="field-hint">' +
            escapeHtml(t('criticsHint')) + '</small></div><div class="action-row">' +
            '<button class="button primary focusable" data-action="critics-save">' + t('criticsSave') + '</button>' +
            (configured ? '<button class="button ghost focusable" data-action="critics-clear">' +
                t('criticsClear') + '</button>' : '') +
            '</div>' + message + '</div>', t('criticsTitle'), true);
    }

    function saveCriticsKey() {
        var input = document.getElementById('critics-key');
        var value = input && input.value;
        if (!BuroCritics.safeKey(value)) {
            state.screenData = { messageKey: 'criticsKeyInvalid', error: true }; render(); return;
        }
        BuroCritics.save(value, function () {
            if (state.screen !== 'CRITICS_SETTINGS') { return; }
            /* As notas guardadas com os títulos foram calculadas sem chave, então
               saem daqui para que a próxima visita busque de verdade. */
            clearTmdbDetails();
            state.screenData = { messageKey: 'criticsSaved', error: false }; render();
        }, function () {
            if (state.screen !== 'CRITICS_SETTINGS') { return; }
            state.screenData = { messageKey: 'tmdbSecureError', error: true }; render();
        });
    }

    function clearCriticsKey() {
        if (BuroCritics.remove()) {
            clearTmdbDetails();
            state.screenData = { messageKey: 'criticsRemoved', error: false };
        } else {
            state.screenData = { messageKey: 'tmdbSecureError', error: true };
        }
        render();
    }

    function renderCriticsGuide() {
        var steps = [
            { title: 'criticsGuideStep1Title', text: 'criticsGuideStep1Body', diagram: 'omdb-api' },
            { title: 'criticsGuideStep2Title', text: 'criticsGuideStep2Body', diagram: 'omdb-free' },
            { title: 'criticsGuideStep3Title', text: 'criticsGuideStep3Body', diagram: 'omdb-email' },
            { title: 'criticsGuideStep4Title', text: 'criticsGuideStep4Body', diagram: 'omdb-activate' }
        ];
        var rows = steps.map(function (step, index) {
            return '<article class="tmdb-guide-step critics-guide-step"><div class="tmdb-guide-copy">' +
                '<b>' + (index + 1) + '</b><div><h3>' + t(step.title) + '</h3><p>' + t(step.text) +
                '</p></div></div>' + tmdbGuideDiagram(step.diagram).replace(
                    'tmdb-guide-diagram', 'tmdb-guide-diagram critics-guide-diagram') + '</article>';
        }).join('');
        shell('<div class="form-panel tmdb-guide-panel"><h2>' + t('criticsGuideTitle') + '</h2>' +
            '<p class="tmdb-guide-intro">' + t('criticsGuideIntro') + '</p><div class="tmdb-guide-grid">' +
            rows + '</div><div class="action-row tmdb-guide-actions">' +
            '<button class="button primary focusable" data-action="critics-guide-open">' +
            t('criticsGuideOpenSite') + '</button><button class="button ghost focusable" data-action="back">' +
            t('back') + '</button></div></div>', t('criticsGuideTitle'), true);
    }

    function openCriticsGuide() {
        var input = document.getElementById('critics-key');
        state.screenData = state.screenData || {};
        state.screenData.criticsDraft = String(input && input.value || '').substring(0, 64);
        pushScreen('CRITICS_GUIDE');
    }

    function tmdbGuideDiagram(kind) {
        return '<div class="tmdb-guide-diagram ' + attr(kind) + '" aria-hidden="true">' +
            '<i></i><i></i><i></i><i></i><i></i><i></i></div>';
    }

    function renderTmdbGuide() {
        var steps = [
            { text: 'tmdbGuideStepAccount', diagram: 'sign-up' },
            { text: 'tmdbGuideStepSettings', diagram: 'settings-menu' },
            { text: 'tmdbGuideStepRequest', diagram: 'application-form' },
            { text: 'tmdbGuideStepCopy', diagram: 'the-key' }
        ];
        var rows = steps.map(function (step, index) {
            return '<article class="tmdb-guide-step"><div class="tmdb-guide-copy"><b>' + (index + 1) +
                '</b><p>' + t(step.text) + '</p></div>' + tmdbGuideDiagram(step.diagram) + '</article>';
        }).join('');
        shell('<div class="form-panel tmdb-guide-panel"><h2>' + t('tmdbGuideTitle') + '</h2><p class="tmdb-guide-intro">' +
            t('tmdbGuideIntro') + '</p><div class="tmdb-guide-grid">' + rows + '</div><div class="action-row tmdb-guide-actions">' +
            '<button class="button primary focusable" data-action="tmdb-guide-open">' + t('tmdbGuideOpenSite') + '</button>' +
            '<button class="button ghost focusable" data-action="back">' + t('back') + '</button></div></div>', t('tmdbGuideTitle'), true);
    }

    function openTmdbGuide() {
        var shared = document.getElementById('tmdb-key-shared');
        var profile = document.getElementById('tmdb-key-profile');
        state.screenData = state.screenData || {};
        state.screenData.tmdbDraftShared = String(shared && shared.value || '').substring(0, 256);
        state.screenData.tmdbDraftProfile = String(profile && profile.value || '').substring(0, 256);
        pushScreen('TMDB_GUIDE');
    }

    function saveTmdbKey(scope) {
        var input = document.getElementById('tmdb-key-' + scope);
        var value = input && input.value;
        var profileId = state.activeProfile && state.activeProfile.id;
        if (!BuroTmdb.safeKey(value)) {
            state.screenData = { messageKey: 'tmdbKeyInvalid', error: true }; render(); return;
        }
        state.screenData = { messageKey: 'tmdbChecking', error: false }; render();
        BuroTmdb.validateKey(value, function (validated) {
            if (state.screen !== 'TMDB_SETTINGS') { return; }
            BuroTmdb.save(scope, profileId, validated, function () {
                clearTmdbDetails();
                BuroTmdb.clearShelfCache();
                state.screenData = { messageKey: 'tmdbSaved', error: false }; render();
            }, function () {
                state.screenData = { messageKey: 'tmdbSecureError', error: true }; render();
            });
        }, function (error) {
            if (state.screen !== 'TMDB_SETTINGS') { return; }
            state.screenData = {
                messageKey: error && error.code === 'TMDB_KEY_REJECTED' ? 'tmdbKeyRejected' :
                    (error && error.code === 'TMDB_KEY_INVALID' ? 'tmdbKeyInvalid' : 'tmdbUnavailable'),
                error: true
            };
            render();
        });
    }

    function clearTmdbKey(scope) {
        if (BuroTmdb.remove(scope, state.activeProfile && state.activeProfile.id)) {
            clearTmdbDetails();
            BuroTmdb.clearShelfCache();
            state.screenData = { messageKey: 'tmdbCleared', error: false };
        } else { state.screenData = { messageKey: 'tmdbSecureError', error: true }; }
        render();
    }

    function personCreditKey(credit) {
        return (credit.isSeries ? 'series:' : 'movie:') + (credit.id || BuroDomain.foldAccents(credit.title));
    }

    function titleComparable(value) {
        return BuroDomain.foldAccents(value || '').replace(/\b(19|20)\d{2}\b/g, ' ').replace(/[^a-z0-9]+/g, ' ').replace(/^\s+|\s+$/g, '');
    }

    function hydratePersonLocalMatches(personData) {
        var wanted = {};
        (personData.credits || []).forEach(function (credit) {
            wanted[(credit.isSeries ? 'SERIES:' : 'MOVIE:') + titleComparable(credit.title)] = credit;
        });
        BuroStorage.fold('items', function (matches, item) {
            var key;
            var credit;
            if (!item || (item.contentType !== 'MOVIE' && item.contentType !== 'SERIES')) { return matches; }
            key = item.contentType + ':' + titleComparable(item.name);
            credit = wanted[key];
            if (credit && (!credit.year || !item.year || Number(credit.year) === Number(item.year)) && !matches[personCreditKey(credit)]) {
                matches[personCreditKey(credit)] = item;
            }
            return matches;
        }, {}, function (matches) {
            if (state.screen !== 'PERSON' || !state.screenData || state.screenData.name !== personData.name) { return; }
            state.screenData.localMatches = matches;
            render();
        }, function () {});
    }

    function renderPerson() {
        var data = state.screenData || {};
        var person = data.person;
        var matches = data.localMatches || {};
        var portrait = person && safeArtworkUrl(person.photoUrl);
        var header = '<div class="person-header">' +
            (portrait ? '<img class="person-portrait" src="' + attr(portrait) + '" alt="">' :
                '<span class="person-portrait fallback">' + escapeHtml(String(data.name || '?').charAt(0).toUpperCase()) + '</span>') +
            '<div><h2>' + escapeHtml(person && person.name || data.name || '') + '</h2>' +
            (person && (person.birthday || person.placeOfBirth) ? '<p>' +
                escapeHtml([person.birthday, person.placeOfBirth].filter(Boolean).join(' · ')) + '</p>' : '') +
            (data.loading ? '<p class="metadata-loading">' + t('personLoading') + '</p>' : '') + '</div></div>';
        var biography = person && person.biography ? '<p class="person-biography">' + escapeHtml(person.biography) + '</p>' : '';
        var hint = !data.configured ? '<p class="form-message">' + t('personMetadataHint') + '</p>' :
            (data.error ? '<p class="form-message">' + t(data.error === 'TMDB_KEY_REJECTED' ? 'tmdbKeyRejected' : 'tmdbUnavailable') + '</p>' : '');
        var credits = person && person.credits && person.credits.length ? '<div class="section-heading"><h2>' +
            t('personFilmography') + '</h2><p>' + person.credits.length + '</p></div><div class="person-credit-list">' +
            person.credits.map(function (credit) {
                var local = matches[personCreditKey(credit)];
                var poster = safeArtworkUrl(credit.posterUrl);
                var content = (poster ? '<img src="' + attr(poster) + '" alt="">' : '<span class="credit-poster">' +
                    escapeHtml(credit.title.charAt(0)) + '</span>') + '<div><strong>' + escapeHtml(credit.title) + '</strong><p>' +
                    escapeHtml([credit.year, credit.character].filter(Boolean).join(' · ')) + '</p>' +
                    (local ? '<small>' + t('personInLibrary') + '</small>' : '') + '</div>';
                return local ? '<button class="person-credit focusable" data-action="person-local" data-id="' + attr(local.id) + '">' + content + '</button>' :
                    (credit.id ? '<button class="person-credit focusable" data-action="person-credit" data-id="' + attr(credit.id) +
                        '" data-series="' + (credit.isSeries ? 'true' : 'false') + '" data-title="' + attr(credit.title) +
                        '" data-year="' + attr(credit.year || '') + '">' + content + '</button>' : '<div class="person-credit">' + content + '</div>');
            }).join('') + '</div>' : (!data.loading ? '<p class="form-message">' + t('personNoFilmography') + '</p>' : '');
        shell('<div class="person-page">' + header + biography + hint + credits + '</div>', t('castTitle'), true);
    }

    function openPerson(name) {
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (tmdbPersonRequest && tmdbPersonRequest.abort) { tmdbPersonRequest.abort(); }
        pushScreen('PERSON', { name: name, configured: Boolean(key), loading: Boolean(key), person: null, localMatches: {} });
        if (!key) { return; }
        tmdbPersonRequest = BuroTmdb.loadPerson(key, name, state.preferences.language, function (person) {
            tmdbPersonRequest = null;
            if (state.screen !== 'PERSON' || !state.screenData || state.screenData.name !== name) { return; }
            state.screenData.loading = false;
            state.screenData.person = person;
            render();
            hydratePersonLocalMatches(person);
        }, function (error) {
            tmdbPersonRequest = null;
            if (state.screen !== 'PERSON' || !state.screenData || state.screenData.name !== name) { return; }
            state.screenData.loading = false;
            state.screenData.error = error && error.code || 'TMDB_UNAVAILABLE';
            render();
        });
    }

    function openPersonLocal(itemId) {
        var found = findItemAndSource(itemId);
        personReturnData = state.screenData;
        state.screen = 'SHELL';
        if (!found.item) { state.section = 'MOVIES'; state.screenData = null; render(); return; }
        if (found.item.contentType === 'SERIES') { openSeriesById(itemId, 'MOVIES'); }
        else { openMovieDetails(itemId, 'MOVIES'); }
    }

    function subscriptionTitleKey(title) {
        return (title.isSeries ? 'series:' : 'movie:') + String(title.tmdbId || title.id || '');
    }

    function loadSubscriptions(kind, force) {
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        var region = BuroTmdb.safeRegion(state.preferences.tmdbRegion);
        var cached;
        kind = String(kind || 'MOVIES').toUpperCase();
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        cached = !force && key ? BuroTmdb.readShelfCache(region, kind, state.preferences.language) : null;
        state.screenData = {
            kind: 'subscriptions', filter: kind, region: region, loading: !cached,
            completedServices: 0, totalServices: 0, shelves: cached || [], error: null, selected: null
        };
        render();
        if (!key) { state.section = 'SETTINGS'; state.screenData = null; render(); return; }
        if (cached) { return; }
        subscriptionRequest = BuroTmdb.loadShelves(key, region, kind, state.preferences.language,
            function (completed, total, visible) {
                if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || state.screenData.filter !== kind) { return; }
                state.screenData.completedServices = completed;
                state.screenData.totalServices = total;
                state.screenData.visibleServices = visible;
                render();
            }, function (shelves) {
                subscriptionRequest = null;
                if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || state.screenData.filter !== kind) { return; }
                state.screenData.loading = false;
                state.screenData.shelves = shelves;
                BuroTmdb.writeShelfCache(region, kind, state.preferences.language, shelves);
                render();
            }, function (error) {
                subscriptionRequest = null;
                if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || state.screenData.filter !== kind) { return; }
                state.screenData.loading = false;
                state.screenData.error = error && error.code || 'TMDB_UNAVAILABLE';
                render();
            });
    }

    function subscriptionFacts(title) {
        return [title.year, detailRating(title.rating), title.duration ? (Number(title.duration) > 0 ? Number(title.duration) + ' min' : title.duration) : null, title.genre]
            .filter(Boolean).join(' · ');
    }

    /*
      Um título das prateleiras externas ainda não possui linha no catálogo.
      Mesmo assim ele precisa compartilhar a identidade por nome/ano usada pelos
      lembretes locais: quando a mesma obra entrar numa lista futura, os dois
      caminhos encontrarão uma única marca. O id TMDb não é usado como
      providerItemId porque isso quebraria essa reconciliação.
    */
    function subscriptionReminderItem(data) {
        var selected = data && data.selected;
        if (!selected || !BuroDomain.trim(selected.title)) { return null; }
        return {
            contentType: selected.isSeries ? 'SERIES' : 'MOVIE',
            name: selected.title,
            year: selected.year || null
        };
    }

    function renderSubscriptionSelection(data) {
        var selection = data.selection || { details: null, offers: [], unknown: false };
        var selected = data.selected;
        var reminderItem = subscriptionReminderItem(data);
        var reminderMarked = hasReminder(reminderItem);
        var details = mergeTmdbDetails({
            tmdbId: selected.tmdbId, title: selected.title, plot: selected.overview,
            releaseDate: selected.year, rating: selected.rating, youtubeTrailerId: selected.youtubeTrailerId
        }, selection.details || {});
        var poster = safeArtworkUrl((selection.details && selection.details.posterUrl) || selected.posterUrl);
        var backdrop = safeArtworkUrl(selection.details && selection.details.backdropUrl);
        var offers = (selection.localItem ? [{
            providerId: 'local', providerName: 'IPTV BURO', type: 'library', localItem: selection.localItem,
            requiresAttribution: false
        }] : []).concat(selection.offers || []);
        var offerHtml = data.selectionLoading ? '<div class="subscription-loading"><span class="boot-indicator"></span><p>' +
            t('subscriptionsLoadingOffers') + '</p></div>' : (offers.length ? offers.map(function (offer) {
                var action = offer.localItem ? 'subscription-local' : 'subscription-offer';
                return '<button class="subscription-offer focusable" data-action="' + action + '"' +
                    (offer.localItem ? ' data-id="' + attr(offer.localItem.id) + '"' : ' data-url="' + attr(offer.url || '') + '"') +
                    '>' + subscriptionOfferLogo(offer.providerLogoUrl) + '<div><strong>' + escapeHtml(offer.providerName) + '</strong><p>' + t('offer' +
                    String(offer.type || '').charAt(0).toUpperCase() + String(offer.type || '').slice(1)) + '</p>' +
                    (offer.requiresAttribution ? '<small>Streaming data provided by JustWatch</small>' : '') + '</div><span>›</span></button>';
            }).join('') : (!data.selectionLoading ? '<p class="form-message">' + t('subscriptionsUnknown') + '</p>' : ''));
        var cast = details.castMembers && details.castMembers.length ? '<section class="subscription-cast"><h3>' + t('castTitle') +
            '</h3><div>' + details.castMembers.map(function (member) {
                var photo = safeArtworkUrl(member.photoUrl);
                return '<button class="cast-chip focusable" data-action="person" data-name="' + attr(member.name) + '">' +
                    (photo ? '<img src="' + attr(photo) + '" alt="">' : '<i>' + escapeHtml(member.name.charAt(0)) + '</i>') +
                    '<span><strong>' + escapeHtml(member.name) + '</strong>' +
                    (member.character ? '<small>' + escapeHtml(member.character) + '</small>' : '') + '</span></button>';
            }).join('') + '</div></section>' : '';
        return '<div class="subscription-detail">' + (backdrop ? '<span class="subscription-backdrop"><img src="' + attr(backdrop) + '" alt=""></span>' : '') +
            '<button class="button ghost focusable" data-action="subscription-back">‹ ' + t('subscriptionsBack') + '</button>' +
            '<div class="subscription-title-head">' + (poster ? '<img src="' + attr(poster) + '" alt="">' : '') +
            '<div><span class="hero-kicker">' + t('subscriptionsWhere') + '</span><h2>' + escapeHtml(details.title || selected.title) + '</h2>' +
            (subscriptionFacts(details) ? '<p>' + escapeHtml(subscriptionFacts(details)) + '</p>' : '') +
            '<div class="action-row">' +
            (details.youtubeTrailerId ? '<button class="button ghost focusable" data-action="subscription-trailer">' + t('trailer') + '</button>' : '') +
            '<button class="button ghost focusable' + (reminderMarked ? ' selected' : '') +
            '" data-action="subscription-reminder" aria-pressed="' + (reminderMarked ? 'true' : 'false') + '">' +
            (reminderMarked ? t('reminderRemove') : t('reminderAdd')) + '</button></div>' +
            '</div></div><div class="section-heading"><h2>' + t('subscriptionsAvailable') + '</h2></div><div class="subscription-offers">' +
            offerHtml + '</div>' + (details.plot ? '<section class="subscription-copy"><h3>' + t('subscriptionsSynopsis') + '</h3><p>' +
            escapeHtml(details.plot) + '</p></section>' : '') + cast + '</div>';
    }

    function renderExpandedSubscription(data) {
        var expanded = data.expanded;
        var title = t('subscriptionsAllOn').replace('{service}', expanded.providerName);
        var status = expanded.loading ? '<div class="subscription-loading subscription-expanded-status" role="status">' +
            '<span class="boot-indicator"></span><p>' + t('subscriptionsLoadingMore') + '</p></div>' :
            (expanded.error ? '<p class="form-message error-message" role="status">' +
                t('subscriptionsMoreFailed') + '</p>' : '');
        var cards = expanded.titles.slice(0, 100).map(function (item) {
            var poster = safeArtworkUrl(item.posterUrl);
            return '<button class="subscription-poster focusable" data-action="subscription-title" data-key="' +
                attr(subscriptionTitleKey(item)) + '">' + (poster ? '<img src="' + attr(poster) + '" alt="">' :
                '<span>' + escapeHtml(item.title.charAt(0)) + '</span>') + subscriptionCardLogo(expanded.providerLogoUrl) +
                '<strong>' + escapeHtml(item.title) + '</strong>' +
                (item.year ? '<small>' + item.year + '</small>' : '') + '</button>';
        }).join('');
        return '<div class="subscription-expanded" aria-busy="' + (expanded.loading ? 'true' : 'false') + '">' +
            '<button class="button ghost focusable" data-action="subscription-expanded-back">‹ ' +
            t('subscriptionsBack') + '</button><div class="section-heading"><h2>' +
            subscriptionProviderLogo(expanded.providerLogoUrl) + escapeHtml(title) + '</h2><p>' +
            expanded.titles.length + '</p></div>' + status + '<div class="subscription-expanded-grid">' + cards + '</div></div>';
    }

    function renderSubscriptions() {
        var data = state.screenData;
        var filters;
        var regions;
        var body;
        if (!data || data.kind !== 'subscriptions') {
            state.screenData = { kind: 'subscriptions', filter: 'MOVIES', region: BuroTmdb.safeRegion(state.preferences.tmdbRegion),
                loading: true, shelves: [], selected: null };
            window.setTimeout(function () { loadSubscriptions('MOVIES'); }, 0);
            data = state.screenData;
        }
        if (data.selected) { shell(renderSubscriptionSelection(data), t('subscriptions'), true); return; }
        if (data.expanded) { shell(renderExpandedSubscription(data), t('subscriptions'), true); return; }
        filters = ['MOVIES', 'SERIES', 'THIS_WEEK', 'UPCOMING'].map(function (kind) {
            return '<button class="button ' + (data.filter === kind ? 'primary' : 'ghost') +
                ' focusable" data-action="subscription-filter" data-kind="' + kind + '">' + t('subscriptions' +
                { MOVIES: 'Movies', SERIES: 'Series', THIS_WEEK: 'ThisWeek', UPCOMING: 'Upcoming' }[kind]) + '</button>';
        }).join('');
        regions = BuroTmdb.supportedRegions().map(function (region) {
            return '<button class="button ' + (data.region === region ? 'primary' : 'ghost') +
                ' focusable" data-action="subscription-region" data-region="' + region + '">' + region + '</button>';
        }).join('');
        if (data.loading) {
            body = '<div class="subscription-loading"><span class="boot-indicator"></span><h2>' + t('subscriptionsLoading') + '</h2>' +
                (data.totalServices ? '<p>' + data.completedServices + '/' + data.totalServices + ' · ' +
                    (data.visibleServices || 0) + ' ' + t('subscriptionsServices') + '</p>' : '') + '</div>';
        } else if (data.error) {
            body = emptyState('!', t('subscriptionsUnavailable'), data.error === 'TMDB_KEY_REJECTED' ? t('tmdbKeyRejected') :
                t('tmdbUnavailable'), 'subscription-retry', t('retry'));
        } else if (!data.shelves.length) {
            body = emptyState('B', t('subscriptions'), t('subscriptionsEmpty'), '', '');
        } else {
            body = '<div class="subscription-shelves">' + data.shelves.map(function (shelf) {
                return '<section><div class="section-heading"><h2>' + subscriptionProviderLogo(shelf.providerLogoUrl) +
                    escapeHtml(shelf.providerName === 'coming-soon' ?
                    t('subscriptionsUpcomingShelf') : shelf.providerName) + '</h2><p>' + shelf.titles.length + '</p></div><div class="subscription-row">' +
                    shelf.titles.map(function (title) {
                        var poster = safeArtworkUrl(title.posterUrl);
                        return '<button class="subscription-poster focusable" data-action="subscription-title" data-key="' +
                            attr(subscriptionTitleKey(title)) + '">' + (poster ? '<img src="' + attr(poster) + '" alt="">' :
                            '<span>' + escapeHtml(title.title.charAt(0)) + '</span>') + subscriptionCardLogo(shelf.providerLogoUrl) +
                            '<strong>' + escapeHtml(title.title) + '</strong>' +
                            (title.year ? '<small>' + title.year + '</small>' : '') + '</button>';
                    }).join('') + (shelf.providerId ? '<button class="subscription-see-more focusable" data-action="subscription-expand" data-provider="' +
                        attr(shelf.providerId) + '"><strong>' + t('subscriptionsSeeMore') + '</strong><span>›</span></button>' : '') +
                    '</div></section>';
            }).join('') + '</div>';
        }
        shell('<div class="subscriptions-header"><h2>' + t('subscriptionsBrowse') + '</h2><div class="action-row">' + filters +
            '</div><div class="subscription-region"><strong>' + t('subscriptionsRegion') + '</strong><div class="action-row">' + regions +
            '</div></div></div>' + body, t('subscriptions'), true);
    }

    function findSubscriptionTitle(key) {
        var found = null;
        var data = state.screenData;
        if (data && data.expanded) {
            data.expanded.titles.some(function (title) {
                if (subscriptionTitleKey(title) === key) { found = title; return true; }
                return false;
            });
            if (found) { return found; }
        }
        (data && data.shelves || []).some(function (shelf) {
            return shelf.titles.some(function (title) {
                if (subscriptionTitleKey(title) === key) { found = title; return true; }
                return false;
            });
        });
        return found;
    }

    function findSubscriptionShelf(providerId) {
        var found = null;
        (state.screenData && state.screenData.shelves || []).some(function (shelf) {
            if (String(shelf.providerId || '') === String(providerId || '')) { found = shelf; return true; }
            return false;
        });
        return found;
    }

    function expandSubscriptionService(providerId) {
        var data = state.screenData;
        var shelf = findSubscriptionShelf(providerId);
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (!data || !shelf || !shelf.providerId || data.filter === 'UPCOMING' || !key) { return; }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        data.expanded = {
            providerId: shelf.providerId,
            providerName: shelf.providerName,
            providerLogoUrl: safeProviderLogoUrl(shelf.providerLogoUrl),
            titles: shelf.titles.slice(0, 100),
            loading: true,
            error: false
        };
        render();
        subscriptionRequest = BuroTmdb.loadServiceCatalogue(key, shelf.providerId, data.region, data.filter,
            state.preferences.language, function (titles) {
                subscriptionRequest = null;
                if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.expanded ||
                        String(state.screenData.expanded.providerId) !== String(shelf.providerId)) { return; }
                state.screenData.expanded.loading = false;
                state.screenData.expanded.error = false;
                if (titles && titles.length) { state.screenData.expanded.titles = titles.slice(0, 100); }
                render();
            }, function () {
                subscriptionRequest = null;
                if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.expanded ||
                        String(state.screenData.expanded.providerId) !== String(shelf.providerId)) { return; }
                state.screenData.expanded.loading = false;
                state.screenData.expanded.error = true;
                render();
            });
    }

    function closeExpandedSubscription() {
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); subscriptionRequest = null; }
        if (!state.screenData) { return; }
        state.screenData.expanded = null;
        render();
    }

    function matchSubscriptionLocal(title) {
        BuroStorage.fold('items', function (match, item) {
            if (match || !item || item.contentType !== (title.isSeries ? 'SERIES' : 'MOVIE')) { return match; }
            if (titleComparable(item.name) !== titleComparable(title.title)) { return null; }
            if (title.year && item.year && Number(title.year) !== Number(item.year)) { return null; }
            return item;
        }, null, function (match) {
            if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.selected ||
                    subscriptionTitleKey(state.screenData.selected) !== subscriptionTitleKey(title)) { return; }
            state.screenData.selection = state.screenData.selection || { offers: [], unknown: true };
            state.screenData.selection.localItem = match;
            render();
        }, function () {});
    }

    function selectSubscriptionTitle(title, selectedReturn) {
        var data = state.screenData;
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (!data || data.kind !== 'subscriptions') {
            data = { kind: 'subscriptions', filter: title.isSeries ? 'SERIES' : 'MOVIES',
                region: BuroTmdb.safeRegion(state.preferences.tmdbRegion), shelves: [], loading: false };
            state.screenData = data;
        }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        data.selected = title;
        data.selectedReturn = selectedReturn || null;
        data.selectionLoading = true;
        data.selection = { details: null, offers: [], unknown: false, localItem: null };
        render();
        subscriptionRequest = BuroTmdb.loadSubscriptionTitle(key, title, data.region, state.preferences.language, function (selection) {
            subscriptionRequest = null;
            if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.selected ||
                    subscriptionTitleKey(state.screenData.selected) !== subscriptionTitleKey(title)) { return; }
            state.screenData.selectionLoading = false;
            state.screenData.selection = selection;
            render();
            matchSubscriptionLocal(title);
        }, function () {
            subscriptionRequest = null;
            if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.selected) { return; }
            state.screenData.selectionLoading = false;
            state.screenData.selection = { details: null, offers: [], unknown: true, localItem: null };
            render();
            matchSubscriptionLocal(title);
        });
    }

    function backFromSubscriptionSelection() {
        var returned = state.screenData && state.screenData.selectedReturn;
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); subscriptionRequest = null; }
        if (returned && returned.screen === 'PERSON') {
            state.screen = 'PERSON'; state.screenData = returned.data; render(); return;
        }
        state.screenData.selected = null; state.screenData.selection = null; state.screenData.selectionLoading = false; render();
    }

    function openSubscriptionLocal(itemId) {
        var found = findItemAndSource(itemId);
        subscriptionReturnData = state.screenData;
        if (!found.item) { return; }
        if (found.item.contentType === 'SERIES') { openSeriesById(itemId, 'SUBSCRIPTIONS'); }
        else { openMovieDetails(itemId, 'SUBSCRIPTIONS'); }
    }

    function safeOfferUrl(value) {
        var anchor = document.createElement('a');
        var hosts = ['www.netflix.com', 'www.primevideo.com', 'www.disneyplus.com', 'tv.apple.com', 'play.google.com',
            'play.max.com', 'globoplay.globo.com', 'www.paramountplus.com', 'www.themoviedb.org', 'themoviedb.org'];
        try { anchor.href = String(value || ''); }
        catch (ignoredUrl) { return null; }
        return anchor.protocol === 'https:' && hosts.indexOf(anchor.hostname) >= 0 ? anchor.href : null;
    }

    function launchExternalUrl(url) {
        if (!url || !window.tizen || !tizen.application || !tizen.ApplicationControl) {
            showToast(t('externalOpenUnavailable'), true); return;
        }
        try {
            tizen.application.launchAppControl(new tizen.ApplicationControl(
                'http://tizen.org/appcontrol/operation/view', url, null, null), null,
                function () {}, function () { showToast(t('externalOpenUnavailable'), true); });
        } catch (ignoredLaunch) { showToast(t('externalOpenUnavailable'), true); }
    }

    function openExternalOffer(value) {
        launchExternalUrl(safeOfferUrl(value));
    }

    function openOfficialCriticsSite() {
        launchExternalUrl(OMDB_API_KEY_URL);
    }

    function openSubscriptionTrailer() {
        var data = state.screenData;
        var details = data && data.selection && data.selection.details;
        if (!details || !BuroTrailer.open(details.youtubeTrailerId, details.title || data.selected.title, {
            title: t('trailer'), loading: t('trailerLoading'), playing: t('trailerPlaying'),
            playingMuted: t('trailerPlayingMuted'), paused: t('trailerPaused'), ended: t('trailerEnded'),
            error: t('trailerUnavailable'), hint: t('trailerHint')
        })) { showToast(t('trailerUnavailable'), true); }
    }

    function openPersonCredit(element) {
        var personData = state.screenData;
        var title = {
            tmdbId: Number(element.getAttribute('data-id')), isSeries: element.getAttribute('data-series') === 'true',
            title: element.getAttribute('data-title'), year: Number(element.getAttribute('data-year')) || null,
            posterUrl: null
        };
        state.screen = 'SHELL'; state.section = 'SUBSCRIPTIONS';
        state.screenData = { kind: 'subscriptions', filter: title.isSeries ? 'SERIES' : 'MOVIES',
            region: BuroTmdb.safeRegion(state.preferences.tmdbRegion), shelves: [], loading: false };
        selectSubscriptionTitle(title, { screen: 'PERSON', data: personData });
    }

    function renderParentalForm() {
        var hasPin = Boolean(state.preferences.parentalPin);
        var current = hasPin ? '<div class="field"><label>' + t('currentPin') +
            '</label><input id="current-pin" class="focusable" type="password" inputmode="numeric" maxlength="4"></div>' : '';
        shell('<div class="form-panel"><h2>' + t('parentalPin') + '</h2><p class="form-message">' + t('parentalHint') +
            '</p>' + current + '<div class="field"><label>' + t('newPin') +
            '</label><input id="new-pin" class="focusable" type="password" inputmode="numeric" maxlength="4"></div>' +
            '<div id="pin-message" class="form-message"></div><div class="action-row"><button class="button primary focusable" data-action="parental-save">' +
            t('save') + '</button>' + (hasPin ? '<button class="button ghost focusable" data-action="parental-clear">' + t('removePin') + '</button>' : '') +
            '<button class="button ghost focusable" data-action="back">' + t('cancel') + '</button></div></div>', t('parentalPin'), true);
    }

    function renderPinUnlock() {
        var category = state.screenData.category;
        shell('<div class="form-panel"><h2>' + t('lockedCategory') + '</h2><p class="form-message">' +
            escapeHtml(category.name) + '</p><div class="field"><label>PIN</label><input id="unlock-pin" class="focusable" type="password" inputmode="numeric" maxlength="4"></div>' +
            '<div id="pin-message" class="form-message"></div><div class="action-row"><button class="button primary focusable" data-action="pin-unlock">' +
            t('unlock') + '</button><button class="button ghost focusable" data-action="back">' + t('cancel') +
            '</button></div></div>', t('lockedCategory'), true);
    }

    function categorySettingsTypeRank(contentType) {
        if (contentType === 'LIVE') { return 0; }
        if (contentType === 'MOVIE') { return 1; }
        if (contentType === 'SERIES') { return 2; }
        return 3;
    }

    /*
      O Android carrega todas as seções para a lista de ajustes. No Tizen o
      IndexedDB conserva categorias de várias fontes ao mesmo tempo, portanto
      esta tela deve limitar-se à fonte do perfil atual. Um perfil Kids nunca
      recebe nomes adultos nem mesmo nos ajustes, seguindo a política familiar
      usada pelo catálogo e pelo desktop.
    */
    function categoriesForSettings() {
        var sourceId = state.activeSource && state.activeSource.id;
        var kids = Boolean(state.activeProfile && state.activeProfile.isKids);
        if (!sourceId) { return []; }
        return state.categories.filter(function (category) {
            return category.sourceId === sourceId && !(kids && BuroGuard.looksAdult(category.name));
        }).sort(function (left, right) {
            var typeOrder = categorySettingsTypeRank(left.contentType) - categorySettingsTypeRank(right.contentType);
            var nameOrder;
            if (typeOrder) { return typeOrder; }
            nameOrder = String(left.name || '').localeCompare(String(right.name || ''));
            if (nameOrder) { return nameOrder; }
            return String(left.id || '').localeCompare(String(right.id || ''));
        });
    }

    function categorySettingsTypeLabel(contentType) {
        if (contentType === 'LIVE') { return t('live'); }
        if (contentType === 'MOVIE') { return t('movies'); }
        if (contentType === 'SERIES') { return t('series'); }
        return contentType || t('unavailable');
    }

    function renderCategorySettings() {
        var data = state.screenData || {};
        var categories = categoriesForSettings();
        var pageCount = Math.max(1, Math.ceil(categories.length / CATEGORY_SETTINGS_PAGE_SIZE));
        var page = BuroDomain.clamp(Number(data.page) || 0, 0, pageCount - 1);
        var start = page * CATEGORY_SETTINGS_PAGE_SIZE;
        var visible = categories.slice(start, start + CATEGORY_SETTINGS_PAGE_SIZE);
        var rows;
        state.screenData = data;
        data.kind = 'category-settings';
        data.page = page;
        rows = visible.map(function (category) {
            var hidden = state.preferences.hiddenCategoryIds.indexOf(category.id) >= 0;
            var locked = state.preferences.lockedCategoryIds.indexOf(category.id) >= 0;
            return '<div class="guard-row"><div><strong>' + escapeHtml(category.name) + '</strong><small>' +
                escapeHtml(categorySettingsTypeLabel(category.contentType)) + '</small></div><div class="action-row"><button class="button ghost focusable" data-action="category-hidden" data-id="' +
                attr(category.id) + '">' + (hidden ? t('hidden') : t('visible')) + '</button><button class="button ghost focusable" data-action="category-locked" data-id="' +
                attr(category.id) + '" ' + (state.preferences.parentalPin ? '' : 'disabled') + '>' + (locked ? t('locked') : t('open')) + '</button></div></div>';
        }).join('');
        shell('<div class="guard-list">' + (rows || emptyState('B', t('categoryControl'), t('unavailable'), '', '')) +
            paginationControls('category-settings-page', page, pageCount, start, start + visible.length,
                categories.length, '', 'guard-pagination') + '</div>', t('categoryControl'), true);
    }

    function renderShell() {
        if (state.section === 'HOME') { renderHome(); }
        else if (state.section === 'LIVE') { renderCatalog('LIVE'); }
        else if (state.section === 'MOVIES') { renderCatalog('MOVIE'); }
        else if (state.section === 'SERIES') { renderCatalog('SERIES'); }
        else if (state.section === 'DISCOVER') { renderDiscover(); }
        else if (state.section === 'SEARCH') { renderSearch(); }
        else if (state.section === 'MY_BURO') { renderMyBuro(); }
        else if (state.section === 'CONTINUE_WATCHING') { renderProgressSection(false); }
        else if (state.section === 'HISTORY') { renderProgressSection(true); }
        else if (state.section === 'REMINDERS') { renderReminders(); }
        else if (state.section === 'DOWNLOADS') { renderDownloads(); }
        else if (state.section === 'SUBSCRIPTIONS') { renderSubscriptions(); }
        else if (state.section === 'PROFILES') { renderProfiles(); }
        else if (state.section === 'SOURCES') { renderSources(); }
        else if (state.section === 'SETTINGS') { renderSettings(); }
        else { shell(emptyState('B', t('unavailable'), t('previewNotice'), '', ''), sectionTitle(), false); }
    }

    function reportRuntimeReady() {
        if (runtimeReadyReported || state.screen !== 'SHELL' || !root.querySelector('.shell')) { return; }
        runtimeReadyReported = true;
        root.setAttribute('data-runtime-ready', APP_VERSION_FALLBACK);
        if (window.console && typeof window.console.info === 'function') {
            window.console.info('IPTVBURO_RUNTIME_READY screen=SHELL version=' + APP_VERSION_FALLBACK);
        }
    }

    function sectionTitle() {
        var result = state.section;
        navigationEntries().forEach(function (entry) { if (entry.section === state.section) { result = t(entry.label); } });
        return result;
    }

    function render() {
        var previousAction = focusables[focusIndex] && focusables[focusIndex].getAttribute('data-action');
        var previousFocusKey = focusIdentity(focusables[focusIndex]);
        if (homeHeroTimer) { window.clearTimeout(homeHeroTimer); homeHeroTimer = null; }
        if (homeEnrichmentTimer) { window.clearTimeout(homeEnrichmentTimer); homeEnrichmentTimer = null; }
        if (state.screen === 'LANGUAGE') { renderLanguageSelection(); }
        else if (state.screen === 'BOOT') { renderBoot(); }
        else if (state.screen === 'LEGAL') { renderLegal(); }
        else if (state.screen === 'PROFILES') { renderProfiles(); }
        else if (state.screen === 'PROFILE_FORM') { renderProfileForm(); }
        else if (state.screen === 'PROFILE_PHOTO_PICKER') { renderProfilePhotoPicker(); }
        else if (state.screen === 'RESUME_PROMPT') { renderResumePrompt(); }
        else if (state.screen === 'BULK_DOWNLOAD_CONFIRM') { renderBulkDownloadConfirm(); }
        else if (state.screen === 'SOURCE_CHOICE') { renderSourceChoice(); }
        else if (state.screen === 'SOURCE_USB_M3U') { renderUsbM3uPicker(); }
        else if (state.screen === 'SOURCE_FORM') { renderSourceForm(state.screenData.type); }
        else if (state.screen === 'SOURCE_MANAGE') { renderSourceManage(); }
        else if (state.screen === 'PARENTAL_FORM') { renderParentalForm(); }
        else if (state.screen === 'PIN_UNLOCK') { renderPinUnlock(); }
        else if (state.screen === 'CATEGORY_SETTINGS') { renderCategorySettings(); }
        else if (state.screen === 'LICENCE') { renderLicenceActivation(); }
        else if (state.screen === 'TMDB_SETTINGS') { renderTmdbSettings(); }
        else if (state.screen === 'CRITICS_SETTINGS') { renderCriticsSettings(); }
        else if (state.screen === 'CRITICS_GUIDE') { renderCriticsGuide(); }
        else if (state.screen === 'STORAGE_SETTINGS') { renderStorageSettings(); }
        else if (state.screen === 'NOTIFICATIONS') { renderNotifications(); }
        else if (state.screen === 'TMDB_GUIDE') { renderTmdbGuide(); }
        else if (state.screen === 'PERSON') { renderPerson(); }
        else if (state.screen === 'SHARE') { renderShare(); }
        else if (state.screen === 'ERROR') { root.innerHTML = emptyState('!', t('error'), state.screenData, 'retry', t('retry')); }
        else { renderShell(); }
        bindArtworkErrors();
        applyAccessibilitySemantics();
        refreshFocus(previousAction, previousFocusKey);
        bindClicks();
        bindSearchInput();
        bindDownloadSearchInput();
        bindStalkerForm();
        reportRuntimeReady();
        if (pendingSharedTitle && !sharedTitleNoticeVisible) { resolvePendingSharedTitle(); }
    }

    function focusIdentity(element) {
        var names = ['data-action', 'data-id', 'data-section', 'data-season', 'data-property', 'data-value',
            'data-language', 'data-type', 'data-avatar', 'data-name', 'data-scope', 'data-kind',
            'data-region', 'data-key', 'data-series', 'data-title', 'data-year'];
        if (!element) { return ''; }
        return element.tagName + '#' + (element.id || '') + '#' + names.map(function (name) {
            return element.getAttribute(name) || '';
        }).join('#');
    }

    function refreshFocus(preferredAction, preferredFocusKey) {
        var preferred = -1;
        focusables = Array.prototype.slice.call(root.querySelectorAll('.focusable:not([disabled])'));
        focusables.forEach(function (element, index) {
            element.classList.remove('focused');
            if (preferred < 0 && preferredFocusKey && focusIdentity(element) === preferredFocusKey) { preferred = index; }
        });
        if (preferred < 0 && preferredAction) {
            focusables.some(function (element, index) {
                if (element.getAttribute('data-action') === preferredAction) { preferred = index; return true; }
                return false;
            });
        }
        if (preferred >= 0) { focusIndex = preferred; }
        focusIndex = BuroDomain.clamp(focusIndex, 0, Math.max(0, focusables.length - 1));
        applyFocus();
    }

    function applyFocus() {
        focusables.forEach(function (element, index) {
            element.classList.toggle('focused', index === focusIndex);
            if (index === focusIndex) {
                element.setAttribute('tabindex', '0');
                if (element.scrollIntoView) { element.scrollIntoView({ block: 'nearest', inline: 'nearest' }); }
                if (element.focus) {
                    try { element.focus({ preventScroll: true }); }
                    catch (ignoredFocusOptions) { try { element.focus(); } catch (ignoredFocus) {} }
                }
            } else { element.setAttribute('tabindex', '-1'); }
        });
    }

    function moveFocus(delta) {
        var next = focusIndex + delta;
        if (next < 0 || next >= focusables.length) { return; }
        focusIndex = next;
        applyFocus();
    }

    function moveDirectional(keyCode) {
        var K = BuroKeys.CODES;
        var current = focusables[focusIndex];
        var currentRect;
        var currentX;
        var currentY;
        var best = -1;
        var bestScore = Number.MAX_VALUE;
        var hasGeometry = false;
        if (!current || !current.getBoundingClientRect) { moveFocus(keyCode === K.LEFT || keyCode === K.UP ? -1 : 1); return; }
        currentRect = current.getBoundingClientRect();
        currentX = currentRect.left + currentRect.width / 2;
        currentY = currentRect.top + currentRect.height / 2;
        hasGeometry = currentRect.width > 0 || currentRect.height > 0;
        focusables.forEach(function (candidate, index) {
            var rect;
            var x;
            var y;
            var primary;
            var secondary;
            var valid;
            var score;
            if (index === focusIndex) { return; }
            rect = candidate.getBoundingClientRect();
            x = rect.left + rect.width / 2;
            y = rect.top + rect.height / 2;
            if (keyCode === K.LEFT || keyCode === K.RIGHT) {
                primary = Math.abs(x - currentX); secondary = Math.abs(y - currentY);
                valid = keyCode === K.LEFT ? x < currentX - 2 : x > currentX + 2;
            } else {
                primary = Math.abs(y - currentY); secondary = Math.abs(x - currentX);
                valid = keyCode === K.UP ? y < currentY - 2 : y > currentY + 2;
            }
            score = primary + secondary * 2.4;
            if (valid && score < bestScore) { bestScore = score; best = index; }
        });
        if (!hasGeometry) { moveFocus(keyCode === K.LEFT || keyCode === K.UP ? -1 : 1); return; }
        if (best >= 0) { focusIndex = best; applyFocus(); }
    }

    function bindClicks() {
        focusables.forEach(function (element) {
            element.addEventListener('click', function () { activate(element); });
        });
    }

    function applyAccessibilitySemantics() {
        var main = root && root.querySelector ? root.querySelector('main') : null;
        var loading;
        var pressedActions = {
            'profile-avatar': true, 'profile-source': true, 'library-filter': true,
            'download-filter': true, 'download-compact': true, favorite: true,
            reminder: true, 'subtitle-size-select': true, 'subtitle-colour-select': true,
            language: true, 'subscription-filter': true, 'subscription-region': true
        };
        if (!root || !root.querySelectorAll) { return; }

        Array.prototype.slice.call(root.querySelectorAll('.field label')).forEach(function (label) {
            var control = label.parentNode && label.parentNode.querySelector ?
                label.parentNode.querySelector('input, select, textarea') : null;
            if (control && control.id) { label.setAttribute('for', control.id); }
        });
        Array.prototype.slice.call(root.querySelectorAll('.focusable')).forEach(function (element) {
            var tag = String(element.tagName || '').toLowerCase();
            if (tag !== 'button' && tag !== 'input' && tag !== 'select' && tag !== 'textarea' &&
                    tag !== 'a' && !element.getAttribute('role')) { element.setAttribute('role', 'button'); }
        });
        Array.prototype.slice.call(root.querySelectorAll('[data-action="kids-toggle"]')).forEach(function (element) {
            element.setAttribute('role', 'checkbox');
            element.setAttribute('aria-checked', element.classList.contains('checked') ? 'true' : 'false');
        });
        Array.prototype.slice.call(root.querySelectorAll('[data-action="toggle-setting"], [data-action="toggle-adult-lock"]')).forEach(function (element) {
            element.setAttribute('role', 'switch');
            element.setAttribute('aria-checked', element.classList.contains('on') ? 'true' : 'false');
        });
        Array.prototype.slice.call(root.querySelectorAll('[data-action]')).forEach(function (element) {
            var action = element.getAttribute('data-action');
            var pressed;
            if (!pressedActions[action]) { return; }
            pressed = element.classList.contains('selected') || element.classList.contains('primary');
            if (action === 'favorite') { pressed = isFavorite(element.getAttribute('data-id')); }
            if (action === 'reminder') {
                pressed = hasReminder(findItemAndSource(element.getAttribute('data-id')).item);
            }
            element.setAttribute('aria-pressed', pressed ? 'true' : 'false');
        });
        Array.prototype.slice.call(root.querySelectorAll('[data-action="category-hidden"]')).forEach(function (element) {
            element.setAttribute('aria-pressed', state.preferences.hiddenCategoryIds.indexOf(element.getAttribute('data-id')) >= 0 ? 'true' : 'false');
        });
        Array.prototype.slice.call(root.querySelectorAll('[data-action="category-locked"]')).forEach(function (element) {
            element.setAttribute('aria-pressed', state.preferences.lockedCategoryIds.indexOf(element.getAttribute('data-id')) >= 0 ? 'true' : 'false');
        });
        Array.prototype.slice.call(root.querySelectorAll('.catalogue-loading, .search-loading, .subscription-loading')).forEach(function (element) {
            element.setAttribute('role', 'status');
            element.setAttribute('aria-live', 'polite');
            element.setAttribute('aria-atomic', 'true');
        });
        Array.prototype.slice.call(root.querySelectorAll('.catalogue-error, .form-message.error')).forEach(function (element) {
            element.setAttribute('role', 'alert');
        });
        Array.prototype.slice.call(root.querySelectorAll('.form-message')).forEach(function (element) {
            element.setAttribute('aria-live', element.classList.contains('error') ? 'assertive' : 'polite');
            element.setAttribute('aria-atomic', 'true');
        });
        Array.prototype.slice.call(root.querySelectorAll('.empty-symbol, .catalogue-skeleton-row, .empty-icon')).forEach(function (element) {
            element.setAttribute('aria-hidden', 'true');
        });
        loading = Boolean(root.querySelector('.catalogue-loading, .search-loading, .subscription-loading'));
        if (main) { main.setAttribute('aria-busy', loading ? 'true' : 'false'); }
    }

    function clearSearchDebounce() {
        if (!searchDebounceTimer) { return; }
        window.clearTimeout(searchDebounceTimer);
        searchDebounceTimer = null;
    }

    /*
      O Android publica a consulta 300 ms depois da última tecla. A TV segue o
      mesmo contrato para que teclado virtual e físico não exijam uma segunda
      ação e para não varrer catálogos grandes a cada caractere.
    */
    function bindSearchInput() {
        var input = root && root.querySelector ? root.querySelector('#search-query') : null;
        if (!input || state.screen !== 'SHELL' || state.section !== 'SEARCH') { return; }
        input.addEventListener('input', function () {
            var query = String(input.value || '').substring(0, 80);
            clearSearchDebounce();
            searchDebounceTimer = window.setTimeout(function () {
                searchDebounceTimer = null;
                if (state.screen !== 'SHELL' || state.section !== 'SEARCH' ||
                        !root || !root.querySelector || root.querySelector('#search-query') !== input) { return; }
                runSearchPage(BuroDomain.trim(query), 0);
            }, SEARCH_DEBOUNCE_MILLIS);
        });
    }

    /*
      O formulário do portal responde enquanto se digita: o MAC é a credencial
      da assinatura, e descobrir que ele estava errado só depois de uma ida à
      rede é uma espera cara. A confirmação mostra o MAC já normalizado, para
      que o usuário veja que os separadores que ele usou foram aceitos.

      O aviso de HTTP aparece pelo mesmo motivo — antes de conectar, não depois.
    */
    function bindStalkerForm() {
        var mac = root && root.querySelector ? root.querySelector('#source-mac') : null;
        var portal = root && root.querySelector ? root.querySelector('#source-portal') : null;
        var hint = root && root.querySelector ? root.querySelector('#source-mac-hint') : null;
        if (state.screen !== 'SOURCE_FORM' || !state.screenData || state.screenData.type !== 'STALKER') { return; }
        if (mac && hint) {
            mac.addEventListener('input', function () {
                var raw = BuroDomain.trim(mac.value);
                var normalized = raw ? BuroStalker.normalizeMac(raw) : null;
                if (!raw) { hint.textContent = ''; hint.className = 'field-hint'; return; }
                if (normalized) {
                    hint.textContent = t('stalkerMacRecognised').replace('{mac}', normalized);
                    hint.className = 'field-hint ok';
                } else {
                    hint.textContent = t('stalkerMacInvalid');
                    hint.className = 'field-hint error';
                }
            });
        }
        if (portal) {
            portal.addEventListener('input', function () {
                var warning = root && root.querySelector ? root.querySelector('#source-http-warning') : null;
                var value = BuroDomain.trim(portal.value);
                if (!warning) { return; }
                warning.hidden = !/^http:\/\//i.test(value);
            });
        }
    }

    function clearDownloadSearchDebounce() {
        if (!downloadSearchTimer) { return; }
        window.clearTimeout(downloadSearchTimer);
        downloadSearchTimer = null;
    }

    /* A fila tem no máximo 200 entradas persistidas. Filtrar localmente é
       barato, mas um pequeno debounce evita reconstruir o DOM a cada evento
       intermediário do teclado virtual Samsung. A consulta nunca sai da TV. */
    function bindDownloadSearchInput() {
        var input = root && root.querySelector ? root.querySelector('#download-query') : null;
        if (!input || state.screen !== 'SHELL' || state.section !== 'DOWNLOADS') { return; }
        input.addEventListener('input', function () {
            downloadQuery = String(input.value || '').substring(0, 80);
            clearDownloadSearchDebounce();
            downloadSearchTimer = window.setTimeout(function () {
                downloadSearchTimer = null;
                if (state.screen !== 'SHELL' || state.section !== 'DOWNLOADS' ||
                        !root || !root.querySelector || root.querySelector('#download-query') !== input) { return; }
                downloadPage = 0;
                render();
            }, DOWNLOAD_SEARCH_DEBOUNCE_MILLIS);
        });
    }

    function focusMatching(selector) {
        var element = root.querySelector(selector);
        var index = focusables.indexOf(element);
        if (index < 0) { return; }
        focusIndex = index;
        applyFocus();
    }

    /* Restaura foco sem montar um seletor CSS com um id vindo da fonte. */
    function focusActionId(action, id) {
        focusables.some(function (element, index) {
            if (element.getAttribute('data-action') !== action || element.getAttribute('data-id') !== id) {
                return false;
            }
            focusIndex = index;
            applyFocus();
            return true;
        });
    }

    function bindArtworkErrors() {
        Array.prototype.slice.call(root.querySelectorAll('.media-art img, .hero-art img, .detail-art img, .discover-art img')).forEach(function (image) {
            image.addEventListener('error', function () {
                var fallback = safeArtworkUrl(image.getAttribute('data-artwork-fallback'));
                if (fallback) {
                    image.removeAttribute('data-artwork-fallback');
                    image.src = fallback;
                    return;
                }
                if (image.parentNode) { image.parentNode.style.display = 'none'; }
            });
        });
        Array.prototype.slice.call(root.querySelectorAll('.subscription-poster img, .subscription-title-head img, ' +
                '.subscription-backdrop img, .subscription-cast img, .person-page img, .profile-photo-choice img, ' +
                '.avatar img, .ribbon-avatar img')).forEach(function (image) {
            image.addEventListener('error', function () { image.style.display = 'none'; });
        });
    }

    function pushScreen(screen, data) {
        state.backStack.push({ screen: state.screen, section: state.section, data: state.screenData });
        state.screen = screen;
        state.screenData = data || null;
        focusIndex = 0;
        render();
    }

    function goBack() {
        var previous;
        var focused;
        var ribbonTarget;
        var reminderReturnId;
        if (state.screen === 'PERSON' && tmdbPersonRequest && tmdbPersonRequest.abort) {
            tmdbPersonRequest.abort(); tmdbPersonRequest = null;
        }
        if (state.screen === 'SHELL' && state.section === 'SUBSCRIPTIONS' && state.screenData) {
            if (state.screenData.selected) { backFromSubscriptionSelection(); return; }
            if (state.screenData.expanded) { closeExpandedSubscription(); return; }
        }
        if (state.screen === 'SHELL' && state.screenData &&
                (state.screenData.kind === 'category' || state.screenData.kind === 'series' ||
                    state.screenData.kind === 'movie' || state.screenData.kind === 'live' ||
                    state.screenData.kind === 'catalogue-loading' || state.screenData.kind === 'catalogue-error' ||
                    state.screenData.kind === 'demo-story')) {
            catalogueRequestId += 1;
            if (state.screenData.originSection === 'DISCOVER' && discoverReturnData) {
                state.section = 'DISCOVER';
                state.screenData = discoverReturnData;
                discoverReturnData = null;
                focusIndex = 0;
                render();
                focusMatching('[data-action="discover-details"]');
                return;
            }
            if (personReturnData) {
                state.screen = 'PERSON';
                state.screenData = personReturnData;
                personReturnData = null;
                focusIndex = 0;
                render();
                return;
            }
            if (subscriptionReturnData) {
                state.section = 'SUBSCRIPTIONS';
                state.screenData = subscriptionReturnData;
                subscriptionReturnData = null;
                focusIndex = 0;
                render();
                return;
            }
            if (state.screenData.originSection === 'REMINDERS' && state.screenData.parent) {
                reminderReturnId = state.screenData.parent.id;
            }
            if (state.screenData.originSection) { state.section = state.screenData.originSection; }
            state.screenData = null;
            focusIndex = 0;
            render();
            if (reminderReturnId) { focusActionId('reminder-open', reminderReturnId); }
            return;
        }
        if (state.screen === 'SHELL') {
            focused = focusables[focusIndex];
            if (focused && !focused.closest('.buro-ribbon')) {
                focusables.some(function (candidate, index) {
                    if (candidate.getAttribute('data-action') === 'section' &&
                            candidate.getAttribute('data-section') === state.section) {
                        ribbonTarget = index;
                        return true;
                    }
                    return false;
                });
                if (ribbonTarget !== undefined) {
                    focusIndex = ribbonTarget;
                    applyFocus();
                    return;
                }
            }
        }
        previous = state.backStack.pop();
        if (previous) {
            state.screen = previous.screen;
            state.section = previous.section;
            state.screenData = previous.data;
            focusIndex = 0;
            render();
            return;
        }
        if (state.screen !== 'SHELL' && state.preferences.acceptedLegal && state.profiles.length) {
            state.screen = 'SHELL'; state.section = 'HOME'; state.screenData = null; render(); return;
        }
        if (window.tizen && tizen.application) { tizen.application.getCurrentApplication().exit(); }
    }

    function acceptLegal() {
        if (state.screen !== 'LEGAL') { return; }
        state.preferences.acceptedLegal = true;
        savePreferences();
        state.screen = state.profiles.length ? 'SHELL' : 'PROFILES';
        state.screenData = null;
        render();
        if (state.screen === 'SHELL') { startActiveSourceHydration(false); }
    }

    function profileDraft() {
        var draft = state.screenData || {};
        var input = document.getElementById('profile-name');
        if (input) { draft.profileName = input.value; }
        if (AVATAR_KEYS.indexOf(draft.avatarKey) < 0) { draft.avatarKey = 'gold'; }
        state.screenData = draft;
        return draft;
    }

    function loadProfilePhotoImages() {
        state.screenData = { loading: true, images: [], error: null };
        render();
        BuroUsb.listImages(function (images) {
            if (state.screen !== 'PROFILE_PHOTO_PICKER') { return; }
            state.screenData = { loading: false, images: images || [], error: null };
            render();
        }, function () {
            if (state.screen !== 'PROFILE_PHOTO_PICKER') { return; }
            state.screenData = { loading: false, images: [], error: true };
            render();
        });
    }

    function openProfilePhotoPicker() {
        profileDraft();
        pushScreen('PROFILE_PHOTO_PICKER', { loading: true, images: [], error: null });
        loadProfilePhotoImages();
    }

    function restoreProfileDraftWithPhoto(photo) {
        var previous = state.backStack.pop();
        if (!previous || previous.screen !== 'PROFILE_FORM') {
            state.screen = 'SHELL'; state.section = 'PROFILES'; state.screenData = null; render();
            return;
        }
        previous.data = previous.data || {};
        previous.data.photoDataUrl = photo;
        previous.data.confirmDelete = false;
        state.screen = previous.screen;
        state.section = previous.section;
        state.screenData = previous.data;
        focusIndex = 0;
        render();
    }

    function selectProfilePhoto(key) {
        if (state.screen !== 'PROFILE_PHOTO_PICKER') { return; }
        state.screenData = { loading: true, images: [], error: null, reading: true };
        render();
        BuroUsb.readImage(key, function (source) {
            BuroProfilePhoto.resize(source, function (photo) {
                restoreProfileDraftWithPhoto(photo);
            }, function () {
                if (state.screen !== 'PROFILE_PHOTO_PICKER') { return; }
                state.screenData = { loading: false, images: [], error: true };
                render();
            });
        }, function () {
            if (state.screen !== 'PROFILE_PHOTO_PICKER') { return; }
            state.screenData = { loading: false, images: [], error: true };
            render();
        });
    }

    function saveProfile() {
        var input = document.getElementById('profile-name');
        var draft = profileDraft();
        var existing = null;
        var profile;
        if (!draft.editingId && state.profiles.length >= MAX_PROFILES) {
            showToast(t('profileLimit'), true);
            return;
        }
        state.profiles.forEach(function (row) { if (row.id === draft.editingId) { existing = row; } });
        try {
            profile = BuroDomain.createProfile({
                id: existing && existing.id,
                createdAt: existing && existing.createdAt,
                name: input.value,
                avatarKey: draft.avatarKey,
                isKids: Boolean(draft.kids),
                sourceId: draft.sourceId || null
            });
            profile.photoDataUrl = safeProfilePhoto(draft.photoDataUrl);
        }
        catch (error) { showToast(t('profileName'), true); return; }
        BuroStorage.put('profiles', profile, function () {
            if (existing) {
                state.profiles = state.profiles.map(function (row) { return row.id === profile.id ? profile : row; });
                if (state.preferences.activeProfileId === profile.id) { state.activeProfile = profile; }
            } else {
                state.profiles.push(profile);
                state.activeProfile = profile;
                state.preferences.activeProfileId = profile.id;
            }
            refreshActiveReferences();
            savePreferences();
            state.backStack = [];
            state.screen = 'SHELL'; state.section = existing ? 'PROFILES' : 'HOME'; state.screenData = null;
            render();
            retryPendingSharedTitle();
            startActiveSourceHydration(false);
            showToast(t('profileSaved'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function deleteProfile() {
        var draft = profileDraft();
        var profileId = draft.editingId;
        var favorites;
        var progress;
        var reminders;
        if (!profileId || state.profiles.length <= 1) { showToast(t('lastProfileCannotDelete'), true); return; }
        if (!draft.confirmDelete) { draft.confirmDelete = true; render(); return; }
        favorites = state.favorites.filter(function (row) { return row.profileId === profileId; });
        progress = state.progress.filter(function (row) { return row.profileId === profileId; });
        reminders = state.reminders.filter(function (row) { return row.profileId === profileId; });
        BuroStorage.remove('profiles', profileId, function () {
            BuroTmdb.remove('profile', profileId);
            removeMany('favorites', favorites, function () {
                removeMany('progress', progress, function () {
                  /* Os lembretes saem junto: são marcas daquele perfil, e deixá-los
                     no banco faria o próximo perfil criado herdá-las. */
                  removeMany('reminders', reminders, function () {
                    state.profiles = state.profiles.filter(function (row) { return row.id !== profileId; });
                    state.favorites = state.favorites.filter(function (row) { return row.profileId !== profileId; });
                    state.progress = state.progress.filter(function (row) { return row.profileId !== profileId; });
                    state.reminders = state.reminders.filter(function (row) { return row.profileId !== profileId; });
                    if (state.preferences.activeProfileId === profileId) {
                        state.preferences.activeProfileId = state.profiles[0].id;
                    }
                    refreshActiveReferences();
                    savePreferences();
                    state.backStack = [];
                    state.screen = 'SHELL'; state.section = 'PROFILES'; state.screenData = null;
                    render();
                    showToast(t('profileDeleted'), false);
                  }, function (error) { showToast(friendlyError(error), true); });
                }, function (error) { showToast(friendlyError(error), true); });
            }, function (error) { showToast(friendlyError(error), true); });
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function assignSourceToProfile(source) {
        if (!state.activeProfile) { return; }
        state.activeProfile.sourceId = source.id;
        state.activeSource = source;
        BuroStorage.put('profiles', state.activeProfile, function () {}, function () {});
    }

    function sourceManageDraft() {
        var draft = state.screenData || {};
        var input = document.getElementById('source-manage-name');
        if (input) { draft.sourceName = input.value; }
        state.screenData = draft;
        return draft;
    }

    function renameSource() {
        var draft = sourceManageDraft();
        var existing = state.sources.filter(function (row) { return row.id === draft.sourceId; })[0];
        var updated;
        if (!existing) { showToast(t('sourceError'), true); return; }
        try {
            updated = BuroDomain.createSourceMetadata({
                id: existing.id,
                name: draft.sourceName,
                type: existing.type,
                channelCount: existing.channelCount,
                createdAt: existing.createdAt,
                updatedAt: Date.now()
            });
        } catch (error) { showToast(t('sourceName'), true); return; }
        BuroStorage.put('sources', updated, function () {
            state.sources = state.sources.map(function (row) { return row.id === updated.id ? updated : row; });
            refreshActiveReferences();
            state.backStack = [];
            state.screen = 'SHELL'; state.section = 'SOURCES'; state.screenData = null;
            render(); showToast(t('sourceUpdated'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function deleteSource() {
        var draft = sourceManageDraft();
        var sourceId = draft.sourceId;
        var itemIds = {};
        if (!draft.confirmDelete) { draft.confirmDelete = true; render(); return; }
        state.items.forEach(function (item) {
            if (item.sourceId === sourceId) {
                itemIds[item.id] = true;
                forgetUrl(artworkMemory, artworkOrder, item.id);
                forgetUrl(detailBackdropMemory, detailBackdropOrder, item.id);
                delete seriesDetailsMemory[item.id];
            }
        });
        Object.keys(artworkRequests).forEach(function (key) {
            if (key.indexOf(sourceId + ':') === 0) { delete artworkRequests[key]; }
        });
        BuroHeroEnrichment.clearSource(sourceId);
        BuroCatalogueSync.clearSource(sourceId);
        /* O token e os `cmd` em memória não sobrevivem à fonte que os originou. */
        delete stalkerSessions[sourceId];
        BuroStalker.clearSession();
        try { BuroStorage.secureRemove(sourceId); }
        catch (error) { showToast(friendlyError(error), true); return; }
        BuroStorage.deleteSourceData(sourceId, function () {
            state.sources = state.sources.filter(function (row) { return row.id !== sourceId; });
            state.categories = state.categories.filter(function (row) { return row.sourceId !== sourceId; });
            state.items = state.items.filter(function (row) { return row.sourceId !== sourceId; });
            state.favorites = state.favorites.filter(function (row) { return !itemIds[row.itemId]; });
            state.progress = state.progress.filter(function (row) { return !itemIds[row.itemId]; });
            state.profiles.forEach(function (profile) { if (profile.sourceId === sourceId) { profile.sourceId = null; } });
            delete playlistMemory[sourceId];
            refreshActiveReferences();
            savePreferences();
            state.backStack = [];
            state.screen = 'SHELL'; state.section = 'SOURCES'; state.screenData = null;
            render(); showToast(t('sourceDeleted'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function connectSource(type) {
        var name = BuroDomain.trim(document.getElementById('source-name').value);
        var source;
        if (!name || state.busy) { showToast(t('sourceName'), true); return; }
        try { source = BuroDomain.createSourceMetadata({ name: name, type: type }); }
        catch (error) { showToast(friendlyError(error), true); return; }
        state.busy = true;
        setFormMessage(t('connecting'), false);
        if (type === 'XTREAM') { connectXtream(source); }
        else if (type === 'STALKER') { connectStalker(source); }
        else { connectM3u(source); }
    }

    /*
      Um portal exige handshake antes de qualquer catálogo, e o estado da conta
      logo depois: uma assinatura bloqueada responde ao handshake normalmente e
      só se revela em get_main_info. Descobrir isso agora evita importar um
      catálogo inteiro que não vai reproduzir nada.
    */
    /*
      "Não alcancei o servidor" é conselho pobre para um portal: o endereço tem
      um caminho que o provedor dita (/c/, /stalker_portal/c/) e errar esse
      caminho parece exatamente com uma queda de rede. A mensagem do portal
      manda conferir as duas coisas.
    */
    function stalkerFailed(error) {
        var code = error && (error.code || error.message);
        /* O adapter normaliza qualquer falha de transporte para NETWORK; os
           outros códigos podem chegar quando a falha vem antes dele. */
        if (code === 'NETWORK' || code === 'NETWORK_ERROR' ||
                code === 'NETWORK_TIMEOUT' || code === 'HTTP_ERROR') {
            state.busy = false;
            setFormMessage(t('stalkerErrorNetwork'), true);
            return;
        }
        sourceFailed(error);
    }

    function connectStalker(source) {
        var secret;
        try {
            secret = BuroStalker.credentials({
                portalUrl: document.getElementById('source-portal').value,
                macAddress: document.getElementById('source-mac').value,
                username: document.getElementById('source-username').value,
                password: document.getElementById('source-password').value
            });
        } catch (error) { stalkerFailed(error); return; }
        setFormMessage(t('stalkerConnecting'), false);
        BuroStalker.handshake(secret, function (session) {
            BuroStalker.account(secret, session, function (account) {
                if (account && account.blocked) { stalkerFailed({ code: 'PORTAL_BLOCKED' }); return; }
                BuroStorage.secureSave(source.id, secret, function () {
                    stalkerSessions[source.id] = session;
                    loadStalkerCategorySets(secret, session, source, ['LIVE', 'MOVIE', 'SERIES'], []);
                }, stalkerFailed);
            }, stalkerFailed);
        }, stalkerFailed);
    }

    /* O adapter já devolve categoria com sourceId e id no mesmo formato do
       resto do app, então aqui só acumulamos os tres tipos de conteudo. */
    function loadStalkerCategorySets(secret, session, source, remaining, collected) {
        fetchStalkerCategorySets(secret, session, source, remaining, collected, function (categories) {
            persistSource(source, categories, []);
        }, stalkerFailed);
    }

    /*
      Devolve uma sessão válida para a fonte, refazendo o handshake quando o
      token de dez minutos já venceu. O segredo nunca sai daqui.
    */
    function withStalkerSession(source, secret, success, failure) {
        var existing = stalkerSessions[source.id];
        if (BuroStalker.sessionValid(existing)) { success(existing); return; }
        delete stalkerSessions[source.id];
        BuroStalker.handshake(secret, function (session) {
            stalkerSessions[source.id] = session;
            success(session);
        }, failure);
    }

    function connectXtream(source) {
        var secret;
        try {
            secret = BuroXtream.credentials({
                server: document.getElementById('source-server').value,
                username: document.getElementById('source-username').value,
                password: document.getElementById('source-password').value
            });
        } catch (error) { sourceFailed(error); return; }
        BuroXtream.authenticate(secret, function () {
            BuroStorage.secureSave(source.id, secret, function () {
                loadXtreamCategorySets(secret, source, ['LIVE', 'MOVIE', 'SERIES'], []);
            }, sourceFailed);
        }, sourceFailed);
    }

    function loadXtreamCategorySets(secret, source, remaining, collected) {
        var contentType;
        if (!remaining.length) { persistSource(source, collected, []); return; }
        contentType = remaining.shift();
        BuroXtream.loadCategories(secret, contentType, function (categories) {
            categories.forEach(function (category) {
                category.sourceId = source.id;
                category.id = BuroDomain.id('category', source.id + ':' + contentType + ':' + category.providerCategoryId);
            });
            loadXtreamCategorySets(secret, source, remaining, collected.concat(categories));
        }, sourceFailed);
    }

    function fetchXtreamCategorySets(secret, source, remaining, collected, success, failure) {
        var contentType;
        if (!remaining.length) { success(collected); return; }
        contentType = remaining.shift();
        BuroXtream.loadCategories(secret, contentType, function (categories) {
            categories.forEach(function (category) {
                category.sourceId = source.id;
                category.id = BuroDomain.id('category', source.id + ':' + contentType + ':' + category.providerCategoryId);
            });
            fetchXtreamCategorySets(secret, source, remaining, collected.concat(categories), success, failure);
        }, failure);
    }

    function fetchStalkerCategorySets(secret, session, source, remaining, collected, success, failure) {
        var contentType;
        if (!remaining.length) { success(collected); return; }
        contentType = remaining.shift();
        BuroStalker.loadCategories(secret, session, source.id, contentType, function (categories) {
            fetchStalkerCategorySets(secret, session, source, remaining, collected.concat(categories), success, failure);
        }, failure);
    }

    function m3uSnapshot(source, text) {
        var parsed = BuroM3u.parse(text, source.id);
        var categories = {};
        parsed.entries.forEach(function (entry) {
            var key = entry.item.categoryId;
            if (!categories[key]) {
                categories[key] = { id: key, sourceId: source.id, providerCategoryId: key,
                    name: entry.group, contentType: entry.item.contentType, sortOrder: Object.keys(categories).length };
            }
        });
        return {
            parsed: parsed,
            categories: Object.keys(categories).map(function (key) { return categories[key]; }),
            items: BuroM3u.metadata(parsed)
        };
    }

    function readM3uSource(source, secret, success, failure) {
        if (source.type === 'LOCAL_M3U') {
            BuroUsb.resolvePlaylist(secret, success, failure); return;
        }
        if (source.type === 'REMOTE_M3U') {
            BuroNetwork.text({ url: secret.url, maxBytes: 16 * 1024 * 1024, timeoutMs: 30000 }, success, failure);
            return;
        }
        failure({ code: 'SOURCE_TYPE_UNAVAILABLE' });
    }

    function selectUsbM3u(key) {
        var data = state.screenData || {};
        var selected = (data.files || []).filter(function (file) { return file.key === key; })[0];
        var name;
        var source;
        if (!selected || state.busy) { return; }
        name = BuroDomain.trim(selected.name.replace(/\.(m3u|m3u8)$/i, '')) || 'M3U USB';
        try { source = BuroDomain.createSourceMetadata({ name: name, type: 'LOCAL_M3U' }); }
        catch (error) { showToast(friendlyError(error), true); return; }
        state.busy = true;
        state.screenData = { loading: true, files: [] }; render();
        BuroUsb.readPlaylist(key, function (text, descriptor) {
            var snapshot;
            var selector;
            try { snapshot = m3uSnapshot(source, text); }
            catch (error) {
                state.busy = false; state.screenData = { loading: false, files: [], error: true };
                render(); showToast(friendlyError(error), true); return;
            }
            text = null;
            playlistMemory[source.id] = snapshot.parsed.entries;
            rememberM3uArtwork(snapshot.parsed.entries);
            source.channelCount = snapshot.items.length;
            selector = {
                playlistToken: descriptor.key,
                fileName: descriptor.name,
                fileSize: Number(descriptor.size) || 0
            };
            BuroStorage.secureSave(source.id, selector, function () {
                selector = null;
                persistSource(source, snapshot.categories, snapshot.items);
            }, function (error) {
                selector = null; state.busy = false;
                state.screenData = { loading: false, files: [], error: true };
                render(); showToast(friendlyError(error), true);
            });
        }, function (error) {
            state.busy = false; state.screenData = { loading: false, files: [], error: true };
            render(); showToast(friendlyError(error), true);
        });
    }

    function connectM3u(source) {
        var url = BuroDomain.trim(document.getElementById('source-playlist').value);
        if (!/^https?:\/\//i.test(url)) { sourceFailed({ code: 'SERVER_URL_INVALID' }); return; }
        BuroNetwork.text({ url: url, maxBytes: 16 * 1024 * 1024, timeoutMs: 30000 }, function (text) {
            var snapshot;
            try { snapshot = m3uSnapshot(source, text); }
            catch (error) { sourceFailed(error); return; }
            playlistMemory[source.id] = snapshot.parsed.entries;
            rememberM3uArtwork(snapshot.parsed.entries);
            source.channelCount = snapshot.items.length;
            BuroStorage.secureSave(source.id, { url: url }, function () {
                persistSource(source, snapshot.categories, snapshot.items);
            }, sourceFailed);
        }, sourceFailed);
    }

    function finishSourceRefresh(requestId, source, categories, items, replaceAllItems, parsedEntries) {
        var updated;
        if (requestId !== sourceRefreshRequestId) { return; }
        try {
            updated = BuroDomain.createSourceMetadata({
                id: source.id,
                name: source.name,
                type: source.type,
                channelCount: replaceAllItems ? items.length : source.channelCount,
                createdAt: source.createdAt,
                updatedAt: Date.now()
            });
        } catch (metadataError) {
            if (state.screenData && state.screenData.sourceId === source.id) {
                state.screenData.refreshing = false;
                state.screenData.refreshError = friendlyError(metadataError);
                render();
            }
            return;
        }
        BuroStorage.replaceSourceCatalogue(updated, categories, items, replaceAllItems, function (result) {
            var removed = {};
            var categoryIds = {};
            if (requestId !== sourceRefreshRequestId) { return; }
            (result.removedItemIds || []).forEach(function (id) {
                removed[id] = true;
                forgetUrl(artworkMemory, artworkOrder, id);
                forgetUrl(detailBackdropMemory, detailBackdropOrder, id);
                delete seriesDetailsMemory[id];
            });
            state.items.forEach(function (row) {
                if (row.sourceId === updated.id && row.contentType === 'SERIES') { delete seriesDetailsMemory[row.id]; }
            });
            categories.forEach(function (row) { categoryIds[row.id] = true; });
            state.sources = state.sources.map(function (row) { return row.id === updated.id ? updated : row; });
            state.categories = state.categories.filter(function (row) { return row.sourceId !== updated.id; }).concat(categories);
            state.items = state.items.filter(function (row) {
                if (row.sourceId !== updated.id) { return true; }
                return replaceAllItems ? false : Boolean(categoryIds[row.categoryId]);
            }).concat(items);
            state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
            state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
            if (replaceAllItems) {
                playlistMemory[updated.id] = parsedEntries || [];
                rememberM3uArtwork(parsedEntries);
            }
            Object.keys(artworkRequests).forEach(function (key) {
                if (key.indexOf(updated.id + ':') === 0) { delete artworkRequests[key]; }
            });
            refreshActiveReferences();
            if (state.screenData && state.screenData.sourceId === updated.id) {
                state.screenData.refreshing = false;
                state.screenData.refreshError = null;
                state.screenData.refreshSuccess = replaceAllItems;
            }
            render();
            retryPendingSharedTitle();
            if (updated.type === 'XTREAM') {
                BuroHeroEnrichment.clearSource(updated.id);
                startXtreamHydration(updated, true);
                showToast(t('catalogueSyncStarted'), false);
            } else { showToast(t('sourceRefreshed'), false); }
        }, function (error) {
            if (requestId !== sourceRefreshRequestId) { return; }
            if (state.screenData && state.screenData.sourceId === source.id) {
                state.screenData.refreshing = false;
                state.screenData.refreshError = friendlyError(error);
                render();
            } else { showToast(friendlyError(error), true); }
        });
    }

    function refreshSource() {
        var draft = sourceManageDraft();
        var source = state.sources.filter(function (row) { return row.id === draft.sourceId; })[0];
        var secret;
        var requestId;
        var resumeOnFailure = false;
        var sync;
        if (!source || draft.refreshing) { return; }
        if (source.type === 'XTREAM') {
            sync = catalogueSyncStatus(source);
            if (sync && sync.state === 'RUNNING') { resumeOnFailure = BuroCatalogueSync.cancel(); }
        }
        try { secret = BuroStorage.secureGet(source.id); }
        catch (error) { draft.refreshError = friendlyError(error); render(); return; }
        requestId = ++sourceRefreshRequestId;
        draft.refreshing = true;
        draft.refreshError = null;
        draft.refreshSuccess = false;
        render();
        function failed(error) {
            if (requestId !== sourceRefreshRequestId) { return; }
            draft.refreshing = false;
            draft.refreshError = friendlyError(error);
            render();
            if (resumeOnFailure) { startXtreamHydration(source, false); }
        }
        if (source.type === 'XTREAM') {
            BuroXtream.authenticate(secret, function () {
                fetchXtreamCategorySets(secret, source, ['LIVE', 'MOVIE', 'SERIES'], [], function (categories) {
                    finishSourceRefresh(requestId, source, categories, [], false, null);
                }, failed);
            }, failed);
        } else if (source.type === 'STALKER') {
            withStalkerSession(source, secret, function (session) {
                fetchStalkerCategorySets(secret, session, source, ['LIVE', 'MOVIE', 'SERIES'], [], function (categories) {
                    finishSourceRefresh(requestId, source, categories, [], false, null);
                }, failed);
            }, failed);
        } else if (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') {
            readM3uSource(source, secret, function (text) {
                var snapshot;
                try { snapshot = m3uSnapshot(source, text); }
                catch (error) { failed(error); return; }
                finishSourceRefresh(requestId, source, snapshot.categories, snapshot.items, true, snapshot.parsed.entries);
            }, failed);
        } else { failed(new Error('SOURCE_TYPE_UNAVAILABLE')); }
    }

    function persistSource(source, categories, items) {
        BuroStorage.replaceSourceCatalogue(source, categories, items, true, function () {
            state.sources.push(source);
            state.categories = state.categories.concat(categories);
            state.items = state.items.concat(items);
            assignSourceToProfile(source);
            state.busy = false;
            state.backStack = [];
            state.screen = 'SHELL'; state.section = 'SOURCES'; state.screenData = null;
            render(); showToast(t('sourceSaved'), false);
            retryPendingSharedTitle();
            startXtreamHydration(source, false);
        }, function (error) {
            try { BuroStorage.secureRemove(source.id); } catch (ignoredSecretCleanup) {}
            sourceFailed(error);
        });
    }

    function sourceFailed(error) {
        state.busy = false;
        setFormMessage(friendlyError(error), true);
    }

    function setFormMessage(message, isError) {
        var element = document.getElementById('source-form-message');
        if (!element) { showToast(message, isError); return; }
        element.textContent = message;
        element.className = isError ? 'form-message error' : 'form-message';
    }

    function openCategory(categoryId) {
        var category = null;
        var source = null;
        var requestId;
        state.categories.forEach(function (candidate) { if (candidate.id === categoryId) { category = candidate; } });
        if (!category) { return; }
        if (BuroGuard.requiresPin(category, state.preferences) && !state.unlockedCategoryIds[category.id]) {
            pushScreen('PIN_UNLOCK', { category: category });
            return;
        }
        state.sources.forEach(function (candidate) { if (candidate.id === category.sourceId) { source = candidate; } });
        requestId = beginCatalogueRequest('category', category.contentType, { category: category });
        BuroStorage.byIndex('items', 'byCategory', [category.sourceId, category.id], function (cached) {
            /* Xtream e Stalker paginam no servidor: a categoria chega vazia da
               importação e só busca itens quando é aberta. M3U já vem inteira. */
            var lazy = source && (source.type === 'XTREAM' || source.type === 'STALKER');
            if (!catalogueRequestCurrent(requestId)) { return; }
            if (cached.length || !lazy) {
                mergeItems(cached);
                state.screenData = { kind: 'category', contentType: category.contentType, category: category,
                    items: cached, cataloguePage: 0 };
                focusIndex = 0; render(); hydrateCategoryArtwork(category); return;
            }
            if (source.type === 'STALKER') { loadStalkerCategory(category, requestId); return; }
            if (BuroCatalogueSync.contains(category.sourceId, category.id)) { return; }
            loadXtreamCategory(category, requestId);
        }, function (error) { failCatalogueRequest(requestId, error); });
    }

    function beginCatalogueRequest(target, contentType, values) {
        var requestId = ++catalogueRequestId;
        var data = values || {};
        data.kind = 'catalogue-loading';
        data.target = target;
        data.contentType = contentType;
        data.requestId = requestId;
        state.screenData = data;
        focusIndex = 0;
        render();
        return requestId;
    }

    function catalogueRequestCurrent(requestId) {
        return state.screen === 'SHELL' && state.screenData &&
            state.screenData.kind === 'catalogue-loading' && state.screenData.requestId === requestId;
    }

    function failCatalogueRequest(requestId, error) {
        var current;
        if (!catalogueRequestCurrent(requestId)) { return; }
        current = state.screenData;
        current.kind = 'catalogue-error';
        current.errorCode = friendlyError(error);
        focusIndex = 0;
        render();
    }

    function mergeItems(items) {
        var known = {};
        state.items.forEach(function (item) { known[item.id] = true; });
        (items || []).forEach(function (item) {
            if (!known[item.id]) { known[item.id] = true; state.items.push(item); }
        });
    }

    function categoriesForSource(sourceId) {
        return state.categories.filter(function (category) { return category.sourceId === sourceId; });
    }

    function catalogueSyncStatus(source) {
        if (!source || source.type !== 'XTREAM') { return null; }
        return BuroCatalogueSync.progress(source, categoriesForSource(source.id));
    }

    function catalogueSyncText(status) {
        var key;
        if (!status) { return ''; }
        if (status.state === 'COMPLETE') { key = 'catalogueSyncComplete'; }
        else if (status.state === 'CANCELLED') { key = 'catalogueSyncCancelled'; }
        else if (status.state === 'ERROR') { key = 'catalogueSyncError'; }
        else if (status.state === 'RUNNING') { key = 'catalogueSyncRunning'; }
        else { key = 'catalogueSyncReady'; }
        return t(key).replace('{completed}', String(status.completed)).replace('{total}', String(status.total))
            .replace('{items}', String(status.itemCount));
    }

    function catalogueSyncBanner(source) {
        var status = catalogueSyncStatus(source);
        var percent;
        var action;
        var label;
        if (!status || !status.total || status.state === 'COMPLETE') { return ''; }
        percent = status.total ? Math.round(status.completed / status.total * 100) : 0;
        action = status.state === 'RUNNING' ? 'catalogue-sync-cancel' : 'catalogue-sync-resume';
        label = status.state === 'RUNNING' ? t('catalogueSyncCancel') : t('catalogueSyncResume');
        return '<section class="catalogue-sync-banner" data-sync-source="' + attr(source.id) + '">' +
            '<div><span class="hero-kicker">' + t('catalogueSyncTitle') + '</span><p class="catalogue-sync-label">' +
            escapeHtml(catalogueSyncText(status)) + '</p><div class="catalogue-sync-track"><span style="width:' +
            percent + '%"></span></div></div><button class="button ghost focusable" data-action="' + action +
            '" data-id="' + attr(source.id) + '">' + label + '</button></section>';
    }

    function removeHydrationReferences(removedItemIds) {
        var removed = {};
        (removedItemIds || []).forEach(function (id) {
            removed[id] = true;
            forgetUrl(artworkMemory, artworkOrder, id);
            forgetUrl(detailBackdropMemory, detailBackdropOrder, id);
            delete seriesDetailsMemory[id];
        });
        state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
        state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
    }

    function applyHydratedCategory(category, items, artwork, removedItemIds) {
        var current = state.screenData;
        var foreground = state.screen === 'SHELL' && current && current.category && current.category.id === category.id;
        removeHydrationReferences(removedItemIds);
        if (!foreground) { return; }
        state.items = state.items.filter(function (candidate) {
            return !(candidate.sourceId === category.sourceId && candidate.categoryId === category.id);
        }).concat(items);
        rememberArtworkMap(artwork);
        state.screenData = {
            kind: 'category', contentType: category.contentType, category: category,
            items: items, cataloguePage: 0
        };
        focusIndex = 0;
        render();
    }

    function completeXtreamHydration(source, status) {
        var current = state.sources.filter(function (row) { return row.id === source.id; })[0];
        var updated;
        if (!current) { return; }
        try {
            updated = BuroDomain.createSourceMetadata({
                id: current.id, name: current.name, type: current.type,
                channelCount: status.itemCount, createdAt: current.createdAt, updatedAt: Date.now()
            });
        } catch (ignoredMetadata) { return; }
        BuroStorage.put('sources', updated, function () {
            state.sources = state.sources.map(function (row) { return row.id === updated.id ? updated : row; });
            refreshActiveReferences();
            if (state.screen === 'SOURCE_MANAGE' && state.screenData && state.screenData.sourceId === updated.id) {
                state.screenData.refreshing = false;
                state.screenData.refreshError = null;
                state.screenData.refreshSuccess = true;
            }
            if (state.screen === 'SHELL' && state.section === 'HOME') {
                state.screenData = null;
            }
            render();
            retryPendingSharedTitle();
            showToast(t('catalogueSyncCompleted'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function startXtreamHydration(source, force) {
        var categories;
        var current;
        if (!source || source.type !== 'XTREAM') { return; }
        categories = categoriesForSource(source.id);
        if (!categories.length) { return; }
        current = catalogueSyncStatus(source);
        if (force || !current || current.completed < current.total) { BuroHeroEnrichment.cancel(); }
        try {
            BuroCatalogueSync.start(source, categories, {
                getSecret: function (sourceId) { return BuroStorage.secureGet(sourceId); },
                onCategory: applyHydratedCategory,
                onComplete: function (status) { completeXtreamHydration(source, status); }
            }, Boolean(force));
        } catch (error) { showToast(friendlyError(error), true); }
    }

    function startActiveSourceHydration(force) {
        startXtreamHydration(state.activeSource, force);
    }

    function catalogueSyncChanged(status) {
        var waiting = state.screenData;
        var banner;
        var label;
        var fill;
        if (!status || !state.ready) { return; }
        if (waiting && waiting.kind === 'catalogue-loading' && waiting.category &&
                waiting.category.id === status.currentCategoryId && status.state === 'ERROR') {
            failCatalogueRequest(waiting.requestId, { code: status.errorCode });
            return;
        }
        if (state.screen === 'SOURCE_MANAGE' || (state.screen === 'SHELL' && state.section === 'SOURCES')) {
            render();
            return;
        }
        banner = root && root.querySelector('[data-sync-source="' + status.sourceId + '"]');
        if (banner && status.state === 'RUNNING') {
            label = banner.querySelector('.catalogue-sync-label');
            fill = banner.querySelector('.catalogue-sync-track span');
            if (label) { label.textContent = catalogueSyncText(status); }
            if (fill) { fill.style.width = (status.total ? Math.round(status.completed / status.total * 100) : 0) + '%'; }
        } else if (banner && status.state !== 'RUNNING') { render(); }
    }

    function hydrateReferencedSeriesParents(data, referenced, done, failure) {
        var known = {};
        var wanted = {};
        var missing;
        var pending;
        var settled = false;
        function fail(error) {
            if (!settled) { settled = true; failure(error); }
        }
        data.items.forEach(function (item) { known[item.id] = true; });
        data.items.forEach(function (item) {
            if (referenced[item.id] && item.contentType === 'EPISODE' && item.categoryId && !known[item.categoryId]) {
                wanted[item.categoryId] = true;
            }
        });
        missing = Object.keys(wanted);
        pending = missing.length;
        if (!pending) { settled = true; done(data); return; }
        missing.forEach(function (itemId) {
            BuroStorage.get('items', itemId, function (item) {
                if (settled) { return; }
                if (item && item.contentType === 'SERIES' && !known[item.id]) {
                    known[item.id] = true;
                    data.items.push(item);
                }
                pending -= 1;
                if (!pending) { settled = true; done(data); }
            }, fail);
        });
    }

    function hydrateReferencedItems(data, done, failure) {
        var known = {};
        var wanted = {};
        var referenced = {};
        var missing;
        var pending;
        var settled = false;
        function fail(error) {
            if (!settled) { settled = true; failure(error); }
        }
        function finish() {
            if (settled) { return; }
            settled = true;
            hydrateReferencedSeriesParents(data, referenced, done, failure);
        }
        data.items.forEach(function (item) { known[item.id] = true; });
        data.favorites.concat(data.progress).forEach(function (entry) {
            if (entry.itemId) { referenced[entry.itemId] = true; }
            if (entry.itemId && !known[entry.itemId]) { wanted[entry.itemId] = true; }
        });
        missing = Object.keys(wanted);
        pending = missing.length;
        if (!pending) { finish(); return; }
        missing.forEach(function (itemId) {
            BuroStorage.get('items', itemId, function (item) {
                if (settled) { return; }
                if (item && !known[item.id]) { known[item.id] = true; data.items.push(item); }
                pending -= 1;
                if (!pending) { finish(); }
            }, fail);
        });
    }

    function loadXtreamCategory(category, requestId, cached, explicitRefresh, fallbackPage, resumeBackground) {
        var secret;
        cached = cached || [];
        function failed(error) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            if (!cached.length) { failCatalogueRequest(requestId, error); return; }
            state.screenData = { kind: 'category', contentType: category.contentType, category: category,
                items: cached, refreshError: true, cataloguePage: Number(fallbackPage) || 0 };
            focusIndex = 0;
            render();
        }
        try { secret = BuroStorage.secureGet(category.sourceId); }
        catch (error) { failed(error); return; }
        BuroXtream.loadItems(secret, category.sourceId, category.contentType, category, function (items, artwork) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            BuroStorage.replaceCategoryItems(category.sourceId, category.id, items, function (result) {
                var removed = {};
                var displayCurrent = catalogueRequestCurrent(requestId);
                (result.removedItemIds || []).forEach(function (id) {
                    removed[id] = true;
                    forgetUrl(artworkMemory, artworkOrder, id);
                    forgetUrl(detailBackdropMemory, detailBackdropOrder, id);
                    delete seriesDetailsMemory[id];
                });
                state.items = state.items.filter(function (candidate) {
                    return !(candidate.sourceId === category.sourceId && candidate.categoryId === category.id);
                }).concat(items);
                state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
                state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
                rememberArtworkMap(artwork);
                BuroCatalogueSync.markCategoryComplete(category.sourceId, category.id, items.length);
                if (explicitRefresh) { BuroHeroEnrichment.clearSource(category.sourceId); }
                if (resumeBackground) { startActiveSourceHydration(false); }
                if (!displayCurrent) { return; }
                state.screenData = { kind: 'category', contentType: category.contentType, category: category,
                    items: items, cataloguePage: 0 };
                focusIndex = 0; render();
                if (explicitRefresh) { showToast(t('categoryRefreshed'), false); }
            }, failed);
        }, failed);
    }

    /*
      O portal pagina no servidor, como o Xtream. Trazemos a primeira página e
      gravamos o que der para gravar: o `cmd` de cada item fica no adapter, em
      memória, e é reconstruído no próximo handshake se a sessão cair.
    */
    function loadStalkerCategory(category, requestId, cached, explicitRefresh, fallbackPage) {
        var secret;
        var source = null;
        cached = cached || [];
        state.sources.forEach(function (candidate) {
            if (candidate.id === category.sourceId) { source = candidate; }
        });
        function failed(error) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            if (!cached.length) { failCatalogueRequest(requestId, error); return; }
            state.screenData = { kind: 'category', contentType: category.contentType, category: category,
                items: cached, refreshError: true, cataloguePage: Number(fallbackPage) || 0 };
            focusIndex = 0;
            render();
        }
        if (!source) { failed({ code: 'SOURCE_TYPE_UNAVAILABLE' }); return; }
        try { secret = BuroStorage.secureGet(category.sourceId); }
        catch (error) { failed(error); return; }
        withStalkerSession(source, secret, function (session) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            BuroStalker.loadItems(secret, session, category.sourceId, category.contentType, category, 1, function (page) {
                var items = page && page.items ? page.items : [];
                if (!catalogueRequestCurrent(requestId)) { return; }
                BuroStorage.replaceCategoryItems(category.sourceId, category.id, items, function (result) {
                    var removed = {};
                    var displayCurrent = catalogueRequestCurrent(requestId);
                    (result.removedItemIds || []).forEach(function (id) {
                        removed[id] = true;
                        forgetUrl(artworkMemory, artworkOrder, id);
                        forgetUrl(detailBackdropMemory, detailBackdropOrder, id);
                        delete seriesDetailsMemory[id];
                    });
                    state.items = state.items.filter(function (candidate) {
                        return !(candidate.sourceId === category.sourceId && candidate.categoryId === category.id);
                    }).concat(items);
                    state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
                    state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
                    if (!displayCurrent) { return; }
                    state.screenData = { kind: 'category', contentType: category.contentType, category: category,
                        items: items, cataloguePage: 0 };
                    focusIndex = 0; render();
                    if (explicitRefresh) { showToast(t('categoryRefreshed'), false); }
                }, failed);
            }, failed);
        }, failed);
    }

    function refreshOpenCategory() {
        var data = state.screenData;
        var category;
        var cached;
        var requestId;
        var sync;
        var source = null;
        var resumeBackground = false;
        if (!data || data.kind !== 'category' || !data.category) { return; }
        category = data.category;
        cached = data.items || [];
        state.sources.forEach(function (candidate) {
            if (candidate.id === category.sourceId) { source = candidate; }
        });
        sync = catalogueSyncStatus(state.activeSource);
        if (sync && sync.sourceId === category.sourceId && sync.state === 'RUNNING') {
            resumeBackground = BuroCatalogueSync.cancel();
        }
        requestId = beginCatalogueRequest('category', category.contentType, {
            category: category, cataloguePage: Number(data.cataloguePage) || 0
        });
        if (source && source.type === 'STALKER') {
            loadStalkerCategory(category, requestId, cached, true, data.cataloguePage);
            return;
        }
        loadXtreamCategory(category, requestId, cached, true, data.cataloguePage, resumeBackground);
    }

    function changeCategoryPage(delta) {
        var data = state.screenData;
        var preferred;
        if (!data || data.kind !== 'category') { return; }
        data.cataloguePage = Math.max(0, (Number(data.cataloguePage) || 0) + delta);
        render();
        preferred = delta > 0 ? 'category-page-next' : 'category-page-previous';
        if (!root.querySelector('[data-action="' + preferred + '"]')) {
            preferred = delta > 0 ? 'category-page-previous' : 'category-page-next';
        }
        focusMatching('[data-action="' + preferred + '"]');
    }

    function changeCategorySettingsPage(delta) {
        var data = state.screenData;
        var preferred;
        if (state.screen !== 'CATEGORY_SETTINGS' || !data || data.kind !== 'category-settings') { return; }
        data.page = Math.max(0, (Number(data.page) || 0) + delta);
        render();
        preferred = delta > 0 ? 'category-settings-page-next' : 'category-settings-page-previous';
        if (!root.querySelector('[data-action="' + preferred + '"]')) {
            preferred = delta > 0 ? 'category-settings-page-previous' : 'category-settings-page-next';
        }
        focusMatching('[data-action="' + preferred + '"]');
    }

    function resetLibraryView() {
        ['MY_BURO', 'CONTINUE_WATCHING', 'HISTORY'].forEach(function (section) {
            libraryFilters[section] = 'ALL';
            libraryPages[section] = 0;
        });
    }

    function changeLibraryPage(element, delta) {
        var section = element.getAttribute('data-section');
        var preferred;
        var selector;
        if (['MY_BURO', 'CONTINUE_WATCHING', 'HISTORY'].indexOf(section) < 0 || state.section !== section) { return; }
        libraryPages[section] = Math.max(0, (Number(libraryPages[section]) || 0) + delta);
        render();
        preferred = delta > 0 ? 'library-page-next' : 'library-page-previous';
        selector = '[data-action="' + preferred + '"][data-section="' + section + '"]';
        if (!root.querySelector(selector)) {
            preferred = delta > 0 ? 'library-page-previous' : 'library-page-next';
            selector = '[data-action="' + preferred + '"][data-section="' + section + '"]';
        }
        focusMatching(selector);
    }

    function changeDownloadPage(delta) {
        var preferred;
        if (state.screen !== 'SHELL' || state.section !== 'DOWNLOADS') { return; }
        downloadPage = Math.max(0, (Number(downloadPage) || 0) + delta);
        render();
        preferred = delta > 0 ? 'download-page-next' : 'download-page-previous';
        if (!root.querySelector('[data-action="' + preferred + '"]')) {
            preferred = delta > 0 ? 'download-page-previous' : 'download-page-next';
        }
        focusMatching('[data-action="' + preferred + '"]');
    }

    function changeSeriesPage(element, delta) {
        var data = state.screenData;
        var season = Number(element.getAttribute('data-season'));
        var preferred;
        var selector;
        if (!data || data.kind !== 'series' || !isFinite(season)) { return; }
        data.seasonPages = data.seasonPages || {};
        data.seasonPages[season] = Math.max(0, (Number(data.seasonPages[season]) || 0) + delta);
        render();
        preferred = delta > 0 ? 'series-page-next' : 'series-page-previous';
        selector = '[data-action="' + preferred + '"][data-season="' + season + '"]';
        if (!root.querySelector(selector)) {
            preferred = delta > 0 ? 'series-page-previous' : 'series-page-next';
            selector = '[data-action="' + preferred + '"][data-season="' + season + '"]';
        }
        focusMatching(selector);
    }

    function sourceForDownload(item) {
        var source = null;
        state.sources.some(function (candidate) {
            if (item && candidate.id === item.sourceId) { source = candidate; return true; }
            return false;
        });
        return source;
    }

    function downloadEntryForItem(item) {
        var identity = item ? BuroDomain.contentIdentity(item) : '';
        var entry = null;
        BuroDownloads.list().some(function (candidate) {
            if (candidate.id === identity) { entry = candidate; return true; }
            return false;
        });
        return entry;
    }

    /*
      A linha de ações do detalhe.

      Assistir conserva o botão com rótulo; o resto vira glifo com legenda curta
      embaixo, na mesma ordem do Android: Favoritar, Lembrete, Baixar, Trailer,
      Compartilhar.

      A razão que motivou a mudança no Android — seis pílulas rotuladas enchendo
      três linhas antes da sinopse começar — vale igualmente aqui, só que por
      outro caminho. O hero tem 1076 px úteis e as mesmas seis pílulas somam
      cerca de 1265 px; como `.action-row` não quebra linha nem rola, o que passa
      do fim simplesmente não é alcançável. Numa série, com `Baixar temporada` e
      `Baixar série`, sobra ainda menos.

      Nada foi escondido atrás de um menu: esconder Compartilhar ou Trailer sob
      um "⋮" pouparia o mesmo espaço e custaria ao usuário qualquer pista de que
      existem — a mesma decisão registrada no Android.

      Os controles de download continuam vindo de `downloadControls`, porque ali
      um mesmo lugar muda de significado conforme a fila (cancelar, continuar,
      assistir, tentar novamente) e tem estados que um glifo fixo não representa.
    */
    function actionGlyph(action, id, glyph, label, selected, extra) {
        return '<button class="action-glyph focusable' + (selected ? ' selected' : '') +
            '" data-action="' + attr(action) + '" data-id="' + attr(id) + '"' +
            (extra || '') + ' aria-label="' + attr(label) + '">' +
            '<span class="action-glyph-mark" aria-hidden="true">' + glyph + '</span>' +
            '<span class="action-glyph-label" aria-hidden="true">' + escapeHtml(label) + '</span>' +
            '</button>';
    }

    function detailActionsHtml(item, isSeries, episodeRows, trailerId) {
        var favorite = isFavorite(item.id);
        var marked = hasReminder(item);
        var glyphs = '';
        var primary = !isSeries ?
            '<div class="action-row detail-actions"><button class="button primary focusable" data-action="play" data-id="' +
                attr(item.id) + '">' + t('watch') + '</button></div>' : '';
        /* Rótulo curto nesta barra: `addFavorite` é "Adicionar à Minha BURO",
           escrito para uma pílula larga. Numa coluna de 128 px ele quebrava em
           duas linhas e empurrava as legendas vizinhas para fora do alinhamento.
           O Android usa "Favoritar"/"Favoritado" pelo mesmo motivo. */
        glyphs += actionGlyph('favorite', item.id, favorite ? '★' : '☆',
            favorite ? t('favoritedShort') : t('favoriteShort'), favorite);
        glyphs += actionGlyph('reminder', item.id, marked ? '◉' : '○',
            marked ? t('reminderRemove') : t('reminderAdd'), marked);
        /* Download conserva os próprios botões: o rótulo acompanha a fila real. */
        glyphs += downloadButton(item, isSeries) + seriesDownloadAllButton(item, episodeRows, isSeries);
        if (trailerId && BuroTrailer.available()) {
            glyphs += actionGlyph('trailer', item.id, '▷', t('trailer'), false);
        }
        glyphs += actionGlyph('share', item.id, '↗', t('share'), false);
        return primary + '<div class="detail-action-bar">' + glyphs + '</div>';
    }

    function downloadControls(item, compact) {
        var entry = downloadEntryForItem(item);
        var css = compact ? ' episode-download-action' : '';
        var label;
        if (!entry) {
            return '<button class="button ghost focusable' + css + '" data-action="download" data-id="' +
                attr(item.id) + '">↓ ' + t('download') + '</button>';
        }
        if (entry.state === 'DOWNLOADING' || entry.state === 'QUEUED') {
            label = t('downloadCancel') + (entry.state === 'DOWNLOADING' && entry.totalBytes ? ' · ' + entry.percent + '%' : '');
            return '<button class="button ghost focusable' + css + '" data-action="download-cancel" data-id="' +
                attr(entry.id) + '">' + escapeHtml(label) + '</button>';
        }
        if (entry.state === 'PAUSED' || entry.state === 'STORAGE_MISSING') {
            return '<button class="button ghost focusable' + css + '" data-action="download-resume" data-id="' +
                attr(entry.id) + '">▶ ' + t('downloadResume') + '</button>';
        }
        if (entry.state === 'COMPLETED') {
            return '<button class="button primary focusable' + css + '" data-action="download-play" data-id="' +
                attr(entry.id) + '">▶ ' + t('watch') + '</button><button class="button ghost focusable' + css +
                '" data-action="download-remove" data-id="' + attr(entry.id) + '">' + t('downloadRemove') + '</button>';
        }
        return '<button class="button ghost focusable' + css + '" data-action="download-retry" data-id="' +
            attr(item.id) + '" data-key="' + attr(entry.id) + '">↻ ' + t('retry') + '</button>';
    }

    function episodeCards(items, downloadsAvailable) {
        var visible = (items || []).filter(itemVisible);
        if (!visible.length) { return emptyState('B', t('error'), t('unavailable'), '', ''); }
        return '<div class="card-row catalogue-layout-compact episode-download-row">' + visible.map(function (item) {
            return '<div class="episode-download-item">' + mediaCard(item, 'compact') +
                (downloadsAvailable ? downloadControls(item, true) : '') + '</div>';
        }).join('') + '</div>';
    }

    function seriesBulkDownloadAvailable(item) {
        var source = sourceForDownload(item);
        return Boolean(BuroDownloads.enabled() && source && source.type === 'XTREAM');
    }

    function seriesDownloadAllButton(item, episodes, isSeries) {
        if (!isSeries || !seriesBulkDownloadAvailable(item) ||
                !BuroDownloads.bulkCandidates(episodes).length) { return ''; }
        return '<button class="button ghost focusable" data-action="series-download-all" data-id="' +
            attr(item.id) + '">↓ ' + t('downloadSeries') + '</button>';
    }

    /*
      O botão só existe quando há um dispositivo USB montado e uma fonte que o
      adapter realmente consegue resolver no último instante. A TV ao vivo
      nunca é elegível — ver ADR-008.
    */
    function downloadButton(item, isSeries) {
        var source = sourceForDownload(item);
        var sourceSupported = source && (source.type === 'XTREAM' || source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U');
        var directM3uFile = source && (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') &&
            item && item.locator && Boolean(item.locator.extension);
        if (isSeries || !BuroDownloads.enabled() || !sourceSupported ||
                ((source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') && !directM3uFile)) { return ''; }
        if (!BuroDownloads.downloadable(item.contentType)) { return ''; }
        return downloadControls(item, false);
    }

    /*
      A URL é produzida dentro do callback, no instante em que o download
      começa, e some assim que ele retorna. Nada dela é guardado — a mesma
      regra de resolução tardia que vale para a reprodução.
    */
    function startDownloadItem(item, success, failure) {
        var source = sourceForDownload(item);
        if (!item || !source) { failure({ code: 'SOURCE_UNRESOLVED' }); return; }
        if (source.type === 'XTREAM') {
            BuroDownloads.start(item, function () {
                return BuroXtream.resolvePlayback(BuroStorage.secureGet(source.id), item.locator);
            }, success, failure);
            return;
        }
        if ((source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') && item.locator && item.locator.extension) {
            BuroDownloads.startAsync(item, function (resolved, unresolved) {
                resolveM3uItemUrl(source, item, resolved, unresolved);
            }, success, failure);
            return;
        }
        failure({ code: 'SOURCE_UNRESOLVED' });
    }

    function downloadItem(itemId) {
        var item = null;
        state.items.forEach(function (candidate) { if (candidate.id === itemId) { item = candidate; } });
        if (!item) { return; }
        startDownloadItem(item, function () {
            showToast(t('downloadStarted'));
            render();
        }, function (error) {
            showToast(downloadError(error), true);
        });
    }

    function openBulkDownloadConfirm(seasonNumber) {
        var data = state.screenData;
        var candidates;
        if (!data || data.kind !== 'series' || !seriesBulkDownloadAvailable(data.parent)) {
            showToast(t('downloadUnavailable'), true); return;
        }
        candidates = BuroDownloads.bulkCandidates(data.items || [], seasonNumber);
        if (!candidates.length) { showToast(t('downloadBatchNoChange'), false); return; }
        pushScreen('BULK_DOWNLOAD_CONFIRM', {
            title: (data.details && data.details.title) || data.parent.name,
            season: seasonNumber == null ? null : Number(seasonNumber),
            items: candidates
        });
    }

    function startBulkDownload(items) {
        var pending = items.length;
        var started = 0;
        var failed = 0;
        var already = 0;
        function finished() {
            var message;
            if (pending > 0) { return; }
            if (started && failed) {
                message = t('downloadBatchPartial').replace('{started}', started).replace('{failed}', failed);
                showToast(message, true);
            } else if (started) {
                showToast(t('downloadBatchStarted').replace('{count}', started), false);
            } else if (failed) { showToast(t('downloadBatchFailed'), true); }
            else if (already) { showToast(t('downloadBatchNoChange'), false); }
            render();
        }
        if (!pending) { showToast(t('downloadBatchNoChange'), false); return; }
        items.forEach(function (item) {
            startDownloadItem(item, function () {
                started += 1; pending -= 1; finished();
            }, function (error) {
                if (error && error.code === 'ALREADY_QUEUED') { already += 1; }
                else { failed += 1; }
                pending -= 1; finished();
            });
        });
    }

    function confirmBulkDownload() {
        var items = state.screenData && state.screenData.items ? state.screenData.items.slice() : [];
        goBack();
        startBulkDownload(items);
    }

    function downloadError(error) {
        var code = error && error.code;
        var known = {
            LIVE_NOT_DOWNLOADABLE: 'liveNotDownloadable',
            DOWNLOAD_UNAVAILABLE: 'downloadUnavailable',
            STORAGE_REQUIRED: 'usbRequired',
            ALREADY_QUEUED: 'downloadAlreadyQueued',
            TARGET_UNWRITABLE: 'targetUnwritable',
            STORAGE_UNAVAILABLE: 'usbRequired'
        };
        return t(known[code] || 'downloadFailed');
    }

    function playItem(itemId) {
        var found = findItemAndSource(itemId);
        var progress = playbackProgress(itemId);
        var decision;
        if (!found.item) { return; }
        decision = BuroDomain.resumeDecision(progress && progress.entry, found.item.contentType !== 'LIVE');
        if (decision.kind === 'resume') {
            pushScreen('RESUME_PROMPT', { itemId: itemId, positionMs: decision.positionMs });
            return;
        }
        beginPlayback(itemId, 0);
    }

    function compatibleAlternativeBetter(candidate, current) {
        var candidateRisk;
        var currentRisk;
        var candidateOrder;
        var currentOrder;
        if (!current) { return true; }
        candidateRisk = BuroDomain.hasHighRiskVideoTag(candidate.name) ? 1 : 0;
        currentRisk = BuroDomain.hasHighRiskVideoTag(current.name) ? 1 : 0;
        if (candidateRisk !== currentRisk) { return candidateRisk < currentRisk; }
        candidateOrder = Number(candidate.sortOrder) || 0;
        currentOrder = Number(current.sortOrder) || 0;
        if (candidateOrder !== currentOrder) { return candidateOrder < currentOrder; }
        return String(candidate.id) < String(current.id);
    }

    function findCompatibleMovieAlternative(item, success) {
        var prefix = BuroDomain.compatibilityTitlePrefix(item && item.name).toLowerCase();
        if (!item || item.contentType !== 'MOVIE' || !BuroDomain.hasHighRiskVideoTag(item.name) || !prefix) {
            success(null); return;
        }
        BuroStorage.foldByIndex('items', 'byType', [item.sourceId, 'MOVIE'], function (best, candidate) {
            if (!candidate || candidate.id === item.id ||
                    String(candidate.name || '').toLowerCase().indexOf(prefix) !== 0) { return best; }
            return compatibleAlternativeBetter(candidate, best) ? candidate : best;
        }, null, success, function () { success(null); });
    }

    function beginResolvedPlayback(item, source, playbackItem, startPositionMs) {
        var secret;
        var savedProgress = playbackProgress(item.id);
        var schedule = state.screenData && state.screenData.kind === 'live' &&
            state.screenData.parent && state.screenData.parent.id === item.id ?
            (state.screenData.schedule || []).slice(0, 100) : [];
        playerTitle.textContent = item.name;
        currentPlayback = { itemId: item.id, contentType: item.contentType, positionMs: startPositionMs,
            durationMs: startPositionMs > 0 && savedProgress && savedProgress.entry ?
                Number(savedProgress.entry.durationMs) || 0 : 0, lastSavedAt: 0, schedule: schedule };
        overlay.hidden = false;
        document.body.classList.add('playing');
        root.setAttribute('aria-hidden', 'true');
        preparePlayerOverlay();
        updatePlayerTimeline(startPositionMs, currentPlayback.durationMs);
        try { secret = BuroStorage.secureGet(source.id); }
        catch (error) { playbackFailed({ code: 'PLAYBACK_UNKNOWN' }); return; }
        if (source.type === 'XTREAM') {
            try { BuroPlayer.play(BuroXtream.resolvePlayback(secret, playbackItem.locator), startPositionMs); }
            catch (error) { playbackFailed({ code: 'PLAYBACK_UNKNOWN' }); }
        } else if (source.type === 'STALKER') {
            resolveStalkerPlayback(source, secret, playbackItem, startPositionMs);
        } else if (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') {
            resolveM3uPlayback(source, secret, playbackItem, startPositionMs);
        }
    }

    /*
      O portal devolve uma URL de uso único a cada play. Ela vai direto para o
      player e não é guardada em lugar nenhum — nem no item, nem no catálogo.
    */
    function resolveStalkerPlayback(source, secret, item, startPositionMs) {
        var playback = currentPlayback;
        withStalkerSession(source, secret, function (session) {
            if (currentPlayback !== playback) { return; }
            BuroStalker.resolvePlayback(secret, session, source.id, item.locator, function (url) {
                if (currentPlayback !== playback) { url = null; return; }
                BuroPlayer.play(url, startPositionMs);
                url = null;
            }, function (error) {
                if (currentPlayback !== playback) { return; }
                playbackFailed({ code: error && error.code === 'NETWORK_ERROR' ?
                    'PLAYBACK_CONNECTION' : 'PLAYBACK_SOURCE_UNAVAILABLE' });
            });
        }, function (error) {
            if (currentPlayback !== playback) { return; }
            playbackFailed({ code: error && error.code === 'NETWORK_ERROR' ?
                'PLAYBACK_CONNECTION' : 'PLAYBACK_SOURCE_UNAVAILABLE' });
        });
    }

    function beginPlayback(itemId, startPositionMs) {
        var found = findItemAndSource(itemId);
        var requestId;
        if (!found.item || !found.source) { showToast(t('unavailable'), true); return; }
        if (found.source.type === 'XTREAM' && found.item.contentType === 'SERIES') {
            openSeries(found.item, found.source); return;
        }
        requestId = ++playbackResolveRequestId;
        playerTitle.textContent = found.item.name;
        overlay.hidden = false;
        document.body.classList.add('playing');
        root.setAttribute('aria-hidden', 'true');
        preparePlayerOverlay();
        updatePlayerTimeline(startPositionMs, 0);
        findCompatibleMovieAlternative(found.item, function (alternative) {
            if (requestId !== playbackResolveRequestId || !document.body.classList.contains('playing')) { return; }
            beginResolvedPlayback(found.item, found.source, alternative || found.item, startPositionMs);
        });
    }

    function beginOfflinePlayback(downloadId, startPositionMs) {
        var entry = BuroDownloads.list().filter(function (candidate) { return candidate.id === downloadId; })[0];
        var playback;
        if (!entry || entry.state !== 'COMPLETED') { showToast(t('unavailable'), true); return; }
        currentPlayback = {
            itemId: entry.id, contentType: entry.contentType, positionMs: Math.max(0, Number(startPositionMs) || 0),
            durationMs: 0, lastSavedAt: 0, offline: true
        };
        playback = currentPlayback;
        playerTitle.textContent = entry.name;
        overlay.hidden = false;
        document.body.classList.add('playing');
        root.setAttribute('aria-hidden', 'true');
        preparePlayerOverlay();
        updatePlayerTimeline(playback.positionMs, 0);
        BuroDownloads.resolveCompletedFile(entry.id, function (uri) {
            if (currentPlayback !== playback) { uri = null; return; }
            BuroPlayer.play(uri, playback.positionMs);
            uri = null;
        }, function () {
            if (currentPlayback === playback) { playbackFailed({ code: 'PLAYBACK_SOURCE_UNAVAILABLE' }); }
        });
    }

    function playCompletedDownload(downloadId) {
        var entry = BuroDownloads.list().filter(function (candidate) { return candidate.id === downloadId; })[0];
        var progress = playbackProgress(downloadId);
        var decision;
        if (!entry || entry.state !== 'COMPLETED') { showToast(t('unavailable'), true); return; }
        decision = BuroDomain.resumeDecision(progress && progress.entry, true);
        if (decision.kind === 'resume') {
            pushScreen('RESUME_PROMPT', { offlineDownloadId: downloadId, positionMs: decision.positionMs });
            return;
        }
        beginOfflinePlayback(downloadId, 0);
    }

    function chooseResume(resume) {
        var data = state.screenData;
        var previous;
        if (state.screen !== 'RESUME_PROMPT' || !data || (!data.itemId && !data.offlineDownloadId)) { return; }
        previous = state.backStack.pop();
        if (previous) {
            state.screen = previous.screen; state.section = previous.section; state.screenData = previous.data;
        } else { state.screen = 'SHELL'; state.screenData = null; }
        focusIndex = 0;
        render();
        if (data.offlineDownloadId) {
            beginOfflinePlayback(data.offlineDownloadId, resume ? Number(data.positionMs) || 0 : 0);
        } else { beginPlayback(data.itemId, resume ? Number(data.positionMs) || 0 : 0); }
    }

    function findItemAndSource(itemId) {
        var item = null;
        var source = null;
        state.items.forEach(function (candidate) { if (candidate.id === itemId) { item = candidate; } });
        if (item) { state.sources.forEach(function (candidate) { if (candidate.id === item.sourceId) { source = candidate; } }); }
        return { item: item, source: source };
    }

    function openMovieDetails(itemId, originOverride) {
        var found = findItemAndSource(itemId);
        var secret;
        var originSection = originOverride || state.section;
        var requestId;
        if (!found.item || !found.source) { return; }
        state.section = 'MOVIES';
        if (found.source.type !== 'XTREAM') {
            state.screenData = { kind: 'movie', parent: found.item, details: { title: found.item.name }, originSection: originSection };
            focusIndex = 0; render(); enrichTitleFromTmdb(found.item, false); return;
        }
        requestId = beginCatalogueRequest('movie', 'MOVIE', { parent: found.item, originSection: originSection });
        try { secret = BuroStorage.secureGet(found.source.id); }
        catch (error) { failCatalogueRequest(requestId, error); return; }
        BuroXtream.loadMovieDetails(secret, found.item, function (details, artworkUrl, backdropUrl) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            rememberArtwork(found.item.id, artworkUrl);
            rememberDetailBackdrop(found.item.id, backdropUrl);
            state.screenData = { kind: 'movie', parent: found.item, details: details, originSection: originSection };
            focusIndex = 0; render(); enrichTitleFromTmdb(found.item, false);
        }, function (error) { failCatalogueRequest(requestId, error); });
    }

    function openSeriesById(itemId, originOverride) {
        var found = findItemAndSource(itemId);
        if (!found.item || !found.source) { return; }
        if (found.source.type !== 'XTREAM') { showToast(t('unavailable'), true); return; }
        openSeries(found.item, found.source, originOverride);
    }

    function openLiveDetails(itemId, originOverride) {
        var found = findItemAndSource(itemId);
        var secret;
        var originSection = originOverride || state.section;
        var requestId;
        if (!found.item || !found.source) { return; }
        state.section = 'LIVE';
        if (found.source.type !== 'XTREAM') {
            state.screenData = { kind: 'live', parent: found.item, schedule: [], originSection: originSection };
            focusIndex = 0; render(); return;
        }
        requestId = beginCatalogueRequest('live', 'LIVE', { parent: found.item, originSection: originSection });
        try { secret = BuroStorage.secureGet(found.source.id); }
        catch (error) { failCatalogueRequest(requestId, error); return; }
        BuroXtream.loadLiveEpg(secret, found.item, function (schedule) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            state.screenData = { kind: 'live', parent: found.item, schedule: schedule, originSection: originSection };
            focusIndex = 0; render();
        }, function (error) { failCatalogueRequest(requestId, error); });
    }

    function openSeries(item, source, originOverride) {
        var originSection = originOverride || state.section;
        var cached = state.items.filter(function (candidate) {
            return candidate.sourceId === source.id && candidate.contentType === 'EPISODE' && candidate.categoryId === item.id;
        });
        var secret;
        var requestId;
        function showCachedError(error) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            if (!cached.length) { failCatalogueRequest(requestId, error || { code: 'NETWORK_ERROR' }); return; }
            state.screenData = { kind: 'series', parent: item, items: cached, details: null,
                detailsError: true, originSection: originSection };
            focusIndex = 0;
            render(); enrichTitleFromTmdb(item, true);
        }
        state.section = 'SERIES';
        if (cached.length && seriesDetailsMemory[item.id]) {
            state.screenData = { kind: 'series', parent: item, items: cached,
                details: seriesDetailsMemory[item.id], originSection: originSection };
            focusIndex = 0; render(); enrichTitleFromTmdb(item, true); return;
        }
        requestId = beginCatalogueRequest('series', 'SERIES', { parent: item, originSection: originSection });
        try { secret = BuroStorage.secureGet(source.id); }
        catch (error) { showCachedError(); return; }
        BuroXtream.loadSeriesEpisodes(secret, source.id, item, function (episodes, details, artworkUrl, backdropUrl) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            rememberArtwork(item.id, artworkUrl);
            rememberDetailBackdrop(item.id, backdropUrl);
            BuroStorage.replaceCategoryItems(source.id, item.id, episodes, function (result) {
                var removed = {};
                var displayCurrent = catalogueRequestCurrent(requestId);
                (result.removedItemIds || []).forEach(function (id) {
                    removed[id] = true;
                    forgetUrl(artworkMemory, artworkOrder, id);
                    forgetUrl(detailBackdropMemory, detailBackdropOrder, id);
                });
                state.items = state.items.filter(function (candidate) {
                    return !(candidate.sourceId === source.id && candidate.contentType === 'EPISODE' && candidate.categoryId === item.id);
                }).concat(episodes);
                state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
                state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
                seriesDetailsMemory[item.id] = details || { title: item.name };
                if (!displayCurrent) { return; }
                state.screenData = { kind: 'series', parent: item, items: episodes, details: details, originSection: originSection };
                focusIndex = 0; render(); enrichTitleFromTmdb(item, true);
            }, showCachedError);
        }, showCachedError);
    }

    function retryCatalogueRequest() {
        var data = state.screenData;
        if (!data || data.kind !== 'catalogue-error') { return; }
        if (data.target === 'category' && data.category) { openCategory(data.category.id); }
        else if (data.target === 'movie' && data.parent) { openMovieDetails(data.parent.id, data.originSection); }
        else if (data.target === 'live' && data.parent) { openLiveDetails(data.parent.id, data.originSection); }
        else if (data.target === 'series' && data.parent) { openSeriesById(data.parent.id, data.originSection); }
    }

    function openTrailer(itemId) {
        var data = state.screenData;
        var details;
        if (!data || (data.kind !== 'movie' && data.kind !== 'series') || !data.parent || data.parent.id !== itemId) { return; }
        details = data.details || {};
        if (!BuroTrailer.open(details.youtubeTrailerId, details.title || data.parent.name, {
            title: t('trailer'),
            loading: t('trailerLoading'),
            playing: t('trailerPlaying'),
            playingMuted: t('trailerPlayingMuted'),
            paused: t('trailerPaused'),
            ended: t('trailerEnded'),
            error: t('trailerUnavailable'),
            hint: t('trailerHint')
        })) { showToast(t('trailerUnavailable'), true); }
    }

    function matchingM3uEntry(entries, item) {
        var index = Number(item && item.locator && item.locator.entryIndex);
        var identity = BuroDomain.contentIdentity(item);
        var candidate = isFinite(index) ? entries[index] : null;
        if (candidate && BuroDomain.contentIdentity(candidate.item) === identity) { return candidate; }
        candidate = null;
        (entries || []).some(function (entry) {
            if (BuroDomain.contentIdentity(entry.item) === identity) { candidate = entry; return true; }
            return false;
        });
        return candidate;
    }

    function resolveM3uItemUrl(source, item, success, failure, knownSecret) {
        var entries = playlistMemory[source.id];
        var entry = entries ? matchingM3uEntry(entries, item) : null;
        var secret;
        if (entry) { success(entry.streamUrl); return; }
        try { secret = knownSecret || BuroStorage.secureGet(source.id); }
        catch (error) { failure({ code: 'SOURCE_UNRESOLVED' }); return; }
        readM3uSource(source, secret, function (text) {
            var parsed;
            var resolvedEntry;
            try { parsed = BuroM3u.parse(text, source.id); }
            catch (error) { failure({ code: 'SOURCE_UNRESOLVED' }); return; }
            playlistMemory[source.id] = parsed.entries;
            rememberM3uArtwork(parsed.entries);
            resolvedEntry = matchingM3uEntry(parsed.entries, item);
            if (!resolvedEntry) { failure({ code: 'SOURCE_UNRESOLVED' }); return; }
            success(resolvedEntry.streamUrl);
        }, function () {
            failure({ code: source.type === 'LOCAL_M3U' ? 'SOURCE_UNRESOLVED' : 'NETWORK_ERROR' });
        });
    }

    function resolveM3uPlayback(source, secret, item, startPositionMs) {
        resolveM3uItemUrl(source, item, function (url) {
            BuroPlayer.play(url, startPositionMs);
            url = null;
        }, function (error) {
            playbackFailed({ code: error && error.code === 'NETWORK_ERROR' ?
                'PLAYBACK_CONNECTION' : 'PLAYBACK_SOURCE_UNAVAILABLE' });
        }, secret);
        secret = null;
    }

    function playbackFailed(error) {
        persistProgress(false);
        closePlayerMenu();
        BuroPlayer.stop();
        showPlayerError(error);
    }

    function stopPlayback() {
        playbackResolveRequestId += 1;
        resetPlayerControlsLock();
        persistProgress(false);
        currentPlayback = null;
        clearPlayerError();
        closePlayerMenu();
        BuroPlayer.stop();
        document.body.classList.remove('playing');
        root.removeAttribute('aria-hidden');
        overlay.hidden = true;
    }

    function updatePlaybackTime(positionMs, durationMs) {
        updatePlayerTimeline(positionMs, durationMs);
        if (!currentPlayback) { return; }
        currentPlayback.positionMs = Number(positionMs) || 0;
        currentPlayback.durationMs = Number(durationMs) || 0;
        if (Date.now() - currentPlayback.lastSavedAt >= 15000) { persistProgress(false); }
    }

    function persistProgress(completed) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var playback = currentPlayback;
        var row;
        var replaced = false;
        if (!profileId || !playback || playback.contentType === 'LIVE' ||
                (!completed && Number(playback.durationMs) <= 0)) { return; }
        row = {
            id: BuroDomain.id('progress', profileId + ':' + playback.itemId),
            profileId: profileId, itemId: playback.itemId,
            positionMs: playback.positionMs, durationMs: playback.durationMs,
            completed: Boolean(completed || BuroDomain.playbackCompleted(playback.positionMs, playback.durationMs)),
            updatedAt: Date.now()
        };
        playback.lastSavedAt = row.updatedAt;
        state.progress = state.progress.map(function (entry) {
            if (entry.id === row.id) { replaced = true; return row; }
            return entry;
        });
        if (!replaced) { state.progress.push(row); }
        BuroStorage.put('progress', row, function () {}, function () {});
        if (completed) { currentPlayback = null; }
    }

    function toggleFavorite(itemId, completed) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var existing = null;
        var row;
        if (!profileId || !itemId) { return; }
        state.favorites.forEach(function (favorite) {
            if (favorite.profileId === profileId && favorite.itemId === itemId) { existing = favorite; }
        });
        if (existing) {
            BuroStorage.remove('favorites', existing.id, function () {
                state.favorites = state.favorites.filter(function (favorite) { return favorite.id !== existing.id; });
                if (!document.body.classList.contains('playing')) { render(); }
                showToast(t('favoriteRemoved'), false); if (completed) { completed(false); }
            }, function (error) { showToast(friendlyError(error), true); });
            return;
        }
        row = { id: BuroDomain.id('favorite', profileId + ':' + itemId), profileId: profileId, itemId: itemId, createdAt: Date.now() };
        BuroStorage.put('favorites', row, function () {
            state.favorites.push(row); if (!document.body.classList.contains('playing')) { render(); }
            showToast(t('favoriteAdded'), false); if (completed) { completed(true); }
        }, function (error) { showToast(friendlyError(error), true); });
    }

    /*
      Lembretes.

      Marcados por identidade de conteúdo, não pelo id da linha: um lembrete
      existe muitas vezes para um título que ainda não está na lista, e uma nova
      importação troca todos os ids. Mesma decisão do Android.
    */
    function profileReminders() {
        var profileId = state.activeProfile && state.activeProfile.id;
        return state.reminders.filter(function (row) { return row.profileId === profileId; });
    }

    function reminderIdentityFor(item) {
        return item ? BuroDomain.contentIdentity(item) : '';
    }

    function hasReminder(item) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var identity = reminderIdentityFor(item);
        if (!profileId || !identity) { return false; }
        return state.reminders.some(function (row) {
            return row.profileId === profileId && row.identity === identity;
        });
    }

    function toggleReminderTarget(item, options) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var identity = reminderIdentityFor(item);
        var existing = null;
        var row;
        options = options || {};
        if (!profileId || !identity) { return; }
        state.reminders.forEach(function (reminder) {
            if (reminder.profileId === profileId && reminder.identity === identity) { existing = reminder; }
        });
        if (existing) {
            BuroStorage.remove('reminders', existing.id, function () {
                state.reminders = state.reminders.filter(function (reminder) {
                    return reminder.id !== existing.id;
                });
                render();
                showToast(t('reminderRemoved'), false);
            }, function (error) { showToast(friendlyError(error), true); });
            return;
        }
        try {
            row = BuroDomain.createReminder({
                profileId: profileId,
                item: item,
                /* A arte vem da memória da sessão, e mesmo assim passa pelo filtro
                   de credencial do domínio antes de ser gravada. */
                artworkUrl: options.artworkUrl || artworkMemory[item.id] || item.artworkUrl,
                /* A data de estreia raramente vem na linha do catálogo; quando a
                   tela de detalhes já a buscou, ela está no cache da sessão. Sem
                   nenhuma das duas o lembrete fica sem data, que é a forma
                   "me lembre disto" e continua perfeitamente válida. */
                releaseDate: options.releaseDate || item.releaseDate ||
                    (tmdbDetailsMemory[item.id] && tmdbDetailsMemory[item.id].releaseDate)
            });
        } catch (error) { showToast(friendlyError(error), true); return; }
        BuroStorage.put('reminders', row, function () {
            state.reminders.push(row);
            render();
            showToast(t('reminderAdded'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function toggleReminder(itemId) {
        var found = findItemAndSource(itemId);
        toggleReminderTarget(found && found.item);
    }

    function toggleSubscriptionReminder() {
        var data = state.section === 'SUBSCRIPTIONS' && state.screenData;
        var item = subscriptionReminderItem(data);
        var details = data && data.selection && data.selection.details;
        var selected = data && data.selected;
        if (!item || !selected) { return; }
        toggleReminderTarget(item, {
            artworkUrl: (details && details.posterUrl) || selected.posterUrl,
            releaseDate: selected.releaseDate || (details && details.releaseDate)
        });
    }

    function openReminderItem(itemId) {
        var found = findItemAndSource(itemId);
        if (!reminderItemCanOpen(found.item) || !itemVisible(found.item)) {
            showToast(t('unavailable'), true); return;
        }
        if (found.item.contentType === 'MOVIE') { openMovieDetails(itemId, 'REMINDERS'); return; }
        if (found.item.contentType === 'SERIES') { openSeriesById(itemId, 'REMINDERS'); return; }
        showToast(t('unavailable'), true);
    }

    function removeReminderById(reminderId) {
        BuroStorage.remove('reminders', reminderId, function () {
            state.reminders = state.reminders.filter(function (reminder) {
                return reminder.id !== reminderId;
            });
            render();
            showToast(t('reminderRemoved'), false);
        }, function (error) { showToast(friendlyError(error), true); });
    }

    function togglePlayerFavorite() {
        var playback = currentPlayback;
        if (!playback || playback.offline || !findItemAndSource(playback.itemId).item) { return false; }
        toggleFavorite(playback.itemId, function () {
            if (currentPlayback === playback) { refreshPlayerContextLabels(); showPlayerControls(); }
        });
        return true;
    }

    function pinMessage(message, isError) {
        var element = document.getElementById('pin-message');
        if (!element) { showToast(message, isError); return; }
        element.textContent = message;
        element.className = isError ? 'form-message error' : 'form-message success';
    }

    function verifyExistingPin(candidate, success) {
        if (!state.preferences.parentalPin) { success(); return; }
        BuroGuard.matches(candidate, state.preferences.parentalPin, function (matches) {
            if (!matches) { pinMessage(t('wrongPin'), true); return; }
            success();
        }, function (error) { pinMessage(friendlyError(error), true); });
    }

    function saveParentalPin() {
        var currentInput = document.getElementById('current-pin');
        var nextInput = document.getElementById('new-pin');
        var current = currentInput ? currentInput.value : '';
        var next = nextInput ? nextInput.value : '';
        if (!BuroGuard.validPin(next)) { pinMessage(t('pinFormat'), true); return; }
        verifyExistingPin(current, function () {
            BuroGuard.createPin(next, function (record) {
                state.preferences.parentalPin = record;
                state.preferences.lockAdultCategories = true;
                savePreferences();
                state.unlockedCategoryIds = {};
                pinMessage(t('pinSaved'), false);
            }, function (error) { pinMessage(friendlyError(error), true); });
        });
    }

    function clearParentalPin() {
        var currentInput = document.getElementById('current-pin');
        verifyExistingPin(currentInput ? currentInput.value : '', function () {
            state.preferences.parentalPin = null;
            state.preferences.lockedCategoryIds = [];
            state.preferences.lockAdultCategories = true;
            state.unlockedCategoryIds = {};
            savePreferences();
            goBack();
            showToast(t('pinRemoved'), false);
        });
    }

    function unlockPendingCategory() {
        var category = state.screenData && state.screenData.category;
        var input = document.getElementById('unlock-pin');
        if (!category || !input) { return; }
        BuroGuard.matches(input.value, state.preferences.parentalPin, function (matches) {
            var previous;
            if (!matches) { pinMessage(t('wrongPin'), true); return; }
            state.unlockedCategoryIds[category.id] = true;
            previous = state.backStack.pop();
            if (previous) {
                state.screen = previous.screen; state.section = previous.section; state.screenData = previous.data;
            } else { state.screen = 'SHELL'; state.screenData = null; }
            openCategory(category.id);
        }, function (error) { pinMessage(friendlyError(error), true); });
    }

    function selectPreference(property, value, values) {
        if (values.indexOf(value) < 0) { return; }
        state.preferences[property] = value;
        savePreferences(); render();
    }

    function activate(element) {
        var action = element.getAttribute('data-action');
        var id = element.getAttribute('data-id');
        var property;
        var profile;
        var source;
        if (!action && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA')) { element.focus(); return; }
        if (action === 'select-language') {
            state.preferences.language = element.getAttribute('data-language');
            state.preferences.languageSelected = true;
            savePreferences();
            initializeData();
        } else if (action === 'legal-accept') { acceptLegal(); }
        else if (action === 'profile-form') {
            if (state.profiles.length >= MAX_PROFILES) { showToast(t('profileLimit'), true); }
            else { pushScreen('PROFILE_FORM', { avatarKey: 'gold', sourceId: null, kids: false, photoDataUrl: null }); }
        }
        else if (action === 'profile-edit') {
            profile = state.profiles.filter(function (row) { return row.id === id; })[0];
            if (profile) {
                pushScreen('PROFILE_FORM', {
                    editingId: profile.id,
                    profileName: profile.name,
                    avatarKey: profile.avatarKey || 'gold',
                    sourceId: profile.sourceId || null,
                    kids: Boolean(profile.isKids),
                    photoDataUrl: safeProfilePhoto(profile.photoDataUrl),
                    confirmDelete: false
                });
            }
        }
        else if (action === 'kids-toggle') {
            profileDraft().kids = !state.screenData.kids; state.screenData.confirmDelete = false; render();
        } else if (action === 'profile-avatar') {
            profileDraft().avatarKey = element.getAttribute('data-avatar'); state.screenData.confirmDelete = false; render();
        } else if (action === 'profile-source') {
            profileDraft().sourceId = id || null; state.screenData.confirmDelete = false; render();
        } else if (action === 'profile-photo-choose') { openProfilePhotoPicker();
        } else if (action === 'profile-photo-remove') {
            profileDraft().photoDataUrl = null; state.screenData.confirmDelete = false; render();
        } else if (action === 'profile-photo-retry') { loadProfilePhotoImages();
        } else if (action === 'profile-photo-select') { selectProfilePhoto(element.getAttribute('data-key'));
        } else if (action === 'profile-save') { saveProfile(); }
        else if (action === 'profile-delete') { deleteProfile(); }
        else if (action === 'select-profile') {
            profile = state.profiles.filter(function (row) { return row.id === id; })[0];
            if (profile) {
                state.activeProfile = profile; state.preferences.activeProfileId = profile.id; refreshActiveReferences(); savePreferences();
                resetLibraryView();
                clearTmdbDetails();
                state.screen = 'SHELL'; state.section = 'HOME'; state.screenData = null; render();
                retryPendingSharedTitle();
                startActiveSourceHydration(false);
            }
        } else if (action === 'section') {
            if (element.getAttribute('data-section') !== 'DOWNLOADS') { clearDownloadSearchDebounce(); }
            if (state.section === 'HOME' && element.getAttribute('data-section') !== 'HOME') {
                BuroHeroEnrichment.cancel();
            }
            homeRequestId += 1;
            discoverRequestId += 1;
            catalogueRequestId += 1;
            searchRequestId += 1;
            if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); subscriptionRequest = null; }
            discoverReturnData = null;
            state.section = element.getAttribute('data-section'); state.screen = 'SHELL'; state.screenData = null;
            state.preferences.section = state.section; savePreferences(); focusIndex = 0; render();
        } else if (action === 'library-filter') {
            property = element.getAttribute('data-section');
            if (libraryFilters[property] != null) {
                libraryFilters[property] = element.getAttribute('data-kind') || 'ALL'; libraryPages[property] = 0; render();
            }
        } else if (action === 'library-page-next') { changeLibraryPage(element, 1);
        } else if (action === 'library-page-previous') { changeLibraryPage(element, -1);
        } else if (action === 'download-filter') {
            property = element.getAttribute('data-kind');
            downloadFilter = ['ALL', 'MOVIE', 'EPISODE'].indexOf(property) >= 0 ? property : 'ALL'; downloadPage = 0; render();
        } else if (action === 'download-compact') { downloadCompact = !downloadCompact; render();
        } else if (action === 'download-page-next') { changeDownloadPage(1);
        } else if (action === 'download-page-previous') { changeDownloadPage(-1);
        } else if (action === 'discover-retry') {
            state.screenData = null; focusIndex = 0; render();
        } else if (action === 'discover-again') { dealDiscoverAgain();
        } else if (action === 'discover-skip') { decideDiscover('SKIPPED');
        } else if (action === 'discover-keep') { decideDiscover('KEPT');
        } else if (action === 'discover-details') { openDiscoverDetails(id);
        } else if (action === 'shared-retry') { retryPendingSharedTitle();
        } else if (action === 'shared-dismiss') { sharedTitleNoticeVisible = false; render();
        } else if (action === 'home-retry') {
            state.screenData = null; focusIndex = 0; render();
        } else if (action === 'catalogue-retry') { retryCatalogueRequest();
        } else if (action === 'series-details-retry') { openSeriesById(id, state.screenData && state.screenData.originSection);
        } else if (action === 'demo-story') {
            state.screenData = { kind: 'demo-story' }; focusIndex = 0; render();
        } else if (action === 'source-add') { pushScreen('SOURCE_CHOICE'); }
        else if (action === 'source-usb-m3u') { openUsbM3uPicker(); }
        else if (action === 'source-usb-m3u-retry') { loadUsbM3uFiles(); }
        else if (action === 'source-usb-m3u-select') { selectUsbM3u(element.getAttribute('data-key')); }
        else if (action === 'source-form') { pushScreen('SOURCE_FORM', { type: element.getAttribute('data-type') }); }
        else if (action === 'source-connect') { connectSource(element.getAttribute('data-type')); }
        else if (action === 'source-manage') { pushScreen('SOURCE_MANAGE', { sourceId: id, confirmDelete: false }); }
        else if (action === 'source-refresh') { refreshSource(); }
        else if (action === 'catalogue-sync-cancel') {
            if (BuroCatalogueSync.cancel()) { showToast(t('catalogueSyncCancelledToast'), false); }
        }
        else if (action === 'catalogue-sync-resume') {
            source = state.sources.filter(function (row) { return row.id === id; })[0];
            if (source) { startXtreamHydration(source, false); showToast(t('catalogueSyncStarted'), false); }
        }
        else if (action === 'source-rename') { renameSource(); }
        else if (action === 'source-delete') { deleteSource(); }
        else if (action === 'select-source') {
            source = state.sources.filter(function (row) { return row.id === id; })[0];
            if (source) {
                assignSourceToProfile(source); render(); retryPendingSharedTitle();
                startXtreamHydration(source, false); showToast(source.name, false);
            }
        } else if (action === 'catalogue-layout') {
            state.screenData.catalogueLayout = cycleValue(CATALOGUE_LAYOUTS, state.screenData.catalogueLayout || 'poster');
            render();
        } else if (action === 'catalogue-sort') {
            state.screenData.catalogueFilter.sort = cycleValue(CATALOGUE_SORTS, state.screenData.catalogueFilter.sort || 'provider');
            state.screenData.cataloguePage = 0;
            render();
        } else if (action === 'catalogue-genre') {
            state.screenData.catalogueFilter.genre = cycleValue([null].concat(state.screenData.availableGenres || []), state.screenData.catalogueFilter.genre || null);
            state.screenData.cataloguePage = 0;
            render();
        } else if (action === 'catalogue-year') {
            state.screenData.catalogueFilter.year = cycleValue([null].concat(state.screenData.availableYears || []),
                state.screenData.catalogueFilter.year == null ? null : state.screenData.catalogueFilter.year);
            state.screenData.cataloguePage = 0;
            render();
        } else if (action === 'catalogue-reset') {
            state.screenData.catalogueFilter = { genre: null, year: null, sort: 'provider' };
            state.screenData.cataloguePage = 0;
            render();
        } else if (action === 'category-refresh') { refreshOpenCategory();
        } else if (action === 'category-page-next') { changeCategoryPage(1);
        } else if (action === 'category-page-previous') { changeCategoryPage(-1);
        } else if (action === 'series-season') {
            var selectedSeason = Number(element.getAttribute('data-season'));
            state.screenData.expandedSeason = Number(state.screenData.expandedSeason) === selectedSeason ? null : selectedSeason;
            state.screenData.seasonPages = state.screenData.seasonPages || {};
            state.screenData.seasonPages[selectedSeason] = 0;
            render();
        } else if (action === 'series-page-next') { changeSeriesPage(element, 1);
        } else if (action === 'series-page-previous') { changeSeriesPage(element, -1);
        } else if (action === 'series-download-all') { openBulkDownloadConfirm(null);
        } else if (action === 'series-download-season') {
            openBulkDownloadConfirm(Number(element.getAttribute('data-season')));
        } else if (action === 'bulk-download-confirm') { confirmBulkDownload();
        } else if (action === 'category') { openCategory(id); }
        else if (action === 'play') { playItem(id); }
        else if (action === 'resume-continue') { chooseResume(true); }
        else if (action === 'resume-restart') { chooseResume(false); }
        else if (action === 'movie-details') { openMovieDetails(id); }
        else if (action === 'series-details') { openSeriesById(id); }
        else if (action === 'live-details') { openLiveDetails(id); }
        else if (action === 'trailer') { openTrailer(id); }
        else if (action === 'person') { openPerson(element.getAttribute('data-name')); }
        else if (action === 'person-local') { openPersonLocal(id); }
        else if (action === 'person-credit') { openPersonCredit(element); }
        else if (action === 'share') { openTitleShare(id); }
        else if (action === 'subscription-filter') { loadSubscriptions(element.getAttribute('data-kind')); }
        else if (action === 'subscription-region') {
            state.preferences.tmdbRegion = BuroTmdb.safeRegion(element.getAttribute('data-region'));
            savePreferences(); loadSubscriptions(state.screenData.filter);
        }
        else if (action === 'subscription-retry') { loadSubscriptions(state.screenData.filter, true); }
        else if (action === 'subscription-title') {
            var subscriptionTitle = findSubscriptionTitle(element.getAttribute('data-key'));
            if (subscriptionTitle) { selectSubscriptionTitle(subscriptionTitle); }
        }
        else if (action === 'subscription-expand') { expandSubscriptionService(element.getAttribute('data-provider')); }
        else if (action === 'subscription-expanded-back') { closeExpandedSubscription(); }
        else if (action === 'subscription-back') { backFromSubscriptionSelection(); }
        else if (action === 'subscription-local') { openSubscriptionLocal(id); }
        else if (action === 'subscription-offer') { openExternalOffer(element.getAttribute('data-url')); }
        else if (action === 'subscription-trailer') { openSubscriptionTrailer(); }
        else if (action === 'subscription-reminder') { toggleSubscriptionReminder(); }
        else if (action === 'favorite') { toggleFavorite(id); }
        else if (action === 'reminder') { toggleReminder(id); }
        else if (action === 'reminder-open') { openReminderItem(id); }
        else if (action === 'reminder-remove') { removeReminderById(id); }
        else if (action === 'download') { downloadItem(id); }
        else if (action === 'download-retry') {
            BuroDownloads.remove(element.getAttribute('data-key'));
            downloadItem(id);
        }
        else if (action === 'download-play') { playCompletedDownload(id); }
        else if (action === 'download-pause') { BuroDownloads.pause(id); }
        else if (action === 'download-resume') { BuroDownloads.resume(id); }
        else if (action === 'download-cancel') { BuroDownloads.cancel(id); }
        else if (action === 'download-remove') { BuroDownloads.remove(id); render(); }
        else if (action === 'licence-activate') { pushScreen('LICENCE'); }
        else if (action === 'licence-redeem') { redeemLicenceKey(); }
        else if (action === 'search-run') { runSearch(); }
        else if (action === 'search-next') { changeSearchPage(1); }
        else if (action === 'search-previous') { changeSearchPage(-1); }
        else if (action === 'search-retry') { changeSearchPage(0); }
        else if (action === 'toggle-setting') {
            property = element.getAttribute('data-property'); state.preferences[property] = !state.preferences[property]; savePreferences(); render();
        } else if (action === 'tmdb-settings') { pushScreen('TMDB_SETTINGS', {}); }
        else if (action === 'tmdb-guide') { openTmdbGuide(); }
        else if (action === 'tmdb-guide-open') { openExternalOffer(TMDB_SIGNUP_URL); }
        else if (action === 'tmdb-save') { saveTmdbKey(element.getAttribute('data-scope')); }
        else if (action === 'tmdb-clear') { clearTmdbKey(element.getAttribute('data-scope')); }
        else if (action === 'critics-settings') { pushScreen('CRITICS_SETTINGS', {}); }
        else if (action === 'critics-guide') { openCriticsGuide(); }
        else if (action === 'critics-guide-open') { openOfficialCriticsSite(); }
        else if (action === 'critics-save') { saveCriticsKey(); }
        else if (action === 'critics-clear') { clearCriticsKey(); }
        else if (action === 'storage-settings') { pushScreen('STORAGE_SETTINGS', {}); measureStorage(); }
        else if (action === 'storage-measure') { measureStorage(); }
        else if (action === 'storage-clear') { clearStoredCatalogue(); }
        else if (action === 'notifications') { pushScreen('NOTIFICATIONS', {}); }
        else if (action === 'notifications-read') { markNotificationsRead(); }
        else if (action === 'notification-remove') { removeNotification(element.getAttribute('data-id')); }
        else if (action === 'catalogue-scope-genre') { cycleCatalogueScope('genre'); }
        else if (action === 'catalogue-scope-service') { cycleCatalogueScope('service'); }
        else if (action === 'catalogue-scope-reset') { resetCatalogueScope(); }
        else if (action === 'parental-form') { pushScreen('PARENTAL_FORM'); }
        else if (action === 'parental-save') { saveParentalPin(); }
        else if (action === 'parental-clear') { clearParentalPin(); }
        else if (action === 'pin-unlock') { unlockPendingCategory(); }
        else if (action === 'toggle-adult-lock') {
            if (!state.preferences.parentalPin) { showToast(t('pinRequired'), true); }
            else { state.preferences.lockAdultCategories = !state.preferences.lockAdultCategories; savePreferences(); render(); }
        } else if (action === 'category-settings') { pushScreen('CATEGORY_SETTINGS', { kind: 'category-settings', page: 0 }); }
        else if (action === 'category-settings-page-next') { changeCategorySettingsPage(1); }
        else if (action === 'category-settings-page-previous') { changeCategorySettingsPage(-1); }
        else if (action === 'category-hidden') {
            state.preferences.hiddenCategoryIds = BuroGuard.toggle(state.preferences.hiddenCategoryIds, id); savePreferences(); render();
        } else if (action === 'category-locked') {
            if (state.preferences.parentalPin) {
                state.preferences.lockedCategoryIds = BuroGuard.toggle(state.preferences.lockedCategoryIds, id);
                state.unlockedCategoryIds[id] = false; savePreferences(); render();
            }
        } else if (action === 'subtitle-size-select') {
            selectPreference('subtitleSize', element.getAttribute('data-value'), ['small', 'medium', 'large', 'huge']);
        }
        else if (action === 'subtitle-colour-select') {
            selectPreference('subtitleColour', element.getAttribute('data-value'), ['white', 'yellow', 'grey', 'green', 'cyan']);
        }
        else if (action === 'language') {
            state.preferences.language = element.getAttribute('data-language'); savePreferences(); render();
        } else if (action === 'retry') { initializeData(); }
        else if (action === 'back') { goBack(); }
        else if (action === 'unavailable') { showToast(t('unavailable'), false); }
    }

    function onKeyDown(event) {
        var K = BuroKeys.CODES;
        var active = document.activeElement;
        if (BuroTrailer.isOpen()) {
            if (event.keyCode === K.RETURN || event.keyCode === K.STOP) { BuroTrailer.close(); }
            else if (event.keyCode === K.ENTER || event.keyCode === K.PLAY_PAUSE ||
                    event.keyCode === K.PLAY || event.keyCode === K.PAUSE) { BuroTrailer.togglePlayback(); }
            else if (event.keyCode === K.LEFT || event.keyCode === K.REWIND) { BuroTrailer.seekBy(-10000); }
            else if (event.keyCode === K.RIGHT || event.keyCode === K.FAST_FORWARD) { BuroTrailer.seekBy(10000); }
            else if (event.keyCode === K.UP) { BuroTrailer.toggleMute(); }
            else { return; }
            event.preventDefault();
            return;
        }
        if (document.body.classList.contains('playing')) {
            if (playerErrorActive) {
                if (handlePlayerErrorKey(event.keyCode)) { event.preventDefault(); }
                return;
            }
            showPlayerControls();
            if (playerMenuState) {
                if (event.keyCode === K.RETURN) { closePlayerMenu(); }
                else if (event.keyCode === K.LEFT || event.keyCode === K.UP) { movePlayerMenu(-1); }
                else if (event.keyCode === K.RIGHT || event.keyCode === K.DOWN) { movePlayerMenu(1); }
                else if (event.keyCode === K.ENTER) { choosePlayerMenuOption(); }
                else { return; }
                event.preventDefault();
                return;
            }
            if (playerControlsLocked) {
                if (event.keyCode === K.RETURN) { stopPlayback(); }
                else if (event.keyCode === K.ENTER) { beginPlayerEnterPress(); }
                event.preventDefault();
                return;
            }
            if (event.keyCode === K.RETURN || event.keyCode === K.STOP) { stopPlayback(); event.preventDefault(); }
            else if (event.keyCode === K.ENTER) { beginPlayerEnterPress(); event.preventDefault(); }
            else if (event.keyCode === K.PLAY_PAUSE || event.keyCode === K.PLAY ||
                    event.keyCode === K.PAUSE) { BuroPlayer.togglePause(); event.preventDefault(); }
            else if (event.keyCode === K.LEFT || event.keyCode === K.REWIND) { BuroPlayer.seekBy(-10000); event.preventDefault(); }
            else if (event.keyCode === K.RIGHT || event.keyCode === K.FAST_FORWARD) { BuroPlayer.seekBy(30000); event.preventDefault(); }
            else if (event.keyCode === K.UP) { openPlayerMenu('AUDIO'); event.preventDefault(); }
            else if (event.keyCode === K.DOWN) { openPlayerMenu('TEXT'); event.preventDefault(); }
            else if (event.keyCode === K.RED) { if (togglePlayerFavorite()) { event.preventDefault(); } }
            else if (event.keyCode === K.GREEN) {
                if (currentPlayback && currentPlayback.contentType === 'LIVE') { openPlayerMenu('GUIDE'); }
                else { openPlayerMenu('TEXT'); }
                event.preventDefault();
            }
            else if (event.keyCode === K.YELLOW) { openPlayerMenu('SPEED'); event.preventDefault(); }
            else if (event.keyCode === K.BLUE) { cyclePlayerDisplayMode(); event.preventDefault(); }
            return;
        }
        if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA') &&
                event.keyCode !== K.RETURN && event.keyCode !== K.UP && event.keyCode !== K.DOWN) { return; }
        if (event.keyCode === K.LEFT || event.keyCode === K.UP ||
                event.keyCode === K.RIGHT || event.keyCode === K.DOWN) {
            moveDirectional(event.keyCode); event.preventDefault();
        }
        else if (event.keyCode === K.YELLOW) {
            if (focusables[focusIndex] && ['play', 'movie-details', 'series-details', 'live-details'].indexOf(
                    focusables[focusIndex].getAttribute('data-action')) >= 0) {
                toggleFavorite(focusables[focusIndex].getAttribute('data-id'));
            }
            event.preventDefault();
        }
        else if (event.keyCode === K.ENTER) { if (focusables[focusIndex]) { activate(focusables[focusIndex]); } event.preventDefault(); }
        else if (event.keyCode === K.RETURN) { if (active && active.blur) { active.blur(); } goBack(); event.preventDefault(); }
    }

    function onKeyUp(event) {
        if (event.keyCode !== BuroKeys.CODES.ENTER || !playerEnterPressActive) { return; }
        finishPlayerEnterPress();
        event.preventDefault();
    }

    function init() {
        if (initialized) { return; }
        initialized = true;
        root = document.getElementById('app');
        toast = document.getElementById('toast');
        overlay = document.getElementById('player-overlay');
        playerStatus = document.getElementById('player-status');
        playerTitle = document.getElementById('player-title');
        playerProgramme = document.getElementById('player-programme');
        playerProgress = document.getElementById('player-progress');
        playerTimeline = document.getElementById('player-timeline');
        playerElapsed = document.getElementById('player-elapsed');
        playerDuration = document.getElementById('player-duration');
        playerAudioLabel = document.getElementById('player-audio-label');
        playerSubtitleLabel = document.getElementById('player-subtitle-label');
        playerFavoriteLabel = document.getElementById('player-favorite-label');
        playerGuideLabel = document.getElementById('player-guide-label');
        playerSubtitleCue = document.getElementById('player-subtitle-cue');
        playerSubtitleText = document.getElementById('player-subtitle-text');
        playerSpeedLabel = document.getElementById('player-speed-label');
        playerAspectLabel = document.getElementById('player-aspect-label');
        playerLockLabel = document.getElementById('player-lock-label');
        playerLockPanel = document.getElementById('player-lock-panel');
        playerLockTitle = document.getElementById('player-lock-title');
        playerLockHint = document.getElementById('player-lock-hint');
        playerReturnLabel = document.getElementById('player-return-label');
        playerRemoteActions = document.querySelector('.player-remote-actions');
        playerMenu = document.getElementById('player-menu');
        playerMenuTitle = document.getElementById('player-menu-title');
        playerMenuOptions = document.getElementById('player-menu-options');
        playerMenuHint = document.getElementById('player-menu-hint');
        playerWaiting = document.getElementById('player-waiting');
        playerWaitingLabel = document.getElementById('player-waiting-label');
        playerErrorPanel = document.getElementById('player-error-panel');
        playerErrorTitle = document.getElementById('player-error-title');
        playerErrorMessage = document.getElementById('player-error-message');
        playerErrorRetry = document.getElementById('player-error-retry');
        playerErrorBack = document.getElementById('player-error-back');
        playerErrorRetry.onclick = retryPlayback;
        playerErrorBack.onclick = stopPlayback;
        BuroTrailer.init();
        BuroKeys.registerMediaKeys();
        BuroPlayer.setListeners({
            onStatus: function (code, value) {
                var message = playerStatusText(code, value);
                playerStatus.textContent = message;
                playerStatus.classList.remove('error');
                if (code === 'PREPARING' || code === 'RESUMING' || code === 'BUFFERING') {
                    playerWaiting.hidden = false;
                    playerWaitingLabel.textContent = message;
                    overlay.setAttribute('aria-busy', 'true');
                } else {
                    playerWaiting.hidden = true;
                    overlay.setAttribute('aria-busy', 'false');
                }
                showPlayerControls();
            },
            onError: function (error) { playerStatus.classList.add('error'); playbackFailed(error); },
            onTime: function (position, duration) {
                playerWaiting.hidden = true;
                overlay.setAttribute('aria-busy', 'false');
                updatePlaybackTime(position, duration);
            },
            onSubtitle: showPlayerSubtitle,
            onComplete: function () {
                persistProgress(true); clearPlayerError(); closePlayerMenu();
                document.body.classList.remove('playing'); root.removeAttribute('aria-hidden'); overlay.hidden = true;
            }
        });
        /*
          A lista de downloads é redesenhada a cada mudança de estado, e a ação
          "Baixar" aparece ou some junto com o pendrive. Sem isto o usuário
          teria de sair e voltar da seção para ver o próprio progresso.
        */
        BuroDownloads.watch(function () {
            if (state.screen === 'SHELL' && (state.section === 'DOWNLOADS' ||
                    (state.screenData && (state.screenData.kind === 'movie' || state.screenData.kind === 'series')))) {
                followFocusedDownloadOnRender = state.section === 'DOWNLOADS';
                render();
            }
        });
        BuroCatalogueSync.watch(catalogueSyncChanged);
        if (BuroUsb.available()) {
            BuroUsb.watch(function (mounted) {
                if (!mounted.length) { showToast(t('usbRemoved'), true); }
                /* A tela de detalhes precisa reavaliar se mostra "Baixar". */
                if (state.ready) { render(); }
            });
        }

        /*
          A licença é conferida em segundo plano, sem segurar o boot.

          Enquanto a chave pública do servidor não estiver embutida, nada disto
          roda: sem ela não há como verificar uma assinatura, e um app que
          "valida" sem verificar dá uma falsa sensação de proteção.
        */
        if (BuroLicense.configured() && BuroIdentity.available()) {
            /*
              Valida primeiro, registra só se esta TV for desconhecida.

              O servidor responde 404 `not_registered` a um dispositivo que
              nunca se apresentou, e é esse — e apenas esse — caso que merece
              um registro: ele cria o período de teste, e o usuário novo entra
              no app sem precisar fazer nada.

              Distinguir pelo status importa. Registrar diante de qualquer
              falha significaria tentar isso também quando a internet da TV
              caiu ou o servidor está fora do ar, gastando duas requisições
              para nada e arriscando registros repetidos por um erro passageiro.
            */
            BuroLicense.validate(function () {
                if (state.ready) { render(); }
            }, function (error) {
                if (!error || error.status !== 404) { return; }
                BuroLicense.register(function () {
                    if (state.ready) { render(); }
                }, function () {
                    /*
                      Silencioso de propósito: o app abre normalmente e a tela
                      de licença mostra o estado. Um alerta no primeiro boot,
                      antes de o usuário ver qualquer coisa, transformaria uma
                      falha de rede numa parede.
                    */
                });
            });
        }

        document.addEventListener('keydown', onKeyDown);
        document.addEventListener('keyup', onKeyUp);
        window.addEventListener('appcontrol', receiveRequestedAppControl);
        /* Pedido frio: no primeiro launch o evento pode ter ocorrido antes de o
           JavaScript registrar o listener, por isso consultamos o app atual. */
        receiveRequestedAppControl();
        initializeData();
    }

    return {
        init: init,
        render: render,
        state: state,
        _activate: activate,
        _onKeyDown: onKeyDown,
        _onKeyUp: onKeyUp,
        _playbackFailed: playbackFailed,
        _friendlyError: friendlyError,
        _receiveRequestedAppControl: receiveRequestedAppControl,
        _resolvePendingSharedTitle: resolvePendingSharedTitle,
        _pendingSharedTitle: function () { return pendingSharedTitle; },
        /*
          Só para os testes. Um vazamento de memória não tem sintoma
          observável de fora — a TV apenas fica lenta depois de horas — então
          o teto é verificado contando as entradas.
        */
        _rememberArtwork: rememberArtwork,
        _rememberDetailBackdrop: rememberDetailBackdrop,
        _openCategory: openCategory,
        _artworkFor: function (itemId) { return artworkMemory[itemId]; },
        _cacheSizes: function () {
            return {
                artwork: Object.keys(artworkMemory).length,
                artworkOrder: artworkOrder.length,
                detailBackdrop: Object.keys(detailBackdropMemory).length,
                detailBackdropOrder: detailBackdropOrder.length
            };
        }
    };
}());

window.addEventListener('load', BuroApp.init);
