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
    var playerNextPanel;
    var playerNextTitle;
    var playerNextCountdown;
    var nextEpisodeTimer = null;
    var nextEpisodeTarget = null;
    var playerSleepLabel;
    var sleepTimerTicker = null;
    var sleepTimerEndsAt = null;
    var sleepTimerMinutes = null;
    var sleepTimerAtEpisodeEnd = false;
    var playerSubtitleDelayTimer = null;
    var playerSkipIntroButton;
    var skipIntroVisible = false;
    var previewTimer = null;
    var previewItemId = null;
    var previewPendingId = null;
    var previewFrame;
    var subtitleOffsetMs = 0;
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
    var artworkCacheRenderTimer = null;
    var seriesDetailsMemory = {};
    var tmdbDetailsMemory = {};
    var tmdbDetailOrder = [];
    var tmdbTitleRequest = null;
    var tmdbSimilarRequest = null;
    var criticsRequest = null;
    /* O relógio do pareamento, para poder parar quando a tela sai. */
    var pairingRequest = null;
    var homeArtworkRedrawTimer = null;
    var clockTimer = null;
    var bootSweepTimer = null;
    var bootSweepDone = null;
    var homeCache = null;
    var discoverCache = null;
    var subscriptionIndex = null;
    var subscriptionIndexAt = 0;
    /* Capa genérica detectada no catálogo Xtream ativo. Só o conjunto final
       sobrevive à varredura; o mapa grande de contagens é descartado. */
    var placeholderArtworkSourceId = null;
    var placeholderArtworkUrls = Object.create(null);
    /* Cada pedido de página da prateleira ganha um número: uma resposta que
       chega depois de o filtro mudar não pode substituir a atual. */
    var catalogueShelfRequestId = 0;
    var HOME_ARTWORK_REDRAW_MILLIS = 500;
    /* Quantas categorias a Home busca de uma vez. Dezenas de requisições ao
       abrir o app deixariam a TV sem responder ao controle. */
    var HOME_ARTWORK_CATEGORY_LIMIT = 6;
    var tmdbPersonRequest = null;
    var personReturnData = null;
    var subscriptionReturnData = null;
    var similarTitleReturnStack = [];
    var subscriptionRequest = null;
    var subscriptionRequestId = 0;
    var catalogueProviderDirectories = {
        MOVIE: { identity: '', rows: [], loading: false, failed: false, request: null },
        SERIES: { identity: '', rows: [], loading: false, failed: false, request: null }
    };
    var homeStreamingShelves = [];
    var homeStreamingIdentity = '';
    var homeStreamingRequest = null;
    var homeStreamingLoading = false;
    var homeStreamingFailed = false;
    var homeStreamingWaiters = [];
    var homeLocalReturnData = null;
    var pendingSharedTitle = null;
    var sharedTitleNoticeVisible = false;
    var sharedTitleResolving = false;
    var sharedTitleNeedsResolution = false;
    var sharedTitleResolveId = 0;
    var appControlReceiveId = 0;
    var appControlReceiveTimer = null;
    var lastRequestedAppControlUri = '';
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
    var historySearchTimer = null;
    var licenceKeyTimer = null;
    var licenceKeyRequestId = 0;
    var sourceRefreshRequestId = 0;
    /* Estado transitório do atalho da barra superior para M3U/Stalker. Não entra
       em preferências nem no IndexedDB; serve apenas para manter o mesmo botão
       ocupado enquanto a fotografia transacional da fonte é substituída. */
    var topbarSourceRefresh = {
        sourceId: null,
        refreshing: false,
        refreshError: null,
        refreshSuccess: false
    };
    var SEARCH_PAGE_SIZE = 40;
    var SEARCH_DEBOUNCE_MILLIS = 300;
    var LICENCE_KEY_DEBOUNCE_MILLIS = 600;
    /*
      Quantos titulos uma categoria aberta mostra por pagina.

      Eram duzentos, herdados de uma leitura equivocada da paridade com o
      Android: la 200 e o limite da *consulta ao banco*
      (`CatalogRepository.limit`, `ChannelDao.search`) — quantas linhas sao
      buscadas, nao quantos cartoes sao desenhados. Aqui viraram duzentos
      cartoes montados de uma vez, que e exatamente o peso que levou a
      prateleira a carregar em blocos.

      Quarenta e dois sao seis fileiras de sete: enchem a area visivel com folga
      para rolar e deixam a pagina seguinte a um toque. Multiplo da fileira pelo
      mesmo motivo do bloco da prateleira — uma fileira pela metade faz a pagina
      parecer menor do que e.

      A paridade com o Android continua, e do que importa: nenhuma das duas
      plataformas prende o controle enquanto o DOM cresce.
    */
    /* Dez segundos entre um episodio e o proximo: o bastante para ler o titulo
       e desistir, curto o bastante para nao virar espera. */
    /* Uma Home montada ha menos disto nao e reconferida: o intervalo cobre a
       distancia entre a abertura monta-la e a SHELL aparecer, e nada mais.
       Trocar de fonte ou varrer o catalogo invalida o cache por outro
       caminho, entao este numero nao governa a validade — so a repeticao. */
    var HOME_CACHE_TRUST_MILLIS = 30000;

    /*
      A janela em que pular a abertura faz sentido, e para onde pular.

      Depois de tres minutos ja nao e abertura: e a historia, e um botao ali
      convidaria a pular o inicio do episodio. Antes de trinta segundos
      tambem nao — muita serie abre com uma cena antes do tema, e oferecer o
      salto ja no primeiro quadro tiraria justamente essa cena.

      Noventa segundos e a duracao tipica de um tema de serie. Sem marcacao
      de capitulos — que nenhuma lista IPTV fornece — um salto fixo e o que
      da para oferecer honestamente, e a barra de progresso mostra para onde
      ele leva antes de a pessoa apertar.
    */
    var SKIP_INTRO_FROM_MS = 30000;
    var SKIP_INTRO_UNTIL_MS = 180000;
    var SKIP_INTRO_JUMP_MS = 90000;

    var NEXT_EPISODE_SECONDS = 10;

    var CATALOGUE_PAGE_SIZE = 42;

    /*
      Quantas linhas uma consulta ao banco traz por vez.

      Separado do tamanho da pagina de proposito: ler e barato — e uma consulta
      indexada — e desenhar nao, porque cada cartao vira DOM. Manter os dois no
      mesmo numero fazia a TV consultar o banco a cada quarenta e dois titulos
      quando a pagina encolheu, que e o oposto do que se queria.

      Duzentos e o mesmo valor que o Android usa em `CatalogRepository.limit` e
      `ChannelDao.search`, e ali sempre significou isto: linhas lidas.
    */
    var CATALOGUE_READ_SIZE = 200;
    /*
      Quantos títulos a prateleira acrescenta por vez.

      Vinte e um, que é um múltiplo dos sete cartões que cabem numa fileira: com
      dez, a segunda linha ficava pela metade e o botão de carregar mais era
      empurrado para fora da tela — a prateleira parecia menor do que é e a
      continuação ficava inalcançável. Três fileiras cheias preenchem a área
      visível e deixam o botão logo abaixo, ao alcance do D-pad.

      Ainda longe dos duzentos que travavam a TV: o que se paga aqui é uma tela,
      não um catálogo.
    */
    var CATALOGUE_BLOCK_SIZE = 21;

    /* Quantas paginas um salto atravessa. Dez porque cinco poupa pouco e
       cinquenta atravessa demais para quem esta procurando alguma coisa. */
    var CATALOGUE_PAGE_JUMP = 10;

    /*
      Quanto tempo o foco precisa ficar parado antes de a previa comecar.

      Sem espera, atravessar a lista com o D-pad abriria e fecharia um fluxo
      por canal — dezenas de aberturas por minuto. Cada uma custa banda e uma
      sessao no provedor, e ha provedores que limitam conexoes simultaneas e
      derrubam a conta por excesso.

      Um segundo e meio e o tempo de alguem parar para ler o nome do canal.
      Quem esta so passando nunca chega a abrir nada.
    */
    var PREVIEW_DELAY_MILLIS = 1500;
    var EPISODE_PAGE_SIZE = 40;
    var CATEGORY_SETTINGS_PAGE_SIZE = 40;
    var LIBRARY_PAGE_SIZE = 40;
    var FAVORITES_LIMIT = 200;
    var CONTINUE_WATCHING_LIMIT = 20;
    var HISTORY_LIMIT = 60;
    var DOWNLOAD_PAGE_SIZE = 40;
    var DOWNLOAD_SEARCH_DEBOUNCE_MILLIS = 200;
    var HISTORY_SEARCH_DEBOUNCE_MILLIS = 200;
    var currentPlayback = null;
    var playbackRetry = BuroPlaybackRetry.create({ delayMs: 1500, maxRetries: 1 });
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
    var APP_VERSION_FALLBACK = '3.7.0';

    /*
      Quanto tempo o teste fica visivel mesmo quando acaba antes.

      O suficiente para se ler como trabalho a acontecer, curto o bastante para
      ninguem esperar sem motivo.
    */
    var DIAGNOSTICS_MINIMUM_MS = 900;

    /* As leituras, na ordem em que aparecem, para o ecra as mostrar antes de as ter. */
    var DIAGNOSTICS_ROWS = ['download', 'ping', 'loss', 'catalogue', 'link'];
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
    var historyQuery = '';
    var progressMutationPending = false;
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

    /*
      As seções da barra lateral.

      Assinaturas aparece sempre, como no aplicativo do Windows, e não só quando
      há chave do TMDb. Sumir de uma instalação e existir na outra fazia a mesma
      função parecer defeito; quando falta a chave, o destino continua alcançável
      e diz que precisa dela — a mesma decisão do seletor de Serviço.
    */
    function navigationEntries() {
        var entries = NAVIGATION.slice();
        entries.splice(9, 0, { section: 'SUBSCRIPTIONS', label: 'subscriptions', icon: '$' });
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

    function placeholderArtworkKey(value) { return '$' + value; }

    function isPlaceholderArtwork(item, value) {
        var url = safeArtworkUrl(value);
        return Boolean(item && url && item.sourceId === placeholderArtworkSourceId &&
            placeholderArtworkUrls[placeholderArtworkKey(url)]);
    }

    function usableArtworkUrl(item, value) {
        var url = safeArtworkUrl(value);
        return url && !isPlaceholderArtwork(item, url) ? url : null;
    }

    function safeProviderLogoUrl(value) {
        var url = BuroDomain.trim(value);
        return /^https:\/\/image\.tmdb\.org\/t\/p\/(w92|w185)\/[A-Za-z0-9._\/-]{1,240}$/.test(url) &&
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
            /* Com o cache USB ativo, toda arte pública descoberta pela
               varredura entra na fila, não só um card que chegou a ser
               desenhado. Limite, dedupe e filtro de credencial ficam no
               adapter de cache. */
            if (typeof BuroArtworkCache !== 'undefined') {
                BuroArtworkCache.remember(itemId, url);
            }
        }
    }

    function rememberArtworkMap(values, sourceId) {
        Object.keys(values || {}).forEach(function (itemId) {
            var url = values[itemId];
            if (sourceId === placeholderArtworkSourceId &&
                    placeholderArtworkUrls[placeholderArtworkKey(safeArtworkUrl(url) || '')]) { return; }
            rememberArtwork(itemId, url);
        });
    }

    function rememberM3uArtwork(entries) {
        (entries || []).forEach(function (entry) {
            if (entry && entry.item) { rememberArtwork(entry.item.id, entry.artworkUrl); }
        });
    }

    /*
      A capa de um título: a de memória primeiro, a gravada depois.

      A memória tem teto e descarte LRU, entao num catalogo grande ela guarda o
      que a varredura viu por ultimo. O `logoUrl` do item e o que sobrevive: so
      entra ali arte que passou pela peneira de credencial, e e ele que faz a
      prateleira do comeco do catalogo ter capa depois de a varredura terminar.
    */
    function artworkFor(item) {
        var persisted;
        var remembered;
        var remote;
        var local;
        if (!item) { return null; }
        persisted = safeArtworkUrl(item.logoUrl);
        remembered = safeArtworkUrl(artworkMemory[item.id]);
        /* Um arquivo local baixado antes da detecção pode ser a mesma imagem
           genérica. Um enriquecimento posterior e diferente continua válido,
           mas não reutiliza aquele arquivo ambíguo. */
        if (isPlaceholderArtwork(item, persisted)) {
            remote = usableArtworkUrl(item, remembered);
            if (remote && typeof BuroArtworkCache !== 'undefined') {
                BuroArtworkCache.remember(item.id, remote);
            }
            return remote;
        }
        /*
          A copia no pendrive vem primeiro, quando existe.

          Ela abre sem rede, o que numa TV com internet ruim e a diferenca entre
          a prateleira desenhar de imediato e desenhar aos poucos. Sem pendrive
          `localUrl` devolve nulo e tudo segue como antes.
        */
        local = typeof BuroArtworkCache !== 'undefined' ? BuroArtworkCache.localUrl(item.id) : null;
        if (local) { return local; }
        remote = remembered || persisted;
        /* Repete o pedido ao desenhar para cobrir arte que já estava no item
           antes de a opção ser ligada. O adapter deduplica o trabalho. */
        if (remote && typeof BuroArtworkCache !== 'undefined') {
            BuroArtworkCache.remember(item.id, remote);
        }
        return remote;
    }

    function artworkHtml(item, className) {
        var url = artworkFor(item);
        return url ? '<span class="' + (className || 'media-art') + '"><img src="' + attr(url) + '" alt=""></span>' : '';
    }

    function heroArtworkHtml(item, enrichment) {
        var stored = artworkFor(item);
        var backdrop = usableArtworkUrl(item, enrichment && enrichment.backdropUrl);
        var poster = usableArtworkUrl(item, enrichment && enrichment.artworkUrl) || stored;
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

    /* A capa vertical do título, quando existe uma. */
    function detailPosterHtml(item) {
        var poster = artworkFor(item);
        if (!poster) { return ''; }
        return '<span class="detail-poster"><img src="' + attr(poster) + '" alt=""></span>';
    }

    function detailArtworkHtml(item) {
        var poster = artworkFor(item);
        var backdrop = item && usableArtworkUrl(item, detailBackdropMemory[item.id]);
        if (!backdrop && !poster) { return ''; }
        return '<span class="detail-art"><img src="' + attr(backdrop || poster) + '"' +
            (backdrop && poster && backdrop !== poster ? ' data-artwork-fallback="' + attr(poster) + '"' : '') +
            ' alt=""></span>';
    }

    /*
      A arte chegou; quem está na tela precisa saber.

      A prateleira do catálogo faltava aqui, e era por isso que os cartões
      ficavam sem capa: a arte era buscada, guardada em `artworkMemory` e nunca
      desenhada, porque o render já tinha acontecido e nada acontecia depois.

      Agrupado nos dois casos que pedem várias categorias de uma vez — a Home e a
      prateleira. Um redesenho por resposta faria a tela piscar enquanto o
      usuário navega.
    */
    function finishArtworkRequest(key, category) {
        var section = state.section;
        var onShelf = section === 'LIVE' || section === 'MOVIES' || section === 'SERIES';
        artworkRequests[key] = 'done';
        if (state.screen !== 'SHELL') { return; }
        if (state.screenData && state.screenData.kind === 'category' &&
                state.screenData.category.id === category.id) { render(); return; }
        if (section === 'HOME' && state.screenData && state.screenData.kind === 'home') {
            scheduleHomeArtworkRedraw(); return;
        }
        if (onShelf && !state.screenData) { scheduleHomeArtworkRedraw(); }
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

    function activeTmdbRegion() {
        var profileId = state.activeProfile && state.activeProfile.id;
        var regions = state.preferences.tmdbRegionsByProfile;
        if (profileId && regions && typeof regions === 'object' && !Array.isArray(regions) &&
                Object.prototype.hasOwnProperty.call(regions, profileId)) {
            return BuroTmdb.safeRegion(regions[profileId]);
        }
        return profileId ? 'BR' : BuroTmdb.safeRegion(state.preferences.tmdbRegion);
    }

    function resetCatalogueProviderDirectories() {
        Object.keys(catalogueProviderDirectories).forEach(function (contentType) {
            var directory = catalogueProviderDirectories[contentType];
            if (directory.request && directory.request.abort) { directory.request.abort(); }
            directory.identity = '';
            directory.rows = [];
            directory.loading = false;
            directory.failed = false;
            directory.request = null;
        });
    }

    function invalidateStreamingRegionState() {
        resetHomeStreamingShelves();
        resetCatalogueProviderDirectories();
        serviceIndexRequestId += 1;
        if (serviceIndexRequest && serviceIndexRequest.abort) { serviceIndexRequest.abort(); }
        serviceIndex = null;
        serviceIndexBuiltFor = null;
        serviceIndexLoading = false;
        serviceIndexRequest = null;
        subscriptionRequestId += 1;
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        subscriptionRequest = null;
    }

    function changeActiveTmdbRegion(value) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var region = BuroTmdb.safeRegion(value);
        var existing = state.preferences.tmdbRegionsByProfile;
        var regions = {};
        if (existing && typeof existing === 'object' && !Array.isArray(existing)) {
            Object.keys(existing).forEach(function (key) {
                if (key !== '__proto__' && key !== 'constructor' && key !== 'prototype') {
                    regions[key] = BuroTmdb.safeRegion(existing[key]);
                }
            });
        }
        if (profileId) { regions[profileId] = region; }
        state.preferences.tmdbRegionsByProfile = regions;
        /* Mantido para downgrade/migracao de versoes antigas. */
        state.preferences.tmdbRegion = region;
        invalidateStreamingRegionState();
        savePreferences();
        return region;
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
        /*
          O atraso escolhido, quando o firmware nao o aplica na fonte.

          Positivo adia a legenda; negativo mostra ja, porque o instante dela ja
          passou e nao ha como voltar no tempo. Uma legenda adiada substitui a
          anterior no mesmo lugar, entao guardar o temporizador basta.
        */
        var offset = BuroPlayer.subtitleOffset ? BuroPlayer.subtitleOffset() : 0;
        if (offset > 0 && !BuroPlayer.subtitleOffsetHandledNatively && text) {
            if (playerSubtitleDelayTimer) { window.clearTimeout(playerSubtitleDelayTimer); }
            playerSubtitleDelayTimer = window.setTimeout(function () {
                playerSubtitleDelayTimer = null;
                drawPlayerSubtitle(text, durationMs);
            }, offset);
            return;
        }
        drawPlayerSubtitle(text, durationMs);
    }

    function drawPlayerSubtitle(rawText, durationMs) {
        var text = rawText;
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

    /*
      Se o fluxo e uma emissao ao vivo.

      Desconhecido conta como ao vivo, que e o lado seguro: um filme tratado como
      canal fica apenas com o buffer menor que ja tinha, enquanto um canal tratado
      como filme comecaria dois minutos mais tarde.
    */
    function isLiveContent(contentType) {
        return contentType !== 'MOVIE' && contentType !== 'SERIES' && contentType !== 'EPISODE';
    }

    function preparePlayerOverlay() {
        /* O convite sai com a sessao: ele so faz sentido dentro dos
           primeiros minutos de um episodio que esta tocando. */
        skipIntroVisible = false;
        if (playerSkipIntroButton) { playerSkipIntroButton.hidden = true; }
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

    function retryPlayback(automatic) {
        var playback = currentPlayback;
        var position;
        automatic = automatic === true;
        if (!playback) { stopPlayback(); return; }
        if (!automatic) { playbackRetry.reset(); }
        position = playback.contentType === 'LIVE' ? 0 : Number(playback.positionMs) || 0;
        clearPlayerError();
        if (playback.catchUpChannelId && playback.catchUpProgramme) {
            beginCatchUp(playback.catchUpChannelId, playback.catchUpProgramme, position, automatic);
            return;
        }
        beginPlayback(playback.itemId, position, automatic);
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

    /*
      O episodio seguinte ao que acabou de terminar.

      Le `state.screenData.items`, que e a lista da serie aberta — o player e uma
      sobreposicao e nao troca de tela, entao ela continua ali durante a
      reproducao. Fora de uma serie devolve nulo, e e assim que um filme nao
      encadeia com coisa nenhuma.

      A ordem e por temporada e depois por episodio, e nao a ordem em que o
      provedor mandou: listas Xtream chegam desordenadas com frequencia, e
      encadear na ordem de chegada levaria do episodio 3 ao 11.
    */
    function nextEpisodeAfter(itemId) {
        var data = state.screenData;
        var rows;
        var current = null;
        var next = null;
        if (!data || data.kind !== 'series' || !Array.isArray(data.items)) { return null; }
        rows = data.items.filter(function (episode) {
            return episode && episode.contentType === 'EPISODE' && itemVisible(episode);
        }).slice().sort(function (left, right) {
            var leftSeason = Number(left.locator && left.locator.season) || 0;
            var rightSeason = Number(right.locator && right.locator.season) || 0;
            if (leftSeason !== rightSeason) { return leftSeason - rightSeason; }
            return (Number(left.locator && left.locator.episode) || 0) -
                (Number(right.locator && right.locator.episode) || 0);
        });
        rows.some(function (episode, index) {
            if (episode.id !== itemId) { return false; }
            current = episode;
            next = rows[index + 1] || null;
            return true;
        });
        /* O ultimo episodio da serie nao encadeia: nao ha para onde ir, e
           insistir mostraria uma contagem que termina em nada. */
        return current && next ? next : null;
    }

    /*
      A contagem antes de encadear.

      Nao encadeia direto porque quem acabou de ver um episodio pode querer
      parar, e uma TV que decide sozinha por voce e pior do que uma que pergunta.
      Dez segundos e o bastante para ler o titulo e desistir, e curto o bastante
      para nao virar espera.

      O RETURN cancela e volta a ficha, que e o comportamento de sempre; o ENTER
      comeca imediatamente, para quem nao quer esperar.
    */
    function beginNextEpisodeCountdown(next) {
        var remaining = NEXT_EPISODE_SECONDS;

        function paint() {
            if (!playerNextPanel) { return; }
            playerNextPanel.hidden = false;
            playerNextTitle.textContent = t('playerNextUp') + ' · ' + nextEpisodeLabel(next);
            playerNextCountdown.textContent = t('playerNextIn').replace('{seconds}', String(remaining));
        }

        function tick() {
            remaining -= 1;
            if (remaining > 0) { paint(); return; }
            cancelNextEpisode(false);
            playItem(next.id);
        }

        cancelNextEpisode(false);
        nextEpisodeTarget = next;
        paint();
        nextEpisodeTimer = window.setInterval(tick, 1000);
    }

    /* O rotulo do que vem a seguir: T2 E5 e o nome, se houver. O numero sozinho
       nao diz o bastante para alguem decidir continuar. */
    function nextEpisodeLabel(episode) {
        var locator = episode && episode.locator || {};
        var season = Number(locator.season) > 0 ? Number(locator.season) : null;
        var number = Number(locator.episode) > 0 ? Number(locator.episode) : null;
        var marker = season && number ? 'T' + season + ' E' + number : (number ? 'E' + number : '');
        var name = episode && episode.name ? String(episode.name) : '';
        return marker && name ? marker + ' · ' + name : (marker || name);
    }

    /*
      Encerra a contagem. `hide` falso mantem o painel para quem chamou decidir —
      usado quando o encadeamento vai comecar e o painel some junto com o
      overlay.
    */
    function cancelNextEpisode(hide) {
        if (nextEpisodeTimer) { window.clearInterval(nextEpisodeTimer); nextEpisodeTimer = null; }
        nextEpisodeTarget = null;
        if (hide !== false && playerNextPanel) { playerNextPanel.hidden = true; }
    }

    /*
      O temporizador de sono.

      Quem adormece assistindo deixa a TV tocando a noite inteira: a lista
      continua consumindo banda, o painel continua aceso, e no dia seguinte o
      progresso do titulo esta no fim de um episodio que ninguem viu.

      As opcoes sao minutos e "ao fim deste episodio". A ultima existe porque e o
      que as pessoas de facto querem quando ja estao com sono: nao trinta
      minutos, mas ate onde este acabar.

      Ao chegar a zero para e volta a ficha, gravando o progresso — o mesmo
      caminho do RETURN. Fechar o aplicativo inteiro seria pior no caso comum de
      quem esta acordado e so nao mexeu no controle; deixar pausado com a tela
      acesa resolveria metade do problema.
    */
    var SLEEP_MINUTES = [15, 30, 45, 60, 90, 120];

    /* Sessenta segundos de resolucao bastam, e um intervalo por segundo numa TV
       de baixo custo e trabalho a toa durante duas horas. */
    var SLEEP_TICK_MILLIS = 60000;

    /*
      As leituras desta reproducao, uma por linha.

      Existe para diagnosticar a lista de um cliente sem sair da TV: uma
      imagem que trava com bitrate alto e problema de rede, e a mesma imagem
      com bitrate baixo e o provedor entregando menos do que promete. Sem os
      numeros as duas se parecem.

      O que o AVPlay nao devolver simplesmente nao aparece — nem como
      tracinho. Uma linha vazia parece um numero que deveria estar la, e a
      pessoa passa a duvidar do aplicativo em vez de duvidar da lista.

      Nao ha o que escolher aqui: sao leituras. Elas entram como opcoes
      porque o menu do player e a unica superficie que existe durante a
      reproducao.
    */
    function playerStatsOptions() {
        var stats = BuroPlayer.statistics ? BuroPlayer.statistics() : {};
        var rows = [];
        if (currentPlayback) {
            rows.push({ label: t('playerStatsTitle') + ': ' + String(currentPlayback.title || ''), reading: true });
        }
        if (stats.resolution) {
            rows.push({ label: t('playerStatsResolution') + ': ' + stats.resolution, reading: true });
        }
        if (stats.codec) {
            rows.push({ label: t('playerStatsCodec') + ': ' + stats.codec, reading: true });
        }
        if (stats.bitrateKbps) {
            rows.push({ label: t('playerStatsBitrate').replace('{kbps}', String(stats.bitrateKbps)), reading: true });
        }
        /*
          Sem nenhuma leitura, uma linha dizendo isso — e nao um menu vazio.
          Muitos firmwares nao expoem `getStreamingProperty`, e a pessoa
          precisa saber que o silencio e da TV e nao um defeito do
          aplicativo.
        */
        if (rows.length <= 1) {
            rows.push({ label: t('playerStatsUnavailable'), reading: true });
        }
        rows.forEach(function (row, index) { row.index = index; });
        return rows;
    }

    function sleepTimerOptions() {
        var options = [{ index: null, label: t('sleepTimerOff'), selected: sleepTimerEndsAt === null && !sleepTimerAtEpisodeEnd, sleep: true }];
        SLEEP_MINUTES.forEach(function (minutes) {
            options.push({
                index: minutes,
                label: t('sleepTimerMinutes').replace('{minutes}', String(minutes)),
                selected: sleepTimerMinutes === minutes && !sleepTimerAtEpisodeEnd,
                sleep: true
            });
        });
        /* Só faz sentido onde existe um fim: um canal ao vivo nunca acaba. */
        if (currentPlayback && currentPlayback.contentType !== 'LIVE') {
            options.push({
                index: 'EPISODE',
                label: t('sleepTimerEpisodeEnd'),
                selected: sleepTimerAtEpisodeEnd,
                sleep: true
            });
        }
        return options;
    }

    /*
      Aplica o atraso e o guarda para a proxima vez.

      Guardado **por fonte**, e nao por titulo nem global: quando uma lista
      dessincroniza e porque o provedor remuxa o material do mesmo jeito em tudo
      o que serve, entao o valor que acertou um filme costuma acertar o resto.
      Global seria pior — duas fontes com problemas diferentes brigariam.
    */
    function applySubtitleOffset(milliseconds) {
        var sourceId = state.activeSource && state.activeSource.id;
        var applied = BuroPlayer.setSubtitleOffset(milliseconds);
        var offsets;
        if (sourceId) {
            offsets = state.preferences.subtitleOffsets || {};
            if (applied === 0) { delete offsets[sourceId]; } else { offsets[sourceId] = applied; }
            state.preferences.subtitleOffsets = offsets;
            savePreferences();
        }
        updateSubtitleOffsetLabel();
        showToast(applied === 0 ? t('subtitleSyncReset') :
            t('subtitleSyncSet').replace('{seconds}', (applied / 1000).toFixed(1)), false);
        return applied;
    }

    /* O rotulo so aparece com o ajuste em uso: um valor de zero permanente na
       barra seria ruido em toda reproducao normal. */
    function updateSubtitleOffsetLabel() {
        var offset = BuroPlayer.subtitleOffset ? BuroPlayer.subtitleOffset() : 0;
        if (!playerSubtitleLabel) { return; }
        if (!offset) { return; }
        playerSubtitleLabel.textContent = '▼ ' + t('subtitleTrack') + ' ' +
            t('subtitleSyncSet').replace('{seconds}', (offset / 1000).toFixed(1));
    }

    /* Ao abrir uma reproducao, o valor gravado para esta fonte volta a valer. */
    function restoreSubtitleOffset() {
        var sourceId = state.activeSource && state.activeSource.id;
        var offsets = state.preferences.subtitleOffsets || {};
        BuroPlayer.setSubtitleOffset(sourceId && offsets[sourceId] ? offsets[sourceId] : 0);
    }
    function clearSleepTimer() {
        if (sleepTimerTicker) { window.clearInterval(sleepTimerTicker); sleepTimerTicker = null; }
        sleepTimerEndsAt = null;
        sleepTimerMinutes = null;
        sleepTimerAtEpisodeEnd = false;
        updateSleepTimerLabel();
    }

    /*
      O rotulo na barra do player mostra quanto falta, e nao so que o
      temporizador esta ligado: sem o numero a pessoa nao sabe se ainda da tempo
      de ver o resto, e acaba desligando por duvida.
    */
    function updateSleepTimerLabel() {
        var remaining;
        if (!playerSleepLabel) { return; }
        if (sleepTimerAtEpisodeEnd) {
            playerSleepLabel.hidden = false;
            playerSleepLabel.textContent = '⏻ ' + t('sleepTimerEpisodeEnd');
            return;
        }
        if (sleepTimerEndsAt === null) { playerSleepLabel.hidden = true; return; }
        remaining = Math.max(0, Math.ceil((sleepTimerEndsAt - Date.now()) / 60000));
        playerSleepLabel.hidden = false;
        playerSleepLabel.textContent = '⏻ ' + t('sleepTimerRemaining').replace('{minutes}', String(remaining));
    }

    /*
      Chega a zero: grava e sai.

      `stopPlayback` ja faz as duas coisas e e o caminho que o RETURN percorre,
      entao dormir e sair pelo controle terminam no mesmo estado — a ficha, com
      o botao dizendo de onde continuar.
    */
    function fireSleepTimer() {
        clearSleepTimer();
        showToast(t('sleepTimerFired'), false);
        stopPlayback();
    }

    function setSleepTimer(value) {
        clearSleepTimer();
        if (value === null) { showToast(t('sleepTimerOff'), false); return; }
        if (value === 'EPISODE') {
            sleepTimerAtEpisodeEnd = true;
            updateSleepTimerLabel();
            showToast(t('sleepTimerEpisodeEnd'), false);
            return;
        }
        sleepTimerMinutes = Number(value);
        sleepTimerEndsAt = Date.now() + sleepTimerMinutes * 60000;
        sleepTimerTicker = window.setInterval(function () {
            if (sleepTimerEndsAt !== null && Date.now() >= sleepTimerEndsAt) { fireSleepTimer(); return; }
            updateSleepTimerLabel();
        }, SLEEP_TICK_MILLIS);
        updateSleepTimerLabel();
        showToast(t('sleepTimerRemaining').replace('{minutes}', String(sleepTimerMinutes)), false);
    }

    /*
      O convite para pular a abertura.

      So em episodio: um filme nao tem tema que se repita, e num canal ao
      vivo nao ha para onde saltar. So dentro da janela, e so quando o
      episodio e longo o bastante para que noventa segundos nao sejam uma
      fatia grande dele — pular um episodio de cinco minutos comeria um terco
      do que existe.
    */
    function skipIntroAvailable() {
        var position;
        var duration;
        if (!currentPlayback || currentPlayback.contentType !== 'EPISODE') { return false; }
        position = Number(currentPlayback.positionMs) || 0;
        duration = Number(currentPlayback.durationMs) || 0;
        if (duration > 0 && duration < SKIP_INTRO_JUMP_MS * 4) { return false; }
        return position >= SKIP_INTRO_FROM_MS && position <= SKIP_INTRO_UNTIL_MS;
    }

    /* Desenhado so quando muda de estado: escrever no DOM a cada segundo de
       reproducao e trabalho a toa numa TV. */
    function updateSkipIntro() {
        var available = skipIntroAvailable();
        if (!playerSkipIntroButton || available === skipIntroVisible) { return; }
        skipIntroVisible = available;
        playerSkipIntroButton.hidden = !available;
        if (available) { playerSkipIntroButton.textContent = t('skipIntro') + '  ››'; }
    }

    function skipIntro() {
        if (!skipIntroAvailable()) { return false; }
        BuroPlayer.seekBy(SKIP_INTRO_JUMP_MS);
        /* Some assim que e usado: deixa-lo na tela convidaria a um segundo
           salto, que ja seria dentro do episodio. */
        skipIntroVisible = false;
        if (playerSkipIntroButton) { playerSkipIntroButton.hidden = true; }
        showToast(t('skipIntroDone'), false);
        return true;
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
        playerMenuTitle.textContent = playerMenuState.type === 'STATS' ? t('playerStats') :
            (playerMenuState.type === 'SLEEP' ? t('sleepTimer') :
            (playerMenuState.type === 'AUDIO' ? t('audioTracks') :
            (playerMenuState.type === 'TEXT' ? t('subtitleTracks') :
                (playerMenuState.type === 'GUIDE' ? t('programmeGuide') : t('playbackSpeed')))));
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
                    (option.past ? 'past ' : '') + (option.catchUp ? 'catch-up' : '') + '" role="listitem"' +
                    (option.selected ? ' aria-current="true"' : '') + ' tabindex="' +
                    (index === playerMenuState.position ? '0' : '-1') + '" data-player-option="' + index + '">' +
                    '<time>' + escapeHtml(option.time) + '</time><span><strong>' + escapeHtml(option.title) + '</strong>' +
                    (option.description ? '<small>' + escapeHtml(option.description) + '</small>' : '') + '</span>' +
                    (option.catchUp ? '<em>' + escapeHtml(t('catchUpPlay')) + '</em>' : '') + '</button>';
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
        playerMenuHint.textContent = guide && currentPlayback ?
            (currentPlayback.title || playerTitle.textContent || t('programmeGuide')) + ' · ' + t('playerMenuHint') :
            t('playerMenuHint');
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
        var guideChannel = type === 'GUIDE' && currentPlayback ?
            findItemAndSource(currentPlayback.itemId).item : null;
        var options = type === 'STATS' ? playerStatsOptions() : (type === 'SLEEP' ? sleepTimerOptions() : (type === 'SPEED' ? BuroPlayer.playbackRates().map(function (rate) {
            return { index: rate, label: rate + '×', selected: rate === BuroPlayer.playbackRate(), speed: true };
        }).concat([{ index: 'SLEEP_MENU', label: t('sleepTimer') + ' ›', selected: false, openSleep: true },
            { index: 'STATS', label: t('playerStats') + ' ›', selected: false, openStats: true }]) : (type === 'GUIDE' ? playerSchedule().slice(0, 100).map(function (program, index) {
            var catchUp = guideChannel ? BuroXtream.catchUpLocator(guideChannel.locator, program, nowSeconds) : null;
            return {
                index: index,
                label: String(program.title || ''), title: String(program.title || ''),
                description: String(program.description || ''),
                time: epgClock(program, false) + '–' + epgClock(program, true),
                selected: epgIsNow(program, nowSeconds),
                past: Number(program.endEpochSeconds) > 0 && Number(program.endEpochSeconds) <= nowSeconds,
                catchUp: Boolean(catchUp), programme: program,
                guide: true
            };
        }) : BuroPlayer.trackOptions(type))));
        var selected = 0;
        if (type === 'SPEED' && (!currentPlayback || currentPlayback.contentType === 'LIVE')) { return; }
        /* O sono vale tambem no ao vivo — e ali que a TV fica a noite toda. */
        if (type === 'SLEEP' && !currentPlayback) { return; }
        if (type === 'STATS' && !currentPlayback) { return; }
        if (type === 'TEXT') {
            options.unshift({ index: null, label: t('subtitlesOff'), selected: false, off: true });
            /*
              O ajuste de sincronia no fim da lista de faixas.

              Numa lista IPTV a legenda dessincroniza com frequencia — o
              provedor remuxa o arquivo e o offset embutido deixa de valer — e
              sem ajuste a unica saida e desliga-la. Fica depois das faixas
              porque escolher a lingua e o caso comum; corrigir o atraso e o
              caso do dia ruim.
            */
            /* So com faixa de legenda para ajustar: sem nenhuma, o controle
               prometeria corrigir um texto que nao vai aparecer. */
            if (options.length > 1) {
                options.push({ index: -500, label: t('subtitleEarlier'), selected: false, offset: true });
                options.push({ index: 500, label: t('subtitleLater'), selected: false, offset: true });
                if (BuroPlayer.subtitleOffset && BuroPlayer.subtitleOffset() !== 0) {
                    options.push({ index: 0, label: t('subtitleSyncReset'), selected: false, offset: true });
                }
            }
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
        if (option.guide) {
            if (option.catchUp && currentPlayback) {
                var channelId = currentPlayback.itemId;
                closePlayerMenu();
                beginCatchUp(channelId, option.programme, 0);
                return;
            }
            closePlayerMenu(); return;
        }
        /* A entrada que abre o submenu do sono, e nao uma escolha em si. As
           quatro teclas coloridas ja estao ocupadas, entao o temporizador mora
           dentro do menu amarelo — que deixa de ser so velocidade e passa a ser
           o menu de reproducao. */
        if (option.openStats) {
            closePlayerMenu();
            openPlayerMenu('STATS');
            return;
        }
        /* Uma leitura nao e uma escolha: acionar qualquer linha so fecha. */
        if (option.reading) { closePlayerMenu(); return; }
        if (option.openSleep) {
            closePlayerMenu();
            openPlayerMenu('SLEEP');
            return;
        }
        /*
          O ajuste de sincronia e cumulativo e nao fecha o menu: acertar uma
          legenda leva varios toques, e fechar a cada meio segundo obrigaria a
          reabrir o menu inteiro entre eles.
        */
        if (option.offset) {
            applySubtitleOffset(option.index === 0 ? 0 : BuroPlayer.subtitleOffset() + option.index);
            openPlayerMenu('TEXT');
            return;
        }
        if (option.sleep) {
            setSleepTimer(option.index);
            closePlayerMenu();
            return;
        }
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
        /* Perfil ou fonte diferente veem catálogos diferentes — um perfil Kids
           esconde categorias — então a Home guardada não vale para o próximo. */
        forgetHomeCache();
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
        invalidateStreamingRegionState();
    }

    /* Mesmos estados universais usados pelo Android: perfil, catálogo, arte e pronto. */
    /*
      Os passos da tela de abertura.

      `sweep` é a varredura que completa o catálogo. Ela existia só como trabalho
      de fundo: o app abria e as prateleiras iam se enchendo enquanto a pessoa
      navegava, o que numa lista de quarenta mil títulos significa abrir numa
      tela pela metade. O aplicativo do Windows espera na abertura — ver
      `SplashScreen.kt`, que mostra progresso e contagem exatamente por isso.
    */
    /* `home` entra antes de `ready`: a Home era montada depois de a interface
       aparecer, entao quem esperou a varredura inteira ainda encontrava
       "Montando sua Home..." na primeira tela. O trabalho e o mesmo; o lugar
       certo dele e a tela de carregamento, que existe para isso. */
    var BOOT_STEPS = ['profiles', 'catalogue', 'artwork', 'sweep', 'home', 'ready'];

    function bootProgress(stepName, messageKey, detail, fraction) {
        var index = 0;
        while (index < BOOT_STEPS.length && BOOT_STEPS[index] !== stepName) { index += 1; }
        state.boot = {
            step: stepName,
            index: index,
            total: BOOT_STEPS.length,
            messageKey: messageKey,
            detail: detail || '',
            fraction: fraction == null ? null : BuroDomain.clamp(Number(fraction) || 0, 0, 1),
            previewArtwork: state.boot && state.boot.previewArtwork || []
        };
        if (state.screen === 'BOOT') { render(); }
    }

    function finishInitialization() {
        var targetScreen;
        if (!state.preferences.acceptedLegal) { targetScreen = 'LEGAL'; }
        else if (!state.profiles.length) { targetScreen = 'PROFILES'; }
        else { targetScreen = 'SHELL'; }
        state.ready = true;
        /*
          A varredura entra na abertura só quando o destino é o shell.

          Quem ainda vai aceitar o aviso legal ou criar um perfil não tem fonte
          para varrer, e segurar a tela ali seria esperar por nada.
        */
        if (targetScreen !== 'SHELL') { revealApp(targetScreen); return; }
        waitForCatalogueSweep(function () { revealApp(targetScreen); });
    }

    function revealApp(targetScreen) {
        /* A Home e montada atras da tela de abertura sempre que o destino e o
           shell, porque o destino agora e sempre o Inicio. */
        if (targetScreen === 'SHELL') {
            prepareHomeForReveal(function () { completeReveal(targetScreen); });
            return;
        }
        completeReveal(targetScreen);
    }

    function completeReveal(targetScreen) {
        var minimum = state.sources.length ? BOOT_POSTER_REVEAL_MILLIS : BOOT_MINIMUM_MILLIS;
        var remaining = Math.max(0, minimum - (Date.now() - bootStartedAt));
        bootProgress('ready', 'bootReady');
        window.setTimeout(function () {
            state.screen = targetScreen;
            /*
              Abrir sempre no Inicio.

              A secao ficava guardada e era restaurada: quem saiu do
              aplicativo em Configuracoes voltava nelas dias depois, sem
              nenhuma pista de por que. Numa TV isso e pior do que num
              telefone — a pessoa liga o aparelho para assistir, e a primeira
              tela deve ser a que oferece o que assistir.

              A preferencia continua sendo gravada: ela ainda diz onde a
              pessoa estava dentro da mesma sessao, e e o que a volta usa.
              O que muda e a abertura.
            */
            if (targetScreen === 'SHELL') { state.section = 'HOME'; }
            render();
            if (targetScreen === 'SHELL') {
                /* A varredura já rodou na abertura; isto retoma o que tiver
                   sobrado, por exemplo se a pessoa pulou a espera. */
                window.setTimeout(function () { startActiveSourceHydration(false); }, 0);
                /* Depois do shell aparecer, não durante o boot: um aviso sobre a
                   tela de carregamento seria lido como erro. */
                window.setTimeout(showReminderNoticeOnce, 1200);
            }
        }, remaining);
    }

    /*
      Monta a primeira Home ainda atrás da abertura.

      Antes `startHomeLoad()` era chamado enquanto `state.screen` continuava em
      BOOT; `loadHome()` recusava corretamente respostas fora da SHELL e, por
      isso, o trabalho anunciado nunca chegava ao cache. A interface aparecia
      e só então começava a varredura de novo. Aqui a preparação tem seu próprio
      contrato: publica somente `homeCache`, sem fingir que a SHELL já existe.
    */
    function prepareHomeForReveal(done) {
        bootProgress('home', 'bootHome', '', 0.08);
        BuroStorage.fold('items', collectHome, homeAccumulator(), function (result) {
            applyHomePlaceholderArtwork(result);
            mergeItems(homeResultItems(result));
            rememberHome(result, 0);
            bootProgress('home', 'bootHome', '', 0.24);
            /* As prateleiras públicas do TMDb fazem parte do primeiro quadro
               quando a pessoa configurou uma chave. A SHELL só aparece depois
               de a consulta terminar (ou falhar pelo timeout do adapter), tal
               como o Windows espera a Home diária antes de retirar a splash. */
            ensureHomeStreamingShelves(function () {
                preloadInitialHomeHero(result, function () {
                    preloadInitialHomeArtwork(result, done);
                });
            });
        }, function () {
            /* A falha será mostrada pela Home e poderá ser tentada de novo. O
               boot nunca fica preso por uma leitura que já falhou. */
            forgetHomeCache();
            done();
        });
    }

    function initialHomeArtworkUrls(result) {
        var model = homeModel({ result: result, heroIndex: 0 });
        var heroEnrichment = model.hero && state.activeSource ?
            BuroHeroEnrichment.get(state.activeSource.id, model.hero.id) : null;
        var items = [];
        var known = {};
        var urls = [];
        var heroUrl = safeArtworkUrl(heroEnrichment &&
            (heroEnrichment.backdropUrl || heroEnrichment.artworkUrl));
        if (heroUrl) { known[heroUrl] = true; urls.push(heroUrl); }
        if (model.hero) { items.push(model.hero); }
        model.rails.slice(0, 2).forEach(function (rail) {
            items = items.concat((rail.items || []).slice(0, 6));
        });
        homeStreamingShelves.some(function (shelf) {
            (shelf.titles || []).some(function (title) {
                var url = safeArtworkUrl(title.posterUrl);
                if (url && !known[url]) { known[url] = true; urls.push(url); }
                return urls.length >= 12;
            });
            return urls.length >= 12;
        });
        items.some(function (item) {
            var url = artworkFor(item);
            if (url && !known[url]) { known[url] = true; urls.push(url); }
            return urls.length >= 12;
        });
        return urls;
    }

    /*
      O texto e o fundo do destaque também pertencem ao primeiro quadro. Carrega
      somente o Hero atual durante o boot; os outros nove continuam na fila
      serial depois que a pessoa já pode navegar.
    */
    function preloadInitialHomeHero(result, done) {
        var source = state.activeSource;
        var model = homeModel({ result: result, heroIndex: 0 });
        var hero = model.hero;
        var tmdbKey = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        var sync = source ? catalogueSyncStatus(source) : null;
        var allowProvider = Boolean(source && source.type === 'XTREAM' && !(sync && sync.state === 'RUNNING'));
        var completed = false;
        var status;
        function finish() {
            if (completed) { return; }
            completed = true;
            done();
        }
        if (!source || !hero || (!allowProvider && !tmdbKey)) { finish(); return; }
        bootProgress('home', 'bootHome', '', 0.42);
        try {
            status = BuroHeroEnrichment.start(source, [hero], {
                modeKey: allowProvider ? 'provider-tmdb-fallback' : 'tmdb',
                loadDetails: function (item, success, failure) {
                    return loadHomeHeroDetails(source, tmdbKey, allowProvider, item, success, failure);
                },
                onComplete: finish
            });
            if (status && status.state === 'COMPLETE') { finish(); }
        } catch (ignoredInitialHero) { finish(); }
    }

    /*
      Espera apenas pelas capas do primeiro quadro, como o Windows: aquecer o
      catálogo inteiro pode levar muitos minutos e continua sendo tarefa de
      fundo. Erro ou timeout também contam como resolvidos, pois o card possui
      fallback visual e a Home não pode ficar refém de um CDN fora do ar.
    */
    function preloadInitialHomeArtwork(result, done) {
        var urls = initialHomeArtworkUrls(result);
        var completed = 0;
        var preview = [];
        function detail() {
            return t('bootCoversDetail').replace('{completed}', String(completed))
                .replace('{total}', String(urls.length));
        }
        function update() {
            bootProgress('home', 'bootHome', detail(), 0.24 + (urls.length ? 0.76 * completed / urls.length : 0.76));
            state.boot.previewArtwork = preview.slice(0, 12);
            if (state.screen === 'BOOT') { render(); }
        }
        if (!urls.length || typeof window.Image !== 'function') {
            bootProgress('home', 'bootHome', '', 1);
            done();
            return;
        }
        update();
        urls.forEach(function (url) {
            var image = new window.Image();
            var timer;
            var settled = false;
            function finish(loaded) {
                if (settled) { return; }
                settled = true;
                if (timer) { window.clearTimeout(timer); }
                if (loaded && preview.length < 12) { preview.push(url); }
                completed += 1;
                update();
                if (completed === urls.length) { done(); }
            }
            image.onload = function () { finish(true); };
            image.onerror = function () { finish(false); };
            timer = window.setTimeout(function () { finish(false); }, 4500);
            image.src = url;
        });
    }

    function initializeData() {
        if (window.BuroHeroEnrichment) { BuroHeroEnrichment.cancel(); }
        if (window.BuroTrailer) { BuroTrailer.close(); }
        if (tmdbTitleRequest && tmdbTitleRequest.abort) { tmdbTitleRequest.abort(); }
        if (tmdbSimilarRequest && tmdbSimilarRequest.abort) { tmdbSimilarRequest.abort(); }
        if (tmdbPersonRequest && tmdbPersonRequest.abort) { tmdbPersonRequest.abort(); }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        tmdbTitleRequest = null;
        tmdbSimilarRequest = null;
        tmdbPersonRequest = null;
        subscriptionRequest = null;
        personReturnData = null;
        subscriptionReturnData = null;
        similarTitleReturnStack = [];
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
                /* O cache no pendrive, se a pessoa o ligou: le a pasta uma vez e
                   ja sabe quais capas nao precisa buscar de novo. Falha em
                   silencio quando o pendrive nao esta la, que e o caso normal. */
                if (state.preferences.artworkCacheEnabled) {
                    BuroArtworkCache.attach(state.preferences.artworkCacheLimitMb, function () {});
                }
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
        /*
          A foto primeiro; sem ela, o simbolo do avatar escolhido.

          A inicial do nome ficava aqui e nao distinguia nada: uma casa com
          Bruno e Beatriz via dois circulos com B, e os cinco avatares padrao
          eram a mesma letra em cinco cores. O simbolo separa um perfil do
          outro de relance, que e para o que o avatar existe.

          A inicial fica como ultimo recurso, para uma chave fora do
          conjunto: melhor uma letra do que um circulo vazio.
        */
        var symbol = profile.isKids ? 'avatarKids' : 'avatar' +
            (AVATAR_KEYS.indexOf(profile.avatarKey) >= 0 ? profile.avatarKey : 'gold')
                .replace(/^[a-z]/, function (first) { return first.toUpperCase(); });
        if (photo) { return '<img src="' + attr(photo) + '" alt="">'; }
        if (BuroIcons.has(symbol)) { return BuroIcons.svg(symbol); }
        return escapeHtml(profile.name.charAt(0).toUpperCase());
    }

    function focusLabel(profile) {
        var avatarKey = AVATAR_KEYS.indexOf(profile.avatarKey) >= 0 ? profile.avatarKey : 'gold';
        return '<div class="avatar ' + avatarKey + ' ' + (profile.isKids ? 'kids' : '') + '">' +
            profileAvatarContent(profile) + '</div><strong>' +
            escapeHtml(profile.name) + '</strong><small>' + (profile.isKids ? t('kidsProfile') : 'BURO') + '</small>';
    }

    /*
      O que a varredura está fazendo, para a linha de contagem da abertura.

      Vazio quando não há nada contável — sem fonte, ou antes de a varredura
      começar — porque um contador em "0/0" diz menos que nenhum.
    */
    function bootDetail() {
        var status = state.activeSource ? catalogueSyncStatus(state.activeSource) : null;
        if (state.boot && state.boot.detail) { return state.boot.detail; }
        if (!status || !status.total) { return ''; }
        return t('bootSweepDetail')
            .replace('{completed}', status.completed)
            .replace('{total}', status.total)
            .replace('{items}', status.itemCount || 0);
    }

    /*
      Espera a varredura terminar antes de mostrar o app.

      O progresso real permanece visível durante toda a varredura. Estados de
      conclusão, cancelamento ou erro liberam a abertura para que uma falha de
      rede não deixe a TV presa indefinidamente nessa etapa.

      Sem fonte configurada não há o que varrer, e a abertura segue direto.
    */
    function waitForCatalogueSweep(done) {
        var source = state.activeSource;
        var status;
        if (!source || source.type !== 'XTREAM') { done(); return; }
        bootProgress('sweep', 'bootSweep');
        startActiveSourceHydration(false);
        status = catalogueSyncStatus(source);
        if (!status || !status.total || status.state === 'COMPLETE') { done(); return; }
        bootSweepDone = done;
        bootSweepTimer = window.setInterval(function () {
            var current = catalogueSyncStatus(state.activeSource);
            if (state.screen !== 'BOOT') { finishBootSweep(); return; }
            render();
            if (!current || current.state === 'COMPLETE' || current.state === 'CANCELLED' ||
                    current.state === 'ERROR') {
                finishBootSweep();
            }
        }, 400);
    }

    /* Encerra a espera, seja porque acabou ou porque a pessoa pulou. */
    function finishBootSweep() {
        var done = bootSweepDone;
        if (bootSweepTimer) { window.clearInterval(bootSweepTimer); bootSweepTimer = null; }
        bootSweepDone = null;
        if (done) { done(); }
    }

    /*
      Quanto do carregamento ja foi feito, de 0 a 100.

      Cada passo vale uma fatia igual, e dentro do passo da varredura a fatia e
      preenchida pela proporcao de categorias ja lidas. Sem isso a barra saltava
      de vinte em vinte e ficava imovel durante a varredura — que e o passo
      longo, minutos numa lista de dezenas de milhares — e uma barra parada e
      lida como travamento, que foi o relato.
    */
    function bootProgressPercent(boot) {
        var step = Math.max(0, Number(boot.index) || 0);
        var total = Math.max(1, Number(boot.total) || BOOT_STEPS.length);
        var slice = 100 / total;
        var status = BOOT_STEPS[step] === 'sweep' && state.activeSource
            ? catalogueSyncStatus(state.activeSource) : null;
        var inner = boot.fraction != null ? BuroDomain.clamp(Number(boot.fraction) || 0, 0, 1) :
            (status && status.total ? Math.min(1, status.completed / status.total) : 1);
        return Math.min(100, Math.round(step * slice + slice * inner));
    }

    /* A varredura esta em curso: e quando a espera precisa de explicacao. */
    function bootIsSweeping() {
        var status = state.activeSource ? catalogueSyncStatus(state.activeSource) : null;
        return Boolean(status && status.total && status.state === 'RUNNING');
    }

    function renderBoot() {
        var boot = state.boot || { index: 0, total: BOOT_STEPS.length, messageKey: 'bootProfiles' };
        var progressValue = bootProgressPercent(boot);
        var progressLabel = t(boot.messageKey);
        var preview = boot.previewArtwork || [];
        var hasPosterWall = preview.length >= 9;
        var backdropCovers = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11].map(function (index) {
            return '<span>' + (hasPosterWall && preview[index] ?
                '<img src="' + attr(preview[index]) + '" alt="">' : '') + '</span>';
        });
        var backdrop = '<div class="boot-cover-row boot-cover-row-a">' +
            backdropCovers.slice(0, 6).join('') + '</div>' +
            '<div class="boot-cover-row boot-cover-row-b">' +
            backdropCovers.slice(6).join('') + '</div>';
        var dots = BOOT_STEPS.map(function (step, index) {
            var status = index < boot.index ? 'complete' : (index === boot.index ? 'active' : '');
            return '<span class="boot-dot ' + status + '" aria-hidden="true"></span>';
        }).join('');
        root.innerHTML = '<main class="boot-screen">' +
            '<div class="boot-backdrop ' + (hasPosterWall ? 'has-preview' : 'local') +
            '" aria-hidden="true">' + backdrop + '</div>' +
            '<div class="boot-vignette" aria-hidden="true"></div>' +
            '<section class="boot-panel" role="status" aria-live="polite" aria-atomic="true">' +
            '<div class="boot-mark" aria-hidden="true"><span>B</span></div>' +
            '<p class="boot-brand">IPTV BURO</p>' +
            '<p class="boot-message">' + progressLabel +
            /* A porcentagem junto do que esta sendo feito: um numero que anda
               diz que o aparelho esta trabalhando, mesmo quando a etapa demora. */
            ' <b class="boot-percent">' + progressValue + '%</b></p>' +
            /*
              A contagem, numa linha própria.

              Muda várias vezes por segundo enquanto o catálogo entra, e um
              título que se reescreve nesse ritmo é mais difícil de ler do que um
              que fica parado com um número correndo embaixo. Mesma decisão do
              `detail` na splash do Windows.
            */
            (bootDetail() ? '<p class="boot-detail">' + escapeHtml(bootDetail()) + '</p>' : '') +
            '<p class="boot-stage">' + t('bootStageLabel') + '</p>' +
            /*
              Por que a primeira vez demora.

              Uma lista de dezenas de milhares de titulos leva minutos para ser
              organizada, e sem explicacao isso se le como travamento. Dizer que
              a proxima abertura e rapida transforma a espera em algo com fim
              conhecido — e so aparece durante a varredura, que e quando a
              pergunta existe.
            */
            (bootIsSweeping() ? '<p class="boot-note">' + escapeHtml(t('bootFirstRunNote')) + '</p>' : '') +
            '<div class="boot-progress" role="progressbar" aria-label="' + attr(progressLabel) +
            '" aria-valuemin="0" aria-valuemax="100" aria-valuenow="' + progressValue + '">' +
            '<span class="boot-progress-fill" style="width: ' + progressValue + '%"></span>' +
            '<strong class="boot-progress-value" aria-hidden="true">' + progressValue + '%</strong></div>' +
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
                '</h1><div class="profile-row">' + cards + '</div>' +
                // Antes de existir qualquer lista, porque e o cliente sem lista que
                // precisa de mandar o codigo a quem lha vendeu.
                '<button class="button ghost focusable" data-action="device-code">' +
                escapeHtml(t('deviceCode')) + '</button></main>';
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

    function renderHistoryClearConfirm() {
        var busy = Boolean(state.screenData && state.screenData.busy);
        var profileName = state.activeProfile ? state.activeProfile.name : '';
        root.innerHTML = '<main class="resume-screen"><section class="resume-panel history-clear-panel">' +
            '<span class="hero-kicker">IPTV BURO</span><h1>' + escapeHtml(t('historyClearConfirmTitle')) +
            '</h1><h2>' + escapeHtml(profileName) + '</h2><p>' + escapeHtml(t('historyClearConfirmBody')) +
            '</p><div class="action-row"><button class="button danger focusable" data-action="history-clear-confirm"' +
            (busy ? ' disabled' : '') + '>' + escapeHtml(busy ? t('loading') : t('historyClearAll')) +
            '</button><button class="button ghost focusable" data-action="back"' + (busy ? ' disabled' : '') + '>' +
            escapeHtml(t('cancel')) + '</button></div></section></main>';
    }

    /*
      A confirmacao de limpar a lista de Continuar assistindo.

      Igual a do Historico na forma, e diferente no que faz: aqui nada e
      apagado. Cada linha e marcada como concluida, que e exatamente o que
      `forgetContinueProgress` faz um cartao por vez.

      A distincao importa. Apagar as linhas tiraria os titulos do Historico
      tambem — a mesma tabela responde pelas duas telas — e quem pediu para
      limpar a fila de retomada nao pediu para esquecer o que assistiu.
    */
    function renderContinueClearConfirm() {
        var busy = Boolean(state.screenData && state.screenData.busy);
        var profileName = state.activeProfile ? state.activeProfile.name : '';
        root.innerHTML = '<main class="resume-screen"><section class="resume-panel history-clear-panel">' +
            '<span class="hero-kicker">IPTV BURO</span><h1>' + escapeHtml(t('continueClearConfirmTitle')) +
            '</h1><h2>' + escapeHtml(profileName) + '</h2><p>' + escapeHtml(t('continueClearConfirmBody')) +
            '</p><div class="action-row"><button class="button danger focusable" data-action="continue-clear-confirm"' +
            (busy ? ' disabled' : '') + '>' + escapeHtml(busy ? t('loading') : t('continueClearAll')) +
            '</button><button class="button ghost focusable" data-action="back"' + (busy ? ' disabled' : '') + '>' +
            escapeHtml(t('cancel')) + '</button></div></section></main>';
    }

    function navHtml() {
        return navigationEntries().map(function (entry) {
            return '<li class="nav-item focusable ' + (state.section === entry.section ? 'selected' : '') +
                '" role="button"' + (state.section === entry.section ? ' aria-current="page"' : '') +
                ' data-action="section" data-section="' + entry.section + '"><span class="nav-icon">' +
                (BuroIcons.has(entry.label) ? BuroIcons.svg(entry.label) : escapeHtml(entry.icon)) +
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
            /* O indicador fica fora do <h1>: o título tem de continuar sendo
               exatamente o nome da seção, que é o que a tela anuncia. */
            '</h1>' + catalogueSyncChip() + topbarSubtitleHtml() + (topbarExtra || '') +
            '<div class="topbar-status">' + refreshChipHtml() + diagnosticsChipHtml() + downloadChipHtml() + licenceChipHtml() + clockHtml() + profileChipHtml() +
            notificationBellHtml() + '</div></header><section class="content ' + (scrollable ? 'scrollable' : '') + '">' +
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

    function homeRail(title, items, key, service, badge) {
        var identity = service ? BuroProviders.identityForLabel(service) : null;
        if (!items.length) { return ''; }
        return '<section class="home-rail" data-home-rail="' + attr(key || '') + '"><div class="section-heading home-rail-heading">' +
            (identity ? '<h2>' + providerBadge(identity) + escapeHtml(title) + '</h2>' :
                '<h2>' + escapeHtml(title) + '</h2>') +
            /* O selo da fileira sazonal: diz por que ela existe hoje, para
               "Especial de Natal" nao se ler como uma categoria comum. */
            (badge ? '<span class="rail-badge">' + escapeHtml(badge) + '</span>' : '') + '<p>' +
            items.length + '</p></div>' +
            /* Lembretes trazem entradas próprias, não linhas do catálogo: parte
               delas não tem item para `mediaCards` desenhar. */
            (key === 'reminders' ? reminderCardsHtml(items) : mediaCards(items.slice(0, 12))) +
            '</section>';
    }

    /*
      Android e Windows encerram a Home com as prateleiras publicas de
      Assinaturas. Elas sao descoberta externa: nunca recebem acao de play e
      so abrem a ficha "onde assistir".
    */
    function homeStreamingRail(shelf) {
        var seen = {};
        var cards = [];
        var providerId = String(shelf && shelf.providerId || '');
        var providerName = BuroDomain.trim(shelf && shelf.providerName || '');
        (shelf && shelf.titles || []).some(function (title) {
            var key = subscriptionTitleKey(title);
            var poster;
            if (!key || seen[key] || !BuroDomain.trim(title && title.title)) { return false; }
            seen[key] = true;
            poster = safeArtworkUrl(title.posterUrl);
            cards.push('<button class="subscription-poster focusable" data-action="home-subscription-title" data-key="' +
                attr(key) + '">' + (poster ? '<img src="' + attr(poster) + '" alt="">' :
                '<span>' + escapeHtml(title.title.charAt(0)) + '</span>') +
                '<strong>' + escapeHtml(title.title) + '</strong>' +
                (title.year ? '<small>' + escapeHtml(String(title.year)) + '</small>' : '') + '</button>');
            return cards.length >= 12;
        });
        if (!cards.length || !providerName) { return ''; }
        return '<section class="home-rail home-streaming-rail" data-home-rail="streaming-' + attr(providerId) + '">' +
            '<div class="section-heading home-rail-heading"><h2>' + subscriptionProviderLogo(shelf.providerLogoUrl) +
            escapeHtml(providerName) + '</h2><p>' + cards.length + '</p></div>' +
            '<div class="subscription-row home-streaming-row">' + cards.join('') + '</div></section>';
    }

    function resetHomeStreamingShelves() {
        if (homeStreamingRequest && homeStreamingRequest.abort) { homeStreamingRequest.abort(); }
        homeStreamingRequest = null;
        homeStreamingShelves = [];
        homeStreamingIdentity = '';
        homeStreamingLoading = false;
        homeStreamingFailed = false;
    }

    function finishHomeStreamingWaiters() {
        var waiters = homeStreamingWaiters.slice();
        homeStreamingWaiters = [];
        waiters.forEach(function (done) { try { done(); } catch (ignoredWaiter) {} });
    }

    function ensureHomeStreamingShelves(done) {
        var profileId = state.activeProfile && state.activeProfile.id || '';
        var region = activeTmdbRegion();
        var language = state.preferences.language;
        var identity = profileId + '|' + region + '|MOVIES|' + language;
        var key = BuroTmdb.keyForProfile(profileId);
        var cached;
        if (typeof done === 'function') { homeStreamingWaiters.push(done); }
        if (!key) {
            if (homeStreamingIdentity || homeStreamingShelves.length || homeStreamingLoading || homeStreamingFailed) {
                resetHomeStreamingShelves();
            }
            finishHomeStreamingWaiters();
            return;
        }
        if (identity !== homeStreamingIdentity) {
            resetHomeStreamingShelves();
            homeStreamingIdentity = identity;
        }
        if (homeStreamingShelves.length || homeStreamingFailed) {
            finishHomeStreamingWaiters(); return;
        }
        if (homeStreamingLoading) { return; }
        cached = BuroTmdb.readShelfCache(region, 'MOVIES', language);
        if (cached) { homeStreamingShelves = cached; finishHomeStreamingWaiters(); return; }
        homeStreamingLoading = true;
        try {
            homeStreamingRequest = BuroTmdb.loadShelves(key, region, 'MOVIES', language, function () {}, function (shelves) {
                if (homeStreamingIdentity !== identity) { return; }
                homeStreamingRequest = null;
                homeStreamingLoading = false;
                homeStreamingShelves = shelves || [];
                BuroTmdb.writeShelfCache(region, 'MOVIES', language, homeStreamingShelves);
                finishHomeStreamingWaiters();
                if (state.screen === 'SHELL' && state.section === 'HOME') { render(); }
            }, function () {
                if (homeStreamingIdentity !== identity) { return; }
                homeStreamingRequest = null;
                homeStreamingLoading = false;
                homeStreamingFailed = true;
                finishHomeStreamingWaiters();
            });
        } catch (ignoredHomeStreamingError) {
            homeStreamingRequest = null;
            homeStreamingLoading = false;
            homeStreamingFailed = true;
            finishHomeStreamingWaiters();
        }
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

    /*
      Dias que faltam, ou null quando não há data utilizável.

      Usa o mesmo horizonte do aviso: um cartao que mostrasse a contagem de um
      titulo que o aviso ainda ignora diria duas coisas diferentes na mesma
      tela.
    */
    function reminderCountdown(reminder) {
        var digest = BuroDomain.reminderDigest([reminder], null, reminderHorizonDays());
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

    function catalogueVisibilitySnapshot() {
        /*
          Vazio quando o utilizador pediu para juntar as listas.

          Uma fonte falsa aqui ja significava "todas as fontes" em todo o codigo a
          jusante, entao juntar e escolher isso mais uma passagem que tira os
          repetidos — e nao um segundo caminho a discordar deste.
        */
        var sourceId = mergeEverySource() ? null : (state.activeSource && state.activeSource.id);
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

    /*
      Se todas as assinaturas sao mostradas como um so catalogo.

      Desligado por defeito: quem tem uma lista nao ganha nada, e quem tem duas e
      nao pediu isto nao deve encontrar a biblioteca reorganizada sozinha.
    */
    /*
      Guarda a escolha e volta a desenhar.

      Passa a valer na proxima vez que uma fonte e aberta e nao de imediato:
      reconstruir o catalogo por baixo de quem esta a navegar esvaziaria o ecra que
      a pessoa esta a ver.
    */
    function toggleMergeSources() {
        state.preferences.mergeEverySource = !mergeEverySource();
        savePreferences();
        render();
    }

    function mergeEverySource() {
        return Boolean(state.preferences && state.preferences.mergeEverySource);
    }

    function snapshotItemVisible(snapshot, item) {
        if (snapshot.sourceId && item.sourceId !== snapshot.sourceId) { return false; }
        if (item.categoryId && Object.prototype.hasOwnProperty.call(snapshot.categoryVisibility, item.categoryId)) {
            return Boolean(snapshot.categoryVisibility[item.categoryId]);
        }
        return true;
    }

    /*
      Natal, Halloween, Namorados, Ano Novo, ferias — quando o calendario esta
      dentro da janela de cada um.

      As colecoes, os termos e as datas vem de `js/seasonal.js`, porte do
      `SeasonalCollections` do dominio compartilhado: o mesmo que o Windows e o
      Android usam, para as tres plataformas mostrarem a mesma prateleira no
      mesmo dia.

      Uma coleção por vez, como no Windows: a Home tem espaco para uma fileira
      sazonal, e duas competiriam entre si em vez de destacar o dia.
    */
    function seasonalRail(result, consumedIds, consumedTitles, rails) {
        var collection = result.seasonalCollection;
        var matched = result.seasonal || [];
        var selected = [];
        var shelfTitles = {};
        if (!collection) { return; }
        if (!matched.length) { return; }
        /* O Windows desenha a fotografia sazonal completa mesmo quando seu
           primeiro título também ocupa o Hero. Aqui essa repetição é útil: com
           um único especial no catálogo, esconder o card faria a fileira — e
           sua explicação sazonal — desaparecer por completo. A partir daqui os
           ids ficam consumidos para não voltarem nas fileiras comuns. */
        matched.some(function (item) {
            var titleKey = homeTitleKey(item) || item.id;
            if (!shelfTitles[titleKey]) {
                shelfTitles[titleKey] = true;
                selected.push(item);
                consumedIds[item.id] = true;
                if (titleKey) { consumedTitles[titleKey] = true; }
            }
            return selected.length >= HOME_RAIL_LIMIT;
        });
        rails.push({
            key: 'seasonal-' + collection.id,
            title: BuroSeasonal.titleFor(collection, state.preferences.language),
            /* O selo diz por que a fileira existe: sem ele "Especial de Natal"
               se le como uma categoria comum que ninguem pediu. */
            badge: t('seasonalBadge'),
            items: selected
        });
    }

    function homeAccumulator() {
        var snapshot = catalogueVisibilitySnapshot();
        var seasonalCollection = BuroSeasonal.primaryCollectionFor(new Date());
        var detectPlaceholderArtwork = Boolean(state.activeSource && state.activeSource.type === 'XTREAM');
        return {
            count: 0,
            sourceId: snapshot.sourceId,
            categoryVisibility: snapshot.categoryVisibility,
            currentYear: new Date().getFullYear(),
            currentReleases: [], previousReleases: [], recent: [], topRated: [], movies: [], series: [],
            /* A varredura já percorre o catálogo inteiro. Guardar aqui os
               primeiros encontros sazonais evita repetir até uma dúzia de
               buscas completas como o repositório paginado do desktop precisa
               fazer, e encontra títulos muito além das 24 escolhas editoriais. */
            seasonalCollection: seasonalCollection,
            seasonalTerms: seasonalCollection ? seasonalCollection.terms.map(function (term) {
                return BuroDomain.foldAccents(term);
            }) : [],
            seasonal: [],
            seasonalTitleKeys: {},
            /* Uma única passagem serve à Home e à detecção. Este mapa é
               transitório e removido antes de o resultado entrar no cache. */
            artworkScan: detectPlaceholderArtwork ? BuroPlaceholderArtwork.create() : null,
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
        var seasonalName;
        var seasonalTitleKey;
        var orderCompare = function (left, right) {
            return Number(left.sortOrder) - Number(right.sortOrder) || String(left.id).localeCompare(String(right.id));
        };
        if (!item || ['MOVIE', 'SERIES'].indexOf(type) < 0) { return result; }
        if (result.artworkScan && (!result.sourceId || item.sourceId === result.sourceId)) {
            BuroPlaceholderArtwork.add(result.artworkScan, item.logoUrl);
        }
        if (!snapshotItemVisible(result, item)) { return result; }
        result.count += 1;
        if (result.seasonalTerms && result.seasonalTerms.length && result.seasonal.length < 18) {
            seasonalName = BuroDomain.foldAccents(item.name || '');
            seasonalTitleKey = homeTitleKey(item) || item.id;
            if (!result.seasonalTitleKeys[seasonalTitleKey] && result.seasonalTerms.some(function (term) {
                return seasonalName.indexOf(term) >= 0;
            })) {
                result.seasonalTitleKeys[seasonalTitleKey] = true;
                result.seasonal.push(item);
            }
        }
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

    function applyHomePlaceholderArtwork(result) {
        var sourceId = result && result.sourceId;
        var detected = result && result.artworkScan ?
            BuroPlaceholderArtwork.finish(result.artworkScan) : [];
        placeholderArtworkSourceId = sourceId || null;
        placeholderArtworkUrls = Object.create(null);
        detected.forEach(function (url) {
            placeholderArtworkUrls[placeholderArtworkKey(url)] = true;
        });
        if (result) { delete result.artworkScan; }
    }

    function homeResultItems(result) {
        var rows = [];
        var known = {};
        ['seasonal', 'currentReleases', 'previousReleases', 'recent', 'topRated', 'movies', 'series'].forEach(function (key) {
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
        var seasonal = result.seasonal || [];
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
            var item = byId[entry.itemId];
            var parent;
            if (!item) { return; }
            /* Android e Windows representam o progresso de episodio pela serie
               na Home. O episodio mais recente ainda fornece a barra correta,
               sem alterar o objeto persistido da serie. */
            if (item.contentType === 'EPISODE') {
                parent = item.categoryId ? byId[item.categoryId] : null;
                if (!parent || parent.contentType !== 'SERIES') { return; }
                item = transientLibraryItem(parent, entry.itemId);
            }
            if (!continuedIds[item.id]) {
                continuedIds[item.id] = true;
                continued.push(item);
            }
        });
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
        /*
          A prateleira do calendario, acima das escolhas do dia.

          E a razao de a Home parecer diferente hoje: enterra-la sob as fileiras
          de sempre anularia o proposito — mesma decisao do `XtreamDailyHome` do
          Windows, e o Android ja a tinha. Fica vazia na maior parte do ano, e
          `takeHomeItems` a descarta sozinha quando nao ha titulo que case.
        */
        seasonalRail(result, consumedIds, consumedTitles, rails);
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
        var detailsAction;
        var heroActions;
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
        detailsAction = hero.contentType === 'MOVIE' ? 'movie-details' :
            (hero.contentType === 'SERIES' ? 'series-details' :
                (hero.contentType === 'LIVE' ? 'live-details' : ''));
        /*
          A referência Windows deixa a Home cumprir sua função principal sem
          obrigar uma ida à ficha: filme e canal podem começar diretamente no
          Hero. Série continua abrindo detalhes, pois precisa escolher/resolver
          um episódio. O botão `play` reutiliza exatamente ResumeDecision,
          resolução tardia de URL, AVPlay e restauração visual já usados pelos
          cards — não existe um segundo caminho de reprodução nem URL no DOM.
        */
        heroActions = '<div class="action-row home-hero-actions">';
        if (hero.contentType !== 'SERIES') {
            heroActions += '<button class="button primary focusable" data-action="play" data-id="' +
                attr(hero.id) + '">▶ ' + t('watch') + '</button>';
        }
        if (detailsAction) {
            heroActions += '<button class="button ghost focusable" data-action="' + detailsAction +
                '" data-id="' + attr(hero.id) + '">' + t('viewDetails') + '</button>';
        }
        heroActions += '</div>';
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
            '</p>' + heroActions + '</div>';
        model.rails.forEach(function (rail) { content += homeRail(rail.title, rail.items, rail.key, rail.service, rail.badge); });
        homeStreamingShelves.forEach(function (shelf) { content += homeStreamingRail(shelf); });
        return content;
    }

    function loadHome(requestId) {
        BuroStorage.fold('items', collectHome, homeAccumulator(), function (result) {
            var all;
            var currentData;
            var currentHero;
            var nextData;
            var nextRotation;
            if (requestId !== homeRequestId || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                    !state.screenData || state.screenData.requestId !== requestId) { return; }
            applyHomePlaceholderArtwork(result);
            currentData = state.screenData;
            currentHero = currentData.heroRotation &&
                currentData.heroRotation[Number(currentData.heroIndex) || 0];
            all = homeResultItems(result);
            mergeItems(all);
            nextData = { kind: 'home', loading: false, result: result, heroIndex: 0, requestId: requestId };
            /*
              O cache diário aparece antes desta conferência. Se o IndexedDB
              terminar no mesmo instante em que chegam a sinopse e a arte do
              Hero, voltar sempre ao índice zero faz o destaque saltar e pode
              desenhar o fallback sobre a resposta recém-chegada. Preserve o
              título visível quando ele continua elegível no resultado novo.
            */
            if (currentHero && currentData.cached && !currentData.loading) {
                nextRotation = homeModel(nextData).rotation;
                nextRotation.some(function (item, index) {
                    if (item.id !== currentHero.id) { return false; }
                    nextData.heroIndex = index;
                    return true;
                });
            }
            state.screenData = nextData;
            rememberHome(result, nextData.heroIndex);
            focusIndex = 0;
            render();
            /* Depois de desenhar, não antes: a Home aparece com o que já tem e
               as capas entram conforme chegam. */
            hydrateHomeArtwork(state.screenData);
        }, function (error) {
            var current;
            if (requestId !== homeRequestId || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                    !state.screenData || state.screenData.requestId !== requestId) { return; }
            current = state.screenData;
            current.loading = false;
            current.error = friendlyError(error);
            /* Uma varredura que falhou não deixa Home guardada: a próxima visita
               precisa tentar de novo, e não herdar um resultado que não existe. */
            forgetHomeCache();
            focusIndex = 0;
            render();
        });
    }

    /*
      A Home é montada uma vez por dia, não a cada visita.

      Voltar a Início mostrava "Montando sua Home…" de novo, porque sair da
      seção limpa `screenData` e a varredura recomeçava do zero. Numa TV isso é
      caro — a varredura percorre o catálogo inteiro — e desnecessário: a
      seleção editorial é a do dia, então dentro do mesmo dia o resultado
      guardado serve.

      A varredura de fundo trocar o catálogo invalida o guardado, senão a Home
      ficaria mostrando ontem enquanto novos títulos chegam.
    */
    function startHomeLoad() {
        var requestId = ++homeRequestId;
        var fallback;
        if (homeCache && homeCache.day === localEditorialDay() && homeCache.result) {
            /*
              Mostra o guardado e confere por baixo.

              Servir o cache e parar por aí esconderia uma falha de leitura: a
              Home apareceria montada enquanto o banco estava inacessível. Então
              a varredura roda de qualquer forma — o que a visita economiza é a
              espera e a mensagem "Montando sua Home…", não a verificação.
            */
            state.screenData = {
                kind: 'home', loading: false, result: homeCache.result,
                heroIndex: homeCache.heroIndex || 0, requestId: requestId, cached: true
            };
            /*
              Uma varredura recente nao e refeita.

              A conferencia existe para nao esconder uma falha de leitura: a
              Home apareceria montada com o banco inacessivel. Isso vale para
              um cache de horas atras, nao para um que a abertura acabou de
              montar — ali a leitura ja aconteceu, e com sucesso.

              A abertura varria a mesma tabela duas vezes seguidas:
              prepareHomeForReveal montava a Home e gravava o cache, e esta
              funcao refazia tudo no quadro seguinte. Sao 450ms cada num PC,
              e varios segundos numa TV, gastos para chegar ao mesmo
              resultado que ja estava na tela.
            */
            if (Date.now() - (homeCache.at || 0) < HOME_CACHE_TRUST_MILLIS) { return; }
            window.setTimeout(function () { loadHome(requestId); }, 0);
            return;
        }
        fallback = homeAccumulator();
        state.items.forEach(function (item) { collectHome(fallback, item); });
        state.screenData = { kind: 'home', loading: true, result: fallback, heroIndex: 0, requestId: requestId };
        window.setTimeout(function () { loadHome(requestId); }, 0);
    }

    /* O que a Home montou hoje, para uma segunda visita não refazer a varredura. */
    /* O carimbo de tempo e o que permite a Home logo a seguir confiar no
       resultado em vez de refazer o mesmo trabalho. */
    function rememberHome(result, heroIndex) {
        homeCache = { day: localEditorialDay(), result: result, heroIndex: heroIndex || 0, at: Date.now() };
    }

    /* Catálogo novo, Home velha: o guardado deixa de valer. */
    /* Um catalogo novo invalida tudo o que foi lido dele: a Home, o baralho
       do Descobrir e o indice de nomes das Assinaturas. */
    function forgetHomeCache() { homeCache = null; forgetDiscoverCache(); forgetSubscriptionIndex(); }

    function elementWithinClass(element, className) {
        var current = element;
        while (current && current !== root) {
            if (current.classList && current.classList.contains(className)) { return true; }
            current = current.parentNode;
        }
        return false;
    }

    function scheduleHomeHeroRotation(data) {
        if (homeHeroTimer) { window.clearTimeout(homeHeroTimer); homeHeroTimer = null; }
        if (state.preferences.reducedMotion || !data.heroRotation || data.heroRotation.length <= 1) { return; }
        homeHeroTimer = window.setTimeout(function () {
            var current = focusables[focusIndex];
            var action = current && current.getAttribute('data-action');
            var id = current && current.getAttribute('data-id');
            var section = current && current.getAttribute('data-section');
            /*
              As ações do Hero vivem numa linha própria para igualar o Windows.
              Verificar só o pai direto deixa de reconhecer o foco nessa linha e
              permite que o destaque troque entre o foco e o ENTER. Percorrer os
              ancestrais mantém título, id e ação atômicos enquanto a pessoa
              decide entre Assistir e Ver detalhes.
            */
            var heroFocused = elementWithinClass(current, 'real-home-hero');
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

    function tmdbHeroDetails(metadata) {
        var minutes = Number(metadata && metadata.duration);
        return {
            synopsis: metadata && metadata.plot || null,
            genre: metadata && metadata.genre || null,
            duration: minutes > 0 ? Math.round(minutes) + ' min' : null,
            rating: metadata && metadata.rating || null,
            artworkUrl: metadata && metadata.posterUrl || null,
            backdropUrl: metadata && metadata.backdropUrl || null
        };
    }

    function mergeHeroDetails(provider, fallback) {
        provider = provider || {};
        fallback = fallback || {};
        return {
            synopsis: provider.synopsis || fallback.synopsis || null,
            genre: provider.genre || fallback.genre || null,
            duration: provider.duration || fallback.duration || null,
            rating: Number(provider.rating) > 0 ? provider.rating : fallback.rating,
            artworkUrl: provider.artworkUrl || fallback.artworkUrl || null,
            backdropUrl: provider.backdropUrl || fallback.backdropUrl || null
        };
    }

    /*
      O Windows enriquece o destaque mesmo quando a fonte é M3U. No Samsung, a
      fila antiga aceitava somente locators Xtream e por isso uma chave TMDb
      válida melhorava a ficha do filme, mas deixava a Home com a sinopse
      genérica. Este loader mantém o provedor em primeiro lugar e consulta o
      TMDb apenas quando faltam sinopse/arte ou quando a fonte não tem endpoint
      de detalhes. Tudo continua transitório e cancelável.
    */
    function loadHomeHeroDetails(source, tmdbKey, allowProvider, item, success, failure) {
        var providerRequest = null;
        var tmdbRequest = null;
        var providerDetails = null;
        var secret = null;
        var stopped = false;
        var finished = false;
        function publish(details) {
            if (stopped || finished) { return; }
            finished = true;
            secret = null;
            success(details || {});
        }
        function reject(error) {
            if (stopped || finished) { return; }
            finished = true;
            secret = null;
            failure(error);
        }
        function loadTmdb() {
            if (stopped || finished) { return; }
            if (!tmdbKey) {
                if (providerDetails) { publish(providerDetails); }
                else { reject({ code: 'TMDB_NOT_CONFIGURED' }); }
                return;
            }
            tmdbRequest = BuroTmdb.loadTitle(tmdbKey, item, item.contentType === 'SERIES',
                state.preferences.language, function (metadata) {
                    tmdbRequest = null;
                    publish(mergeHeroDetails(providerDetails, tmdbHeroDetails(metadata)));
                }, function (error) {
                    tmdbRequest = null;
                    if (providerDetails) { publish(providerDetails); }
                    else { reject(error); }
                });
        }
        if (allowProvider && source.type === 'XTREAM' && item.locator &&
                item.locator.kind === 'xtream' && item.locator.providerItemId) {
            try {
                secret = BuroStorage.secureGet(source.id);
                providerRequest = BuroXtream.loadHeroDetails(secret, item, function (details) {
                    providerRequest = null;
                    secret = null;
                    providerDetails = details || {};
                    if (providerDetails.synopsis && (providerDetails.backdropUrl || providerDetails.artworkUrl)) {
                        publish(providerDetails);
                    } else { loadTmdb(); }
                }, function () {
                    providerRequest = null;
                    secret = null;
                    loadTmdb();
                });
            } catch (ignoredProviderHero) {
                secret = null;
                loadTmdb();
            }
        } else { loadTmdb(); }
        return { abort: function () {
            if (stopped || finished) { return; }
            stopped = true;
            secret = null;
            if (providerRequest && providerRequest.abort) { providerRequest.abort(); }
            if (tmdbRequest && tmdbRequest.abort) { tmdbRequest.abort(); }
        } };
    }

    function scheduleHomeHeroEnrichment(data) {
        var source = state.activeSource;
        var sync;
        var candidates = data && data.heroRotation || [];
        var requestId = data && data.requestId;
        var tmdbKey = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        var allowProvider;
        if (homeEnrichmentTimer) { window.clearTimeout(homeEnrichmentTimer); homeEnrichmentTimer = null; }
        if (!source || !candidates.length) { return; }
        sync = catalogueSyncStatus(source);
        allowProvider = source.type === 'XTREAM' && !(sync && sync.state === 'RUNNING');
        if (!allowProvider && !tmdbKey) { return; }
        homeEnrichmentTimer = window.setTimeout(function () {
            homeEnrichmentTimer = null;
            if (state.screen !== 'SHELL' || state.section !== 'HOME' || state.screenData !== data ||
                    data.requestId !== requestId || !state.activeSource || state.activeSource.id !== source.id) { return; }
            try {
                BuroHeroEnrichment.start(source, candidates, {
                    modeKey: allowProvider ? 'provider-tmdb-fallback' : 'tmdb',
                    loadDetails: function (item, success, failure) {
                        return loadHomeHeroDetails(source, tmdbKey, allowProvider, item, success, failure);
                    },
                    onItem: function (item, enrichment) {
                        var current;
                        var liveData = state.screenData;
                        if (!enrichment || state.screen !== 'SHELL' || state.section !== 'HOME' ||
                                !liveData || liveData.kind !== 'home' || liveData.requestId !== requestId ||
                                !state.activeSource || state.activeSource.id !== source.id) { return; }
                        rememberArtwork(item.id, enrichment.artworkUrl);
                        current = liveData.heroRotation && liveData.heroRotation[Number(liveData.heroIndex) || 0];
                        if (current && current.id === item.id) { render(); }
                    }
                });
            } catch (ignoredHeroEnrichment) { /* O Hero conserva arte e texto de fallback. */ }
        }, 0);
    }

    /*
      A demonstração local é o mesmo catálogo fictício usado pelo Android.

      Não há URL, stream, provedor nem obra real nestes objetos. Manter os ids,
      três trilhos, formatos e progressos alinhados permite que a primeira Home
      ensine foco, rolagem e hierarquia antes de o usuário importar uma fonte.
    */
    function demoHomeCatalogue(year) {
        function item(id, titleKey, subtitleKey, artwork, progress, kind, format) {
            return {
                id: id,
                titleKey: titleKey,
                subtitleKey: subtitleKey,
                artwork: artwork,
                progress: progress,
                kind: kind || 'story',
                format: format || 'landscape'
            };
        }
        return {
            hero: item('demo:hero:quiet-orbit', 'demoHeroTitle', 'demoHeroSubtitle', 'aurora'),
            rails: [
                {
                    id: 'demo:rail:continue', title: t('demoContinueTitle'), badgeKey: 'demoBadge',
                    items: [
                        item('demo:continue:amber-archive', 'demoAmberTitle', 'demoAmberSubtitle', 'paper', 0.68),
                        item('demo:continue:prism-city', 'demoPrismTitle', 'demoPrismSubtitle', 'cobalt', 0.42),
                        item('demo:continue:green-signal', 'demoSignalTitle', 'demoSignalSubtitle', 'forest', 0.81),
                        item('demo:continue:violet-map', 'demoMapTitle', 'demoMapSubtitle', 'plum', 0.23)
                    ]
                },
                {
                    id: 'demo:rail:live', title: t('demoLiveTitle'), badgeKey: 'demoLiveBadge',
                    items: [
                        item('demo:live:north-studio', 'demoNorthTitle', 'demoNorthSubtitle', 'cobalt', 0.31, 'live'),
                        item('demo:live:solar-room', 'demoSolarTitle', 'demoSolarSubtitle', 'paper', 0.57, 'live'),
                        item('demo:live:field-notes', 'demoFieldTitle', 'demoFieldSubtitle', 'forest', 0.74, 'live'),
                        item('demo:live:violet-stage', 'demoStageTitle', 'demoStageSubtitle', 'plum', 0.46, 'live')
                    ]
                },
                {
                    id: 'demo:rail:editorial', title: t('demoEditorialTitle').replace('{year}', year), badgeKey: 'demoBadge',
                    items: [
                        item('demo:editorial:blue-frequency', 'demoFrequencyTitle', 'demoVisualSubtitle', 'cobalt', null, 'editorial', 'poster'),
                        item('demo:editorial:paper-sun', 'demoSunTitle', 'demoVisualSubtitle', 'paper', null, 'editorial', 'poster'),
                        item('demo:editorial:forest-code', 'demoForestTitle', 'demoVisualSubtitle', 'forest', null, 'editorial', 'poster'),
                        item('demo:editorial:ember-line', 'demoEmberTitle', 'demoVisualSubtitle', 'ember', null, 'editorial', 'poster'),
                        item('demo:editorial:soft-axis', 'demoAxisTitle', 'demoVisualSubtitle', 'aurora', null, 'editorial', 'poster'),
                        item('demo:editorial:plum-window', 'demoWindowTitle', 'demoVisualSubtitle', 'plum', null, 'editorial', 'poster')
                    ]
                }
            ]
        };
    }

    function demoHomeItemById(catalogue, id) {
        var found = catalogue.hero.id === id ? catalogue.hero : null;
        catalogue.rails.some(function (rail) {
            return rail.items.some(function (item) {
                if (item.id === id) { found = item; return true; }
                return false;
            });
        });
        return found || catalogue.hero;
    }

    function demoItemMetadata(item, year) {
        if (item.id === 'demo:hero:quiet-orbit') { return year + ' · ' + t('demoHeroMetadata'); }
        if (item.kind === 'live') { return year + ' · ' + t('demoLiveMetadata'); }
        return year + ' · ' + t('demoConceptMetadata');
    }

    function demoItemSynopsis(item) {
        if (item.id === 'demo:hero:quiet-orbit') { return t('demoHeroSynopsis'); }
        if (item.kind === 'live') { return t('demoLiveSynopsis'); }
        if (item.kind === 'editorial') { return t('demoEditorialSynopsis'); }
        return t('demoStorySynopsis');
    }

    function renderHome() {
        var data = state.screenData;
        var content;
        var demoCatalogue;
        var demoItem;
        var topbarExtra = '';
        var year = new Date().getFullYear();
        if (state.screenData && state.screenData.kind === 'demo-story') {
            demoCatalogue = demoHomeCatalogue(year);
            demoItem = demoHomeItemById(demoCatalogue, state.screenData.demoId);
            content = '<div class="demo-story"><span class="hero-kicker">' + t('demoBadge') + '</span>' +
                '<h2>' + escapeHtml(t(demoItem.titleKey)) + '</h2><p class="demo-story-subtitle">' +
                escapeHtml(t(demoItem.subtitleKey)) + '</p><p class="demo-story-meta">' +
                escapeHtml(demoItemMetadata(demoItem, year)) + '</p><p>' + escapeHtml(demoItemSynopsis(demoItem)) +
                '</p><p class="demo-no-playback">' + escapeHtml(t('demoStoryNoPlayback')) +
                '</p><div class="action-row detail-actions">' +
                '<button class="button primary focusable" data-action="source-add">' + t('addSource') + '</button>' +
                '<button class="button ghost focusable" data-action="back">' + t('back') + '</button></div></div>';
            shell(content, t('home'), true);
            return;
        }
        if (!state.sources.length) {
            demoCatalogue = demoHomeCatalogue(year);
            topbarExtra = '<div class="demo-notice">' + t('demoNotice') + '</div>';
            content = '<div class="hero living-hero"><span class="hero-kicker">' + t('demoBadge') + '</span><h2>' +
                t('demoHeroTitle') + '</h2><p class="hero-metadata">' + year + ' · ' + t('demoHeroMetadata') + '</p><p>' +
                t('demoHeroSynopsis') + '</p><button class="button primary focusable" data-action="demo-story" data-id="' +
                attr(demoCatalogue.hero.id) + '">' +
                t('demoViewStory') + '</button></div>' +
                demoCatalogue.rails.map(renderDemoRail).join('');
        } else {
            if (!data || data.kind !== 'home') { startHomeLoad(); data = state.screenData; }
            ensureHomeStreamingShelves();
            if (data.loading && !(data.result && data.result.count)) {
                content = '<div class="home-loading"><span class="boot-indicator"></span><h2>' + t('homeLoading') +
                    '</h2><p>' + t('homeLoadingBody') + '</p></div>';
            } else if (data.error && !(data.result && data.result.count)) {
                content = emptyState('!', t('couldNotLoad'), t('homeLoadError'), 'home-retry', t('retry'));
            } else {
                /*
                  A faixa de "montando" sai quando ha Home para ver.

                  Este ramo so e alcancado quando `data.result.count` existe —
                  ou seja, quando ja ha destaques e prateleiras desenhados. A
                  faixa ficava por cima deles anunciando um trabalho que a
                  tela de abertura ja tinha feito e anunciado, com percentagem
                  e contagem de titulos.

                  Dizer duas vezes a mesma coisa e pior do que dizer uma: a
                  segunda faz a pessoa procurar o que mudou.

                  A conferencia por baixo continua acontecendo; ela so deixa
                  de ocupar a primeira linha do Inicio. O erro, abaixo, fica —
                  aquele **e** noticia, porque diz que o que esta na tela pode
                  estar velho.
                */
                content =
                    (data.error ? '<div class="details-inline-warning home-cache-warning"><span>!</span><p>' +
                        t('homeCachedWarning') + '</p><button class="button ghost focusable" data-action="home-retry">' +
                        t('retry') + '</button></div>' : '') + renderRealHome(data);
            }
            /* O aviso da varredura virou um indicador compacto ao lado do
               título — ver `catalogueSyncChip`. Uma faixa inteira empurrava a
               Home para baixo por algo que roda sozinho e termina. */
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

    /*
      A leitura do Descobrir, guardada.

      Cada visita a aba varria o catalogo inteiro — 42.000 titulos, 453ms num
      PC e varios segundos numa TV. Sair para a Home e voltar refazia tudo,
      para chegar a mesma baralhada.

      O que se guarda e a **leitura**, nao o baralho: `buildDiscoverDeck`
      continua rodando a cada visita, entao o gosto aprendido nas cartas
      anteriores e as ja vistas seguem mudando o que sai. O que deixa de se
      repetir e a ida ao banco.
    */
    /* O baralho e montado aqui, e nao junto da leitura: assim a visita que
       reaproveita a leitura guardada continua tirando cartas novas. */
    function applyDiscoverResult(requestId, result) {
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
    }

    function rememberDiscoverRead(result) {
        discoverCache = { result: result, at: Date.now() };
    }

    function forgetDiscoverCache() { discoverCache = null; }

    function loadDiscover() {
        var requestId = ++discoverRequestId;
        ensureDiscoverSession();
        /*
          Uma leitura recente serve de novo.

          O mesmo intervalo da Home, e pela mesma razao: cobre a distancia de
          uma navegacao entre abas, nao a de uma sessao. A varredura de fundo
          e a troca de fonte descartam o guardado por outro caminho.
        */
        if (discoverCache && Date.now() - discoverCache.at < HOME_CACHE_TRUST_MILLIS) {
            applyDiscoverResult(requestId, discoverCache.result);
            return;
        }
        BuroStorage.fold('items', collectDiscover, discoverAccumulator(), function (result) {
            rememberDiscoverRead(result);
            applyDiscoverResult(requestId, result);
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
        return '<article class="discover-card ' + layer + (artworkFor(item) ? ' has-art' : '') +
            '" data-id="' + attr(item.id) + '"' + (layer === 'next' ? ' aria-hidden="true"' :
                ' aria-label="' + attr(item.name) + '"') + '>' +
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

    function renderDemoRail(rail) {
        return '<section class="demo-rail" data-demo-rail="' + attr(rail.id) + '">' +
            '<div class="section-heading demo-heading"><h2>' + escapeHtml(rail.title) + '</h2><p>' +
            escapeHtml(t(rail.badgeKey)) + '</p></div><div class="demo-card-row ' +
            (rail.items[0] && rail.items[0].format === 'poster' ? 'poster-row' : '') + '">' +
            rail.items.map(function (item) { return demoHomeCard(item, rail.badgeKey); }).join('') +
            '</div></section>';
    }

    function demoHomeCard(item, badgeKey) {
        var progress = item.progress == null ? '' : '<span class="demo-card-progress" aria-label="' +
            attr(Math.round(item.progress * 100) + '%') + '"><span style="width:' +
            Math.round(item.progress * 100) + '%"></span></span>';
        return '<button class="demo-media-card ' + attr(item.artwork) + ' ' + attr(item.format) +
            ' focusable" data-action="demo-story" data-id="' + attr(item.id) + '" aria-label="' +
            attr(t(item.titleKey) + ' · ' + t(item.subtitleKey)) + '"><span class="badge">' +
            escapeHtml(t(badgeKey)) + '</span><span class="demo-card-copy"><strong>' +
            escapeHtml(t(item.titleKey)) + '</strong><small>' + escapeHtml(t(item.subtitleKey)) +
            '</small></span>' + progress + '</button>';
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
            catalogueScopes[contentType] = {
                query: '', genre: null, service: null, year: null, minimumRating: null,
                /*
                  Ao Vivo comeca compacto; filme e serie, em poster.

                  Um canal nao tem capa 2:3. O que o provedor manda e um logo
                  quadrado ou horizontal, e desenha-lo numa caixa alta de
                  poster deixava a marca boiando no meio de um retangulo
                  vazio — e o canal sem logo virava um cartao alto so com
                  texto. O cartao compacto tem a proporcao que o material
                  tem.

                  Continua sendo so o padrao: quem preferir outro formato
                  troca no seletor de densidade, e a escolha vale para a aba.
                */
                layout: contentType === 'LIVE' ? 'compact' : 'poster',
                rows: undefined, total: null, hasMore: false, loading: false
            };
        }
        return catalogueScopes[contentType];
    }

    /*
      O índice de serviços: quais títulos da lista cada serviço carrega.

      Existe para as listas que arquivam por gênero — "Filmes | Ação",
      "Filmes | Drama" — onde categoria nenhuma nomeia um serviço e o seletor de
      Serviço ficava permanentemente desativado. As categorias não sabem
      responder "o que tem na Netflix"; o TMDb sabe.

      Construído uma vez por chave e região, e só quando alguém abre o seletor:
      são várias requisições e não vale gastá-las numa aba que talvez nunca use
      o filtro. Falha em silêncio — isto acrescenta um filtro a uma tela que
      funciona sem ele.
    */
    var serviceIndex = null;
    var serviceIndexBuiltFor = null;
    var serviceIndexLoading = false;
    var serviceIndexRequest = null;
    var serviceIndexRequestId = 0;

    function currentServiceIndex() {
        return serviceIndex || BuroServiceIndex.empty();
    }

    /*
      Os serviços públicos que viram atalhos no topo de Filmes/Séries.

      Ficam só em memória e separados por tipo: o diretório de filmes pode
      ter ordem e disponibilidade diferentes do de séries. A identidade nunca
      inclui a chave; segredo algum atravessa o estado de UI.
    */
    function catalogueProviderDirectory(contentType) {
        return catalogueProviderDirectories[contentType] || null;
    }

    function ensureCatalogueProviderDirectory(contentType) {
        var directoryState = catalogueProviderDirectory(contentType);
        var profileId = state.activeProfile && state.activeProfile.id || '';
        var region = activeTmdbRegion();
        var language = state.preferences.language;
        var identity = profileId + '|' + region + '|' + contentType + '|' + language;
        var key = BuroTmdb.keyForProfile(profileId);
        var kind = contentType === 'SERIES' ? 'SERIES' : 'MOVIES';
        if (!directoryState) { return; }
        if (!key) {
            if (directoryState.request && directoryState.request.abort) { directoryState.request.abort(); }
            directoryState.identity = '';
            directoryState.rows = [];
            directoryState.loading = false;
            directoryState.failed = false;
            directoryState.request = null;
            return;
        }
        if (directoryState.identity !== identity) {
            if (directoryState.request && directoryState.request.abort) { directoryState.request.abort(); }
            directoryState.identity = identity;
            directoryState.rows = [];
            directoryState.loading = false;
            directoryState.failed = false;
            directoryState.request = null;
        }
        if (directoryState.rows.length || directoryState.loading || directoryState.failed) { return; }
        directoryState.loading = true;
        directoryState.request = BuroTmdb.loadProviderDirectory(key, region, kind, language, function (rows) {
            if (directoryState.identity !== identity) { return; }
            directoryState.request = null;
            directoryState.loading = false;
            directoryState.rows = Array.isArray(rows) ? rows.slice(0, 12) : [];
            if (state.screen === 'SHELL' && !state.screenData &&
                    ((contentType === 'MOVIE' && state.section === 'MOVIES') ||
                    (contentType === 'SERIES' && state.section === 'SERIES'))) { render(); }
        }, function () {
            if (directoryState.identity !== identity) { return; }
            directoryState.request = null;
            directoryState.loading = false;
            directoryState.failed = true;
        });
    }

    function catalogueProviderShortcutsHtml(contentType) {
        var directoryState = catalogueProviderDirectory(contentType);
        var profileId = state.activeProfile && state.activeProfile.id || '';
        var expectedIdentity = profileId + '|' + activeTmdbRegion() +
            '|' + contentType + '|' + state.preferences.language;
        var configured = Boolean(BuroTmdb.keyForProfile(profileId));
        var rows;
        var scope;
        var open;
        if (!directoryState) { return ''; }
        if (!configured || directoryState.identity !== expectedIdentity) {
            window.setTimeout(function () { ensureCatalogueProviderDirectory(contentType); }, 0);
            return '';
        }
        if (!directoryState.rows.length && !directoryState.loading && !directoryState.failed) {
            window.setTimeout(function () { ensureCatalogueProviderDirectory(contentType); }, 0);
        }
        rows = directoryState.rows;
        if (!rows.length) { return ''; }
        scope = catalogueScope(contentType);
        open = scope.openPicker === 'provider-directory';
        return '<span class="picker-slot provider-directory-slot' + (open ? ' open' : '') + '">' +
            '<button class="scope-chip compact provider-directory-selector focusable" ' +
            'data-action="catalogue-pick-provider-directory" aria-haspopup="listbox" aria-expanded="' +
            (open ? 'true' : 'false') + '"><span class="provider-selector-copy"><small>' + escapeHtml(t('serviceSelector')) + ' · ' + rows.length +
            '</small><strong>' + escapeHtml(t('subscriptionsBrowse')) + ' ▾</strong></span></button>' +
            (open ? '<div class="catalogue-options provider-directory-options" role="listbox">' + rows.map(function (provider) {
                var logo = safeProviderLogoUrl(provider.logoUrl);
                var identity = BuroProviders.identityForLabel(provider.name);
                var mark = logo ? '<img src="' + attr(logo) + '" alt="">' :
                    providerBadge(identity || { mark: provider.name.charAt(0), label: provider.name, colour: '#343741' });
                return '<button class="option-chip provider-directory-option focusable" role="option" ' +
                    'data-action="catalogue-provider-shortcut" data-provider="' + attr(String(provider.id)) + '">' + mark + '<strong>' +
                    escapeHtml(provider.name) + '</strong></button>';
            }).join('') + (rows.length > OPTIONS_VISIBLE ? '<p class="options-count">' +
                escapeHtml(t('optionsTotal').replace('{count}', rows.length)) + '</p>' : '') + '</div>' : '') + '</span>';
    }

    function ensureServiceIndex(contentType) {
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        var region = activeTmdbRegion();
        var cacheKey = key ? region + '|' + contentType : null;
        var requestId;
        if (!key || serviceIndexLoading) { return; }
        if (serviceIndexBuiltFor === cacheKey && serviceIndex && !serviceIndex.isEmpty()) { return; }
        serviceIndexLoading = true;
        requestId = ++serviceIndexRequestId;
        /* Sem `render()` aqui: esta função é chamada de dentro do desenho da
           barra, e redesenhar no meio do desenho é recursão. O "procurando"
           aparece no próximo quadro, que os retornos abaixo provocam. */
        /*
          A lista inteira, e não `state.items`: aquela é a amostra do boot, e um
          índice construído sobre cento e vinte linhas casaria quase nada.

          Guardando só nome, ano e id — a varredura passa por dezenas de milhares
          de linhas e reter o item inteiro seria carregar o catálogo na memória
          da TV para usar três campos.
        */
        BuroStorage.fold('items', function (accumulator, item) {
            if (item && item.contentType === contentType &&
                    (!state.activeSource || item.sourceId === state.activeSource.id)) {
                accumulator.push({ id: item.id, name: item.name, year: item.year });
            }
            return accumulator;
        }, [], function (library) {
            if (requestId !== serviceIndexRequestId) { return; }
            if (!library.length) { serviceIndexLoading = false; render(); return; }
            serviceIndexRequest = BuroTmdb.loadServiceTitles(key, region, state.preferences.language,
                null, function (byService) {
                    var built = BuroServiceIndex.build(byService, library);
                    if (requestId !== serviceIndexRequestId) { return; }
                    serviceIndexLoading = false;
                    serviceIndexRequest = null;
                    if (!built.isEmpty()) { serviceIndex = built; serviceIndexBuiltFor = cacheKey; }
                    render();
                }, function () {
                    if (requestId !== serviceIndexRequestId) { return; }
                    serviceIndexLoading = false;
                    serviceIndexRequest = null;
                    render();
                });
        }, function () {
            if (requestId !== serviceIndexRequestId) { return; }
            serviceIndexLoading = false; render();
        });
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
    /* Ha alguma pergunta ativa alem de "mostre a aba"? A mesma lista que o ramo
       do vazio usa para decidir entre "nenhum titulo com estes filtros" e "o
       catalogo ainda nao chegou aqui". */
    function catalogueScopeIsFiltered(scope) {
        return Boolean(scope.query || scope.genre || scope.service ||
            scope.year != null || scope.minimumRating != null);
    }

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
        var providerDirectory = catalogueProviderShortcutsHtml(contentType);
        /*
          Cada chip carrega a sua propria lista, ancorada nele.

          A lista era acrescentada depois das duas barras, num bloco so: clicar
          em "Nota", la na direita, abria a janela debaixo de "Genero", la na
          esquerda. Numa TV isso se le como o seletor errado ter aberto.
        */
        var chips = pickerSlot('genre', contentType,
            '<button class="scope-chip compact filter-labelled focusable ' + (scope.genre ? 'selected' : '') +
            '" data-action="catalogue-pick-genre"><small>' + t('genreSelector') + '</small><strong>' +
            escapeHtml(genreLabel) + '</strong></button>');
        /*
          Três casos, na mesma ordem do Windows.

          A lista que nomeia serviços nas categorias é usada direto: é o
          arquivamento do próprio provedor e não custa requisição nenhuma.
          Faltando isso, o índice do TMDb responde. Faltando os dois, o chip
          aparece desativado dizendo o motivo — esconder deixava quem procura
          "só Netflix" sem saber se a função existe, está quebrada ou não se
          aplica à lista dele.
        */
        if (parts.hasProviders || !currentServiceIndex().isEmpty()) {
            chips += pickerSlot('service', contentType,
                '<button class="scope-chip compact filter-labelled focusable ' + (scope.service ? 'selected' : '') +
                '" data-action="catalogue-pick-service">' +
                '<small>' + t('serviceSelector') + '</small><strong>' + escapeHtml(serviceLabel) + '</strong></button>');
        } else {
            chips += '<span class="scope-chip compact filter-labelled disabled"><small>' + t('serviceSelector') +
                '</small><strong>' + escapeHtml(t(serviceIndexLoading ? 'servicesLoading' : 'servicesUnavailable')) +
                '</strong></span>';
            /*
              Pedido aqui e não ao abrir o seletor: sem índice o chip não é
              clicável, então não haveria como abri-lo. Ao vivo fica de fora —
              um canal não está "na Netflix", e o índice do TMDb é de filmes.
            */
            if (contentType !== 'LIVE') { ensureServiceIndex(contentType); }
        }
        if (scope.query || scope.genre || scope.service || scope.year != null || scope.minimumRating != null) {
            chips += '<button class="scope-chip clear focusable" data-action="catalogue-scope-reset">' +
                t('clearFilters') + '</button>';
        }
        /*
          Ano e nota numa linha própria, acima de gênero e serviço — a mesma
          ordem do aplicativo do Windows, e a mesma pergunta: primeiro "de
          quando", depois "de que tipo".

          Ao vivo não tem nenhum dos dois: um canal não tem ano de lançamento
          nem nota, e chips que não filtram nada só ocupam a tela.
        */
        return '<div class="catalogue-scope-bar catalogue-filter-strip' +
            (contentType === 'LIVE' ? '' : ' catalogue-year-bar') + '">' +
            (contentType === 'LIVE' ? '' : catalogueYearBar(contentType)) + providerDirectory + chips + '</div>';
    }

    /*
      Busca dentro da aba, no mesmo lugar do campo do Windows.

      Ela compartilha o predicado da prateleira com ano, nota, genero e
      servico. Assim a contagem, o bloco carregado e o texto digitado nunca
      descrevem consultas diferentes. O campo fica no catalogo — Pesquisa
      continua existindo para procurar entre todos os tipos de midia.
    */
    function catalogueSearchBar(contentType) {
        var scope = catalogueScope(contentType);
        return '<div class="catalogue-search"><span aria-hidden="true">⌕</span>' +
            '<input id="catalogue-query" class="focusable" type="search" maxlength="80"' +
            ' autocomplete="off" aria-label="' + attr(t('catalogueSearchHint')) + '" placeholder="' +
            attr(t('catalogueSearchHint')) + '" value="' + attr(scope.query || '') + '"></div>';
    }

    /*
      A faixa de tipos do catálogo do Windows, sem criar uma segunda navegação.

      Cada aba dispara a mesma ação `section` da Ribbon; portanto fonte ativa,
      cancelamento de pedidos, preferência persistida e restauração de foco
      continuam num único caminho. `aria-selected` descreve a seleção dentro do
      tablist, enquanto a Ribbon conserva `aria-current="page"`.
    */
    function catalogueToolbar(contentType) {
        var tabs = [
            { type: 'LIVE', section: 'LIVE', label: 'live' },
            { type: 'MOVIE', section: 'MOVIES', label: 'movies' },
            { type: 'SERIES', section: 'SERIES', label: 'series' }
        ];
        return '<div class="catalogue-toolbar"><div class="catalogue-type-tabs" role="tablist" aria-label="' +
            attr(t('catalogue')) + '">' + tabs.map(function (tab) {
                var selected = tab.type === contentType;
                return '<button class="catalogue-type-tab focusable ' + (selected ? 'selected' : '') +
                    '" role="tab" aria-selected="' + (selected ? 'true' : 'false') +
                    '" data-action="section" data-section="' + tab.section + '">' +
                    escapeHtml(t(tab.label)) + '</button>';
            }).join('') + '</div>' + catalogueSearchBar(contentType) + '</div>';
    }

    /* As notas mínimas oferecidas. Estrelas inteiras e não um controle contínuo:
       a nota do provedor é grosseira e "pelo menos quatro estrelas" é a pergunta
       que as pessoas realmente fazem. Mesma escolha do Windows. */
    var CATALOGUE_RATINGS = [null, 9, 8, 7, 6, 5];

    /* Quantas opções cabem na janela do seletor: 380px de altura por 56px de
       cada chip, e é a partir daí que a contagem no rodapé passa a valer a pena.
       Acompanha `.catalogue-options` no CSS. */
    var OPTIONS_VISIBLE = 6;

    function catalogueYearBar(contentType) {
        var scope = catalogueScope(contentType);
        var currentYear = new Date().getFullYear();
        var yearLabel = scope.year == null ? t('chooseYear') : String(scope.year);
        var ratingLabel = scope.minimumRating == null ? t('anyRating') :
            t('ratingAtLeast').replace('{rating}', scope.minimumRating);
        return '<button class="scope-chip compact focusable ' + (scope.year == null ? 'selected' : '') +
            '" data-action="catalogue-year-all"><strong>' + t('allYears') + '</strong></button>' +
            '<button class="scope-chip compact focusable ' + (scope.year === currentYear ? 'selected' : '') +
            '" data-action="catalogue-year-current"><strong>' +
            escapeHtml(t('releasesIn').replace('{year}', currentYear)) + '</strong></button>' +
            pickerSlot('year', contentType,
                '<button class="scope-chip compact focusable ' +
                (scope.year != null && scope.year !== currentYear ? 'selected' : '') +
                '" data-action="catalogue-pick-year"><strong>' + escapeHtml(yearLabel) + ' ▾</strong></button>') +
            pickerSlot('rating', contentType,
                '<button class="scope-chip compact focusable ' + (scope.minimumRating != null ? 'selected' : '') +
                '" data-action="catalogue-pick-rating"><strong>' + escapeHtml(ratingLabel) + ' ▾</strong></button>') +
            '';
    }

    /*
      As opções de um seletor, todas visíveis.

      Ciclar o valor com ENTER escondia o que existia: para chegar ao terceiro
      gênero eram três toques às cegas. Aqui o chip abre a lista embaixo dele e
      cada opção é um alvo próprio do D-pad, como no aplicativo do Windows.

      Uma lista aberta por vez — duas ocupariam a tela inteira numa TV.
    */
    /*
      Um chip com a sua lista ancorada nele.

      A lista fica dentro do contentor do chip e posicionada em relacao a ele,
      entao ela abre onde o dedo tocou. Antes era um bloco solto depois das duas
      barras: clicar em "Nota", na direita, abria a janela debaixo de "Genero",
      na esquerda.
    */
    function pickerSlot(picker, contentType, chipHtml) {
        var scope = catalogueScope(contentType);
        var open = scope.openPicker === picker;
        return '<span class="picker-slot' + (open ? ' open' : '') + '">' + chipHtml +
            (open ? catalogueOptionsHtml(contentType) : '') + '</span>';
    }

    function catalogueOptionsHtml(contentType) {
        var scope = catalogueScope(contentType);
        var open = scope.openPicker;
        var options = [];
        var currentYear = new Date().getFullYear();
        var categories;
        var parts;
        if (!open) { return ''; }
        categories = sourceCategories(contentType);
        parts = BuroProviders.split(categories);
        if (open === 'year') {
            options.push({ value: null, label: t('allYears'), selected: scope.year == null });
            catalogueYears(contentType).forEach(function (year) {
                options.push({ value: year, label: String(year), selected: scope.year === year });
            });
        } else if (open === 'rating') {
            CATALOGUE_RATINGS.forEach(function (rating) {
                options.push({
                    value: rating,
                    label: rating == null ? t('anyRating') : t('ratingAtLeast').replace('{rating}', rating),
                    selected: scope.minimumRating === rating
                });
            });
        } else if (open === 'genre') {
            options.push({ value: null, label: t('allGenres'), selected: !scope.genre });
            parts.genres.forEach(function (row) {
                options.push({ value: row.id, label: row.label, selected: scope.genre === row.id });
            });
        } else if (open === 'service') {
            options.push({ value: null, label: t('allServices'), selected: !scope.service });
            if (parts.hasProviders) {
                parts.providers.forEach(function (row) {
                    options.push({ value: row.label, label: row.label, selected: scope.service === row.label });
                });
            } else {
                /* Do índice: o rótulo leva a contagem, que é o que permite julgar
                   se vale filtrar por ele — "Netflix (128)" diz mais do que
                   "Netflix" numa lista onde o cruzamento pode ter casado pouco. */
                currentServiceIndex().services().forEach(function (label) {
                    options.push({
                        value: label,
                        label: label + ' (' + currentServiceIndex().countFor(label) + ')',
                        selected: scope.service === label
                    });
                });
            }
        } else if (open === 'density') {
            CATALOGUE_LAYOUTS.forEach(function (layout) {
                options.push({
                    value: layout, label: catalogueDensityLabel(layout),
                    selected: (scope.layout || 'poster') === layout
                });
            });
        }
        if (!options.length) { return ''; }
        /*
          A contagem no rodapé quando a lista não cabe na janela.

          A janela mostra cerca de seis opções e rola. Sem dizer quantas
          existem, seis numa TV se leem como a lista inteira — foi exatamente o
          relato: "em genero falta todos os generos", com dezesseis gêneros
          presentes e seis visíveis. A janela continua pequena de propósito;
          o que faltava era avisar que há mais abaixo.
        */
        return '<div class="catalogue-options" role="listbox">' + options.map(function (option) {
            return '<button class="option-chip focusable ' + (option.selected ? 'selected' : '') +
                '" role="option" aria-selected="' + (option.selected ? 'true' : 'false') +
                '" data-action="catalogue-option" data-picker="' + attr(open) +
                '" data-value="' + attr(option.value == null ? '' : option.value) + '">' +
                escapeHtml(option.label) + '</button>';
        }).join('') +
            (options.length > OPTIONS_VISIBLE ? '<p class="options-count">' +
                escapeHtml(t('optionsTotal').replace('{count}', options.length)) + '</p>' : '') +
            '</div>';
    }

    /* Abre a lista de um seletor, ou a fecha se já era ela que estava aberta. */
    function toggleCataloguePicker(picker) {
        var scope = catalogueScope(currentCatalogueType());
        scope.openPicker = scope.openPicker === picker ? null : picker;
        render();
    }

    /* Aplica a opção escolhida e fecha a lista. */
    function chooseCatalogueOption(picker, rawValue) {
        var scope = catalogueScope(currentCatalogueType());
        var value = rawValue === '' ? null : rawValue;
        if (picker === 'year') {
            scope.year = value == null ? null : Number(value);
        } else if (picker === 'rating') {
            scope.minimumRating = value == null ? null : Number(value);
        } else if (picker === 'genre') {
            scope.genre = value;
            /* Gênero e serviço se excluem: um título pertence a uma categoria só,
               então filtrar pelos dois só poderia esvaziar a tela. */
            if (value) { scope.service = null; }
        } else if (picker === 'service') {
            scope.service = value;
            if (value) { scope.genre = null; }
        } else if (picker === 'density') {
            /* A densidade não muda o resultado, só como ele é desenhado, então a
               página carregada continua valendo. */
            scope.layout = value || 'poster';
            scope.openPicker = null;
            render();
            return;
        }
        scope.openPicker = null;
        /* Filtro novo, prateleira do zero: o que já estava carregado respondia a
           outra pergunta.

           `loading` também volta a falso. Uma carga em andamento responde ao
           filtro antigo e será descartada pelo `requestId` quando voltar; deixar
           a marca de pé fazia a prateleira nunca pedir a carga nova, e a tela
           ficava presa no resultado sem filtro. */
        scope.rows = undefined;
        scope.loading = false;
        /* Filtro novo, primeira pagina: continuar na pagina 40 de uma lista
           que agora tem 3 mostraria o vazio como se fosse resposta. */
        scope.page = 0;
        render();
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
        var watched;
        var marks;
        var metadata = mediaMetadata(item);
        var art = artworkHtml(item, 'media-art');
        var action = item.contentType === 'MOVIE' ? 'movie-details' :
            (item.contentType === 'SERIES' ? 'series-details' :
                (item.contentType === 'LIVE' ? 'live-details' : 'play'));
        /* Mesmo sem URL, o poster conserva a caixa 2:3. Isso evita que a grade
           salte enquanto a arte chega e deixa a ausencia visivel sem fabricar
           uma capa que o provedor nao forneceu. */
        if (poster && !art) {
            art = '<span class="media-art media-art-placeholder" aria-hidden="true"></span>';
        }
        /*
          O selo diz o que so ele sabe, e some quando nao tem o que dizer.

          Ele carregava o `contentType` escrito — MOVIE, SERIES — sobre a capa de
          todo cartao. Numa aba chamada Filmes isso repete o cabecalho em cada
          item e cobre a arte que a pessoa esta olhando para reconhecer.

          O que fica sao as duas marcas que a capa nao mostra sozinha: ★ do
          favorito e ✓ do assistido. Sem nenhuma delas o span inteiro sai, senao
          restaria uma caixa vazia com borda dourada sobre a imagem.

          O separador " · " so aparece entre as duas, e nao mais depois da
          ultima: ele existia para separar da palavra que saiu.
        */
        watched = Boolean(playback && playback.completed);
        marks = (favorite ? '★' : '') + (favorite && watched ? ' · ' : '') + (watched ? '✓' : '');
        return '<button class="media-card focusable ' + (poster ? 'poster' : '') + ' ' + layout +
            (artworkFor(item) ? ' has-art' : '') + '" data-action="' + action + '" data-id="' + attr(item.id) + '">' +
            art + (marks ? '<span class="badge">' + marks + '</span>' : '') + '<h3>' +
            escapeHtml(item.name) + '</h3><p>' + metadata + '</p>' +
            (playback ? '<span class="media-progress"><i style="width:' + playback.percent.toFixed(2) + '%"></i></span>' : '') + '</button>';
    }

    /*
      `wrap` transforma a fileira numa grade.

      As prateleiras da Home são uma linha por assunto e rolam com o foco, então
      quebrar linha ali misturaria os assuntos. O catálogo é o contrário: uma
      página de duzentos títulos desenhada como uma fileira só mostrava sete e
      escondia o resto atrás de `overflow: hidden`, deixando meia tela vazia.
    */
    function mediaCards(items, layout, wrap) {
        layout = CATALOGUE_LAYOUTS.indexOf(layout) >= 0 ? layout : 'poster';
        items = items.filter(itemVisible);
        if (!items.length) { return emptyState('B', t('error'), t('unavailable'), '', ''); }
        return '<div class="card-row ' + (wrap ? 'card-grid ' : '') + 'catalogue-layout-' + layout + '">' +
            items.map(function (item) {
            return mediaCard(item, layout);
        }).join('') + '</div>';
    }

    /*
      A segunda linha do cartao.

      Num canal ao vivo ela saia com o nome da fonte — "IPTV BURO" repetido em
      cada cartao, que nao distingue nada quando ha uma fonte so e vira ruido
      mesmo quando ha varias. Um canal nao tem ano nem nota; se nao houver
      programa no ar para mostrar, e melhor a linha ficar vazia do que
      carregar uma palavra que nao informa.
    */
    function mediaMetadata(item) {
        var parts = [];
        /* Cada parte entra ja escapada: a funcao passou a devolver HTML por causa
           da estrela colorida, entao escapar so na saida deixaria de valer. Os
           valores aqui sao todos numericos e passam por `Number`, mas escapar
           mesmo assim mantem a regra visivel em vez de depender disso. */
        if (item.contentType === 'EPISODE' && item.locator) {
            if (Number(item.locator.season) > 0) { parts.push('T' + Number(item.locator.season)); }
            if (Number(item.locator.episode) > 0) { parts.push('E' + Number(item.locator.episode)); }
        }
        if (Number(item.year) > 0) { parts.push(String(Number(item.year))); }
        /*
          A estrela so fica dourada quando a nota a justifica.

          Antes toda nota saia em dourado, entao um 2.9 tinha o mesmo destaque
          visual de um 4.8 e a cor deixava de informar. O corte fica em 4 de 5, a
          faixa que o usuario pediu: abaixo dela a nota e escrita na cor do resto
          da linha, acima ela se ilumina.

          A escala do provedor varia — alguns mandam 0-10 — entao a comparacao e
          feita na fracao, e nao no numero cru: `>= 0.8` do maximo vale para as
          duas escalas sem precisar adivinhar qual chegou.
        */
        if (Number(item.rating) > 0) {
            parts.push('<span class="' + (ratingIsHigh(item.rating) ? 'rating-high' : 'rating-plain') +
                '">★ ' + escapeHtml(Number(item.rating).toFixed(1)) + '</span>');
        }
        if (parts.length) { return parts.join(' · '); }
        return item && item.contentType === 'LIVE' ? '' : escapeHtml('IPTV BURO');
    }

    /*
      A nota esta no alto da propria escala?

      O provedor manda 0-5 numa lista e 0-10 noutra, as vezes na mesma conta. Um
      corte fixo em 4 marcaria como alta uma nota 4 de 10, que e baixa. Comparar
      a fracao resolve sem adivinhar: 4 de 5 e 8 de 10 dao a mesma fracao.
    */
    function ratingIsHigh(value) {
        var numeric = Number(value);
        var scale = numeric > 5 ? 10 : 5;
        if (!isFinite(numeric) || numeric <= 0) { return false; }
        return numeric / scale >= 0.8;
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

    function categoryLoadMoreControl(data) {
        var loaded;
        var total;
        if (!data.catalogueHasMore && !data.catalogueRemoteHasMore && !data.catalogueLoadingMore) { return ''; }
        loaded = (data.items || []).length;
        total = Math.max(loaded, Number(data.catalogueTotalCount) || loaded);
        return '<div class="catalogue-progressive" aria-live="polite">' +
            '<button class="button primary focusable" data-action="category-load-more"' +
            (data.catalogueLoadingMore ? ' disabled aria-busy="true"' : '') + '>' +
            (data.catalogueLoadingMore ? t('loadingMoreCatalogue') : t('loadMoreCatalogue')) +
            '</button><span>' + loaded + ' / ' + total + '</span></div>';
    }

    function renderFilteredCategory(data) {
        var filterBar = catalogueFilterBar(data.items || [], data);
        var selectedFilter = data.catalogueFilter || { genre: null, year: null, sort: 'provider' };
        /* Filtra antes de agrupar, como o repositorio Windows: se a primeira
           copia nao satisfaz ano/genero, uma copia valida ainda pode aparecer.
           A ordenacao visual vem depois e nao troca a variante escolhida. */
        var filtered = BuroDomain.applyCatalogueFilter(data.items || [], {
            genre: selectedFilter.genre, year: selectedFilter.year, sort: 'provider'
        });
        if (state.preferences.collapseDuplicateTitles !== false) {
            filtered = BuroDomain.collapseShelfDuplicates(filtered);
        }
        filtered = BuroDomain.applyCatalogueFilter(filtered, { sort: selectedFilter.sort });
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
            ) + categoryLoadMoreControl(data);
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
            shell(renderLiveDetails(state.screenData.parent, state.screenData.schedule || [],
                Boolean(state.screenData.epgLoading)), state.screenData.parent.name, true);
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
            /*
              A aba abre na prateleira, não numa lista de categorias.

              O aplicativo do Windows abre "Filmes" já com os pôsteres e põe as
              categorias na barra de filtros — que é onde elas servem como
              pergunta ("só Netflix", "só Ação") em vez de como um degrau a mais
              antes de ver qualquer coisa. Com 42 mil títulos, a lista de
              categorias era uma tela inteira que ninguém queria ver.

              As categorias continuam alcançáveis: escolher um gênero na barra é
              o mesmo que abrir aquela categoria.
            */
            shell(catalogueToolbar(contentType) + catalogueScopeBar(contentType, categories) +
                catalogueShelf(contentType, categories), t(titleKey), true);
        }());
    }

    /*
      Os títulos desta aba, já filtrados, paginados como o resto do catálogo.

      Lê de `state.items`, que é o que a Home também usa: os itens que a
      varredura de fundo já trouxe. Uma aba aberta antes da varredura terminar
      mostra o que existe e cresce sozinha conforme o resto chega.
    */
    /*
      O predicado desta aba, do jeito que o banco vai aplicá-lo.

      Devolvido como função para que a contagem e a página usem exatamente a
      mesma regra: um total que não bate com o que a página mostra é pior do que
      não mostrar total nenhum.
    */
    function catalogueMatcher(contentType, categories) {
        var scope = catalogueScope(contentType);
        var sourceId = state.activeSource && state.activeSource.id;
        var needle = BuroDomain.foldAccents(BuroDomain.trim(scope.query || ''));
        var allowed = null;
        var allowedItems = null;
        var snapshot = catalogueVisibilitySnapshot();
        if (scope.service) {
            allowed = {};
            BuroProviders.categoryIdsForLabel(categories, scope.service).forEach(function (id) {
                allowed[id] = true;
            });
            /*
              Nenhuma categoria com esse rótulo: o serviço veio do índice, que
              responde por título e não por categoria. Filtrar pelo id do item é
              o mesmo filtro visto de outro ângulo.
            */
            if (!Object.keys(allowed).length) {
                allowed = null;
                allowedItems = {};
                currentServiceIndex().idsFor(scope.service).forEach(function (id) {
                    allowedItems[id] = true;
                });
            }
        }
        return function (item) {
            if (!item || item.contentType !== contentType) { return false; }
            if (sourceId && item.sourceId !== sourceId) { return false; }
            if (needle && BuroDomain.foldAccents(item.name || '').indexOf(needle) < 0) { return false; }
            if (!snapshotItemVisible(snapshot, item)) { return false; }
            if (allowed && !allowed[item.categoryId]) { return false; }
            if (allowedItems && !allowedItems[item.id]) { return false; }
            if (scope.genre && item.categoryId !== scope.genre) { return false; }
            if (scope.year != null && Number(item.year) !== scope.year) { return false; }
            if (scope.minimumRating != null && !(Number(item.rating) >= scope.minimumRating)) { return false; }
            return true;
        };
    }

    /* Um predicado novo para cada passada no IndexedDB. Contagem e pagina
       percorrem o store separadamente; compartilhar `seen` entre as duas faria
       a contagem consumir todas as chaves e devolver uma pagina vazia. */
    function catalogueDuplicateMatcher(matcher) {
        var seen = Object.create(null);
        /*
          Com as listas juntadas, os repetidos tem de sair mesmo que a pessoa tenha
          desligado o agrupamento por qualidade.

          Sao duas coisas diferentes com a mesma aparencia: colapsar copias do mesmo
          provedor e uma escolha sobre qualidades, enquanto mostrar o mesmo filme uma
          vez por assinatura e a duplicacao que juntar as listas existe para acabar.
        */
        if (state.preferences.collapseDuplicateTitles === false && !mergeEverySource()) {
            return matcher;
        }
        return function (item) {
            var key;
            if (!matcher(item)) { return false; }
            key = BuroDomain.shelfItemDeduplicationKey(item);
            if (!key) { return true; }
            if (Object.prototype.hasOwnProperty.call(seen, key)) { return false; }
            seen[key] = true;
            return true;
        };
    }

    /*
      Carrega uma página da prateleira direto do banco.

      `state.items` é uma amostra de HOME_CATALOG_LIMIT linhas lida no boot para
      a Home ter o que desenhar de imediato. Filtrar essa amostra mostrava 93
      filmes de um catálogo de dezenas de milhares — foi o que apareceu na TV.
      O banco é quem sabe o catálogo inteiro, então é dele que a prateleira lê.

      Contagem e página vêm da mesma regra e do mesmo pedido: primeiro o total,
      que é o que permite dizer "página 1 de 230", depois a fatia visível.
    */
    /*
      Vai para uma pagina, sem deixar sair da lista.

      O indice e preso entre zero e a ultima: um salto de dez a partir da
      pagina 3 nao deve cair no vazio, deve cair na primeira. E um salto para
      a frente que passaria do fim para na ultima, que e onde a pessoa queria
      chegar quando apertou.
    */
    function goToCataloguePage(contentType, target) {
        var scope = catalogueScope(contentType);
        var pages = Math.max(1, Math.ceil((Number(scope.total) || 0) / CATALOGUE_BLOCK_SIZE));
        var page = BuroDomain.clamp(Number(target) || 0, 0, pages - 1);
        if (page === (Number(scope.page) || 0) && scope.rows !== undefined) { return; }
        scope.page = page;
        /* A pagina nova substitui a antiga em vez de se somar a ela: e o que
           mantem uma pagina so no DOM. */
        scope.rows = undefined;
        scope.loading = false;
        render();
    }

    function loadCatalogueShelf(contentType, more) {
        var categories = sourceCategories(contentType);
        var scope = catalogueScope(contentType);
        var matcher = catalogueMatcher(contentType, categories);
        var requestId;
        var loaded = more && scope.rows ? scope.rows : [];
        /* Com paginas, o deslocamento vem do indice; "carregar mais" deixou
           de existir, mas o parametro fica porque a retomada por cursor
           interna ainda o usa. */
        var offset = more ? loaded.length : (Number(scope.page) || 0) * CATALOGUE_BLOCK_SIZE;
        /*
          Um "carregar mais" sobre uma carga em andamento é ignorado: são a mesma
          pergunta, e a segunda só duplicaria o bloco.

          Um filtro novo, não. Recusar aqui deixava a carga antiga terminar e
          escrever o resultado *sem* o filtro — escolher um serviço enquanto a
          prateleira ainda carregava não filtrava nada, e nada tentava de novo
          depois. O `requestId` abaixo é o que faz a carga antiga ser descartada
          quando ela voltar.
        */
        if (scope.loading && more) { return; }
        requestId = ++catalogueShelfRequestId;
        scope.loading = true;
        if (!more) { scope.total = null; }

        function withTotal(total) {
            if (requestId !== catalogueShelfRequestId) { return; }
            BuroStorage.wherePage('items', catalogueDuplicateMatcher(matcher), offset, CATALOGUE_BLOCK_SIZE, function (result) {
                var fresh = result.rows || [];
                if (requestId !== catalogueShelfRequestId) { return; }
                scope.loading = false;
                scope.total = total;
                scope.rows = loaded.concat(fresh);
                /* `hasMore` vem do cursor e não de uma conta com o total: o total
                   é de quando a contagem rodou, e o catálogo pode ter crescido
                   desde então enquanto a varredura de fundo trabalha. */
                scope.hasMore = Boolean(result.hasMore);
                /* Os itens carregados entram no estado para que capa, favorito e
                   progresso encontrem o mesmo objeto que o resto do app usa. */
                mergeItems(fresh);
                render();
                hydrateShelfArtwork(contentType, fresh);
            }, function () {
                if (requestId !== catalogueShelfRequestId) { return; }
                scope.loading = false;
                scope.rows = loaded;
                scope.hasMore = false;
                render();
            });
        }

        /* A contagem roda uma vez por filtro, não a cada bloco: ela percorre o
           store inteiro, e repetir isso a cada dez títulos seria o oposto de
           aliviar a TV. */
        if (more && scope.total != null) { withTotal(scope.total); return; }
        BuroStorage.countWhere('items', catalogueDuplicateMatcher(matcher), withTotal, function () {
            if (requestId !== catalogueShelfRequestId) { return; }
            scope.loading = false;
            scope.rows = [];
            scope.total = 0;
            scope.hasMore = false;
            render();
        });
    }

    /*
      As capas dos títulos desta página, pelas categorias que eles ocupam.

      Só enquanto a prateleira é o que está na tela. Abrir uma categoria troca a
      tela mas não cancela a busca da página que estava carregando, e a resposta
      dela chegava depois — pedindo arte de categorias que ninguém está mais
      olhando, e concorrendo com a requisição da categoria aberta.
    */
    function hydrateShelfArtwork(contentType, rows) {
        var byId = {};
        var seen = {};
        var wanted = [];
        /*
          Só enquanto a prateleira é o que está na tela.

          A guarda anterior recusava qualquer `screenData` e com isso recusava a
          própria prateleira — que é uma seção do shell sem screenData de
          categoria, mas passa por aqui logo depois de carregar. O efeito foi
          cartões sem capa nenhuma. O que precisa ser evitado é outra coisa:
          abrir uma categoria ou um detalhe deixa o pedido da prateleira no ar, e
          a resposta dele não deve ir buscar arte para uma tela que já saiu.
        */
        if (state.screen !== 'SHELL') { return; }
        if (state.section !== 'LIVE' && state.section !== 'MOVIES' && state.section !== 'SERIES') { return; }
        if (state.screenData) { return; }
        state.categories.forEach(function (category) { byId[category.id] = category; });
        (rows || []).forEach(function (item) {
            var category = byId[item.categoryId];
            if (artworkFor(item) || !category || seen[category.id]) { return; }
            seen[category.id] = true;
            wanted.push(category);
        });
        wanted.slice(0, HOME_ARTWORK_CATEGORY_LIMIT).forEach(function (category) {
            hydrateCategoryArtwork(category);
        });
    }

    /*
      Aviso de que a lista ainda nao tem as capas gravadas.

      Sem isto o sintoma — prateleira de cartoes de texto — nao distingue tres
      causas muito diferentes: o provedor nao mandou arte, a peneira de
      credencial recusou, ou as linhas sao antigas e ainda nao foram regravadas.
      A terceira e a comum depois de uma atualizacao do aplicativo, e a saida e
      um botao, nao esperar. Dizer isso poupa a pessoa de concluir que o app
      esta quebrado.

      Some assim que a maioria da pagina tem capa, que e quando a resposta ja
      esta na tela e o aviso viraria ruido.
    */
    function shelfArtworkNote(rows) {
        var withArt = 0;
        if (!rows || rows.length < 4) { return ''; }
        rows.forEach(function (item) { if (artworkFor(item)) { withArt += 1; } });
        if (withArt * 2 >= rows.length) { return ''; }
        return '<button class="shelf-art-note focusable" data-action="catalogue-refresh">' +
            escapeHtml(t('artworkMissingHint')) + '</button>';
    }

    /* Poster, compacto ou lista — o mesmo seletor de densidade do Windows. */
    function catalogueDensityLabel(layout) {
        if (layout === 'compact') { return t('catalogueCompact'); }
        if (layout === 'list') { return t('catalogueList'); }
        return t('cataloguePoster');
    }

    /*
      A prateleira cresce em blocos, em vez de paginar de duzentos em duzentos.

      Duzentos cartões de uma vez é DOM demais para uma TV: montar a página
      prende o controle por um instante e a memória sobe de degrau. Um punhado
      por vez desenha na hora, e quem chega ao fim pede o próximo — que é como um
      catálogo de quarenta mil títulos cabe num aparelho modesto.

      "Carregar mais" é um botão e não um gatilho por posição de rolagem: numa TV
      a rolagem acompanha o foco, então chegar ao fim da lista é exatamente o
      foco chegar ao último cartão, e ali o próximo passo do D-pad já é o botão.
    */
    function catalogueShelf(contentType, categories) {
        var scope = catalogueScope(contentType);
        var layout = scope.layout || 'poster';
        var total = Number(scope.total) || 0;
        var rows = scope.rows || [];
        var heading;
        if (scope.rows === undefined && !scope.loading) {
            window.setTimeout(function () { loadCatalogueShelf(contentType, false); }, 0);
        }
        heading = '<div class="section-heading catalogue-shelf-heading"><h2>' + t('catalogue') + '</h2>' +
            pickerSlot('density', contentType,
                '<button class="scope-chip compact focusable" data-action="catalogue-pick-density"><strong>' +
                escapeHtml(catalogueDensityLabel(layout)) + ' ▾</strong></button>') +
            shelfArtworkNote(rows) +
            /* O total antigo some junto com as linhas antigas: enquanto a
               leitura nova corre, "0 de 9056" conta uma historia que ja nao vale
               — 9056 era a resposta da pergunta anterior. */
            '<p>' + (scope.total == null || (scope.rows === undefined && catalogueScopeIsFiltered(scope)) ? '' : escapeHtml(t('shelfLoadedOf')
                .replace('{loaded}', rows.length).replace('{total}', total))) + '</p></div>';
        /*
          Um filtro escolhido e ainda nao lido nao e um filtro sem resultado.

          chooseCatalogueOption limpa as linhas do escopo e devolve loading a
          falso antes de agendar a carga nova, entao o quadro seguinte chega
          aqui com linhas vazias e loading falso — que era indistinguivel de
          "leu e nao achou nada". A tela escrevia "Nenhum titulo com estes
          filtros" durante os segundos da leitura, e o contador ainda mostrava
          o total da pergunta anterior: "0 de 9056".

          Linhas por ler separa nunca-lido de lido-e-vazio, mas so conta quando
          ha filtro. Na primeira abertura da aba as linhas tambem estao por ler,
          e ali quem deve responder e o ramo de baixo — ele mantem a lista de
          categorias alcancavel enquanto a varredura corre.
        */
        if (!rows.length && (scope.loading || (scope.rows === undefined && catalogueScopeIsFiltered(scope)))) {
            return heading + '<div class="search-loading"><span class="boot-indicator"></span><p>' +
                escapeHtml(t('loadingCatalogue')) + '</p></div>';
        }
        if (!rows.length) {
            /*
              Nada para mostrar, e o motivo muda a resposta.

              Se o filtro casou zero títulos, o certo é dizer isso e oferecer
              limpar — cair na lista de categorias era confuso: aparecia uma tela
              diferente da que a pessoa estava usando, sem explicar por quê.

              Se o catálogo inteiro está vazio, as categorias são de fato a única
              saída, porque abrir uma delas é o que traz os títulos.
            */
            if (Number(scope.total) > 0 || scope.query || scope.genre || scope.service ||
                    scope.year != null || scope.minimumRating != null) {
                return heading + emptyState('B', t('noMatches'), t('noMatchesBody'),
                    'catalogue-scope-reset', t('clearFilters'));
            }
            /*
              Zero titulos com a varredura em curso nao e um catalogo vazio: e um
              catalogo que ainda nao chegou nesta aba. As primeiras categorias
              lidas podem ser todas de outro tipo, entao Filmes fica em zero por
              um bom tempo enquanto Ao Vivo ja tem conteudo.

              Cair na lista de categorias ali era a resposta errada — o usuario
              pediu a prateleira e recebeu uma tela diferente, sem explicacao.
              Dizer que esta carregando, com a contagem andando, e a resposta
              certa e some sozinha quando os titulos entram.
            */
            return heading +
                /*
                  O aviso vem antes das categorias, nao no lugar delas.

                  Zero titulos com a varredura em curso nao e catalogo vazio: e
                  catalogo que ainda nao chegou nesta aba — as primeiras
                  categorias lidas podem ser todas de outro tipo. Sem dizer isso,
                  a lista de categorias aparecia como se fosse a resposta final e
                  o usuario a lia como a tela errada.

                  Mas as categorias continuam ali: abrir uma delas e o que traz
                  os titulos daquela agora, sem esperar a varredura chegar nela.
                  Trocar uma coisa pela outra tirava essa saida.
                */
                (bootIsSweeping() ? '<div class="search-loading"><span class="boot-indicator"></span><p>' +
                    escapeHtml(t('shelfWaitingSweep')) + '</p>' +
                    (bootDetail() ? '<p class="form-note">' + escapeHtml(bootDetail()) + '</p>' : '') +
                    '</div>' : '') +
                categoryCards(scopedCategories(contentType, categories));
        }
        return heading + mediaCards(rows, layout, true) + cataloguePager(contentType, scope);
    }

    /*
      As paginas do catalogo, e o salto de varias.

      Havia so "Carregar mais": cada toque acrescentava um bloco a mesma
      lista, entao chegar ao fim de quarenta mil titulos significava empilhar
      tudo no DOM e rolar por cima. Numa TV isso fica lento antes do meio.

      Agora sao paginas — uma de cada vez no DOM, com anterior e proxima. E o
      salto de dez, que o aplicativo do Windows nao tem: com quarenta mil
      titulos e vinte e um por pagina sao quase dois mil toques de "proxima"
      para atravessar a lista, e ninguem faz isso.

      Dez, e nao cinco nem cinquenta: cinco poupa pouco, cinquenta atravessa
      demais para quem esta procurando alguma coisa. E os saltos so aparecem
      quando ha para onde saltar, para nao oferecerem uma viagem que acaba na
      mesma pagina.
    */
    function cataloguePager(contentType, scope) {
        var page = Number(scope.page) || 0;
        var total = Number(scope.total) || 0;
        var pages = Math.max(1, Math.ceil(total / CATALOGUE_BLOCK_SIZE));
        var buttons = '';
        if (pages <= 1) { return ''; }
        if (page >= CATALOGUE_PAGE_JUMP) {
            buttons += pagerButton('catalogue-page-first', '«', scope.loading);
            buttons += pagerButton('catalogue-page-back-jump',
                '‹‹ ' + CATALOGUE_PAGE_JUMP, scope.loading);
        }
        buttons += pagerButton('catalogue-page-previous', '‹ ' + t('previousPage'),
            scope.loading || page === 0);
        buttons += '<span class="catalogue-page-mark">' +
            escapeHtml(t('pageOf').replace('{page}', String(page + 1)).replace('{pages}', String(pages))) +
            '</span>';
        buttons += pagerButton('catalogue-page-next', t('nextPage') + ' ›',
            scope.loading || page >= pages - 1);
        if (page + CATALOGUE_PAGE_JUMP < pages) {
            buttons += pagerButton('catalogue-page-forward-jump',
                CATALOGUE_PAGE_JUMP + ' ››', scope.loading);
            buttons += pagerButton('catalogue-page-last', '»', scope.loading);
        }
        return '<div class="catalogue-pagination">' + buttons + '</div>';
    }

    /* Um botao desabilitado continua desenhado: some-lo faria a fileira
       mudar de tamanho a cada pagina, e o foco pularia de lugar. */
    function pagerButton(action, label, disabled) {
        return '<button class="button ghost focusable catalogue-page-button" data-action="' + action +
            '"' + (disabled ? ' disabled' : '') + '>' + escapeHtml(label) + '</button>';
    }

    /*
      Os anos que o catálogo desta aba realmente tem, do mais novo ao mais
      antigo.

      Lidos dos itens em memória em vez de uma faixa fixa: um provedor pode não
      ter nada antes de 2015, e oferecer 1994 seria oferecer uma tela vazia.
    */
    function catalogueYears(contentType) {
        var wanted = contentType === 'LIVE' ? null : contentType;
        var found = {};
        state.items.forEach(function (item) {
            var year = Number(item.year);
            if (wanted && item.contentType !== wanted) { return; }
            if (year >= 1900 && year <= 2100) { found[year] = true; }
        });
        return Object.keys(found).map(Number).sort(function (left, right) { return right - left; });
    }

    function currentCatalogueType() {
        return state.section === 'LIVE' ? 'LIVE' :
            (state.section === 'MOVIES' ? 'MOVIE' : 'SERIES');
    }

    /* Os dois atalhos de ano da barra: tudo, ou o ano corrente. */
    function cycleCatalogueYear(mode) {
        var scope = catalogueScope(currentCatalogueType());
        scope.year = mode === 'current' ? new Date().getFullYear() : null;
        scope.openPicker = null;
        scope.rows = undefined;
        render();
    }

    function resetCatalogueScope() {
        var contentType = state.section === 'LIVE' ? 'LIVE' :
            (state.section === 'MOVIES' ? 'MOVIE' : 'SERIES');
        var scope = catalogueScope(contentType);
        scope.query = '';
        scope.genre = null;
        scope.service = null;
        scope.year = null;
        scope.minimumRating = null;
        scope.rows = undefined;
        /* O chip "Limpar filtros" some depois de limpar, então aqui o foco não
           tem para onde voltar: cai no primeiro chip, que é o de gênero. */
        focusIndex = 0;
        render();
    }

    function invalidateCatalogueShelves() {
        catalogueShelfRequestId += 1;
        Object.keys(catalogueScopes).forEach(function (contentType) {
            catalogueScopes[contentType].rows = undefined;
            catalogueScopes[contentType].total = null;
            catalogueScopes[contentType].hasMore = false;
            catalogueScopes[contentType].loading = false;
        });
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
        /* A nota editorial da fonte e a nota do público TMDb são números
           diferentes. Guardá-las separadas impede atribuir ao TMDb uma nota
           que veio da playlist/provedor. */
        if (metadata.rating != null && metadata.voteCount != null) {
            merged.tmdbRating = Number(metadata.rating);
            merged.tmdbVoteCount = Number(metadata.voteCount);
        }
        if (metadata.critics != null) { merged.critics = metadata.critics; }
        if (Array.isArray(metadata.similarTitles)) { merged.similarTitles = metadata.similarTitles.slice(0, 16); }
        if (metadata.similarTitlesLoaded != null) {
            merged.similarTitlesLoaded = Boolean(metadata.similarTitlesLoaded);
        }
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
        if (tmdbSimilarRequest && tmdbSimilarRequest.abort) { tmdbSimilarRequest.abort(); }
        tmdbSimilarRequest = null;
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
                if (!artworkFor(item)) { rememberArtwork(item.id, cached.posterUrl); }
                if (!detailBackdropMemory[item.id]) { rememberDetailBackdrop(item.id, cached.backdropUrl); }
                render();
            }
            enrichTitleFromSimilar(item, cached, isSeries);
            return;
        }
        if (tmdbTitleRequest && tmdbTitleRequest.abort) { tmdbTitleRequest.abort(); }
        tmdbTitleRequest = BuroTmdb.loadTitle(key, item, isSeries, state.preferences.language, function (metadata) {
            tmdbTitleRequest = null;
            rememberTmdbDetails(item.id, metadata);
            if (!currentTitleMatches(item.id)) { return; }
            state.screenData.details = mergeTmdbDetails(state.screenData.details, metadata);
            if (!artworkFor(item)) { rememberArtwork(item.id, metadata.posterUrl); }
            if (!detailBackdropMemory[item.id]) { rememberDetailBackdrop(item.id, metadata.backdropUrl); }
            render();
            enrichTitleFromCritics(item, metadata);
            enrichTitleFromSimilar(item, metadata, isSeries);
        }, function () { tmdbTitleRequest = null; });
    }

    function enrichTitleFromSimilar(item, metadata, isSeries) {
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (!key || !item || !metadata || !metadata.tmdbId || metadata.similarTitlesLoaded) { return; }
        if (tmdbSimilarRequest && tmdbSimilarRequest.abort) { tmdbSimilarRequest.abort(); }
        tmdbSimilarRequest = BuroTmdb.loadSimilarTitles(key, metadata.tmdbId, isSeries,
            state.preferences.language, function (titles) {
                tmdbSimilarRequest = null;
                metadata.similarTitles = (titles || []).filter(function (title) {
                    return title && title.title && title.tmdbId &&
                        BuroDomain.foldAccents(title.title) !== BuroDomain.foldAccents(item.name);
                }).slice(0, 16);
                metadata.similarTitlesLoaded = true;
                if (!currentTitleMatches(item.id)) { return; }
                state.screenData.details = mergeTmdbDetails(state.screenData.details, metadata);
                render();
            }, function () {
                tmdbSimilarRequest = null;
                metadata.similarTitles = [];
                metadata.similarTitlesLoaded = true;
            });
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

    /* Abaixo disto a nota é de três pessoas, e uma média de três votos não é
       uma nota. Mesmo limite do aplicativo do Windows. */
    var MINIMUM_TMDB_VOTES = 20;

    /*
      A nota do público do TMDb, com a contagem de votos.

      Separada da faixa da crítica logo abaixo porque as duas coisas são
      diferentes: esta é o público do TMDb votando, aquela é Rotten Tomatoes,
      IMDb e Metacritic. As duas discordam de rotina — o mesmo filme sai 80%
      numa e 68% na outra — e juntá-las numa fileira só atribuiria um número a
      quem não o calculou.

      A marca é escrita, não é um logotipo buscado do CDN: no Windows esse slot
      apontava para um caminho de logo de *provedor*, e o arquivo por trás dele
      era a marca da Netflix — o painel desenhava o logotipo da Netflix ao lado
      das palavras "Nota TMDb". Letras na cor da marca dizem de quem é o número
      e não viram silenciosamente o de outra empresa.

      Ausente quando ninguém votou: o TMDb responde 0.0 com zero votos para
      títulos que guarda mas ninguém avaliou, e "0%" se lê como veredito e não
      como a falta de um.
    */
    function ratingsSection(details) {
        var average = details && Number(details.tmdbRating);
        var votes = details && Number(details.tmdbVoteCount);
        if (!(average > 0) || !(votes >= MINIMUM_TMDB_VOTES)) { return ''; }
        return '<section class="detail-ratings" aria-label="' + attr(t('ratingsSection')) + '">' +
            '<h3>' + escapeHtml(t('ratingsSection')) + '</h3>' +
            '<div class="rating-row" role="group" aria-label="' +
            attr(t('tmdbScore') + ': ' + Math.round(average * 10) + '%') + '">' +
            '<span class="rating-mark" aria-hidden="true">TMDb</span>' +
            /* Em porcentagem, que é como se lê uma nota de relance: o TMDb
               publica sobre dez, e "76%" entra mais rápido que "7,6". */
            '<strong class="rating-value">' + Math.round(average * 10) + '%</strong>' +
            '<span class="rating-copy"><span>' + escapeHtml(t('tmdbScore')) + '</span><small>' +
            escapeHtml(t('ratingVotes').replace('{votes}', formatVoteCount(votes))) +
            '</small></span></div></section>';
    }

    /* Milhares abreviados: "12 mil" cabe onde "12.438" quebraria a linha, e a
       precisão exata de uma contagem de votos não muda nada para quem lê. */
    function formatVoteCount(votes) {
        if (votes >= 1000) { return Math.round(votes / 1000) + ' ' + t('thousandSuffix'); }
        return String(votes);
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

    /*
      Manda este titulo para o celular ou o computador.

      O que viaja e a identidade do titulo, nunca o video nem a credencial da
      fonte — a outra ponta abre da lista dela. E a mesma decisao do
      aplicativo do Windows, que tambem nao transmite fluxo.

      Reaproveita `BuroShare.build`, entao o que sai daqui e exatamente o que o
      QR de compartilhar ja mostrava: titulo, ano, arte publica e sinopse, com
      `publicArtwork` recusando qualquer coisa que nao seja host publico.
    */
    function sendTitleToScreen(itemId) {
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
            artworkUrl: metadata.posterUrl || artworkFor(item), description: details.plot
        });
        if (!payload) { return; }
        startSendToScreen(payload);
    }

    /*
      Pede um codigo e espera o outro aparelho reivindica-lo.

      Sentido inverso do pareamento de chaves: la a TV espera receber, aqui ela
      publica e o celular busca. O mesmo `open_title` do servidor atende os
      dois, e o codigo continua valendo cinco minutos.
    */
    function startSendToScreen(payload) {
        cancelPairing();
        pushScreen('SEND_TO_SCREEN', { title: payload.title, year: payload.year, sending: true });
        pairingRequest = BuroPairing.publish('open_title', payload.webUrl, {
            code: function (code, seconds) {
                if (state.screen !== 'SEND_TO_SCREEN') { return; }
                state.screenData = {
                    title: payload.title, year: payload.year, code: code, seconds: seconds
                };
                render();
            },
            failure: function (error) {
                if (state.screen !== 'SEND_TO_SCREEN') { return; }
                state.screenData = {
                    title: payload.title, year: payload.year,
                    error: (error && error.code) || 'PAIRING_FAILED'
                };
                render();
            }
        });
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
        /*
          getRequestedAppControl() conserva o último pedido mesmo depois de o
          consumidor tratá-lo. `focus` e `visibilitychange` podem, portanto,
          reler a mesma URI minutos depois. O limite antigo de cinco segundos
          transformava "Fechar aviso" numa pausa: o painel voltava sozinho no
          próximo foco. Enquanto o título continua pendente, a mesma URI é o
          mesmo pedido, independentemente do tempo transcorrido. Uma URI nova
          ainda substitui a anterior; depois de encontrar o título o pending é
          limpo e o mesmo link pode ser recebido de novo legitimamente.
        */
        if (raw === lastRequestedAppControlUri && pendingSharedTitle) { return true; }
        lastRequestedAppControlUri = raw;
        pendingSharedTitle = value;
        sharedTitleNoticeVisible = false;
        sharedTitleNeedsResolution = true;
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
        if (!wanted || !sharedTitleNeedsResolution || sharedTitleResolving || !state.ready || state.screen !== 'SHELL' ||
                !state.preferences || !state.preferences.acceptedLegal || !state.activeProfile || !state.activeSource) { return; }
        snapshot = catalogueVisibilitySnapshot();
        requestId = sharedTitleResolveId;
        sharedTitleResolving = true;
        /* Um miss fica parado até a ação explícita de retry. Isso impede que
           qualquer render reabra um aviso que o usuário acabou de fechar. */
        sharedTitleNeedsResolution = false;
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
                refreshFocus('shared-retry', null);
                return;
            }
            pendingSharedTitle = null;
            sharedTitleNoticeVisible = false;
            sharedTitleNeedsResolution = false;
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
            if (state.screen === 'SHELL') {
                render();
                refreshFocus('shared-retry', null);
            }
            showToast(t('sharedResolveError'), true);
        });
    }

    function retryPendingSharedTitle() {
        if (!pendingSharedTitle) { return; }
        sharedTitleNoticeVisible = false;
        sharedTitleNeedsResolution = true;
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
            /* Uma recomendacao local pertence ao mesmo acervo do titulo aberto.
               Sem este limite, uma segunda fonte podia vazar um card que seria
               recusado somente depois do ENTER, parecendo um botao morto. */
            if (candidate.sourceId !== item.sourceId) { return; }
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

    function similarTitleKey(title) {
        if (title && title.localId) { return 'local:' + title.localId; }
        return (title && title.isSeries ? 'series:' : 'movie:') + String(title && title.tmdbId || '');
    }

    function displayedSimilarTitles(item, details) {
        if (Array.isArray(details && details.similarTitles) && details.similarTitles.length) {
            return details.similarTitles.slice(0, 16);
        }
        return similarTitles(item, details, 12).map(function (local) {
            return {
                localId: local.id,
                isSeries: local.contentType === 'SERIES',
                title: local.name,
                year: local.year || null,
                posterUrl: artworkFor(local)
            };
        });
    }

    function externalSimilarCards(titles) {
        var opening = state.screenData && state.screenData.similarOpeningKey;
        return '<div class="similar-title-row">' + titles.map(function (title) {
            var poster = safeArtworkUrl(title.posterUrl);
            var key = similarTitleKey(title);
            return '<button class="similar-title-card focusable" data-action="similar-title" data-key="' +
                attr(key) + '" aria-busy="' + (opening === key ? 'true' : 'false') + '">' +
                (poster ? '<span class="similar-title-art"><img src="' + attr(poster) + '" alt=""></span>' :
                    '<span class="similar-title-art fallback">' + escapeHtml(title.title.charAt(0)) + '</span>') +
                (opening === key ? '<i class="similar-title-opening" aria-hidden="true"></i>' : '') +
                '<strong>' + escapeHtml(title.title) + '</strong>' +
                (title.year ? '<small>' + escapeHtml(title.year) + '</small>' : '') + '</button>';
        }).join('') + '</div>';
    }

    /*
      A saida da ficha, no alto e a esquerda.

      O RETURN do controle ja faz isto e continua fazendo. O botao existe porque
      o aplicativo do Windows tem um — uma barra propria acima da ficha, com o
      rotulo escrito — e porque quem usa mouse ou teclado numa TV nao tem RETURN
      a mao.

      No alto e nao no fim: uma saida no rodape so e encontrada por quem ja rolou
      a pagina inteira, e quem quer sair normalmente quer sair antes disso.
    */
    function detailBackBar() {
        return '<div class="detail-back-row"><button class="button ghost focusable" data-action="back">← ' +
            escapeHtml(t('backToCatalogue')) + '</button></div>';
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
        /*
          A mesma linha do aplicativo do Windows, na mesma ordem: lancamento,
          duracao, genero, pais, nota.

          A data leva o rotulo "Lancamento" porque sozinha ela e ambigua numa
          ficha que tambem mostra temporadas e episodios — e a decisao do
          `XtreamWorkspace`, onde a lista comeca com `"Lancamento $it"`.

          O pais estava so na secao de baixo, junto do diretor. Ali ele exige
          rolar; na linha de fatos e lido junto com o resto, que e onde alguem
          decide se quer o filme.
        */
        if (details.releaseDate) {
            facts.push(t('releaseDate').replace(/:$/, '') + ' ' + details.releaseDate);
        } else if (item.year) { facts.push(String(item.year)); }
        if (detailDuration(details.duration)) { facts.push(detailDuration(details.duration)); }
        if (details.genre || item.genre) { facts.push(details.genre || item.genre); }
        if (details.country) { facts.push(details.country); }
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
        /* So o diretor: o pais subiu para a linha de fatos, onde e lido junto com
           o resto em vez de exigir rolar ate aqui. Repeti-lo nos dois lugares
           seria dizer a mesma coisa duas vezes na mesma tela. */
        if (details.director) {
            supporting += '<section class="detail-credit-card"><h3>' + t('credits') + '</h3>' +
                '<p><strong>' + t('director') + '</strong> ' + escapeHtml(details.director) + '</p></section>';
        }
        if (castMembers.length || cast.length) {
            supporting += '<section class="detail-cast"><h3>' + t('castTitle') + '</h3><div class="cast-row">' +
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
        related = displayedSimilarTitles(item, details);
        if (related.length) {
            supporting += '<section class="detail-related"><h3>' + t('similarTitles') + '</h3>' +
                externalSimilarCards(related) + '</section>';
        }
        return '<div class="detail-page">' + detailBackBar() + '<div class="detail-hero">' + detailArtworkHtml(item) +
            /*
              O pôster ao lado do texto, como no aplicativo do Windows.

              O fundo já é a arte do título, mas em paisagem e coberto por
              degradês: o pôster vertical é a capa que a pessoa reconhece de
              relance, e é o que o Windows põe à esquerda da ficha. Sai da tela
              quando não há capa, em vez de deixar um retângulo cinza.
            */
            detailPosterHtml(item) +
            '<div class="detail-hero-copy">' +
            '<span class="hero-kicker">' + (isSeries ? t('series') : t('movies')) +
            '</span><h2>' + escapeHtml(details.title || item.name) + '</h2>' +
            (facts.length ? '<div class="detail-facts">' + facts.map(function (fact) {
                return detailFact(fact, /^★/.test(fact) ? 'rating' : '');
            }).join('') + '</div>' : '') +
            '<p>' + escapeHtml(details.plot || t('noSynopsis')) + '</p>' + detailProgress(item) +
            /*
              As notas depois das acoes, e nao antes.

              Ficavam entre a linha de fatos e a sinopse, empurrando Assistir e a
              barra de glifos para baixo — quem abre a ficha para assistir tinha
              de passar por TMDb, OMDb e criticos antes de chegar ao botao.

              A ordem e a do Windows: sinopse, e o bloco de notas depois. La as
              acoes vivem noutro bloco acima; aqui elas estao na mesma coluna,
              entao "depois das acoes" e o equivalente — decidir vem primeiro,
              conferir a nota vem depois.
            */
            detailActionsHtml(item, isSeries, episodeRows, trailerId) +
            ratingsSection(details) + criticsStrip(details) + '</div></div>' +
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

    function renderLiveDetails(item, schedule, epgLoading) {
        var nowSeconds = Math.floor(Date.now() / 1000);
        var current = null;
        var rows = schedule.map(function (program, index) {
            var isNow = epgIsNow(program, nowSeconds);
            var isPast = Number(program.endEpochSeconds) > 0 && Number(program.endEpochSeconds) <= nowSeconds;
            var catchUp = BuroXtream.catchUpLocator(item.locator, program, nowSeconds);
            var tag = catchUp ? 'button' : 'div';
            if (isNow) { current = program; }
            return '<' + tag + ' class="epg-row ' + (isNow ? 'current' : '') + (isPast ? ' past' : '') +
                (catchUp ? ' catch-up focusable' : '') + '"' +
                (catchUp ? ' data-action="catch-up" data-id="' + attr(item.id) + '" data-program-index="' + index + '"' : '') +
                (isNow ? ' aria-current="true"' : '') + '><time>' + escapeHtml(epgClock(program, false)) +
                '<small>' + escapeHtml(epgClock(program, true)) + '</small></time><div><strong>' + escapeHtml(program.title) +
                '</strong><p>' + escapeHtml(program.description || '') + '</p>' +
                (isNow ? '<div class="epg-progress"><i style="width:' + epgProgress(program, nowSeconds).toFixed(2) + '%"></i></div>' : '') +
                '</div><span>' + (isNow ? t('now') : (catchUp ? t('catchUpPlay') : '')) + '</span></' + tag + '>';
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
            '</p></div><div class="epg-list" aria-live="polite">' +
            (rows || (epgLoading ? '<div class="search-loading"><span class="boot-indicator"></span><p>' +
                t('loading') + '</p></div>' : '<p class="form-message">' + t('epgUnavailable') + '</p>')) + '</div>';
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
            /*
              `type="search"` nao e cosmetico aqui: e ele que faz o teclado
              virtual da Samsung mostrar o proprio botao de microfone. Em
              modelos onde a Web Speech API nao existe, esse teclado e o
              unico caminho para ditar — e ele ja estava a uma letra de
              distancia.
            */
            '</label><input id="search-query" class="focusable" type="search" maxlength="80" value="' + attr(query) + '"></div>' +
            voiceSearchButton() +
            '<button class="button primary focusable" data-action="search-run">' + t('search') + '</button></div></div>' +
            body,
            t('search'), true);
    }

    /*
      O botao de ditar, quando o aparelho sabe escutar.

      Some quando a Web Speech API nao existe — e nesse caso o microfone do
      teclado virtual continua sendo o caminho, porque o campo e
      `type="search"`. Um botao que nao funciona seria pior do que nenhum:
      numa TV a pessoa aperta, nao acontece nada, e ela conclui que o
      aplicativo esta quebrado.
    */
    function voiceSearchButton() {
        if (!BuroVoice.available()) { return ''; }
        return '<button class="button ghost focusable voice-search' +
            (BuroVoice.isListening() ? ' listening' : '') +
            '" data-action="search-voice" aria-label="' + attr(t('searchByVoice')) + '">' +
            '<span class="voice-mark" aria-hidden="true"></span>' +
            escapeHtml(BuroVoice.isListening() ? t('searchListening') : t('searchByVoice')) +
            '</button>';
    }

    /*
      Escuta uma frase e busca com ela.

      Busca sozinho depois de ouvir, e nao espera um segundo ENTER: quem
      falou o nome do filme ja disse o que queria, e pedir confirmacao com o
      controle na mao desfaria a economia de nao ter digitado.

      O texto vai para o campo antes da busca, para a pessoa ver o que foi
      entendido — se saiu errado, ela corrige dali em vez de recomecar.
    */
    function startVoiceSearch() {
        var input;
        if (BuroVoice.isListening()) { BuroVoice.stop(); render(); return; }
        if (!BuroVoice.available()) { showToast(t('searchVoiceUnavailable'), true); return; }
        BuroVoice.listen(state.preferences.language || 'pt-BR', function (text) {
            var heard = BuroDomain.trim(text);
            /* A tela pode ter mudado enquanto o aparelho escutava. */
            if (state.screen !== 'SHELL' || state.section !== 'SEARCH') { return; }
            render();
            if (!heard) { showToast(t('searchVoiceEmpty'), false); return; }
            input = document.getElementById('search-query');
            if (input) { input.value = heard; }
            runSearch();
        }, function (error) {
            if (state.screen !== 'SHELL' || state.section !== 'SEARCH') { return; }
            render();
            /* Desistir nao e falhar: quem soltou o botao ou ficou em silencio
               nao precisa de um aviso vermelho. */
            if (error.code === 'VOICE_CANCELLED') { return; }
            showToast(t(error.code === 'VOICE_UNAVAILABLE' ?
                'searchVoiceUnavailable' : 'searchVoiceFailed'), true);
        });
        render();
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
    /*
      Com quanta antecedencia o aviso aparece.

      A TV nao pode notificar com o aplicativo fechado — o manifesto declara
      `background-support=disable` e a propria tela ja diz isso abaixo. O que
      esta escolha governa e o aviso da abertura: quanto tempo antes da
      estreia um titulo marcado passa a ser anunciado.

      Fica acima da lista porque muda o que a lista significa: com um dia de
      horizonte, quase tudo aparece como "guardado"; com noventa, quase tudo
      vira contagem regressiva.
    */
    function reminderHorizonPicker() {
        var current = reminderHorizonDays();
        return '<div class="reminder-horizon"><strong>' + escapeHtml(t('reminderHorizon')) +
            '</strong><div class="action-row">' +
            BuroDomain.REMINDER_HORIZON_CHOICES.map(function (days) {
                return '<button class="filter-chip focusable ' + (days === current ? 'selected' : '') +
                    '" data-action="reminder-horizon" data-days="' + days + '">' +
                    escapeHtml(t(days === 1 ? 'reminderHorizonDay' : 'reminderHorizonDays')
                        .replace('{days}', String(days))) + '</button>';
            }).join('') + '</div></div>';
    }

    function renderReminders() {
        var entries = reminderCards();
        var content;
        if (!entries.length) {
            content = emptyState('!', t('remindersEmpty'), t('remindersEmptyBody'), '', '');
        } else {
            content = '<p class="profile-help">' + escapeHtml(t('remindersSubtitle')) + '</p>' +
                reminderHorizonPicker() +
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
    /* O horizonte escolhido pela pessoa, ou o padrao do dominio. */
    function reminderHorizonDays() {
        var stored = Number(state.preferences.reminderHorizonDays);
        return BuroDomain.REMINDER_HORIZON_CHOICES.indexOf(stored) >= 0 ?
            stored : BuroDomain.COUNTDOWN_HORIZON_DAYS;
    }

    function setReminderHorizon(days) {
        state.preferences.reminderHorizonDays = Number(days);
        savePreferences();
        /* O aviso da abertura ja foi mostrado com o horizonte antigo; deixar
           que ele volte a aparecer seria repetir o mesmo aviso na mesma
           sessao. A escolha vale a partir da proxima. */
        render();
        showToast(t('reminderHorizonSaved'), false);
    }

    function reminderNoticeText() {
        var digest = BuroDomain.reminderDigest(profileReminders(), null, reminderHorizonDays());
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
    /*
      A barra superior, com o que o aplicativo do Windows mostra ali.

      Antes tinha só o sino e um chip escrito "Samsung Tizen" — o nome da
      plataforma, que quem está usando a TV já sabe. O Windows usa esse espaço
      para as quatro coisas que mudam sozinhas: quanto falta da licença, que
      horas são, quem está usando e se há aviso.
    */
    /*
      Mantém o relógio da barra certo sem redesenhar a tela.

      Trocar só o texto do relógio, de minuto em minuto: um `render()` a cada
      minuto reconstruiria a tela inteira e tiraria o foco do lugar enquanto a
      pessoa navega, o que é caro para o único elemento que precisa mudar.
    */
    function startClock() {
        if (clockTimer) { window.clearInterval(clockTimer); }
        clockTimer = window.setInterval(function () {
            var host = root && root.querySelector ? root.querySelector('.topbar-clock') : null;
            var fresh;
            if (!host) { return; }
            fresh = document.createElement('div');
            fresh.innerHTML = clockHtml();
            if (fresh.firstChild) { host.innerHTML = fresh.firstChild.innerHTML; }
        }, 30000);
    }

    /*
      Quantos títulos o catálogo tem, de verdade.

      A barra contava `state.items`, que é a amostra do boot mais o que as telas
      carregaram sob demanda: numa lista de quarenta e dois mil títulos ela dizia
      quatrocentos e poucos. O número certo está no banco, e `count()` o obtém
      sem materializar nada.

      Medido de tempos em tempos e guardado, porque a barra é redesenhada a cada
      render e contar a cada vez seria caro sem necessidade — o total muda quando
      a varredura de fundo grava, não entre dois desenhos da mesma tela.

      Enquanto o total é zero a medição se repete de perto: no começo do app o
      banco ainda está sendo preenchido, e uma primeira contagem que pegou zero
      não pode congelar a barra por quinze segundos.
    */
    var catalogueSize = 0;
    var catalogueSizeMeasuredAt = 0;
    var CATALOGUE_SIZE_MAX_AGE_MILLIS = 15000;
    var CATALOGUE_SIZE_EMPTY_RETRY_MILLIS = 1500;

    function measureCatalogueSize() {
        var age = catalogueSize ? CATALOGUE_SIZE_MAX_AGE_MILLIS : CATALOGUE_SIZE_EMPTY_RETRY_MILLIS;
        if (catalogueSizeMeasuredAt && Date.now() - catalogueSizeMeasuredAt < age) { return; }
        catalogueSizeMeasuredAt = Date.now();
        BuroStorage.count('items', function (total) {
            var changed = total !== catalogueSize;
            catalogueSize = total;
            /* Redesenha só quando o número mudou, senão a medição periódica
               viraria um render periódico. */
            if (changed && state.screen === 'SHELL') { render(); }
        }, function () {});
    }

    function topbarSubtitleHtml() {
        var parts = [];
        measureCatalogueSize();
        if (state.sources.length) {
            parts.push(t('topbarSources').replace('{count}', state.sources.length));
        }
        if (catalogueSize) {
            parts.push(t('topbarItems').replace('{count}', catalogueSize));
        }
        parts.push('IPTV BURO v' + applicationVersion());
        return '<p class="topbar-subtitle">' + escapeHtml(parts.join(' · ')) + '</p>';
    }

    /* Quantos dias faltam, quando há uma data. O Windows mostra "Faltam 26
       dias"; sem data — dispositivo não registrado — o chip diz o estado. */
    /*
      O download em andamento, na barra superior.

      Pedido do usuário: "barra de dawlaond deve aparecer na barra superior com
      tempo velocidade". `js/downloads.js` já calcula velocidade e estimativa —
      o que faltava era a TV mostrar. Sem isso a fila só era visível abrindo a
      tela de Downloads, e uma cópia para o USB leva dezenas de minutos.

      Só aparece quando algo está de facto baixando: um chip parado em "0%"
      ocuparia a barra sem dizer nada. Quando a fila tem mais de um item, o
      número deles acompanha, porque a barra mostra o progresso de um só.
    */
    function downloadChipHtml() {
        var entries;
        var active = null;
        var queued = 0;
        var detail = [];
        if (!BuroDownloads.enabled()) { return ''; }
        try { entries = BuroDownloads.list(); } catch (ignoredDownloads) { return ''; }
        (entries || []).forEach(function (entry) {
            if (entry.state === 'RUNNING' && !active) { active = entry; return; }
            if (entry.state === 'RUNNING' || entry.state === 'QUEUED') { queued += 1; }
        });
        if (!active) { return ''; }
        if (active.bytesPerSecond > 0) { detail.push(formatSpeed(active.bytesPerSecond)); }
        if (active.remainingSeconds != null) { detail.push(formatRemaining(active.remainingSeconds)); }
        if (queued > 0) { detail.push(t('downloadsWaiting').replace('{count}', queued)); }
        return '<button class="topbar-chip download-chip focusable"' +
            ' data-action="section" data-section="DOWNLOADS"' +
            ' aria-label="' + attr(t('downloadProgressLabel')
                .replace('{name}', active.name).replace('{percent}', active.percent)) + '">' +
            '<span class="download-chip-copy"><strong>' + escapeHtml(active.percent + '%') + '</strong>' +
            (detail.length ? '<small>' + escapeHtml(detail.join(' · ')) + '</small>' : '') + '</span>' +
            '<span class="download-chip-bar" aria-hidden="true"><i style="width:' +
            BuroDomain.clamp(active.percent, 0, 100) + '%"></i></span></button>';
    }

    /* Velocidade em MB/s ou kB/s. Casas decimais só abaixo de dez, onde elas
       ainda mudam a leitura. */
    function formatSpeed(bytesPerSecond) {
        var mb = bytesPerSecond / 1048576;
        if (mb >= 10) { return Math.round(mb) + ' MB/s'; }
        if (mb >= 0.1) { return mb.toFixed(1) + ' MB/s'; }
        return Math.max(1, Math.round(bytesPerSecond / 1024)) + ' kB/s';
    }

    /* O tempo que falta, arredondado. Segundos exatos numa estimativa que
       oscila dariam uma precisão que ela não tem. */
    function formatRemaining(seconds) {
        var minutes;
        if (seconds < 60) { return t('remainingSeconds').replace('{count}', Math.max(1, seconds)); }
        minutes = Math.round(seconds / 60);
        if (minutes < 60) { return t('remainingMinutes').replace('{count}', minutes); }
        return t('remainingHours').replace('{count}', Math.round(minutes / 60));
    }

    /*
      Atualizar o catalogo, na barra de cima — como no aplicativo do Windows.

      Estava so dentro de Gerenciar fonte, a tres telas de distancia, e a lista
      muda sozinha do lado do provedor: quem ve um filme faltando quer pedir a
      lista de novo dali mesmo. O botao forca a varredura, pelo mesmo motivo que
      o Windows forca (ver `refreshCatalog`): sem isso a fila pula toda categoria
      completada nas ultimas 24 horas e o botao parece nao fazer nada.

      Enquanto roda vira um indicador girando e nao reticencias: um catalogo
      grande demora, e um "..." parado e indistinguivel de um botao que falhou.
      Some quando nao ha fonte Xtream, porque ai nao ha o que atualizar.
    */
    function refreshChipHtml() {
        var source = state.activeSource;
        var status;
        if (!source) { return ''; }
        if (source.type === 'XTREAM') {
            status = catalogueSyncStatus(source);
            if (status && status.state === 'RUNNING') {
                return '<span class="topbar-chip refresh-chip busy" role="status" aria-label="' +
                    attr(t('refreshingCatalogue')) + '"><span class="boot-indicator"></span>' +
                    escapeHtml(catalogueSyncShortLabel(status)) + '</span>';
            }
            return '<button class="topbar-chip refresh-chip focusable" data-action="catalogue-refresh"' +
                ' aria-label="' + attr(t('refreshCatalogue')) + '">⟳ ' +
                escapeHtml(t('refreshCatalogue')) + '</button>';
        }
        if (topbarSourceRefresh.sourceId === source.id && topbarSourceRefresh.refreshing) {
            return '<span class="topbar-chip refresh-chip busy" role="status" aria-label="' +
                attr(t('refreshingSource')) + '"><span class="boot-indicator"></span>' +
                escapeHtml(t('refreshingSource')) + '</span>';
        }
        return '<button class="topbar-chip refresh-chip focusable" data-action="active-source-refresh"' +
            ' aria-label="' + attr(t('refreshCatalogue')) + '">⟳ ' +
            escapeHtml(t('refreshCatalogue')) + '</button>';
    }

    /*
      O teste de ligacao, ao lado de atualizar.

      Os dois respondem a mesma queixa por lados opostos: "a minha lista esta
      errada" e "a imagem fica travando". Quem nao sabe qual dos dois tem vai
      tentar ambos, e ambos estao aqui.
    */
    function diagnosticsChipHtml() {
        return '<button class="topbar-chip focusable" data-action="diagnostics"' +
            ' aria-label="' + attr(t('diagnosticsAction')) + '">◉ ' +
            escapeHtml(t('diagnosticsAction')) + '</button>';
    }

    /* Quanto falta, curto o bastante para caber na barra. */
    function catalogueSyncShortLabel(status) {
        if (!status || !status.total) { return t('refreshingCatalogue'); }
        return status.completed + '/' + status.total;
    }

    function licenceChipHtml() {
        var decision;
        var days;
        try { decision = BuroLicense.decide(); }
        catch (ignoredLicence) { return ''; }
        if (!decision) { return ''; }
        days = decision.expiresAt ? Math.ceil((Date.parse(decision.expiresAt) - Date.now()) / 86400000) : null;
        if (days != null && isFinite(days) && days >= 0) {
            return '<button class="topbar-chip focusable ' + (days <= 7 ? 'urgent' : '') +
                '" data-action="licence-activate">' +
                escapeHtml(t(days === 1 ? 'licenceDayLeft' : 'licenceDaysLeft').replace('{count}', days)) +
                '</button>';
        }
        return '<button class="topbar-chip focusable ' + (decision.allowed ? '' : 'urgent') +
            '" data-action="licence-activate">' + escapeHtml(licenceStatusText(decision)) + '</button>';
    }

    /*
      Hora e data.

      Redesenhado pelo relógio de minuto em minuto e não a cada segundo: numa TV
      ninguém confere segundos, e um `render()` por segundo tiraria o foco do
      lugar enquanto a pessoa navega.
    */
    function clockHtml() {
        var now = new Date();
        var uses24HourClock = state.preferences.uses24HourClock !== false;
        var hours = uses24HourClock ? ('0' + now.getHours()).slice(-2) : String((now.getHours() % 12) || 12);
        var minutes = ('0' + now.getMinutes()).slice(-2);
        var period = uses24HourClock ? '' : (now.getHours() < 12 ? ' AM' : ' PM');
        var days = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
        var months = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
        return '<div class="topbar-clock"><strong>' + hours + ':' + minutes + period + '</strong><small>' +
            escapeHtml(t('day' + days[now.getDay()]) + ', ' + now.getDate() + ' ' +
                t('month' + months[now.getMonth()])) + '</small></div>';
    }

    function profileChipHtml() {
        var profile = state.activeProfile;
        var name = profile ? profile.name : t('profiles');
        return '<button class="topbar-profile focusable" data-action="section" data-section="PROFILES" aria-label="' +
            attr(name) + '"><span class="topbar-avatar ' + (profile && profile.isKids ? 'kids' : '') + '">' +
            profileAvatarContent(profile || { name: name }) + '</span></button>';
    }

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
        /* O sino segue o horizonte escolhido, como o aviso da abertura. */
        digest = BuroDomain.reminderDigest(reminders, null, reminderHorizonDays());
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
                '<div class="action-row"><button class="button ghost focusable" data-action="notifications-clear">' +
                t('notificationsClearAll') + '</button></div>';
        }
        shell(content + '<p class="form-note privacy">' + escapeHtml(t('notificationsBackground')) + '</p>',
            t('notificationsTitle'), true);
    }

    function openNotifications() {
        var rows = profileNotifications();
        /* Android e Windows tratam abrir como leitura. Persistir antes de trocar
           a tela também remove o contador do shell já na primeira renderização. */
        if (BuroNotifications.unreadCount(rows)) {
            saveNotifications(BuroNotifications.markAllRead(rows));
        }
        pushScreen('NOTIFICATIONS', {});
    }

    function removeNotification(id) {
        saveNotifications(BuroNotifications.remove(profileNotifications(), id));
        render();
    }

    function clearNotifications() {
        saveNotifications(BuroNotifications.clear());
        render();
    }

    function transientLibraryItem(item, progressItemId, progressRowId) {
        var copy = {};
        Object.keys(item || {}).forEach(function (key) { copy[key] = item[key]; });
        copy._libraryProgressItemId = progressItemId;
        copy._libraryProgressRowId = progressRowId;
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
            return transientLibraryItem(item, entry.itemId, entry.id);
        }).filter(function (item) { return Boolean(item) && itemVisible(item); });
    }

    /* Mesmo normalizador usado pela busca do Historico no Windows: acentos,
       ano e decoracao de qualidade/idioma nao mudam a identidade do titulo. */
    function historySearchKey(value) {
        return BuroDomain.foldAccents(value || '')
            .replace(/\b(19|20)[0-9]{2}\b/g, ' ')
            .replace(/\b(4k|uhd|hd|sd|fhd|1080p?|720p?|480p?|2160p?|dublado|dual|legendado|leg|nacional|dub|bluray|blu ray|webrip|web dl|webdl|hdrip|dvdrip|remux|imax|extended|remastered|remasterizado)\b/g, ' ')
            .replace(/[^a-z0-9]+/g, '');
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

    function progressCard(item, history) {
        var row = activeProgressRow(item && item._libraryProgressRowId);
        var direct = '';
        var target = row ? findItemAndSource(row.itemId).item : null;
        if (!history && target && (target.contentType === 'MOVIE' || target.contentType === 'EPISODE')) {
            direct = '<button class="button primary progress-action focusable" data-action="continue-play" data-id="' +
                attr(row.id) + '">' + escapeHtml(t('resumeFrom').replace('{time}', formatPlaybackTime(row.positionMs))) +
                '</button><button class="button ghost progress-action focusable" data-action="continue-restart" data-id="' +
                attr(row.id) + '">' + escapeHtml(t('startOver')) + '</button>';
        }
        return '<div class="progress-card">' + mediaCard(item, 'poster') + direct +
            '<button class="button ghost progress-action progress-remove focusable" data-action="' +
            (history ? 'history-remove' : 'continue-remove') + '" data-id="' +
            attr(item._libraryProgressRowId) + '">' + escapeHtml(t('downloadRemove')) + '</button></div>';
    }

    function progressLibraryContent(section, items, history, title, emptyMessage) {
        var filterBar = libraryFilterBar(section, items);
        var filtered = filterLibraryItems(items, libraryFilters[section]);
        var needle = history ? historySearchKey(historyQuery) : '';
        var pageCount;
        var page;
        var start;
        var visible;
        var toolbar = '';
        if (history) {
            toolbar = '<div class="history-toolbar"><label for="history-query">' + escapeHtml(t('search')) +
                '</label><input id="history-query" class="focusable" type="text" maxlength="80" value="' +
                attr(historyQuery) + '" placeholder="' + attr(t('search')) + '">' +
                (items.length ? '<button class="button ghost focusable" data-action="history-clear">' +
                    escapeHtml(t('historyClearAll')) + '</button>' : '') + '</div>';
        } else if (items.length) {
            /*
              Limpar a lista inteira, e nao um cartao por vez.

              Cada cartao ja tem "Remover da lista", mas uma lista de vinte
              titulos exige vinte idas ao mesmo botao. O Historico ao lado ja
              oferece o "apagar tudo"; nao havia motivo para Continuar assistindo
              nao oferecer.

              Passa pela mesma tela de confirmacao, e nao apaga direto: e uma
              acao que nao tem volta, e um ENTER errado no controle remoto e
              facil demais.
            */
            toolbar = '<div class="history-toolbar continue-toolbar">' +
                '<button class="button ghost focusable" data-action="continue-clear">' +
                escapeHtml(t('continueClearAll')) + '</button></div>';
        }
        if (!items.length) {
            libraryPages[section] = 0;
            return toolbar + emptyState('>', title, emptyMessage, '', '');
        }
        if (needle) {
            filtered = filtered.filter(function (item) {
                return historySearchKey(item.name).indexOf(needle) >= 0;
            });
        }
        if (!filtered.length) {
            libraryPages[section] = 0;
            return toolbar + filterBar + '<p class="history-no-match">' + escapeHtml(t('search')) + ': &quot;' +
                escapeHtml(BuroDomain.trim(historyQuery)) + '&quot; &mdash; 0</p>';
        }
        pageCount = Math.max(1, Math.ceil(filtered.length / LIBRARY_PAGE_SIZE));
        page = BuroDomain.clamp(Number(libraryPages[section]) || 0, 0, pageCount - 1);
        libraryPages[section] = page;
        start = page * LIBRARY_PAGE_SIZE;
        visible = filtered.slice(start, start + LIBRARY_PAGE_SIZE);
        return toolbar + filterBar + '<div class="card-row progress-card-row' +
            (history ? '' : ' continue-progress-row') + '">' + visible.map(function (item) {
            return progressCard(item, history);
        }).join('') + '</div>' + paginationControls(
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
        shell(progressLibraryContent(section, items, history, title,
            t(history ? 'historyEmpty' : 'continueEmpty')), title, true);
    }

    function activeProgressRow(rowId) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var found = null;
        state.progress.some(function (row) {
            if (row.id === rowId && row.profileId === profileId) { found = row; return true; }
            return false;
        });
        return found;
    }

    /* Remover de Continuar nao apaga o que foi assistido: como no Windows,
       conclui a linha, que sai desta lista e permanece consultavel no Historico. */
    function forgetContinueProgress(rowId) {
        var row = activeProgressRow(rowId);
        var completed;
        if (!row || progressMutationPending) { return; }
        completed = {};
        Object.keys(row).forEach(function (key) { completed[key] = row[key]; });
        completed.completed = true;
        completed.positionMs = Number(completed.durationMs) > 0 ? Number(completed.durationMs) :
            Math.max(0, Number(completed.positionMs) || 0);
        completed.updatedAt = Date.now();
        progressMutationPending = true;
        BuroStorage.put('progress', completed, function () {
            state.progress = state.progress.map(function (entry) {
                return entry.id === completed.id ? completed : entry;
            });
            progressMutationPending = false;
            render();
            showToast(t('continueRemoved'), false);
        }, function () {
            progressMutationPending = false;
            showToast(t('historyChangeFailed'), true);
        });
    }

    function forgetHistoryProgress(rowId) {
        var row = activeProgressRow(rowId);
        if (!row || progressMutationPending) { return; }
        progressMutationPending = true;
        BuroStorage.remove('progress', row.id, function () {
            state.progress = state.progress.filter(function (entry) { return entry.id !== row.id; });
            progressMutationPending = false;
            render();
            showToast(t('historyRemoved'), false);
        }, function () {
            progressMutationPending = false;
            showToast(t('historyChangeFailed'), true);
        });
    }

    function confirmClearHistory() {
        var profileId = state.activeProfile && state.activeProfile.id;
        if (!profileId || progressMutationPending || state.screen !== 'HISTORY_CLEAR_CONFIRM') { return; }
        progressMutationPending = true;
        state.screenData = state.screenData || {};
        state.screenData.busy = true;
        render();
        BuroStorage.removeByIndex('progress', 'byProfile', profileId, function () {
            state.progress = state.progress.filter(function (entry) { return entry.profileId !== profileId; });
            progressMutationPending = false;
            historyQuery = '';
            libraryPages.HISTORY = 0;
            goBack();
            showToast(t('historyCleared'), false);
        }, function () {
            progressMutationPending = false;
            state.screenData.busy = false;
            render();
            showToast(t('historyChangeFailed'), true);
        });
    }

    /*
      Marca como concluida toda linha que a lista de retomada mostraria.

      As gravacoes vao para o banco numa so passada e o estado local e refeito a
      partir do mesmo criterio, para as duas metades nao poderem divergir se uma
      escrita falhar no meio.
    */
    function confirmClearContinue() {
        var profileId = state.activeProfile && state.activeProfile.id;
        var now = Date.now();
        var pending;
        if (!profileId || progressMutationPending || state.screen !== 'CONTINUE_CLEAR_CONFIRM') { return; }
        pending = state.progress.filter(function (entry) {
            return entry.profileId === profileId && !entry.completed;
        }).map(function (entry) {
            var completed = {};
            Object.keys(entry).forEach(function (key) { completed[key] = entry[key]; });
            completed.completed = true;
            completed.positionMs = Number(completed.durationMs) > 0 ? Number(completed.durationMs) :
                Math.max(0, Number(completed.positionMs) || 0);
            completed.updatedAt = now;
            return completed;
        });
        if (!pending.length) { goBack(); return; }
        progressMutationPending = true;
        state.screenData = state.screenData || {};
        state.screenData.busy = true;
        render();
        BuroStorage.putBatch('progress', pending, function () {
            var byId = {};
            pending.forEach(function (entry) { byId[entry.id] = entry; });
            state.progress = state.progress.map(function (entry) {
                return byId[entry.id] || entry;
            });
            progressMutationPending = false;
            libraryPages.CONTINUE = 0;
            goBack();
            showToast(t('continueCleared'), false);
        }, function () {
            progressMutationPending = false;
            state.screenData.busy = false;
            render();
            showToast(t('historyChangeFailed'), true);
        });
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
        /* So com mais de uma lista: com uma nao ha nada para juntar, e o interruptor
           seria uma pergunta sobre nada. */
        var merge = state.sources.length > 1
            ? '<button class="merge-toggle focusable" data-action="toggle-merge-sources">' +
              '<span class="merge-mark">' + (mergeEverySource() ? '◉' : '○') + '</span>' +
              '<span class="merge-text"><strong>' + escapeHtml(t('mergeSourcesTitle')) + '</strong>' +
              '<span>' + escapeHtml(t('mergeSourcesHelp')) + '</span></span></button>'
            : '';
        shell(merge + '<div class="card-row">' + cards + '</div>', t('sources'), true);
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
            settingCard('subtitleBackground', 'subtitleBackground') +
            /*
              A previa do canal focado, desligada por padrao.

              Cada abertura e uma sessao no provedor, e ha provedores que
              limitam conexoes simultaneas e derrubam a conta por excesso.
              Quem sabe que a sua aguenta liga aqui; quem nao sabe nao e
              exposto ao risco sem ter escolhido.
            */
            settingCard('livePreview', 'livePreview') + '</div></section>';
    }

    function clockChoice(value, labelKey, selected) {
        return '<button class="subtitle-choice focusable ' + (selected ? 'selected' : '') +
            '" data-action="clock-format" data-value="' + attr(value) + '" aria-pressed="' +
            (selected ? 'true' : 'false') + '"><strong>' + escapeHtml(t(labelKey)) + '</strong></button>';
    }

    function clockSettingsPanel() {
        var uses24HourClock = state.preferences.uses24HourClock !== false;
        return '<div class="section-heading"><h2>' + t('clockLabel') + '</h2></div>' +
            '<section class="subtitle-settings-card clock-settings-card"><p>' + t('clockHint') +
            '</p><div class="subtitle-choice-row">' +
            clockChoice('24', 'clock24h', uses24HourClock) +
            clockChoice('12', 'clock12h', !uses24HourClock) + '</div></section>';
    }

    function regionSettingsPanel() {
        var selectedRegion = activeTmdbRegion();
        var regions = BuroTmdb.supportedRegions().map(function (region) {
            var selected = selectedRegion === region;
            return '<button class="subtitle-choice focusable ' + (selected ? 'selected' : '') +
                '" data-action="settings-region" data-region="' + attr(region) + '" aria-pressed="' +
                (selected ? 'true' : 'false') + '"><strong>' + escapeHtml(region) + '</strong></button>';
        }).join('');
        return '<div class="section-heading"><h2>' + t('subscriptionsRegion') + '</h2></div>' +
            '<section class="subtitle-settings-card region-settings-card"><p>' + t('settingsRegionHint') +
            '</p><div class="subtitle-choice-row">' + regions + '</div></section>';
    }

    function duplicateSettingsPanel() {
        var enabled = state.preferences.collapseDuplicateTitles !== false;
        return '<div class="section-heading"><h2>' + t('duplicatesLabel') + '</h2></div>' +
            '<div class="settings-grid"><button class="setting-card focusable ' + (enabled ? 'on' : '') +
            '" data-action="collapse-duplicates" aria-pressed="' + (enabled ? 'true' : 'false') + '">' +
            '<div><h3>' + t('duplicatesToggle') + '</h3><p>' + t('duplicatesHint') +
            '</p></div><span class="toggle"><span></span></span></button></div>';
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
        var data = state.screenData || {};
        var pending = Boolean(data.busy);
        var draft = String(data.licenceDraft || '').substring(0, 32);
        var usable = data.keyInfoKey === draft && data.keyInfo &&
            ['available', 'yours'].indexOf(data.keyInfo.state) >= 0;
        var info = licenceKeyInfoPresentation(data);

        root.innerHTML = '<main class="gate-screen"><div class="brand-mark">B</div>' +
            '<h1>' + t('licence') + '</h1>' +
            '<p class="gate-copy">' + escapeHtml(licenceStatusText(decision)) + '</p>' +
            '<div class="licence-device"><small>' + escapeHtml(t('licenceDeviceId')) + '</small>' +
            '<strong>' + escapeHtml(deviceId || '—') + '</strong></div>' +
            licencePurchaseHtml(deviceId) +
            '<div class="field"><label>' + t('licenceKey') + '</label>' +
            '<input id="licence-key" class="focusable" maxlength="32" autocomplete="off" value="' +
            attr(draft) + '"><div class="form-message licence-key-state ' + info.className + '">' +
            escapeHtml(info.message) + '</div></div>' +
            '<div class="action-row">' +
            '<button class="button primary focusable" data-action="licence-redeem"' +
            (pending || !usable ? ' disabled' : '') + '>' +
            (pending ? t('licenceChecking') : t('licenceRedeem')) + '</button>' +
            (data.keyInfoError ? '<button class="button ghost focusable" data-action="licence-inspect">' +
                t('licenceKeyRetry') + '</button>' : '') +
            '<button class="button ghost focusable" data-action="back">' + t('back') + '</button>' +
            '</div></main>';
    }

    function licenceKeyInfoPresentation(data) {
        var info = data && data.keyInfo;
        var key;
        var message = '';
        if (data && data.inspecting) {
            return { message: t('licenceKeyInspecting'), className: '' };
        }
        if (data && data.keyInfoError) {
            return { message: t(data.keyInfoError === 'offline' ? 'licenceOffline' : 'licenceUnavailable'), className: 'error' };
        }
        if (!info || data.keyInfoKey !== data.licenceDraft) { return { message: '', className: '' }; }
        key = {
            available: 'licenceKeyAvailable',
            yours: 'licenceKeyYours',
            in_use: 'licenceKeyInUse',
            expired: 'licenceKeyExpired',
            unknown: 'licenceKeyUnknown'
        }[info.state];
        message = key ? t(key) : '';
        message = message.replace('{days}', info.grantDays == null ? '—' : String(info.grantDays));
        return {
            message: message,
            className: ['available', 'yours'].indexOf(info.state) >= 0 ? 'success' : 'error'
        };
    }

    function clearLicenceKeyTimer(invalidate) {
        if (licenceKeyTimer) { window.clearTimeout(licenceKeyTimer); licenceKeyTimer = null; }
        if (invalidate) { licenceKeyRequestId += 1; }
    }

    function inspectLicenceKey(value, requestId) {
        var data = state.screenData || {};
        if (state.screen !== 'LICENCE' || requestId !== licenceKeyRequestId ||
                String(data.licenceDraft || '') !== value) { return; }
        data.inspecting = true;
        data.keyInfoError = null;
        state.screenData = data;
        render();
        BuroLicense.keyInfo(value, function (info) {
            if (state.screen !== 'LICENCE' || requestId !== licenceKeyRequestId ||
                    !state.screenData || state.screenData.licenceDraft !== value) { return; }
            state.screenData.inspecting = false;
            state.screenData.keyInfo = info;
            state.screenData.keyInfoKey = value;
            state.screenData.keyInfoError = null;
            render();
        }, function (error) {
            var offline = error && ['NETWORK_ERROR', 'NETWORK_TIMEOUT', 'LICENSE_UNREACHABLE'].indexOf(error.code) >= 0;
            if (state.screen !== 'LICENCE' || requestId !== licenceKeyRequestId ||
                    !state.screenData || state.screenData.licenceDraft !== value) { return; }
            state.screenData.inspecting = false;
            state.screenData.keyInfo = null;
            state.screenData.keyInfoKey = '';
            state.screenData.keyInfoError = offline ? 'offline' : 'failed';
            render();
        });
    }

    function scheduleLicenceKeyInspection(value, immediate) {
        var code = BuroDomain.trim(String(value || '')).toUpperCase().substring(0, 32);
        var requestId;
        var data = state.screenData || {};
        clearLicenceKeyTimer(true);
        requestId = licenceKeyRequestId;
        data.licenceDraft = code;
        data.inspecting = false;
        data.keyInfo = null;
        data.keyInfoKey = '';
        data.keyInfoError = null;
        state.screenData = data;
        if (code.length < 6) { return; }
        licenceKeyTimer = window.setTimeout(function () {
            licenceKeyTimer = null;
            inspectLicenceKey(code, requestId);
        }, immediate ? 0 : LICENCE_KEY_DEBOUNCE_MILLIS);
    }

    function bindLicenceKeyInput() {
        var input = root && root.querySelector ? root.querySelector('#licence-key') : null;
        if (!input || state.screen !== 'LICENCE') { return; }
        input.addEventListener('input', function () {
            var message;
            var redeem;
            scheduleLicenceKeyInspection(input.value, false);
            input.value = state.screenData.licenceDraft;
            message = root.querySelector('.licence-key-state');
            redeem = root.querySelector('[data-action="licence-redeem"]');
            if (message) { message.textContent = ''; message.className = 'form-message licence-key-state'; }
            if (redeem) { redeem.disabled = true; }
        });
    }

    /*
      Como comprar, sem digitar o código do aparelho em lugar nenhum.

      O QR já leva o dispositivo na URL, então o celular abre a página de compra
      com o campo preenchido. O endereço aparece por extenso ao lado porque nem
      toda câmera lê QR de tela de TV, e um endereço legível é a saída quando
      isso acontece.
    */
    function licencePurchaseHtml(deviceId) {
        var url = BuroPairing.BASE + '/comprar' +
            (deviceId ? '?device=' + encodeURIComponent(deviceId) : '');
        var matrix;
        var drawing = '';
        try {
            matrix = BuroQr.encode(url);
            drawing = matrix ? BuroQr.svg(matrix) : '';
        } catch (ignoredQr) { drawing = ''; }
        return '<div class="licence-purchase">' +
            (drawing ? '<div class="licence-qr">' + drawing + '</div>' : '') +
            '<div class="licence-purchase-copy"><p>' + escapeHtml(t('licenceBuyHint')) + '</p>' +
            '<strong>' + escapeHtml(url.replace(/^https:\/\//, '')) + '</strong></div></div>';
    }

    function redeemLicenceKey() {
        var field = document.getElementById('licence-key');
        var value = BuroDomain.trim(field ? field.value : '').toUpperCase();
        var data = state.screenData || {};
        if (!value) { showToast(t('licenceKeyInvalid'), true); return; }
        if (!data.keyInfo || data.keyInfoKey !== value ||
                ['available', 'yours'].indexOf(data.keyInfo.state) < 0) {
            scheduleLicenceKeyInspection(value, true);
            return;
        }

        data.busy = true;
        state.screenData = data;
        render();
        BuroLicense.redeem(value, function () {
            state.screenData = null;
            showToast(t('licenceRedeemed'));
            render();
        }, function (error) {
            data.busy = false;
            state.screenData = data;
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
            '<div class="section-heading"><h2>' + t('receiverTitle') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="pair-title"><div><h3>' + t('receiverAction') +
            '</h3><p>' + t('receiverHint') + '</p></div><strong>›</strong></button></div>' +
            '<div class="section-heading"><h2>' + t('metadata') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable ' + (tmdbConfiguration.effective ? 'on' : '') +
            '" data-action="tmdb-settings"><div><h3>' + t('tmdbTitle') + '</h3><p>' +
            (tmdbConfiguration.effective ? t('configured') : t('notConfigured')) + '</p></div><strong>TMDb</strong></button>' +
             '<button class="setting-card focusable ' + (BuroCritics.configured() ? 'on' : '') +
            '" data-action="critics-settings"><div><h3>' + t('criticsTitle') + '</h3><p>' +
            (BuroCritics.configured() ? t('configured') : t('notConfigured')) + '</p></div><strong>OMDb</strong></button></div>' +
            regionSettingsPanel() +
            '<div class="section-heading"><h2>' + t('contentProtection') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="parental-form"><div><h3>' + t('parentalPin') +
            '</h3><p>' + (state.preferences.parentalPin ? t('configured') : t('notConfigured')) + '</p></div><strong>PIN</strong></button>' +
            '<button class="setting-card focusable ' + (state.preferences.parentalPin && state.preferences.lockAdultCategories ? 'on' : '') +
            '" data-action="toggle-adult-lock"><div><h3>' + t('lockAdult') + '</h3><p>' +
            (state.preferences.parentalPin ? t(state.preferences.lockAdultCategories ? 'settingOn' : 'settingOff') : t('pinRequired')) +
            '</p></div><span class="toggle"><span></span></span></button>' +
            '<button class="setting-card focusable" data-action="category-settings"><div><h3>' + t('categoryControl') +
            '</h3><p>' + manageableCategories.length + '</p></div><strong>›</strong></button></div>' +
            duplicateSettingsPanel() +
            subtitleSettings +
            '<div class="section-heading"><h2>' + t('storageTitle') + '</h2></div><div class="settings-grid">' +
            '<button class="setting-card focusable" data-action="storage-settings"><div><h3>' +
            t('storageTitle') + '</h3><p>' + escapeHtml(t('storageHint')) + '</p></div><strong>›</strong></button></div>' +
            '<div class="section-heading"><h2>' + t('settings') + '</h2></div><div class="settings-grid">' +
            settingCard('reducedMotion', 'reducedMotion') + settingCard('highContrast', 'highContrast') +
            settingCard('removeTransparency', 'reducedTransparency') + '</div>' +
            clockSettingsPanel() +
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
            t('tmdbBody') + '</p><div class="tmdb-guide-action">' +
            '<button class="button primary focusable" data-action="pair-tmdb">' + t('pairFromPhone') + '</button>' +
            '<button class="button ghost focusable" data-action="tmdb-guide">?' +
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

      O catálogo permanece no IndexedDB da aplicação. Capas só ganham uma cópia
      persistente quando a pessoa ativa explicitamente o cache num USB; vídeo
      baixado também fica no USB e tem a própria tela em Downloads.
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

    /* Megabytes legiveis: "512 MB" ou "1,4 GB", porque um numero de bytes numa
       TV nao diz nada a ninguem. */
    function formatCacheSize(bytes) {
        var mb = Number(bytes) / (1024 * 1024);
        if (!isFinite(mb) || mb <= 0) { return '0 MB'; }
        if (mb >= 1024) { return (mb / 1024).toFixed(1).replace('.', ',') + ' GB'; }
        return Math.round(mb) + ' MB';
    }

    function knownArtworkEntries() {
        var entries = [];
        var known = {};
        Object.keys(artworkMemory).forEach(function (id) {
            var url = safeArtworkUrl(artworkMemory[id]);
            if (url && !known[id]) { known[id] = true; entries.push({ id: id, url: url }); }
        });
        (state.items || []).forEach(function (item) {
            var url = safeArtworkUrl(item && item.logoUrl);
            if (item && item.id && url && !known[item.id]) {
                known[item.id] = true;
                entries.push({ id: item.id, url: url });
            }
        });
        return entries;
    }

    function fillKnownArtwork() {
        BuroArtworkCache.fill(knownArtworkEntries());
        render();
    }

    function artworkCacheProgressHtml(status) {
        var stateText = '';
        var rateText = '';
        var progress = '';
        var action;
        if (status.total > 0) {
            stateText = t(status.paused ? 'artworkCachePaused' :
                (status.complete ? 'artworkCacheComplete' : 'artworkCacheFilling'))
                .replace('{done}', status.done).replace('{total}', status.total)
                .replace('{percent}', status.percent == null ? 0 : status.percent);
            if (status.running && Number(status.bytesPerSecond) > 0) {
                rateText = formatDownloadRate(status.bytesPerSecond);
            }
            progress = '<div class="artwork-cache-progress" role="progressbar" aria-valuemin="0"' +
                ' aria-valuemax="100" aria-valuenow="' + (status.percent == null ? 0 : status.percent) + '">' +
                '<span style="width:' + (status.percent == null ? 0 : status.percent) + '%"></span></div>' +
                '<p class="artwork-cache-state"><span>' + escapeHtml(stateText) + '</span>' +
                (rateText ? '<strong class="artwork-cache-rate">' + escapeHtml(rateText) + '</strong>' : '') +
                '</p>' +
                (status.failed ? '<p class="form-note warning">' +
                    escapeHtml(t('artworkCacheFailed').replace('{count}', status.failed)) + '</p>' : '');
        }
        if (status.running) {
            action = '<button class="button ghost focusable" data-action="artwork-cache-pause">' +
                escapeHtml(t('artworkCachePause')) + '</button>';
        } else if (status.paused && status.pending) {
            action = '<button class="button primary focusable" data-action="artwork-cache-resume">' +
                escapeHtml(t('artworkCacheResume')) + '</button>';
        } else {
            action = '<button class="button ghost focusable" data-action="artwork-cache-fill">' +
                escapeHtml(t(status.complete ? 'artworkCacheRefresh' : 'artworkCacheStart')) + '</button>';
        }
        return progress + '<div class="action-row artwork-cache-fill-actions">' + action + '</div>';
    }

    /*
      O painel do cache de capas no pendrive.

      Depende de um aparelho que a pessoa precisa plugar, entao o painel diz o
      estado antes de oferecer a acao: sem pendrive o botao nao aparece, e a
      explicacao toma o lugar dele. Prometer uma funcao que nao pode funcionar
      agora seria pior do que dizer o que falta.
    */
    function artworkCachePanelHtml() {
        var status = BuroArtworkCache.status();
        var body;
        if (!status.hasStorage) {
            body = '<p class="form-note">' + escapeHtml(t('artworkCacheNoUsb')) + '</p>';
        } else if (!status.enabled) {
            body = '<p class="form-note">' + escapeHtml(t('artworkCacheOffHint')) + '</p>' +
                '<div class="action-row"><button class="button primary focusable" data-action="artwork-cache-on">' +
                escapeHtml(t('artworkCacheEnable')) + '</button></div>';
        } else {
            body = '<p class="form-note">' +
                escapeHtml(t('artworkCacheUsage')
                    .replace('{count}', status.count)
                    .replace('{size}', formatCacheSize(status.bytes))
                    .replace('{limit}', status.limitMb + ' MB')) + '</p>' +
                artworkCacheProgressHtml(status) +
                '<div class="action-row">' +
                '<button class="button ghost focusable" data-action="artwork-cache-limit">' +
                escapeHtml(t('artworkCacheLimit').replace('{limit}', status.limitMb + ' MB')) + '</button>' +
                '<button class="button ghost focusable" data-action="artwork-cache-clear">' +
                escapeHtml(t('artworkCacheClear')) + '</button>' +
                '<button class="button ghost focusable" data-action="artwork-cache-off">' +
                escapeHtml(t('artworkCacheDisable')) + '</button></div>';
        }
        return '<section class="tmdb-key-scope artwork-cache-panel"><h3>' +
            escapeHtml(t('artworkCacheTitle')) + '</h3>' + body + '</section>';
    }

    /* Liga ou desliga o cache no pendrive, guardando a escolha. Desligar nao
       apaga nada: quem desliga hoje pode religar amanha e achar as capas onde
       deixou. */
    function setArtworkCache(on) {
        state.preferences.artworkCacheEnabled = Boolean(on);
        savePreferences();
        if (on) {
            BuroArtworkCache.attach(state.preferences.artworkCacheLimitMb, function (attached) {
                if (attached) { fillKnownArtwork(); }
                else { render(); }
            });
        } else {
            BuroArtworkCache.detach();
        }
        render();
    }

    /* Os tamanhos oferecidos. Poucos e redondos: numa TV nao ha como digitar um
       numero, e "quanto do meu pendrive" nao e uma pergunta de precisao. */
    var ARTWORK_CACHE_STEPS = [256, 512, 1024, 2048, 4096];

    function cycleArtworkCacheLimit() {
        var current = BuroArtworkCache.safeLimitMb(state.preferences.artworkCacheLimitMb);
        var index = ARTWORK_CACHE_STEPS.indexOf(current);
        var next = ARTWORK_CACHE_STEPS[(index + 1) % ARTWORK_CACHE_STEPS.length];
        state.preferences.artworkCacheLimitMb = next;
        savePreferences();
        BuroArtworkCache.attach(next, function () { render(); });
        render();
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
            '</div>' + artworkCachePanelHtml() +
            '<p class="form-note privacy">' + escapeHtml(t('storageClearHint')) + '</p>' +
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
            forgetHomeCache();
            catalogueSize = 0;
            catalogueSizeMeasuredAt = 0;
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

    /*
      A tela que mostra o código e espera o celular.

      Três passos numerados em vez de um parágrafo: quem está lendo isto está de
      pé com o celular na mão, olhando para a TV do outro lado da sala, e um
      texto corrido nessa posição não se lê.

      O código sai em dígitos grandes e espaçados porque vai ser copiado de
      longe, à mão.
    */
    /*
      O QR da tela de pareamento, com o código já dentro do endereço.

      Pedido do usuário: "seria mais rapido colocar qr code para ir mais rapido
      pro site". A câmera abre a página com o campo do código já preenchido —
      `/parear` aceita `?code=` — então sobra digitar só a chave, que é o que
      não cabe num controle remoto.

      O endereço e os seis dígitos continuam por extenso ao lado: nem toda
      câmera lê QR de tela de TV, e digitar é a saída quando isso acontece.
      Some sem alarde se a codificação falhar, pelo mesmo motivo.
    */
    function pairQrHtml(code, kind) {
        var url = BuroPairing.BASE + '/parear' +
            (/^[0-9]{6}$/.test(String(code)) ? '?code=' + encodeURIComponent(code) : '') +
            (kind === 'open_title' ? '&kind=open_title' : '');
        var drawing = '';
        var matrix;
        try {
            matrix = BuroQr.encode(url);
            drawing = matrix ? BuroQr.svg(matrix) : '';
        } catch (ignoredPairQr) { drawing = ''; }
        return drawing ? '<div class="pair-qr" aria-hidden="true">' + drawing + '</div>' : '';
    }

    /**
     * O codigo deste aparelho, para quem nao consegue configurar sozinho.
     *
     * E assim que quem vendeu a lista encontra esta televisao no painel e a
     * configura de longe. Precisa estar alcancavel antes de existir qualquer
     * lista: quem mais precisa disto e exatamente quem ainda nao conseguiu
     * configurar nada, e ate agora o codigo so aparecia na tela de licenca.
     *
     * Numa televisao nao ha como copiar. O codigo e lido da tela e enviado por
     * mensagem, entao e desenhado grande, no mesmo estilo do pareamento.
     */
    /*
      O teste de ligacao.

      Quem esta a ver um filme que trava nao sabe se a culpa e do Wi-Fi, do
      provedor ou do aplicativo. Sem resposta conclui que o aplicativo esta com
      defeito, entao cada leitura aqui vira uma frase que a pessoa pode usar.
    */
    function openDiagnostics() {
        pushScreen('DIAGNOSTICS', { running: true, findings: null });
        runDiagnostics();
    }

    function closeDiagnostics() {
        goBack();
    }

    /*
      Corre as medicoes e desenha o resultado.

      A duracao minima nao e para a medicao: e para a pessoa. Um teste que falha
      num instante — provedor fora do ar — acabaria antes de o ecra desenhar um
      unico quadro, e carregar no botao pareceria nao fazer nada.
    */
    function runDiagnostics() {
        var source = state.activeSource;
        var started = new Date().getTime();
        var secret = null;

        state.screenData = { running: true, findings: null };
        render();

        function finish(findings, readings) {
            var elapsed = new Date().getTime() - started;
            var wait = Math.max(0, DIAGNOSTICS_MINIMUM_MS - elapsed);
            window.setTimeout(function () {
                if (state.screen !== 'DIAGNOSTICS') { return; }
                state.screenData = { running: false, findings: findings, readings: readings };
                render();
            }, wait);
        }

        /* Sem fonte nao ha o que medir, mas as leituras locais continuam uteis. */
        if (!source || source.type !== 'XTREAM') {
            finish(localFindings(0, 'signed-out'), {});
            return;
        }

        /* Sincrono e pode lancar: um cofre indisponivel nao pode derrubar o ecra. */
        try {
            secret = BuroStorage.secureGet(source.id);
        } catch (unavailable) {
            secret = null;
        }
        if (!secret) { finish(localFindings(0, 'signed-out'), {}); return; }
        measureDiagnostics(secret);

        function measureDiagnostics(credentials) {
            var probeUrl = BuroDiagnostics.probeUrl(credentials);
            BuroDiagnostics.measureTransfer(probeUrl, function (transfer) {
                var mbps = transfer
                    ? BuroDiagnostics.megabitsPerSecond(transfer.bytes, transfer.milliseconds)
                    : null;
                BuroDiagnostics.measureLatency(probeUrl, BuroDiagnostics.PING_ATTEMPTS,
                    function (latency) {
                        var ping = BuroDiagnostics.median(latency.samples);
                        var loss = BuroDiagnostics.lossPercent(latency);
                        var findings = [
                            {
                                id: 'download',
                                severity: BuroDiagnostics.downloadVerdict(mbps),
                                detail: mbps === null ? '\u2014' : (Math.round(mbps * 10) / 10) + ' Mbit/s',
                                advice: BuroDiagnostics.qualityCeiling(mbps)
                            },
                            {
                                id: 'ping',
                                severity: BuroDiagnostics.pingVerdict(ping),
                                detail: ping === null ? '\u2014' : ping + ' ms',
                                /* Diz o que significa, e nao so fica vermelho: a latencia e a
                                   leitura que mais explica uma imagem que trava numa ligacao
                                   cuja velocidade parece boa. */
                                advice: BuroDiagnostics.latencyAdvice(ping)
                            },
                            {
                                id: 'loss',
                                severity: BuroDiagnostics.lossVerdict(loss),
                                detail: loss === null ? '\u2014'
                                    : (Math.round(loss * 10) / 10) + '% de ' + latency.attempted
                            }
                        ];
                        finish(findings.concat(localFindings(catalogueCount(), null)), {
                            quality: BuroDiagnostics.qualityCeiling(mbps)
                        });
                    });
            });
        }
    }

    /* Quantos itens a lista trouxe: uma ligacao perfeita com catalogo vazio ainda e um problema. */
    function catalogueCount() {
        return (state.channels && state.channels.length) || 0;
    }

    /*
      As leituras que nao precisam de rede.

      Feitas a parte porque funcionam sem internet nenhuma — que e exatamente
      quando alguem abre este ecra.
    */
    function localFindings(itemCount, catalogueOverride) {
        var link = BuroDiagnostics.linkKind();
        var findings = [];

        findings.push({
            id: 'catalogue',
            severity: catalogueOverride === 'signed-out' ? 'WARNING'
                : (itemCount <= 0 ? 'PROBLEM' : 'GOOD'),
            detail: catalogueOverride === 'signed-out' ? '\u2014' : String(itemCount),
            advice: catalogueOverride === 'signed-out' ? 'signed-out'
                : (itemCount <= 0 ? 'empty' : null)
        });
        findings.push({
            id: 'link',
            /* Wi-Fi nao e defeito, mas e a explicacao mais comum para uma ligacao
               que mede bem e mesmo assim corta. */
            severity: link === 'none' ? 'PROBLEM' : (link === 'wireless' ? 'WARNING' : 'GOOD'),
            /*
              Um travessao quando a plataforma nao sabe dizer o tipo de ligacao.

              A Tizen so expoe isso em algumas versoes, e reaproveitar aqui a
              frase da velocidade — "nao foi possivel medir a velocidade" — dizia
              a coisa errada sobre a linha errada. Um travessao e honesto.
            */
            detail: link === 'wireless' ? t('diagnosticsWireless')
                : (link === 'wired' ? t('diagnosticsWired')
                : (link === 'none' ? t('diagnosticsNoLink') : '—')),
            advice: link
        });
        return findings;
    }

    function diagnosticsMark(severity) {
        if (severity === 'GOOD') { return '\u25CF'; }
        if (severity === 'WARNING') { return '\u25B2'; }
        return '\u25A0';
    }

    function diagnosticsLabel(id) {
        if (id === 'download') { return t('diagnosticsDownload'); }
        if (id === 'ping') { return t('diagnosticsPing'); }
        if (id === 'loss') { return t('diagnosticsLoss'); }
        if (id === 'catalogue') { return t('diagnosticsCatalogue'); }
        if (id === 'link') { return t('diagnosticsConnection'); }
        return id;
    }

    function diagnosticsQualityLabel(ceiling) {
        if (ceiling === 'unstable') { return t('diagnosticsQualityUnstable'); }
        if (ceiling === 'sd') { return t('diagnosticsQualitySd'); }
        if (ceiling === 'hd') { return t('diagnosticsQualityHd'); }
        if (ceiling === 'uhd') { return t('diagnosticsQualityUhd'); }
        return t('diagnosticsQualityUnknown');
    }

    function diagnosticsLatencyLabel(advice) {
        if (advice === 'good') { return t('diagnosticsLatencyGood'); }
        if (advice === 'fair') { return t('diagnosticsLatencyFair'); }
        if (advice === 'unstable') { return t('diagnosticsLatencyUnstable'); }
        return t('diagnosticsLatencyUnknown');
    }

    /* A frase por baixo da leitura, so quando ha uma que valha a pena ler. */
    function diagnosticsAdvice(finding) {
        /* Sempre, e nao so quando esta ma: "os canais trocam sem espera" vale a pena ler,
           e uma leitura boa sem nada por baixo parece que o aplicativo nao teve nada a
           dizer. */
        if (finding.id === 'ping') { return diagnosticsLatencyLabel(finding.advice); }
        if (finding.id === 'download' && finding.severity !== 'GOOD') {
            return diagnosticsQualityLabel(finding.advice);
        }
        if (finding.id === 'link' && finding.advice === 'wireless') { return t('diagnosticsWireless'); }
        if (finding.id === 'link' && finding.advice === 'none') { return t('diagnosticsNoLink'); }
        if (finding.id === 'catalogue' && finding.advice === 'empty') { return t('diagnosticsCatalogueEmpty'); }
        if (finding.id === 'catalogue' && finding.advice === 'signed-out') { return t('diagnosticsSignedOut'); }
        return null;
    }

    function renderDiagnostics() {
        var data = state.screenData || {};
        var net = BuroDiagnostics.network();
        var body = '<div class="pair-panel">';
        var verdict;
        var rows = '';

        if (data.running) {
            /*
              Cada linha que o teste vai preencher, ja com o seu indicador. Sem
              isto o painel ficava vazio e enchia de golpe, o que numa falha
              rapida parecia um botao que nao fez nada.
            */
            body += '<p class="form-note"><span class="boot-indicator"></span> ' +
                escapeHtml(t('diagnosticsRunning')) + '</p>';
            DIAGNOSTICS_ROWS.forEach(function (id) {
                rows += '<div class="diagnostics-row pending"><span class="boot-indicator"></span>' +
                    '<span class="diagnostics-name">' + escapeHtml(diagnosticsLabel(id)) + '</span></div>';
            });
        } else if (!data.findings) {
            body += '<p class="form-note">' + escapeHtml(t('diagnosticsQualityUnknown')) + '</p>';
        } else {
            verdict = BuroDiagnostics.overall(data.findings);
            /* O veredito num painel da sua cor, e nao mais uma linha de texto: e a
               unica coisa que quem nao ler mais nada tem de levar consigo. */
            body += '<div class="diagnostics-banner ' + verdict.toLowerCase() + '">' +
                '<span class="diagnostics-banner-mark">' + diagnosticsMark(verdict) + '</span>' +
                '<span class="diagnostics-banner-text"><strong>' +
                escapeHtml(t(verdict === 'GOOD' ? 'diagnosticsVerdictGood'
                    : (verdict === 'WARNING' ? 'diagnosticsVerdictWarning' : 'diagnosticsVerdictProblem'))) +
                '</strong><span>' +
                escapeHtml(diagnosticsQualityLabel((data.readings || {}).quality)) +
                '</span></span></div>';
            data.findings.forEach(function (finding) {
                var advice = diagnosticsAdvice(finding);
                rows += '<div class="diagnostics-row">' +
                    '<span class="diagnostics-mark ' + finding.severity.toLowerCase() + '">' +
                    diagnosticsMark(finding.severity) + '</span>' +
                    '<span class="diagnostics-name">' + escapeHtml(diagnosticsLabel(finding.id)) + '</span>' +
                    '<span class="diagnostics-value">' + escapeHtml(finding.detail) + '</span>' +
                    '</div>';
                if (advice) {
                    rows += '<div class="diagnostics-advice">' + escapeHtml(advice) + '</div>';
                }
            });
            /* Os enderecos, que sao o que um pedido de suporte pergunta. */
            if (net.address) {
                rows += diagnosticsFactHtml(t('diagnosticsAddress'), net.address);
            }
            if (net.netmask) {
                rows += diagnosticsFactHtml(t('diagnosticsNetmask'), net.netmask);
            }
            if (net.gateway) {
                rows += diagnosticsFactHtml(t('diagnosticsGateway'), net.gateway);
            }
        }

        body += '<div class="diagnostics-list' + (data.running ? ' pending' : '') + '">' +
            rows + '</div>';
        body += '<div class="form-actions">' +
            '<button class="button focusable" data-action="diagnostics-run"' +
            (data.running ? ' disabled' : '') + '>' +
            escapeHtml(data.running ? t('diagnosticsRunning') : t('diagnosticsRun')) + '</button>' +
            '<button class="button ghost focusable" data-action="diagnostics-close">' +
            escapeHtml(t('diagnosticsClose')) + '</button></div>';
        body += '</div>';
        shell(body, t('diagnosticsTitle'), true);
    }

    function diagnosticsFactHtml(label, value) {
        return '<div class="diagnostics-fact"><span class="diagnostics-name">' +
            escapeHtml(label) + '</span><span class="diagnostics-value">' +
            escapeHtml(value) + '</span></div>';
    }

    function renderDeviceCode() {
        var code = BuroLicense.deviceId();
        var body = code
            ? '<div class="pair-panel"><p class="form-note">' + escapeHtml(t('deviceCodeHelp')) +
              '</p><div class="pair-body"><strong class="pair-code">' + escapeHtml(code) +
              '</strong></div></div>'
            // Sem identidade nao ha o que ler em voz alta, e um espaco em branco
            // seria pior do que dizer que ainda nao esta pronto.
            : '<div class="pair-panel"><p class="form-note">' + escapeHtml(t('deviceCodeHelp')) +
              '</p></div>';
        shell(body, t('deviceCode'), true);
    }

    function renderPairing() {
        var data = state.screenData || {};
        var receivingTitle = data.kind === 'open_title';
        var hintKey = receivingTitle ? 'receiverHint' : 'pairHint';
        var titleKey = receivingTitle ? 'receiverTitle' : 'pairTitle';
        var step3Key = receivingTitle ? 'receiverStep3' : 'pairStep3';
        var body;
        if (data.error) {
            body = emptyState('!', t(data.error === 'PAIRING_EXPIRED' ? 'pairExpired' :
                (data.error === 'PAIRING_PAYLOAD_INVALID' ? 'receiverInvalid' : 'pairFailed')),
                t(hintKey), 'pair-retry', t('pairRetry'));
        } else if (!data.code) {
            body = '<div class="search-loading"><span class="boot-indicator"></span><p>' +
                escapeHtml(t('pairStarting')) + '</p></div>';
        } else {
            body = '<div class="pair-panel"><p class="form-note">' + escapeHtml(t(hintKey)) + '</p>' +
                '<div class="pair-body">' + pairQrHtml(data.code, data.kind) +
                '<ol class="pair-steps">' +
                '<li><span>' + escapeHtml(t('pairStep1')) + '</span><strong class="pair-url">' +
                escapeHtml(BuroPairing.phoneUrl()) + '</strong></li>' +
                '<li><span>' + escapeHtml(t('pairStep2')) + '</span><strong class="pair-code">' +
                escapeHtml(data.code) + '</strong></li>' +
                '<li><span>' + escapeHtml(t(step3Key)) + '</span></li></ol></div>' +
                '<p class="pair-waiting"><span class="boot-indicator"></span>' +
                escapeHtml(t('pairWaiting')) + '</p>' +
                '<p class="form-note">' + escapeHtml(t('pairExpiresIn').replace('{minutes}',
                    Math.max(1, Math.round((Number(data.seconds) || 300) / 60)))) + '</p>' +
                '<div class="action-row"><button class="button ghost focusable" data-action="back">' +
                t('cancel') + '</button></div></div>';
        }
        shell(body, t(titleKey), true);
    }

    /*
      A tela do "Enviar a tela": o codigo que o outro aparelho vai abrir.

      Mesma forma da tela de parear — QR, endereco por extenso e os seis
      digitos — porque e o mesmo gesto visto do outro lado: la o celular manda
      para a TV, aqui a TV manda para o celular. O QR ja leva o codigo no
      endereco, entao quem aponta a camera chega com o campo preenchido.

      Nao ha espera: publicado o codigo, o lado da TV terminou. Por isso a tela
      nao mostra "aguardando" — seria uma espera que nao existe.
    */
    function renderSendToScreen() {
        var data = state.screenData || {};
        var heading = data.title ? data.title + (data.year ? ' (' + data.year + ')' : '') : '';
        var body;
        if (data.error) {
            body = emptyState('!', t(data.error === 'PAIRING_EXPIRED' ? 'pairExpired' : 'pairFailed'),
                t('castHint'), 'back', t('back'));
        } else if (!data.code) {
            body = '<div class="search-loading"><span class="boot-indicator"></span><p>' +
                escapeHtml(t('pairStarting')) + '</p></div>';
        } else {
            body = '<div class="pair-panel">' +
                (heading ? '<p class="form-note">' +
                    escapeHtml(t('castSending').replace('{title}', heading)) + '</p>' : '') +
                '<div class="pair-body">' + pairQrHtml(data.code) +
                '<ol class="pair-steps">' +
                '<li><span>' + escapeHtml(t('pairStep1')) + '</span><strong class="pair-url">' +
                escapeHtml(BuroPairing.phoneUrl()) + '</strong></li>' +
                '<li><span>' + escapeHtml(t('pairStep2')) + '</span><strong class="pair-code">' +
                escapeHtml(data.code) + '</strong></li>' +
                '<li><span>' + escapeHtml(t('castStep3')) + '</span></li></ol></div>' +
                '<p class="form-note">' + escapeHtml(t('pairExpiresIn').replace('{minutes}',
                    Math.max(1, Math.round((Number(data.seconds) || 300) / 60)))) + '</p>' +
                '<div class="action-row"><button class="button ghost focusable" data-action="back">' +
                t('back') + '</button></div></div>';
        }
        shell(body, t('castAction'), true);
    }

    function startPairing(kind) {
        cancelPairing();
        pushScreen('PAIRING', { kind: kind });
        pairingRequest = BuroPairing.start(kind, {
            code: function (code, seconds) {
                if (state.screen !== 'PAIRING') { return; }
                state.screenData = { kind: kind, code: code, seconds: seconds };
                render();
            },
            success: function (value) {
                pairingRequest = null;
                if (state.screen !== 'PAIRING') { return; }
                applyPairedValue(kind, value);
                value = null;
            },
            failure: function (error) {
                pairingRequest = null;
                if (state.screen !== 'PAIRING') { return; }
                state.screenData = { kind: kind, error: (error && error.code) || 'PAIRING_FAILED' };
                render();
            }
        });
    }

    function cancelPairing() {
        if (pairingRequest && pairingRequest.cancel) { pairingRequest.cancel(); }
        pairingRequest = null;
    }

    /*
      Guarda o que veio do celular pelo mesmo caminho da digitação manual: a
      validação e o armazenamento seguro são os mesmos, então uma chave enviada
      do celular não entra por uma porta com menos conferência.
    */
    function applyPairedValue(kind, value) {
        var profileId = state.activeProfile && state.activeProfile.id;
        var receivedTitle;
        if (kind === 'open_title') {
            receivedTitle = BuroShare.parsePairingPayload(value);
            if (!receivedTitle) {
                state.screenData = { kind: kind, error: 'PAIRING_PAYLOAD_INVALID' };
                render();
                return;
            }
            pendingSharedTitle = receivedTitle;
            sharedTitleNoticeVisible = false;
            sharedTitleNeedsResolution = true;
            sharedTitleResolveId += 1;
            goBack();
            showToast(t('receiverReceived').replace('{title}', receivedTitle.title), false);
            resolvePendingSharedTitle();
            return;
        }
        if (kind === 'critics_key') {
            BuroCritics.save(value, function () {
                clearTmdbDetails();
                goBack();
                showToast(t('pairReceived'), false);
            }, function () {
                state.screenData = { kind: kind, error: 'PAIRING_FAILED' };
                render();
            });
            return;
        }
        BuroTmdb.validateKey(value, function (validated) {
            BuroTmdb.save('profile', profileId, validated, function () {
                clearTmdbDetails();
                goBack();
                showToast(t('pairReceived'), false);
            }, function () {
                state.screenData = { kind: kind, error: 'PAIRING_FAILED' };
                render();
            });
        }, function () {
            state.screenData = { kind: kind, error: 'TMDB_KEY_REJECTED' };
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
            '<button class="button primary focusable" data-action="pair-critics">' + t('pairFromPhone') + '</button>' +
            '<button class="button ghost focusable" data-action="critics-save">' + t('criticsSave') + '</button>' +
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
        state.screenData = { messageKey: 'criticsChecking', error: false };
        render();
        BuroCritics.validateKey(value, function (validated) {
            if (state.screen !== 'CRITICS_SETTINGS') { return; }
            BuroCritics.save(validated, function () {
                if (state.screen !== 'CRITICS_SETTINGS') { return; }
                /* As notas guardadas com os títulos foram calculadas sem chave, então
                   saem daqui para que a próxima visita busque de verdade. */
                clearTmdbDetails();
                state.screenData = { messageKey: 'criticsSaved', error: false }; render();
            }, function () {
                if (state.screen !== 'CRITICS_SETTINGS') { return; }
                state.screenData = { messageKey: 'tmdbSecureError', error: true }; render();
            });
        }, function (error) {
            if (state.screen !== 'CRITICS_SETTINGS') { return; }
            state.screenData = { messageKey: error && error.code === 'CRITICS_KEY_REJECTED' ?
                'criticsKeyRejected' : (error && error.code === 'CRITICS_KEY_INVALID' ?
                    'criticsKeyInvalid' : 'criticsUnavailable'), error: true };
            render();
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
                resetHomeStreamingShelves();
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
            resetHomeStreamingShelves();
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
        var region = activeTmdbRegion();
        var cached;
        var requestId;
        kind = String(kind || 'MOVIES').toUpperCase();
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        requestId = ++subscriptionRequestId;
        cached = !force && key ? BuroTmdb.readShelfCache(region, kind, state.preferences.language) : null;
        state.screenData = {
            kind: 'subscriptions', filter: kind, region: region, loading: !cached,
            completedServices: 0, totalServices: 0, shelves: cached || [], error: null, selected: null
        };
        render();
        /*
          Sem chave, a tela diz o que falta em vez de jogar o usuário nas
          configurações. O desvio silencioso era defensável quando a guia só
          existia com chave; agora que ela aparece sempre — como no Windows —
          ser levado para outra tela sem explicação parece defeito.
        */
        if (!key) {
            state.screenData = {
                kind: 'subscriptions', filter: kind, region: region, loading: false,
                completedServices: 0, totalServices: 0, shelves: [], error: 'NO_KEY', selected: null
            };
            render();
            return;
        }
        if (cached) { return; }
        subscriptionRequest = BuroTmdb.loadShelves(key, region, kind, state.preferences.language,
            function (completed, total, visible) {
                if (requestId !== subscriptionRequestId || state.section !== 'SUBSCRIPTIONS' || !state.screenData ||
                        state.screenData.filter !== kind || state.screenData.region !== region) { return; }
                state.screenData.completedServices = completed;
                state.screenData.totalServices = total;
                state.screenData.visibleServices = visible;
                render();
            }, function (shelves) {
                if (requestId !== subscriptionRequestId || state.section !== 'SUBSCRIPTIONS' || !state.screenData ||
                        state.screenData.filter !== kind || state.screenData.region !== region) { return; }
                subscriptionRequest = null;
                state.screenData.loading = false;
                state.screenData.shelves = shelves;
                BuroTmdb.writeShelfCache(region, kind, state.preferences.language, shelves);
                render();
            }, function (error) {
                if (requestId !== subscriptionRequestId || state.section !== 'SUBSCRIPTIONS' || !state.screenData ||
                        state.screenData.filter !== kind || state.screenData.region !== region) { return; }
                subscriptionRequest = null;
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
                /*
                  O proprio aplicativo tem marca; o TMDb nao a fornece.

                  As outras linhas mostram o logo que o catalogo de provedores
                  entrega. O IPTV BURO nao esta nesse catalogo — e nem deveria
                  — entao ficava sem nada, a unica linha sem identidade numa
                  lista onde a marca e o que se le primeiro.
                */
                var mark = offer.localItem ? '<span class="subscription-offer-mark">B</span>' :
                    subscriptionOfferLogo(offer.providerLogoUrl);
                return '<button class="subscription-offer focusable" data-action="' + action + '"' +
                    (offer.localItem ? ' data-id="' + attr(offer.localItem.id) + '"' : ' data-url="' + attr(offer.url || '') + '"') +
                    '>' + mark + '<div><strong>' + escapeHtml(offer.providerName) + '</strong><p>' + t('offer' +
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
                t(expanded.titles.length ? 'subscriptionsMoreFailed' : 'tmdbUnavailable') + '</p>' : '');
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
            state.screenData = { kind: 'subscriptions', filter: 'MOVIES', region: activeTmdbRegion(),
                loading: true, shelves: [], selected: null };
            window.setTimeout(function () { loadSubscriptions('MOVIES'); }, 0);
            data = state.screenData;
        }
        if (data.error === 'NO_KEY') {
            shell(emptyState('$', t('subscriptions'), t('subscriptionsNeedKey'),
                'tmdb-settings', t('detailAddKey')), t('subscriptions'), true);
            return;
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
                return '<section data-provider="' + attr(String(shelf.providerId || shelf.providerName || '')) +
                    '"><div class="section-heading"><h2>' + subscriptionProviderLogo(shelf.providerLogoUrl) +
                    escapeHtml(shelf.providerName === 'coming-soon' ?
                    t('subscriptionsUpcomingShelf') : shelf.providerName) + '</h2>' +
                    /*
                      O "Ver mais" tambem no cabecalho, e nao so no fim da
                      fileira.

                      Ele existia so depois dos vinte posteres. Sete cabem na
                      tela, entao alcanca-lo exigia atravessar treze cartoes
                      que a pessoa nao queria ver — e nada dizia que ele
                      estava la. No cabecalho ele fica ao lado do numero que
                      promete os vinte, que e onde a pergunta "cade o resto"
                      aparece.

                      O do fim continua: quem varreu a fileira inteira chega
                      nele naturalmente, e tira-lo obrigaria a voltar.
                    */
                    (shelf.providerId ? '<button class="subscription-head-more focusable" data-action="subscription-expand" data-provider="' +
                        attr(shelf.providerId) + '">' + t('subscriptionsSeeMore') + ' ›</button>' : '') +
                    '<p>' + shelf.titles.length + '</p></div><div class="subscription-row">' +
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

    function findHomeStreamingTitle(key) {
        var found = null;
        homeStreamingShelves.some(function (shelf) {
            return (shelf.titles || []).some(function (title) {
                if (subscriptionTitleKey(title) === key) { found = title; return true; }
                return false;
            });
        });
        return found;
    }

    function openHomeStreamingTitle(key) {
        var title = findHomeStreamingTitle(key);
        var homeData = state.screenData;
        if (!title) { return; }
        state.section = 'SUBSCRIPTIONS';
        state.screen = 'SHELL';
        state.screenData = {
            kind: 'subscriptions', filter: 'MOVIES',
            region: activeTmdbRegion(),
            loading: false, shelves: homeStreamingShelves, selected: null
        };
        selectSubscriptionTitle(title, { screen: 'HOME', data: homeData, key: key });
    }

    function findSubscriptionShelf(providerId) {
        var found = null;
        (state.screenData && state.screenData.shelves || []).some(function (shelf) {
            if (String(shelf.providerId || '') === String(providerId || '')) { found = shelf; return true; }
            return false;
        });
        return found;
    }

    function startExpandedSubscription(shelf, initialTitles, directShortcut) {
        var data = state.screenData;
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (!data || !shelf || !shelf.providerId || data.filter === 'UPCOMING' || !key) { return; }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        data.expandedVisual = directShortcut ? null : subscriptionExpandedVisual(shelf.providerId);
        data.directShortcut = Boolean(directShortcut);
        data.expanded = {
            providerId: shelf.providerId,
            providerName: shelf.providerName,
            providerLogoUrl: safeProviderLogoUrl(shelf.providerLogoUrl),
            titles: (initialTitles || []).slice(0, 100),
            loading: true,
            error: false
        };
        focusIndex = 0;
        render();
        focusMatching('[data-action="subscription-expanded-back"]');
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

    function expandSubscriptionService(providerId) {
        var shelf = findSubscriptionShelf(providerId);
        if (!shelf) { return; }
        startExpandedSubscription(shelf, shelf.titles || [], false);
    }

    /* Abre o catálogo completo diretamente do atalho visual de Filmes/Séries. */
    function openCatalogueProviderShortcut(providerId) {
        var contentType = currentCatalogueType();
        var directoryState = catalogueProviderDirectory(contentType);
        var provider = null;
        var filter = contentType === 'SERIES' ? 'SERIES' : 'MOVIES';
        if (!directoryState || (contentType !== 'MOVIE' && contentType !== 'SERIES')) { return; }
        directoryState.rows.some(function (candidate) {
            if (String(candidate.id) !== String(providerId || '')) { return false; }
            provider = candidate;
            return true;
        });
        if (!provider) { return; }
        state.section = 'SUBSCRIPTIONS';
        state.screen = 'SHELL';
        state.preferences.section = state.section;
        savePreferences();
        state.screenData = {
            kind: 'subscriptions', filter: filter,
            region: activeTmdbRegion(),
            loading: false, shelves: [], selected: null, directShortcut: true
        };
        startExpandedSubscription({
            providerId: provider.id,
            providerName: provider.name,
            providerLogoUrl: provider.logoUrl
        }, [], true);
    }

    function closeExpandedSubscription() {
        var visual = state.screenData && state.screenData.expandedVisual;
        var directShortcut = Boolean(state.screenData && state.screenData.directShortcut);
        var filter = state.screenData && state.screenData.filter;
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); subscriptionRequest = null; }
        if (!state.screenData) { return; }
        state.screenData.expanded = null;
        state.screenData.expandedVisual = null;
        state.screenData.directShortcut = false;
        focusIndex = 0;
        if (directShortcut) {
            loadSubscriptions(filter || 'MOVIES');
            return;
        }
        render();
        restoreSubscriptionExpandedVisual(visual);
    }

    /*
      O indice de nomes do catalogo, montado uma vez e reaproveitado.

      Cada titulo aberto em Assinaturas varria as 42.000 linhas para responder
      uma pergunta so — "este filme esta na minha lista?". As outras
      plataformas saem junto com a ficha, e a do proprio aplicativo chegava
      cinco a dez segundos depois, o que fazia parecer que ela nao existia.

      Guarda so o que a comparacao usa — nome dobrado, ano, id e tipo — e nao
      os itens: o objeto inteiro de 42.000 linhas nao cabe confortavelmente na
      memoria de uma TV, e nada mais aqui precisa dele.

      A chave junta nome e tipo porque um filme e uma serie podem ter o mesmo
      nome, e sao respostas diferentes.
    */
    function subscriptionIndexKey(name, isSeries) {
        return (isSeries ? 's:' : 'm:') + titleComparable(name);
    }

    function buildSubscriptionIndex(done, failed) {
        BuroStorage.fold('items', function (index, item) {
            var key;
            if (!item || (item.contentType !== 'MOVIE' && item.contentType !== 'SERIES')) { return index; }
            key = subscriptionIndexKey(item.name, item.contentType === 'SERIES');
            /* O primeiro vence: a lista costuma repetir o mesmo titulo em
               varias qualidades, e qualquer um deles abre a mesma ficha. */
            if (!index[key]) {
                index[key] = { id: item.id, year: Number(item.year) || null };
            }
            return index;
        }, {}, function (index) {
            subscriptionIndex = index;
            subscriptionIndexAt = Date.now();
            done(index);
        }, failed);
    }

    function forgetSubscriptionIndex() { subscriptionIndex = null; subscriptionIndexAt = 0; }

    /*
      Acha o titulo na biblioteca, pelo indice.

      O ano so descarta quando os dois lados o tem: uma lista que nao informa
      ano nao deve perder o casamento por isso, e era assim que a varredura
      antiga ja se comportava.
    */
    function matchSubscriptionLocal(title) {
        function answer(index) {
            var hit = index[subscriptionIndexKey(title.title, title.isSeries)];
            if (state.section !== 'SUBSCRIPTIONS' || !state.screenData || !state.screenData.selected ||
                    subscriptionTitleKey(state.screenData.selected) !== subscriptionTitleKey(title)) { return; }
            if (hit && title.year && hit.year && Number(title.year) !== hit.year) { hit = null; }
            state.screenData.selection = state.screenData.selection || { offers: [], unknown: true };
            /* O indice guarda o id; a ficha e aberta a partir dele, lendo o
               item do banco na hora. */
            state.screenData.selection.localItem = hit ? { id: hit.id } : null;
            render();
        }
        if (subscriptionIndex && Date.now() - subscriptionIndexAt < HOME_CACHE_TRUST_MILLIS) {
            answer(subscriptionIndex);
            return;
        }
        buildSubscriptionIndex(answer, function () {});
    }

    function subscriptionVisualForTitle(title, originElement) {
        var key = subscriptionTitleKey(title);
        var content = root && root.querySelector ? root.querySelector('.content.scrollable') : null;
        var element = originElement && originElement.getAttribute &&
            originElement.getAttribute('data-action') === 'subscription-title' &&
            originElement.getAttribute('data-key') === key ? originElement : null;
        var row = null;
        var section = null;
        if (!element && root && root.querySelectorAll) {
            Array.prototype.some.call(root.querySelectorAll('[data-action="subscription-title"]'), function (candidate) {
                if (candidate.getAttribute('data-key') !== key) { return false; }
                element = candidate;
                return true;
            });
        }
        row = element && element.closest ? element.closest('.subscription-row') : null;
        section = row && row.parentNode;
        return {
            key: key,
            providerId: section && section.getAttribute ? section.getAttribute('data-provider') || '' : '',
            contentScrollTop: content ? Number(content.scrollTop) || 0 : 0,
            rowScrollLeft: row ? Number(row.scrollLeft) || 0 : 0
        };
    }

    function restoreSubscriptionTitleVisual(visual) {
        var content = root && root.querySelector ? root.querySelector('.content.scrollable') : null;
        var element = null;
        var row = null;
        var index = -1;
        if (!visual || !root || !root.querySelectorAll) { return; }
        Array.prototype.some.call(root.querySelectorAll('[data-action="subscription-title"]'), function (candidate) {
            if (candidate.getAttribute('data-key') !== visual.key) { return false; }
            var candidateRow = candidate.closest ? candidate.closest('.subscription-row') : null;
            var candidateSection = candidateRow && candidateRow.parentNode;
            if (visual.providerId && (!candidateSection ||
                    candidateSection.getAttribute('data-provider') !== visual.providerId)) { return false; }
            element = candidate;
            return true;
        });
        if (!element) { return; }
        if (content) { content.scrollTop = Math.max(0, Number(visual.contentScrollTop) || 0); }
        row = element.closest ? element.closest('.subscription-row') : null;
        if (row) { row.scrollLeft = Math.max(0, Number(visual.rowScrollLeft) || 0); }
        index = focusables.indexOf(element);
        if (index >= 0) { focusIndex = index; applyFocus(); }
    }

    function subscriptionExpandedVisual(providerId) {
        var content = root && root.querySelector ? root.querySelector('.content.scrollable') : null;
        var element = null;
        var row = null;
        if (root && root.querySelectorAll) {
            Array.prototype.some.call(root.querySelectorAll('.subscription-row [data-action="subscription-expand"]'), function (candidate) {
                if (candidate.getAttribute('data-provider') !== String(providerId || '')) { return false; }
                element = candidate;
                return true;
            });
        }
        row = element && element.closest ? element.closest('.subscription-row') : null;
        return {
            providerId: String(providerId || ''),
            contentScrollTop: content ? Number(content.scrollTop) || 0 : 0,
            rowScrollLeft: row ? Number(row.scrollLeft) || 0 : 0
        };
    }

    /*
      A busca e restrita a fileira de proposito.

      "Ver mais" existe em dois lugares — no cabecalho e no fim da fileira —
      com o mesmo data-action e o mesmo data-provider. Sem a restricao, esta
      busca encontra o do cabecalho, que nao vive dentro de .subscription-row:
      closest devolve nulo, a posicao horizontal nao e restaurada e o foco
      volta para o botao errado.
    */
    function restoreSubscriptionExpandedVisual(visual) {
        var content = root && root.querySelector ? root.querySelector('.content.scrollable') : null;
        var element = null;
        var row;
        var index;
        if (!visual || !root || !root.querySelectorAll) { return; }
        Array.prototype.some.call(root.querySelectorAll('.subscription-row [data-action="subscription-expand"]'), function (candidate) {
            if (candidate.getAttribute('data-provider') !== visual.providerId) { return false; }
            element = candidate;
            return true;
        });
        if (!element) { return; }
        if (content) { content.scrollTop = Math.max(0, Number(visual.contentScrollTop) || 0); }
        row = element.closest ? element.closest('.subscription-row') : null;
        if (row) { row.scrollLeft = Math.max(0, Number(visual.rowScrollLeft) || 0); }
        index = focusables.indexOf(element);
        if (index >= 0) { focusIndex = index; applyFocus(); }
    }

    function selectSubscriptionTitle(title, selectedReturn, selectedVisual) {
        var data = state.screenData;
        var key = BuroTmdb.keyForProfile(state.activeProfile && state.activeProfile.id);
        if (!data || data.kind !== 'subscriptions') {
            data = { kind: 'subscriptions', filter: title.isSeries ? 'SERIES' : 'MOVIES',
                region: activeTmdbRegion(), shelves: [], loading: false };
            state.screenData = data;
        }
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); }
        data.selectedVisual = selectedVisual || subscriptionVisualForTitle(title);
        data.selected = title;
        data.selectedReturn = selectedReturn || null;
        data.selectionLoading = true;
        data.selection = { details: null, offers: [], unknown: false, localItem: null };
        focusIndex = 0;
        render();
        focusMatching('[data-action="subscription-back"]');
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
        var visual = state.screenData && state.screenData.selectedVisual;
        var restoredIndex;
        if (subscriptionRequest && subscriptionRequest.abort) { subscriptionRequest.abort(); subscriptionRequest = null; }
        if (returned && returned.screen === 'PERSON') {
            state.screen = 'PERSON'; state.screenData = returned.data; render(); return;
        }
        if (returned && returned.screen === 'HOME') {
            state.screen = 'SHELL';
            state.section = 'HOME';
            state.screenData = returned.data;
            render();
            focusables.some(function (element, index) {
                if (element.getAttribute('data-action') === 'home-subscription-title' &&
                        element.getAttribute('data-key') === returned.key) {
                    restoredIndex = index;
                    return true;
                }
                return false;
            });
            if (restoredIndex != null) { focusIndex = restoredIndex; applyFocus(); }
            return;
        }
        if (returned && returned.screen === 'TITLE' && returned.value) {
            similarTitleReturnStack.push(returned.value);
            restoreSimilarTitleReturn();
            return;
        }
        state.screenData.selected = null;
        state.screenData.selection = null;
        state.screenData.selectionLoading = false;
        state.screenData.selectedVisual = null;
        focusIndex = 0;
        render();
        restoreSubscriptionTitleVisual(visual);
    }

    /*
      Abrir o titulo que a linha "Na sua biblioteca" promete.

      `findItemAndSource` le `state.items`, que e a amostra que o boot
      carregou — algumas centenas de linhas de um catalogo de dezenas de
      milhares. Mas quem casou este titulo foi `matchSubscriptionLocal`, que
      varre o **banco inteiro**: o item quase sempre existe no banco e quase
      nunca na amostra.

      O resultado era um cartao que prometia o filme e nao fazia nada ao ser
      acionado, sem mensagem nenhuma — o `return` mudo abaixo.

      Agora, quando a amostra nao tem, o item e lido do banco pelo id antes
      de abrir. E se nem o banco tiver — a linha ficou velha, o catalogo foi
      trocado — a tela diz isso em vez de engolir o toque.
    */
    function openSubscriptionLocal(itemId) {
        var found = findItemAndSource(itemId);
        subscriptionReturnData = state.screenData;
        if (found.item) { openSubscriptionLocalItem(found.item); return; }
        BuroStorage.get('items', itemId, function (item) {
            if (!item) { showToast(t('subscriptionsLocalGone'), true); return; }
            /* Entra no estado para que a ficha encontre o mesmo objeto que o
               resto do aplicativo usa. */
            mergeItems([item]);
            openSubscriptionLocalItem(item);
        }, function () {
            showToast(t('subscriptionsLocalGone'), true);
        });
    }

    function openSubscriptionLocalItem(item) {
        if (item.contentType === 'SERIES') { openSeriesById(item.id, 'SUBSCRIPTIONS'); }
        else { openMovieDetails(item.id, 'SUBSCRIPTIONS'); }
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
            error: t('trailerUnavailable'), hint: t('trailerHint'),
            fallbackHint: t('trailerFallbackHint')
        })) { showToast(t('trailerUnavailable'), true); }
    }

    /*
      Abrir um credito da filmografia: o catalogo primeiro, Assinaturas depois.

      Antes ia direto para Assinaturas. Numa TV sem chave TMDb isso deixava a
      pessoa numa tela vazia; com chave, mandava para "onde assistir" um titulo
      que muitas vezes esta na propria lista dela — e a ficha, com o botao de
      assistir, e o que ela queria.

      O aplicativo do Windows resolve isso com openCredit, que devolve um
      destino: PLAYLIST_ITEM quando o titulo esta no catalogo, SUBSCRIPTIONS
      quando nao esta. Esta funcao faz a mesma pergunta.

      A busca e por nome e ano, a mesma comparacao de matchSubscriptionLocal.
      Ela roda sobre o banco e nao sobre state.items, que e so a amostra do
      boot: procurar ali acharia quase nada num catalogo de dezenas de milhares.
    */
    function openPersonCredit(element) {
        var personData = state.screenData;
        var title = {
            tmdbId: Number(element.getAttribute('data-id')), isSeries: element.getAttribute('data-series') === 'true',
            title: element.getAttribute('data-title'), year: Number(element.getAttribute('data-year')) || null,
            posterUrl: null
        };
        var wantedType = title.isSeries ? 'SERIES' : 'MOVIE';
        var wantedName = titleComparable(title.title);

        function fallBackToSubscriptions() {
            state.screen = 'SHELL'; state.section = 'SUBSCRIPTIONS';
            state.screenData = { kind: 'subscriptions', filter: title.isSeries ? 'SERIES' : 'MOVIES',
                region: activeTmdbRegion(), shelves: [], loading: false };
            selectSubscriptionTitle(title, { screen: 'PERSON', data: personData });
        }

        /* O aviso de procura fica na propria pagina da pessoa: trocar de tela
           antes de saber o destino faria a volta piscar se a busca falhasse. */
        state.screenData = state.screenData || {};
        state.screenData.creditOpeningId = title.tmdbId;
        render();

        BuroStorage.fold('items', function (match, item) {
            if (match || !item || item.contentType !== wantedType) { return match; }
            if (titleComparable(item.name) !== wantedName) { return null; }
            if (title.year && item.year && Number(title.year) !== Number(item.year)) { return null; }
            return item;
        }, null, function (match) {
            /* A pessoa pode ter saido enquanto a leitura corria. */
            if (state.screen !== 'PERSON' || !state.screenData ||
                    state.screenData.creditOpeningId !== title.tmdbId) { return; }
            state.screenData.creditOpeningId = null;
            if (match) { openPersonLocal(match.id); return; }
            fallBackToSubscriptions();
        }, function () {
            if (state.screen !== 'PERSON' || !state.screenData ||
                    state.screenData.creditOpeningId !== title.tmdbId) { return; }
            state.screenData.creditOpeningId = null;
            /* A leitura falhou, mas o destino de reserva continua valido. */
            fallBackToSubscriptions();
        });
    }

    function captureSimilarTitleReturn(key) {
        var content = root && root.querySelector ? root.querySelector('.content.scrollable') : null;
        var row = root && root.querySelector ? root.querySelector('.similar-title-row') : null;
        return {
            section: state.section,
            data: state.screenData,
            key: key,
            contentScrollTop: content ? Number(content.scrollTop) || 0 : 0,
            rowScrollLeft: row ? Number(row.scrollLeft) || 0 : 0
        };
    }

    function restoreSimilarTitleReturn() {
        var returned = similarTitleReturnStack.pop();
        var content;
        var row;
        var target = null;
        var index = -1;
        if (!returned || !returned.data) { return false; }
        returned.data.similarOpeningKey = null;
        state.screen = 'SHELL';
        state.section = returned.section;
        state.screenData = returned.data;
        focusIndex = 0;
        render();
        content = root.querySelector('.content.scrollable');
        row = root.querySelector('.similar-title-row');
        if (content) { content.scrollTop = Math.max(0, Number(returned.contentScrollTop) || 0); }
        if (row) { row.scrollLeft = Math.max(0, Number(returned.rowScrollLeft) || 0); }
        Array.prototype.some.call(root.querySelectorAll('[data-action="similar-title"]'), function (candidate) {
            if (candidate.getAttribute('data-key') !== returned.key) { return false; }
            target = candidate;
            return true;
        });
        index = target ? focusables.indexOf(target) : -1;
        if (index >= 0) { focusIndex = index; applyFocus(); }
        return true;
    }

    /*
      Algumas versoes do WRT disparam o evento quente um instante antes de
      getRequestedAppControl() refletir o pedido novo. Repetimos somente essa
      leitura local por no maximo um segundo; nada e persistido nem registrado.
    */
    function receiveRequestedAppControlEvent() {
        var receiveId = ++appControlReceiveId;
        var attempts = 0;
        if (appControlReceiveTimer) {
            window.clearTimeout(appControlReceiveTimer);
            appControlReceiveTimer = null;
        }
        function attempt() {
            if (receiveId !== appControlReceiveId) { return; }
            attempts += 1;
            if (receiveRequestedAppControl() || attempts >= 9) {
                appControlReceiveTimer = null;
                return;
            }
            appControlReceiveTimer = window.setTimeout(attempt, 125);
        }
        attempt();
    }

    function receiveRequestedAppControlOnResume() {
        if (typeof document.hidden === 'boolean' && document.hidden) { return; }
        receiveRequestedAppControlEvent();
    }

    function findSimilarLocal(title, success, failure) {
        var wantedType = title.isSeries ? 'SERIES' : 'MOVIE';
        var sourceId = state.activeSource && state.activeSource.id;
        BuroStorage.fold('items', function (matches, item) {
            var exactYear;
            if (!item || item.sourceId !== sourceId || item.contentType !== wantedType || !itemVisible(item) ||
                    titleComparable(item.name) !== titleComparable(title.title)) { return matches; }
            exactYear = title.year && item.year && Number(title.year) === Number(item.year);
            if (exactYear && !matches.exact) { matches.exact = item; }
            else if ((!title.year || !item.year) && !matches.loose) { matches.loose = item; }
            return matches;
        }, { exact: null, loose: null }, function (matches) {
            success(matches.exact || matches.loose || null);
        }, failure);
    }

    function openSimilarTitle(key) {
        var data = state.screenData;
        var details = data && data.details;
        var titles = data && data.parent ? displayedSimilarTitles(data.parent, details) : [];
        var title = null;
        var returned;
        var found;
        titles.some(function (candidate) {
            if (similarTitleKey(candidate) !== key) { return false; }
            title = candidate;
            return true;
        });
        if (!title || data.similarOpeningKey) { return; }
        returned = captureSimilarTitleReturn(key);
        data.similarOpeningKey = key;
        render();
        if (title.localId) {
            found = findItemAndSource(title.localId);
            if (!found.item || found.item.sourceId !== (state.activeSource && state.activeSource.id) ||
                    !itemVisible(found.item)) {
                data.similarOpeningKey = null;
                render();
                return;
            }
            data.similarOpeningKey = null;
            similarTitleReturnStack.push(returned);
            if (found.item.contentType === 'SERIES') { openSeriesById(found.item.id, 'SIMILAR'); }
            else { openMovieDetails(found.item.id, 'SIMILAR'); }
            return;
        }
        findSimilarLocal(title, function (local) {
            if (state.screenData !== data || data.similarOpeningKey !== key) { return; }
            data.similarOpeningKey = null;
            if (local) {
                similarTitleReturnStack.push(returned);
                if (local.contentType === 'SERIES') { openSeriesById(local.id, 'SIMILAR'); }
                else { openMovieDetails(local.id, 'SIMILAR'); }
                return;
            }
            state.screen = 'SHELL';
            state.section = 'SUBSCRIPTIONS';
            state.screenData = { kind: 'subscriptions', filter: title.isSeries ? 'SERIES' : 'MOVIES',
                region: activeTmdbRegion(), shelves: [], loading: false };
            selectSubscriptionTitle(title, { screen: 'TITLE', value: returned });
        }, function () {
            if (state.screenData !== data) { return; }
            data.similarOpeningKey = null;
            render();
            showToast(t('couldNotLoad'), true);
        });
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
        reportArtworkCoverage();
    }

    /*
      Quantos titulos do banco tem capa gravada.

      Existe porque "as capas nao aparecem" e um sintoma com varias causas
      possiveis — provedor sem arte, peneira recusando, linha antiga por
      regravar — e sem contar nao da para saber qual. Uma linha no log responde
      em vez de adivinhar. Nao imprime URL nenhuma: a contagem basta, e uma URL
      de provedor no log e exatamente o que nao pode vazar.
    */
    function reportArtworkCoverage() {
        if (!window.console || typeof window.console.info !== 'function') { return; }
        BuroStorage.fold('items', function (acc, item) {
            acc.total += 1;
            if (item && item.logoUrl) { acc.withArt += 1; }
            else if (item && item.contentType === 'MOVIE') { acc.moviesWithout += 1; }
            return acc;
        }, { total: 0, withArt: 0, moviesWithout: 0 }, function (result) {
            window.console.info('IPTVBURO_ARTWORK total=' + result.total +
                ' comCapa=' + result.withArt + ' filmesSemCapa=' + result.moviesWithout);
        }, function () {});
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
        else if (state.screen === 'HISTORY_CLEAR_CONFIRM') { renderHistoryClearConfirm(); }
        else if (state.screen === 'CONTINUE_CLEAR_CONFIRM') { renderContinueClearConfirm(); }
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
        else if (state.screen === 'PAIRING') { renderPairing(); }
        else if (state.screen === 'DEVICE_CODE') { renderDeviceCode(); }
        else if (state.screen === 'DIAGNOSTICS') { renderDiagnostics(); }
        else if (state.screen === 'SEND_TO_SCREEN') { renderSendToScreen(); }
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
        bindLicenceKeyInput();
        reportRuntimeReady();
        if (pendingSharedTitle && sharedTitleNeedsResolution && !sharedTitleNoticeVisible) {
            resolvePendingSharedTitle();
        }
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

    /*
      `scrollIntoView({ inline: 'nearest' })` só recebeu o objeto de opções em
      engines posteriores às primeiras TVs suportadas. Nessas versões antigas
      a chamada pode alinhar verticalmente e deixar o card focado escondido no
      `overflow: hidden` da prateleira. O deslocamento horizontal explícito
      mantém o mesmo contrato das LazyRows Android/Windows sem depender dessa
      parte moderna da API.
    */
    function horizontalContainerFor(element) {
        var container = element && element.parentNode;
        while (container && container !== root) {
            if (container.classList && (container.classList.contains('nav-list') ||
                    container.classList.contains('card-row') ||
                    container.classList.contains('subscription-row') ||
                    container.classList.contains('similar-title-row') ||
                    container.classList.contains('cast-row') ||
                    container.classList.contains('demo-card-row'))) { return container; }
            container = container.parentNode;
        }
        return null;
    }

    function revealFocusedRowItem(element) {
        var container = horizontalContainerFor(element);
        var containerRect;
        var elementRect;
        var scrollLeft;
        var inset = 12;
        if (!container || !container.getBoundingClientRect ||
                !element || !element.getBoundingClientRect) { return; }
        containerRect = container.getBoundingClientRect();
        elementRect = element.getBoundingClientRect();
        if (!containerRect || !elementRect || !(containerRect.right > containerRect.left)) { return; }
        scrollLeft = Number(container.scrollLeft) || 0;
        if (elementRect.left < containerRect.left + inset) {
            container.scrollLeft = Math.max(0, scrollLeft - (containerRect.left + inset - elementRect.left));
        } else if (elementRect.right > containerRect.right - inset) {
            container.scrollLeft = scrollLeft + elementRect.right - (containerRect.right - inset);
        }
    }

    /*
      A caixa que rola verticalmente em volta de um elemento focado.

      A janela do seletor vem primeiro por ser a mais proxima: ela tem
      `max-height` e `overflow-y: auto`, e sem reconhece-la o foco descia para
      uma opcao fora da area visivel sem nada rolar. Da TV isso se via como o
      D-pad simplesmente parando — "quando eu vou com a seta do controle, era
      pra mim conseguir ir pra baixo, mas eu nao consigo".

      Ela nao pode ser alcancada pela regra de baixo: e `position: absolute`, e
      o `.content.scrollable` que a envolve rolaria a pagina inteira em vez da
      lista.
    */
    function verticalContentFor(element) {
        var container = element && element.parentNode;
        while (container && container !== root) {
            if (container.classList && container.classList.contains('catalogue-options')) { return container; }
            if (container.classList && container.classList.contains('content') &&
                    container.classList.contains('scrollable')) { return container; }
            container = container.parentNode;
        }
        return null;
    }

    /*
      Rola somente a área de conteúdo do shell.

      `scrollIntoView` também pode deslocar um ancestral com `overflow: hidden`.
      No Chromium e em alguns Web Engines Tizen isso fazia `.main-pane` subir e
      levava Ribbon/topbar para fora do quadro ao abrir detalhes. Ajustar o
      `scrollTop` conhecido preserva o chrome fixo da TV.
    */
    function revealFocusedContentItem(element, container) {
        var containerRect;
        var elementRect;
        var scrollTop;
        var inset = 12;
        if (!container || !container.getBoundingClientRect || !element ||
                !element.getBoundingClientRect) { return; }
        containerRect = container.getBoundingClientRect();
        elementRect = element.getBoundingClientRect();
        if (!containerRect || !elementRect || !(containerRect.bottom > containerRect.top)) { return; }
        scrollTop = Number(container.scrollTop) || 0;
        if (elementRect.top < containerRect.top + inset) {
            container.scrollTop = Math.max(0, scrollTop - (containerRect.top + inset - elementRect.top));
        } else if (elementRect.bottom > containerRect.bottom - inset) {
            container.scrollTop = scrollTop + elementRect.bottom - (containerRect.bottom - inset);
        }
    }

    /*
      A previa do canal focado.

      E o que as listas de IPTV fazem: a lista de um lado, o canal em foco
      tocando pequeno do outro, trocando conforme o foco anda. Sem isso a
      unica forma de saber o que esta passando e abrir o canal inteiro e
      voltar.

      **Desligada por padrao.** Cada abertura e uma sessao no provedor, e ha
      provedores que limitam conexoes e derrubam a conta por excesso. Quem
      sabe que a sua aguenta liga em Configuracoes; quem nao sabe nao e
      exposto ao risco sem escolher.
    */
    function previewEnabled() {
        return state.preferences.livePreview === true;
    }

    function cancelPreview() {
        if (previewTimer) { window.clearTimeout(previewTimer); previewTimer = null; }
        if (previewItemId) {
            previewItemId = null;
            BuroPlayer.stop();
            if (previewFrame) { previewFrame.hidden = true; }
        }
        /* O alvo pendente sai junto: sem isto, sair da lista e voltar ao mesmo
           canal nao reabriria a previa, porque o alvo continuaria "igual". */
        previewPendingId = null;
    }

    /*
      Chamada a cada movimento do foco. Cancela o que estava a caminho e
      recomeca a contagem: e o cancelamento que impede a enxurrada de
      aberturas quando alguem atravessa a lista.
    */
    function schedulePreview(element) {
        var itemId = element && element.getAttribute ? element.getAttribute('data-id') : null;
        var isChannel = element && element.getAttribute &&
            element.getAttribute('data-action') === 'live-details';
        /*
          So quando o alvo muda de verdade.

          `applyFocus` roda a cada redesenho, e nao apenas quando o foco anda —
          uma prateleira que recebe capas redesenha varias vezes por segundo.
          Agendar ali sem comparar criava um `setTimeout` por redesenho: medi 320
          callbacks pendentes contra um limite de 12 em
          `chromium-visual.test.js`, que foi quem pegou.

          Comparar com o alvo pendente resolve na raiz: um redesenho sobre o
          mesmo canal nao mexe no que ja esta contando.
        */
        /*
          Um redesenho sobre o mesmo canal nao mexe no que ja esta contando —
          mas so enquanto houver algo contando ou tocando. Sem a segunda metade,
          um alvo marcado por uma tentativa que **nao** comecou (a preferencia
          estava desligada, o player estava aberto) bloqueava a tentativa
          seguinte: ligar a previa nas Configuracoes so faria efeito depois de
          mover o foco para outro canal e voltar.
        */
        if (itemId && itemId === previewPendingId && (previewTimer || previewItemId)) { return; }
        /* Cancelar primeiro, marcar depois: `cancelPreview` limpa o alvo
           pendente, e faze-lo na outra ordem apagaria a marca recem-posta e a
           guarda acima nunca valeria. */
        cancelPreview();
        previewPendingId = isChannel ? itemId : null;
        if (!previewEnabled() || !isChannel || !itemId) { return; }
        /* Nunca por cima de uma reproducao de verdade: quem esta assistindo
           nao quer o audio de outro canal por baixo. */
        if (document.body.classList.contains('playing')) { return; }
        previewTimer = window.setTimeout(function () {
            previewTimer = null;
            startPreview(itemId);
        }, PREVIEW_DELAY_MILLIS);
    }

    function startPreview(itemId) {
        var found = findItemAndSource(itemId);
        var secret;
        if (!found.item || !found.source || found.source.type !== 'XTREAM') { return; }
        if (document.body.classList.contains('playing')) { return; }
        try { secret = BuroStorage.secureGet(found.source.id); }
        catch (ignoredSecret) { return; }
        previewItemId = itemId;
        if (previewFrame) { previewFrame.hidden = false; }
        /* A URL e produzida aqui e some quando a chamada retorna, como na
           reproducao normal — resolucao tardia, sem nada guardado. */
        try { BuroPlayer.play(BuroXtream.resolvePlayback(secret, found.item.locator), 0); }
        catch (ignoredPlay) { cancelPreview(); }
    }

    function applyFocus() {
        focusables.forEach(function (element, index) {
            var verticalContent;
            element.classList.toggle('focused', index === focusIndex);
            if (index === focusIndex) {
                /* O foco mudou: a previa antiga morre e a nova comeca a
                   contar. */
                schedulePreview(element);
                element.setAttribute('tabindex', '0');
                verticalContent = verticalContentFor(element);
                if (verticalContent) {
                    revealFocusedContentItem(element, verticalContent);
                } else if (element.scrollIntoView) {
                    try { element.scrollIntoView({ block: 'nearest', inline: 'nearest' }); }
                    catch (ignoredScrollOptions) {
                        try { element.scrollIntoView(false); } catch (ignoredScroll) {}
                    }
                }
                revealFocusedRowItem(element);
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

    /*
      Os alvos permitidos quando ha uma lista de seletor aberta: as opcoes dela
      e o chip que a abriu. Nulo quando nao ha lista, e ai o D-pad percorre a
      tela inteira como sempre.
    */
    function openPickerCandidates() {
        var slot = root.querySelector ? root.querySelector('.picker-slot.open') : null;
        if (!slot || !slot.querySelectorAll) { return null; }
        return Array.prototype.slice.call(slot.querySelectorAll('.focusable:not([disabled])'));
    }

    function moveDirectional(keyCode) {
        var K = BuroKeys.CODES;
        var current = focusables[focusIndex];
        var candidates;
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
        /*
          Com uma lista de seletor aberta, o D-pad fica dentro dela.

          A pontuacao abaixo pesa a distancia horizontal por 2,4, e a lista abre
          ancorada no chip: descendo de um chip da direita, uma opcao da lista
          perdia para qualquer elemento mais alinhado ao centro e o foco saltava
          para fora. De quem esta no controle isso se via como nao dar para
          escolher o ano, a nota, o servico nem o genero.

          O chip que abriu continua no conjunto, para o UP fechar voltando nele.
        */
        candidates = openPickerCandidates();
        focusables.forEach(function (candidate, index) {
            var rect;
            var x;
            var y;
            var primary;
            var secondary;
            var valid;
            var score;
            if (index === focusIndex) { return; }
            if (candidates && candidates.indexOf(candidate) < 0) { return; }
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
            language: true, 'subscription-filter': true, 'subscription-region': true, 'settings-region': true
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

    function clearHistorySearchDebounce() {
        if (!historySearchTimer) { return; }
        window.clearTimeout(historySearchTimer);
        historySearchTimer = null;
    }

    /* A fila tem no máximo 200 entradas persistidas. Filtrar localmente é
       barato, mas um pequeno debounce evita reconstruir o DOM a cada evento
       intermediário do teclado virtual Samsung. A consulta nunca sai da TV. */
    function bindDownloadSearchInput() {
        if (!root) { return; }
        /* O container permanece montado enquanto o campo é recriado a cada
           render. Delegar o evento nele evita perder a segunda digitação se
           artwork, progresso ou outra atualização substituir o input durante
           os 200 ms de debounce. */
        root.oninput = function (event) {
            var input = event && (event.target || event.srcElement);
            var catalogueType;
            var scope;
            if (!input || state.screen !== 'SHELL') { return; }
            if (input.id === 'catalogue-query' && !state.screenData &&
                    ['LIVE', 'MOVIES', 'SERIES'].indexOf(state.section) >= 0) {
                catalogueType = currentCatalogueType();
                scope = catalogueScope(catalogueType);
                scope.query = String(input.value || '').substring(0, 80);
                clearSearchDebounce();
                searchDebounceTimer = window.setTimeout(function () {
                    searchDebounceTimer = null;
                    if (state.screen !== 'SHELL' || state.screenData ||
                            currentCatalogueType() !== catalogueType) { return; }
                    /* Invalida tambem uma leitura do filtro anterior que ainda
                       esteja percorrendo o IndexedDB. */
                    scope.rows = undefined;
                    scope.total = null;
                    scope.hasMore = false;
                    scope.loading = false;
                    scope.openPicker = null;
                    render();
                }, SEARCH_DEBOUNCE_MILLIS);
                return;
            }
            if (input.id === 'history-query' && state.section === 'HISTORY') {
                historyQuery = String(input.value || '').substring(0, 80);
                clearHistorySearchDebounce();
                historySearchTimer = window.setTimeout(function () {
                    historySearchTimer = null;
                    if (state.screen !== 'SHELL' || state.section !== 'HISTORY' || !root) { return; }
                    libraryPages.HISTORY = 0;
                    render();
                }, HISTORY_SEARCH_DEBOUNCE_MILLIS);
                return;
            }
            if (input.id !== 'download-query' || state.section !== 'DOWNLOADS') { return; }
            downloadQuery = String(input.value || '').substring(0, 80);
            clearDownloadSearchDebounce();
            downloadSearchTimer = window.setTimeout(function () {
                downloadSearchTimer = null;
                if (state.screen !== 'SHELL' || state.section !== 'DOWNLOADS' || !root) { return; }
                downloadPage = 0;
                render();
            }, DOWNLOAD_SEARCH_DEBOUNCE_MILLIS);
        };
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

    /*
      O AVPlay fica sobre o shell sem destruí-lo. Mesmo assim, ao voltar, filmes,
      episódios e trilhos precisam refletir o checkpoint que acabou de ser
      gravado. Android/Windows recompõem a UI reativa; aqui fazemos uma única
      recomposição e recolocamos exatamente o scroll e o foco existentes antes
      do overlay, inclusive a posição horizontal da prateleira.
    */
    function capturePlaybackReturnVisual() {
        var focused;
        var row;
        var rows;
        var content;
        if (state.screen !== 'SHELL') { return null; }
        focused = document.activeElement;
        if (!focused || !root.contains(focused)) { focused = focusables[focusIndex] || null; }
        row = horizontalContainerFor(focused);
        rows = Array.prototype.slice.call(root.querySelectorAll(
            '.nav-list, .card-row, .subscription-row, .demo-card-row'));
        content = root.querySelector('.content');
        return {
            action: focused ? focused.getAttribute('data-action') || '' : '',
            id: focused ? focused.getAttribute('data-id') || '' : '',
            contentScrollTop: content ? Number(content.scrollTop) || 0 : 0,
            rowIndex: row ? rows.indexOf(row) : -1,
            rowScrollLeft: row ? Number(row.scrollLeft) || 0 : 0
        };
    }

    function refreshPlaybackReturnVisual(snapshot) {
        var content;
        var rows;
        if (!snapshot || state.screen !== 'SHELL') { return; }
        render();
        content = root.querySelector('.content');
        if (content) { content.scrollTop = snapshot.contentScrollTop; }
        rows = Array.prototype.slice.call(root.querySelectorAll(
            '.nav-list, .card-row, .subscription-row, .demo-card-row'));
        if (snapshot.rowIndex >= 0 && rows[snapshot.rowIndex]) {
            rows[snapshot.rowIndex].scrollLeft = snapshot.rowScrollLeft;
        }
        if (snapshot.action && snapshot.id) { focusActionId(snapshot.action, snapshot.id); }
    }

    /*
      A Home é um modelo composto por cursor, hero e várias prateleiras. Recriá-la
      ao voltar de um detalhe custa outra leitura do IndexedDB e perde exatamente
      o ponto que a pessoa escolheu. Android e Windows conservam o estado das
      listas; na TV guardamos apenas a referência transitória e as posições
      visuais, nunca catálogo ou URL em outro armazenamento.
    */
    function rememberHomeLocalReturn(element) {
        var content;
        var rail = element;
        var row;
        if (state.screen !== 'SHELL' || state.section !== 'HOME' || !state.screenData ||
                state.screenData.kind !== 'home' || !element) { return; }
        while (rail && rail !== root && (!rail.classList || !rail.classList.contains('home-rail'))) {
            rail = rail.parentNode;
        }
        if (!rail || rail === root) { rail = null; }
        row = rail && rail.querySelector ? rail.querySelector('.card-row, .subscription-row') : null;
        content = root.querySelector('.content');
        homeLocalReturnData = {
            data: state.screenData,
            action: element.getAttribute('data-action') || '',
            id: element.getAttribute('data-id') || '',
            contentScrollTop: content ? Number(content.scrollTop) || 0 : 0,
            railKey: rail ? rail.getAttribute('data-home-rail') || '' : '',
            railScrollLeft: row ? Number(row.scrollLeft) || 0 : 0
        };
    }

    function restoreHomeLocalReturn() {
        var returned = homeLocalReturnData;
        var content;
        var row;
        homeLocalReturnData = null;
        if (!returned || !returned.data) { return false; }
        state.screen = 'SHELL';
        state.section = 'HOME';
        state.screenData = returned.data;
        focusIndex = 0;
        render();
        content = root.querySelector('.content');
        if (content) { content.scrollTop = returned.contentScrollTop; }
        if (returned.railKey) {
            Array.prototype.some.call(root.querySelectorAll('.home-rail[data-home-rail]'), function (rail) {
                if (rail.getAttribute('data-home-rail') !== returned.railKey) { return false; }
                row = rail.querySelector('.card-row, .subscription-row');
                if (row) { row.scrollLeft = returned.railScrollLeft; }
                return true;
            });
        }
        focusActionId(returned.action, returned.id);
        return true;
    }

    /*
      Uma imagem remota não deve piscar como um retângulo quebrado enquanto o
      Web Engine decodifica a capa. Android/Windows mantêm um placeholder e
      revelam a arte pronta; aqui o mesmo estado é aplicado depois de cada
      render, inclusive às imagens que já vieram do cache do navegador/USB.

      O estado fica só no DOM. URL, tamanho e resultado do carregamento não são
      persistidos nem enviados a telemetria.
    */
    function bindProgressiveImage(image, hideFrameOnFailure, allowFallback) {
        var frame = image.parentNode;
        var settled = false;

        function loading() {
            image.classList.add('buro-progressive-image');
            image.classList.remove('image-ready');
            if (frame && frame.classList) {
                frame.classList.add('buro-image-frame');
                frame.classList.remove('image-ready');
            }
        }

        function ready() {
            if (settled) { return; }
            settled = true;
            image.classList.add('image-ready');
            if (frame && frame.classList) { frame.classList.add('image-ready'); }
        }

        function failed() {
            var fallback = allowFallback ? safeArtworkUrl(image.getAttribute('data-artwork-fallback')) : null;
            if (fallback) {
                image.removeAttribute('data-artwork-fallback');
                settled = false;
                loading();
                image.src = fallback;
                return;
            }
            settled = true;
            image.classList.remove('image-ready');
            if (frame && frame.classList) { frame.classList.remove('image-ready'); }
            if (hideFrameOnFailure && frame) { frame.style.display = 'none'; }
            else { image.style.display = 'none'; }
        }

        loading();
        image.addEventListener('load', ready);
        image.addEventListener('error', failed);
        /* `load` pode ter ocorrido entre innerHTML e a ligação dos eventos. */
        if (image.complete && Number(image.naturalWidth) > 0) { ready(); }
        else if (image.complete && Number(image.naturalWidth) === 0) { failed(); }
    }

    function bindArtworkErrors() {
        Array.prototype.slice.call(root.querySelectorAll('.media-art img, .hero-art img, .detail-art img, ' +
                '.detail-poster img, .discover-art img, .resume-art img')).forEach(function (image) {
            bindProgressiveImage(image, true, true);
        });
        Array.prototype.slice.call(root.querySelectorAll('.subscription-poster img, .subscription-title-head img, ' +
                '.subscription-backdrop img, .subscription-cast img, .person-page img, .cast-chip img, ' +
                '.similar-title-card img, .profile-photo-choice img, .avatar img, .ribbon-avatar img, ' +
                '.settings-profile-avatar img')).forEach(function (image) {
            bindProgressiveImage(image, false, false);
        });
    }

    function pushScreen(screen, data) {
        state.backStack.push({ screen: state.screen, section: state.section, data: state.screenData });
        state.screen = screen;
        state.screenData = data || null;
        focusIndex = 0;
        render();
        /*
          O foco entra no conteúdo, não na faixa de navegação.

          `focusIndex = 0` sozinho punha o foco no primeiro item da ribbon, que
          vem antes do conteúdo no DOM. Numa tela aberta de propósito — a chave
          do TMDb, o pareamento — isso obrigava a descer às cegas antes de
          alcançar o primeiro controle, e um ENTER apressado saía da tela em vez
          de agir nela. Apareceu no emulador exatamente assim.
        */
        focusFirstInMainPane();
    }

    /*
      O primeiro focável da área de conteúdo.

      `.content` e não `.main-pane`: o sino de avisos mora na barra superior, que
      também está dentro do painel, e abrir a tela da chave do TMDb com o foco no
      sino é quase tão inútil quanto abri-la com o foco na ribbon.
    */
    function focusFirstInMainPane() {
        var content = root.querySelector ? root.querySelector('.content') : null;
        var target = content && content.querySelector ? content.querySelector('.focusable:not([disabled])') : null;
        var index;
        var contentRect;
        var targetRect;
        if (!target || !target.getBoundingClientRect) { return; }
        index = focusables.indexOf(target);
        if (index < 0) { return; }
        /*
          Só se o alvo já estiver à vista.

          `applyFocus` rola para o que recebe o foco. Numa tela cujo primeiro
          focável está no fim de um texto longo — o guia da chave, com quatro
          passos e os botões embaixo — mover o foco para lá rolaria a página e
          esconderia o passo 1, que é justamente o que a pessoa veio ler.
          Nesse caso o foco fica onde estava e a rolagem começa do começo.
        */
        contentRect = content.getBoundingClientRect();
        targetRect = target.getBoundingClientRect();
        if (targetRect.bottom > contentRect.bottom || targetRect.top < contentRect.top) { return; }
        focusIndex = index;
        applyFocus();
    }

    function goBack() {
        /* Sair da busca encerra a escuta: um reconhecimento vivo noutra tela
           devolveria texto para um campo que ja nao existe. */
        if (typeof BuroVoice !== 'undefined' && BuroVoice.isListening()) { BuroVoice.stop(); }
        var previous;
        var focused;
        var ribbonTarget;
        var reminderReturnId;
        if (state.screen === 'LICENCE') { clearLicenceKeyTimer(true); }
        /* Nao sair da confirmacao enquanto a gravacao corre: voltar no meio
           deixaria metade das linhas marcadas e a tela ja renderizada de novo. */
        if ((state.screen === 'HISTORY_CLEAR_CONFIRM' || state.screen === 'CONTINUE_CLEAR_CONFIRM') &&
                progressMutationPending) { return; }
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
            if (tmdbSimilarRequest && tmdbSimilarRequest.abort) {
                tmdbSimilarRequest.abort();
                tmdbSimilarRequest = null;
            }
            if (state.screenData.originSection === 'DISCOVER' && discoverReturnData) {
                state.section = 'DISCOVER';
                state.screenData = discoverReturnData;
                discoverReturnData = null;
                focusIndex = 0;
                render();
                focusMatching('[data-action="discover-details"]');
                return;
            }
            if (state.screenData.originSection === 'SIMILAR' && similarTitleReturnStack.length &&
                    restoreSimilarTitleReturn()) { return; }
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
            if (state.screenData.originSection === 'HOME' && restoreHomeLocalReturn()) { return; }
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
                    if (state.preferences.tmdbRegionsByProfile &&
                            Object.prototype.hasOwnProperty.call(state.preferences.tmdbRegionsByProfile, profileId)) {
                        delete state.preferences.tmdbRegionsByProfile[profileId];
                    }
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
        forgetHomeCache();
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
            BuroXmltv.clear(sourceId);
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

    /*
      A lista que o vendedor configurou no painel, aplicada na abertura.

      Quem vende IPTV vende para gente que nao consegue cadastrar um servidor
      Xtream no controle remoto. O cliente le o codigo do aparelho na tela de
      Licenca e manda por mensagem; quem vendeu preenche no painel; na proxima
      abertura a lista esta aqui.

      Silencioso quando nao ha nada — o caso comum de toda abertura — e
      silencioso tambem quando falha: a pessoa nao pediu isto e uma mensagem de
      erro sobre um provisionamento que ela nem sabe que existe seria ruido. O
      que falhou vai para o painel, onde ha quem possa corrigir.

      So aplica quando nao ha fonte nenhuma. Uma TV que ja tem lista configurada
      — pela propria pessoa ou por um provisionamento anterior — nao pode ter a
      escolha dela trocada por baixo.
    */
    /*
      A lista que quem vendeu enviou, acrescentada as que ja existem.

      Antes isto desistia assim que houvesse qualquer fonte, para nao trocar a
      lista de quem tinha configurado a propria. A intencao estava certa e o
      efeito era outro: uma vez que o cliente tinha lista, quem vendeu nao
      conseguia mais mandar nenhuma — nem a segunda assinatura que ele acabara de
      comprar, nem a substituta de um endereco que caiu.

      Agora ela entra ao lado. O que continua valendo, e e o que importa, e que
      nada do cliente e apagado: a escolha dele permanece na lista de fontes, e
      so a fonte selecionada muda. Uma entrega que apagasse a lista de alguem
      seria pior do que uma que nao chega.
    */
    function applyAssignedSource() {
        BuroLicense.fetchAssignedSource(function (assigned) {
            if (!assigned) { return; }
            /*
              Uma entrega pode nao trazer conexao nenhuma: so uma chave, so um
              nome, para um cliente cuja lista ja funciona. Exigir servidor,
              usuario e senha de novo so para entregar uma chave foi o que quem
              vende relatou. Sem conexao nao ha o que autenticar nem gravar:
              aplica as chaves e confirma, senao o painel fica mostrando pendente
              uma entrega que ja foi aplicada.
            */
            if (!assigned.server || !assigned.username || !assigned.password) {
                applyAssignedKeys(assigned);
                render();
                BuroLicense.confirmAssignedSource(null);
                return;
            }
            connectAssignedXtream(assigned);
        }, function () { /* Sem rede, sem provisionamento: tenta na proxima. */ });
    }

    /*
      Valida antes de gravar, pelo mesmo caminho de quem digita no formulario.

      Autenticar primeiro importa aqui mais do que ali: quem digitou esta olhando
      para a tela e ve o erro, enquanto isto acontece sozinho. Uma credencial
      errada gravada em silencio daria ao cliente um aplicativo que abre e nao
      mostra nada, sem pista do motivo.
    */
    function connectAssignedXtream(assigned) {
        var secret;
        var source;
        try {
            secret = BuroXtream.credentials({
                server: assigned.server, username: assigned.username, password: assigned.password
            });
            source = BuroDomain.createSourceMetadata({ name: assignedSourceName(assigned), type: 'XTREAM' });
        } catch (invalid) {
            BuroLicense.confirmAssignedSource('bad_credentials');
            return;
        }
        BuroXtream.authenticate(secret, function () {
            BuroStorage.secureSave(source.id, secret, function () {
                loadAssignedCategorySets(secret, source, ['LIVE', 'MOVIE', 'SERIES'], [], assigned);
            }, function () { BuroLicense.confirmAssignedSource('store_failed'); });
        }, function (error) {
            /* O painel precisa saber que o endereco nao respondeu: e quem
               vendeu que consegue trocar por outro. */
            BuroLicense.confirmAssignedSource((error && error.code) || 'auth_failed');
        });
    }

    /* O nome da fonte vem do endereco, sem a credencial: e o que a pessoa vera
       na lista de fontes, e nao pode virar um lugar onde a senha aparece. */
    function assignedSourceName(assigned) {
        var host;
        /* O nome que quem vendeu escolheu vale mais do que o endereco: o
           endereco e como o servidor se chama, nao como o cliente comprou. */
        if (assigned.listLabel) { return String(assigned.listLabel).substring(0, 40); }
        host = String(assigned.server || '').replace(/^https?:\/\//i, '').split(/[/:?]/)[0];
        return (host || 'IPTV').substring(0, 40);
    }

    function loadAssignedCategorySets(secret, source, remaining, collected, assigned) {
        var contentType;
        if (!remaining.length) { persistAssignedSource(source, collected, assigned); return; }
        contentType = remaining.shift();
        BuroXtream.loadCategories(secret, contentType, function (categories) {
            categories.forEach(function (category) { category.sourceId = source.id; });
            loadAssignedCategorySets(secret, source, remaining, collected.concat(categories), assigned);
        }, function () {
            /* Um tipo de conteudo que o provedor nao oferece nao invalida os
               outros: segue com o que deu. */
            loadAssignedCategorySets(secret, source, remaining, collected, assigned);
        });
    }

    /*
      Grava e segue, sem trocar a tela.

      Diferente de `persistSource`, que navega para Fontes e mostra um aviso:
      aquilo responde a um gesto de quem estava configurando. Aqui a pessoa esta
      abrindo o aplicativo, e a resposta certa e a lista simplesmente estar la.
    */
    function persistAssignedSource(source, categories, assigned) {
        BuroStorage.replaceSourceCatalogue(source, categories, [], true, function () {
            state.sources.push(source);
            state.categories = state.categories.concat(categories);
            assignSourceToProfile(source);
            state.activeSource = source;
            applyAssignedKeys(assigned);
            /* So agora o servidor pode apagar o que guardou: ate aqui, uma falha
               ainda poderia ser tentada de novo na proxima abertura. */
            BuroLicense.confirmAssignedSource(null);
            startXtreamHydration(source, true);
            render();
        }, function () { BuroLicense.confirmAssignedSource('store_failed'); });
    }

    /*
      As chaves de metadados que vieram no mesmo pacote.

      Pelo mesmo motivo da lista: quem nao consegue cadastrar um servidor tambem
      nao vai criar conta no TMDb. Sao opcionais, e uma chave recusada nao
      impede a lista de funcionar — o cliente fica sem capa, nao sem canal.

      Nunca sobrescreve uma chave que a pessoa ja escolheu: quem configurou a
      propria conta fez uma escolha, e ela vale mais do que a nossa.
    */
    function applyAssignedKeys(assigned) {
        var profileId = state.activeProfile && state.activeProfile.id;
        if (assigned.metadataKey && !BuroTmdb.keyForProfile(profileId)) {
            BuroTmdb.save('shared', profileId, assigned.metadataKey, function () {}, function () {});
        }
        if (assigned.criticsKey && !BuroCritics.configured()) {
            BuroCritics.save(assigned.criticsKey, function () {}, function () {});
        }
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

    /* A lista pode anunciar o XMLTV no cabeçalho. Ele acompanha o seletor
       secreto da fonte e nunca a fotografia persistível do catálogo. */
    function m3uSecretWithGuide(secret, parsed) {
        var next = {};
        Object.keys(secret || {}).forEach(function (key) { next[key] = secret[key]; });
        next.epgUrls = BuroXmltv.safeUrls(parsed && parsed.header && parsed.header.epgUrls);
        return next;
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
                fileSize: Number(descriptor.size) || 0,
                epgUrls: BuroXmltv.safeUrls(snapshot.parsed.header && snapshot.parsed.header.epgUrls)
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
            BuroStorage.secureSave(source.id, m3uSecretWithGuide({ url: url }, snapshot.parsed), function () {
                persistSource(source, snapshot.categories, snapshot.items);
            }, sourceFailed);
        }, sourceFailed);
    }

    function finishSourceRefresh(requestId, source, categories, items, replaceAllItems, parsedEntries, refreshDraft) {
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
            refreshDraft.refreshing = false;
            refreshDraft.refreshError = friendlyError(metadataError);
            render();
            if (refreshDraft === topbarSourceRefresh) { showToast(refreshDraft.refreshError, true); }
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
            refreshDraft.refreshing = false;
            refreshDraft.refreshError = null;
            refreshDraft.refreshSuccess = replaceAllItems;
            render();
            retryPendingSharedTitle();
            if (updated.type === 'XTREAM') {
                BuroHeroEnrichment.clearSource(updated.id);
                startXtreamHydration(updated, true);
                showToast(t('catalogueSyncStarted'), false);
            } else { showToast(t('sourceRefreshed'), false); }
        }, function (error) {
            if (requestId !== sourceRefreshRequestId) { return; }
            refreshDraft.refreshing = false;
            refreshDraft.refreshError = friendlyError(error);
            render();
            if (refreshDraft === topbarSourceRefresh) { showToast(refreshDraft.refreshError, true); }
        });
    }

    /*
      O botao "Atualizar" da barra de cima.

      Pede a lista de novo ao provedor, forcando: sem `force` a fila pula toda
      categoria completada nas ultimas 24 horas e o botao parece nao fazer nada
      — exatamente o que aconteceu quando o usuario mandou atualizar e as capas
      continuaram vazias. Mesma decisao do `refreshCatalog` do Windows.

      Diferente de "Atualizar fonte" em Gerenciar fonte, que reautentica e
      relista as categorias: aqui a fonte ja esta validada e o que se quer e o
      conteudo dela.
    */
    function refreshCatalogueFromTopBar() {
        var source = state.activeSource;
        var status;
        if (!source || source.type !== 'XTREAM') { return; }
        status = catalogueSyncStatus(source);
        if (status && status.state === 'RUNNING') { return; }
        forgetHomeCache();
        catalogueSizeMeasuredAt = 0;
        startXtreamHydration(source, true);
        showToast(t('catalogueSyncStarted'), false);
        render();
    }

    function refreshSource(sourceOverride, fromTopbar) {
        var draft = fromTopbar ? topbarSourceRefresh : sourceManageDraft();
        var source = sourceOverride || state.sources.filter(function (row) { return row.id === draft.sourceId; })[0];
        var secret;
        var requestId;
        var resumeOnFailure = false;
        var sync;
        if (!source || draft.refreshing) { return; }
        draft.sourceId = source.id;
        if (source.type === 'XTREAM') {
            sync = catalogueSyncStatus(source);
            if (sync && sync.state === 'RUNNING') { resumeOnFailure = BuroCatalogueSync.cancel(); }
        }
        try { secret = BuroStorage.secureGet(source.id); }
        catch (error) {
            draft.refreshError = friendlyError(error); render();
            if (fromTopbar) { showToast(draft.refreshError, true); }
            return;
        }
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
            if (fromTopbar) { showToast(draft.refreshError, true); }
            if (resumeOnFailure) { startXtreamHydration(source, false); }
        }
        if (source.type === 'XTREAM') {
            BuroXtream.authenticate(secret, function () {
                fetchXtreamCategorySets(secret, source, ['LIVE', 'MOVIE', 'SERIES'], [], function (categories) {
                    finishSourceRefresh(requestId, source, categories, [], false, null, draft);
                }, failed);
            }, failed);
        } else if (source.type === 'STALKER') {
            withStalkerSession(source, secret, function (session) {
                fetchStalkerCategorySets(secret, session, source, ['LIVE', 'MOVIE', 'SERIES'], [], function (categories) {
                    finishSourceRefresh(requestId, source, categories, [], false, null, draft);
                }, failed);
            }, failed);
        } else if (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') {
            readM3uSource(source, secret, function (text) {
                var snapshot;
                var updatedSecret;
                try { snapshot = m3uSnapshot(source, text); }
                catch (error) { failed(error); return; }
                updatedSecret = m3uSecretWithGuide(secret, snapshot.parsed);
                BuroStorage.secureSave(source.id, updatedSecret, function () {
                    updatedSecret = null;
                    BuroXmltv.clear(source.id);
                    finishSourceRefresh(requestId, source, snapshot.categories, snapshot.items, true,
                        snapshot.parsed.entries, draft);
                }, function (error) { updatedSecret = null; failed(error); });
            }, failed);
        } else { failed(new Error('SOURCE_TYPE_UNAVAILABLE')); }
    }

    function refreshActiveSourceFromTopBar() {
        if (!state.activeSource || state.activeSource.type === 'XTREAM') { return; }
        refreshSource(state.activeSource, true);
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
            /*
              Forcado, e nao a varredura normal.

              A fila pula categoria completada ha menos de 24 horas, entao sem
              `force` quem aperta "Atualizar" nao reprocessa nada e a tela fica
              igual — foi o que aconteceu na TV. O aplicativo do Windows resolveu
              isto do mesmo jeito e deixou a razao escrita em `refreshCatalog`:
              quem apertou o botao quer o que o provedor tem agora.
            */
            startXtreamHydration(source, true);
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

    function showStoredCategoryPage(category, page, values) {
        var data = {
            kind: 'category',
            contentType: category.contentType,
            category: category,
            items: page.rows || [],
            cataloguePage: 0,
            catalogueHasMore: Boolean(page.hasMore),
            catalogueNextCursor: page.nextCursor || null,
            catalogueLocalTotalCount: Math.max((page.rows || []).length, Number(page.totalCount) || 0),
            catalogueTotalCount: Math.max((page.rows || []).length, Number(page.totalCount) || 0),
            catalogueLoadingMore: false
        };
        Object.keys(values || {}).forEach(function (key) { data[key] = values[key]; });
        mergeItems(data.items);
        state.screenData = data;
        focusIndex = 0;
        render();
    }

    function stalkerPagingValues(category, localTotal) {
        var remotePage = Math.max(0, Number(category && category.stalkerLoadedPage) || 0);
        var remoteTotal = Math.max(Number(localTotal) || 0,
            Number(category && category.stalkerTotalItems) || 0);
        return {
            catalogueRemoteType: remotePage > 0 ? 'STALKER' : null,
            catalogueRemotePage: remotePage,
            catalogueRemotePageSize: Math.max(0, Number(category && category.stalkerPageSize) || 0),
            catalogueRemoteTotalCount: remoteTotal,
            catalogueRemoteHasMore: remotePage > 0 && (Number(localTotal) || 0) < remoteTotal,
            catalogueTotalCount: remoteTotal
        };
    }

    function persistStalkerPaging(category, page, success, failure) {
        category.stalkerLoadedPage = Math.max(1, Number(page && page.page) || 1);
        category.stalkerTotalItems = Math.max((page && page.items || []).length,
            Number(page && page.totalItems) || 0);
        category.stalkerPageSize = Math.max((page && page.items || []).length,
            Number(page && page.pageSize) || 0);
        BuroStorage.put('categories', category, success, failure);
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
        BuroStorage.categoryPage(category.sourceId, category.id, null, CATALOGUE_READ_SIZE, function (page) {
            /* Xtream e Stalker paginam no servidor: a categoria chega vazia da
               importação e só busca itens quando é aberta. M3U já vem inteira. */
            var lazy = source && (source.type === 'XTREAM' || source.type === 'STALKER');
            if (!catalogueRequestCurrent(requestId)) { return; }
            if (page.rows.length || !lazy) {
                showStoredCategoryPage(category, page,
                    source && source.type === 'STALKER' ? stalkerPagingValues(category, page.totalCount) : null);
                hydrateCategoryArtwork(category);
                return;
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

    /*
      O andamento da varredura, ao lado do nome da seção.

      Era uma faixa larga no topo da Home, com título, contagem e um botão de
      pausar — espaço demais para algo que começa sozinho na abertura e termina
      sozinho. Aqui é uma barrinha com a porcentagem: quem quiser pausar ou
      retomar continua tendo o painel inteiro em Fontes.

      Sai da tela quando termina, porque um indicador parado em 100% vira
      decoração.
    */
    function catalogueSyncChip() {
        var source = state.activeSource;
        var status = source ? catalogueSyncStatus(source) : null;
        var percent;
        if (!status || !status.total || status.state === 'COMPLETE') { return ''; }
        percent = Math.round(status.completed / status.total * 100);
        return '<span class="sync-chip" role="status" aria-label="' +
            attr(t('catalogueSyncTitle') + ' ' + percent + '%') + '">' +
            '<span class="sync-chip-track"><i style="width:' + percent + '%"></i></span>' +
            '<small>' + escapeHtml(status.completed + '/' + status.total) + '</small></span>';
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
        /*
          A arte fica guardada mesmo com a categoria fechada.

          Antes isto vinha depois do `return` abaixo, então tudo o que a varredura
          de fundo trazia era descartado a menos que o usuário estivesse olhando
          exatamente aquela categoria. O efeito aparecia na Home: o destaque tinha
          pôster, porque tem enriquecimento próprio, e as prateleiras ficavam com
          cartões de texto — que foi o que se viu na TV.

          `rememberArtwork` tem teto e descarte LRU, então guardar o que a
          varredura já trouxe não faz a memória crescer sem limite.
        */
        rememberArtworkMap(artwork);
        /* Catálogo novo: a seleção guardada é de antes destes títulos, e o total
           da barra também. */
        forgetHomeCache();
        catalogueSizeMeasuredAt = 0;
        if (!foreground) {
            /* A Home mostra o catálogo inteiro, então arte nova de qualquer
               categoria muda o que está na tela. Mas a varredura entrega uma
               categoria por vez e são dezenas: redesenhar a cada uma faria a
               Home piscar e roubaria o controle do usuário. Um redesenho
               agrupado, no fim de uma rajada, mostra as capas sem isso. */
            if (state.screen === 'SHELL' && state.section === 'HOME' &&
                    current && current.kind === 'home') { scheduleHomeArtworkRedraw(); }
            return;
        }
        state.items = state.items.filter(function (candidate) {
            return !(candidate.sourceId === category.sourceId && candidate.categoryId === category.id);
        }).concat(items);
        state.screenData = {
            kind: 'category', contentType: category.contentType, category: category,
            items: items, cataloguePage: 0
        };
        focusIndex = 0;
        render();
    }

    /*
      Um redesenho da Home depois que a arte parou de chegar.

      Agrupado em vez de imediato: a varredura entrega categoria por categoria e
      cada uma acrescenta algumas capas. Meio segundo sem novidade é sinal de que
      a rajada acabou, e aí um único redesenho troca os cartões de texto pelas
      capas de uma vez.
    */
    /*
      Busca as capas que faltam para o que a Home está mostrando.

      A varredura de catálogo marca cada categoria como concluída e as pula na
      próxima execução, então uma TV que sincronizou antes desta correção nunca
      voltaria a pedir a arte — a Home ficaria com cartões de texto para sempre.
      Isto cobre essa lacuna e o caso normal ao mesmo tempo.

      Por categoria e não por título: uma chamada ao Xtream devolve o mapa de
      arte da categoria inteira, então doze capas custam uma requisição e não
      doze. As categorias entram na ordem em que aparecem na Home e o número é
      limitado, para a primeira prateleira ganhar capa antes das de baixo.
    */
    function hydrateHomeArtwork(data) {
        var source = state.activeSource;
        var sync;
        var wanted = [];
        var seen = {};
        var byId = {};
        if (!source || source.type !== 'XTREAM' || !data || data.kind !== 'home') { return; }
        /* Com a varredura em curso, ela já está buscando estas categorias e
           entrega a arte pelo mesmo caminho: pedir de novo aqui seria baixar
           duas vezes o que uma requisição só já traz. */
        if (window.BuroCatalogueSync) {
            sync = BuroCatalogueSync.progress(source, categoriesForSource(source.id));
            if (sync && sync.state === 'RUNNING') { return; }
        }
        state.categories.forEach(function (category) { byId[category.id] = category; });
        (data.result ? homeResultItems(data.result) : []).forEach(function (item) {
            var category = byId[item.categoryId];
            if (artworkFor(item) || !category || seen[category.id]) { return; }
            if (artworkRequests[source.id + ':' + category.id]) { return; }
            seen[category.id] = true;
            wanted.push(category);
        });
        wanted.slice(0, HOME_ARTWORK_CATEGORY_LIMIT).forEach(function (category) {
            hydrateCategoryArtwork(category);
        });
    }

    function scheduleHomeArtworkRedraw() {
        if (homeArtworkRedrawTimer) { window.clearTimeout(homeArtworkRedrawTimer); }
        homeArtworkRedrawTimer = window.setTimeout(function () {
            var section = state.section;
            var onShelf = section === 'LIVE' || section === 'MOVIES' || section === 'SERIES';
            homeArtworkRedrawTimer = null;
            if (state.screen !== 'SHELL') { return; }
            if (section === 'HOME') {
                if (!state.screenData || state.screenData.kind !== 'home') { return; }
                render();
                return;
            }
            /* A prateleira usa o mesmo agrupamento: sem screenData, é ela que
               está desenhada. */
            if (onShelf && !state.screenData) { render(); }
        }, HOME_ARTWORK_REDRAW_MILLIS);
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

    function showRefreshedCategoryFromStorage(category, requestId, explicitRefresh, failed, values) {
        BuroStorage.categoryPage(category.sourceId, category.id, null, CATALOGUE_READ_SIZE, function (page) {
            if (!catalogueRequestCurrent(requestId)) { return; }
            showStoredCategoryPage(category, page, values);
            if (explicitRefresh) { showToast(t('categoryRefreshed'), false); }
        }, failed);
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
                });
                state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
                state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
                rememberArtworkMap(artwork);
                BuroCatalogueSync.markCategoryComplete(category.sourceId, category.id, items.length);
                if (explicitRefresh) { BuroHeroEnrichment.clearSource(category.sourceId); }
                if (resumeBackground) { startActiveSourceHydration(false); }
                if (!displayCurrent) { return; }
                showRefreshedCategoryFromStorage(category, requestId, explicitRefresh, failed);
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
                    });
                    state.favorites = state.favorites.filter(function (row) { return !removed[row.itemId]; });
                    state.progress = state.progress.filter(function (row) { return !removed[row.itemId]; });
                    persistStalkerPaging(category, page, function () {
                        if (!displayCurrent || !catalogueRequestCurrent(requestId)) { return; }
                        showRefreshedCategoryFromStorage(category, requestId, explicitRefresh, failed,
                            stalkerPagingValues(category, items.length));
                    }, failed);
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

    function appendCategoryPage(data, page, oldPageCount) {
        var known = {};
        var newFiltered;
        var newPageCount;
        if (state.screenData !== data || data.kind !== 'category') { return; }
        (data.items || []).forEach(function (item) { known[item.id] = true; });
        (page.rows || []).forEach(function (item) {
            if (!known[item.id]) { known[item.id] = true; data.items.push(item); }
        });
        mergeItems(page.rows || []);
        data.catalogueHasMore = Boolean(page.hasMore);
        data.catalogueNextCursor = page.nextCursor || data.catalogueNextCursor || null;
        data.catalogueLocalTotalCount = Math.max(data.items.length, Number(page.totalCount) || 0);
        data.catalogueTotalCount = Math.max(data.catalogueLocalTotalCount,
            Number(data.catalogueRemoteTotalCount) || 0);
        data.catalogueLoadingMore = false;
        newFiltered = BuroDomain.applyCatalogueFilter(data.items || [], data.catalogueFilter);
        newPageCount = Math.max(1, Math.ceil(newFiltered.length / CATALOGUE_PAGE_SIZE));
        if ((Number(data.cataloguePage) || 0) === oldPageCount - 1 && newPageCount > oldPageCount) {
            data.cataloguePage = oldPageCount;
        }
        render();
        if (root.querySelector('[data-action="category-load-more"]')) {
            focusMatching('[data-action="category-load-more"]');
        } else { focusMatching('[data-action="category-page-previous"]'); }
    }

    function failCategoryLoadMore(data, error) {
        if (state.screenData !== data || data.kind !== 'category') { return; }
        data.catalogueLoadingMore = false;
        render();
        focusMatching('[data-action="category-load-more"]');
        showToast(friendlyError(error), true);
    }

    function loadMoreStalkerCategory(data, oldPageCount) {
        var category = data.category;
        var source = null;
        var secret;
        var nextPage = Math.max(1, Number(data.catalogueRemotePage) || 1) + 1;
        var previousLocalTotal = Math.max((data.items || []).length,
            Number(data.catalogueLocalTotalCount) || 0);
        state.sources.forEach(function (candidate) {
            if (candidate.id === category.sourceId) { source = candidate; }
        });
        if (!source || source.type !== 'STALKER') {
            failCategoryLoadMore(data, { code: 'SOURCE_TYPE_UNAVAILABLE' }); return;
        }
        try { secret = BuroStorage.secureGet(category.sourceId); }
        catch (error) { failCategoryLoadMore(data, error); return; }
        withStalkerSession(source, secret, function (session) {
            if (state.screenData !== data || data.kind !== 'category') { return; }
            BuroStalker.loadItems(secret, session, category.sourceId, category.contentType, category, nextPage,
                function (remote) {
                    var items = remote && remote.items || [];
                    if (state.screenData !== data || data.kind !== 'category') { return; }
                    data.catalogueRemotePage = Math.max(nextPage, Number(remote && remote.page) || nextPage);
                    data.catalogueRemotePageSize = Math.max(items.length,
                        Number(remote && remote.pageSize) || 0);
                    data.catalogueRemoteTotalCount = Math.max(data.items.length, items.length,
                        Number(remote && remote.totalItems) || 0);
                    data.catalogueRemoteHasMore = Boolean(remote && remote.hasMore);
                    if (!items.length) {
                        data.catalogueRemoteHasMore = false;
                        persistStalkerPaging(category, remote || {
                            page: data.catalogueRemotePage,
                            totalItems: data.catalogueRemoteTotalCount,
                            pageSize: data.catalogueRemotePageSize,
                            items: []
                        }, function () {
                            data.catalogueLoadingMore = false;
                            render();
                            focusMatching('[data-action="category-page-previous"]');
                        }, function (error) { failCategoryLoadMore(data, error); });
                        return;
                    }
                    BuroStorage.putBatch('items', items, function () {
                        persistStalkerPaging(category, remote, function () {
                            if (state.screenData !== data || data.kind !== 'category') { return; }
                            BuroStorage.categoryPage(category.sourceId, category.id, data.catalogueNextCursor,
                                CATALOGUE_READ_SIZE, function (page) {
                                    var localTotal = Math.max((page.rows || []).length,
                                        Number(page.totalCount) || 0);
                                    if (state.screenData !== data || data.kind !== 'category') { return; }
                                    /* A portal that repeats only ids from the previous page must
                                       not leave an endless Load more loop on the TV. */
                                    data.catalogueRemoteHasMore = Boolean(remote.hasMore) &&
                                        localTotal > previousLocalTotal;
                                    appendCategoryPage(data, page, oldPageCount);
                                }, function (error) { failCategoryLoadMore(data, error); });
                        }, function (error) { failCategoryLoadMore(data, error); });
                    }, function (error) { failCategoryLoadMore(data, error); });
                }, function (error) { failCategoryLoadMore(data, error); });
        }, function (error) { failCategoryLoadMore(data, error); });
    }

    function loadMoreCategory() {
        var data = state.screenData;
        var category;
        var oldFiltered;
        var oldPageCount;
        if (!data || data.kind !== 'category' || data.catalogueLoadingMore ||
                (!data.catalogueHasMore && !data.catalogueRemoteHasMore)) { return; }
        category = data.category;
        oldFiltered = BuroDomain.applyCatalogueFilter(data.items || [], data.catalogueFilter);
        oldPageCount = Math.max(1, Math.ceil(oldFiltered.length / CATALOGUE_PAGE_SIZE));
        data.catalogueLoadingMore = true;
        render();
        if (!data.catalogueHasMore && data.catalogueRemoteType === 'STALKER' && data.catalogueRemoteHasMore) {
            loadMoreStalkerCategory(data, oldPageCount);
            return;
        }
        BuroStorage.categoryPage(category.sourceId, category.id, data.catalogueNextCursor,
            CATALOGUE_READ_SIZE, function (page) {
                appendCategoryPage(data, page, oldPageCount);
            }, function (error) { failCategoryLoadMore(data, error); });
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
        clearHistorySearchDebounce();
        historyQuery = '';
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

    /*
      Windows oferece uma ação principal no topo da série: o primeiro episódio
      com retomada vence; sem retomada, vence o primeiro episódio da lista.
      A escolha usa a mesma ResumeDecision do player, para o texto e a posição
      inicial nunca divergirem. Itens concluídos não sequestram o botão.
    */
    function seriesPrimaryEpisode(episodeRows) {
        var first = null;
        var resumable = null;
        (episodeRows || []).some(function (episode) {
            var progress;
            var decision;
            if (!episode || episode.contentType !== 'EPISODE') { return false; }
            if (!first) { first = episode; }
            progress = playbackProgress(episode.id);
            decision = BuroDomain.resumeDecision(progress && progress.entry, true);
            if (decision.kind === 'resume') {
                resumable = { item: episode, decision: decision };
                return true;
            }
            return false;
        });
        if (resumable) { return resumable; }
        return first ? { item: first, decision: { kind: 'start', positionMs: 0 } } : null;
    }

    function seriesPrimaryLabel(target) {
        var locator = target && target.item && target.item.locator || {};
        var season = Number(locator.season) > 0 ? String(Number(locator.season)) : '—';
        var episode = Number(locator.episode) > 0 ? String(Number(locator.episode)) : '—';
        return t(target && target.decision && target.decision.kind === 'resume' ?
            'seriesContinueEpisode' : 'seriesWatchEpisode')
            .replace('{season}', season).replace('{episode}', episode);
    }

    function detailActionsHtml(item, isSeries, episodeRows, trailerId) {
        var favorite = isFavorite(item.id);
        var marked = hasReminder(item);
        var seriesPrimary = isSeries ? seriesPrimaryEpisode(episodeRows) : null;
        var glyphs = '';
        var primary = !isSeries ?
            '<div class="action-row detail-actions"><button class="button primary focusable" data-action="play" data-id="' +
                attr(item.id) + '">' + t('watch') + '</button></div>' :
            (seriesPrimary ? '<div class="action-row detail-actions"><button class="button primary focusable" data-action="series-primary-play" data-id="' +
                attr(seriesPrimary.item.id) + '">▶ ' + escapeHtml(seriesPrimaryLabel(seriesPrimary)) + '</button></div>' : '');
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
        /*
          Enviar a tela: manda *qual titulo*, nunca o video.

          Cast nao existe no Tizen Web Runtime — nao ha como abrir um socket a
          espera. Mas o aplicativo do Windows tambem nao transmite fluxo: ele
          entrega a identidade do titulo e a outra ponta o abre da lista dela,
          "so the other end plays from its own list and this machine's
          credentials stay here". Isso a TV pode fazer, pelo mesmo pareamento por
          codigo que ja traz chaves do celular — aqui no sentido inverso.

          Fica antes de compartilhar: a ordem dos glifos acompanha o Android e
          `reminders-app.test.js` verifica que compartilhar e o ultimo.
        */
        glyphs += actionGlyph('send-to-screen', item.id, '⇥', t('castAction'), false);
        glyphs += actionGlyph('share', item.id, '↗', t('share'), false);
        /*
          A volta nao esta aqui: ela e desenhada por `detailBackBar()`, no topo
          da pagina.

          Ficava no fim do hero, depois dos glifos, e isso a punha no meio da
          tela — abaixo da sinopse e das acoes, acima do elenco. O aplicativo do
          Windows a poe numa barra propria no alto, alinhada a esquerda, antes de
          qualquer conteudo da ficha, que e onde se procura por uma saida.

          Fora da barra de glifos ela continua, e pelo motivo de sempre: a ordem
          favoritar, lembrete, …, compartilhar por ultimo e a do Android e
          `reminders-app.test.js` a verifica. Um botao de sair no meio das acoes
          do titulo quebraria as duas coisas.
        */
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
    /*
      O botao de baixar, ou a razao de ele nao estar ali.

      Ele exige um pendrive ou HD montado, e isso nao e capricho: o
      armazenamento interno do aplicativo Tizen nao comporta video, entao a
      unica gravacao possivel e num volume removivel. Ver ADR-008.

      O que estava errado era o **silencio**. Sem USB o botao simplesmente
      sumia, e quem procurava por ele concluia que o aplicativo nao baixa —
      ou pior, que esta quebrado, porque o aplicativo do Windows e o do
      celular baixam. Uma linha dizendo o que falta transforma uma ausencia
      inexplicavel numa instrucao.

      A dica so aparece onde o download faria sentido: num titulo elegivel,
      de uma fonte que o adapter resolve. Num canal ao vivo — que a ADR-008
      recusa — continuar calado e o certo, porque ali nem um pendrive
      ajudaria.
    */
    function downloadButton(item, isSeries) {
        var source = sourceForDownload(item);
        var sourceSupported = source && (source.type === 'XTREAM' || source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U');
        var directM3uFile = source && (source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') &&
            item && item.locator && Boolean(item.locator.extension);
        var eligible = !isSeries && sourceSupported &&
            !((source.type === 'REMOTE_M3U' || source.type === 'LOCAL_M3U') && !directM3uFile) &&
            BuroDownloads.downloadable(item.contentType);
        if (!eligible) { return ''; }
        /*
          Sem armazenamento, o botao continua ali — apagado, e explicando quando
          tocado.

          Escrevi isto primeiro como um paragrafo no lugar do botao, e ficou
          errado por duas razoes: o texto solto desalinhava a barra de glifos, e
          a funcao deixava de ser descobrivel — quem nunca viu o botao com um
          pendrive ligado nao sabia que baixar existia.

          Um glifo apagado diz "isto existe e agora nao da", que e a verdade. A
          razao aparece ao tocar, quando a pessoa perguntou.
        */
        if (!BuroDownloads.enabled()) {
            return actionGlyph('download-unavailable', item.id, '↓', t('download'), false);
        }
        return downloadControls(item, false);
    }

    /*
      Diz o que falta, e distingue os dois motivos.

      Sem pendrive a pessoa resolve ligando um; sem a API de download a TV
      simplesmente nao sabe baixar, e ligar um pendrive nao mudaria nada.
      Uma mensagem so mandaria metade das pessoas procurar um cabo a toa.
    */
    function explainDownloadUnavailable() {
        showToast(t(BuroDownloads.available() ? 'downloadNeedsUsb' : 'downloadUnsupported'), true);
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
        /*
          Comecar qualquer reproducao encerra a contagem pendente.

          O encadeamento ja a cancela antes de chamar aqui, mas uma reproducao
          iniciada por outro caminho — a pessoa abriu outro titulo — deixava o
          alvo de pe. Com ele de pe o tratador de teclas do player responde a
          contagem e engole todo o resto: no canal ao vivo seguinte, a tecla
          verde deixava de abrir o guia. Cancelar na raiz vale para todos os
          caminhos.
        */
        cancelNextEpisode(true);
        /* Uma reproducao de verdade encerra a previa: dois fluxos ao mesmo
           tempo e o dobro do custo, e o audio brigaria. */
        cancelPreview();
        restoreSubtitleOffset();
        if (!found.item) { return; }
        decision = BuroDomain.resumeDecision(progress && progress.entry, found.item.contentType !== 'LIVE');
        if (decision.kind === 'resume') {
            pushScreen('RESUME_PROMPT', { itemId: itemId, positionMs: decision.positionMs });
            return;
        }
        beginPlayback(itemId, 0);
    }

    /*
      A linha de Continuar conhece a identidade realmente reproduzida. Quando
      um episodio e apresentado pela capa da serie pai, abrir pelo id do card
      tocaria a serie, nao o episodio salvo. As acoes diretas recebem o id da
      linha de progresso, validam novamente o perfil ativo e so entao resolvem
      o item real, mantendo URL e credencial no mesmo caminho tardio do player.
    */
    function playProgressRow(rowId, restart) {
        var row;
        var found;
        var position;
        var duration;
        if (state.screen !== 'SHELL' || state.section !== 'CONTINUE_WATCHING') { return; }
        row = activeProgressRow(rowId);
        if (!row || row.completed) { return; }
        found = findItemAndSource(row.itemId);
        if (!found.item || !found.source ||
                (found.item.contentType !== 'MOVIE' && found.item.contentType !== 'EPISODE')) { return; }
        position = restart ? 0 : Math.max(0, Number(row.positionMs) || 0);
        duration = Math.max(0, Number(row.durationMs) || 0);
        if (duration > 0) { position = Math.min(position, duration); }
        beginPlayback(found.item.id, position);
    }

    /*
      Reprodução de um programa arquivado pelo próprio provedor.

      O programa é transitório: guardamos somente canal e EPG na memória da
      sessão. Usuário, senha e URL timeshift nascem aqui e seguem diretamente
      ao AVPlay, sem entrar em item, progresso, localStorage ou IndexedDB.
    */
    function beginCatchUp(channelId, programme, startPositionMs, preserveRetryBudget) {
        var found = findItemAndSource(channelId);
        var nowSeconds = Math.floor(Date.now() / 1000);
        var locator;
        var secret;
        var title;
        var playback;
        if (preserveRetryBudget !== true) { playbackRetry.reset(); }
        if (!found.item || !found.source || found.source.type !== 'XTREAM') {
            showToast(t('catchUpUnavailable'), true); return;
        }
        locator = BuroXtream.catchUpLocator(found.item.locator, programme, nowSeconds);
        if (!locator) { showToast(t('catchUpUnavailable'), true); return; }
        try { secret = BuroStorage.secureGet(found.source.id); }
        catch (error) { showToast(t('catchUpUnavailable'), true); return; }
        playbackResolveRequestId += 1;
        title = BuroDomain.trim(programme && programme.title) || found.item.name;
        startPositionMs = Math.max(0, Number(startPositionMs) || 0);
        currentPlayback = {
            itemId: BuroDomain.id('catchup', channelId + ':' + locator.startEpochSeconds),
            title: title, contentType: 'MOVIE', positionMs: startPositionMs,
            durationMs: locator.durationMinutes * 60000, lastSavedAt: 0,
            catchUpChannelId: channelId, catchUpProgramme: programme, skipProgress: true
        };
        playback = currentPlayback;
        playerTitle.textContent = title;
        overlay.hidden = false;
        document.body.classList.add('playing');
        root.setAttribute('aria-hidden', 'true');
        preparePlayerOverlay();
        updatePlayerTimeline(startPositionMs, playback.durationMs);
        /* Catch-up e um ficheiro gravado, nao uma emissao: le a frente como um filme. */
        try { BuroPlayer.play(BuroXtream.resolveCatchUp(secret, locator), startPositionMs, false); }
        catch (error) { playbackFailed({ code: 'PLAYBACK_SOURCE_UNAVAILABLE' }); }
        secret = null;
        locator = null;
    }

    function playSeriesPrimaryEpisode(itemId) {
        var data = state.screenData;
        var target;
        if (!data || data.kind !== 'series') { return; }
        target = seriesPrimaryEpisode(data.items);
        if (!target || target.item.id !== itemId) { return; }
        beginPlayback(target.item.id, target.decision.kind === 'resume' ?
            Number(target.decision.positionMs) || 0 : 0);
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
        currentPlayback = { itemId: item.id, title: item.name, contentType: item.contentType, positionMs: startPositionMs,
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
            try {
                BuroPlayer.play(
                    BuroXtream.resolvePlayback(secret, playbackItem.locator),
                    startPositionMs,
                    isLiveContent(item.contentType)
                );
            }
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
                BuroPlayer.play(url, startPositionMs, isLiveContent(item.contentType));
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

    function beginPlayback(itemId, startPositionMs, preserveRetryBudget) {
        var found = findItemAndSource(itemId);
        var requestId;
        if (preserveRetryBudget !== true) { playbackRetry.reset(); }
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
        playbackRetry.reset();
        if (!entry || entry.state !== 'COMPLETED') { showToast(t('unavailable'), true); return; }
        currentPlayback = {
            itemId: entry.id, title: entry.name, contentType: entry.contentType, positionMs: Math.max(0, Number(startPositionMs) || 0),
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
            BuroPlayer.play(uri, playback.positionMs, isLiveContent(playback.contentType));
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
        if (found.source.type === 'REMOTE_M3U' || found.source.type === 'LOCAL_M3U') {
            openM3uLiveDetails(found.item, found.source, originSection);
            return;
        }
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

    function m3uGuideRequestCurrent(requestId, itemId) {
        return state.screen === 'SHELL' && state.screenData && state.screenData.kind === 'live' &&
            state.screenData.requestId === requestId && state.screenData.parent &&
            state.screenData.parent.id === itemId;
    }

    function finishM3uGuide(requestId, item, schedule) {
        if (!m3uGuideRequestCurrent(requestId, item.id)) { return; }
        state.screenData.schedule = (schedule || []).slice(0, 100);
        state.screenData.epgLoading = false;
        focusIndex = 0;
        render();
    }

    function loadM3uGuideIds(source, item, requestId, secret) {
        var currentTvgId = BuroDomain.trim(item.locator && item.locator.tvgId);
        var initial = { ids: [], seen: {} };
        function addId(accumulator, candidate) {
            var value = BuroDomain.trim(candidate && candidate.locator && candidate.locator.tvgId);
            var key = value.toLowerCase();
            if (value && !accumulator.seen[key] && accumulator.ids.length < 20000) {
                accumulator.seen[key] = true;
                accumulator.ids.push(value);
            }
            return accumulator;
        }
        function requestGuide(result) {
            if (!m3uGuideRequestCurrent(requestId, item.id)) { return; }
            if (!result.seen[currentTvgId.toLowerCase()]) { result = addId(result, item); }
            BuroXmltv.load(source.id, secret.epgUrls, result.ids, function () {
                finishM3uGuide(requestId, item, BuroXmltv.schedule(source.id, currentTvgId));
            }, function () { finishM3uGuide(requestId, item, []); });
        }
        BuroStorage.foldByIndex('items', 'byType', [source.id, 'LIVE'], addId, initial,
            requestGuide, function () { requestGuide(addId(initial, item)); });
    }

    function openM3uLiveDetails(item, source, originSection) {
        var requestId = ++catalogueRequestId;
        var tvgId = BuroDomain.trim(item.locator && item.locator.tvgId);
        var secret;
        var cached;
        try { secret = BuroStorage.secureGet(source.id); }
        catch (error) { secret = null; }
        cached = tvgId ? BuroXmltv.schedule(source.id, tvgId) : [];
        state.screenData = {
            kind: 'live', parent: item, schedule: cached.slice(0, 100),
            epgLoading: Boolean(!cached.length && tvgId && secret && BuroXmltv.safeUrls(secret.epgUrls).length),
            originSection: originSection, requestId: requestId
        };
        focusIndex = 0;
        render();
        if (!state.screenData.epgLoading) { return; }
        loadM3uGuideIds(source, item, requestId, secret);
        secret = null;
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
            hint: t('trailerHint'),
            fallbackHint: t('trailerFallbackHint')
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
        var scheduled;
        persistProgress(false);
        closePlayerMenu();
        resetPlayerControlsLock();
        BuroPlayer.stop();
        scheduled = Boolean(currentPlayback) && playbackRetry.schedule(error, function () {
            if (currentPlayback && document.body.classList.contains('playing')) { retryPlayback(true); }
        });
        if (scheduled) {
            clearPlayerError();
            playerStatus.classList.remove('error');
            playerStatus.textContent = t('retryPlayback');
            playerWaiting.hidden = false;
            playerWaitingLabel.textContent = t('retryPlayback');
            overlay.setAttribute('aria-busy', 'true');
            showPlayerControls();
            return;
        }
        showPlayerError(error);
    }

    function stopPlayback() {
        var visual = capturePlaybackReturnVisual();
        var progressChanged;
        playbackResolveRequestId += 1;
        playbackRetry.reset();
        /* Sair leva a contagem junto: um temporizador vivo depois do player
           fechado abriria o proximo episodio por cima da tela que a pessoa
           acabou de escolher. */
        cancelNextEpisode(true);
        /* O convite de pular a abertura tambem sai: ele so vale enquanto um
           episodio esta tocando dentro dos primeiros minutos. */
        skipIntroVisible = false;
        if (playerSkipIntroButton) { playerSkipIntroButton.hidden = true; }
        /* O temporizador morre com a sessao: armado numa reproducao, nao deve
           derrubar a proxima que a pessoa escolher. */
        clearSleepTimer();
        resetPlayerControlsLock();
        progressChanged = persistProgress(false);
        currentPlayback = null;
        clearPlayerError();
        closePlayerMenu();
        BuroPlayer.stop();
        document.body.classList.remove('playing');
        root.removeAttribute('aria-hidden');
        overlay.hidden = true;
        if (progressChanged) { refreshPlaybackReturnVisual(visual); }
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
        if (!profileId || !playback || playback.contentType === 'LIVE' || playback.skipProgress ||
                (!completed && Number(playback.durationMs) <= 0)) { return false; }
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
        return true;
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
        else if (action === 'device-code') { pushScreen('DEVICE_CODE', {}); }
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
            homeLocalReturnData = null;
            clearSearchDebounce();
            if (element.getAttribute('data-section') !== 'DOWNLOADS') { clearDownloadSearchDebounce(); }
            if (element.getAttribute('data-section') !== 'HISTORY') {
                clearHistorySearchDebounce(); historyQuery = ''; libraryPages.HISTORY = 0;
            }
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
        } else if (action === 'continue-remove') { forgetContinueProgress(id);
        } else if (action === 'continue-play') { playProgressRow(id, false);
        } else if (action === 'continue-restart') { playProgressRow(id, true);
        } else if (action === 'history-remove') { forgetHistoryProgress(id);
        } else if (action === 'search-voice') { startVoiceSearch();
        } else if (action === 'reminder-horizon') {
            setReminderHorizon(element.getAttribute('data-days'));
        } else if (action === 'continue-clear') { pushScreen('CONTINUE_CLEAR_CONFIRM', {});
        } else if (action === 'continue-clear-confirm') { confirmClearContinue();
        } else if (action === 'history-clear') { pushScreen('HISTORY_CLEAR_CONFIRM', {});
        } else if (action === 'history-clear-confirm') { confirmClearHistory();
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
            /* Tentar de novo é pedir uma varredura nova: servir o guardado seria
               responder a mesma coisa que acabou de falhar. */
            forgetHomeCache();
            state.screenData = null; focusIndex = 0; render();
        } else if (action === 'catalogue-retry') { retryCatalogueRequest();
        } else if (action === 'series-details-retry') { openSeriesById(id, state.screenData && state.screenData.originSection);
        } else if (action === 'demo-story') {
            state.screenData = { kind: 'demo-story', demoId: id || 'demo:hero:quiet-orbit' }; focusIndex = 0; render();
        } else if (action === 'source-add') { pushScreen('SOURCE_CHOICE'); }
        else if (action === 'source-usb-m3u') { openUsbM3uPicker(); }
        else if (action === 'source-usb-m3u-retry') { loadUsbM3uFiles(); }
        else if (action === 'source-usb-m3u-select') { selectUsbM3u(element.getAttribute('data-key')); }
        else if (action === 'source-form') { pushScreen('SOURCE_FORM', { type: element.getAttribute('data-type') }); }
        else if (action === 'source-connect') { connectSource(element.getAttribute('data-type')); }
        else if (action === 'source-manage') { pushScreen('SOURCE_MANAGE', { sourceId: id, confirmDelete: false }); }
        else if (action === 'source-refresh') { refreshSource(); }
        else if (action === 'active-source-refresh') { refreshActiveSourceFromTopBar(); }
        else if (action === 'catalogue-refresh') { refreshCatalogueFromTopBar(); }
        else if (action === 'diagnostics') { openDiagnostics(); }
        else if (action === 'toggle-merge-sources') { toggleMergeSources(); }
        else if (action === 'diagnostics-run') { runDiagnostics(); }
        else if (action === 'diagnostics-close') { closeDiagnostics(); }
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
        } else if (action === 'category-load-more') { loadMoreCategory();
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
        else if (action === 'catch-up') {
            var programmeIndex = Number(element.getAttribute('data-program-index'));
            var liveData = state.screenData;
            if (liveData && liveData.kind === 'live' && liveData.parent && liveData.parent.id === id &&
                    isFinite(programmeIndex) && liveData.schedule && liveData.schedule[programmeIndex]) {
                beginCatchUp(id, liveData.schedule[programmeIndex], 0);
            }
        }
        else if (action === 'series-primary-play') { playSeriesPrimaryEpisode(id); }
        else if (action === 'resume-continue') { chooseResume(true); }
        else if (action === 'resume-restart') { chooseResume(false); }
        else if (action === 'movie-details') { rememberHomeLocalReturn(element); openMovieDetails(id); }
        else if (action === 'series-details') { rememberHomeLocalReturn(element); openSeriesById(id); }
        else if (action === 'live-details') { rememberHomeLocalReturn(element); openLiveDetails(id); }
        else if (action === 'trailer') { openTrailer(id); }
        else if (action === 'person') { openPerson(element.getAttribute('data-name')); }
        else if (action === 'person-local') { openPersonLocal(id); }
        else if (action === 'person-credit') { openPersonCredit(element); }
        else if (action === 'similar-title') { openSimilarTitle(element.getAttribute('data-key')); }
        else if (action === 'share') { openTitleShare(id); }
        else if (action === 'send-to-screen') { sendTitleToScreen(id); }
        else if (action === 'subscription-filter') { loadSubscriptions(element.getAttribute('data-kind')); }
        else if (action === 'subscription-region') {
            changeActiveTmdbRegion(element.getAttribute('data-region'));
            loadSubscriptions(state.screenData.filter);
        }
        else if (action === 'settings-region') {
            changeActiveTmdbRegion(element.getAttribute('data-region'));
            render();
        }
        else if (action === 'subscription-retry') { loadSubscriptions(state.screenData.filter, true); }
        else if (action === 'subscription-title') {
            var subscriptionTitle = findSubscriptionTitle(element.getAttribute('data-key'));
            if (subscriptionTitle) { selectSubscriptionTitle(subscriptionTitle, null, subscriptionVisualForTitle(subscriptionTitle, element)); }
        }
        else if (action === 'home-subscription-title') {
            openHomeStreamingTitle(element.getAttribute('data-key'));
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
        else if (action === 'download-unavailable') { explainDownloadUnavailable(); }
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
        else if (action === 'licence-inspect') {
            scheduleLicenceKeyInspection(state.screenData && state.screenData.licenceDraft, true);
        }
        else if (action === 'search-run') { runSearch(); }
        else if (action === 'search-next') { changeSearchPage(1); }
        else if (action === 'search-previous') { changeSearchPage(-1); }
        else if (action === 'search-retry') { changeSearchPage(0); }
        else if (action === 'toggle-setting') {
            property = element.getAttribute('data-property'); state.preferences[property] = !state.preferences[property]; savePreferences(); render();
        } else if (action === 'clock-format') {
            state.preferences.uses24HourClock = element.getAttribute('data-value') !== '12';
            savePreferences(); render();
        } else if (action === 'collapse-duplicates') {
            state.preferences.collapseDuplicateTitles = state.preferences.collapseDuplicateTitles === false;
            invalidateCatalogueShelves();
            savePreferences(); render();
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
        else if (action === 'pair-title') { startPairing('open_title'); }
        else if (action === 'pair-tmdb') { startPairing('tmdb_key'); }
        else if (action === 'pair-critics') { startPairing('critics_key'); }
        else if (action === 'pair-retry') { startPairing((state.screenData && state.screenData.kind) || 'tmdb_key'); }
        else if (action === 'storage-settings') { pushScreen('STORAGE_SETTINGS', {}); measureStorage(); }
        else if (action === 'artwork-cache-on') { setArtworkCache(true); }
        else if (action === 'artwork-cache-off') { setArtworkCache(false); }
        else if (action === 'artwork-cache-limit') { cycleArtworkCacheLimit(); }
        else if (action === 'artwork-cache-fill') { fillKnownArtwork(); }
        else if (action === 'artwork-cache-pause') { BuroArtworkCache.pause(); render(); }
        else if (action === 'artwork-cache-resume') { BuroArtworkCache.resume(); render(); }
        else if (action === 'artwork-cache-clear') {
            BuroArtworkCache.clear(function () { render(); });
            showToast(t('artworkCacheCleared'), false);
        }
        else if (action === 'storage-measure') { measureStorage(); }
        else if (action === 'storage-clear') { clearStoredCatalogue(); }
        else if (action === 'notifications') { openNotifications(); }
        else if (action === 'notifications-clear') { clearNotifications(); }
        else if (action === 'notification-remove') { removeNotification(element.getAttribute('data-id')); }
        else if (action === 'catalogue-pick-genre') { toggleCataloguePicker('genre'); }
        else if (action === 'catalogue-pick-service') { toggleCataloguePicker('service'); }
        else if (action === 'catalogue-pick-provider-directory') { toggleCataloguePicker('provider-directory'); }
        else if (action === 'catalogue-pick-year') { toggleCataloguePicker('year'); }
        else if (action === 'catalogue-pick-rating') { toggleCataloguePicker('rating'); }
        else if (action === 'catalogue-pick-density') { toggleCataloguePicker('density'); }
        else if (action === 'catalogue-provider-shortcut') {
            openCatalogueProviderShortcut(element.getAttribute('data-provider'));
        }
        else if (action === 'catalogue-option') {
            chooseCatalogueOption(element.getAttribute('data-picker'), element.getAttribute('data-value'));
        }
        else if (action === 'catalogue-scope-reset') { resetCatalogueScope(); }
        else if (action === 'catalogue-year-all') { cycleCatalogueYear('all'); }
        else if (action === 'catalogue-year-current') { cycleCatalogueYear('current'); }

        else if (action === 'catalogue-page-next') {
            goToCataloguePage(currentCatalogueType(),
                (Number(catalogueScope(currentCatalogueType()).page) || 0) + 1);
        }
        else if (action === 'catalogue-page-previous') {
            goToCataloguePage(currentCatalogueType(),
                (Number(catalogueScope(currentCatalogueType()).page) || 0) - 1);
        }
        else if (action === 'catalogue-page-forward-jump') {
            goToCataloguePage(currentCatalogueType(),
                (Number(catalogueScope(currentCatalogueType()).page) || 0) + CATALOGUE_PAGE_JUMP);
        }
        else if (action === 'catalogue-page-back-jump') {
            goToCataloguePage(currentCatalogueType(),
                (Number(catalogueScope(currentCatalogueType()).page) || 0) - CATALOGUE_PAGE_JUMP);
        }
        else if (action === 'catalogue-page-first') { goToCataloguePage(currentCatalogueType(), 0); }
        else if (action === 'catalogue-page-last') {
            goToCataloguePage(currentCatalogueType(), Number.MAX_SAFE_INTEGER);
        }

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
        var chosenNext;
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
            /*
              A contagem para o proximo episodio responde antes de tudo.

              ENTER comeca ja, para quem nao quer esperar os dez segundos.
              RETURN cancela e fecha o player, que e o que RETURN sempre fez
              ali. Qualquer outra tecla tambem cancela a contagem, sem fazer
              mais nada: quem mexe no controle esta decidindo por si, e o app
              nao deve continuar contando por baixo dessa decisao.
            */
            if (nextEpisodeTarget) {
                if (event.keyCode === K.ENTER || event.keyCode === K.PLAY_PAUSE ||
                        event.keyCode === K.PLAY) {
                    chosenNext = nextEpisodeTarget;
                    cancelNextEpisode(false);
                    playItem(chosenNext.id);
                } else if (event.keyCode === K.RETURN || event.keyCode === K.STOP) {
                    cancelNextEpisode(true);
                    stopPlayback();
                } else {
                    cancelNextEpisode(true);
                }
                event.preventDefault();
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
            /*
              Com o convite na tela, ENTER pula a abertura.

              As quatro teclas coloridas ja tem dono e o convite dura pouco
              mais de dois minutos, entao gastar uma delas com algo tao
              passageiro seria caro. O ENTER longo continua bloqueando os
              controles: so o toque curto salta.
            */
            else if (event.keyCode === K.ENTER && skipIntroVisible) { skipIntro(); event.preventDefault(); }
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
        previewFrame = document.getElementById('live-preview');
        playerSkipIntroButton = document.getElementById('player-skip-intro');
        playerNextPanel = document.getElementById('player-next-panel');
        playerNextTitle = document.getElementById('player-next-title');
        playerNextCountdown = document.getElementById('player-next-countdown');
        playerSleepLabel = document.getElementById('player-sleep-label');
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
        startClock();
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
                updateSkipIntro();
            },
            onSubtitle: showPlayerSubtitle,
            onComplete: function () {
                var visual = capturePlaybackReturnVisual();
                var finishedId = currentPlayback && currentPlayback.itemId;
                var progressChanged;
                var next;
                progressChanged = persistProgress(true);
                /* LIVE e catch-up não gravam progresso; ainda assim o fim
                   natural precisa encerrar a sessão em memória. */
                currentPlayback = null;
                clearPlayerError(); closePlayerMenu();
                /*
                  Um episodio que termina oferece o proximo, em vez de devolver
                  a pessoa a ficha para procura-lo. Numa serie de vinte
                  episodios essa procura acontecia vinte vezes.

                  O progresso e gravado antes: se a contagem for aceita, o
                  episodio que acabou ja esta marcado como visto, e o proximo
                  comeca com a lista correta atras dele.
                */
                /*
                  O temporizador "ao fim deste episodio" ganha do encadeamento.

                  Quem o escolheu disse que este e o ultimo, entao oferecer o
                  proximo — e comeca-lo sozinho em dez segundos — seria o oposto
                  do pedido.
                */
                if (sleepTimerAtEpisodeEnd) {
                    clearSleepTimer();
                    document.body.classList.remove('playing'); root.removeAttribute('aria-hidden'); overlay.hidden = true;
                    if (progressChanged) { refreshPlaybackReturnVisual(visual); }
                    showToast(t('sleepTimerFired'), false);
                    return;
                }
                next = finishedId ? nextEpisodeAfter(finishedId) : null;
                if (next) {
                    if (progressChanged) { refreshPlaybackReturnVisual(visual); }
                    beginNextEpisodeCountdown(next);
                    return;
                }
                document.body.classList.remove('playing'); root.removeAttribute('aria-hidden'); overlay.hidden = true;
                if (progressChanged) { refreshPlaybackReturnVisual(visual); }
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
        /* A fila pode concluir milhares de capas. Agrupar redesenhos mantém o
           progresso vivo sem recompor a tela a cada arquivo individual. */
        BuroArtworkCache.watch(function () {
            if (state.screen !== 'STORAGE_SETTINGS' || artworkCacheRenderTimer) { return; }
            artworkCacheRenderTimer = window.setTimeout(function () {
                artworkCacheRenderTimer = null;
                if (state.screen === 'STORAGE_SETTINGS') { render(); }
            }, 180);
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
                /* Depois da licenca, e nao antes: o provisionamento so faz
                   sentido para um aparelho que o servidor ja conhece, e e o
                   registro que o apresenta. */
                applyAssignedSource();
            }, function (error) {
                if (!error || error.status !== 404) { return; }
                BuroLicense.register(function () {
                    if (state.ready) { render(); }
                    /* Um aparelho recem-registrado e justamente o caso de quem
                       acabou de comprar: e a primeira abertura que vai buscar a
                       lista que o vendedor deixou pronta. */
                    applyAssignedSource();
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
        document.addEventListener('visibilitychange', receiveRequestedAppControlOnResume);
        window.addEventListener('appcontrol', receiveRequestedAppControlEvent);
        window.addEventListener('focus', receiveRequestedAppControlOnResume);
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
        /* Expostas para o teste: o encadeamento so acontece no fim de uma
           reproducao real, que o AVPlay nao entrega fora de uma TV. */
        _nextEpisodeTargetForTest: function(){return nextEpisodeTarget;},
        _nextEpisodeAfter: nextEpisodeAfter,
        /* Expostas para o teste do temporizador: o menu so abre sobre uma
           sessao de reproducao, que o AVPlay nao entrega fora de uma TV. */
        _setCurrentPlaybackForTest: function (playback) { currentPlayback = playback; },
        _openPlayerMenu: openPlayerMenu,
        _setSleepTimer: setSleepTimer,
        _updateSkipIntro: updateSkipIntro,
        _skipIntro: skipIntro,
        _applySubtitleOffset: applySubtitleOffset,
        _forgetHomeCache: forgetHomeCache,
        _matchSubscriptionLocal: matchSubscriptionLocal,
        _schedulePreview: schedulePreview,
        /* Envelhece o cache sem o descartar: e o estado de uma segunda visita
           horas depois, que e quando a conferencia serve. */
        _ageHomeCacheForTest: function () { if (homeCache) { homeCache.at = 0; } },
        _restoreSubtitleOffset: restoreSubtitleOffset,
        _beginNextEpisodeCountdown: beginNextEpisodeCountdown,
        _onKeyDown: onKeyDown,
        _onKeyUp: onKeyUp,
        _playbackFailed: playbackFailed,
        _friendlyError: friendlyError,
        _ratingsSection: ratingsSection,
        _downloadChipHtml: downloadChipHtml,
        _refreshChipHtml: refreshChipHtml,
        _applyAssignedSource: applyAssignedSource,
        /* So para os testes: poe o foco num alvo pelo `data-action`, para
           exercitar o D-pad a partir de onde a pessoa realmente esta. */
        _focusAction: function (action) { refreshFocus(action, null); },
        _bootProgressPercent: bootProgressPercent,
        _bootSteps: function () { return BOOT_STEPS.slice(); },
        _prepareHomeForReveal: prepareHomeForReveal,
        _homeCache: function () { return homeCache; },
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
        /* O tamanho do bloco muda para caber na fileira da TV; os testes leem daqui
           em vez de repetir o número e quebrar a cada ajuste. */
        _catalogueBlockSize: function () { return CATALOGUE_BLOCK_SIZE; },
        _cataloguePageSize: function () { return CATALOGUE_PAGE_SIZE; },
        _artworkFor: function (itemId) { return artworkMemory[itemId]; },
        _rememberArtworkMap: rememberArtworkMap,
        _artworkCount: function () { return Object.keys(artworkMemory).length; },
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
