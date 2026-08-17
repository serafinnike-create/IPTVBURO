/* Contract tests for the bounded, resumable Xtream catalogue queue. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function waitFor(predicate, timeoutMs) {
    var started = Date.now();
    return new Promise(function (resolve, reject) {
        function poll() {
            if (predicate()) { resolve(); return; }
            if (Date.now() - started >= timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function loadEngine() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'));
    window.BuroStorage = {};
    window.BuroXtream = {};
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'catalogue-sync.js'), 'utf8'));
    return window;
}

function categories(sourceId) {
    return [
        { id: 'live', sourceId: sourceId, contentType: 'LIVE', providerCategoryId: '3', sortOrder: 0 },
        { id: 'series', sourceId: sourceId, contentType: 'SERIES', providerCategoryId: '2', sortOrder: 0 },
        { id: 'movies', sourceId: sourceId, contentType: 'MOVIE', providerCategoryId: '1', sortOrder: 0 }
    ];
}

async function run() {
    var window = loadEngine();
    var source = { id: 'source-sync', type: 'XTREAM' };
    var pending = [];
    var persisted = [];
    var statuses = [];
    var concurrent = 0;
    var maximumConcurrent = 0;
    var secret = { server: 'https://provider.test', username: 'fixture', password: 'never-persist-me' };

    process.stdout.write('Cancelamento do cliente HTTP\n');
    var networkDom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var networkWindow = networkDom.window;
    var xhrAbortCount = 0;
    var xhrFailureCode = null;
    function FakeXhr() { this.readyState = 0; this.status = 0; }
    FakeXhr.prototype.open = function () {};
    FakeXhr.prototype.setRequestHeader = function () {};
    FakeXhr.prototype.send = function () {};
    FakeXhr.prototype.abort = function () {
        xhrAbortCount += 1;
        if (this.onabort) { this.onabort(); }
    };
    networkWindow.XMLHttpRequest = FakeXhr;
    networkWindow.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'network.js'), 'utf8'));
    var httpController = networkWindow.BuroNetwork.json({ url: 'https://public.test/catalogue' }, function () {}, function (error) {
        xhrFailureCode = error.code;
    });
    check('requisição JSON devolve um controller cancelável', httpController && typeof httpController.abort === 'function');
    httpController.abort();
    check('controller aborta o XHR e devolve somente um código sanitizado',
        xhrAbortCount === 1 && xhrFailureCode === 'NETWORK_ABORTED');
    networkWindow.close();

    process.stdout.write('Fila Xtream serial e retomável\n');
    window.BuroXtream.loadItems = function (credentials, sourceId, contentType, category, success, failure) {
        concurrent += 1;
        maximumConcurrent = Math.max(maximumConcurrent, concurrent);
        pending.push({ credentials: credentials, category: category, success: success, failure: failure, aborted: false });
        return { abort: function () { pending[pending.length - 1].aborted = true; concurrent -= 1; failure({ code: 'NETWORK_ABORTED' }); } };
    };
    window.BuroStorage.replaceCategoryItems = function (sourceId, categoryId, items, success) {
        persisted.push({ sourceId: sourceId, categoryId: categoryId, items: items });
        success({ removedItemIds: [] });
    };

    window.BuroCatalogueSync.watch(function (status) { statuses.push(status); });
    window.BuroCatalogueSync.start(source, categories(source.id), {
        getSecret: function () { return secret; }
    }, false);
    await waitFor(function () { return pending.length === 1; }, 500);
    check('filmes são priorizados antes de séries e TV ao vivo', pending[0].category.id === 'movies');
    check('somente uma requisição fica ativa', maximumConcurrent === 1);
    concurrent -= 1;
    pending[0].success([], {});
    await waitFor(function () { return pending.length === 2; }, 500);
    check('categoria vazia também avança o checkpoint', persisted[0].categoryId === 'movies' && persisted[0].items.length === 0);
    check('séries são a segunda prioridade', pending[1].category.id === 'series');
    concurrent -= 1;
    pending[1].success([{ id: 'series:1', sourceId: source.id, categoryId: 'series' }], {});
    await waitFor(function () { return pending.length === 3; }, 500);
    check('TV ao vivo é hidratada depois do catálogo editorial', pending[2].category.id === 'live');
    concurrent -= 1;
    pending[2].success([{ id: 'live:1', sourceId: source.id, categoryId: 'live' }], {});
    await waitFor(function () {
        var status = window.BuroCatalogueSync.progress(source, categories(source.id));
        return status.state === 'COMPLETE';
    }, 500);
    var complete = window.BuroCatalogueSync.progress(source, categories(source.id));
    check('conclusão informa todas as categorias e itens persistidos',
        complete.completed === 3 && complete.total === 3 && complete.itemCount === 2);
    check('checkpoint nunca contém credenciais',
        window.localStorage.getItem(window.BuroCatalogueSync._checkpointKey).indexOf('never-persist-me') < 0);

    var requestCount = pending.length;
    window.BuroCatalogueSync.start(source, categories(source.id), { getSecret: function () { return secret; } }, false);
    await waitFor(function () {
        return window.BuroCatalogueSync.progress(source, categories(source.id)).state === 'COMPLETE';
    }, 500);
    check('reinício dentro da validade retoma sem baixar categorias concluídas', pending.length === requestCount);

    window.BuroCatalogueSync.start(source, categories(source.id), { getSecret: function () { return secret; } }, true);
    await waitFor(function () { return pending.length === requestCount + 1; }, 500);
    check('atualização forçada reinicia a fotografia pela prioridade editorial',
        pending[pending.length - 1].category.id === 'movies');
    concurrent -= 1;
    pending[pending.length - 1].success([], {});
    await waitFor(function () { return pending.length === requestCount + 2; }, 500);
    window.BuroCatalogueSync.cancel();
    check('pausar uma atualização forçada conserva a categoria já confirmada',
        window.BuroCatalogueSync.progress(source, categories(source.id)).completed === 1);
    window.BuroCatalogueSync.start(source, categories(source.id), { getSecret: function () { return secret; } }, false);
    await waitFor(function () { return pending.length === requestCount + 3; }, 500);
    check('retomada após atualização parcial não repete a categoria confirmada',
        pending[pending.length - 1].category.id === 'series');
    window.BuroCatalogueSync.cancel();

    process.stdout.write('Cancelamento e descarte de resposta atrasada\n');
    var cancelWindow = loadEngine();
    var cancelPending;
    var abortCount = 0;
    var cancelPersistCount = 0;
    cancelWindow.BuroXtream.loadItems = function (credentials, sourceId, contentType, category, success, failure) {
        cancelPending = { success: success, failure: failure };
        return { abort: function () { abortCount += 1; failure({ code: 'NETWORK_ABORTED' }); } };
    };
    cancelWindow.BuroStorage.replaceCategoryItems = function () { cancelPersistCount += 1; };
    cancelWindow.BuroCatalogueSync.start(source, categories(source.id), { getSecret: function () { return secret; } }, false);
    await waitFor(function () { return Boolean(cancelPending); }, 500);
    check('cancelar aborta a requisição de rede ativa', cancelWindow.BuroCatalogueSync.cancel() && abortCount === 1);
    check('estado final distingue cancelamento de erro',
        cancelWindow.BuroCatalogueSync.progress(source, categories(source.id)).state === 'CANCELLED');
    cancelPending.success([{ id: 'late' }], {});
    await new Promise(function (resolve) { setTimeout(resolve, 20); });
    check('resposta posterior ao cancelamento não altera o banco', cancelPersistCount === 0);

    process.stdout.write('Retry e atomicidade do checkpoint\n');
    var retryWindow = loadEngine();
    var attempts = 0;
    var persistedAfterRetry = 0;
    retryWindow.BuroXtream.loadItems = function (credentials, sourceId, contentType, category, success, failure) {
        attempts += 1;
        if (attempts === 1) { failure({ code: 'NETWORK_TIMEOUT' }); }
        else { success([], {}); }
        return { abort: function () {} };
    };
    retryWindow.BuroStorage.replaceCategoryItems = function (sourceId, categoryId, items, success) {
        persistedAfterRetry += 1; success({ removedItemIds: [] });
    };
    retryWindow.BuroCatalogueSync.start(source, [categories(source.id)[2]], {
        getSecret: function () { return secret; }
    }, false);
    await waitFor(function () {
        return retryWindow.BuroCatalogueSync.progress(source, [categories(source.id)[2]]).state === 'COMPLETE';
    }, 1200);
    check('falha transitória recebe uma tentativa limitada antes de concluir', attempts === 2 && persistedAfterRetry === 1);

    var storageWindow = loadEngine();
    var storageRequests = 0;
    storageWindow.BuroXtream.loadItems = function (credentials, sourceId, contentType, category, success) {
        storageRequests += 1; success([], {}); return { abort: function () {} };
    };
    storageWindow.BuroStorage.replaceCategoryItems = function (sourceId, categoryId, items, success, failure) {
        failure({ code: 'DATABASE_REQUEST_FAILED' });
    };
    storageWindow.BuroCatalogueSync.start(source, [categories(source.id)[2]], {
        getSecret: function () { return secret; }
    }, false);
    await waitFor(function () {
        return storageWindow.BuroCatalogueSync.progress(source, [categories(source.id)[2]]).state === 'ERROR';
    }, 500);
    check('falha de persistência não é declarada como catálogo completo',
        storageWindow.BuroCatalogueSync.progress(source, [categories(source.id)[2]]).completed === 0);
    check('categoria sem transação confirmada permanece elegível para retomada',
        storageWindow.localStorage.getItem(storageWindow.BuroCatalogueSync._checkpointKey).indexOf('completedAt') < 0 && storageRequests === 1);

    window.close(); cancelWindow.close(); retryWindow.close(); storageWindow.close();
    process.stdout.write('\nResultado: ' + passed + ' passaram, ' + failures.length + ' falharam.\n');
    if (failures.length) { process.exitCode = 1; }
}

run().catch(function (error) {
    process.stderr.write(error.stack + '\n');
    process.exitCode = 1;
});
