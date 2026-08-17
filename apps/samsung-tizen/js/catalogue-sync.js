/*
 * Fila retomável para hidratar catálogos Xtream sem bloquear o shell.
 *
 * O checkpoint contém somente ids, contagens e datas. Credenciais continuam
 * no KeyManager e existem nesta fila apenas durante a requisição em curso.
 * Uma categoria é marcada como concluída somente depois da transação IndexedDB.
 */
var BuroCatalogueSync = (function () {
    'use strict';

    var CHECKPOINT_KEY = 'iptvburo.xtream-catalogue-sync.v1';
    var FRESH_MILLIS = 24 * 60 * 60 * 1000;
    var RETRY_DELAY_MILLIS = 400;
    var MAX_NETWORK_ATTEMPTS = 2;
    var listeners = [];
    var active = null;
    var nextJobId = 0;

    function emptyCheckpoint() { return { version: 1, sources: {} }; }

    function loadCheckpoint() {
        var parsed;
        try { parsed = JSON.parse(localStorage.getItem(CHECKPOINT_KEY) || 'null'); }
        catch (ignoredParse) { parsed = null; }
        if (!parsed || parsed.version !== 1 || !parsed.sources || typeof parsed.sources !== 'object') {
            return emptyCheckpoint();
        }
        return parsed;
    }

    function saveCheckpoint(value) {
        try { localStorage.setItem(CHECKPOINT_KEY, JSON.stringify(value)); }
        catch (ignoredStorage) { /* A transação do catálogo continua sendo a fonte de verdade. */ }
    }

    function sourceCheckpoint(root, sourceId) {
        if (!root.sources[sourceId] || typeof root.sources[sourceId] !== 'object') {
            root.sources[sourceId] = { categories: {} };
        }
        if (!root.sources[sourceId].categories || typeof root.sources[sourceId].categories !== 'object') {
            root.sources[sourceId].categories = {};
        }
        return root.sources[sourceId];
    }

    function validCategories(source, categories) {
        var known = {};
        return (categories || []).filter(function (category) {
            if (!category || category.sourceId !== source.id || !category.id || known[category.id] ||
                    ['MOVIE', 'SERIES', 'LIVE'].indexOf(category.contentType) < 0) { return false; }
            known[category.id] = true;
            return true;
        }).sort(function (left, right) {
            var priority = { MOVIE: 0, SERIES: 1, LIVE: 2 };
            return priority[left.contentType] - priority[right.contentType] ||
                Number(left.sortOrder) - Number(right.sortOrder) || String(left.id).localeCompare(String(right.id));
        });
    }

    function reconcile(sourceId, categoryIds) {
        var root = loadCheckpoint();
        var source = sourceCheckpoint(root, sourceId);
        var allowed = {};
        (categoryIds || []).forEach(function (id) { allowed[id] = true; });
        Object.keys(source.categories).forEach(function (id) {
            if (!allowed[id]) { delete source.categories[id]; }
        });
        saveCheckpoint(root);
    }

    function resetSource(sourceId) {
        var root = loadCheckpoint();
        root.sources[sourceId] = { categories: {} };
        saveCheckpoint(root);
    }

    function clearSource(sourceId) {
        var root = loadCheckpoint();
        if (active && active.source.id === sourceId && active.state === 'RUNNING') { cancel(); }
        delete root.sources[sourceId];
        saveCheckpoint(root);
    }

    function markCategoryComplete(sourceId, categoryId, itemCount) {
        var root = loadCheckpoint();
        var source = sourceCheckpoint(root, sourceId);
        source.categories[categoryId] = {
            completedAt: Date.now(),
            itemCount: Math.max(0, Number(itemCount) || 0)
        };
        saveCheckpoint(root);
    }

    function checkpointSummary(source, categories) {
        var root = loadCheckpoint();
        var saved = sourceCheckpoint(root, source.id).categories;
        var now = Date.now();
        var completed = 0;
        var itemCount = 0;
        categories.forEach(function (category) {
            var entry = saved[category.id];
            if (entry && Number(entry.completedAt) > 0 && now - Number(entry.completedAt) < FRESH_MILLIS) {
                completed += 1;
                itemCount += Math.max(0, Number(entry.itemCount) || 0);
            }
        });
        return { saved: saved, completed: completed, itemCount: itemCount };
    }

    function publicStatus(job) {
        if (!job) { return null; }
        return {
            sourceId: job.source.id,
            state: job.state,
            phase: job.phase,
            completed: job.completed,
            total: job.total,
            itemCount: job.itemCount,
            currentCategoryId: job.current ? job.current.id : null,
            errorCode: job.errorCode || null,
            forced: Boolean(job.forced)
        };
    }

    function progress(source, categories) {
        var rows = validCategories(source, categories);
        var summary;
        if (active && active.source.id === source.id) { return publicStatus(active); }
        summary = checkpointSummary(source, rows);
        return {
            sourceId: source.id,
            state: rows.length && summary.completed === rows.length ? 'COMPLETE' : 'IDLE',
            phase: null,
            completed: summary.completed,
            total: rows.length,
            itemCount: summary.itemCount,
            currentCategoryId: null,
            errorCode: null,
            forced: false
        };
    }

    function notify(job) {
        var status = publicStatus(job);
        listeners.slice().forEach(function (listener) {
            try { listener(status); } catch (ignoredListener) {}
        });
        if (job.callbacks && job.callbacks.onStatus) {
            try { job.callbacks.onStatus(status); } catch (ignoredCallback) {}
        }
    }

    function watch(listener) {
        if (typeof listener !== 'function') { return function () {}; }
        listeners.push(listener);
        return function () {
            listeners = listeners.filter(function (candidate) { return candidate !== listener; });
        };
    }

    function finishCancelled(job) {
        if (job.finished) { return; }
        job.finished = true;
        job.state = 'CANCELLED';
        job.phase = null;
        job.current = null;
        job.request = null;
        notify(job);
    }

    function cancel() {
        var job = active;
        if (!job || job.state !== 'RUNNING') { return false; }
        job.cancelRequested = true;
        if (job.retryTimer) {
            window.clearTimeout(job.retryTimer);
            job.retryTimer = null;
            finishCancelled(job);
        } else if (job.phase === 'NETWORK' && job.request && job.request.abort) {
            job.request.abort();
            /* Alguns mocks/plataformas não disparam onabort. */
            job.cancelTimer = window.setTimeout(function () { finishCancelled(job); }, 0);
        } else if (job.phase !== 'PERSISTING') { finishCancelled(job); }
        return true;
    }

    function retryable(error) {
        var code = error && (error.code || error.message);
        return ['NETWORK_ERROR', 'NETWORK_TIMEOUT', 'HTTP_ERROR', 'NETWORK_SETUP_FAILED'].indexOf(code) >= 0;
    }

    function failJob(job, error) {
        if (job.finished) { return; }
        if (job.cancelTimer) { window.clearTimeout(job.cancelTimer); job.cancelTimer = null; }
        if (job.cancelRequested) { finishCancelled(job); return; }
        if (retryable(error) && job.attempt < MAX_NETWORK_ATTEMPTS) {
            job.phase = 'RETRY_WAIT';
            job.retryTimer = window.setTimeout(function () {
                job.retryTimer = null;
                fetchCurrent(job);
            }, RETRY_DELAY_MILLIS);
            notify(job);
            return;
        }
        job.state = 'ERROR';
        job.phase = null;
        job.errorCode = error && (error.code || error.message) || 'CATALOGUE_SYNC_FAILED';
        job.request = null;
        notify(job);
    }

    function finishComplete(job) {
        if (job.finished) { return; }
        job.finished = true;
        job.state = 'COMPLETE';
        job.phase = null;
        job.current = null;
        job.request = null;
        notify(job);
        if (job.callbacks && job.callbacks.onComplete) {
            try { job.callbacks.onComplete(publicStatus(job)); } catch (ignoredComplete) {}
        }
    }

    function nextCategory(job) {
        if (active !== job || job.finished) { return; }
        if (job.cancelRequested) { finishCancelled(job); return; }
        job.current = job.queue.shift() || null;
        job.attempt = 0;
        if (!job.current) { finishComplete(job); return; }
        fetchCurrent(job);
    }

    function persisted(job, category, items, artwork, result) {
        var removed = result && result.removedItemIds || [];
        markCategoryComplete(job.source.id, category.id, items.length);
        job.completed += 1;
        job.itemCount += items.length;
        job.phase = null;
        job.request = null;
        job.current = null;
        if (job.callbacks && job.callbacks.onCategory) {
            try { job.callbacks.onCategory(category, items, artwork || {}, removed); }
            catch (callbackError) { failJob(job, callbackError); return; }
        }
        notify(job);
        if (job.cancelRequested) { finishCancelled(job); return; }
        window.setTimeout(function () { nextCategory(job); }, 0);
    }

    function fetchCurrent(job) {
        var category = job.current;
        var secret;
        var controller;
        if (active !== job || job.finished) { return; }
        if (job.cancelRequested) { finishCancelled(job); return; }
        job.attempt += 1;
        job.phase = 'NETWORK';
        job.errorCode = null;
        notify(job);
        try { secret = job.callbacks.getSecret(job.source.id); }
        catch (error) { failJob(job, error); return; }
        controller = BuroXtream.loadItems(secret, job.source.id, category.contentType, category, function (items, artwork) {
            secret = null;
            job.request = null;
            if (active !== job || job.finished) { return; }
            if (job.cancelRequested) { finishCancelled(job); return; }
            job.phase = 'PERSISTING';
            notify(job);
            BuroStorage.replaceCategoryItems(job.source.id, category.id, items, function (result) {
                persisted(job, category, items, artwork, result);
            }, function (error) { failJob(job, error); });
        }, function (error) {
            secret = null;
            job.request = null;
            failJob(job, error);
        });
        /* Um adapter de teste pode concluir sincronamente antes de devolver o controller. */
        if (!job.finished && job.phase === 'NETWORK' && job.current === category) { job.request = controller || null; }
    }

    function start(source, categories, callbacks, force) {
        var rows;
        var summary;
        var queue;
        var job;
        if (!source || source.type !== 'XTREAM' || !BuroDomain.safeId(source.id)) {
            throw new Error('SOURCE_TYPE_UNAVAILABLE');
        }
        callbacks = callbacks || {};
        if (typeof callbacks.getSecret !== 'function') { throw new Error('CREDENTIALS_REQUIRED'); }
        if (active && active.state === 'RUNNING') { cancel(); }
        rows = validCategories(source, categories);
        reconcile(source.id, rows.map(function (category) { return category.id; }));
        if (force) { resetSource(source.id); }
        summary = checkpointSummary(source, rows);
        queue = rows.filter(function (category) {
            var entry = summary.saved[category.id];
            return force || !entry || !Number(entry.completedAt) ||
                Date.now() - Number(entry.completedAt) >= FRESH_MILLIS;
        });
        active = {
            id: ++nextJobId,
            source: source,
            callbacks: callbacks,
            queue: queue,
            total: rows.length,
            completed: force ? 0 : summary.completed,
            itemCount: force ? 0 : summary.itemCount,
            current: null,
            request: null,
            retryTimer: null,
            cancelTimer: null,
            state: queue.length ? 'RUNNING' : 'COMPLETE',
            phase: null,
            attempt: 0,
            forced: Boolean(force),
            cancelRequested: false,
            finished: !queue.length,
            errorCode: null
        };
        job = active;
        notify(active);
        if (queue.length) { window.setTimeout(function () { nextCategory(job); }, 0); }
        return publicStatus(active);
    }

    function contains(sourceId, categoryId) {
        if (!active || active.source.id !== sourceId || active.state !== 'RUNNING') { return false; }
        if (active.current && active.current.id === categoryId) { return true; }
        return active.queue.some(function (category) { return category.id === categoryId; });
    }

    return {
        start: start,
        cancel: cancel,
        watch: watch,
        progress: progress,
        contains: contains,
        reconcile: reconcile,
        resetSource: resetSource,
        clearSource: clearSource,
        markCategoryComplete: markCategoryComplete,
        _checkpointKey: CHECKPOINT_KEY
    };
}());
