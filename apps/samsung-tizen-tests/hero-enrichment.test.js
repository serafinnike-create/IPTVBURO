/* Contract tests for bounded, transient Hero enrichment. */
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
    window.BuroXtream = {};
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'hero-enrichment.js'), 'utf8'));
    return window;
}

function item(sourceId, index, type) {
    return {
        id: (type || 'MOVIE').toLowerCase() + ':' + index,
        sourceId: sourceId,
        contentType: type || 'MOVIE',
        locator: { kind: 'xtream', contentType: type || 'MOVIE', providerItemId: String(index) }
    };
}

async function run() {
    var window = loadEngine();
    var source = { id: 'source-hero', type: 'XTREAM' };
    var candidates = [];
    var pending = [];
    var delivered = [];
    var statuses = [];
    var concurrent = 0;
    var maximumConcurrent = 0;
    var completeCount = 0;
    var index;
    for (index = 0; index < 12; index += 1) {
        candidates.push(item(source.id, index, index % 2 ? 'SERIES' : 'MOVIE'));
    }

    process.stdout.write('Sinopse equivalente ao Android\n');
    var sentence = new Array(15).join('palavra ') + 'Fim da frase. ' + new Array(190).join('x');
    var sentenceCut = window.BuroHeroEnrichment.bannerSynopsis(sentence);
    var ellipsisCut = window.BuroHeroEnrichment.bannerSynopsis(new Array(45).join('palavra '));
    check('sinopse longa termina numa frase útil quando ela cabe',
        sentenceCut.length <= 180 && sentenceCut.substring(sentenceCut.length - 13) === 'Fim da frase.');
    check('sinopse sem frase é limitada a 180 caracteres e sinaliza continuação',
        ellipsisCut.length <= 181 && ellipsisCut.charAt(ellipsisCut.length - 1) === '…');
    check('espaços e controles do provedor são normalizados',
        window.BuroHeroEnrichment.bannerSynopsis('  Um\n\ttexto   limpo.  ') === 'Um texto limpo.');

    process.stdout.write('Fila transitória limitada\n');
    window.BuroXtream.loadHeroDetails = function (secret, candidate, success, failure) {
        var request = { secret: secret, item: candidate, success: success, failure: failure, aborted: false };
        concurrent += 1;
        maximumConcurrent = Math.max(maximumConcurrent, concurrent);
        pending.push(request);
        return { abort: function () {
            if (request.aborted) { return; }
            request.aborted = true; concurrent -= 1; failure({ code: 'NETWORK_ABORTED' });
        } };
    };
    var startStatus = window.BuroHeroEnrichment.start(source, candidates, {
        getSecret: function () { return { username: 'private-user', password: 'private-password' }; },
        onItem: function (candidate, value) { delivered.push({ item: candidate, value: value }); },
        onStatus: function (status) { statuses.push(status); },
        onComplete: function () { completeCount += 1; }
    });
    await waitFor(function () { return pending.length === 1; }, 4000);
    check('somente os dez títulos da rotação entram na fila', startStatus.total === 10);
    check('fila começa pelo primeiro Hero e mantém uma requisição ativa',
        pending[0].item.id === candidates[0].id && maximumConcurrent === 1);
    concurrent -= 1;
    pending[0].success({
        synopsis: sentence, genre: 'Drama', duration: '1h 42min', rating: 8.7,
        artworkUrl: 'https://images.public.test/poster.jpg',
        backdropUrl: 'https://images.public.test/backdrop.jpg?token=memory-only'
    });
    await waitFor(function () { return pending.length === 2; }, 4000);
    check('Filme recebe sinopse, fatos e backdrop no cache de sessão', (function () {
        var value = window.BuroHeroEnrichment.get(source.id, candidates[0].id);
        return value && value.synopsis === sentenceCut && value.genre === 'Drama' &&
            value.rating === 8.7 && value.backdropUrl.indexOf('memory-only') >= 0;
    }()));
    concurrent -= 1;
    pending[1].failure({ code: 'NETWORK_ERROR' });
    for (index = 2; index < 10; index += 1) {
        await waitFor(function () { return pending.length > index; }, 4000);
        concurrent -= 1;
        pending[index].success({ synopsis: 'Sinopse ' + index, artworkUrl: 'https://images.public.test/' + index + '.jpg' });
    }
    await waitFor(function () { return completeCount === 1; }, 4000);
    check('falha de uma Série não impede os outros destaques',
        delivered.length === 9 && statuses[statuses.length - 1].failed === 1);
    check('filmes e séries compartilham a mesma fila sem misturar identidades',
        delivered.some(function (entry) { return entry.item.contentType === 'MOVIE'; }) &&
        delivered.some(function (entry) { return entry.item.contentType === 'SERIES'; }));
    check('nenhum detalhe ou segredo é gravado no armazenamento comum',
        window.localStorage.length === 0 && JSON.stringify(delivered).indexOf('private-password') < 0);

    var requestCount = pending.length;
    startStatus = window.BuroHeroEnrichment.start(source, candidates, {
        getSecret: function () { return {}; }
    });
    await new Promise(function (resolve) { setTimeout(resolve, 20); });
    check('reabrir a Home não repete sucessos, vazios ou falhas recentes',
        startStatus.state === 'COMPLETE' && pending.length === requestCount);

    process.stdout.write('Cancelamento e resposta obsoleta\n');
    var cancelWindow = loadEngine();
    var cancelPending;
    var cancelItems = 0;
    var cancelState;
    var abortCount = 0;
    cancelWindow.BuroXtream.loadHeroDetails = function (secret, candidate, success, failure) {
        cancelPending = { success: success, failure: failure };
        return { abort: function () { abortCount += 1; failure({ code: 'NETWORK_ABORTED' }); } };
    };
    cancelWindow.BuroHeroEnrichment.start(source, [item(source.id, 99)], {
        getSecret: function () { return {}; },
        onItem: function () { cancelItems += 1; },
        onStatus: function (status) { cancelState = status.state; }
    });
    await waitFor(function () { return Boolean(cancelPending); }, 4000);
    cancelWindow.BuroHeroEnrichment.cancel();
    check('cancelamento aborta a chamada de detalhes ativa', abortCount === 1 && cancelState === 'CANCELLED');
    cancelPending.success({ synopsis: 'Resposta antiga', backdropUrl: 'https://images.public.test/late.jpg' });
    await new Promise(function (resolve) { setTimeout(resolve, 20); });
    check('resposta posterior ao cancelamento não publica texto nem arte',
        cancelItems === 0 && !cancelWindow.BuroHeroEnrichment.get(source.id, 'movie:99'));

    var reboundWindow = loadEngine();
    var reboundPending;
    var oldScreenItems = 0;
    var currentScreenItems = 0;
    reboundWindow.BuroXtream.loadHeroDetails = function (secret, candidate, success, failure) {
        reboundPending = { success: success, failure: failure };
        return { abort: function () {} };
    };
    reboundWindow.BuroHeroEnrichment.start(source, [item(source.id, 100)], {
        getSecret: function () { return {}; }, onItem: function () { oldScreenItems += 1; }
    });
    await waitFor(function () { return Boolean(reboundPending); }, 4000);
    reboundWindow.BuroHeroEnrichment.start(source, [item(source.id, 100)], {
        getSecret: function () { return {}; }, onItem: function () { currentScreenItems += 1; }
    });
    reboundPending.success({ synopsis: 'Tela atual' });
    await waitFor(function () { return currentScreenItems === 1; }, 4000);
    check('recompor a mesma Home atualiza o callback sem duplicar a chamada de rede',
        oldScreenItems === 0 && currentScreenItems === 1);

    process.stdout.write('Limite de memória e limpeza por fonte\n');
    var boundedWindow = loadEngine();
    var boundedComplete = 0;
    boundedWindow.BuroXtream.loadHeroDetails = function (secret, candidate, success) {
        success({ synopsis: 'Sinopse ' + candidate.id, backdropUrl: 'https://images.public.test/' + candidate.id + '.jpg' });
        return { abort: function () {} };
    };
    for (var batch = 0; batch < 3; batch += 1) {
        var batchItems = [];
        for (index = 0; index < 10; index += 1) { batchItems.push(item(source.id, batch * 10 + index)); }
        boundedWindow.BuroHeroEnrichment.start(source, batchItems, {
            getSecret: function () { return {}; },
            onComplete: function () { boundedComplete += 1; }
        });
        await waitFor((function (expected) { return function () { return boundedComplete === expected; }; }(batch + 1)), 500);
    }
    check('cache LRU nunca passa de vinte títulos',
        boundedWindow.BuroHeroEnrichment.cacheSize() === boundedWindow.BuroHeroEnrichment.MAX_CACHE_ENTRIES);
    boundedWindow.BuroHeroEnrichment.clearSource(source.id);
    check('excluir ou atualizar a fonte remove todos os metadados transitórios',
        boundedWindow.BuroHeroEnrichment.cacheSize() === 0);

    window.close(); cancelWindow.close(); reboundWindow.close(); boundedWindow.close();
    process.stdout.write('\nResultado: ' + passed + ' passaram, ' + failures.length + ' falharam.\n');
    if (failures.length) { process.exitCode = 1; }
}

run().catch(function (error) {
    process.stderr.write(error.stack + '\n');
    process.exitCode = 1;
});
