/* Metadados opcionais do TMDb. A chave fica no KeyManager e nunca entra em logs ou no catálogo. */
var BuroTmdb = (function () {
    'use strict';

    var API_BASE = 'https://api.themoviedb.org/3';
    var IMAGE_BASE = 'https://image.tmdb.org/t/p';
    var SHARED_SECRET_ID = 'tmdb-shared';
    var MAX_CAST = 12;
    var SUPPORTED_REGIONS = ['BR', 'PT', 'US', 'DE', 'IT'];
    var MAX_SERVICES = 12;
    var TITLES_PER_SERVICE = 20;

    function clean(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    function safeKey(value) {
        var key = clean(value);
        return /^[A-Za-z0-9._-]{16,256}$/.test(key) ? key : null;
    }

    function language(value) {
        return { 'pt-BR': 'pt-BR', en: 'en-US', de: 'de-DE', it: 'it-IT', es: 'es-ES' }[value] || 'pt-BR';
    }

    function safeRegion(value) {
        var region = clean(value).toUpperCase();
        return SUPPORTED_REGIONS.indexOf(region) >= 0 ? region : 'BR';
    }

    function profileSecretId(profileId) {
        if (!BuroDomain.safeId(profileId)) { return null; }
        return 'tmdb-profile-' + profileId;
    }

    function readSecret(id) {
        var value;
        try {
            value = BuroStorage.secureGet(id);
            return safeKey(value && value.apiKey);
        } catch (ignoredSecret) { return null; }
    }

    function keyForProfile(profileId) {
        var id = profileSecretId(profileId);
        return (id && readSecret(id)) || readSecret(SHARED_SECRET_ID);
    }

    function configuration(profileId) {
        var profileIdKey = profileSecretId(profileId);
        return {
            profile: Boolean(profileIdKey && readSecret(profileIdKey)),
            shared: Boolean(readSecret(SHARED_SECRET_ID)),
            effective: Boolean(keyForProfile(profileId))
        };
    }

    function save(scope, profileId, value, success, failure) {
        var key = safeKey(value);
        var id = scope === 'shared' ? SHARED_SECRET_ID : profileSecretId(profileId);
        if (!key || !id) { failure({ code: 'TMDB_KEY_INVALID' }); return; }
        BuroStorage.secureSave(id, { apiKey: key }, success, function () {
            failure({ code: 'SECURE_STORE_UNAVAILABLE' });
        });
    }

    function remove(scope, profileId) {
        var id = scope === 'shared' ? SHARED_SECRET_ID : profileSecretId(profileId);
        if (!id) { return false; }
        try { BuroStorage.secureRemove(id); return true; }
        catch (ignoredRemove) { return false; }
    }

    function url(path, key, params) {
        var query = ['api_key=' + encodeURIComponent(key)];
        Object.keys(params || {}).forEach(function (name) {
            var value = params[name];
            if (value != null && value !== '') { query.push(encodeURIComponent(name) + '=' + encodeURIComponent(value)); }
        });
        return API_BASE + '/' + String(path || '').replace(/^\/+/, '') + '?' + query.join('&');
    }

    function image(path, size) {
        var value = clean(path);
        if (!/^\/[A-Za-z0-9._/-]{1,240}$/.test(value) || value.indexOf('..') >= 0) { return null; }
        return IMAGE_BASE + '/' + size + value;
    }

    function request(path, key, params, success, failure) {
        return BuroNetwork.json({
            url: url(path, key, params), headers: { Accept: 'application/json' },
            maxBytes: 4 * 1024 * 1024, timeoutMs: 18000
        }, function (payload) {
            if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
                failure({ code: 'TMDB_MALFORMED' }); return;
            }
            success(payload);
        }, function (error) {
            failure({ code: error && error.code === 'AUTH_REJECTED' ? 'TMDB_KEY_REJECTED' : 'TMDB_UNAVAILABLE' });
        });
    }

    function validateKey(key, success, failure) {
        key = safeKey(key);
        if (!key) { failure({ code: 'TMDB_KEY_INVALID' }); return null; }
        return request('configuration', key, {}, function () { success(key); }, failure);
    }

    function firstResult(payload, expectedYear, isSeries) {
        var rows = Array.isArray(payload && payload.results) ? payload.results : [];
        var dateName = isSeries ? 'first_air_date' : 'release_date';
        var exact = null;
        if (expectedYear) {
            rows.some(function (row) {
                if (String(row && row[dateName] || '').substring(0, 4) === String(expectedYear)) {
                    exact = row; return true;
                }
                return false;
            });
        }
        return exact || rows[0] || null;
    }

    function titleDetails(payload, isSeries) {
        var castRows = payload && payload.credits && Array.isArray(payload.credits.cast) ? payload.credits.cast : [];
        var videos = payload && payload.videos && Array.isArray(payload.videos.results) ? payload.videos.results : [];
        var trailer = null;
        videos.filter(function (video) {
            return video && String(video.site || '').toLowerCase() === 'youtube' &&
                (video.type === 'Trailer' || video.type === 'Teaser') && BuroDomain.sanitizeYouTubeReference(video.key);
        }).sort(function (left, right) {
            return (right.type === 'Trailer' ? 1 : 0) - (left.type === 'Trailer' ? 1 : 0);
        }).some(function (video) { trailer = BuroDomain.sanitizeYouTubeReference(video.key); return true; });
        return {
            tmdbId: Number(payload.id) || null,
            title: clean(payload[isSeries ? 'name' : 'title']) || null,
            plot: clean(payload.overview) || null,
            backdropUrl: image(payload.backdrop_path, 'w1280'),
            posterUrl: image(payload.poster_path, 'w342'),
            releaseDate: clean(payload[isSeries ? 'first_air_date' : 'release_date']) || null,
            rating: isFinite(Number(payload.vote_average)) ? Number(payload.vote_average) : null,
            duration: isSeries ? (Array.isArray(payload.episode_run_time) ? Number(payload.episode_run_time[0]) || null : null) :
                (Number(payload.runtime) || null),
            genre: Array.isArray(payload.genres) ? payload.genres.map(function (genre) { return clean(genre && genre.name); }).filter(Boolean).join(' / ') : null,
            castMembers: castRows.slice(0, MAX_CAST).map(function (member) {
                return {
                    id: Number(member && member.id) || null,
                    name: clean(member && member.name), character: clean(member && member.character) || null,
                    photoUrl: image(member && member.profile_path, 'w185')
                };
            }).filter(function (member) { return Boolean(member.name); }),
            youtubeTrailerId: trailer
        };
    }

    function loadTitle(key, item, isSeries, locale, success, failure) {
        var active = null;
        var stopped = false;
        var type = isSeries ? 'tv' : 'movie';
        key = safeKey(key);
        if (!key || !item || !clean(item.name)) { failure({ code: 'TMDB_NOT_CONFIGURED' }); return null; }
        active = request('search/' + type, key, {
            query: clean(item.name), language: language(locale), include_adult: 'false',
            year: !isSeries ? item.year : null, first_air_date_year: isSeries ? item.year : null
        }, function (payload) {
            var match = firstResult(payload, item.year, isSeries);
            if (stopped) { return; }
            if (!match || !Number(match.id)) { failure({ code: 'TMDB_NOT_FOUND' }); return; }
            active = request(type + '/' + Number(match.id), key, {
                language: language(locale), append_to_response: 'credits,videos'
            }, function (details) {
                if (!stopped) { success(titleDetails(details, isSeries)); }
            }, function (error) { if (!stopped) { failure(error); } });
        }, function (error) { if (!stopped) { failure(error); } });
        return { abort: function () { stopped = true; if (active && active.abort) { active.abort(); } } };
    }

    function personBundle(person, details, credits) {
        var cast = Array.isArray(credits && credits.cast) ? credits.cast : [];
        return {
            id: Number(person.id), name: clean(person.name), knownFor: clean(person.known_for_department) || null,
            photoUrl: image(person.profile_path, 'w342'), biography: clean(details && details.biography) || null,
            birthday: clean(details && details.birthday) || null,
            placeOfBirth: clean(details && details.place_of_birth) || null,
            credits: cast.map(function (credit) {
                return {
                    id: Number(credit && credit.id) || null, isSeries: credit && credit.media_type === 'tv',
                    title: clean(credit && (credit.title || credit.name)),
                    year: Number(String(credit && (credit.release_date || credit.first_air_date) || '').substring(0, 4)) || null,
                    posterUrl: image(credit && credit.poster_path, 'w185'),
                    character: clean(credit && credit.character) || null,
                    popularity: Number(credit && credit.popularity) || 0
                };
            }).filter(function (credit) { return Boolean(credit.title); })
                .sort(function (left, right) { return right.popularity - left.popularity; })
                .filter(function (credit, index, rows) {
                    var name = BuroDomain.foldAccents(credit.title);
                    return rows.slice(0, index).every(function (prior) { return BuroDomain.foldAccents(prior.title) !== name; });
                }).slice(0, 24)
        };
    }

    function loadPerson(key, name, locale, success, failure) {
        var active = null;
        var stopped = false;
        key = safeKey(key);
        name = clean(name);
        if (!key || !name) { failure({ code: 'TMDB_NOT_CONFIGURED' }); return null; }
        active = request('search/person', key, {
            query: name, language: language(locale), include_adult: 'false'
        }, function (payload) {
            var person = Array.isArray(payload.results) ? payload.results[0] : null;
            var details = null;
            if (stopped) { return; }
            if (!person || !Number(person.id)) { failure({ code: 'TMDB_NOT_FOUND' }); return; }
            active = request('person/' + Number(person.id), key, { language: language(locale) }, function (value) {
                details = value;
                if (stopped) { return; }
                active = request('person/' + Number(person.id) + '/combined_credits', key,
                    { language: language(locale) }, function (credits) {
                        if (!stopped) { success(personBundle(person, details, credits)); }
                    }, function () { if (!stopped) { success(personBundle(person, details, {})); } });
            }, function () {
                if (stopped) { return; }
                active = request('person/' + Number(person.id) + '/combined_credits', key,
                    { language: language(locale) }, function (credits) {
                        if (!stopped) { success(personBundle(person, null, credits)); }
                    }, function (error) { if (!stopped) { failure(error); } });
            });
        }, function (error) { if (!stopped) { failure(error); } });
        return { abort: function () { stopped = true; if (active && active.abort) { active.abort(); } } };
    }

    function localIsoDate(offsetDays, offsetMonths) {
        var now = new Date();
        var year;
        var month;
        var day;
        if (offsetMonths) { now.setMonth(now.getMonth() + offsetMonths); }
        if (offsetDays) { now.setDate(now.getDate() + offsetDays); }
        year = now.getFullYear(); month = now.getMonth() + 1; day = now.getDate();
        return year + '-' + (month < 10 ? '0' : '') + month + '-' + (day < 10 ? '0' : '') + day;
    }

    function discoveredTitles(payload, isSeries) {
        var rows = Array.isArray(payload && payload.results) ? payload.results : [];
        return rows.map(function (row) {
            var releaseDate = clean(row && row[isSeries ? 'first_air_date' : 'release_date']);
            return {
                tmdbId: Number(row && row.id) || null, isSeries: Boolean(isSeries),
                title: clean(row && row[isSeries ? 'name' : 'title']),
                year: Number(releaseDate.substring(0, 4)) || null, releaseDate: releaseDate || null,
                posterUrl: image(row && row.poster_path, 'w342'), overview: clean(row && row.overview) || null,
                rating: isFinite(Number(row && row.vote_average)) ? Number(row.vote_average) : null
            };
        }).filter(function (row) { return row.tmdbId && row.title; });
    }

    function directory(payload) {
        var rows = Array.isArray(payload && payload.results) ? payload.results : [];
        return rows.map(function (row) {
            return {
                id: Number(row && row.provider_id) || null, name: clean(row && row.provider_name),
                priority: isFinite(Number(row && row.display_priority)) ? Number(row.display_priority) : 999999
            };
        }).filter(function (row) { return row.id && row.name; })
            .sort(function (left, right) { return left.priority - right.priority; }).slice(0, MAX_SERVICES);
    }

    function discoverParams(providerId, region, kind) {
        var params = {
            language: null, with_watch_providers: providerId, watch_region: region,
            include_adult: 'false', sort_by: 'popularity.desc'
        };
        if (kind === 'MOVIES') {
            params.sort_by = 'primary_release_date.desc'; params['primary_release_date.lte'] = localIsoDate();
            params['vote_count.gte'] = 10;
        } else if (kind === 'SERIES') {
            params.sort_by = 'first_air_date.desc'; params['first_air_date.lte'] = localIsoDate();
            params['vote_count.gte'] = 10;
        } else if (kind === 'THIS_WEEK') {
            params['air_date.gte'] = localIsoDate(-7); params['air_date.lte'] = localIsoDate();
        }
        return params;
    }

    function loadUpcoming(key, region, locale, success, failure) {
        var active = null;
        var stopped = false;
        var selected = [];
        var candidates = [];
        var index = 0;
        var lastError = null;
        function next() {
            var candidate;
            if (stopped) { return; }
            if (selected.length >= TITLES_PER_SERVICE || index >= candidates.length) {
                if (!selected.length && lastError) { failure(lastError); }
                else { success(selected.length ? [{ providerId: null, providerName: 'coming-soon', titles: selected }] : []); }
                return;
            }
            candidate = candidates[index]; index += 1;
            active = request('movie/' + candidate.tmdbId + '/watch/providers', key, {}, function (payload) {
                var area = payload.results && payload.results[region];
                if (!area || !Array.isArray(area.flatrate) || !area.flatrate.length) { selected.push(candidate); }
                next();
            }, function (error) { lastError = error; next(); });
        }
        active = request('discover/movie', key, {
            language: language(locale), region: region, include_adult: 'false', sort_by: 'popularity.desc',
            'primary_release_date.gte': localIsoDate(0, -6), 'primary_release_date.lte': localIsoDate(),
            with_release_type: 3, 'vote_count.gte': 20
        }, function (payload) { candidates = discoveredTitles(payload, false).slice(0, 40); next(); }, failure);
        return { abort: function () { stopped = true; if (active && active.abort) { active.abort(); } } };
    }

    function loadShelves(key, region, kind, locale, progress, success, failure) {
        var active = null;
        var stopped = false;
        var services;
        var shelves = [];
        var index = 0;
        var failures = 0;
        var isSeries;
        key = safeKey(key); region = safeRegion(region); kind = String(kind || 'MOVIES').toUpperCase();
        isSeries = kind === 'SERIES' || kind === 'THIS_WEEK';
        if (!key) { failure({ code: 'TMDB_NOT_CONFIGURED' }); return null; }
        if (kind === 'UPCOMING') { return loadUpcoming(key, region, locale, success, failure); }
        function next() {
            var service;
            var params;
            if (stopped) { return; }
            if (index >= services.length) {
                if (!shelves.length && failures) { failure({ code: 'TMDB_UNAVAILABLE' }); }
                else { success(shelves); }
                return;
            }
            service = services[index]; index += 1;
            params = discoverParams(service.id, region, kind);
            params.language = language(locale);
            active = request(isSeries ? 'discover/tv' : 'discover/movie', key, params, function (payload) {
                var titles = discoveredTitles(payload, isSeries).slice(0, TITLES_PER_SERVICE);
                if (titles.length) { shelves.push({ providerId: service.id, providerName: service.name, titles: titles }); }
                if (progress) { progress(index, services.length, shelves.length); }
                next();
            }, function () { failures += 1; if (progress) { progress(index, services.length, shelves.length); } next(); });
        }
        active = request(isSeries ? 'watch/providers/tv' : 'watch/providers/movie', key, {
            language: language(locale), watch_region: region
        }, function (payload) {
            services = directory(payload);
            if (!services.length) { success([]); return; }
            next();
        }, failure);
        return { abort: function () { stopped = true; if (active && active.abort) { active.abort(); } } };
    }

    function providerSlug(name) {
        return BuroDomain.foldAccents(name || '').replace(/\+/g, '-plus').replace(/[^a-z0-9]+/g, '-')
            .replace(/-+/g, '-').replace(/^-|-$/g, '');
    }

    function safeTmdbPage(value) {
        var anchor = document.createElement('a');
        try {
            anchor.href = clean(value);
            return anchor.protocol === 'https:' && (anchor.hostname === 'www.themoviedb.org' || anchor.hostname === 'themoviedb.org') ? anchor.href : null;
        } catch (ignoredUrl) { return null; }
    }

    function providerTarget(name, title, fallback) {
        var slug = providerSlug(name);
        var patterns = {
            netflix: 'https://www.netflix.com/search?q=', 'prime-video': 'https://www.primevideo.com/search/ref=atv_nb_sr?phrase=',
            'disney-plus': 'https://www.disneyplus.com/search?q=', 'apple-tv': 'https://tv.apple.com/search?term=',
            'google-play': 'https://play.google.com/store/search?c=movies&q=', 'hbo-max': 'https://play.max.com/search?q=',
            globoplay: 'https://globoplay.globo.com/busca/?q=', 'paramount-plus': 'https://www.paramountplus.com/search/?q='
        };
        var homes = {
            netflix: 'https://www.netflix.com/', 'prime-video': 'https://www.primevideo.com/',
            'disney-plus': 'https://www.disneyplus.com/', 'apple-tv': 'https://tv.apple.com/',
            'google-play': 'https://play.google.com/store/movies', 'hbo-max': 'https://play.max.com/',
            globoplay: 'https://globoplay.globo.com/', 'paramount-plus': 'https://www.paramountplus.com/'
        };
        return patterns[slug] ? patterns[slug] + encodeURIComponent(clean(title)) : (homes[slug] || safeTmdbPage(fallback));
    }

    function offerRows(area, title) {
        var rows = [];
        var seen = {};
        function add(bucket, type) {
            (Array.isArray(area && area[bucket]) ? area[bucket] : []).forEach(function (entry) {
                var id = Number(entry && entry.provider_id);
                var name = clean(entry && entry.provider_name);
                var key = id + ':' + type;
                if (!id || !name || seen[key]) { return; }
                seen[key] = true;
                rows.push({ providerId: id, providerName: name, type: type,
                    url: providerTarget(name, title, area.link), requiresAttribution: true });
            });
        }
        add('flatrate', 'subscription'); add('ads', 'ads'); add('free', 'free'); add('rent', 'rent'); add('buy', 'buy');
        return rows;
    }

    function loadSubscriptionTitle(key, title, region, locale, success, failure) {
        var active = null;
        var stopped = false;
        var details = null;
        var detailsFailed = false;
        var type = title && title.isSeries ? 'tv' : 'movie';
        var id = Number(title && (title.tmdbId || title.id));
        key = safeKey(key); region = safeRegion(region);
        if (!key || !id) { failure({ code: 'TMDB_NOT_CONFIGURED' }); return null; }
        function loadOffers() {
            active = request(type + '/' + id + '/watch/providers', key, {}, function (payload) {
                var area = payload.results && payload.results[region];
                if (!stopped) { success({ details: details, offers: area ? offerRows(area, title.title) : [], unknown: !area }); }
            }, function (error) {
                if (stopped) { return; }
                if (details || !detailsFailed) { success({ details: details, offers: [], unknown: true }); }
                else { failure(error); }
            });
        }
        active = request(type + '/' + id, key, { language: language(locale), append_to_response: 'credits,videos' },
            function (payload) { details = titleDetails(payload, title.isSeries); loadOffers(); },
            function () { detailsFailed = true; loadOffers(); });
        return { abort: function () { stopped = true; if (active && active.abort) { active.abort(); } } };
    }

    return {
        safeKey: safeKey, profileSecretId: profileSecretId, keyForProfile: keyForProfile,
        configuration: configuration, save: save, remove: remove, validateKey: validateKey,
        loadTitle: loadTitle, loadPerson: loadPerson, image: image, safeRegion: safeRegion,
        supportedRegions: function () { return SUPPORTED_REGIONS.slice(); },
        loadShelves: loadShelves, loadSubscriptionTitle: loadSubscriptionTitle,
        providerTarget: providerTarget
    };
}());
