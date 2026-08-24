'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const appRoot = path.join(__dirname, '..', 'samsung-tizen');
const dom = new JSDOM('<!doctype html><html><body></body></html>', {
    runScripts: 'outside-only',
    url: 'https://app.synthetic.invalid/'
});

function load(relativePath) {
    dom.window.eval(fs.readFileSync(path.join(appRoot, relativePath), 'utf8'));
}

load('js/domain.js');
load('js/stalker.js');

const BuroStalker = dom.window.BuroStalker;

function queueTransport(entries) {
    const calls = [];
    const queue = entries.slice();
    return {
        calls,
        json(options, success, failure) {
            const entry = queue.length ? queue.shift() : { error: { code: 'NETWORK_ERROR' } };
            calls.push(options);
            if (entry.error) {
                failure(entry.error);
            } else {
                success(entry.payload);
            }
            return { abort() {} };
        }
    };
}

function fixedAdapter(transport) {
    return BuroStalker.createAdapter(transport, {
        clock: function () { return 1000; },
        timeZone: 'Europe/Berlin'
    });
}

function validSecret(adapter) {
    return adapter.credentials({
        portalUrl: 'https://portal.synthetic.invalid/stalker_portal/c/',
        macAddress: '00-1a-79-ab-cd-ef'
    });
}

function validSession() {
    return { token: 'synthetic-token', expiresAtEpochMillis: 601000 };
}

let passed = 0;

function test(name, body) {
    try {
        body();
        passed += 1;
        process.stdout.write('PASS ' + name + '\n');
    } catch (error) {
        process.stderr.write('FAIL ' + name + '\n');
        throw error;
    }
}

test('normaliza formatos usuais de MAC e mascara o segredo', function () {
    assert.strictEqual(BuroStalker.normalizeMac(' 001a79abcdef '), '00:1A:79:AB:CD:EF');
    assert.strictEqual(BuroStalker.normalizeMac('00.1a.79.ab.cd.ef'), '00:1A:79:AB:CD:EF');
    assert.strictEqual(BuroStalker.normalizeMac('nao-e-mac'), null);
    assert.strictEqual(BuroStalker.maskMac('00:1a:79:ab:cd:ef'), '**:**:**:**:**:EF');
});

test('rejeita MAC invalido antes de qualquer chamada de rede', function () {
    const transport = queueTransport([]);
    const adapter = fixedAdapter(transport);
    let error;
    adapter.handshake({ portalUrl: 'https://portal.synthetic.invalid', macAddress: 'invalid' },
        function () { assert.fail('handshake nao deveria ter sucesso'); },
        function (value) { error = value; });
    assert.strictEqual(error.code, 'UNAUTHORISED');
    assert.strictEqual(transport.calls.length, 0);
});

test('handshake tenta layouts conhecidos e envia identidade MAG somente em memoria', function () {
    const transport = queueTransport([
        { error: { code: 'HTTP_ERROR', status: 404 } },
        { payload: { js: { token: 'synthetic-token' } } }
    ]);
    const adapter = fixedAdapter(transport);
    const secret = validSecret(adapter);
    let session;
    adapter.handshake(secret, function (value) { session = value; }, assert.fail);

    assert.strictEqual(session.token, 'synthetic-token');
    assert.strictEqual(session.expiresAtEpochMillis, 601000);
    assert.strictEqual(transport.calls.length, 2);
    assert.ok(transport.calls[1].url.indexOf('/stalker_portal/server/load.php?') > 0);
    assert.ok(transport.calls[1].headers.Cookie.indexOf('mac=00%3A1A%3A79%3AAB%3ACD%3AEF') >= 0);
    assert.ok(transport.calls[1].clientUserAgent.indexOf('MAG200') >= 0);
    assert.strictEqual(transport.calls[1].maxBytes, 8 * 1024 * 1024);
});

test('token com separador de header e recusado antes de formar uma sessao', function () {
    const transport = queueTransport([{ payload: { js: { token: 'value\r\nInjected: header' } } }]);
    const adapter = fixedAdapter(transport);
    let error;
    adapter.handshake(validSecret(adapter), assert.fail, function (value) { error = value; });
    assert.strictEqual(error.code, 'UNAUTHORISED');
});

test('estado da conta interpreta bloqueio sem expor credenciais', function () {
    const transport = queueTransport([{ payload: {
        js: { blocked: '1', phone: '2099-01-01', tariff_plan: 'synthetic-plan' }
    } }]);
    const adapter = fixedAdapter(transport);
    let account;
    adapter.account(validSecret(adapter), validSession(), function (value) { account = value; }, assert.fail);
    assert.deepStrictEqual(JSON.parse(JSON.stringify(account)), {
        authenticated: true,
        expiryDate: '2099-01-01',
        tariffPlan: 'synthetic-plan',
        blocked: true
    });
    assert.strictEqual(transport.calls[0].headers.Authorization, 'Bearer synthetic-token');
});

test('categorias descartam a pseudo-categoria All', function () {
    const transport = queueTransport([{ payload: { js: [
        { id: '*', title: 'All' },
        { id: '7', title: 'Sports' }
    ] } }]);
    const adapter = fixedAdapter(transport);
    let categories;
    adapter.loadCategories(validSecret(adapter), validSession(), 'source-1', 'LIVE',
        function (value) { categories = value; }, assert.fail);
    assert.strictEqual(categories.length, 1);
    assert.strictEqual(categories[0].providerCategoryId, '7');
    assert.strictEqual(categories[0].sourceId, 'source-1');
    assert.strictEqual(categories[0].contentType, 'LIVE');
});

test('item persistivel informa pagina remota sem conter cmd, artwork ou URL privada', function () {
    const privateCommand = 'ffmpeg http://private.synthetic.invalid/ch/31915_';
    const transport = queueTransport([{ payload: { js: {
        total_items: '450', max_page_items: '200',
        data: [{
            id: '31915', name: 'Movie One', category_id: '3', year: '1999',
            rating_imdb: '7.4', cmd: privateCommand,
            screenshot_uri: 'http://private.synthetic.invalid/image.jpg?token=secret'
        }]
    } } }]);
    const adapter = fixedAdapter(transport);
    let page;
    adapter.loadItems(validSecret(adapter), validSession(), 'source-1', 'MOVIE', {
        id: 'category-local-3', providerCategoryId: '3'
    }, 2, function (value) { page = value; }, assert.fail);

    assert.strictEqual(page.totalItems, 450);
    assert.strictEqual(page.page, 2);
    assert.strictEqual(page.pageSize, 200);
    assert.strictEqual(page.hasMore, true);
    assert.strictEqual(page.items.length, 1);
    assert.strictEqual(page.items[0].sortOrder, 200);
    assert.strictEqual(page.items[0].logoUrl, null);
    assert.deepStrictEqual(JSON.parse(JSON.stringify(page.items[0].locator)), {
        kind: 'stalker', contentType: 'MOVIE', providerItemId: '31915'
    });
    const persisted = JSON.stringify(page.items[0]);
    assert.strictEqual(persisted.indexOf('private.synthetic.invalid'), -1);
    assert.strictEqual(persisted.indexOf('ffmpeg'), -1);
    assert.strictEqual(persisted.indexOf('secret'), -1);
});

test('resolve create_link tarde e devolve URL final apenas ao callback', function () {
    const command = 'ffmpeg http://private.synthetic.invalid/ch/9_';
    const finalUrl = 'https://cdn.synthetic.invalid/live/9.m3u8?play_token=one-use';
    const transport = queueTransport([
        { payload: { js: { total_items: 1, data: [{ id: '9', name: 'Channel', cmd: command }] } } },
        { payload: { js: { cmd: 'ffmpeg ' + finalUrl + ' -reconnect 1' } } }
    ]);
    const adapter = fixedAdapter(transport);
    const secret = validSecret(adapter);
    let page;
    let resolved;
    adapter.loadItems(secret, validSession(), 'source-1', 'LIVE', null, 1,
        function (value) { page = value; }, assert.fail);
    adapter.resolvePlayback(secret, validSession(), 'source-1', page.items[0].locator,
        function (value) { resolved = value; }, assert.fail);

    assert.strictEqual(resolved, finalUrl);
    assert.ok(transport.calls[1].url.indexOf('action=create_link') >= 0);
    assert.ok(transport.calls[1].url.indexOf(encodeURIComponent(command)) >= 0);
    assert.strictEqual(JSON.stringify(page.items[0]).indexOf('play_token'), -1);

    adapter.clearSession();
    let error;
    adapter.resolvePlayback(secret, validSession(), 'source-1', page.items[0].locator,
        assert.fail, function (value) { error = value; });
    assert.strictEqual(error.code, 'COMMAND_NOT_IN_MEMORY');
    assert.strictEqual(transport.calls.length, 2);
});

test('falhas retornam somente codigo sanitizado', function () {
    const leaked = 'https://portal.synthetic.invalid 00:1A:79:AB:CD:EF synthetic-token';
    const entries = [0, 1, 2, 3].map(function () {
        return { error: { code: 'NETWORK_ERROR', message: leaked } };
    });
    const transport = queueTransport(entries);
    const adapter = fixedAdapter(transport);
    let error;
    adapter.handshake(validSecret(adapter), assert.fail, function (value) { error = value; });
    const rendered = JSON.stringify(error);
    assert.strictEqual(error.code, 'NETWORK');
    assert.strictEqual(rendered.indexOf('portal.synthetic.invalid'), -1);
    assert.strictEqual(rendered.indexOf('00:1A:79'), -1);
    assert.strictEqual(rendered.indexOf('synthetic-token'), -1);
});

test('URL com credencial embutida e pagina fora do limite sao rejeitadas', function () {
    const transport = queueTransport([]);
    const adapter = fixedAdapter(transport);
    assert.throws(function () {
        adapter.credentials({
            portalUrl: 'https://user:password@portal.synthetic.invalid',
            macAddress: '00:1A:79:AB:CD:EF'
        });
    }, /PORTAL_URL_INVALID/);

    let error;
    adapter.loadItems(validSecret(adapter), validSession(), 'source-1', 'LIVE', null, 0,
        assert.fail, function (value) { error = value; });
    assert.strictEqual(error.code, 'PAGE_INVALID');
    assert.strictEqual(transport.calls.length, 0);
});

process.stdout.write('RESULT ' + passed + ' tests passed\n');
