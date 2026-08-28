/* Receber na TV um titulo compartilhado pelo celular/computador via codigo. */
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

function waitFor(predicate, timeoutMs) {
    var started = Date.now();
    return new Promise(function (resolve, reject) {
        function poll() {
            if (predicate()) { resolve(); return; }
            if (Date.now() - started > timeoutMs) { reject(new Error('timeout')); return; }
            setTimeout(poll, 10);
        }
        poll();
    });
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match = pattern.exec(html);
    var dom;
    var window;
    while (match) { scripts.push(match[1]); match = pattern.exec(html); }
    dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/' });
    window = dom.window;
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true,
        hiddenCategoryIds: [], lockedCategoryIds: []
    }));
    window.HTMLElement.prototype.scrollIntoView = function () {};
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
        keymanager: {
            getDataAliasList: function () { return []; },
            saveData: function (name, value, password, success) { success(); },
            getData: function () { throw { name: 'NotFoundError' }; },
            removeData: function () {}
        },
        tvinputdevice: { registerKey: function () {} },
        application: {
            getCurrentApplication: function () { return { exit: function () {}, getRequestedAppControl: function () { return null; } }; },
            launchAppControl: function (control, id, success) { if (success) { success(); } }
        }
    };
    scripts.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.init();
    return window;
}

function put(window, store, value) {
    return new Promise(function (resolve, reject) { window.BuroStorage.put(store, value, resolve, reject); });
}

function activate(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
}

function pairedPayload(window, value) {
    var calls = [];
    window.BuroNetwork.json = function (options, success) {
        var requestPath = String(options.url || '').replace(/^https?:\/\/[^/]+/, '');
        calls.push({ path: requestPath, body: options.body });
        setTimeout(function () {
            if (requestPath === '/v1/pair/start') {
                success({ code: '246810', kind: 'open_title', expiresInSeconds: 300 });
            } else if (requestPath === '/v1/pair/claim') {
                success({ status: 'ready', kind: 'open_title', payload: value });
            }
        }, 0);
        return { abort: function () {} };
    };
    return calls;
}

async function readySettings(window) {
    var profile = { id: 'profile-room', name: 'Sala', sourceId: 'source-room', avatarKey: 'gold', isKids: false };
    var source = { id: 'source-room', name: 'Fonte local', type: 'REMOTE_M3U', channelCount: 1 };
    await waitFor(function () { return Boolean(window.document.querySelector('[data-action="profile-form"]')); }, 8000);
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [];
    window.BuroApp.state.items = [];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.preferences.activeProfileId = profile.id;
    window.BuroApp.state.ready = true;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SETTINGS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    return { profile: profile, source: source };
}

async function run() {
    var window;
    var context;
    var item;
    var payload;
    var calls;

    process.stdout.write('Configuracoes oferecem receber um titulo\n');
    window = loadApp();
    context = await readySettings(window);
    check('ha um cartao focavel para receber de outro aparelho',
        Boolean(window.document.querySelector('[data-action="pair-title"].focusable')));
    pairedPayload(window, '');
    activate(window, '[data-action="pair-title"]');
    await waitFor(function () { return window.BuroApp.state.screenData && window.BuroApp.state.screenData.code; }, 8000);
    check('o pareamento pede explicitamente o tipo open_title',
        window.BuroApp.state.screenData.kind === 'open_title');
    check('a tela usa instrucao de titulo, nao texto de chave de API',
        window.document.querySelector('.pair-panel').textContent.indexOf(window.BuroI18n.t('receiverStep3')) >= 0 &&
        window.document.querySelector('.pair-panel').textContent.indexOf(window.BuroI18n.t('pairStep3')) < 0);
    window.close();

    process.stdout.write('Link oficial abre somente o item local\n');
    window = loadApp();
    context = await readySettings(window);
    item = {
        id: 'local-movie', sourceId: context.source.id, categoryId: 'movies',
        contentType: 'MOVIE', name: 'Central do Brasil', year: 1998
    };
    await put(window, 'items', item);
    payload = 'https://iptvburo.pages.dev/t/?id=' +
        encodeURIComponent(window.BuroShare.identity('MOVIE', item.name, item.year)) +
        '&t=' + encodeURIComponent(item.name) + '&y=1998';
    calls = pairedPayload(window, payload);
    activate(window, '[data-action="pair-title"]');
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.parent &&
            window.BuroApp.state.screenData.parent.id === item.id;
    }, 8000);
    check('o titulo recebido foi resolvido no IndexedDB e abriu detalhes locais',
        window.BuroApp.state.screenData.parent.id === item.id);
    check('nenhuma validacao TMDb ou URL do provedor foi consultada',
        calls.every(function (call) { return call.path === '/v1/pair/start' || call.path === '/v1/pair/claim'; }));
    check('o payload nao foi persistido nas preferencias',
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf('Central') === -1);
    window.close();

    process.stdout.write('Payload hostil e recusado\n');
    window = loadApp();
    await readySettings(window);
    pairedPayload(window, 'https://evil.test/t/?id=movie%3Ateste%3A2024&t=Teste&y=2024');
    activate(window, '[data-action="pair-title"]');
    await waitFor(function () { return window.BuroApp.state.screenData && window.BuroApp.state.screenData.error; }, 8000);
    check('URL externa permanece na tela de pareamento com erro',
        window.BuroApp.state.screen === 'PAIRING' && window.BuroApp.state.screenData.error === 'PAIRING_PAYLOAD_INVALID');
    check('URL externa nao cria pedido pendente', !window.BuroApp._pendingSharedTitle());
    window.close();

    process.stdout.write('Contrato do parser e traducoes\n');
    window = loadApp();
    check('parser aceita a URI privada registrada',
        Boolean(window.BuroShare.parsePairingPayload('iptvburo://title?id=movie%3Ateste%3A2024&t=Teste&y=2024')));
    check('parser recusa identidade malformada',
        !window.BuroShare.parsePairingPayload('https://iptvburo.pages.dev/t/?id=movie%3A%3A2024&t=Teste'));
    check('os cinco idiomas possuem textos do receptor',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['receiverTitle', 'receiverHint', 'receiverAction', 'receiverStep3',
                'receiverInvalid', 'receiverReceived'].every(function (key) {
                var value = window.BuroI18n.t(key);
                return Boolean(value) && value !== key;
            });
        }));
    window.close();

    process.stdout.write('\n' + passed + ' verificacoes aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
}

run().catch(function (error) {
    process.stderr.write('Falha inesperada: ' + (error.stack || error.message) + '\n');
    process.exit(1);
});
