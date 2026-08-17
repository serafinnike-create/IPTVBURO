/* IndexedDB v1 -> v2 migration and ordered search cursor contracts. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function createV1(factory, rows) {
    return new Promise(function (resolve, reject) {
        var request = factory.open('iptvburo.catalog', 1);
        request.onupgradeneeded = function (event) {
            var database = event.target.result;
            var store;
            database.createObjectStore('profiles', { keyPath: 'id' });
            database.createObjectStore('sources', { keyPath: 'id' });
            store = database.createObjectStore('categories', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('bySourceType', ['sourceId', 'contentType'], { unique: false });
            store = database.createObjectStore('items', { keyPath: 'id' });
            store.createIndex('bySource', 'sourceId', { unique: false });
            store.createIndex('byCategory', ['sourceId', 'categoryId'], { unique: false });
            store.createIndex('byType', ['sourceId', 'contentType'], { unique: false });
            store = database.createObjectStore('favorites', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
            store = database.createObjectStore('progress', { keyPath: 'id' });
            store.createIndex('byProfile', 'profileId', { unique: false });
        };
        request.onerror = function () { reject(request.error || new Error('V1_OPEN_FAILED')); };
        request.onsuccess = function () {
            var database = request.result;
            var transaction = database.transaction(['items'], 'readwrite');
            rows.forEach(function (row) { transaction.objectStore('items').put(row); });
            transaction.oncomplete = function () { database.close(); resolve(); };
            transaction.onerror = function () { reject(transaction.error || new Error('V1_WRITE_FAILED')); };
        };
    });
}

function openStorage(storage) {
    return new Promise(function (resolve, reject) { storage.open(resolve, reject); });
}

function get(storage, key) {
    return new Promise(function (resolve, reject) { storage.get('items', key, resolve, reject); });
}

function put(storage, row) {
    return new Promise(function (resolve, reject) { storage.put('items', row, resolve, reject); });
}

function search(storage, query, predicate, cursor, limit) {
    return new Promise(function (resolve, reject) {
        storage.searchPage(query, predicate, cursor, limit, resolve, reject);
    });
}

function replaceSource(storage, source, categories, items) {
    return new Promise(function (resolve, reject) {
        storage.replaceSourceCatalogue(source, categories, items, true, resolve, reject);
    });
}

function replaceCategory(storage, sourceId, categoryId, items) {
    return new Promise(function (resolve, reject) {
        storage.replaceCategoryItems(sourceId, categoryId, items, resolve, reject);
    });
}

async function run() {
    var factory = new fakeIndexedDb.IDBFactory();
    var legacyRows = [
        { id: 'live:z', sourceId: 'source-a', categoryId: 'live', contentType: 'LIVE', name: 'Café Central', sortOrder: 0 },
        { id: 'series:a', sourceId: 'source-a', categoryId: 'series', contentType: 'SERIES', name: 'Café Série', sortOrder: 5 },
        { id: 'movie:b', sourceId: 'source-a', categoryId: 'movies', contentType: 'MOVIE', name: 'Noite Café', sortOrder: 20,
            locator: { kind: 'xtream', providerItemId: '20' } },
        { id: 'movie:a', sourceId: 'source-a', categoryId: 'movies', contentType: 'MOVIE', name: 'Café A', sortOrder: 10 },
        { id: 'movie:other', sourceId: 'source-a', categoryId: 'movies', contentType: 'MOVIE', name: 'Sem correspondência', sortOrder: 1 }
    ];
    var dom;
    var window;
    var database;
    var migrated;
    var first;
    var second;
    var secondPredicateCalls = 0;
    var oneLetterCalls = 0;

    await createV1(factory, legacyRows);
    dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    window = dom.window;
    window.indexedDB = factory;
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'storage.js'), 'utf8'));

    process.stdout.write('Migration IndexedDB v1 → v3\n');
    database = await openStorage(window.BuroStorage);
    /* A versão sobe conforme o esquema cresce; o que este teste protege é que
       subir de v1 direto para a atual não recria os stores nem perde o que já
       estava gravado. As asserções seguintes conferem o conteúdo. */
    check('banco legado abre na versão atual sem recriar os stores',
        database.version === 3 && database.objectStoreNames.contains('profiles') && database.objectStoreNames.contains('items'));
    /* Um banco criado antes de os lembretes existirem precisa ganhar o store
       vazio, senão marcar um título falharia só para quem já usava o app. */
    check('um banco anterior aos lembretes ganha o store novo, vazio',
        database.objectStoreNames.contains('reminders'));
    check('migration cria o índice composto de ordem da busca',
        database.transaction(['items'], 'readonly').objectStore('items').indexNames.contains('bySearchOrder'));
    migrated = await get(window.BuroStorage, 'movie:b');
    check('migration preenche nome normalizado, prioridade e ordem',
        migrated.searchName === 'noite cafe' && migrated.searchRank === 0 && migrated.searchSort === 20);
    check('migration preserva identidade e locator existentes',
        migrated.id === 'movie:b' && migrated.name === 'Noite Café' && migrated.locator.providerItemId === '20');
    check('metadados derivados não introduzem URL, senha ou token',
        !/https?:|password|token/i.test(JSON.stringify(migrated)));

    process.stdout.write('Busca ordenada e cursor retomável\n');
    first = await search(window.BuroStorage, 'CAFE', function () { return true; }, null, 2);
    check('busca mantém substring sem acento e prioriza filmes pela ordem do catálogo',
        first.rows.map(function (row) { return row.id; }).join(',') === 'movie:a,movie:b' && first.hasMore &&
        Array.isArray(first.nextCursor));
    second = await search(window.BuroStorage, 'café', function () { secondPredicateCalls += 1; return true; }, first.nextCursor, 2);
    check('página seguinte continua do cursor e preserva Séries antes de Ao vivo',
        second.rows.map(function (row) { return row.id; }).join(',') === 'series:a,live:z' && !second.hasMore);
    check('cursor da página seguinte não reavalia os resultados anteriores', secondPredicateCalls === 2);
    check('busca por trecho no meio do título continua compatível com Android',
        (await search(window.BuroStorage, 'ite ca', function () { return true; }, null, 10)).rows[0].id === 'movie:b');
    check('uma letra é recusada antes de percorrer o catálogo',
        (await search(window.BuroStorage, 'a', function () { oneLetterCalls += 1; return true; }, null, 40)).rows.length === 0 &&
        oneLetterCalls === 0);

    await put(window.BuroStorage, {
        id: 'movie:new', sourceId: 'source-a', categoryId: 'movies', contentType: 'MOVIE', name: 'Órbita Nova', sortOrder: 2
    });
    migrated = await get(window.BuroStorage, 'movie:new');
    check('novas gravações recebem os mesmos campos derivados automaticamente',
        migrated.searchName === 'orbita nova' && migrated.searchRank === 0 && migrated.searchSort === 2);

    await replaceSource(window.BuroStorage,
        { id: 'source-b', name: 'Fonte B', type: 'REMOTE_M3U' },
        [{ id: 'category-b', sourceId: 'source-b', name: 'Filmes', contentType: 'MOVIE' }],
        [{ id: 'movie:source-snapshot', sourceId: 'source-b', categoryId: 'category-b', contentType: 'MOVIE',
            name: 'Fotografia Índice', sortOrder: 7 }]);
    migrated = await get(window.BuroStorage, 'movie:source-snapshot');
    check('fotografia completa de fonte também atualiza o índice de busca',
        migrated.searchName === 'fotografia indice' && migrated.searchSort === 7);

    await replaceCategory(window.BuroStorage, 'source-b', 'category-b', [
        { id: 'movie:category-snapshot', sourceId: 'source-b', categoryId: 'category-b', contentType: 'MOVIE',
            name: 'Categoria Órbita', sortOrder: 3 }
    ]);
    migrated = await get(window.BuroStorage, 'movie:category-snapshot');
    check('reconciliação individual de categoria mantém o mesmo índice derivado',
        migrated.searchName === 'categoria orbita' && migrated.searchRank === 0 && migrated.searchSort === 3);

    database.close();
    dom.window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.stack + '\n');
    process.exitCode = 1;
});
