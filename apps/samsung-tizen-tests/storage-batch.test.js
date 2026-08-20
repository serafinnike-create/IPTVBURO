/*
  Gravação de catálogo grande sem travar a TV.

  O custo do IndexedDB está na transação, não no dado. Uma lista IPTV comum
  tem dezenas de milhares de canais, e gravá-los um a um deixa a TV parada com
  o controle sem resposta — o JavaScript da TV é de uma thread só, então
  enquanto isso nada mais acontece.

  O que estes testes protegem: que a escrita continue em lote, que ela devolva
  o controle entre blocos, e que uma falha no meio não seja declarada sucesso.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition, detail) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else {
        failures.push(label + (detail ? ' — ' + detail : ''));
        process.stdout.write('  FALHA ' + label + (detail ? ' — ' + detail : '') + '\n');
    }
}

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    window.indexedDB = new fakeIndexedDb.IDBFactory();

    /* Conta transações abertas: é a métrica que separa lote de um-a-um. */
    var transactions = 0;
    var putsInCurrentTask = 0;
    var maximumPutBurst = 0;
    var putResetScheduled = false;
    var openDatabases = [];
    var originalOpen = window.indexedDB.open.bind(window.indexedDB);
    window.indexedDB.open = function () {
        var request = originalOpen.apply(null, arguments);
        request.addEventListener('success', function (event) {
            var database = event.target.result;
            if (openDatabases.indexOf(database) >= 0) { return; }
            openDatabases.push(database);
            var originalTransaction = database.transaction.bind(database);
            database.transaction = function () {
                transactions += 1;
                var transaction = originalTransaction.apply(null, arguments);
                var originalObjectStore = transaction.objectStore.bind(transaction);
                transaction.objectStore = function (name) {
                    var store = originalObjectStore(name);
                    var originalPut;
                    if (store.__buroBurstObserved) { return store; }
                    originalPut = store.put.bind(store);
                    store.put = function () {
                        putsInCurrentTask += 1;
                        maximumPutBurst = Math.max(maximumPutBurst, putsInCurrentTask);
                        if (!putResetScheduled) {
                            putResetScheduled = true;
                            window.setTimeout(function () {
                                putsInCurrentTask = 0;
                                putResetScheduled = false;
                            }, 0);
                        }
                        return originalPut.apply(null, arguments);
                    };
                    store.__buroBurstObserved = true;
                    return store;
                };
                return transaction;
            };
        });
        return request;
    };

    ['js/domain.js', 'js/storage.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });

    window.__transactions = function () { return transactions; };
    window.__resetTransactions = function () { transactions = 0; };
    window.__maximumPutBurst = function () { return maximumPutBurst; };
    window.__resetPutBurst = function () {
        putsInCurrentTask = 0;
        maximumPutBurst = 0;
        putResetScheduled = false;
    };
    return window;
}

function items(count, prefix) {
    var list = [];
    var index;
    for (index = 0; index < count; index += 1) {
        list.push({
            id: prefix + ':' + index,
            sourceId: 'source-1',
            contentType: 'LIVE',
            name: 'Canal ' + index,
            categoryId: 'cat' + (index % 40),
            locator: { kind: 'm3u', contentType: 'LIVE', providerItemId: String(index) }
        });
    }
    return list;
}

function promised(fn) {
    return new Promise(function (resolve, reject) { fn(resolve, reject); });
}

async function run() {
    var window = makeWindow();
    await promised(function (ok, no) { window.BuroStorage.open(ok, no); });

    process.stdout.write('Escrita em lote\n');

    window.__resetTransactions();
    var written = await promised(function (ok, no) {
        window.BuroStorage.putBatch('items', items(5000, 'batch'), ok, no);
    });
    var batchTransactions = window.__transactions();

    check('grava o lote inteiro', written === 5000, 'gravou ' + written);
    /*
      5000 itens em blocos de 1000 são cinco transações. Um-a-um seriam cinco
      mil — o número que travava a TV.
    */
    check('usa uma transação por bloco, não por item',
        batchTransactions <= 10,
        batchTransactions + ' transações para 5000 itens');

    var stored = await promised(function (ok, no) {
        window.BuroStorage.all('items', ok, no);
    });
    check('todos os registros ficam legíveis depois',
        stored.filter(function (row) { return row.id.indexOf('batch:') === 0; }).length === 5000);

    check('o índice de busca é preenchido no caminho em lote',
        stored.every(function (row) {
            return row.id.indexOf('batch:') !== 0 || typeof row.searchName === 'string';
        }));

    process.stdout.write('Casos de borda\n');
    var empty = await promised(function (ok, no) {
        window.BuroStorage.putBatch('items', [], ok, no);
    });
    check('lote vazio termina sem abrir transação', empty === 0);

    /*
      A TV pode negar a escrita no meio — disco cheio é o caso comum. Declarar
      sucesso aí deixaria o app achar que tem um catálogo que não tem.
    */
    var rejected = await promised(function (resolve) {
        window.BuroStorage.putBatch('items', [{ id: null }], function () {
            resolve('sucesso');
        }, function () { resolve('falhou'); });
    });
    check('registro inválido falha em vez de ser dado como gravado',
        rejected === 'falhou');

    process.stdout.write('Responsividade durante a gravação\n');
    /*
      Entre blocos o controle tem de voltar ao navegador, senão o D-pad fica
      morto durante toda a importação. Um temporizador agendado antes da
      gravação precisa disparar antes dela terminar.
    */
    var yielded = await promised(function (resolve) {
        var ranDuringWrite = false;
        window.setTimeout(function () { ranDuringWrite = true; }, 0);
        window.BuroStorage.putBatch('items', items(3000, 'yield'), function () {
            resolve(ranDuringWrite);
        }, function () { resolve(false); });
    });
    check('a gravação devolve o controle entre blocos', yielded);

    process.stdout.write('Substituição transacional usada pela importação\n');
    var snapshotSource = {
        id: 'source-snapshot', name: 'Fonte sintética grande', type: 'REMOTE_M3U',
        createdAt: 1000, updatedAt: 2000
    };
    var snapshotCategories = [];
    var snapshotItems = [];
    for (var categoryIndex = 0; categoryIndex < 40; categoryIndex += 1) {
        snapshotCategories.push({
            id: 'snapshot-category-' + categoryIndex,
            sourceId: snapshotSource.id,
            name: 'Categoria ' + categoryIndex,
            contentType: 'LIVE',
            sortOrder: categoryIndex
        });
    }
    for (var snapshotIndex = 0; snapshotIndex < 5000; snapshotIndex += 1) {
        snapshotItems.push({
            id: 'snapshot-item-' + snapshotIndex,
            sourceId: snapshotSource.id,
            categoryId: snapshotCategories[snapshotIndex % snapshotCategories.length].id,
            contentType: 'LIVE',
            name: 'Canal de escala ' + snapshotIndex,
            sortOrder: snapshotIndex,
            addedAt: snapshotIndex + 1
        });
    }
    window.__resetPutBurst();
    window.__resetTransactions();
    var snapshotResult = await promised(function (ok, no) {
        window.BuroStorage.replaceSourceCatalogue(
            snapshotSource, snapshotCategories, snapshotItems, true, ok, no
        );
    });
    check('a fotografia grande continua sendo uma substituição atômica completa',
        snapshotResult && snapshotResult.removedItemIds.length === 0 &&
        window.__transactions() === 1,
        window.__transactions() + ' transações para a fotografia');
    check('o caminho real de importação limita cada rajada de escrita a mil itens',
        window.__maximumPutBurst() <= 1050,
        window.__maximumPutBurst() + ' puts consecutivos para 5000 itens');


    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
