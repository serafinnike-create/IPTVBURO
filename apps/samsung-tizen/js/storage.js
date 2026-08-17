/* Persistence adapters. Sensitive values are accepted only by Tizen KeyManager. */
var BuroStorage = (function () {
    'use strict';

    var PREFERENCES_KEY = 'iptvburo.preferences.v1';
    var DB_NAME = 'iptvburo.catalog';
    /* v3 acrescenta o store `reminders`. Ver configureSchema. */
    var DB_VERSION = 3;
    var db = null;

    function searchRank(contentType) {
        if (contentType === 'MOVIE') { return 0; }
        if (contentType === 'SERIES') { return 1; }
        return 2;
    }

    function searchableItem(value) {
        var row = {};
        var order = Number(value && value.sortOrder);
        Object.keys(value || {}).forEach(function (key) { row[key] = value[key]; });
        row.searchName = BuroDomain.foldAccents(value && value.name || '');
        row.searchRank = searchRank(value && value.contentType);
        row.searchSort = isFinite(order) ? order : 0;
        return row;
    }

    function defaultPreferences() {
        return {
            acceptedLegal: false,
            language: 'pt-BR',
            languageSelected: false,
            activeProfileId: null,
            section: 'HOME',
            reducedMotion: false,
            highContrast: false,
            reducedTransparency: false,
            hiddenCategoryIds: [],
            lockedCategoryIds: [],
            lockAdultCategories: true,
            parentalPin: null,
            subtitleSize: 'medium',
            subtitleColour: 'white',
            subtitleBackground: true,
            tmdbRegion: 'BR'
        };
    }

    function loadPreferences() {
        var defaults = defaultPreferences();
        var raw;
        var parsed;
        try {
            raw = localStorage.getItem(PREFERENCES_KEY);
            parsed = raw ? JSON.parse(raw) : {};
        } catch (error) {
            parsed = {};
        }
        Object.keys(defaults).forEach(function (key) {
            if (parsed[key] !== undefined) { defaults[key] = parsed[key]; }
        });
        return defaults;
    }

    function savePreferences(preferences) {
        var safe = defaultPreferences();
        Object.keys(safe).forEach(function (key) { safe[key] = preferences[key]; });
        localStorage.setItem(PREFERENCES_KEY, JSON.stringify(safe));
    }

    function configureSchema(database, transaction, oldVersion) {
        var store;
        if (!database.objectStoreNames.contains('profiles')) {
            database.createObjectStore('profiles', { keyPath: 'id' });
        }
        if (!database.objectStoreNames.contains('sources')) {
            database.createObjectStore('sources', { keyPath: 'id' });
        }
        if (!database.objectStoreNames.contains('categories')) {
            store = database.createObjectStore('categories', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('bySourceType', ['sourceId', 'contentType'], { unique: false });
        }
        if (!database.objectStoreNames.contains('items')) {
            store = database.createObjectStore('items', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('byCategory', ['sourceId', 'categoryId'], { unique: false });
            store.createIndex('byType', ['sourceId', 'contentType'], { unique: false });
            store.createIndex('bySearchOrder', ['searchRank', 'searchSort', 'id'], { unique: false });
        } else if (transaction) {
            store = transaction.objectStore('items');
            if (!store.indexNames.contains('bySearchOrder')) {
                store.createIndex('bySearchOrder', ['searchRank', 'searchSort', 'id'], { unique: false });
            }
            if (oldVersion < 2) {
                (function migrateSearchMetadata() {
                    var request = store.openCursor();
                    request.onsuccess = function (event) {
                        var cursor = event.target.result;
                        if (!cursor) { return; }
                        cursor.update(searchableItem(cursor.value));
                        cursor.continue();
                    };
                }());
            }
        }
        if (!database.objectStoreNames.contains('favorites')) {
            store = database.createObjectStore('favorites', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
        }
        if (!database.objectStoreNames.contains('progress')) {
            store = database.createObjectStore('progress', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
        }
        /*
          Lembretes (v3).

          Mesma forma de favorites/progress porque respondem à mesma pergunta:
          o que ESTE perfil marcou. A chave é a identidade de conteúdo, nunca o
          id da linha do catálogo — um lembrete costuma existir justamente para
          um título que ainda não está na lista, e uma nova importação troca
          todos os ids.

          Criar um store novo não precisa de migration de dados: quem vem da v2
          simplesmente ganha um store vazio, e nenhum registro existente é lido
          ou reescrito.
        */
        if (!database.objectStoreNames.contains('reminders')) {
            store = database.createObjectStore('reminders', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
        }
    }

    function open(success, failure) {
        var request;
        if (db) { success(db); return; }
        if (!window.indexedDB) { failure(new Error('INDEXED_DB_UNAVAILABLE')); return; }
        request = indexedDB.open(DB_NAME, DB_VERSION);
        request.onupgradeneeded = function (event) {
            configureSchema(event.target.result, event.target.transaction, event.oldVersion || 0);
        };
        request.onsuccess = function (event) { db = event.target.result; success(db); };
        request.onerror = function () { failure(request.error || new Error('DATABASE_OPEN_FAILED')); };
    }

    function requestStore(storeName, mode, operation, success, failure) {
        open(function (database) {
            var transaction;
            var request;
            try {
                transaction = database.transaction([storeName], mode);
                request = operation(transaction.objectStore(storeName));
                request.onsuccess = function () { success(request.result); };
                request.onerror = function () { failure(request.error || new Error('DATABASE_REQUEST_FAILED')); };
            } catch (error) { failure(error); }
        }, failure);
    }

    function put(storeName, value, success, failure) {
        requestStore(storeName, 'readwrite', function (store) {
            return store.put(storeName === 'items' ? searchableItem(value) : value);
        }, success, failure);
    }

    /*
      Grava muitos registros de uma vez.

      O custo do IndexedDB está na transação, não no dado: gravar mil itens com
      uma transação cada leva cerca de vinte e cinco vezes mais tempo do que
      uma transação para o lote inteiro. Numa lista de vinte mil canais isso é
      a diferença entre um segundo e a TV parada por meio minuto — com o
      controle sem resposta, porque o JavaScript da TV é de uma thread só.

      Os lotes são fatiados em blocos: uma transação única para vinte mil
      registros mantém tudo pendente na memória até o commit, e a TV tem bem
      menos memória que um PC — onde estourar significa a aba morrer, não
      ficar lenta.

      Mil por bloco: medindo dez mil itens, blocos de 250 levaram 1149ms, de
      500 953ms, de 1000 887ms e de 2000 840ms. Depois de mil o ganho vira
      ruído e o pico de memória continua subindo, então é onde a curva se
      achata sem cobrar por isso.

      Entre blocos o controle volta ao navegador. Sem isso a gravação inteira
      vira um bloqueio contínuo, e o usuário vê a mesma tela congelada que
      queríamos evitar.
    */
    var BATCH_SIZE = 1000;

    function putBatch(storeName, values, success, failure) {
        var items = values || [];
        var index = 0;

        if (!items.length) { success(0); return; }

        function writeChunk() {
            var chunk = items.slice(index, index + BATCH_SIZE);
            open(function (database) {
                var transaction;
                var store;
                var position;
                try {
                    transaction = database.transaction([storeName], 'readwrite');
                    store = transaction.objectStore(storeName);
                    for (position = 0; position < chunk.length; position += 1) {
                        store.put(storeName === 'items'
                            ? searchableItem(chunk[position])
                            : chunk[position]);
                    }
                    /*
                      Uma só espera por bloco: aguardar cada `put` individual
                      recriaria o custo por item que este método existe para
                      eliminar.
                    */
                    transaction.oncomplete = function () {
                        index += chunk.length;
                        if (index >= items.length) { success(index); return; }
                        window.setTimeout(writeChunk, 0);
                    };
                    transaction.onerror = function () {
                        failure(transaction.error || new Error('DATABASE_BATCH_FAILED'));
                    };
                    transaction.onabort = function () {
                        failure(transaction.error || new Error('DATABASE_BATCH_ABORTED'));
                    };
                } catch (error) { failure(error); }
            }, failure);
        }

        writeChunk();
    }

    function remove(storeName, key, success, failure) {
        requestStore(storeName, 'readwrite', function (store) { return store.delete(key); }, success, failure);
    }

    function get(storeName, key, success, failure) {
        requestStore(storeName, 'readonly', function (store) { return store.get(key); }, success, failure);
    }

    function all(storeName, success, failure) {
        requestStore(storeName, 'readonly', function (store) { return store.getAll(); }, success, failure);
    }

    function take(storeName, count, success, failure) {
        requestStore(storeName, 'readonly', function (store) {
            return store.getAll(undefined, Math.max(1, Number(count) || 1));
        }, success, failure);
    }

    function byIndex(storeName, indexName, key, success, failure) {
        requestStore(storeName, 'readonly', function (store) {
            return store.index(indexName).getAll(key);
        }, success, failure);
    }

    function where(storeName, predicate, limit, success, failure) {
        var settled = false;
        var maximum = Math.max(1, Number(limit) || 1);
        var rows = [];
        open(function (database) {
            var transaction;
            var request;
            try {
                transaction = database.transaction([storeName], 'readonly');
                request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (settled) { return; }
                    if (!cursor || rows.length >= maximum) {
                        settled = true;
                        success(rows);
                        return;
                    }
                    try {
                        if (predicate(cursor.value)) { rows.push(cursor.value); }
                    } catch (error) {
                        settled = true;
                        failure(error);
                        return;
                    }
                    if (rows.length >= maximum) {
                        settled = true;
                        success(rows);
                    } else { cursor.continue(); }
                };
                request.onerror = function () {
                    if (!settled) { settled = true; failure(request.error || new Error('DATABASE_REQUEST_FAILED')); }
                };
                transaction.onerror = function () {
                    if (!settled) { settled = true; failure(transaction.error || new Error('DATABASE_REQUEST_FAILED')); }
                };
            } catch (error) { failure(error); }
        }, failure);
    }

    /* Percorre um store inteiro sem materializar o catálogo em memória. */
    function fold(storeName, reducer, initialValue, success, failure) {
        var settled = false;
        var accumulator = initialValue;
        open(function (database) {
            var transaction;
            var request;
            function fail(error) {
                if (!settled) {
                    settled = true;
                    failure(error || new Error('DATABASE_REQUEST_FAILED'));
                }
            }
            try {
                transaction = database.transaction([storeName], 'readonly');
                request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (settled) { return; }
                    if (!cursor) {
                        settled = true;
                        success(accumulator);
                        return;
                    }
                    try { accumulator = reducer(accumulator, cursor.value); }
                    catch (error) { fail(error); return; }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
            } catch (error) { fail(error); }
        }, failure);
    }

    /* Percorre apenas uma chave de índice e mantém um acumulador O(1). */
    function foldByIndex(storeName, indexName, key, reducer, initialValue, success, failure) {
        var settled = false;
        var accumulator = initialValue;
        open(function (database) {
            var transaction;
            var request;
            function fail(error) {
                if (!settled) { settled = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            try {
                transaction = database.transaction([storeName], 'readonly');
                request = transaction.objectStore(storeName).index(indexName).openCursor(key);
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (settled) { return; }
                    if (!cursor) { settled = true; success(accumulator); return; }
                    try { accumulator = reducer(accumulator, cursor.value); }
                    catch (error) { fail(error); return; }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
            } catch (error) { fail(error); }
        }, failure);
    }

    function wherePage(storeName, predicate, offset, limit, success, failure) {
        var settled = false;
        var skipped = 0;
        var start = Math.max(0, Number(offset) || 0);
        var maximum = Math.max(1, Number(limit) || 1);
        var rows = [];
        open(function (database) {
            var transaction;
            var request;
            function fail(error) {
                if (!settled) { settled = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            try {
                transaction = database.transaction([storeName], 'readonly');
                request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    var matches;
                    if (settled) { return; }
                    if (!cursor) {
                        settled = true;
                        success({ rows: rows, hasMore: false });
                        return;
                    }
                    try { matches = predicate(cursor.value); } catch (error) { fail(error); return; }
                    if (matches) {
                        if (skipped < start) { skipped += 1; }
                        else if (rows.length < maximum) { rows.push(cursor.value); }
                        else {
                            settled = true;
                            success({ rows: rows, hasMore: true });
                            return;
                        }
                    }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
            } catch (error) { fail(error); }
        }, failure);
    }

    /*
      Busca por trecho com a mesma prioridade do Android (filmes, séries e
      depois os demais tipos). O cursor opaco evita reler páginas anteriores;
      searchName já vem normalizado pela migration v2.
    */
    function searchPage(query, predicate, afterKey, limit, success, failure) {
        var settled = false;
        var needle = BuroDomain.foldAccents(BuroDomain.trim(query || ''));
        var maximum = Math.min(200, Math.max(1, Number(limit) || 1));
        var cursorKey = Array.isArray(afterKey) && afterKey.length === 3 ? afterKey : null;
        var jumped = !cursorKey;
        var rows = [];
        var lastIncludedKey = null;
        if (needle.length < 2) { success({ rows: [], hasMore: false, nextCursor: null }); return; }
        open(function (database) {
            var transaction;
            var request;
            function fail(error) {
                if (!settled) { settled = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            try {
                transaction = database.transaction(['items'], 'readonly');
                request = transaction.objectStore('items').index('bySearchOrder').openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    var row;
                    var matches;
                    if (settled) { return; }
                    if (!cursor) {
                        settled = true;
                        success({ rows: rows, hasMore: false, nextCursor: null });
                        return;
                    }
                    if (!jumped) {
                        jumped = true;
                        try {
                            if (indexedDB.cmp(cursor.key, cursorKey) < 0) {
                                cursor.continue(cursorKey);
                                return;
                            }
                        } catch (jumpError) { fail(jumpError); return; }
                    }
                    if (cursorKey && indexedDB.cmp(cursor.key, cursorKey) <= 0) {
                        cursor.continue();
                        return;
                    }
                    row = cursor.value;
                    try {
                        matches = String(row.searchName || BuroDomain.foldAccents(row.name || '')).indexOf(needle) >= 0 &&
                            (!predicate || predicate(row));
                    } catch (error) { fail(error); return; }
                    if (matches) {
                        if (rows.length >= maximum) {
                            settled = true;
                            success({ rows: rows, hasMore: true, nextCursor: lastIncludedKey });
                            return;
                        }
                        rows.push(row);
                        lastIncludedKey = Array.prototype.slice.call(cursor.key);
                    }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
            } catch (error) { fail(error); }
        }, failure);
    }

    function deleteSourceData(sourceId, success, failure) {
        if (!BuroDomain.safeId(sourceId)) { failure(new Error('SOURCE_ID_INVALID')); return; }
        open(function (database) {
            var names = ['sources', 'categories', 'items', 'favorites', 'progress', 'profiles'];
            var transaction;
            var itemIds = {};
            var failed = false;
            function fail(error) {
                if (!failed) { failed = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            function deleteProfileReferences() {
                var request = transaction.objectStore('profiles').openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    var value;
                    if (!cursor) {
                        transaction.objectStore('sources').delete(sourceId);
                        return;
                    }
                    value = cursor.value;
                    if (value.sourceId === sourceId) {
                        value.sourceId = null;
                        cursor.update(value);
                    }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            function deleteReferences(storeName, next) {
                var request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (!cursor) { next(); return; }
                    if (itemIds[cursor.value.itemId]) { cursor.delete(); }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            function deleteCategories() {
                var request = transaction.objectStore('categories').index('bySource').openCursor(sourceId);
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (!cursor) {
                        deleteReferences('favorites', function () {
                            deleteReferences('progress', deleteProfileReferences);
                        });
                        return;
                    }
                    cursor.delete();
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            try {
                transaction = database.transaction(names, 'readwrite');
                transaction.oncomplete = function () { if (!failed) { success(); } };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
                (function deleteItems() {
                    var request = transaction.objectStore('items').index('bySource').openCursor(sourceId);
                    request.onsuccess = function (event) {
                        var cursor = event.target.result;
                        if (!cursor) { deleteCategories(); return; }
                        itemIds[cursor.value.id] = true;
                        cursor.delete();
                        cursor.continue();
                    };
                    request.onerror = function () { fail(request.error); };
                }());
            } catch (error) { fail(error); }
        }, failure);
    }

    /*
      Substitui a fotografia persistida de uma fonte em uma única transação.
      M3U entrega todos os itens e usa replaceAllItems=true. Xtream atualiza as
      categorias, mas preserva o cache dos itens cujas categorias continuam
      existindo. Favoritos e progresso sobrevivem enquanto a identidade do
      item continuar válida.
    */
    function replaceSourceCatalogue(source, categories, items, replaceAllItems, success, failure) {
        var sourceId = source && source.id;
        var categoryRows = Array.isArray(categories) ? categories : [];
        var itemRows = Array.isArray(items) ? items : [];
        var nextCategoryIds = {};
        var nextItemIds = {};
        var nextItemsById = {};
        if (!BuroDomain.safeId(sourceId)) { failure(new Error('SOURCE_ID_INVALID')); return; }
        try {
            categoryRows.forEach(function (row) {
                if (!row || !BuroDomain.safeId(row.id) || row.sourceId !== sourceId) {
                    throw new Error('CATEGORY_SOURCE_INVALID');
                }
                nextCategoryIds[row.id] = true;
            });
            itemRows.forEach(function (row) {
                if (!row || !row.id || String(row.id).length > 200 || row.sourceId !== sourceId ||
                        (row.categoryId && !nextCategoryIds[row.categoryId])) {
                    throw new Error('ITEM_SOURCE_INVALID');
                }
                nextItemIds[row.id] = true;
                nextItemsById[row.id] = row;
            });
        } catch (validationError) { failure(validationError); return; }
        open(function (database) {
            var transaction;
            var removedItemIds = {};
            var failed = false;
            function fail(error) {
                if (!failed) { failed = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            function removeReferences(storeName, next) {
                var request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (!cursor) { next(); return; }
                    if (removedItemIds[cursor.value.itemId]) { cursor.delete(); }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            function writeSnapshot() {
                categoryRows.forEach(function (row) { transaction.objectStore('categories').put(row); });
                itemRows.forEach(function (row) { transaction.objectStore('items').put(searchableItem(row)); });
                removeReferences('favorites', function () {
                    removeReferences('progress', function () {});
                });
            }
            function reconcileItems() {
                var request = transaction.objectStore('items').index('bySource').openCursor(sourceId);
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    var row;
                    var removed;
                    if (!cursor) { writeSnapshot(); return; }
                    row = cursor.value;
                    if (nextItemsById[row.id] && Number(row.addedAt) > 0) {
                        nextItemsById[row.id].addedAt = Number(row.addedAt);
                    }
                    removed = replaceAllItems ? !nextItemIds[row.id] : !nextCategoryIds[row.categoryId];
                    if (removed) {
                        removedItemIds[row.id] = true;
                        cursor.delete();
                    }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            try {
                transaction = database.transaction(['sources', 'categories', 'items', 'favorites', 'progress'], 'readwrite');
                transaction.oncomplete = function () {
                    if (!failed) { success({ removedItemIds: Object.keys(removedItemIds) }); }
                };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
                transaction.objectStore('sources').put(source);
                (function reconcileCategories() {
                    var request = transaction.objectStore('categories').index('bySource').openCursor(sourceId);
                    request.onsuccess = function (event) {
                        var cursor = event.target.result;
                        if (!cursor) { reconcileItems(); return; }
                        if (!nextCategoryIds[cursor.value.id]) { cursor.delete(); }
                        cursor.continue();
                    };
                    request.onerror = function () { fail(request.error); };
                }());
            } catch (error) { fail(error); }
        }, failure);
    }

    /* Atualiza os episódios de uma série sem tocar no restante da fonte. */
    function replaceCategoryItems(sourceId, categoryId, items, success, failure) {
        var itemRows = Array.isArray(items) ? items : [];
        var nextItemIds = {};
        var nextItemsById = {};
        if (!BuroDomain.safeId(sourceId) || !categoryId || String(categoryId).length > 200) {
            failure(new Error('CATEGORY_ID_INVALID')); return;
        }
        try {
            itemRows.forEach(function (row) {
                if (!row || !row.id || String(row.id).length > 200 || row.sourceId !== sourceId ||
                        row.categoryId !== categoryId) { throw new Error('ITEM_CATEGORY_INVALID'); }
                nextItemIds[row.id] = true;
                nextItemsById[row.id] = row;
            });
        } catch (validationError) { failure(validationError); return; }
        open(function (database) {
            var transaction;
            var removedItemIds = {};
            var failed = false;
            function fail(error) {
                if (!failed) { failed = true; failure(error || new Error('DATABASE_REQUEST_FAILED')); }
            }
            function removeReferences(storeName, next) {
                var request = transaction.objectStore(storeName).openCursor();
                request.onsuccess = function (event) {
                    var cursor = event.target.result;
                    if (!cursor) { next(); return; }
                    if (removedItemIds[cursor.value.itemId]) { cursor.delete(); }
                    cursor.continue();
                };
                request.onerror = function () { fail(request.error); };
            }
            function writeItems() {
                itemRows.forEach(function (row) { transaction.objectStore('items').put(searchableItem(row)); });
                removeReferences('favorites', function () {
                    removeReferences('progress', function () {});
                });
            }
            try {
                transaction = database.transaction(['items', 'favorites', 'progress'], 'readwrite');
                transaction.oncomplete = function () {
                    if (!failed) { success({ removedItemIds: Object.keys(removedItemIds) }); }
                };
                transaction.onerror = function () { fail(transaction.error); };
                transaction.onabort = function () { fail(transaction.error); };
                (function reconcileItems() {
                    var request = transaction.objectStore('items').index('byCategory').openCursor([sourceId, categoryId]);
                    request.onsuccess = function (event) {
                        var cursor = event.target.result;
                        if (!cursor) { writeItems(); return; }
                        if (nextItemsById[cursor.value.id] && Number(cursor.value.addedAt) > 0) {
                            nextItemsById[cursor.value.id].addedAt = Number(cursor.value.addedAt);
                        }
                        if (!nextItemIds[cursor.value.id]) {
                            removedItemIds[cursor.value.id] = true;
                            cursor.delete();
                        }
                        cursor.continue();
                    };
                    request.onerror = function () { fail(request.error); };
                }());
            } catch (error) { fail(error); }
        }, failure);
    }

    function secureAvailable() {
        return Boolean(window.tizen && tizen.keymanager && tizen.keymanager.saveData);
    }

    function alias(sourceId) {
        if (!BuroDomain.safeId(sourceId)) { throw new Error('SOURCE_ID_INVALID'); }
        return 'iptvburo.source.' + sourceId;
    }

    function secureSave(sourceId, value, success, failure) {
        var name;
        var aliases;
        if (!secureAvailable()) { failure(new Error('SECURE_STORE_UNAVAILABLE')); return; }
        name = alias(sourceId);
        try {
            aliases = tizen.keymanager.getDataAliasList();
            aliases.forEach(function (item) {
                if (item.name === name) { tizen.keymanager.removeData({ name: name }); }
            });
            tizen.keymanager.saveData(name, JSON.stringify(value), null, success, failure);
        } catch (error) { failure(error); }
    }

    function secureGet(sourceId) {
        var raw;
        if (!secureAvailable()) { throw new Error('SECURE_STORE_UNAVAILABLE'); }
        raw = tizen.keymanager.getData({ name: alias(sourceId) });
        return JSON.parse(raw);
    }

    function secureRemove(sourceId) {
        if (!secureAvailable()) { throw new Error('SECURE_STORE_UNAVAILABLE'); }
        try { tizen.keymanager.removeData({ name: alias(sourceId) }); } catch (error) {
            if (!error || error.name !== 'NotFoundError') { throw error; }
        }
    }

    return {
        loadPreferences: loadPreferences,
        savePreferences: savePreferences,
        open: open,
        put: put,
        putBatch: putBatch,
        remove: remove,
        get: get,
        all: all,
        take: take,
        byIndex: byIndex,
        where: where,
        fold: fold,
        foldByIndex: foldByIndex,
        wherePage: wherePage,
        searchPage: searchPage,
        deleteSourceData: deleteSourceData,
        replaceSourceCatalogue: replaceSourceCatalogue,
        replaceCategoryItems: replaceCategoryItems,
        secureAvailable: secureAvailable,
        secureSave: secureSave,
        secureGet: secureGet,
        secureRemove: secureRemove
    };
}());
