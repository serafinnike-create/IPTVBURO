/*
  Armazenamento removível (pendrive, HD ou SSD ligado à TV).

  Existe porque o armazenamento interno do app Tizen é pequeno demais para
  vídeo. Com um USB montado o download passa a fazer sentido, e sem ele a
  função inteira fica escondida — a capability `offline` é derivada daqui em
  tempo real, nunca declarada como verdadeira de forma fixa.

  O detalhe que separa isto de um disco comum: o usuário pode arrancar o
  pendrive no meio de uma gravação. Por isso o estado é observado, e não lido
  uma vez só.
*/
var BuroUsb = (function () {
    'use strict';

    /* A Samsung rotula todo volume externo com este prefixo. */
    var REMOVABLE_PREFIX = 'removable_';
    var MOUNTED = 'MOUNTED';

    var watchers = [];
    var listenerId = null;
    var lastKnown = [];
    var imageFiles = {};
    var playlistFiles = {};
    var MAX_IMAGE_FILES = 60;
    var MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    var MAX_PLAYLIST_FILES = 40;
    var MAX_PLAYLIST_BYTES = 16 * 1024 * 1024;

    function available() {
        return Boolean(
            typeof tizen !== 'undefined' &&
            tizen.filesystem &&
            tizen.filesystem.listStorages
        );
    }

    function isRemovable(storage) {
        return Boolean(storage) &&
            String(storage.label || '').indexOf(REMOVABLE_PREFIX) === 0;
    }

    function isMounted(storage) {
        return isRemovable(storage) && storage.state === MOUNTED;
    }

    /*
      Só o que a interface precisa saber. O caminho real do volume não é
      exposto: ele não acrescenta nada à decisão de mostrar ou esconder o
      botão, e mantê-lo fora daqui evita que apareça em log por descuido.
    */
    function describe(storage) {
        return {
            label: storage.label,
            mounted: storage.state === MOUNTED
        };
    }

    function notify() {
        var mounted = lastKnown.filter(isMounted).map(describe);
        watchers.forEach(function (watcher) {
            try { watcher(mounted); } catch (ignored) { /* Um observador não derruba os outros. */ }
        });
    }

    function refresh(done, failed) {
        if (!available()) {
            lastKnown = [];
            if (failed) { failed({ code: 'FILESYSTEM_UNAVAILABLE' }); }
            return;
        }
        tizen.filesystem.listStorages(function (storages) {
            lastKnown = (storages || []).filter(isRemovable);
            notify();
            if (done) { done(lastKnown.filter(isMounted).map(describe)); }
        }, function () {
            lastKnown = [];
            notify();
            if (failed) { failed({ code: 'STORAGE_LIST_FAILED' }); }
        });
    }

    /*
      O listener da plataforma dispara ao montar e ao remover, mas entrega
      apenas o volume que mudou. Relemos a lista inteira para que um segundo
      pendrive ainda ligado não seja esquecido quando o primeiro sai.
    */
    function watch(callback) {
        if (typeof callback === 'function') { watchers.push(callback); }

        if (listenerId === null && available() && tizen.filesystem.addStorageStateChangeListener) {
            try {
                listenerId = tizen.filesystem.addStorageStateChangeListener(function () {
                    refresh();
                }, function () { /* Continuamos com o último estado conhecido. */ });
            } catch (ignored) {
                listenerId = null;
            }
        }

        refresh();
    }

    function unwatch(callback) {
        watchers = watchers.filter(function (item) { return item !== callback; });
    }

    function mountedStorages() {
        return lastKnown.filter(isMounted).map(describe);
    }

    function hasStorage() {
        return mountedStorages().length > 0;
    }

    /*
      Resolve a pasta de destino em modo leitura e escrita.

      A pasta é criada na raiz do volume para o usuário encontrar o arquivo
      num computador depois — um diretório escondido dentro do sandbox do app
      seria inútil para quem quer levar o filme embora.
    */
    function resolveTarget(label, folderName, success, failure) {
        if (!available() || !tizen.filesystem.resolve) {
            failure({ code: 'FILESYSTEM_UNAVAILABLE' });
            return;
        }
        tizen.filesystem.resolve(label, function (root) {
            var existing = null;
            try {
                existing = root.resolve(folderName);
            } catch (notThere) {
                existing = null;
            }
            if (existing) { success(existing); return; }
            try {
                success(root.createDirectory(folderName));
            } catch (error) {
                failure({ code: 'TARGET_UNWRITABLE' });
            }
        }, function () {
            failure({ code: 'STORAGE_UNAVAILABLE' });
        }, 'rw');
    }

    function imageMime(name) {
        var extension = String(name || '').toLowerCase().split('.').pop();
        return { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' }[extension] || null;
    }

    /* Lista somente imagens pequenas, em profundidade limitada. Caminhos completos ficam
       dentro deste módulo e nunca são persistidos nem apresentados na interface. */
    function listImages(success, failure) {
        var storages = lastKnown.filter(isMounted);
        var results = [];
        var storageIndex = 0;
        var serial = 0;
        imageFiles = {};
        if (!available() || !tizen.filesystem.resolve) { failure({ code: 'FILESYSTEM_UNAVAILABLE' }); return; }
        if (!storages.length) { success([]); return; }
        function nextStorage() {
            var storage;
            if (results.length >= MAX_IMAGE_FILES || storageIndex >= storages.length) { success(results); return; }
            storage = storages[storageIndex]; storageIndex += 1;
            tizen.filesystem.resolve(storage.label, function (root) {
                var queue = [{ file: root, depth: 0 }];
                function nextDirectory() {
                    var current;
                    if (results.length >= MAX_IMAGE_FILES || !queue.length) { nextStorage(); return; }
                    current = queue.shift();
                    current.file.listFiles(function (children) {
                        (children || []).forEach(function (file) {
                            var mime;
                            var size;
                            var key;
                            if (results.length >= MAX_IMAGE_FILES) { return; }
                            if (file && file.isDirectory && current.depth < 2) {
                                queue.push({ file: file, depth: current.depth + 1 }); return;
                            }
                            mime = imageMime(file && file.name); size = Number(file && file.fileSize) || 0;
                            if (!mime || size <= 0 || size > MAX_IMAGE_BYTES) { return; }
                            key = 'usb-photo-' + serial; serial += 1; imageFiles[key] = { file: file, mime: mime };
                            results.push({ key: key, name: String(file.name).substring(0, 100), size: size });
                        });
                        nextDirectory();
                    }, function () { nextDirectory(); });
                }
                nextDirectory();
            }, function () { nextStorage(); }, 'r');
        }
        nextStorage();
    }

    function imagePreviewUrl(key) {
        var entry = imageFiles[String(key || '')];
        try { return entry && entry.file && entry.file.toURI ? entry.file.toURI() : null; }
        catch (ignoredUri) { return null; }
    }

    function readImage(key, success, failure) {
        var entry = imageFiles[String(key || '')];
        var size = Number(entry && entry.file && entry.file.fileSize) || 0;
        if (!entry || size <= 0 || size > MAX_IMAGE_BYTES || !entry.file.openStream) {
            failure({ code: 'PHOTO_UNAVAILABLE' }); return;
        }
        entry.file.openStream('r', function (stream) {
            var encoded;
            try {
                encoded = stream.readBase64(size);
                stream.close();
                if (!encoded || encoded.length > Math.ceil(MAX_IMAGE_BYTES * 4 / 3) + 16) {
                    failure({ code: 'PHOTO_TOO_LARGE' }); return;
                }
                success('data:' + entry.mime + ';base64,' + encoded);
            } catch (error) {
                try { stream.close(); } catch (ignoredClose) { /* nada */ }
                failure({ code: 'PHOTO_READ_FAILED' });
            }
        }, function () { failure({ code: 'PHOTO_READ_FAILED' }); }, 'ISO-8859-1');
    }

    function playlistFile(name) {
        return /\.(m3u|m3u8)$/i.test(String(name || ''));
    }

    function playlistKey(storage, file) {
        return 'usb-playlist-' + BuroDomain.stableHash(
            String(storage && storage.label || '') + '\n' + String(file && file.fullPath || file && file.name || '') +
            '\n' + String(Number(file && file.fileSize) || 0)
        );
    }

    /* Lista M3U/M3U8 em profundidade limitada. O caminho entra apenas no hash opaco
       e nunca é devolvido à UI ou persistido. */
    function listPlaylists(success, failure) {
        var storages = lastKnown.filter(isMounted);
        var results = [];
        var storageIndex = 0;
        playlistFiles = {};
        if (!available() || !tizen.filesystem.resolve) { failure({ code: 'FILESYSTEM_UNAVAILABLE' }); return; }
        if (!storages.length) { success([]); return; }

        function nextStorage() {
            var storage;
            if (results.length >= MAX_PLAYLIST_FILES || storageIndex >= storages.length) { success(results); return; }
            storage = storages[storageIndex]; storageIndex += 1;
            tizen.filesystem.resolve(storage.label, function (root) {
                var queue = [{ file: root, depth: 0 }];
                function nextDirectory() {
                    var current;
                    if (results.length >= MAX_PLAYLIST_FILES || !queue.length) { nextStorage(); return; }
                    current = queue.shift();
                    current.file.listFiles(function (children) {
                        (children || []).forEach(function (file) {
                            var size;
                            var key;
                            var descriptor;
                            if (results.length >= MAX_PLAYLIST_FILES) { return; }
                            if (file && file.isDirectory && current.depth < 2) {
                                queue.push({ file: file, depth: current.depth + 1 }); return;
                            }
                            size = Number(file && file.fileSize) || 0;
                            if (!playlistFile(file && file.name) || size <= 0 || size > MAX_PLAYLIST_BYTES || !file.openStream) { return; }
                            key = playlistKey(storage, file);
                            descriptor = { key: key, name: String(file.name).substring(0, 120), size: size };
                            playlistFiles[key] = { file: file, descriptor: descriptor };
                            results.push(descriptor);
                        });
                        nextDirectory();
                    }, function () { nextDirectory(); });
                }
                nextDirectory();
            }, function () { nextStorage(); }, 'r');
        }
        nextStorage();
    }

    function readPlaylist(key, success, failure) {
        var entry = playlistFiles[String(key || '')];
        var size = Number(entry && entry.file && entry.file.fileSize) || 0;
        if (!entry || size <= 0 || size > MAX_PLAYLIST_BYTES || !entry.file.openStream) {
            failure({ code: 'PLAYLIST_UNAVAILABLE' }); return;
        }
        entry.file.openStream('r', function (stream) {
            var text;
            try {
                text = stream.read(size);
                stream.close();
                if (!text || text.length > MAX_PLAYLIST_BYTES) { failure({ code: 'PLAYLIST_TOO_LARGE' }); return; }
                success(text, entry.descriptor);
                text = null;
            } catch (error) {
                try { stream.close(); } catch (ignoredClose) { /* nada */ }
                failure({ code: 'PLAYLIST_READ_FAILED' });
            }
        }, function () { failure({ code: 'PLAYLIST_READ_FAILED' }); }, 'UTF-8');
    }

    /* Reencontra a seleção após reinício. O token é preferido; nome+tamanho é
       fallback apenas quando identifica um único arquivo. */
    function resolvePlaylist(selector, success, failure) {
        var safe = selector || {};
        listPlaylists(function (files) {
            var selected = files.filter(function (file) { return file.key === safe.playlistToken; })[0];
            var fallback;
            if (!selected && safe.fileName && Number(safe.fileSize) > 0) {
                fallback = files.filter(function (file) {
                    return file.name === safe.fileName && Number(file.size) === Number(safe.fileSize);
                });
                if (fallback.length === 1) { selected = fallback[0]; }
            }
            if (!selected) { failure({ code: 'PLAYLIST_UNAVAILABLE' }); return; }
            readPlaylist(selected.key, success, failure);
        }, failure);
    }

    return {
        available: available,
        watch: watch,
        unwatch: unwatch,
        refresh: refresh,
        hasStorage: hasStorage,
        mountedStorages: mountedStorages,
        resolveTarget: resolveTarget,
        listImages: listImages,
        imagePreviewUrl: imagePreviewUrl,
        readImage: readImage,
        listPlaylists: listPlaylists,
        readPlaylist: readPlaylist,
        resolvePlaylist: resolvePlaylist
    };
}());
