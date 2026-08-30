/*
 * Enriquecimento transitório e limitado do Hero.
 *
 * Sinopse e imagens vêm da fonte autorizada, ficam somente na memória da
 * sessão e nunca entram no IndexedDB/localStorage. A fila é serial para não
 * competir com o carregamento do catálogo em TVs de menor capacidade.
 */
var BuroHeroEnrichment = (function () {
    'use strict';

    var MAX_CANDIDATES = 10;
    var MAX_CACHE_ENTRIES = 20;
    var BANNER_SYNOPSIS_LIMIT = 180;
    var ERROR_RETRY_MILLIS = 5 * 60 * 1000;
    var cache = {};
    var cacheOrder = [];
    var active = null;
    var nextJobId = 0;

    function cacheKey(sourceId, itemId) { return sourceId + ':' + itemId; }

    function cleanText(value, maximum) {
        var text = String(value == null ? '' : value)
            .replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ')
            .replace(/^\s+|\s+$/g, '');
        return maximum && text.length > maximum ? text.substring(0, maximum) : text;
    }

    /* Mesma regra Android: frase completa quando possível, reticências senão. */
    function bannerSynopsis(value) {
        var clean = cleanText(value, 4000);
        var cut;
        var sentenceEnd;
        var wordEnd;
        if (clean.length <= BANNER_SYNOPSIS_LIMIT) { return clean; }
        cut = clean.substring(0, BANNER_SYNOPSIS_LIMIT);
        sentenceEnd = Math.max(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'));
        if (sentenceEnd >= BANNER_SYNOPSIS_LIMIT / 2) { return cut.substring(0, sentenceEnd + 1); }
        wordEnd = cut.lastIndexOf(' ');
        if (wordEnd < BANNER_SYNOPSIS_LIMIT / 2) { wordEnd = BANNER_SYNOPSIS_LIMIT; }
        return cut.substring(0, wordEnd).replace(/[,;\s]+$/g, '') + '…';
    }

    function boundedUrl(value) {
        var url = cleanText(value, 4097);
        return url && url.length <= 4096 ? url : null;
    }

    function normalized(details) {
        var synopsis = bannerSynopsis(details && details.synopsis);
        var rating = Number(details && details.rating);
        var value = {
            status: 'READY',
            attemptedAt: Date.now(),
            synopsis: synopsis || null,
            genre: cleanText(details && details.genre, 160) || null,
            duration: cleanText(details && details.duration, 80) || null,
            releaseDate: cleanText(details && details.releaseDate, 80) || null,
            rating: isFinite(rating) && rating > 0 ? rating : null,
            artworkUrl: boundedUrl(details && details.artworkUrl),
            backdropUrl: boundedUrl(details && details.backdropUrl),
            /* Vem com o resto, por isso nao custa um pedido a mais. O banner
               usa-o para tocar o trailer em vez da capa parada. */
            youtubeTrailerId: cleanText(details && details.youtubeTrailerId, 32) || null
        };
        if (!value.synopsis && !value.genre && !value.duration && !value.rating &&
                !value.artworkUrl && !value.backdropUrl) { value.status = 'EMPTY'; }
        return value;
    }

    function remember(key, value) {
        var existing = cacheOrder.indexOf(key);
        if (existing >= 0) { cacheOrder.splice(existing, 1); }
        cache[key] = value;
        cacheOrder.push(key);
        while (cacheOrder.length > MAX_CACHE_ENTRIES) { delete cache[cacheOrder.shift()]; }
    }

    function valueFor(sourceId, itemId) {
        var key = cacheKey(sourceId, itemId);
        var value = cache[key];
        if (!value || value.status !== 'READY') { return null; }
        return {
            synopsis: value.synopsis,
            genre: value.genre,
            duration: value.duration,
            releaseDate: value.releaseDate,
            rating: value.rating,
            artworkUrl: value.artworkUrl,
            backdropUrl: value.backdropUrl,
            youtubeTrailerId: value.youtubeTrailerId
        };
    }

    function validCandidates(source, candidates, customLoader) {
        var known = {};
        return (candidates || []).filter(function (item) {
            var locator = item && item.locator;
            if (!item || item.sourceId !== source.id || known[item.id] ||
                    ['MOVIE', 'SERIES'].indexOf(item.contentType) < 0 ||
                    (customLoader ? !cleanText(item.name, 240) :
                        (!locator || locator.kind !== 'xtream' || !locator.providerItemId))) { return false; }
            known[item.id] = true;
            return true;
        }).slice(0, MAX_CANDIDATES);
    }

    function publicStatus(job) {
        return job ? {
            sourceId: job.source.id,
            state: job.state,
            completed: job.completed,
            total: job.total,
            failed: job.failed,
            currentItemId: job.current ? job.current.id : null
        } : null;
    }

    function notify(job) {
        if (job.callbacks && job.callbacks.onStatus) {
            try { job.callbacks.onStatus(publicStatus(job)); } catch (ignoredStatus) {}
        }
    }

    function finish(job, state) {
        if (job.finished) { return; }
        job.finished = true;
        job.state = state;
        job.current = null;
        job.request = null;
        notify(job);
        if (state === 'COMPLETE' && job.callbacks && job.callbacks.onComplete) {
            try { job.callbacks.onComplete(publicStatus(job)); } catch (ignoredComplete) {}
        }
    }

    function cancel() {
        var job = active;
        if (!job || job.state !== 'RUNNING') { return false; }
        job.cancelRequested = true;
        if (job.request && job.request.abort) {
            job.request.abort();
            job.cancelTimer = window.setTimeout(function () { finish(job, 'CANCELLED'); }, 0);
        } else { finish(job, 'CANCELLED'); }
        return true;
    }

    function next(job) {
        var item;
        var key;
        var secret;
        var controller;
        function succeeded(details) {
            var value;
            secret = null;
            job.request = null;
            if (active !== job || job.finished || job.cancelRequested) { return; }
            value = normalized(details || {});
            remember(key, value);
            job.completed += 1;
            job.current = null;
            if (job.callbacks.onItem) {
                try { job.callbacks.onItem(item, valueFor(job.source.id, item.id)); }
                catch (ignoredItem) {}
            }
            notify(job);
            window.setTimeout(function () { next(job); }, 0);
        }
        function failed() {
            secret = null;
            job.request = null;
            if (active !== job || job.finished) { return; }
            if (job.cancelTimer) { window.clearTimeout(job.cancelTimer); job.cancelTimer = null; }
            if (job.cancelRequested) { finish(job, 'CANCELLED'); return; }
            remember(key, { status: 'ERROR', attemptedAt: Date.now() });
            job.failed += 1;
            job.completed += 1;
            job.current = null;
            notify(job);
            window.setTimeout(function () { next(job); }, 0);
        }
        if (active !== job || job.finished) { return; }
        if (job.cancelRequested) { finish(job, 'CANCELLED'); return; }
        item = job.queue.shift() || null;
        if (!item) { finish(job, 'COMPLETE'); return; }
        job.current = item;
        key = cacheKey(job.source.id, item.id);
        notify(job);
        try {
            if (typeof job.callbacks.loadDetails === 'function') {
                controller = job.callbacks.loadDetails(item, succeeded, failed);
            } else {
                secret = job.callbacks.getSecret(job.source.id);
                controller = BuroXtream.loadHeroDetails(secret, item, succeeded, failed);
            }
        } catch (error) { failed(error); return; }
        /* Também suporta adapters/mocks que respondem de forma síncrona. */
        if (!job.finished && job.current === item) { job.request = controller || null; }
    }

    function start(source, candidates, callbacks) {
        var rows;
        var signature;
        var queue;
        var now = Date.now();
        var job;
        var customLoader;
        callbacks = callbacks || {};
        customLoader = typeof callbacks.loadDetails === 'function';
        if (!source || !BuroDomain.safeId(source.id) || (!customLoader && source.type !== 'XTREAM')) {
            throw new Error('SOURCE_TYPE_UNAVAILABLE');
        }
        if (!customLoader && typeof callbacks.getSecret !== 'function') { throw new Error('CREDENTIALS_REQUIRED'); }
        rows = validCandidates(source, candidates, customLoader);
        signature = source.id + ':' + (customLoader ? String(callbacks.modeKey || 'custom') : 'provider') + ':' +
            rows.map(function (item) { return item.id; }).join('|');
        if (active && active.state === 'RUNNING' && active.signature === signature) {
            /* A Home pode ser recomposta enquanto a mesma fila continua. */
            active.callbacks = callbacks;
            return publicStatus(active);
        }
        if (active && active.state === 'RUNNING') { cancel(); }
        queue = rows.filter(function (item) {
            var value = cache[cacheKey(source.id, item.id)];
            return !value || (value.status === 'ERROR' && now - Number(value.attemptedAt) >= ERROR_RETRY_MILLIS);
        });
        active = {
            id: ++nextJobId,
            source: source,
            signature: signature,
            callbacks: callbacks,
            queue: queue,
            total: queue.length,
            completed: 0,
            failed: 0,
            current: null,
            request: null,
            cancelTimer: null,
            cancelRequested: false,
            state: queue.length ? 'RUNNING' : 'COMPLETE',
            finished: !queue.length
        };
        job = active;
        notify(job);
        if (queue.length) { window.setTimeout(function () { next(job); }, 0); }
        return publicStatus(job);
    }

    function clearSource(sourceId) {
        var prefix = sourceId + ':';
        if (active && active.source.id === sourceId && active.state === 'RUNNING') { cancel(); }
        Object.keys(cache).forEach(function (key) {
            if (key.indexOf(prefix) === 0) { delete cache[key]; }
        });
        cacheOrder = cacheOrder.filter(function (key) { return key.indexOf(prefix) !== 0; });
    }

    function cacheSize() { return cacheOrder.length; }

    return {
        start: start,
        cancel: cancel,
        clearSource: clearSource,
        get: valueFor,
        cacheSize: cacheSize,
        bannerSynopsis: bannerSynopsis,
        MAX_CANDIDATES: MAX_CANDIDATES,
        MAX_CACHE_ENTRIES: MAX_CACHE_ENTRIES
    };
}());
