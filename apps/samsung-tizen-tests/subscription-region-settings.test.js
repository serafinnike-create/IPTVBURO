/* Paridade com Windows: a regiao de streaming e uma preferencia por perfil. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var SCRIPT_FILES = (function () {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var files = [];
    var match = pattern.exec(html);
    while (match) { files.push(match[1]); match = pattern.exec(html); }
    return files;
}());
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

function loadApp(preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences));
    window.tizen = {
        keymanager: {
            getDataAliasList: function () {
                return Object.keys(secureData).map(function (name) { return { name: name }; });
            },
            saveData: function (name, value, password, success) { secureData[name] = value; success(); },
            getData: function (alias) {
                if (!secureData[alias.name]) { throw { name: 'NotFoundError' }; }
                return secureData[alias.name];
            },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: {
            getCurrentApplication: function () { return { exit: function () {} }; },
            launchAppControl: function (control, id, success) { if (success) { success(); } }
        }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    return window;
}

function click(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { return null; }
    window.BuroApp._activate(element);
    return element;
}

async function createProfileAndOpenSettings(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 6000);
    click(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 6000);
    window.document.getElementById('profile-name').value = 'Sala';
    click(window, '[data-action="profile-save"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.nav-list [data-section="SETTINGS"]'));
    }, 6000);
    click(window, '.nav-list [data-action="section"][data-section="SETTINGS"]');
}

function showSettingsFor(window, profile) {
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.preferences.activeProfileId = profile.id;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'SETTINGS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
}

function selectedRegion(window) {
    var element = window.document.querySelector('[data-action="settings-region"][aria-pressed="true"]');
    return element && element.getAttribute('data-region');
}

function report() {
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

async function run() {
    var window;
    var controls;
    var firstProfile;
    var secondProfile;
    var stored;
    var legacy;

    process.stdout.write('A regiao aparece em Configuracoes e pertence ao perfil ativo\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true, acceptedLegal: true });
    await createProfileAndOpenSettings(window);
    firstProfile = window.BuroApp.state.activeProfile;
    controls = window.document.querySelectorAll('[data-action="settings-region"]');
    check('as cinco regioes suportadas ficam visiveis', controls.length === 5);
    check('Brasil e a regiao inicial do primeiro perfil', selectedRegion(window) === 'BR');
    click(window, '[data-action="settings-region"][data-region="DE"]');
    check('a selecao muda para Alemanha', selectedRegion(window) === 'DE');
    check('a regiao fica salva no perfil ativo',
        window.BuroApp.state.preferences.tmdbRegionsByProfile &&
        window.BuroApp.state.preferences.tmdbRegionsByProfile[firstProfile.id] === 'DE');
    stored = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    check('o mapa por perfil foi persistido',
        stored.tmdbRegionsByProfile && stored.tmdbRegionsByProfile[firstProfile.id] === 'DE');

    secondProfile = { id: 'profile-second', name: 'Quarto', avatarKey: 'blue', isKids: false, sourceId: null };
    showSettingsFor(window, secondProfile);
    check('um perfil novo comeca no Brasil', selectedRegion(window) === 'BR');
    click(window, '[data-action="settings-region"][data-region="IT"]');
    check('o segundo perfil guarda sua propria regiao',
        window.BuroApp.state.preferences.tmdbRegionsByProfile['profile-second'] === 'IT');
    showSettingsFor(window, firstProfile);
    check('voltar ao primeiro perfil restaura Alemanha', selectedRegion(window) === 'DE');

    process.stdout.write('As escolhas sobrevivem a uma nova abertura\n');
    stored = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    window.close();
    window = loadApp(stored);
    showSettingsFor(window, firstProfile);
    check('a nova abertura restaura a primeira regiao', selectedRegion(window) === 'DE');
    showSettingsFor(window, secondProfile);
    check('a nova abertura restaura a segunda regiao', selectedRegion(window) === 'IT');

    process.stdout.write('O controle remoto e todos os idiomas cobrem a preferencia\n');
    window.BuroApp._focusAction('settings-region');
    window.BuroApp._onKeyDown({ keyCode: window.BuroKeys.CODES.ENTER, preventDefault: function () {} });
    check('ENTER aplica a regiao focada', selectedRegion(window) === 'BR');
    check('rotulo e explicacao existem nos cinco idiomas',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['subscriptionsRegion', 'settingsRegionHint'].every(function (name) {
                var value = window.BuroI18n.t(name);
                return Boolean(value) && value !== name;
            });
        }));
    window.close();

    process.stdout.write('Uma resposta atrasada da regiao anterior e descartada\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true, acceptedLegal: true });
    await createProfileAndOpenSettings(window);
    window.BuroStorage.secureSave('tmdb-shared', { apiKey: 'synthetic-tmdb-key' }, function () {}, function () {});
    await (function testLateSubscriptionResponse() {
        var requests = [];
        window.BuroTmdb.loadShelves = function (key, region, kind, language, progress, success, failure) {
            var request = { region: region, success: success, failure: failure, aborted: false };
            requests.push(request);
            return { abort: function () { request.aborted = true; } };
        };
        click(window, '.nav-list [data-action="section"][data-section="SUBSCRIPTIONS"]');
        return waitFor(function () { return requests.length === 1; }, 6000).then(function () {
            click(window, '[data-action="subscription-region"][data-region="DE"]');
            return waitFor(function () { return requests.length === 2; }, 6000);
        }).then(function () {
            check('trocar a regiao aborta a consulta anterior', requests[0].aborted === true);
            requests[0].success([{ providerName: 'Resultado antigo', titles: [] }]);
            check('o callback antigo nao preenche a nova regiao',
                window.BuroApp.state.screenData.region === 'DE' &&
                window.BuroApp.state.screenData.shelves.length === 0);
            requests[1].success([{ providerName: 'Resultado atual', titles: [] }]);
            check('somente a resposta da regiao atual e aplicada',
                window.BuroApp.state.screenData.shelves.length === 1 &&
                window.BuroApp.state.screenData.shelves[0].providerName === 'Resultado atual');
        });
    }());
    window.close();

    process.stdout.write('Preferencias antigas sao migradas sem perder a escolha\n');
    window = loadApp({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true,
        activeProfileId: 'legacy-profile', tmdbRegion: 'PT'
    });
    legacy = window.BuroApp.state.preferences;
    check('a regiao global antiga migra para o perfil que estava ativo',
        legacy.tmdbRegionsByProfile && legacy.tmdbRegionsByProfile['legacy-profile'] === 'PT');
    window.close();

    report();
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
