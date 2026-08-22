/* Xtream-compatible adapter. Credentials never leave this module except for late playback. */
var BuroXtream = (function () {
    'use strict';

    var ACTIONS = {
        LIVE: { categories: 'get_live_categories', items: 'get_live_streams' },
        MOVIE: { categories: 'get_vod_categories', items: 'get_vod_streams' },
        SERIES: { categories: 'get_series_categories', items: 'get_series' }
    };

    function normalizeServer(value) {
        var raw = BuroDomain.trim(value);
        var anchor;
        var path;
        if (!/^https?:\/\//i.test(raw)) { throw new Error('SERVER_URL_INVALID'); }
        anchor = document.createElement('a');
        anchor.href = raw;
        if (!anchor.hostname) { throw new Error('SERVER_URL_INVALID'); }
        path = anchor.pathname || '/';
        path = path.replace(/\/(player_api\.php|get\.php|xmltv\.php|panel_api\.php).*$/i, '/');
        path = path.replace(/\/+$/, '');
        return anchor.protocol + '//' + anchor.host + path;
    }

    function credentials(input) {
        var value = {
            server: normalizeServer(input.server),
            username: BuroDomain.trim(input.username),
            password: String(input.password || '')
        };
        if (!value.username || !value.password) { throw new Error('CREDENTIALS_REQUIRED'); }
        return value;
    }

    function apiUrl(secret, action, categoryId) {
        var result = secret.server + '/player_api.php?username=' + encodeURIComponent(secret.username) +
            '&password=' + encodeURIComponent(secret.password);
        if (action) { result += '&action=' + encodeURIComponent(action); }
        if (categoryId != null) { result += '&category_id=' + encodeURIComponent(String(categoryId)); }
        return result;
    }

    function authenticate(secret, success, failure) {
        BuroNetwork.json({ url: apiUrl(secret), maxBytes: 2 * 1024 * 1024 }, function (payload) {
            var user = payload && payload.user_info;
            var auth = user && (user.auth === 1 || user.auth === '1');
            var status = user && String(user.status || '').toLowerCase();
            if (!auth || (status && status !== 'active')) { failure({ code: 'AUTH_REJECTED' }); return; }
            success({ username: user.username || '', status: user.status || 'Active', expiresAt: user.exp_date || null });
        }, failure);
    }

    function loadCategories(secret, contentType, success, failure) {
        var contract = ACTIONS[contentType];
        if (!contract) { failure({ code: 'CONTENT_TYPE_INVALID' }); return; }
        BuroNetwork.json({ url: apiUrl(secret, contract.categories), maxBytes: 4 * 1024 * 1024 }, function (payload) {
            if (!Array.isArray(payload)) { failure({ code: 'MALFORMED_CATEGORIES' }); return; }
            success(payload.map(function (row, index) {
                var providerId = row.category_id == null ? String(index) : String(row.category_id);
                return {
                    id: BuroDomain.id('category', contentType + ':' + providerId),
                    providerCategoryId: providerId,
                    name: BuroDomain.trim(row.category_name) || 'Outros',
                    contentType: contentType,
                    sortOrder: index
                };
            }));
        }, failure);
    }

    function itemFromRow(sourceId, contentType, row, categoryId, index) {
        var providerId = row.stream_id != null ? row.stream_id :
            (row.series_id != null ? row.series_id : index);
        var extension = row.container_extension || (contentType === 'LIVE' ? 'ts' : 'mp4');
        var rating = row.rating_5based != null ? row.rating_5based : row.rating;
        var providerAddedAt = Number(row.added != null ? row.added : row.added_at);
        if (isFinite(providerAddedAt) && providerAddedAt > 0 && providerAddedAt < 100000000000) {
            providerAddedAt *= 1000;
        }
        return BuroDomain.createItem({
            sourceId: sourceId,
            providerItemId: String(providerId),
            name: row.name || row.title || 'Sem título',
            categoryId: categoryId,
            contentType: contentType,
            /*
              A capa, quando ela não carrega credencial.

              Antes isto era `null` sempre, e a arte ia por um canal separado que
              só existia em memória, com teto de 800 e descarte LRU. Num catálogo
              de dezenas de milhares a varredura de fundo percorre as categorias
              em sequência e as últimas expulsavam as primeiras: quem abria
              Filmes via cartões de texto com uma ou outra capa, porque a página
              mostrada é do começo do catálogo, que é o primeiro a ser descartado.

              A preocupação que originou o `null` continua valendo e é a mesma
              regra dos lembretes: `isStorableReminderArtwork` recusa
              usuário:senha@host, qualquer query string e os caminhos
              autenticados do próprio provedor (/movie/<usuario>/<senha>/<id>).
              O que não passa nessa peneira continua só em memória, resolvido
              tarde, como sempre foi.
            */
            logoUrl: row.stream_icon || row.cover || row.poster || null,
            genre: row.genre || null,
            year: row.year || null,
            rating: rating || null,
            sortOrder: index,
            addedAt: providerAddedAt,
            locator: { kind: 'xtream', contentType: contentType, providerItemId: String(providerId), extension: extension }
        });
    }

    function loadItems(secret, sourceId, contentType, category, success, failure) {
        var contract = ACTIONS[contentType];
        if (!contract) { failure({ code: 'CONTENT_TYPE_INVALID' }); return; }
        return BuroNetwork.json({
            url: apiUrl(secret, contract.items, category.providerCategoryId),
            maxBytes: 16 * 1024 * 1024,
            timeoutMs: 30000
        }, function (payload) {
            var artwork = {};
            var items;
            if (!Array.isArray(payload)) { failure({ code: 'MALFORMED_CATALOG' }); return; }
            items = payload.slice(0, 100000).map(function (row, index) {
                var item = itemFromRow(sourceId, contentType, row, category.id, index);
                var image = row.stream_icon || row.cover || row.poster || null;
                if (image) { artwork[item.id] = image; }
                return item;
            });
            success(items, artwork);
        }, failure);
    }

    function loadSeriesEpisodes(secret, sourceId, seriesItem, success, failure) {
        var locator = seriesItem && seriesItem.locator;
        var url;
        if (!locator || locator.kind !== 'xtream' || locator.contentType !== 'SERIES') {
            failure({ code: 'LOCATOR_INVALID' }); return;
        }
        url = apiUrl(secret, 'get_series_info') + '&series_id=' + encodeURIComponent(locator.providerItemId);
        return BuroNetwork.json({ url: url, maxBytes: 16 * 1024 * 1024, timeoutMs: 30000 }, function (payload) {
            var groups = payload && payload.episodes;
            var info = payload && payload.info ? payload.info : {};
            var rows = [];
            if (!payload || typeof payload !== 'object') { failure({ code: 'MALFORMED_CATALOG' }); return; }
            if (!groups || typeof groups !== 'object') { groups = {}; }
            Object.keys(groups).sort(function (a, b) { return Number(a) - Number(b); }).forEach(function (seasonKey) {
                var episodes = Array.isArray(groups[seasonKey]) ? groups[seasonKey] : [];
                episodes.forEach(function (episode, index) {
                    var providerId = episode.id != null ? episode.id : (seasonKey + '-' + index);
                    var number = episode.episode_num != null ? episode.episode_num : (index + 1);
                    rows.push(BuroDomain.createItem({
                        sourceId: sourceId,
                        providerItemId: String(providerId),
                        name: episode.title || ('T' + seasonKey + ' · E' + number),
                        categoryId: seriesItem.id,
                        contentType: BuroDomain.CONTENT.EPISODE,
                        logoUrl: null,
                        locator: {
                            kind: 'xtream', contentType: 'EPISODE', providerItemId: String(providerId),
                            seriesId: locator.providerItemId, season: Number(seasonKey) || 0,
                            episode: Number(number) || (index + 1), extension: episode.container_extension || 'mp4'
                        }
                    }));
                });
            });
            success(rows, seriesDetails(info, seriesItem), info.cover || info.movie_image || null,
                backdropCandidate(info.backdrop_path || info.backdrop));
        }, failure);
    }

    function backdropCandidate(value) {
        if (Array.isArray(value)) { return value.length ? value[0] : null; }
        return value || null;
    }

    function seriesDetails(info, seriesItem) {
        info = info || {};
        return {
            title: BuroDomain.trim(info.name || seriesItem.name),
            plot: BuroDomain.trim(info.plot || info.description) || null,
            cast: BuroDomain.trim(info.cast) || null,
            director: BuroDomain.trim(info.director) || null,
            genre: BuroDomain.trim(info.genre) || null,
            duration: BuroDomain.trim(info.episode_run_time || info.duration) || null,
            releaseDate: BuroDomain.trim(info.releaseDate || info.release_date) || null,
            country: BuroDomain.trim(info.country) || null,
            rating: info.rating == null ? null : Number(info.rating),
            youtubeTrailerId: BuroDomain.sanitizeYouTubeReference(info.youtube_trailer)
        };
    }

    function loadSeriesDetails(secret, seriesItem, success, failure) {
        var locator = seriesItem && seriesItem.locator;
        var url;
        if (!locator || locator.kind !== 'xtream' || locator.contentType !== 'SERIES') {
            failure({ code: 'LOCATOR_INVALID' }); return null;
        }
        url = apiUrl(secret, 'get_series_info') + '&series_id=' + encodeURIComponent(locator.providerItemId);
        return BuroNetwork.json({ url: url, maxBytes: 16 * 1024 * 1024, timeoutMs: 30000 }, function (payload) {
            var info;
            if (!payload || typeof payload !== 'object') { failure({ code: 'MALFORMED_CATALOG' }); return; }
            info = payload.info && typeof payload.info === 'object' ? payload.info : {};
            success(seriesDetails(info, seriesItem), info.cover || info.movie_image || null,
                backdropCandidate(info.backdrop_path || info.backdrop));
        }, failure);
    }

    function loadMovieDetails(secret, movieItem, success, failure) {
        var locator = movieItem && movieItem.locator;
        var url;
        if (!locator || locator.kind !== 'xtream' || locator.contentType !== 'MOVIE') {
            failure({ code: 'LOCATOR_INVALID' }); return;
        }
        url = apiUrl(secret, 'get_vod_info') + '&vod_id=' + encodeURIComponent(locator.providerItemId);
        return BuroNetwork.json({ url: url, maxBytes: 4 * 1024 * 1024, timeoutMs: 30000 }, function (payload) {
            var info = payload && payload.info ? payload.info : {};
            var movie = payload && payload.movie_data ? payload.movie_data : {};
            success({
                title: BuroDomain.trim(info.name || movie.name || movieItem.name),
                plot: BuroDomain.trim(info.plot || info.description) || null,
                cast: BuroDomain.trim(info.cast) || null,
                director: BuroDomain.trim(info.director) || null,
                genre: BuroDomain.trim(info.genre) || null,
                duration: BuroDomain.trim(info.duration || info.duration_secs) || null,
                releaseDate: BuroDomain.trim(info.releasedate || info.releaseDate || info.release_date) || null,
                country: BuroDomain.trim(info.country) || null,
                rating: info.rating == null ? null : Number(info.rating),
                youtubeTrailerId: BuroDomain.sanitizeYouTubeReference(info.youtube_trailer)
            }, info.movie_image || info.cover_big || movie.stream_icon || null,
            backdropCandidate(info.backdrop_path || info.backdrop || movie.backdrop_path));
        }, failure);
    }

    function loadHeroDetails(secret, item, success, failure) {
        function complete(details, artworkUrl, backdropUrl) {
            success({
                synopsis: details.plot || null,
                genre: details.genre || null,
                duration: details.duration || null,
                releaseDate: details.releaseDate || null,
                rating: details.rating == null ? null : Number(details.rating),
                artworkUrl: artworkUrl || null,
                backdropUrl: backdropUrl || null
            });
        }
        if (item && item.contentType === 'MOVIE') {
            return loadMovieDetails(secret, item, complete, failure);
        }
        if (item && item.contentType === 'SERIES') {
            return loadSeriesDetails(secret, item, complete, failure);
        }
        failure({ code: 'CONTENT_TYPE_INVALID' });
        return null;
    }

    function decodeProviderText(value) {
        var text = BuroDomain.trim(value);
        if (!text) { return ''; }
        try {
            if (/^[A-Za-z0-9+/]+={0,2}$/.test(text) && text.length % 4 === 0 && window.atob) {
                return decodeURIComponent(escape(window.atob(text)));
            }
        } catch (ignoredDecode) { /* Alguns provedores já enviam texto normal. */ }
        return text;
    }

    function loadLiveEpg(secret, liveItem, success, failure) {
        var locator = liveItem && liveItem.locator;
        var url;
        if (!locator || locator.kind !== 'xtream' || locator.contentType !== 'LIVE') {
            failure({ code: 'LOCATOR_INVALID' }); return;
        }
        url = apiUrl(secret, 'get_short_epg') + '&stream_id=' + encodeURIComponent(locator.providerItemId) + '&limit=12';
        BuroNetwork.json({ url: url, maxBytes: 2 * 1024 * 1024, timeoutMs: 18000 }, function (payload) {
            var rows = payload && Array.isArray(payload.epg_listings) ? payload.epg_listings : [];
            success(rows.slice(0, 12).map(function (row) {
                return {
                    title: decodeProviderText(row.title) || 'Programa',
                    description: decodeProviderText(row.description) || null,
                    startEpochSeconds: row.start_timestamp == null ? null : Number(row.start_timestamp),
                    endEpochSeconds: row.stop_timestamp == null ? null : Number(row.stop_timestamp),
                    start: BuroDomain.trim(row.start) || null,
                    end: BuroDomain.trim(row.end) || null
                };
            }));
        }, failure);
    }

    function resolvePlayback(secret, locator) {
        var folder;
        if (!locator || locator.kind !== 'xtream') { throw new Error('LOCATOR_INVALID'); }
        if (locator.contentType === 'LIVE') { folder = 'live'; }
        else if (locator.contentType === 'MOVIE') { folder = 'movie'; }
        else if (locator.contentType === 'EPISODE') { folder = 'series'; }
        else { throw new Error('SERIES_EPISODE_REQUIRED'); }
        return secret.server + '/' + folder + '/' + encodeURIComponent(secret.username) + '/' +
            encodeURIComponent(secret.password) + '/' + encodeURIComponent(locator.providerItemId) + '.' +
            encodeURIComponent(locator.extension || (folder === 'live' ? 'ts' : 'mp4'));
    }

    return {
        normalizeServer: normalizeServer,
        credentials: credentials,
        authenticate: authenticate,
        loadCategories: loadCategories,
        loadItems: loadItems,
        loadSeriesEpisodes: loadSeriesEpisodes,
        loadSeriesDetails: loadSeriesDetails,
        loadMovieDetails: loadMovieDetails,
        loadHeroDetails: loadHeroDetails,
        loadLiveEpg: loadLiveEpg,
        resolvePlayback: resolvePlayback
    };
}());
