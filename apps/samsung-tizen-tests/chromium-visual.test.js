/* Visual/layout smoke against the production Samsung HTML and CSS at 1920x1080. */
'use strict';

var childProcess = require('child_process');
var fs = require('fs');
var os = require('os');
var path = require('path');
var pathToFileURL = require('url').pathToFileURL;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var APP_URL = pathToFileURL(path.join(APP_DIR, 'index.html')).href;
var SAFE_X = 64;
var SAFE_Y = 46;
var passed = 0;
var failures = [];
var browser = null;
var profileDir = null;
var socket = null;
var runtimeExceptions = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function delay(milliseconds) {
    return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
}

function browserExecutable() {
    var candidates = [
        process.env.CHROME_PATH,
        'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
        'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
        'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
        '/usr/bin/google-chrome',
        '/usr/bin/google-chrome-stable',
        '/usr/bin/chromium',
        '/usr/bin/chromium-browser'
    ];
    return candidates.filter(Boolean).filter(function (candidate) { return fs.existsSync(candidate); })[0] || null;
}

async function devToolsPort(directory) {
    var activePort = path.join(directory, 'DevToolsActivePort');
    var deadline = Date.now() + 10000;
    while (Date.now() < deadline) {
        if (fs.existsSync(activePort)) {
            var port = Number(fs.readFileSync(activePort, 'utf8').split(/\r?\n/)[0]);
            if (port > 0) { return port; }
        }
        if (browser && browser.exitCode != null) {
            throw new Error('Chromium encerrou antes de publicar a porta de depuracao (codigo ' + browser.exitCode + ').');
        }
        await delay(50);
    }
    throw new Error('Chromium nao publicou DevToolsActivePort em 10 segundos.');
}

async function pageTarget(port) {
    var deadline = Date.now() + 10000;
    while (Date.now() < deadline) {
        try {
            var response = await fetch('http://127.0.0.1:' + port + '/json/list');
            var targets = await response.json();
            var page = targets.filter(function (target) {
                return target.type === 'page' && target.url.indexOf('index.html') >= 0;
            })[0];
            if (page && page.webSocketDebuggerUrl) { return page; }
        } catch (ignored) { /* O navegador ainda esta iniciando. */ }
        await delay(50);
    }
    throw new Error('A pagina do app Samsung nao apareceu no Chromium.');
}

function connect(endpoint) {
    return new Promise(function (resolve, reject) {
        var pending = {};
        var eventHandlers = {};
        var nextId = 0;
        socket = new WebSocket(endpoint);
        socket.onopen = function () {
            function send(method, params) {
                return new Promise(function (sendResolve, sendReject) {
                    var id = ++nextId;
                    pending[id] = { resolve: sendResolve, reject: sendReject };
                    socket.send(JSON.stringify({ id: id, method: method, params: params || {} }));
                });
            }
            send.on = function (method, handler) { eventHandlers[method] = handler; };
            resolve(send);
        };
        socket.onerror = function () { reject(new Error('Falha ao conectar ao Chrome DevTools Protocol.')); };
        socket.onmessage = function (event) {
            var message = JSON.parse(String(event.data));
            var request = pending[message.id];
            if (!request) {
                if (message.method && eventHandlers[message.method]) {
                    eventHandlers[message.method](message.params || {});
                }
                return;
            }
            delete pending[message.id];
            if (message.error) { request.reject(new Error(message.error.message)); }
            else { request.resolve(message.result || {}); }
        };
        socket.onclose = function () {
            Object.keys(pending).forEach(function (id) {
                pending[id].reject(new Error('Chromium fechou a conexao CDP.'));
                delete pending[id];
            });
        };
    });
}

function rectInside(rectangle) {
    return rectangle && rectangle.width > 0 && rectangle.height > 0 &&
        rectangle.left >= SAFE_X && rectangle.top >= SAFE_Y &&
        rectangle.right <= 1920 - SAFE_X && rectangle.bottom <= 1080 - SAFE_Y;
}

function rectVisible(rectangle) {
    return rectangle && rectangle.width > 0 && rectangle.height > 0 &&
        rectangle.left >= 0 && rectangle.top >= 0 &&
        rectangle.right <= 1920 && rectangle.bottom <= 1080;
}

function rectanglesOverlap(first, second) {
    if (!first || !second) { return true; }
    return first.left < second.right && first.right > second.left &&
        first.top < second.bottom && first.bottom > second.top;
}

/*
 * Catálogo deliberadamente fictício. Ele usa somente os contratos públicos da
 * aplicação e URLs HTTPS fictícias interceptadas pelo próprio teste, por isso
 * o smoke visual percorre as telas reais sem playlist, credencial ou rede.
 */
function seedVisualCatalogue() {
    return new Promise(function (resolve, reject) {
        var state = BuroApp.state;
        var now = Date.now();
        var year = new Date().getFullYear();
        var source = BuroDomain.createSourceMetadata({
            id: 'source-visual', name: 'Acervo visual sintético', type: 'LOCAL_M3U', channelCount: 18,
            createdAt: now - 86400000, updatedAt: now
        });
        var categories = [
            { id: 'visual-movies', sourceId: source.id, name: 'Filmes | Drama', contentType: 'MOVIE', sortOrder: 0 },
            { id: 'visual-classics', sourceId: source.id, name: 'Filmes | Clássicos', contentType: 'MOVIE', sortOrder: 1 },
            { id: 'visual-series', sourceId: source.id, name: 'Séries | Originais', contentType: 'SERIES', sortOrder: 0 },
            { id: 'visual-live', sourceId: source.id, name: 'TV | Estúdios', contentType: 'LIVE', sortOrder: 0 }
        ];
        var definitions = [
            ['movie-aurora', 'Aurora de Vidro', 'visual-movies', 'MOVIE', 'Drama', year, 8.7],
            ['movie-atlas', 'Atlas de Papel', 'visual-movies', 'MOVIE', 'Drama, Mistério', year, 8.3],
            ['movie-nocturne', 'Nocturno Azul', 'visual-movies', 'MOVIE', 'Suspense', year - 1, 8.1],
            ['movie-forest', 'Sinal da Floresta', 'visual-movies', 'MOVIE', 'Aventura', year - 1, 7.9],
            ['movie-prism', 'Cidade Prisma', 'visual-movies', 'MOVIE', 'Ficção', year - 2, 7.7],
            ['movie-ember', 'Linha de Brasa', 'visual-movies', 'MOVIE', 'Drama', year - 3, 7.5],
            ['classic-orbit', 'Órbita Silenciosa', 'visual-classics', 'MOVIE', 'Clássico', year - 24, 9.0],
            ['classic-river', 'O Rio Imóvel', 'visual-classics', 'MOVIE', 'Clássico, Drama', year - 31, 8.8],
            ['series-horizon', 'Horizonte Norte', 'visual-series', 'SERIES', 'Drama', year, 8.9],
            ['series-archive', 'Arquivo Âmbar', 'visual-series', 'SERIES', 'Mistério', year - 1, 8.5],
            ['series-frequency', 'Frequência Azul', 'visual-series', 'SERIES', 'Ficção', year, 8.2],
            ['series-violet', 'Janela Violeta', 'visual-series', 'SERIES', 'Drama', year - 2, 7.8],
            ['series-axis', 'Eixo Suave', 'visual-series', 'SERIES', 'Documentário', year - 4, 7.6],
            ['series-code', 'Código Verde', 'visual-series', 'SERIES', 'Aventura', year - 1, 8.0],
            ['live-studio', 'Estúdio Norte', 'visual-live', 'LIVE', null, null, null],
            ['live-room', 'Sala Solar', 'visual-live', 'LIVE', null, null, null],
            ['live-field', 'Notas de Campo', 'visual-live', 'LIVE', null, null, null],
            ['live-stage', 'Palco Violeta', 'visual-live', 'LIVE', null, null, null]
        ];
        var items = definitions.map(function (row, index) {
            return BuroDomain.createItem({
                id: row[0], sourceId: source.id, categoryId: row[2], name: row[1],
                contentType: row[3], providerItemId: 'synthetic-' + index, locator: null,
                genre: row[4], year: row[5], rating: row[6], sortOrder: index,
                addedAt: now - index * 3600000
            });
        });
        var artwork = [
            'https://visual.invalid/buro_paper_sun.webp', 'https://visual.invalid/buro_category_atlas_v1.webp',
            'https://visual.invalid/buro_nocturne_hero.webp', 'https://visual.invalid/buro_forest_signal.webp'
        ];
        var profile = {};
        Object.keys(state.activeProfile).forEach(function (key) { profile[key] = state.activeProfile[key]; });
        profile.sourceId = source.id;
        BuroStorage.replaceSourceCatalogue(source, categories, items, true, function () {
            BuroStorage.put('profiles', profile, function () {
                state.sources = [source];
                state.categories = categories;
                state.items = items;
                state.activeSource = source;
                state.activeProfile = profile;
                state.favorites = [];
                state.progress = [{
                    id: 'visual-progress', profileId: profile.id, itemId: 'movie-atlas',
                    positionMs: 2520000, durationMs: 6000000, completed: false, updatedAt: now
                }];
                items.forEach(function (item, index) {
                    BuroApp._rememberArtwork(item.id, artwork[index % artwork.length]);
                    BuroApp._rememberDetailBackdrop(item.id, artwork[(index + 1) % artwork.length]);
                });
                state.screen = 'SHELL';
                state.section = 'HOME';
                state.screenData = null;
                BuroApp.render();
                /* O perfil foi salvo milissegundos antes. O toast de 4,2 s é
                   correto em produção, mas encobriria todas as capturas deste
                   smoke, que deliberadamente termina mais rápido que isso. */
                document.getElementById('toast').hidden = true;
                resolve({ sourceId: source.id, itemCount: items.length });
            }, reject);
        }, reject);
    });
}

/*
 * Exercita a escala de uma lista IPTV realista no IndexedDB nativo do
 * Chromium. O conjunto continua inteiramente sintético e não possui locator,
 * URL, credencial ou acesso à rede.
 */
function seedScaleCatalogue(totalItems) {
    return new Promise(function (resolve, reject) {
        var state = BuroApp.state;
        var now = Date.now();
        var source = {
            id: 'source-scale', name: 'Catálogo sintético de escala', type: 'LOCAL_M3U',
            channelCount: totalItems, createdAt: now - 86400000, updatedAt: now
        };
        var kinds = ['MOVIE', 'SERIES', 'LIVE'];
        var categoryNames = { MOVIE: 'Filmes', SERIES: 'Séries', LIVE: 'Ao vivo' };
        var categories = [];
        var items = [];
        var kindIndex;
        var categoryIndex;
        var index;
        var tickTimes = [];
        var started = performance.now();
        var ticker = window.setInterval(function () { tickTimes.push(performance.now()); }, 10);
        function padded(value) { return ('00000' + value).slice(-5); }

        for (kindIndex = 0; kindIndex < kinds.length; kindIndex += 1) {
            for (categoryIndex = 0; categoryIndex < 4; categoryIndex += 1) {
                categories.push({
                    id: 'scale-' + kinds[kindIndex].toLowerCase() + '-' + categoryIndex,
                    sourceId: source.id,
                    name: categoryNames[kinds[kindIndex]] + ' ' + (categoryIndex + 1),
                    contentType: kinds[kindIndex],
                    sortOrder: categoryIndex
                });
            }
        }
        for (index = 0; index < totalItems; index += 1) {
            var kind = kinds[index % kinds.length];
            var bucket = Math.floor(index / kinds.length) % 4;
            items.push({
                id: 'scale-item-' + index,
                sourceId: source.id,
                categoryId: 'scale-' + kind.toLowerCase() + '-' + bucket,
                name: 'Título Escala ' + padded(index),
                contentType: kind,
                providerItemId: 'synthetic-scale-' + index,
                locator: null,
                genre: index % 2 ? 'Drama' : 'Aventura',
                year: 2020 + (index % 7),
                rating: 6 + (index % 31) / 10,
                sortOrder: index,
                addedAt: now - index * 1000
            });
        }
        BuroStorage.replaceSourceCatalogue(source, categories, items, true, function () {
            var writeFinished = performance.now();
            var maximumTickGap = tickTimes.length ? tickTimes[0] - started : writeFinished - started;
            var tickIndex;
            window.clearInterval(ticker);
            for (tickIndex = 1; tickIndex < tickTimes.length; tickIndex += 1) {
                maximumTickGap = Math.max(maximumTickGap, tickTimes[tickIndex] - tickTimes[tickIndex - 1]);
            }
            BuroStorage.foldByIndex('items', 'bySource', source.id, function (count) {
                return count + 1;
            }, 0, function (storedCount) {
                var profile = {};
                Object.keys(state.activeProfile).forEach(function (key) { profile[key] = state.activeProfile[key]; });
                profile.sourceId = source.id;
                state.sources = [source];
                state.categories = categories;
                state.items = [];
                state.activeSource = source;
                state.activeProfile = profile;
                state.favorites = [];
                state.progress = [];
                state.screen = 'SHELL';
                state.section = 'HOME';
                state.screenData = null;
                window.__scaleHomeStarted = performance.now();
                BuroApp.render();
                resolve({
                    requested: totalItems,
                    stored: storedCount,
                    writeMs: writeFinished - started,
                    ticks: tickTimes.length,
                    maximumTickGapMs: maximumTickGap,
                    categoryCount: categories.length
                });
            }, reject);
        }, function (error) {
            window.clearInterval(ticker);
            reject(error);
        });
    });
}

function showSyntheticSeriesDetail() {
    var state = BuroApp.state;
    var parent = state.items.filter(function (item) { return item.id === 'series-horizon'; })[0];
    var now = Date.now();
    var episodes = [1, 2, 3, 4].map(function (number) {
        return BuroDomain.createItem({
            id: 'episode-horizon-' + number, sourceId: parent.sourceId, categoryId: parent.id,
            name: 'Horizonte Norte — Episódio ' + number, contentType: 'EPISODE',
            providerItemId: 'episode-' + number,
            locator: { kind: 'xtream', contentType: 'EPISODE', providerItemId: 'episode-' + number, season: 1, episode: number },
            year: new Date().getFullYear(), rating: 8.4, sortOrder: number, addedAt: now - number * 60000
        });
    });
    state.items = state.items.filter(function (item) { return item.contentType !== 'EPISODE'; }).concat(episodes);
    state.section = 'SERIES';
    state.screenData = {
        kind: 'series', parent: parent, items: episodes, expandedSeason: 1, seasonPages: {},
        details: {
            title: parent.name, releaseDate: String(new Date().getFullYear()), duration: '52 min',
            genre: 'Drama, Mistério', rating: 8.9,
            plot: 'Uma equipe acompanha sinais incomuns no extremo norte enquanto tenta preservar a confiança entre seus integrantes.',
            director: 'Equipe BURO', country: 'Universo sintético',
            cast: 'Lina Vale, Caio Norte, Mira Sol'
        }
    };
    BuroApp.render();
    return episodes.length;
}

function showSyntheticLiveDetail() {
    var state = BuroApp.state;
    var parent = state.items.filter(function (item) { return item.id === 'live-studio'; })[0];
    var now = Math.floor(Date.now() / 1000);
    state.section = 'LIVE';
    state.screenData = {
        kind: 'live', parent: parent,
        schedule: [
            { title: 'Boletim do Estúdio', description: 'Resumo visual sintético.', startEpochSeconds: now - 900, endEpochSeconds: now + 900 },
            { title: 'Horizonte Diário', description: 'Próximo programa do catálogo de teste.', startEpochSeconds: now + 900, endEpochSeconds: now + 2700 },
            { title: 'Arquivo da Noite', description: 'Encerramento da programação fictícia.', startEpochSeconds: now + 2700, endEpochSeconds: now + 4500 }
        ]
    };
    BuroApp.render();
    return parent.id;
}

function prepareVisualLibraries() {
    var state = BuroApp.state;
    var profileId = state.activeProfile.id;
    var now = Date.now();
    function item(id) {
        return state.items.filter(function (candidate) { return candidate.id === id; })[0];
    }
    state.favorites = ['movie-aurora', 'series-horizon', 'live-studio'].map(function (itemId, index) {
        return { id: 'visual-favorite-' + index, profileId: profileId, itemId: itemId, createdAt: now - index * 1000 };
    });
    state.progress = [
        { id: 'visual-progress-1', profileId: profileId, itemId: 'movie-atlas', positionMs: 2520000, durationMs: 6000000, completed: false, updatedAt: now },
        { id: 'visual-progress-2', profileId: profileId, itemId: 'series-archive', positionMs: 1320000, durationMs: 3120000, completed: false, updatedAt: now - 1000 },
        { id: 'visual-progress-3', profileId: profileId, itemId: 'movie-forest', positionMs: 5940000, durationMs: 5940000, completed: true, updatedAt: now - 2000 }
    ];
    state.reminders = [
        BuroDomain.createReminder({ profileId: profileId, item: item('movie-prism'), releaseDate: new Date(now + 86400000).toISOString().slice(0, 10), artworkUrl: 'https://visual.invalid/buro_category_atlas_v1.webp' }),
        BuroDomain.createReminder({ profileId: profileId, item: item('series-frequency'), artworkUrl: 'https://visual.invalid/buro_forest_signal.webp' })
    ];
    state.preferences.notifications = BuroNotifications.sanitize([
        { id: 'visual-notice-episode', kind: 'NEW_EPISODE', title: 'Novo episódio disponível', body: 'Horizonte Norte · T1 E4', createdAt: now, read: false },
        { id: 'visual-notice-reminder', kind: 'REMINDER', title: 'Lembrete BURO', body: 'Cidade Prisma estreia amanhã.', createdAt: now - 1000, read: false }
    ]);
    return {
        favorites: state.favorites.length,
        progress: state.progress.length,
        reminders: state.reminders.length,
        notifications: state.preferences.notifications.length
    };
}

function showSyntheticDownloads() {
    var state = BuroApp.state;
    var entries = [
        {
            id: 'visual-download-active', name: 'Aurora de Vidro', contentType: 'MOVIE',
            state: 'DOWNLOADING', percent: 48, bytesPerSecond: 3145728, remainingSeconds: 732
        },
        {
            id: 'visual-download-paused', name: 'Horizonte Norte · T1 E3', contentType: 'EPISODE',
            state: 'PAUSED', percent: 71
        },
        {
            id: 'visual-download-complete', name: 'Atlas de Papel', contentType: 'MOVIE',
            state: 'COMPLETED', percent: 100
        }
    ];
    BuroDownloads.enabled = function () { return true; };
    BuroDownloads.list = function () { return entries; };
    state.screen = 'SHELL';
    state.section = 'DOWNLOADS';
    state.screenData = null;
    BuroApp.render();
    return entries.length;
}

function showSyntheticSubscriptions() {
    var state = BuroApp.state;
    function title(id, name, isSeries, year, poster) {
        return {
            tmdbId: id, id: id, title: name, isSeries: Boolean(isSeries), year: year,
            rating: 8.4, overview: 'Sinopse sintética para validar a composição da interface.',
            posterUrl: 'https://visual.invalid/' + poster
        };
    }
    var titles = [
        title(8101, 'Aurora Pública', false, 2026, 'buro_paper_sun.webp'),
        title(8102, 'Atlas em Série', true, 2025, 'buro_category_atlas_v1.webp'),
        title(8103, 'Nocturno Externo', false, 2024, 'buro_nocturne_hero.webp'),
        title(8104, 'Sinal Compartilhado', true, 2026, 'buro_forest_signal.webp')
    ];
    state.screen = 'SHELL';
    state.section = 'SUBSCRIPTIONS';
    state.screenData = {
        kind: 'subscriptions', filter: 'MOVIES', region: 'BR', loading: false, error: null,
        selected: null, shelves: [
            { providerId: 81, providerName: 'BURO Play', providerLogoUrl: null, titles: titles },
            { providerId: 82, providerName: 'Cinema Sintético', providerLogoUrl: null, titles: titles.slice().reverse() }
        ]
    };
    BuroApp.render();
    return titles.length;
}

function showSyntheticSubscriptionDetail() {
    var state = BuroApp.state;
    var selected = {
        tmdbId: 8101, id: 8101, title: 'Aurora Pública', isSeries: false, year: 2026,
        rating: 8.4, overview: 'Sinopse sintética para validar a composição da interface.',
        posterUrl: 'https://visual.invalid/buro_paper_sun.webp'
    };
    state.screen = 'SHELL';
    state.section = 'SUBSCRIPTIONS';
    state.screenData = {
        kind: 'subscriptions', filter: 'MOVIES', region: 'BR', loading: false, shelves: [],
        selected: selected, selectionLoading: false,
        selection: {
            localItem: state.items.filter(function (item) { return item.id === 'movie-aurora'; })[0],
            offers: [{
                providerId: 'visual-provider', providerName: 'BURO Play', type: 'flatrate',
                url: 'https://www.themoviedb.org/movie/8101/watch', requiresAttribution: true
            }],
            details: {
                title: selected.title, releaseDate: '2026', duration: 108, genre: 'Drama', rating: 8.4,
                plot: 'Uma exploradora recompõe memórias luminosas numa cidade cercada por vidro.',
                posterUrl: selected.posterUrl,
                backdropUrl: 'https://visual.invalid/buro_nocturne_hero.webp',
                castMembers: [
                    { name: 'Lina Vale', character: 'Aurora' },
                    { name: 'Caio Norte', character: 'Atlas' }
                ]
            }
        }
    };
    BuroApp.render();
    document.querySelector('.content.scrollable').scrollTop = 0;
    return state.screenData.selection.offers.length + 1;
}

function showSyntheticPerson() {
    var state = BuroApp.state;
    state.screen = 'PERSON';
    state.screenData = {
        name: 'Lina Vale', configured: true, loading: false, error: null,
        localMatches: { 'movie:8101': state.items.filter(function (item) { return item.id === 'movie-aurora'; })[0] },
        person: {
            name: 'Lina Vale', birthday: '1990-04-12', placeOfBirth: 'Universo sintético',
            biography: 'Intérprete fictícia usada somente para verificar tipografia, hierarquia, foco e rolagem desta tela.',
            photoUrl: 'https://visual.invalid/buro_paper_sun.webp',
            credits: [
                { id: 8101, title: 'Aurora Pública', year: 2026, character: 'Aurora', isSeries: false, posterUrl: 'https://visual.invalid/buro_paper_sun.webp' },
                { id: 8102, title: 'Atlas em Série', year: 2025, character: 'Mira', isSeries: true, posterUrl: 'https://visual.invalid/buro_category_atlas_v1.webp' }
            ]
        }
    };
    BuroApp.render();
    return state.screenData.person.credits.length;
}

function showSyntheticShare() {
    var state = BuroApp.state;
    state.screen = 'SHARE';
    state.screenData = BuroShare.build({
        kind: 'MOVIE', title: 'Aurora de Vidro', year: 2026,
        description: 'Uma recomendação pública sintética, sem fonte, stream ou credencial.'
    });
    BuroApp.render();
    return Boolean(state.screenData && state.screenData.qr && state.screenData.qr.svg);
}

function showSyntheticPlayer(itemId) {
    var state = BuroApp.state;
    var source = state.sources[0];
    var item = state.items.filter(function (candidate) { return candidate.id === itemId; })[0];
    if (!source || !item) { return false; }
    source.type = 'XTREAM';
    item.locator = {
        kind: 'xtream', contentType: item.contentType,
        providerItemId: item.contentType === 'LIVE' ? 'visual-live' : 'visual-movie',
        extension: item.contentType === 'LIVE' ? 'ts' : 'mp4'
    };
    BuroStorage.secureGet = function () {
        return { server: 'https://visual.invalid', username: 'synthetic', password: 'synthetic' };
    };
    window.webapis = { avplay: {
        getState: function () { return 'READY'; },
        open: function () {},
        setListener: function (listener) { window.__visualAvListener = listener; },
        setDisplayRect: function () {},
        setDisplayMethod: function () {},
        prepareAsync: function (ready) { ready(); },
        play: function () {}, pause: function () {}, stop: function () {}, close: function () {},
        seekTo: function (position, ready) { if (ready) { ready(); } },
        jumpForward: function (value, ready) { if (ready) { ready(); } },
        jumpBackward: function (value, ready) { if (ready) { ready(); } },
        getDuration: function () { return 6480000; },
        getTotalTrackInfo: function () { return [
            { type: 'AUDIO', index: 1, extra_info: '{"track_lang":"Português"}' },
            { type: 'AUDIO', index: 2, extra_info: '{"track_lang":"Deutsch"}' },
            { type: 'TEXT', index: 3, extra_info: '{"track_lang":"Português"}' },
            { type: 'TEXT', index: 4, extra_info: '{"track_lang":"Italiano"}' }
        ]; },
        setSelectTrack: function () {}, setSilentSubtitle: function () {}, setSpeed: function () {}
    } };
    var trigger = document.createElement('button');
    trigger.setAttribute('data-action', 'play');
    trigger.setAttribute('data-id', item.id);
    BuroApp._activate(trigger);
    if (window.__visualAvListener && window.__visualAvListener.oncurrentplaytime) {
        window.__visualAvListener.oncurrentplaytime(item.contentType === 'LIVE' ? 0 : 1944000);
    }
    return !document.getElementById('player-overlay').hidden;
}

async function main() {
    var executable = browserExecutable();
    if (!executable) { throw new Error('Chrome ou Edge nao encontrado para o smoke visual.'); }
    profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'iptvburo-tizen-visual-'));
    browser = childProcess.spawn(executable, [
        '--headless=new',
        '--disable-gpu',
        '--hide-scrollbars',
        '--window-size=1920,1080',
        '--force-device-scale-factor=1',
        '--user-data-dir=' + profileDir,
        '--remote-debugging-port=0',
        '--no-first-run',
        '--no-default-browser-check',
        '--allow-file-access-from-files',
        APP_URL
    ], { stdio: 'ignore' });

    var port = await devToolsPort(profileDir);
    var target = await pageTarget(port);
    var send = await connect(target.webSocketDebuggerUrl);
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Performance.enable');
    await send('HeapProfiler.enable');
    send.on('Runtime.exceptionThrown', function (event) {
        var details = event && event.exceptionDetails || {};
        var exception = details.exception || {};
        runtimeExceptions.push(exception.description || details.text || 'Excecao JavaScript sem descricao');
    });
    send.on('Fetch.requestPaused', function (event) {
        var requestUrl = event.request && event.request.url || '';
        var fileName = requestUrl.split('/').pop().split('?')[0];
        var allowed = {
            'buro_paper_sun.webp': true,
            'buro_category_atlas_v1.webp': true,
            'buro_nocturne_hero.webp': true,
            'buro_forest_signal.webp': true
        };
        if (!allowed[fileName]) {
            send('Fetch.failRequest', { requestId: event.requestId, errorReason: 'BlockedByClient' }).catch(function () {});
            return;
        }
        send('Fetch.fulfillRequest', {
            requestId: event.requestId,
            responseCode: 200,
            responseHeaders: [{ name: 'Content-Type', value: 'image/webp' }, { name: 'Cache-Control', value: 'no-store' }],
            body: fs.readFileSync(path.join(APP_DIR, 'assets', fileName)).toString('base64')
        }).catch(function () {});
    });
    await send('Fetch.enable', { patterns: [{ urlPattern: 'https://visual.invalid/*', requestStage: 'Request' }] });
    await send('Emulation.setDeviceMetricsOverride', {
        width: 1920,
        height: 1080,
        deviceScaleFactor: 1,
        mobile: false
    });
    await send('Page.reload', { ignoreCache: true });

    async function evaluate(expression) {
        var result = await send('Runtime.evaluate', {
            expression: expression,
            awaitPromise: true,
            returnByValue: true
        });
        if (result.exceptionDetails) {
            throw new Error(result.exceptionDetails.exception && result.exceptionDetails.exception.description ||
                result.exceptionDetails.text || 'Excecao ao avaliar a pagina.');
        }
        return result.result && result.result.value;
    }

    async function waitFor(expression, timeout) {
        var deadline = Date.now() + (timeout || 8000);
        while (Date.now() < deadline) {
            if (await evaluate(expression)) { return; }
            await delay(50);
        }
        throw new Error('Tempo esgotado aguardando: ' + expression);
    }

    async function screenshotIsRendered(name) {
        /* O frame deve ser exatamente o viewport da TV. O padrão moderno do
           CDP pode expandir a superfície para conteúdo rolável e recompor
           camadas fixas em offsets diferentes, criando um quadro que o usuário
           nunca vê. */
        var shot = await send('Page.captureScreenshot', {
            format: 'png', fromSurface: true, captureBeyondViewport: false
        });
        var bytes = Buffer.from(shot.data || '', 'base64');
        if (process.env.TIZEN_VISUAL_ARTIFACT_DIR && name) {
            var outputDirectory = path.resolve(process.env.TIZEN_VISUAL_ARTIFACT_DIR);
            fs.mkdirSync(outputDirectory, { recursive: true });
            fs.writeFileSync(path.join(outputDirectory, name + '.png'), bytes);
        }
        return bytes.length > 20000 && bytes.slice(1, 4).toString('ascii') === 'PNG' &&
            bytes.readUInt32BE(16) === 1920 && bytes.readUInt32BE(20) === 1080;
    }

    var geometryExpression = function (selectors) {
        function rectangle(selector) {
            var element = document.querySelector(selector);
            if (!element) { return null; }
            var value = element.getBoundingClientRect();
            return {
                left: value.left, top: value.top, right: value.right, bottom: value.bottom,
                width: value.width, height: value.height
            };
        }
        var focused = document.querySelector('.focused');
        return {
            width: window.innerWidth,
            height: window.innerHeight,
            scrollWidth: document.documentElement.scrollWidth,
            scrollHeight: document.documentElement.scrollHeight,
            rectangles: selectors.map(rectangle),
            focused: focused ? {
                action: focused.getAttribute('data-action'),
                language: focused.getAttribute('data-language'),
                rectangle: rectangle('.focused')
            } : null
        };
    };

    function geometry(selectors) {
        return evaluate('(' + geometryExpression.toString() + ')(' + JSON.stringify(selectors) + ')');
    }

    await waitFor("document.readyState === 'complete' && window.BuroApp && BuroApp.state.screen === 'LANGUAGE'");
    var language = await geometry(['.language-panel']);
    var languageOptions = await evaluate("Array.prototype.map.call(document.querySelectorAll('.language-option'), function (item) { return item.getBoundingClientRect().height; })");
    process.stdout.write('Idioma em 1920x1080\n');
    check('viewport de TV usa exatamente 1920x1080', language.width === 1920 && language.height === 1080);
    check('pagina de idioma nao cria overflow global', language.scrollWidth <= 1920 && language.scrollHeight <= 1080);
    check('painel de idioma permanece inteiro na area segura', rectInside(language.rectangles[0]));
    check('os cinco idiomas tem alvo vertical de pelo menos 70 px', languageOptions.length === 5 && Math.min.apply(Math, languageOptions) >= 70);
    check('Portuguese (Brasil) recebe o foco inicial visivel', language.focused && language.focused.language === 'pt-BR' && rectInside(language.focused.rectangle));
    check('a composicao de idioma produz um quadro PNG nao vazio', await screenshotIsRendered('language'));

    var boot = await evaluate("(function () { document.querySelector('[data-action=select-language][data-language=\"pt-BR\"]').click(); var panel = document.querySelector('.boot-panel'); var r = panel && panel.getBoundingClientRect(); return { screen: BuroApp.state.screen, dots: document.querySelectorAll('.boot-dot').length, progress: Number(document.querySelector('.boot-progress') && document.querySelector('.boot-progress').getAttribute('aria-valuenow')), rectangle: r && { left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height }, scrollWidth:document.documentElement.scrollWidth, scrollHeight:document.documentElement.scrollHeight }; }())");
    process.stdout.write('Carregamento cinematico\n');
    /* Cinco passos: a varredura do catalogo entrou na abertura, para o app so
       aparecer com as prateleiras cheias — ver BOOT_STEPS em js/app.js. */
    check('selecao do idioma abre imediatamente a tela de carregamento', boot.screen === 'BOOT' && boot.dots === 5 && boot.progress >= 20 && boot.progress <= 100);
    check('painel de carregamento fica inteiro e sem overflow global', rectInside(boot.rectangle) && boot.scrollWidth <= 1920 && boot.scrollHeight <= 1080);
    check('a tela de carregamento produz um quadro PNG nao vazio', await screenshotIsRendered('boot'));

    await waitFor("BuroApp.state.screen === 'LEGAL'", 5000);
    var legal = await geometry(['.legal-brand', '.legal-card', '.legal-accept']);
    process.stdout.write('Termos e perfis\n');
    check('marca e cartao legal permanecem na area segura', rectInside(legal.rectangles[0]) && rectInside(legal.rectangles[1]));
    check('marca nao sobrepoe o cartao legal', !rectanglesOverlap(legal.rectangles[0], legal.rectangles[1]));
    check('aceite legal recebe foco e permanece visivel', legal.focused && legal.focused.action === 'legal-accept' && rectInside(legal.rectangles[2]));

    await evaluate("document.querySelector('[data-action=legal-accept]').click(); true");
    await waitFor("BuroApp.state.screen === 'PROFILES'");
    var profiles = await geometry(['.brand-mark', '[data-action=profile-form]']);
    check('adicionar perfil recebe foco e fica inteiro na area segura', profiles.focused && profiles.focused.action === 'profile-form' && rectInside(profiles.rectangles[1]));

    await evaluate("document.querySelector('[data-action=profile-form]').click(); true");
    await waitFor("BuroApp.state.screen === 'PROFILE_FORM' && document.querySelector('#profile-name')");
    var form = await geometry(['.profile-form-panel', '#profile-name', '[data-action=profile-save]']);
    check('formulario, nome e salvar permanecem visiveis na tela', rectInside(form.rectangles[0]) && rectInside(form.rectangles[1]) && rectInside(form.rectangles[2]));
    await evaluate("(function () { var input=document.querySelector('#profile-name'); input.value='Casa Visual'; input.dispatchEvent(new Event('input', { bubbles:true })); document.querySelector('[data-action=profile-save]').click(); return true; }())");

    await waitFor("BuroApp.state.screen === 'SHELL' && BuroApp.state.section === 'HOME' && document.querySelector('.shell')", 8000);
    var home = await geometry(['.buro-ribbon', '.topbar', '.demo-notice', '.living-hero', '.topbar-status', '[data-action=notifications]']);
    var runtimeReady = await evaluate("document.querySelector('#app').getAttribute('data-runtime-ready')");
    process.stdout.write('Home sem fonte\n');
    check('shell anuncia runtime 3.0.1 e nao cria overflow global', runtimeReady === '3.0.1' && home.scrollWidth <= 1920 && home.scrollHeight <= 1080);
    check('ribbon e topbar full bleed ficam inteiras no quadro da TV', rectVisible(home.rectangles[0]) && rectVisible(home.rectangles[1]));
    check('aviso e hero permanecem inteiros no quadro da TV', rectVisible(home.rectangles[2]) && rectVisible(home.rectangles[3]));
    /* O chip "Samsung Tizen" deu lugar ao bloco de estado — licenca, relogio,
       perfil e sino — como no aplicativo do Windows. O que se mede continua
       sendo o mesmo: o aviso nao invade esse bloco. O sino agora esta dentro
       dele, entao a segunda comparacao passou a ser contencao e nao separacao. */
    check('aviso e bloco de estado da barra nao se sobrepoem',
        !rectanglesOverlap(home.rectangles[2], home.rectangles[4]) &&
        home.rectangles[5].left >= home.rectangles[4].left - 1 &&
        home.rectangles[5].right <= home.rectangles[4].right + 1);
    check('o primeiro destino da Home recebe foco visivel', home.focused && rectVisible(home.focused.rectangle));
    var demoComposition = await evaluate("({ rails:document.querySelectorAll('[data-demo-rail]').length, cards:document.querySelectorAll('.demo-media-card').length, progress:document.querySelectorAll('.demo-card-progress').length, posters:document.querySelectorAll('.demo-media-card.poster').length })");
    check('Home demonstra os mesmos tres trilhos e 14 conceitos do Android', demoComposition.rails === 3 && demoComposition.cards === 14 && demoComposition.progress === 8 && demoComposition.posters === 6);
    check('a Home produz um quadro PNG nao vazio', await screenshotIsRendered('demo-home'));

    await evaluate("document.querySelector('[data-id=\"demo:continue:prism-city\"]').click(); true");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'demo-story'");
    var demoDetail = await geometry(['.demo-story', '.demo-no-playback', '[data-action=source-add]', '[data-action=back]', '.buro-ribbon', '.topbar']);
    var demoDetailText = await evaluate("({ title:document.querySelector('.demo-story h2').textContent, play:document.querySelectorAll('[data-action=play]').length, mainScroll:document.querySelector('.main-pane').scrollTop })");
    check('card abre detalhe proprio, seguro e inteiro na area da TV', demoDetailText.title === 'Cidade Prisma' && demoDetailText.play === 0 && demoDetail.rectangles.every(rectVisible));
    check('detalhe preserva Ribbon e topbar fixas sem rolar o painel principal', demoDetailText.mainScroll === 0 && rectVisible(demoDetail.rectangles[4]) && rectVisible(demoDetail.rectangles[5]));
    check('detalhe demonstrativo produz um quadro PNG nao vazio', await screenshotIsRendered('demo-detail'));
    await evaluate("document.querySelector('[data-action=back]').click(); true");
    await waitFor("BuroApp.state.section === 'HOME' && !BuroApp.state.screenData && document.querySelectorAll('[data-demo-rail]').length === 3");
    check('voltar do detalhe restaura a Home demonstrativa completa', await evaluate("document.querySelectorAll('.demo-media-card').length === 14"));

    var seeded = await evaluate('(' + seedVisualCatalogue.toString() + ')()');
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'home' && !BuroApp.state.screenData.loading && document.querySelector('.real-home-hero')", 10000);
    var realHome = await geometry(['.buro-ribbon', '.topbar', '.real-home-hero', '.home-rail-heading', '.media-card']);
    var realHomeData = await evaluate("({ rails:document.querySelectorAll('.home-rail-heading').length, cards:document.querySelectorAll('.media-card').length, art:document.querySelectorAll('.media-card.has-art').length, progress:document.querySelectorAll('.media-progress').length, mainScroll:document.querySelector('.main-pane').scrollTop })");
    process.stdout.write('Home com catálogo local sintético\n');
    check('catálogo visual persiste os 18 itens sem rede', seeded.sourceId === 'source-visual' && seeded.itemCount === 18);
    check('Home real monta hero, trilhos e cartões com arte local', realHomeData.rails >= 4 && realHomeData.cards >= 8 && realHomeData.art >= 8);
    check('progresso do perfil aparece na Home real', realHomeData.progress >= 1);
    check('Home real mantém Ribbon, topbar, hero e primeiro trilho visíveis', realHome.rectangles.every(rectVisible));
    check('Home real não desloca o painel principal nem cria overflow global', realHomeData.mainScroll === 0 && realHome.scrollWidth <= 1920 && realHome.scrollHeight <= 1080);
    check('Home real produz um quadro PNG não vazio', await screenshotIsRendered('real-home'));

    /* A aba abre direto na prateleira, como no aplicativo do Windows: as
       categorias viraram filtro na barra em vez de um degrau antes dos
       pôsteres. O que continua sendo medido é o mesmo — a barra de escopo e
       os cartões cabem na tela da TV, sem deslocar o chrome. */
    await evaluate("document.querySelector('[data-action=section][data-section=MOVIES]').click(); true");
    await waitFor("BuroApp.state.section === 'MOVIES' && document.querySelectorAll('.media-card.poster').length >= 1");
    var movieCategories = await geometry(['.buro-ribbon', '.topbar', '.catalogue-scope-bar', '.catalogue-year-bar', '.media-card.poster']);
    process.stdout.write('Filmes e catálogo\n');
    check('aba Filmes exibe escopo e prateleira dentro da área da TV', movieCategories.rectangles.every(rectVisible));
    check('aba Filmes preserva o chrome fixo e sem overflow global', movieCategories.scrollWidth <= 1920 && movieCategories.scrollHeight <= 1080);
    check('prateleira de filmes produz um quadro PNG não vazio', await screenshotIsRendered('movie-categories'));

    /*
      Cada seletor abre debaixo do proprio chip, e a janela cabe na tela.

      O relato foi "seletor nao abre onde eu click": a lista era um bloco solto
      depois das duas barras, entao clicar em "Nota", na direita, abria a janela
      debaixo de "Genero", na esquerda. Aqui isso e medido em pixels a 1920x1080,
      que e a unica forma de provar que ela nasce no lugar certo e nao vaza pela
      borda direita.
    */
    await evaluate("document.querySelector('[data-action=catalogue-pick-rating]').click(); true");
    await waitFor("document.querySelector('.catalogue-options')");
    var ratingPicker = await evaluate("(function(){"
        + "var list=document.querySelector('.catalogue-options');"
        + "var chip=document.querySelector('[data-action=catalogue-pick-rating]');"
        + "var l=list.getBoundingClientRect(), c=chip.getBoundingClientRect();"
        + "return { listLeft:Math.round(l.left), listRight:Math.round(l.right), listTop:Math.round(l.top),"
        + " chipLeft:Math.round(c.left), chipBottom:Math.round(c.bottom), lists:document.querySelectorAll('.catalogue-options').length };"
        + "}())");
    check('a lista de Nota nasce alinhada ao chip de Nota, e nao a barra',
        Math.abs(ratingPicker.listLeft - ratingPicker.chipLeft) <= 24);
    check('e abre logo abaixo dele, nao noutra faixa da tela',
        ratingPicker.listTop >= ratingPicker.chipBottom &&
        ratingPicker.listTop - ratingPicker.chipBottom <= 40);
    check('a janela nao vaza pela borda direita da TV',
        ratingPicker.listRight <= 1920);
    check('uma janela por vez', ratingPicker.lists === 1);
    await evaluate("document.querySelector('[data-action=catalogue-pick-rating]').click(); true");
    await waitFor("!document.querySelector('.catalogue-options')");

    /* A categoria continua alcançável; na prateleira ela é filtro, então o
       teste abre pelo mesmo caminho que o resto do app usa. */
    await evaluate("BuroApp._openCategory('visual-movies'); true");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'category' && document.querySelectorAll('.media-card.poster').length === 6");
    var movieCatalogue = await geometry(['.buro-ribbon', '.topbar', '.catalogue-filter-bar', '.catalogue-result-count', '.media-card.poster']);
    var movieCatalogueData = await evaluate("({ count:document.querySelectorAll('.media-card.poster').length, art:document.querySelectorAll('.media-card.poster.has-art').length, text:document.querySelector('.catalogue-result-count').textContent, mainScroll:document.querySelector('.main-pane').scrollTop })");
    check('categoria real mostra seis pôsteres, contador e filtros', movieCatalogueData.count === 6 && movieCatalogueData.art === 6 && /6 \/ 6/.test(movieCatalogueData.text));
    check('filtros e primeiro pôster ficam visíveis sem rolar o painel principal', rectVisible(movieCatalogue.rectangles[2]) && rectVisible(movieCatalogue.rectangles[3]) && rectVisible(movieCatalogue.rectangles[4]) && movieCatalogueData.mainScroll === 0);
    check('catálogo de filmes produz um quadro PNG não vazio', await screenshotIsRendered('movie-catalogue'));

    await evaluate("document.querySelector('[data-action=movie-details][data-id=movie-aurora]').click(); true");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'movie' && document.querySelector('.detail-hero')");
    var movieDetail = await geometry(['.buro-ribbon', '.topbar', '.detail-hero', '.detail-actions', '[data-action=tmdb-settings]']);
    var movieDetailData = await evaluate("({ title:document.querySelector('.detail-hero h2').textContent, mainScroll:document.querySelector('.main-pane').scrollTop, play:document.querySelectorAll('.detail-actions [data-action=play]').length })");
    process.stdout.write('Detalhes de filme e série\n');
    check('detalhe de filme abre o título correto com ação principal', movieDetailData.title === 'Aurora de Vidro' && movieDetailData.play === 1);
    check('detalhe de filme preserva chrome e conteúdo inicial visíveis', movieDetail.rectangles.every(rectVisible) && movieDetailData.mainScroll === 0);
    check('detalhe de filme produz um quadro PNG não vazio', await screenshotIsRendered('movie-detail'));

    var episodeCount = await evaluate('(' + showSyntheticSeriesDetail.toString() + ')()');
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'series' && document.querySelector('.season-header')");
    var seriesTop = await geometry(['.buro-ribbon', '.topbar', '.detail-hero', '.detail-actions']);
    var seriesData = await evaluate("({ title:document.querySelector('.detail-hero h2').textContent, seasons:document.querySelectorAll('.season-header').length, episodes:document.querySelectorAll('.episode-download-item .media-card').length, mainScroll:document.querySelector('.main-pane').scrollTop })");
    check('detalhe de série reúne metadados, temporada e quatro episódios', episodeCount === 4 && seriesData.title === 'Horizonte Norte' && seriesData.seasons === 1 && seriesData.episodes === 4);
    check('topo da série preserva Ribbon, topbar e ações visíveis', seriesTop.rectangles.every(rectVisible) && seriesData.mainScroll === 0);
    check('detalhe de série produz um quadro PNG não vazio', await screenshotIsRendered('series-detail'));
    var seriesEpisodes = await evaluate("(function(){ var content=document.querySelector('.content.scrollable'); var season=document.querySelector('.season-header'); content.scrollTop=Math.max(0, season.offsetTop-170); var r=season.getBoundingClientRect(); return { ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top, topbar:document.querySelector('.topbar').getBoundingClientRect().top, season:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height}, scroll:content.scrollTop }; }())");
    check('rolagem da série revela episódios sem mover o chrome da aplicação', seriesEpisodes.scroll > 0 && rectVisible(seriesEpisodes.season) && seriesEpisodes.ribbon === 0 && seriesEpisodes.topbar >= 0);
    check('lista de episódios produz um quadro PNG não vazio', await screenshotIsRendered('series-episodes'));

    await evaluate("document.querySelector('[data-action=section][data-section=SETTINGS]').click(); true");
    await waitFor("BuroApp.state.section === 'SETTINGS' && document.querySelector('.settings-about-card')");
    var settings = await geometry(['.buro-ribbon', '.topbar', '.settings-about-card', '.setting-card']);
    var settingsData = await evaluate("({ cards:document.querySelectorAll('.setting-card').length, languages:document.querySelectorAll('.settings-language-option').length, profiles:document.querySelectorAll('.settings-active-profile').length, mainScroll:document.querySelector('.main-pane').scrollTop, contentScroll:document.querySelector('.content.scrollable').scrollTop })");
    process.stdout.write('Configurações\n');
    check('Configurações mostra cartões, cinco idiomas e perfil ativo', settingsData.cards >= 7 && settingsData.languages === 5 && settingsData.profiles === 1);
    check('topo de Configurações mantém chrome, resumo e primeiro cartão visíveis', settings.rectangles.every(rectVisible) && settingsData.mainScroll === 0 && settingsData.contentScroll === 0);
    check('Configurações produz um quadro PNG não vazio', await screenshotIsRendered('settings'));
    var settingsLower = await evaluate("(function(){ var content=document.querySelector('.content.scrollable'); var target=document.querySelector('.settings-language-list'); content.scrollTop=Math.max(0,target.offsetTop-180); var r=target.getBoundingClientRect(); return { scroll:content.scrollTop, language:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height}, ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top }; }())");
    check('rolagem de Configurações alcança idiomas sem deslocar a Ribbon', settingsLower.scroll > 0 && rectVisible(settingsLower.language) && settingsLower.ribbon === 0);
    check('idiomas de Configurações produzem um quadro PNG não vazio', await screenshotIsRendered('settings-languages'));

    await evaluate("document.querySelector('[data-action=section][data-section=LIVE]').click(); true");
    await waitFor("BuroApp.state.section === 'LIVE' && document.querySelector('.media-card')");
    var liveCategories = await geometry(['.buro-ribbon', '.topbar', '.catalogue-scope-bar', '.media-card']);
    process.stdout.write('Ao Vivo\n');
    check('Ao Vivo mostra escopo e canais dentro da área da TV', liveCategories.rectangles.every(rectVisible));
    check('categorias Ao Vivo produzem um quadro PNG válido', await screenshotIsRendered('live-categories'));
    var liveId = await evaluate('(' + showSyntheticLiveDetail.toString() + ')()');
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'live' && document.querySelector('.live-detail')");
    var liveDetail = await geometry(['.buro-ribbon', '.topbar', '.live-detail', '.live-now', '.epg-list']);
    var liveData = await evaluate("({ title:document.querySelector('.live-detail h2').textContent, rows:document.querySelectorAll('.epg-row').length, current:document.querySelectorAll('.epg-row.current').length, mainScroll:document.querySelector('.main-pane').scrollTop })");
    check('detalhe Ao Vivo identifica canal, programa atual e três linhas de EPG', liveId === 'live-studio' && liveData.title === 'Estúdio Norte' && liveData.rows === 3 && liveData.current === 1);
    check('detalhe Ao Vivo preserva chrome e conteúdo inicial sem rolar o painel principal', liveDetail.rectangles.slice(0, 4).every(rectVisible) && liveData.mainScroll === 0);
    check('detalhe Ao Vivo produz um quadro PNG válido', await screenshotIsRendered('live-detail'));

    await evaluate("document.querySelector('[data-action=section][data-section=DISCOVER]').click(); true");
    await waitFor("BuroApp.state.section === 'DISCOVER' && BuroApp.state.screenData && BuroApp.state.screenData.kind === 'discover' && !BuroApp.state.screenData.loading && document.querySelector('.discover-card.current')", 10000);
    var discover = await geometry(['.buro-ribbon', '.topbar', '.discover-intro', '.discover-card.current', '.discover-actions']);
    var discoverData = await evaluate("(function(){ var next=document.querySelector('.discover-card.next'); var copy=next&&next.querySelector('.discover-card-copy'); return { cards:document.querySelectorAll('.discover-card').length, actions:document.querySelectorAll('.discover-action').length, counter:document.querySelector('.discover-counter').textContent, mainScroll:document.querySelector('.main-pane').scrollTop, nextHidden:!next||(next.getAttribute('aria-hidden')==='true'&&getComputedStyle(copy).visibility==='hidden') }; }())");
    process.stdout.write('Descobrir e Pesquisa\n');
    check('Descobrir desenha somente carta atual/próxima e três decisões', discoverData.cards === 2 && discoverData.actions === 3 && /1/.test(discoverData.counter));
    check('a carta seguinte preserva profundidade sem vazar título ou metadados', discoverData.nextHidden);
    check('Descobrir mantém composição e chrome visíveis sem rolagem indevida', discover.rectangles.every(rectVisible) && discoverData.mainScroll === 0);
    check('Descobrir produz um quadro PNG válido', await screenshotIsRendered('discover'));

    await evaluate("document.querySelector('[data-action=section][data-section=SEARCH]').click(); true");
    await waitFor("BuroApp.state.section === 'SEARCH' && document.querySelector('#search-query')");
    await evaluate("(function(){ var input=document.querySelector('#search-query'); input.value='Aurora'; input.dispatchEvent(new Event('input',{bubbles:true})); return true; }())");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind === 'search' && !BuroApp.state.screenData.searching && document.querySelectorAll('.media-card').length === 1", 5000);
    var search = await geometry(['.buro-ribbon', '.topbar', '.form-panel', '#search-query', '.media-card']);
    var searchData = await evaluate("({ value:document.querySelector('#search-query').value, title:document.querySelector('.media-card h3').textContent, mainScroll:document.querySelector('.main-pane').scrollTop })");
    check('Pesquisa automática encontra Aurora sem botão intermediário', searchData.value === 'Aurora' && searchData.title === 'Aurora de Vidro');
    check('Pesquisa mantém formulário, resultado e chrome visíveis', search.rectangles.every(rectVisible) && searchData.mainScroll === 0);
    check('Pesquisa produz um quadro PNG válido', await screenshotIsRendered('search-results'));

    var libraries = await evaluate('(' + prepareVisualLibraries.toString() + ')()');
    check('bibliotecas sintéticas preparam favoritos, progresso, lembretes e avisos', libraries.favorites === 3 && libraries.progress === 3 && libraries.reminders === 2 && libraries.notifications === 2);
    await evaluate("document.querySelector('[data-action=section][data-section=MY_BURO]').click(); true");
    await waitFor("BuroApp.state.section === 'MY_BURO' && document.querySelectorAll('.media-card').length === 3");
    var myBuro = await geometry(['.buro-ribbon', '.topbar', '.library-filter-bar', '.media-card']);
    check('Minha BURO mostra filtros e três tipos favoritos dentro do quadro', myBuro.rectangles.every(rectVisible) && await evaluate("document.querySelectorAll('.library-filter-bar .filter-chip').length === 4"));
    check('Minha BURO produz um quadro PNG válido', await screenshotIsRendered('my-buro'));

    await evaluate("document.querySelector('[data-action=section][data-section=CONTINUE_WATCHING]').click(); true");
    await waitFor("BuroApp.state.section === 'CONTINUE_WATCHING' && document.querySelectorAll('.media-card').length === 2");
    var continuing = await geometry(['.buro-ribbon', '.topbar', '.library-filter-bar', '.media-card', '.media-progress']);
    check('Continuar assistindo mostra dois títulos incompletos com barras visíveis', continuing.rectangles.every(rectVisible));
    check('Continuar assistindo produz um quadro PNG válido', await screenshotIsRendered('continue-watching'));

    await evaluate("document.querySelector('[data-action=section][data-section=HISTORY]').click(); true");
    await waitFor("BuroApp.state.section === 'HISTORY' && document.querySelectorAll('.media-card').length === 3");
    var history = await geometry(['.buro-ribbon', '.topbar', '.library-filter-bar', '.media-card']);
    check('Histórico reúne incompletos e concluído com filtro por tipo', history.rectangles.every(rectVisible));
    check('Histórico produz um quadro PNG válido', await screenshotIsRendered('history'));

    await evaluate("document.querySelector('[data-action=section][data-section=REMINDERS]').click(); true");
    await waitFor("BuroApp.state.section === 'REMINDERS' && document.querySelectorAll('.reminder-row').length === 2");
    var reminders = await geometry(['.buro-ribbon', '.topbar', '.profile-help', '.reminder-row', '.reminders-notice-hint']);
    check('Lembretes apresenta duas linhas e explica a limitação de segundo plano', reminders.rectangles.every(rectVisible));
    check('Lembretes produz um quadro PNG válido', await screenshotIsRendered('reminders'));

    await evaluate("document.querySelector('[data-action=notifications]').click(); true");
    await waitFor("BuroApp.state.screen === 'NOTIFICATIONS' && document.querySelectorAll('.notice-row').length === 2");
    var notices = await geometry(['.buro-ribbon', '.topbar', '.notice-list', '.notice-row', '[data-action=notifications-clear]']);
    var noticeData = await evaluate("({ rows:document.querySelectorAll('.notice-row').length, unread:document.querySelectorAll('.notice-row.unread').length, badge:document.querySelectorAll('.bell-badge').length })");
    check('abrir Avisos mostra duas linhas, marca como lidas e remove o badge', noticeData.rows === 2 && noticeData.unread === 0 && noticeData.badge === 0);
    check('central de Avisos permanece inteira no quadro', notices.rectangles.every(rectVisible));
    check('central de Avisos produz um quadro PNG válido', await screenshotIsRendered('notifications'));

    await evaluate("document.querySelector('[data-action=section][data-section=SOURCES]').click(); true");
    await waitFor("BuroApp.state.section === 'SOURCES' && document.querySelector('.source-entry')");
    var sources = await geometry(['.buro-ribbon', '.topbar', '.source-entry', '.source-card', '.source-manage']);
    process.stdout.write('Fontes e Perfis\n');
    check('Fontes mostra seleção, gerenciamento e adição dentro do quadro', sources.rectangles.every(rectVisible) && await evaluate("document.querySelectorAll('.source-card').length === 2"));
    check('Fontes produz um quadro PNG válido', await screenshotIsRendered('sources'));

    await evaluate("document.querySelector('[data-action=section][data-section=PROFILES]').click(); true");
    await waitFor("BuroApp.state.section === 'PROFILES' && document.querySelector('.profile-row')");
    var profilesManagement = await geometry(['.buro-ribbon', '.topbar', '.profile-help', '.profile-card', '.profile-edit']);
    check('Perfis mostra perfil, edição e adição dentro do quadro', profilesManagement.rectangles.every(rectVisible) && await evaluate("document.querySelectorAll('.profile-card').length === 2"));
    check('Perfis produz um quadro PNG válido', await screenshotIsRendered('profiles'));

    await evaluate("document.querySelector('[data-action=profile-edit]').click(); true");
    await waitFor("BuroApp.state.screen === 'PROFILE_FORM' && document.querySelector('.profile-form-panel')");
    var profileForm = await geometry(['.profile-form-panel', '#profile-name', '.profile-photo-controls', '.avatar-choice-row', '.source-choice-row']);
    check('edição de perfil expõe nome, foto, avatares e fonte sem recorte inicial', profileForm.rectangles.every(rectVisible));
    check('edição de perfil mantém cinco avatares e a fonte sintética', await evaluate("document.querySelectorAll('.avatar-choice').length === 5 && document.querySelectorAll('.source-choice').length === 2"));
    check('edição de perfil produz um quadro PNG válido', await screenshotIsRendered('profile-form'));

    await evaluate("BuroApp.state.screen='SHELL'; BuroApp.state.section='SOURCES'; BuroApp.state.screenData=null; BuroApp.render(); true");
    await waitFor("document.querySelector('[data-action=source-manage]')");
    await evaluate("document.querySelector('[data-action=source-manage]').click(); true");
    await waitFor("BuroApp.state.screen === 'SOURCE_MANAGE' && document.querySelector('.source-manage-panel')");
    var sourceManage = await geometry(['.buro-ribbon', '.topbar', '.source-manage-panel', '.source-summary', '#source-manage-name']);
    check('gerenciamento de fonte mantém resumo, nome e chrome no quadro', sourceManage.rectangles.every(rectVisible));
    check('gerenciamento de fonte produz um quadro PNG válido', await screenshotIsRendered('source-manage'));

    await evaluate("BuroApp.state.screen='SHELL'; BuroApp.state.section='SOURCES'; BuroApp.state.screenData=null; BuroApp.render(); document.querySelector('[data-action=source-add]').click(); true");
    await waitFor("BuroApp.state.screen === 'SOURCE_CHOICE' && document.querySelector('.choice-row')");
    var sourceChoice = await geometry(['.buro-ribbon', '.topbar', '.choice-row', '[data-type=XTREAM]', '[data-type=REMOTE_M3U]', '[data-type=STALKER]']);
    check('nova fonte oferece Xtream, M3U remoto e Stalker no quadro', sourceChoice.rectangles.every(rectVisible));
    check('escolha de fonte produz um quadro PNG válido', await screenshotIsRendered('source-choice'));
    await evaluate("document.querySelector('[data-action=source-form][data-type=XTREAM]').click(); true");
    await waitFor("BuroApp.state.screen === 'SOURCE_FORM' && document.querySelector('#source-server')");
    var xtreamForm = await geometry(['.buro-ribbon', '.topbar', '.form-panel', '#source-name', '#source-server', '#source-username', '#source-password']);
    check('formulário Xtream mostra somente os quatro campos esperados e chrome visível', xtreamForm.rectangles.every(rectVisible));
    check('formulário Xtream produz um quadro PNG válido', await screenshotIsRendered('source-xtream'));

    process.stdout.write('Configurações profundas\n');
    await evaluate("BuroApp.state.screen='PARENTAL_FORM'; BuroApp.state.section='SETTINGS'; BuroApp.state.screenData={}; BuroApp.render(); true");
    await waitFor("document.querySelector('[data-action=parental-save]')");
    var parental = await geometry(['.buro-ribbon', '.topbar', '.form-panel', '#new-pin', '[data-action=parental-save]']);
    check('PIN parental mantém explicação, campo e ação dentro do quadro', parental.rectangles.every(rectVisible));
    check('PIN parental produz um quadro PNG válido', await screenshotIsRendered('parental-pin'));

    await evaluate("BuroApp.state.screen='CATEGORY_SETTINGS'; BuroApp.state.screenData={}; BuroApp.render(); true");
    await waitFor("document.querySelectorAll('.guard-row').length === 4");
    var categoriesSettings = await geometry(['.buro-ribbon', '.topbar', '.guard-list', '.guard-row', '.guard-row .action-row']);
    check('controle de categorias mostra quatro categorias e ações sem recorte inicial', categoriesSettings.rectangles.every(rectVisible));
    check('controle de categorias produz um quadro PNG válido', await screenshotIsRendered('category-settings'));

    await evaluate("BuroApp.state.screen='TMDB_SETTINGS'; BuroApp.state.screenData={}; BuroApp.render(); true");
    await waitFor("document.querySelectorAll('.tmdb-key-scope').length === 2");
    var tmdbSettings = await geometry(['.buro-ribbon', '.topbar', '.tmdb-guide-action', '.tmdb-key-scope']);
    check('TMDb mostra guia e primeiro escopo de chave dentro do quadro inicial', tmdbSettings.rectangles.every(rectVisible) && await evaluate("document.querySelectorAll('.tmdb-key-scope').length === 2"));
    check('configuração TMDb produz um quadro PNG válido', await screenshotIsRendered('tmdb-settings'));
    var tmdbProfileScope = await evaluate("(function(){var content=document.querySelector('.content.scrollable');var target=document.querySelectorAll('.tmdb-key-scope')[1];content.scrollTop=Math.max(0,target.offsetTop-150);var r=target.getBoundingClientRect();return {scroll:content.scrollTop,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top};}())");
    check('rolagem TMDb revela a chave do perfil sem mover a Ribbon', tmdbProfileScope.scroll > 0 && rectVisible(tmdbProfileScope.rect) && tmdbProfileScope.ribbon === 0);
    check('escopo de perfil TMDb produz um quadro PNG válido', await screenshotIsRendered('tmdb-settings-profile'));
    await evaluate("document.querySelector('.content.scrollable').scrollTop=0; true");
    await evaluate("document.querySelector('[data-action=tmdb-guide]').click(); true");
    await waitFor("BuroApp.state.screen === 'TMDB_GUIDE' && document.querySelectorAll('.tmdb-guide-step').length === 4");
    var tmdbGuide = await geometry(['.buro-ribbon', '.topbar', '.tmdb-guide-step']);
    check('guia TMDb apresenta quatro passos e mantém o primeiro legível', tmdbGuide.rectangles.every(rectVisible));
    check('guia TMDb produz um quadro PNG válido', await screenshotIsRendered('tmdb-guide'));
    var tmdbGuideLast = await evaluate("(function(){var content=document.querySelector('.content.scrollable');var steps=document.querySelectorAll('.tmdb-guide-step');var target=steps[steps.length-1];content.scrollTop=Math.max(0,target.offsetTop-150);var r=target.getBoundingClientRect();return {scroll:content.scrollTop,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top};}())");
    check('rolagem do guia TMDb revela o quarto passo sem mover a Ribbon', tmdbGuideLast.scroll > 0 && rectVisible(tmdbGuideLast.rect) && tmdbGuideLast.ribbon === 0);
    var tmdbGuideChrome = await geometry(['.buro-ribbon', '.topbar']);
    check('fim do guia TMDb conserva Ribbon e topbar inteiras no viewport', tmdbGuideChrome.rectangles.every(rectVisible) && await evaluate("window.scrollY === 0 && document.querySelector('.main-pane').scrollTop === 0"));
    check('fim do guia TMDb produz um quadro PNG válido', await screenshotIsRendered('tmdb-guide-end'));

    await evaluate("BuroApp.state.screen='CRITICS_SETTINGS'; BuroApp.state.screenData={}; BuroApp.render(); true");
    await waitFor("document.querySelector('#critics-key')");
    var criticsSettings = await geometry(['.buro-ribbon', '.topbar', '.tmdb-settings-panel', '.tmdb-guide-action', '#critics-key']);
    check('OMDb mostra explicação, guia e campo seguro dentro do quadro', criticsSettings.rectangles.every(rectVisible));
    check('configuração OMDb produz um quadro PNG válido', await screenshotIsRendered('omdb-settings'));
    await evaluate("document.querySelector('[data-action=critics-guide]').click(); true");
    await waitFor("BuroApp.state.screen === 'CRITICS_GUIDE' && document.querySelectorAll('.critics-guide-step').length === 4");
    var criticsGuide = await geometry(['.buro-ribbon', '.topbar', '.critics-guide-step']);
    check('guia OMDb apresenta quatro passos e mantém o primeiro legível', criticsGuide.rectangles.every(rectVisible));
    check('guia OMDb produz um quadro PNG válido', await screenshotIsRendered('omdb-guide'));
    var criticsGuideLast = await evaluate("(function(){var content=document.querySelector('.content.scrollable');var steps=document.querySelectorAll('.critics-guide-step');var target=steps[steps.length-1];content.scrollTop=Math.max(0,target.offsetTop-150);var r=target.getBoundingClientRect();return {scroll:content.scrollTop,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top};}())");
    check('rolagem do guia OMDb revela o quarto passo sem mover a Ribbon', criticsGuideLast.scroll > 0 && rectVisible(criticsGuideLast.rect) && criticsGuideLast.ribbon === 0);
    var criticsGuideChrome = await geometry(['.buro-ribbon', '.topbar']);
    check('fim do guia OMDb conserva Ribbon e topbar inteiras no viewport', criticsGuideChrome.rectangles.every(rectVisible) && await evaluate("window.scrollY === 0 && document.querySelector('.main-pane').scrollTop === 0"));
    check('fim do guia OMDb produz um quadro PNG válido', await screenshotIsRendered('omdb-guide-end'));

    await evaluate("BuroApp.state.screen='STORAGE_SETTINGS'; BuroApp.state.screenData={counts:{items:18,categories:4},measuring:false}; BuroApp.render(); true");
    await waitFor("document.querySelectorAll('.storage-row').length === 3");
    var storageSettings = await geometry(['.buro-ribbon', '.topbar', '.tmdb-settings-panel', '.storage-list', '.storage-row', '[data-action=storage-clear]']);
    check('armazenamento mostra catálogo, arte, downloads e limpeza no quadro', storageSettings.rectangles.every(rectVisible));
    check('armazenamento produz um quadro PNG válido', await screenshotIsRendered('storage-settings'));

    var downloadCount = await evaluate('(' + showSyntheticDownloads.toString() + ')()');
    await waitFor("BuroApp.state.section === 'DOWNLOADS' && document.querySelectorAll('.download-row').length === 3");
    var downloads = await geometry(['.buro-ribbon', '.topbar', '.download-filter-bar', '.download-search', '.download-row', '.download-actions']);
    var downloadData = await evaluate("({ rows:document.querySelectorAll('.download-row').length, filters:document.querySelectorAll('.download-filter-bar .filter-chip').length, tracks:document.querySelectorAll('.download-track').length, rate:document.querySelector('.download-row small').textContent })");
    process.stdout.write('Downloads, Assinaturas e conteúdo público\n');
    check('Downloads mostra três estados, filtros de tipo, busca e telemetria', downloadCount === 3 && downloadData.rows === 3 && downloadData.filters === 4 && downloadData.tracks === 3 && /MB\/s/.test(downloadData.rate));
    check('Downloads mantém toolbar, busca e primeira linha dentro do quadro', downloads.rectangles.every(rectVisible));
    check('Downloads produz um quadro PNG válido', await screenshotIsRendered('downloads'));

    var subscriptionCount = await evaluate('(' + showSyntheticSubscriptions.toString() + ')()');
    await waitFor("BuroApp.state.section === 'SUBSCRIPTIONS' && document.querySelectorAll('.subscription-shelves > section').length === 2");
    var subscriptions = await geometry(['.buro-ribbon', '.topbar', '.subscriptions-header', '.subscription-row', '.subscription-poster']);
    check('Assinaturas mostra dois serviços, filtros e quatro títulos sintéticos', subscriptionCount === 4 && await evaluate("document.querySelectorAll('.subscription-poster').length === 8 && document.querySelectorAll('.subscription-see-more').length === 2"));
    check('Assinaturas mantém cabeçalho e primeira prateleira dentro do quadro', subscriptions.rectangles.every(rectVisible));
    check('Assinaturas produz um quadro PNG válido', await screenshotIsRendered('subscriptions'));

    var offerCount = await evaluate('(' + showSyntheticSubscriptionDetail.toString() + ')()');
    await waitFor("document.querySelector('.subscription-detail') && document.querySelectorAll('.subscription-offer').length === 2");
    var subscriptionDetail = await geometry(['.buro-ribbon', '.topbar', '.subscription-title-head', '.subscription-offers', '.subscription-offer']);
    check('detalhe de Assinaturas reúne biblioteca local e oferta atribuída', offerCount === 2 && await evaluate("document.querySelectorAll('.subscription-offer').length === 2 && /JustWatch/.test(document.querySelector('.subscription-offers').textContent)"));
    check('detalhe de Assinaturas mantém título e ofertas dentro do quadro', subscriptionDetail.rectangles.every(rectVisible));
    check('detalhe de Assinaturas produz um quadro PNG válido', await screenshotIsRendered('subscription-detail'));

    var creditCount = await evaluate('(' + showSyntheticPerson.toString() + ')()');
    await waitFor("BuroApp.state.screen === 'PERSON' && document.querySelectorAll('.person-credit').length === 2");
    var person = await geometry(['.buro-ribbon', '.topbar', '.person-page', '.person-header', '.person-biography', '.person-credit']);
    check('pessoa mostra biografia e dois créditos com arte sintética', creditCount === 2 && person.rectangles.every(rectVisible));
    check('pessoa produz um quadro PNG válido', await screenshotIsRendered('person'));

    var qrReady = await evaluate('(' + showSyntheticShare.toString() + ')()');
    await waitFor("BuroApp.state.screen === 'SHARE' && document.querySelector('.share-page')");
    var share = await geometry(['.buro-ribbon', '.topbar', '.share-page', '.share-copy', '.share-code', '.share-qr']);
    check('Compartilhar gera QR local e mantém todo o conteúdo público no quadro', qrReady && share.rectangles.every(rectVisible));
    check('Compartilhar limita o link aos campos públicos do contrato', await evaluate("(function(){var value=document.querySelector('[data-share-url]').getAttribute('data-share-url');var url=new URL(value);var allowed={id:true,t:true,y:true,img:true,d:true};return url.protocol==='https:'&&url.hostname==='iptvburo.pages.dev'&&Array.prototype.every.call(url.searchParams.keys(),function(key){return allowed[key]===true;});}())"));
    check('Compartilhar produz um quadro PNG válido', await screenshotIsRendered('share'));

    process.stdout.write('Fluxos restantes de fonte e confirmação\n');
    await evaluate("BuroApp.state.screen='SOURCE_FORM'; BuroApp.state.section='SOURCES'; BuroApp.state.screenData={type:'REMOTE_M3U'}; BuroApp.render(); true");
    await waitFor("document.querySelector('#source-playlist')");
    var remoteM3u = await geometry(['.buro-ribbon', '.topbar', '.form-panel', '#source-name', '#source-playlist', '[data-action=source-connect]']);
    check('formulário M3U remoto mantém campos, ação e chrome no quadro', remoteM3u.rectangles.every(rectVisible));
    check('formulário M3U remoto produz um quadro PNG válido', await screenshotIsRendered('source-m3u'));

    await evaluate("BuroApp.state.screenData={type:'STALKER'}; BuroApp.render(); true");
    await waitFor("document.querySelector('#source-portal') && document.querySelector('#source-mac')");
    var stalkerForm = await geometry(['.buro-ribbon', '.topbar', '.form-panel h2', '#source-name', '.form-note', '#source-portal', '#source-mac']);
    check('formulário Stalker apresenta portal, MAC e aviso inicial sem recorte', stalkerForm.rectangles.every(rectVisible));
    check('topo do formulário Stalker produz um quadro PNG válido', await screenshotIsRendered('source-stalker-top'));
    var stalkerEnd = await evaluate("(function(){var content=document.querySelector('.content.scrollable');var target=document.querySelector('[data-action=source-connect]');var heading=document.querySelector('.form-optional h3').getBoundingClientRect();var copy=document.querySelector('.form-optional p').getBoundingClientRect();content.scrollTop=Math.max(0,target.offsetTop-650);var r=target.getBoundingClientRect();heading=document.querySelector('.form-optional h3').getBoundingClientRect();copy=document.querySelector('.form-optional p').getBoundingClientRect();return {scroll:content.scrollTop,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},heading:{left:heading.left,top:heading.top,right:heading.right,bottom:heading.bottom,width:heading.width,height:heading.height},copy:{left:copy.left,top:copy.top,right:copy.right,bottom:copy.bottom,width:copy.width,height:copy.height},ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top};}())");
    check('rolagem Stalker alcança credenciais opcionais e Conectar sem mover a Ribbon', stalkerEnd.scroll > 0 && rectVisible(stalkerEnd.rect) && stalkerEnd.ribbon === 0);
    check('explicação opcional Stalker fica abaixo do título sem sobrepor texto', rectVisible(stalkerEnd.heading) && rectVisible(stalkerEnd.copy) && stalkerEnd.copy.top >= stalkerEnd.heading.bottom + 5);
    check('fim do formulário Stalker produz um quadro PNG válido', await screenshotIsRendered('source-stalker'));

    await evaluate("BuroApp.state.screen='SOURCE_USB_M3U'; BuroApp.state.screenData={loading:false,files:[{key:'usb-playlist-a',name:'Casa autorizada.m3u',size:18432},{key:'usb-playlist-b',name:'Canais da família.m3u8',size:32768}]}; BuroApp.render(); true");
    await waitFor("document.querySelectorAll('[data-action=source-usb-m3u-select]').length === 2");
    var usbM3u = await geometry(['.buro-ribbon', '.topbar', '.form-message', '.source-file-list', '.source-card']);
    check('seletor USB mostra somente duas playlists sintéticas e mantém o chrome', usbM3u.rectangles.every(rectVisible));
    check('seletor USB produz um quadro PNG válido', await screenshotIsRendered('source-usb-m3u'));

    await evaluate("BuroApp.state.screen='PROFILE_PHOTO_PICKER'; BuroApp.state.screenData={loading:false,images:[{key:'photo-a',name:'familia.jpg'},{key:'photo-b',name:'sala.png'},{key:'photo-c',name:'perfil.webp'}]}; BuroApp.render(); true");
    await waitFor("document.querySelectorAll('.profile-photo-choice').length === 3");
    var profilePhoto = await geometry(['.profile-photo-panel', '.profile-photo-grid', '.profile-photo-choice', '[data-action=back]']);
    check('seletor de foto USB mantém três opções e Voltar dentro da área segura', profilePhoto.rectangles.every(rectInside));
    check('seletor de foto USB produz um quadro PNG válido', await screenshotIsRendered('profile-photo-picker'));

    await evaluate("BuroApp.state.screen='RESUME_PROMPT'; BuroApp.state.screenData={itemId:'movie-atlas',positionMs:2520000}; BuroApp.render(); true");
    await waitFor("document.querySelector('.resume-panel')");
    var resumePrompt = await geometry(['.resume-panel', '.resume-art', '[data-action=resume-continue]', '[data-action=resume-restart]', '[data-action=back]']);
    check('decisão Continuar/Recomeçar mostra arte, tempo e três ações sem recorte', resumePrompt.rectangles.every(rectVisible) && await evaluate("/42:00/.test(document.querySelector('.resume-panel').textContent)"));
    check('decisão de retomada produz um quadro PNG válido', await screenshotIsRendered('resume-prompt'));

    await evaluate("BuroDownloads.enabled=function(){return true;}; BuroApp.state.screen='BULK_DOWNLOAD_CONFIRM'; BuroApp.state.screenData={title:'Horizonte Norte',season:1,items:[{id:'episode-a'},{id:'episode-b'},{id:'episode-c'}]}; BuroApp.render(); true");
    await waitFor("document.querySelector('[data-action=bulk-download-confirm]')");
    var bulkDownload = await geometry(['.resume-panel', '[data-action=bulk-download-confirm]', '[data-action=back]']);
    check('confirmação de temporada informa três episódios e mantém ações visíveis', bulkDownload.rectangles.every(rectVisible) && await evaluate("/3/.test(document.querySelector('.resume-panel').textContent)"));
    check('confirmação de download em lote produz um quadro PNG válido', await screenshotIsRendered('bulk-download-confirm'));

    await evaluate("BuroLicense.decide=function(){return {allowed:false,reason:'UNREGISTERED'};}; BuroLicense.deviceId=function(){return 'TV-7K2M-91QX';}; BuroApp.state.screen='LICENCE'; BuroApp.state.screenData={busy:false}; BuroApp.render(); true");
    await waitFor("document.querySelector('#licence-key')");
    var licence = await geometry(['.brand-mark', '.gate-copy', '.licence-device', '#licence-key', '[data-action=licence-redeem]', '[data-action=back]']);
    check('ativação de licença mantém código, orientação, chave e ações na área segura', licence.rectangles.every(rectInside) && await evaluate("document.querySelector('.licence-device strong').textContent === 'TV-7K2M-91QX'"));
    check('ativação de licença produz um quadro PNG válido', await screenshotIsRendered('licence'));

    process.stdout.write('Player, menus e trailer\n');
    await evaluate("document.querySelector('#av-player').style.display='none'; document.documentElement.style.background='#050608'; document.body.style.background='#050608'; true");
    var playerReady = await evaluate('(' + showSyntheticPlayer.toString() + ")('movie-aurora')");
    await waitFor("document.body.classList.contains('playing') && !document.querySelector('#player-overlay').hidden");
    var player = await geometry(['.player-topbar', '.player-controls', '.player-timeline', '.player-control-row', '.player-track-row']);
    check('player VOD mostra título, progresso e atalhos inteiros no viewport', playerReady && player.rectangles.every(rectVisible) && await evaluate("document.querySelector('#player-timeline').getAttribute('aria-valuenow') === '30'"));
    check('player VOD produz um quadro PNG válido', await screenshotIsRendered('player-vod'));

    var playerLocked = await evaluate("(function(){BuroApp._onKeyDown({keyCode:13,preventDefault:function(){}});return new Promise(function(resolve){setTimeout(function(){BuroApp._onKeyUp({keyCode:13,preventDefault:function(){}});resolve(document.querySelector('#player-overlay').classList.contains('controls-locked'));},950);});}())");
    var lockPanel = await geometry(['.player-lock-panel']);
    check('pressão longa bloqueia controles e mantém a orientação visível', playerLocked && lockPanel.rectangles.every(rectVisible));
    check('bloqueio do player produz um quadro PNG válido', await screenshotIsRendered('player-locked'));
    await evaluate("(function(){BuroApp._onKeyDown({keyCode:13,preventDefault:function(){}});return new Promise(function(resolve){setTimeout(function(){BuroApp._onKeyUp({keyCode:13,preventDefault:function(){}});resolve(!document.querySelector('#player-overlay').classList.contains('controls-locked'));},950);});}())");

    await evaluate("BuroApp._onKeyDown({keyCode:38,preventDefault:function(){}}); true");
    await waitFor("!document.querySelector('#player-menu').hidden && document.querySelectorAll('#player-menu [data-player-option]').length === 2");
    var audioMenu = await geometry(['.player-menu', '#player-menu-title', '.player-menu-option.focused']);
    check('menu de áudio mostra duas faixas e foco dentro do quadro', audioMenu.rectangles.every(rectVisible));
    check('menu de áudio produz um quadro PNG válido', await screenshotIsRendered('player-audio-menu'));
    await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); BuroApp._onKeyDown({keyCode:40,preventDefault:function(){}}); true");
    await waitFor("!document.querySelector('#player-menu').hidden && document.querySelectorAll('#player-menu [data-player-option]').length === 3");
    var subtitleMenu = await geometry(['.player-menu', '#player-menu-title', '.player-menu-option.focused']);
    check('menu de legendas inclui Desligadas e duas faixas sem recorte', subtitleMenu.rectangles.every(rectVisible));
    check('menu de legendas produz um quadro PNG válido', await screenshotIsRendered('player-subtitle-menu'));
    await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); BuroApp._onKeyDown({keyCode:405,preventDefault:function(){}}); true");
    await waitFor("!document.querySelector('#player-menu').hidden && document.querySelectorAll('#player-menu [data-player-option]').length === 2");
    var speedMenu = await geometry(['.player-menu', '#player-menu-title', '.player-menu-option.focused']);
    check('menu de velocidade apresenta somente 1× e 2× compatíveis com AVPlay', speedMenu.rectangles.every(rectVisible) && await evaluate("document.querySelector('#player-menu-options').textContent.indexOf('1×')>=0 && document.querySelector('#player-menu-options').textContent.indexOf('2×')>=0"));
    check('menu de velocidade produz um quadro PNG válido', await screenshotIsRendered('player-speed-menu'));
    await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); BuroApp._playbackFailed({code:'PLAYBACK_CONNECTION'}); true");
    await waitFor("!document.querySelector('#player-error-panel').hidden");
    var playerError = await geometry(['.player-error-panel', '#player-error-title', '#player-error-message', '#player-error-retry', '#player-error-back']);
    check('erro de player mantém causa, Tentar novamente e Voltar acessíveis', playerError.rectangles.every(rectVisible) && await evaluate("document.activeElement === document.querySelector('#player-error-retry')"));
    check('erro de player produz um quadro PNG válido', await screenshotIsRendered('player-error'));
    await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); true");

    var guideNow = Math.floor(Date.now() / 1000);
    await evaluate("(function(now){var item=BuroApp.state.items.filter(function(row){return row.id==='live-studio';})[0];BuroApp.state.screen='SHELL';BuroApp.state.section='LIVE';BuroApp.state.screenData={kind:'live',parent:item,schedule:[{title:'Programa encerrado',description:'Resumo anterior',startEpochSeconds:now-3600,endEpochSeconds:now-1800},{title:'Programa atual',description:'Resumo em exibição',startEpochSeconds:now-300,endEpochSeconds:now+900},{title:'Próximo programa',description:'Resumo seguinte',startEpochSeconds:now+900,endEpochSeconds:now+2700}]};BuroApp.render();return true;})(" + guideNow + ")");
    var livePlayerReady = await evaluate('(' + showSyntheticPlayer.toString() + ")('live-studio')");
    await waitFor("document.body.classList.contains('playing')");
    await evaluate("BuroApp._onKeyDown({keyCode:404,preventDefault:function(){}}); true");
    await waitFor("!document.querySelector('#player-menu').hidden && document.querySelector('#player-menu').classList.contains('guide')");
    var guideMenu = await geometry(['.player-menu.guide', '#player-menu-title', '.player-guide-option.current', '.player-guide-option.past']);
    check('player ao vivo mostra guia atual, passado e próximo dentro do quadro', livePlayerReady && guideMenu.rectangles.every(rectVisible));
    check('rodapé visual do guia identifica o canal e nunca exibe undefined', await evaluate("document.querySelector('#player-menu-hint').textContent.indexOf('Estúdio Norte')>=0 && document.querySelector('#player-menu-hint').textContent.indexOf('undefined')<0"));
    check('programa atual focado mantém fundo claro e texto escuro legível', await evaluate("(function(){var row=document.querySelector('.player-guide-option.current.focused');var style=getComputedStyle(row);return style.backgroundColor==='rgb(246, 247, 250)'&&style.color==='rgb(21, 23, 24)';}())"));
    check('guia do player ao vivo produz um quadro PNG válido', await screenshotIsRendered('player-live-guide'));
    await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}}); true");

    var trailerReady = await evaluate("(function(){var ok=BuroTrailer.open('dQw4w9WgXcQ','Trailer sintético',{title:'Trailer',loading:'Carregando trailer…',playing:'Reproduzindo',playingMuted:'Reproduzindo sem som',paused:'Pausado',ended:'Trailer encerrado',error:'Trailer indisponível',hint:'ENTER pausar · RETURN voltar'});document.querySelector('#trailer-frame').src='about:blank';return ok;}())");
    await waitFor("!document.querySelector('#trailer-overlay').hidden");
    var trailer = await geometry(['.trailer-topbar', '.trailer-controls', '.trailer-timeline', '.trailer-time', '#trailer-hint']);
    check('trailer incorporado mantém título, estado, timeline e controles no viewport', trailerReady && trailer.rectangles.every(rectVisible));
    check('trailer produz um quadro PNG válido', await screenshotIsRendered('trailer'));
    await evaluate("BuroTrailer.close(); true");

    /*
      As 58 rotas acima usam PT-BR para permitir comparação quadro a quadro.
      Isso não prova que textos maiores em alemão ou italiano continuam dentro
      dos mesmos componentes. A matriz abaixo usa exatamente o HTML/CSS de
      produção nas quatro superfícies mais sensíveis a expansão de texto.
    */
    process.stdout.write('Matriz visual multilíngue\n');
    var visualLanguages = [
        { tag: 'pt-BR', slug: 'pt-br' },
        { tag: 'en', slug: 'en' },
        { tag: 'de', slug: 'de' },
        { tag: 'it', slug: 'it' },
        { tag: 'es', slug: 'es' }
    ];
    for (var visualLanguageIndex = 0; visualLanguageIndex < visualLanguages.length; visualLanguageIndex += 1) {
        var visualLanguage = visualLanguages[visualLanguageIndex];
        var languageLiteral = JSON.stringify(visualLanguage.tag);

        await evaluate("(function(language){BuroApp.state.preferences.language=language;BuroI18n.setLanguage(language);BuroApp.state.screen='SHELL';BuroApp.state.section='SETTINGS';BuroApp.state.screenData=null;BuroApp.render();return true;})(" + languageLiteral + ")");
        await waitFor("document.querySelector('.settings-about-card') && document.documentElement.lang === " + languageLiteral);
        var multilingualSettings = await geometry(['.buro-ribbon', '.topbar', '.settings-about-card', '.setting-card']);
        check(visualLanguage.tag + ' mantém o topo de Configurações dentro do viewport',
            multilingualSettings.rectangles.every(rectVisible) && multilingualSettings.scrollWidth <= 1920 && multilingualSettings.scrollHeight <= 1080);
        check(visualLanguage.tag + ' aplica o título localizado de Configurações sem fallback cru',
            await evaluate("document.querySelector('.topbar h1').textContent===BuroI18n.t('settings') && BuroI18n.t('settings')!=='settings'"));
        check(visualLanguage.tag + ' gera PNG válido de Configurações',
            await screenshotIsRendered('locale-' + visualLanguage.slug + '-settings'));

        await evaluate("BuroApp.state.screen='SOURCE_FORM';BuroApp.state.section='SOURCES';BuroApp.state.screenData={type:'STALKER'};BuroApp.render();true");
        await waitFor("document.querySelector('.form-optional') && document.querySelector('[data-action=source-connect]')");
        var multilingualStalker = await evaluate("(function(){var content=document.querySelector('.content.scrollable');var target=document.querySelector('[data-action=source-connect]');content.scrollTop=Math.max(0,target.offsetTop-650);var optional=document.querySelector('.form-optional').getBoundingClientRect();var heading=document.querySelector('.form-optional h3').getBoundingClientRect();var copy=document.querySelector('.form-optional p').getBoundingClientRect();var connect=target.getBoundingClientRect();var topbar=document.querySelector('.topbar').getBoundingClientRect();var contentRect=content.getBoundingClientRect();var ribbonHit=document.elementFromPoint(160,300);var topbarHit=document.elementFromPoint(900,40);function box(r){return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height};}function inside(element,selector){while(element){if(element.matches&&element.matches(selector)){return true;}element=element.parentNode;}return false;}return {optional:box(optional),heading:box(heading),copy:box(copy),connect:box(connect),topbar:box(topbar),content:box(contentRect),scroll:content.scrollTop,windowY:Number(window.scrollY)||Number(document.documentElement.scrollTop)||Number(document.body.scrollTop)||0,ribbon:document.querySelector('.buro-ribbon').getBoundingClientRect().top,ribbonOwnsHit:inside(ribbonHit,'.buro-ribbon'),topbarOwnsHit:inside(topbarHit,'.topbar'),localized:document.querySelector('.form-optional h3').textContent===BuroI18n.t('stalkerOptional')};}())");
        check(visualLanguage.tag + ' empilha a orientação Stalker e alcança Conectar',
            multilingualStalker.scroll > 0 && multilingualStalker.ribbon === 0 &&
            multilingualStalker.copy.top >= multilingualStalker.heading.bottom + 5 &&
            rectVisible(multilingualStalker.optional) && rectVisible(multilingualStalker.connect));
        /* A navegacao passou para a lateral esquerda, como no aplicativo do
           Windows: a topbar comeca no topo da janela em vez de abaixo de uma
           faixa de 92px, e a ribbon e sondada a esquerda em vez de em cima. O
           que continua sendo verificado e o mesmo: rolar o conteudo nao move
           nem a navegacao nem a barra de titulo. */
        check(visualLanguage.tag + ' conserva Ribbon e topbar fixas ao rolar Stalker',
            multilingualStalker.windowY === 0 && multilingualStalker.ribbon === 0 &&
            multilingualStalker.topbar.top === 0 && multilingualStalker.topbar.bottom === 92 &&
            multilingualStalker.content.top === 92 && multilingualStalker.content.bottom === 1080 &&
            multilingualStalker.ribbonOwnsHit && multilingualStalker.topbarOwnsHit);
        check(visualLanguage.tag + ' mantém o texto Stalker realmente localizado', multilingualStalker.localized);
        check(visualLanguage.tag + ' gera PNG válido do formulário Stalker',
            await screenshotIsRendered('locale-' + visualLanguage.slug + '-stalker'));

        await evaluate("BuroApp.state.screen='RESUME_PROMPT';BuroApp.state.screenData={itemId:'movie-atlas',positionMs:2520000};BuroApp.render();true");
        await waitFor("document.querySelector('.resume-panel') && document.querySelector('[data-action=resume-continue]')");
        var multilingualResume = await geometry(['.resume-panel', '.resume-panel h1', '.resume-panel p', '[data-action=resume-continue]', '[data-action=resume-restart]', '[data-action=back]']);
        check(visualLanguage.tag + ' mantém retomada e três decisões inteiras na TV', multilingualResume.rectangles.every(rectVisible));
        check(visualLanguage.tag + ' gera PNG válido da retomada',
            await screenshotIsRendered('locale-' + visualLanguage.slug + '-resume'));

        await evaluate("BuroPlayer.stop();BuroApp.state.progress=BuroApp.state.progress.filter(function(row){return row.itemId!=='movie-aurora';});document.body.classList.remove('playing');document.querySelector('#av-player').style.display='none';document.documentElement.style.background='#050608';document.body.style.background='#050608';true");
        await evaluate('(' + showSyntheticPlayer.toString() + ")('movie-aurora')");
        await waitFor("document.body.classList.contains('playing')");
        await evaluate("BuroApp._playbackFailed({code:'PLAYBACK_CONNECTION'});true");
        await waitFor("!document.querySelector('#player-error-panel').hidden");
        var multilingualPlayerError = await geometry(['.player-error-panel', '#player-error-title', '#player-error-message', '#player-error-retry', '#player-error-back']);
        check(visualLanguage.tag + ' mantém erro e ações do player dentro do viewport', multilingualPlayerError.rectangles.every(rectVisible));
        check(visualLanguage.tag + ' localiza a falha do player sem chave exposta',
            await evaluate("document.querySelector('#player-error-title').textContent===BuroI18n.t('playbackErrorTitle') && document.querySelector('#player-error-message').textContent===BuroI18n.t('playbackConnectionError')"));
        check(visualLanguage.tag + ' gera PNG válido do erro de player',
            await screenshotIsRendered('locale-' + visualLanguage.slug + '-player-error'));
        await evaluate("BuroApp._onKeyDown({keyCode:10009,preventDefault:function(){}});true");
    }

    /*
      Uma captura bonita prova apenas um instante. Na TV, a mesma WebView fica
      aberta durante horas; telas recriadas, listeners destacados e nos DOM
      retidos aparecem para o usuario como foco lento e engasgos. Este ciclo
      usa o app de producao e o coletor do Chromium para tornar essa regressao
      mensuravel sem playlist, rede ou credencial.
    */
    process.stdout.write('Estabilidade de sessao prolongada no Chromium\n');
    await evaluate("BuroPlayer.stop();BuroTrailer.close();document.body.classList.remove('playing');BuroI18n.setLanguage('pt-BR');BuroApp.state.preferences.language='pt-BR';BuroApp.state.screen='SHELL';BuroApp.state.section='MOVIES';BuroApp.state.screenData=null;BuroApp.render();window.scrollTo(0,0);true");
    await delay(100);
    await send('HeapProfiler.collectGarbage');
    var beforeMetricsResult = await send('Performance.getMetrics');
    var beforeMetrics = {};
    (beforeMetricsResult.metrics || []).forEach(function (metric) { beforeMetrics[metric.name] = metric.value; });
    var beforeDomNodes = await evaluate("document.querySelectorAll('*').length");

    var endurance = await evaluate("(function(){var sections=['MOVIES','SERIES','LIVE','MY_BURO','CONTINUE_WATCHING','HISTORY','REMINDERS','DOWNLOADS','PROFILES','SOURCES','SETTINGS','SEARCH'];var languages=['pt-BR','en','de','it','es'];var started=performance.now();var index;for(index=0;index<960;index+=1){BuroApp.state.screen='SHELL';BuroApp.state.section=sections[index%sections.length];BuroApp.state.screenData=null;if(index%48===0){BuroApp.state.preferences.language=languages[(index/48)%languages.length];BuroI18n.setLanguage(BuroApp.state.preferences.language);}BuroApp.render();BuroApp._onKeyDown({keyCode:index%2?40:39,preventDefault:function(){}});if(index%31===0){BuroApp.state.screen='SOURCE_FORM';BuroApp.state.section='SOURCES';BuroApp.state.screenData={type:index%62===0?'STALKER':'REMOTE_M3U'};BuroApp.render();}if(index%37===0){BuroApp.state.screen='RESUME_PROMPT';BuroApp.state.screenData={itemId:'movie-atlas',positionMs:2520000};BuroApp.render();}}BuroApp.state.preferences.language='pt-BR';BuroI18n.setLanguage('pt-BR');BuroApp.state.screen='SHELL';BuroApp.state.section='MOVIES';BuroApp.state.screenData=null;BuroApp.render();window.scrollTo(0,0);var root=document.getElementById('app');return {elapsedMs:performance.now()-started,domNodes:document.querySelectorAll('*').length,shells:root.querySelectorAll('.shell').length,ribbons:root.querySelectorAll('.buro-ribbon').length,topbars:root.querySelectorAll('.topbar').length,focused:root.querySelectorAll('.focused').length,globalScroll:window.scrollY,rootTextHasUndefined:/\\b(undefined|null)\\b/.test(root.textContent),playing:document.body.classList.contains('playing'),playerHidden:document.getElementById('player-overlay').hidden,trailerHidden:document.getElementById('trailer-overlay').hidden};}())");
    await delay(100);
    await send('HeapProfiler.collectGarbage');
    var afterMetricsResult = await send('Performance.getMetrics');
    var afterMetrics = {};
    (afterMetricsResult.metrics || []).forEach(function (metric) { afterMetrics[metric.name] = metric.value; });
    var heapGrowth = (afterMetrics.JSHeapUsedSize || 0) - (beforeMetrics.JSHeapUsedSize || 0);
    var nodeGrowth = (afterMetrics.Nodes || 0) - (beforeMetrics.Nodes || 0);
    var listenerGrowth = (afterMetrics.JSEventListeners || 0) - (beforeMetrics.JSEventListeners || 0);
    var documentGrowth = (afterMetrics.Documents || 0) - (beforeMetrics.Documents || 0);
    process.stdout.write('  metricas: ' + Math.round(endurance.elapsedMs) + ' ms; DOM ' + beforeDomNodes +
        ' -> ' + endurance.domNodes + '; nos CDP ' + nodeGrowth + '; listeners ' + listenerGrowth +
        '; documentos ' + documentGrowth + '; heap ' + Math.round(heapGrowth / 1024) + ' KiB\n');

    check('960 trocas de tela terminam sem excecao JavaScript nao tratada', runtimeExceptions.length === 0);
    check('ciclo prolongado termina em menos de 30 segundos', endurance.elapsedMs < 30000);
    check('DOM final volta ao mesmo tamanho da rota Filmes', endurance.domNodes === beforeDomNodes);
    check('Chromium nao retem mais de 200 nos depois da coleta', nodeGrowth <= 200);
    check('listeners JavaScript permanecem limitados depois da coleta', listenerGrowth <= 12);
    check('nenhum documento extra fica retido depois da coleta', documentGrowth <= 0);
    check('heap coletado cresce menos de 8 MiB', heapGrowth <= 8 * 1024 * 1024);
    check('chrome estrutural continua unico apos o ciclo', endurance.shells === 1 && endurance.ribbons === 1 && endurance.topbars === 1);
    check('exatamente um alvo conserva o foco visual', endurance.focused === 1);
    check('sessao termina sem scroll global nem texto indefinido', endurance.globalScroll === 0 && !endurance.rootTextHasUndefined);
    check('player e trailer terminam fechados sem estado residual', !endurance.playing && endurance.playerHidden && endurance.trailerHidden);

    process.stdout.write('Catalogo de escala no Chromium\n');
    var scale = await evaluate('(' + seedScaleCatalogue.toString() + ')(12000)');
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind==='home' && !BuroApp.state.screenData.loading && document.querySelector('.real-home-hero')", 30000);
    var scaleHome = await evaluate("({elapsedMs:performance.now()-window.__scaleHomeStarted,stateItems:BuroApp.state.items.length,domCards:document.querySelectorAll('.media-card').length,rails:document.querySelectorAll('.home-rail-heading').length,shells:document.querySelectorAll('#app .shell').length,scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight})");
    process.stdout.write('  metricas: escrita ' + Math.round(scale.writeMs) + ' ms; ' + scale.ticks +
        ' ticks; maior intervalo ' + Math.round(scale.maximumTickGapMs) + ' ms; Home ' +
        Math.round(scaleHome.elapsedMs) + ' ms; ' + scaleHome.stateItems + ' itens em memoria\n');
    check('fotografia real persiste os 12.000 itens sintéticos', scale.requested === 12000 && scale.stored === 12000 && scale.categoryCount === 12);
    check('escrita de 12.000 itens termina em menos de 15 segundos', scale.writeMs < 15000);
    check('event loop continua atendendo ticks durante a escrita grande', scale.ticks > 0 && scale.maximumTickGapMs < 500);
    check('Home sobre 12.000 itens termina em menos de 10 segundos', scaleHome.elapsedMs < 10000);
    check('Home conserva somente a seleção editorial limitada em memória', scaleHome.stateItems <= 160);
    check('Home de escala limita cartões/DOM e mantém chrome único', scaleHome.domCards <= 100 && scaleHome.rails >= 4 && scaleHome.shells === 1);

    await evaluate("(function(){BuroApp.state.screen='SHELL';BuroApp.state.section='SEARCH';BuroApp.state.screenData=null;BuroApp.render();var input=document.querySelector('#search-query');input.value='11999';window.__scaleSearchStarted=performance.now();document.querySelector('[data-action=search-run]').click();return true;}())");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind==='search' && !BuroApp.state.screenData.searching", 10000);
    var scaleSearch = await evaluate("({elapsedMs:performance.now()-window.__scaleSearchStarted,matches:BuroApp.state.screenData.matches.length,title:BuroApp.state.screenData.matches[0]&&BuroApp.state.screenData.matches[0].name,domCards:document.querySelectorAll('.media-card').length})");
    check('busca no fim do índice de 12.000 itens termina em menos de 5 segundos', scaleSearch.elapsedMs < 5000);
    check('busca de escala encontra somente o título correto sem inflar o DOM', scaleSearch.matches === 1 && scaleSearch.title === 'Título Escala 11999' && scaleSearch.domCards === 1);

    await evaluate("BuroApp.state.screen='SHELL';BuroApp.state.section='MOVIES';BuroApp.state.screenData=null;BuroApp.render();BuroApp._openCategory('scale-movie-0');true");
    await waitFor("BuroApp.state.screenData && BuroApp.state.screenData.kind==='category' && BuroApp.state.screenData.items.length===200", 10000);
    var scaleCategory = await evaluate("({items:BuroApp.state.screenData.items.length,total:BuroApp.state.screenData.catalogueTotalCount,hasMore:BuroApp.state.screenData.catalogueHasMore,domCards:document.querySelectorAll('.media-card').length,progressive:document.querySelector('.catalogue-progressive')&&document.querySelector('.catalogue-progressive').textContent,shells:document.querySelectorAll('#app .shell').length,scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight})");
    check('categoria grande abre somente os primeiros 200 de 1.000 itens',
        scaleCategory.items === 200 && scaleCategory.total === 1000 && scaleCategory.hasMore);
    check('controle progressivo informa a quantidade carregada e o total persistido',
        scaleCategory.progressive && scaleCategory.progressive.indexOf('200 / 1000') >= 0);
    check('categoria grande materializa somente 200 cards no DOM', scaleCategory.domCards === 200);
    check('catálogo de escala preserva shell único e sem overflow global',
        scaleCategory.shells === 1 && scaleCategory.scrollWidth <= 1920 && scaleCategory.scrollHeight <= 1080);

    await evaluate("document.querySelector('[data-action=category-load-more]').click();true");
    await waitFor("BuroApp.state.screenData.items.length===400 && BuroApp.state.screenData.cataloguePage===1", 10000);
    var scaleSecondBlock = await evaluate("({items:BuroApp.state.screenData.items.length,domCards:document.querySelectorAll('.media-card').length,focused:document.querySelector('[data-action=category-load-more]').classList.contains('focused')})");
    check('segundo bloco abre a página seguinte sem repetir os 200 anteriores',
        scaleSecondBlock.items === 400 && await evaluate("new Set(BuroApp.state.screenData.items.map(function(row){return row.id;})).size===400"));
    check('D-pad continua no carregamento progressivo com somente 200 cards visíveis',
        scaleSecondBlock.domCards === 200 && scaleSecondBlock.focused);

    for (var scaleLoaded = 600; scaleLoaded <= 1000; scaleLoaded += 200) {
        await evaluate("document.querySelector('[data-action=category-load-more]').click();true");
        await waitFor("BuroApp.state.screenData.items.length===" + scaleLoaded, 10000);
    }
    var scaleCompleteCategory = await evaluate("({items:BuroApp.state.screenData.items.length,page:BuroApp.state.screenData.cataloguePage,domCards:document.querySelectorAll('.media-card').length,pages:document.querySelector('.catalogue-pagination')&&document.querySelector('.catalogue-pagination').getAttribute('aria-label'),hasLoadMore:Boolean(document.querySelector('[data-action=category-load-more]')),unique:new Set(BuroApp.state.screenData.items.map(function(row){return row.id;})).size})");
    check('quatro retomadas por cursor alcançam os 1.000 itens sem duplicação',
        scaleCompleteCategory.items === 1000 && scaleCompleteCategory.unique === 1000);
    check('fim do cursor remove Carregar mais e conserva cinco páginas acessíveis',
        !scaleCompleteCategory.hasLoadMore && scaleCompleteCategory.pages && scaleCompleteCategory.pages.indexOf('5') >= 0);
    check('última página continua limitada a 200 cards no DOM',
        scaleCompleteCategory.page === 4 && scaleCompleteCategory.domCards === 200);
}

async function cleanup() {
    if (socket && socket.readyState < 2) { socket.close(); }
    if (browser && browser.exitCode == null) { browser.kill(); }
    await delay(150);
    if (profileDir) {
        var expectedPrefix = path.join(os.tmpdir(), 'iptvburo-tizen-visual-');
        if (profileDir.indexOf(expectedPrefix) === 0) {
            try { fs.rmSync(profileDir, { recursive: true, force: true }); } catch (ignored) {}
        }
    }
}

main().catch(function (error) {
    failures.push(error.message);
    process.stdout.write('  FALHA ' + error.message + '\n');
}).then(cleanup).then(function () {
    process.stdout.write('\n' + passed + ' verificacoes visuais aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
});
