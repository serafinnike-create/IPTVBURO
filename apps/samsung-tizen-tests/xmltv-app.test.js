/* Full-shell XMLTV regression tests. Synthetic fixtures only. */
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

function call(window, method, args) {
    return new Promise(function (resolve, reject) {
        method.apply(window.BuroStorage, args.concat([resolve, reject]));
    });
}

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match;
    var secureData = {};
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    while ((match = pattern.exec(html)) !== null) { scripts.push(match[1]); }
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () { return Object.keys(secureData).map(function (name) { return { name: name }; }); },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) {
                if (!secureData[alias.name]) { throw { name: 'NotFoundError' }; }
                return secureData[alias.name];
            },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    scripts.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.init();
    return window;
}

async function run() {
    var window = loadApp();
    var source;
    var parsed;
    var item;
    var category;
    var profile;
    var requestUrl = '';
    var xml;
    var button;
    await waitFor(function () { return window.BuroApp.state.ready; }, 4000);

    source = { id: 'source-xmltv-app', name: 'Lista XMLTV', type: 'REMOTE_M3U',
        channelCount: 1, createdAt: Date.now(), updatedAt: null };
    parsed = window.BuroM3u.parse('#EXTM3U url-tvg="https://guide.private/epg.xml?token=never-store"\n' +
        '#EXTINF:-1 tvg-id="canal.br" group-title="TV",Canal Teste\nhttps://stream.private/live.m3u8', source.id);
    item = window.BuroM3u.metadata(parsed)[0];
    category = { id: item.categoryId, sourceId: source.id, providerCategoryId: item.categoryId,
        name: 'TV', contentType: 'LIVE', sortOrder: 0 };
    profile = { id: 'profile-xmltv-app', name: 'Teste', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now() };
    await call(window, window.BuroStorage.put, ['sources', source]);
    await call(window, window.BuroStorage.put, ['categories', category]);
    await call(window, window.BuroStorage.put, ['items', item]);
    await call(window, window.BuroStorage.secureSave, [source.id, {
        url: 'https://playlist.private/list.m3u?credential=hidden',
        epgUrls: parsed.header.epgUrls
    }]);
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = [item];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    xml = '<tv><programme channel="canal.br" start="20260824200000 +0000" stop="20260824210000 +0000">' +
        '<title>Jornal sintético</title><desc>Resumo público.</desc></programme></tv>';
    window.BuroNetwork.text = function (options, success) {
        requestUrl = options.url;
        window.setTimeout(function () { success(xml); }, 40);
        return { abort: function () {} };
    };
    button = window.document.createElement('button');
    button.setAttribute('data-action', 'live-details'); button.setAttribute('data-id', item.id);
    window.BuroApp._activate(button);
    check('detalhe abre imediatamente com reprodução disponível durante o guia',
        window.BuroApp.state.screenData && window.BuroApp.state.screenData.epgLoading === true &&
        Boolean(window.document.querySelector('[data-action="play"]')) &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('loading')) >= 0);
    window.BuroApp._focusAction('play');
    check('Assistir ao vivo permanece alcançável pelo foco do D-pad',
        window.document.querySelector('[data-action="play"]').classList.contains('focused'));
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.epgLoading === false;
    }, 4000);
    check('guia XMLTV aparece cruzado por tvg-id',
        window.BuroApp.state.screenData.schedule.length === 1 &&
        window.document.body.textContent.indexOf('Jornal sintético') >= 0 &&
        requestUrl.indexOf('guide.private') >= 0);
    check('URL de guia, playlist e token nunca entram na tela nem no estado',
        JSON.stringify(window.BuroApp.state).indexOf('guide.private') === -1 &&
        JSON.stringify(window.BuroApp.state).indexOf('playlist.private') === -1 &&
        window.document.documentElement.textContent.indexOf('never-store') === -1);

    window.BuroXmltv.clear(source.id);
    window.BuroNetwork.text = function (options, success, failure) {
        window.setTimeout(function () { failure({ code: 'NETWORK_ERROR' }); }, 5);
        return { abort: function () {} };
    };
    window.BuroApp._activate(button);
    await waitFor(function () {
        return window.BuroApp.state.screenData && window.BuroApp.state.screenData.epgLoading === false;
    }, 4000);
    check('falha XMLTV deixa o canal reproduzível e mostra guia indisponível',
        window.BuroApp.state.screenData.schedule.length === 0 &&
        Boolean(window.document.querySelector('[data-action="play"]')) &&
        window.document.body.textContent.indexOf(window.BuroI18n.t('epgUnavailable')) >= 0);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        process.exitCode = 1; return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte XMLTV do app: ' + error.stack + '\n');
    process.exitCode = 1;
});
