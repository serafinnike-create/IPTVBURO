/*
  Downloads para armazenamento USB.

  Segue o ADR-008, que liberou o download de VOD mantendo três limites que não
  são estéticos:

    1. TV ao vivo é recusada. Um stream ao vivo não termina; baixá-lo cresceria
       até encher o disco.
    2. Nenhuma URL ou credencial vai para o disco. A URL é resolvida em memória
       na hora de iniciar, nunca é gravada, registrada em log ou usada no nome
       do arquivo — o nome vem da identidade de conteúdo.
    3. Conteúdo protegido é gravado como recebido. O app não tenta contornar
       criptografia.

  O arquivo é escrito com sufixo `.part` e renomeado só ao concluir, para que
  uma interrupção não deixe um arquivo truncado parecendo completo.

  O que muda em relação ao Windows: o disco pode ser arrancado no meio da
  gravação. Um USB removido pausa a fila em vez de acumular falhas.
*/
var BuroDownloads = (function () {
    'use strict';

    /* Fica na raiz do volume para o usuário achar o arquivo num computador. */
    var FOLDER_NAME = 'IPTV BURO';
    var PARTIAL_SUFFIX = '.part';
    var SNAPSHOT_KEY = 'iptvburo.downloads.v1';
    var MAX_SNAPSHOT_ENTRIES = 200;
    var RATE_SAMPLE_MIN_MILLIS = 500;
    var RATE_SMOOTHING = 0.3;

    /* Só VOD. Ver restrição 1 do ADR-008. */
    var DOWNLOADABLE = { MOVIE: true, EPISODE: true };

    var queue = [];
    var byId = {};
    var watchers = [];
    var storageWatched = false;
    var restored = false;

    function available() {
        var exposed = Boolean(
            typeof tizen !== 'undefined' &&
            tizen.download &&
            tizen.DownloadRequest &&
            BuroUsb.available()
        );
        if (!exposed) { return false; }
        if (tizen.systeminfo && typeof tizen.systeminfo.getCapability === 'function') {
            try {
                if (!tizen.systeminfo.getCapability('http://tizen.org/feature/download')) { return false; }
            } catch (ignoredCapability) { return false; }
        }
        return true;
    }

    /* A função só existe com um USB montado: o armazenamento interno da TV
       não comporta vídeo. */
    function enabled() {
        return available() && BuroUsb.hasStorage();
    }

    function persistSnapshot() {
        var snapshot;
        try {
            snapshot = queue.slice(0, MAX_SNAPSHOT_ENTRIES).map(function (entry) {
                return {
                    id: entry.id,
                    name: entry.name,
                    contentType: entry.contentType,
                    fileName: entry.fileName,
                    state: entry.state,
                    receivedBytes: entry.receivedBytes,
                    totalBytes: entry.totalBytes,
                    errorCode: entry.errorCode,
                    platformId: entry.platformId
                };
            });
            localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot));
        } catch (ignoredPersistence) { /* A fila continua funcional somente nesta sessão. */ }
    }

    function notify() {
        persistSnapshot();
        var snapshot = list();
        watchers.forEach(function (watcher) {
            try { watcher(snapshot); } catch (ignored) { /* Um observador não derruba os outros. */ }
        });
    }

    function watch(callback) {
        if (typeof callback === 'function') { watchers.push(callback); }
        if (!restored && available()) { restore(); }
        if (!storageWatched && BuroUsb.available()) {
            storageWatched = true;
            BuroUsb.watch(function (mounted) {
                if (!mounted.length) { pauseAllForMissingStorage(); }
                notify();
            });
        }
    }

    function unwatch(callback) {
        watchers = watchers.filter(function (item) { return item !== callback; });
    }

    /*
      Nome derivado da identidade de conteúdo, nunca da URL.

      `movie:42` vira `movie-42`. Sem isso o nome do arquivo poderia carregar
      usuário e senha embutidos no endereço do provedor — que é exatamente o
      que a restrição 2 do ADR-008 proíbe.
    */
    function safeFileName(item) {
        var identity = BuroDomain.contentIdentity(item);
        var base = String(identity)
            .replace(/[^a-zA-Z0-9]+/g, '-')
            .replace(/^-+|-+$/g, '')
            .slice(0, 80);
        var extensionValue = item && (item.extension || (item.locator && item.locator.extension));
        var extension = String(extensionValue || 'mp4')
            .replace(/[^a-zA-Z0-9]/g, '')
            .slice(0, 5);
        return (base || 'download') + '.' + (extension || 'mp4');
    }

    function entryFor(id) { return byId[id] || null; }

    function safeSnapshotEntry(value) {
        var stateValues = {
            QUEUED: true, DOWNLOADING: true, PAUSED: true, STORAGE_MISSING: true,
            CANCELLED: true, COMPLETED: true, FAILED: true
        };
        var id = String(value && value.id || '').substring(0, 200);
        var fileName = String(value && value.fileName || '').substring(0, 96);
        var contentType = value && value.contentType;
        var platformId = Number(value && value.platformId);
        var state = value && stateValues[value.state] ? value.state : 'FAILED';
        if (!id || !DOWNLOADABLE[contentType] ||
                !/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}$/.test(fileName) || fileName.indexOf('..') >= 0) { return null; }
        return {
            id: id,
            name: String(value.name || '').substring(0, 240),
            contentType: contentType,
            fileName: fileName,
            state: state,
            receivedBytes: Math.max(0, Number(value.receivedBytes) || 0),
            totalBytes: Math.max(0, Number(value.totalBytes) || 0),
            errorCode: /^[a-zA-Z0-9_.-]{1,64}$/.test(String(value.errorCode || '')) ? String(value.errorCode) : null,
            platformId: isFinite(platformId) && platformId >= 0 ? Math.floor(platformId) : null,
            bytesPerSecond: 0,
            rateSampleAt: null,
            rateSampleBytes: 0
        };
    }

    function recoveredCompletion(entry) {
        var request = null;
        var path = '';
        try {
            request = tizen.download.getDownloadRequest(entry.platformId);
            path = String(request.destination || '').replace(/\/$/, '') + '/' + String(request.fileName || '');
        } catch (ignoredRequest) { path = ''; }
        request = null;
        if (path && /\.part$/i.test(path)) { finalize(entry, path); }
        else { setState(entry, 'COMPLETED'); }
    }

    function reconcile(entry) {
        var platformState;
        if (entry.state === 'COMPLETED' || entry.state === 'CANCELLED' || entry.state === 'FAILED') { return; }
        if (entry.platformId === null || typeof tizen.download.getState !== 'function') {
            setState(entry, 'FAILED', 'DOWNLOAD_INTERRUPTED'); return;
        }
        try {
            platformState = tizen.download.getState(entry.platformId);
            if (platformState === 'COMPLETED') { recoveredCompletion(entry); return; }
            if (platformState === 'CANCELED') { setState(entry, 'CANCELLED'); return; }
            if (platformState === 'FAILED') { setState(entry, 'FAILED', 'DOWNLOAD_INTERRUPTED'); return; }
            if (platformState === 'QUEUED' || platformState === 'DOWNLOADING' || platformState === 'PAUSED') {
                entry.state = platformState;
                if (typeof tizen.download.setListener === 'function') {
                    tizen.download.setListener(entry.platformId, callbacksFor(entry));
                }
                notify();
                return;
            }
            setState(entry, 'FAILED', 'DOWNLOAD_INTERRUPTED');
        } catch (ignoredState) { setState(entry, 'FAILED', 'DOWNLOAD_INTERRUPTED'); }
    }

    function restore() {
        var raw;
        var values;
        if (restored || !available()) { return false; }
        restored = true;
        try {
            raw = localStorage.getItem(SNAPSHOT_KEY);
            values = raw ? JSON.parse(raw) : [];
        } catch (ignoredSnapshot) { values = []; }
        if (!Array.isArray(values)) { values = []; }
        queue = [];
        byId = {};
        values.slice(0, MAX_SNAPSHOT_ENTRIES).forEach(function (value) {
            var entry = safeSnapshotEntry(value);
            if (!entry || byId[entry.id]) { return; }
            byId[entry.id] = entry;
            queue.push(entry);
        });
        queue.slice().forEach(reconcile);
        notify();
        return true;
    }

    function stateFor(item) {
        var entry = item ? entryFor(BuroDomain.contentIdentity(item)) : null;
        return entry ? entry.state : null;
    }

    /*
      Mesma regra do Android para uma ação em lote: episódios concluídos já
      têm os bytes no disco e são ignorados; os demais permanecem elegíveis.
      A ordem T/E é também a ordem entregue ao DownloadManager, permitindo que
      o começo da série chegue primeiro quando a plataforma mantém uma fila.
    */
    function bulkCandidates(items, seasonNumber) {
        var selectedSeason = seasonNumber == null ? null : Number(seasonNumber);
        return (items || []).filter(function (item) {
            var season = Number(item && item.locator && item.locator.season) || 0;
            return item && item.contentType === 'EPISODE' &&
                (selectedSeason == null || season === selectedSeason) &&
                stateFor(item) !== 'COMPLETED';
        }).slice().sort(function (left, right) {
            var leftSeason = Number(left.locator && left.locator.season) || 0;
            var rightSeason = Number(right.locator && right.locator.season) || 0;
            var leftEpisode = Number(left.locator && left.locator.episode);
            var rightEpisode = Number(right.locator && right.locator.episode);
            if (leftSeason !== rightSeason) { return leftSeason - rightSeason; }
            if (!isFinite(leftEpisode) || leftEpisode <= 0) { leftEpisode = Number.MAX_VALUE; }
            if (!isFinite(rightEpisode) || rightEpisode <= 0) { rightEpisode = Number.MAX_VALUE; }
            if (leftEpisode !== rightEpisode) { return leftEpisode - rightEpisode; }
            return String(left.id || '').localeCompare(String(right.id || ''));
        });
    }

    function list() {
        return queue.map(function (entry) {
            var bytesPerSecond = Math.max(0, Math.floor(Number(entry.bytesPerSecond) || 0));
            var remainingBytes = Math.max(0, Number(entry.totalBytes) - Number(entry.receivedBytes));
            return {
                id: entry.id,
                name: entry.name,
                contentType: entry.contentType,
                fileName: entry.fileName,
                state: entry.state,
                receivedBytes: entry.receivedBytes,
                totalBytes: entry.totalBytes,
                percent: entry.totalBytes
                    ? Math.max(0, Math.min(100, Math.floor((entry.receivedBytes / entry.totalBytes) * 100)))
                    : 0,
                errorCode: entry.errorCode,
                bytesPerSecond: bytesPerSecond,
                remainingSeconds: bytesPerSecond > 0 && remainingBytes > 0
                    ? Math.floor(remainingBytes / bytesPerSecond)
                    : null
            };
        });
    }

    /*
      Velocidade e estimativa pertencem somente à sessão atual. Persistir uma
      taxa faria a TV mostrar um número antigo depois de pausar ou reiniciar.
      O snapshot acima continua deliberadamente sem estes campos.
    */
    function resetTransferMetrics(entry) {
        entry.bytesPerSecond = 0;
        entry.rateSampleAt = null;
        entry.rateSampleBytes = Math.max(0, Number(entry.receivedBytes) || 0);
    }

    function observeProgress(entry, received, total) {
        var now = Date.now();
        var receivedBytes = Math.max(0, Number(received) || 0);
        var totalBytes = Math.max(0, Number(total) || 0);
        var elapsed;
        var delta;
        var instant;

        entry.receivedBytes = receivedBytes;
        entry.totalBytes = totalBytes;

        if (entry.rateSampleAt === null || receivedBytes < entry.rateSampleBytes) {
            entry.rateSampleAt = now;
            entry.rateSampleBytes = receivedBytes;
            entry.bytesPerSecond = 0;
            return;
        }

        elapsed = now - entry.rateSampleAt;
        if (elapsed < RATE_SAMPLE_MIN_MILLIS) { return; }

        delta = receivedBytes - entry.rateSampleBytes;
        instant = delta > 0 ? delta * 1000 / elapsed : 0;
        entry.bytesPerSecond = entry.bytesPerSecond > 0
            ? entry.bytesPerSecond * (1 - RATE_SMOOTHING) + instant * RATE_SMOOTHING
            : instant;
        entry.rateSampleAt = now;
        entry.rateSampleBytes = receivedBytes;
    }

    function setState(entry, state, errorCode) {
        if (state !== 'DOWNLOADING' || entry.state !== 'DOWNLOADING') {
            resetTransferMetrics(entry);
        }
        entry.state = state;
        entry.errorCode = errorCode || null;
        notify();
    }

    /*
      O usuário arrancou o pendrive. Marcamos como pausado por falta de disco
      em vez de falha: a gravação pode continuar quando ele voltar, e uma lista
      cheia de erros vermelhos sugeriria que o app quebrou.
    */
    function pauseAllForMissingStorage() {
        queue.forEach(function (entry) {
            if (entry.state === 'DOWNLOADING' || entry.state === 'QUEUED') {
                try {
                    if (entry.platformId !== null) { tizen.download.pause(entry.platformId); }
                } catch (ignored) { /* A plataforma já pode ter cancelado sozinha. */ }
                setState(entry, 'STORAGE_MISSING');
            }
        });
    }

    function callbacksFor(entry) {
        return {
            onprogress: function (id, received, total) {
                if (entry.state !== 'DOWNLOADING') {
                    resetTransferMetrics(entry);
                    entry.state = 'DOWNLOADING';
                }
                observeProgress(entry, received, total);
                notify();
            },
            onpaused: function () { setState(entry, 'PAUSED'); },
            oncanceled: function () { setState(entry, 'CANCELLED'); },
            oncompleted: function (id, fullPath) {
                /*
                  O `.part` vira o nome final só agora. Se a TV desligar antes
                  disto, o que sobra é um `.part` — reconhecível como incompleto
                  em vez de um vídeo que corta no meio.
                */
                finalize(entry, fullPath);
            },
            onfailed: function (id, error) {
                /* Só o código: a mensagem da plataforma pode conter a URL. */
                setState(entry, 'FAILED', (error && error.name) ? error.name : 'DOWNLOAD_FAILED');
            }
        };
    }

    function finalize(entry, fullPath) {
        if (!tizen.filesystem || !tizen.filesystem.resolve) {
            setState(entry, 'COMPLETED');
            return;
        }
        /*
          `moveTo` pertence ao diretório, não ao arquivo: resolvemos a pasta e
          renomeamos de lá. O arquivo já está completo neste ponto, então uma
          falha no rename deixa um `.part` íntegro — chato, mas honesto.
        */
        var separator = String(fullPath).lastIndexOf('/');
        var directoryPath = separator > 0 ? String(fullPath).slice(0, separator) : '';
        var partialName = String(fullPath).slice(separator + 1);

        if (!directoryPath) { setState(entry, 'COMPLETED'); return; }

        tizen.filesystem.resolve(directoryPath, function (directory) {
            try {
                directory.moveTo(partialName, entry.fileName, true, function () {
                    setState(entry, 'COMPLETED');
                }, function () {
                    setState(entry, 'COMPLETED');
                });
            } catch (ignored) {
                setState(entry, 'COMPLETED');
            }
        }, function () {
            setState(entry, 'COMPLETED');
        }, 'rw');
    }

    function startPlatformDownload(entry, directory, url, success, failure) {
        var request;
        if (!url || byId[entry.id] !== entry || entry.state === 'CANCELLED') {
            if (byId[entry.id] === entry && entry.state !== 'CANCELLED') {
                setState(entry, 'FAILED', 'SOURCE_UNRESOLVED');
            }
            failure({ code: entry.state === 'CANCELLED' ? 'DOWNLOAD_CANCELLED' : 'SOURCE_UNRESOLVED' });
            return;
        }
        try {
            request = new tizen.DownloadRequest(url, directory.fullPath, entry.fileName + PARTIAL_SUFFIX);
            entry.platformId = tizen.download.start(request, callbacksFor(entry));
            request = null;
            url = null;
            if (!isFinite(Number(entry.platformId)) || Number(entry.platformId) < 0) {
                entry.platformId = null;
                setState(entry, 'FAILED', 'DOWNLOAD_REJECTED');
                failure({ code: 'DOWNLOAD_REJECTED' });
                return;
            }
            setState(entry, 'DOWNLOADING');
            success(entry.id);
        } catch (error) {
            request = null;
            url = null;
            setState(entry, 'FAILED', 'DOWNLOAD_REJECTED');
            failure({ code: 'DOWNLOAD_REJECTED' });
        }
    }

    /*
      O resolver assíncrono roda depois de o destino USB estar confirmado. Ele
      entrega a URL diretamente ao DownloadRequest e o adapter a descarta; nem
      o item, nem a fila, nem o snapshot recebem esse valor.
    */
    function startAsync(item, resolveUrlAsync, success, failure) {
        var contentType = item && item.contentType;
        var entry;

        if (!DOWNLOADABLE[contentType]) {
            failure({ code: 'LIVE_NOT_DOWNLOADABLE' });
            return;
        }
        if (!available()) {
            failure({ code: 'DOWNLOAD_UNAVAILABLE' });
            return;
        }
        if (!BuroUsb.hasStorage()) {
            failure({ code: 'STORAGE_REQUIRED' });
            return;
        }

        entry = {
            id: BuroDomain.contentIdentity(item),
            name: item.name || '',
            contentType: contentType,
            fileName: safeFileName(item),
            state: 'QUEUED',
            receivedBytes: 0,
            totalBytes: 0,
            errorCode: null,
            platformId: null,
            bytesPerSecond: 0,
            rateSampleAt: null,
            rateSampleBytes: 0
        };

        if (byId[entry.id]) { failure({ code: 'ALREADY_QUEUED' }); return; }

        byId[entry.id] = entry;
        queue.push(entry);
        notify();

        BuroUsb.resolveTarget(
            BuroUsb.mountedStorages()[0].label,
            FOLDER_NAME,
            function (directory) {
                var settled = false;
                function resolved(url) {
                    if (settled) { return; }
                    settled = true;
                    startPlatformDownload(entry, directory, url, success, failure);
                    url = null;
                }
                function unresolved() {
                    if (settled) { return; }
                    settled = true;
                    if (byId[entry.id] === entry && entry.state !== 'CANCELLED') {
                        setState(entry, 'FAILED', 'SOURCE_UNRESOLVED');
                    }
                    failure({ code: 'SOURCE_UNRESOLVED' });
                }
                try {
                    resolveUrlAsync(resolved, unresolved);
                } catch (error) { unresolved(); }
            },
            function (error) {
                setState(entry, 'FAILED', error.code || 'TARGET_UNWRITABLE');
                failure(error);
            }
        );
    }

    function start(item, resolveUrl, success, failure) {
        startAsync(item, function (resolved, unresolved) {
            var url;
            try { url = resolveUrl(); }
            catch (error) { unresolved(); return; }
            if (url) { resolved(url); }
            else { unresolved(); }
            url = null;
        }, success, failure);
    }

    function cancel(id) {
        var entry = entryFor(id);
        if (!entry) { return; }
        try {
            if (entry.platformId !== null) { tizen.download.cancel(entry.platformId); }
        } catch (ignored) { /* Já terminou ou foi cancelado pela plataforma. */ }
        setState(entry, 'CANCELLED');
    }

    function pause(id) {
        var entry = entryFor(id);
        if (!entry || entry.state !== 'DOWNLOADING') { return; }
        try { tizen.download.pause(entry.platformId); }
        catch (ignored) { /* A plataforma decide; o estado chega pelo callback. */ }
    }

    function resume(id) {
        var entry = entryFor(id);
        if (!entry) { return; }
        if (!BuroUsb.hasStorage()) { setState(entry, 'STORAGE_MISSING'); return; }
        try { tizen.download.resume(entry.platformId); }
        catch (ignored) { setState(entry, 'FAILED', 'RESUME_REJECTED'); }
    }

    /*
      Resolve uma cópia concluída somente quando o usuário aperta Assistir.

      O snapshot guarda o nome seguro do arquivo, mas nunca o caminho físico do
      pendrive. Por isso procuramos o mesmo nome em cada volume removível que
      estiver realmente montado. A URI `file:` existe apenas durante o callback
      e vai diretamente para o AVPlay, como a URL de rede no fluxo normal.
    */
    function resolveCompletedFile(id, success, failure) {
        var entry = entryFor(id);
        var storages;
        var storageIndex = 0;
        var candidateNames;
        if (!entry || entry.state !== 'COMPLETED') {
            failure({ code: 'DOWNLOAD_NOT_COMPLETED' }); return;
        }
        if (!BuroUsb.hasStorage()) { failure({ code: 'STORAGE_REQUIRED' }); return; }
        if (!tizen.filesystem || typeof tizen.filesystem.resolve !== 'function') {
            failure({ code: 'FILESYSTEM_UNAVAILABLE' }); return;
        }
        storages = BuroUsb.mountedStorages();
        candidateNames = [entry.fileName, entry.fileName + PARTIAL_SUFFIX];

        function nextStorage() {
            var storage;
            var candidateIndex = 0;
            if (storageIndex >= storages.length) { failure({ code: 'OFFLINE_FILE_MISSING' }); return; }
            storage = storages[storageIndex]; storageIndex += 1;

            function nextCandidate() {
                var path;
                if (candidateIndex >= candidateNames.length) { nextStorage(); return; }
                path = storage.label + '/' + FOLDER_NAME + '/' + candidateNames[candidateIndex];
                candidateIndex += 1;
                tizen.filesystem.resolve(path, function (file) {
                    var uri = null;
                    try { uri = file && !file.isDirectory && file.toURI ? String(file.toURI()) : null; }
                    catch (ignoredUri) { uri = null; }
                    file = null;
                    path = null;
                    if (!uri || uri.length > 4096 || !/^file:\/\//i.test(uri)) { nextCandidate(); return; }
                    success(uri, { id: entry.id, name: entry.name, contentType: entry.contentType });
                    uri = null;
                }, function () { path = null; nextCandidate(); }, 'r');
            }
            nextCandidate();
        }
        nextStorage();
    }

    function remove(id) {
        var entry = entryFor(id);
        if (!entry) { return; }
        if (entry.state === 'DOWNLOADING' || entry.state === 'QUEUED') { cancel(id); }
        delete byId[id];
        queue = queue.filter(function (item) { return item.id !== id; });
        notify();
    }

    function clearForTesting() {
        queue = [];
        byId = {};
        watchers = [];
        storageWatched = false;
        restored = false;
        try { localStorage.removeItem(SNAPSHOT_KEY); } catch (ignoredStorage) {}
    }

    return {
        available: available,
        enabled: enabled,
        downloadable: function (contentType) { return Boolean(DOWNLOADABLE[contentType]); },
        safeFileName: safeFileName,
        restore: restore,
        stateFor: stateFor,
        bulkCandidates: bulkCandidates,
        watch: watch,
        unwatch: unwatch,
        list: list,
        start: start,
        startAsync: startAsync,
        cancel: cancel,
        pause: pause,
        resume: resume,
        resolveCompletedFile: resolveCompletedFile,
        remove: remove,
        clearForTesting: clearForTesting
    };
}());
