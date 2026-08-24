/* Link de titulo recebido pelo app-control Samsung: frio, quente e seguro. */
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

function loadApp(initialRequestedUri) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match = pattern.exec(html);
    var dom;
    var window;
    var requestedUri = initialRequestedUri || '';
    var requestedReadsRemaining = 0;
    var secureData = {};
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
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) { return secureData[alias.name]; },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: {
            getCurrentApplication: function () {
                return {
                    exit: function () {},
                    getRequestedAppControl: function () {
                        if (requestedReadsRemaining > 0) {
                            requestedReadsRemaining -= 1;
                            return null;
                        }
                        return requestedUri ? { appControl: { uri: requestedUri } } : null;
                    }
                };
            },
            launchAppControl: function (control, id, success) { if (success) { success(); } }
        }
    };
    window.__setRequestedUri = function (value) { requestedUri = value; };
    window.__setRequestedUriAfterReads = function (value, reads) {
        requestedUri = value;
        requestedReadsRemaining = reads;
    };
    scripts.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.init();
    return window;
}

function put(window, store, value) {
    return new Promise(function (resolve, reject) { window.BuroStorage.put(store, value, resolve, reject); });
}

async function run() {
    var title = 'O Auto da Compadecida + Extras';
    var identity;
    var appUri;
    var cold;
    var window;
    var profile;
    var activeSource;
    var otherSource;
    var category;
    var localItem;
    var otherItem;
    var fetchCount = 0;

    process.stdout.write('Inicializacao fria\n');
    cold = loadApp('iptvburo://title?id=movie%3Ateste%3A2024&t=Teste&y=2024');
    check('pedido frio e capturado antes de perfil e catalogo estarem prontos',
        cold.BuroApp._pendingSharedTitle() && cold.BuroApp._pendingSharedTitle().identity === 'movie:teste:2024');
    check('pedido frio nao atravessa o gate nem inventa um item',
        cold.BuroApp.state.screen === 'BOOT' && !cold.BuroApp.state.screenData);
    cold.close();

    process.stdout.write('Aplicativo aberto\n');
    window = loadApp('');
    await waitFor(function () { return window.document.querySelector('[data-action="profile-form"]'); }, 2500);
    profile = { id: 'profile-local', name: 'Sala', sourceId: 'source-active', avatarKey: 'gold', isKids: false };
    activeSource = { id: 'source-active', name: 'Fonte ativa', type: 'REMOTE_M3U', channelCount: 1 };
    otherSource = { id: 'source-other', name: 'Outra fonte', type: 'REMOTE_M3U', channelCount: 1 };
    category = { id: 'category-hidden', sourceId: activeSource.id, contentType: 'MOVIE', name: 'Filmes' };
    localItem = {
        id: 'movie:local-decorated', sourceId: activeSource.id, categoryId: category.id,
        contentType: 'MOVIE', name: '[4K] ' + title + ' (2024) DUAL', year: 2024
    };
    otherItem = {
        id: 'movie:other-source', sourceId: otherSource.id, categoryId: 'other-category',
        contentType: 'MOVIE', name: title, year: 2024
    };
    await put(window, 'items', localItem);
    await put(window, 'items', otherItem);
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.sources = [activeSource, otherSource];
    window.BuroApp.state.categories = [category, {
        id: 'other-category', sourceId: otherSource.id, contentType: 'MOVIE', name: 'Outros filmes'
    }];
    /* O item intencionalmente nao entra na amostra em memoria: a busca deve
       percorrer o IndexedDB completo e hidrata-lo somente quando encontrar. */
    window.BuroApp.state.items = [];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = activeSource;
    window.BuroApp.state.preferences.activeProfileId = profile.id;
    window.BuroApp.state.preferences.hiddenCategoryIds = [category.id];
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SETTINGS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    identity = window.BuroShare.identity('MOVIE', title, 2024);
    appUri = 'iptvburo://title?id=' + encodeURIComponent(identity) + '&t=' + encodeURIComponent(title) +
        '&y=2024&img=' + encodeURIComponent('https://image.tmdb.org/t/p/w342/public.jpg');
    window.fetch = function () { fetchCount += 1; return Promise.reject(new Error('unexpected fetch')); };
    window.__setRequestedUriAfterReads(appUri, 2);
    window.dispatchEvent(new window.Event('focus'));
    await waitFor(function () { return window.document.querySelector('.shared-link-notice'); }, 4000);
    check('retomada quente consulta o pedido atrasado sem reiniciar a sessao',
        window.BuroApp._pendingSharedTitle() && window.BuroApp.state.screen === 'SHELL');
    window.dispatchEvent(new window.Event('appcontrol'));
    check('evento e retomada do mesmo pedido sao deduplicados',
        window.document.querySelectorAll('.shared-link-notice').length === 1);
    check('outra fonte e categoria oculta nao viram atalho lateral',
        !window.BuroApp.state.screenData && window.document.querySelector('[data-action="shared-retry"]'));
    check('aviso persistente tem semantica acessivel e duas acoes D-pad',
        window.document.querySelector('.shared-link-notice').getAttribute('role') === 'alertdialog' &&
        window.document.querySelectorAll('.shared-link-notice .focusable').length === 2 &&
        window.document.activeElement.getAttribute('data-action') === 'shared-retry');
    check('metadado recebido nao dispara consulta de imagem ou rede', fetchCount === 0);

    window.BuroApp._activate(window.document.querySelector('[data-action="shared-dismiss"]'));
    check('Fechar remove somente o aviso e conserva o pedido para uma tentativa futura',
        !window.document.querySelector('.shared-link-notice') && window.BuroApp._pendingSharedTitle());

    window.BuroApp._receiveRequestedAppControl(appUri);
    await waitFor(function () { return window.document.querySelector('.shared-link-notice'); }, 4000);
    window.BuroApp.state.preferences.hiddenCategoryIds = [];
    window.BuroApp._activate(window.document.querySelector('[data-action="shared-retry"]'));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.kind === 'movie';
    }, 4000);
    check('retry encontra o titulo decorado no IndexedDB da fonte ativa e abre os detalhes locais',
        window.BuroApp.state.screenData.parent.id === localItem.id &&
        window.BuroApp.state.items.some(function (item) { return item.id === localItem.id; }));
    check('pedido resolvido sai da memoria e nao e persistido',
        !window.BuroApp._pendingSharedTitle() &&
        (window.localStorage.getItem('iptvburo.preferences.v1') || '').indexOf(identity) === -1);

    check('URI hostil e ignorada sem substituir a navegacao atual',
        !window.BuroApp._receiveRequestedAppControl('https://evil.test/?id=' + encodeURIComponent(identity) + '&t=Teste') &&
        window.BuroApp.state.screenData.parent.id === localItem.id);
    window.close();

    process.stdout.write('\n' + passed + ' verificacoes aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
}

run().catch(function (error) {
    process.stderr.write('Falha inesperada: ' + error.message + '\n');
    process.exit(1);
});
