/* Category-management parity tests. Synthetic fixtures only. */
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
    while (match) {
        files.push(match[1]);
        match = pattern.exec(html);
    }
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

function loadApp() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true
    }));
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
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    SCRIPT_FILES.forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    window.BuroApp.init();
    return window;
}

async function run() {
    var window = loadApp();
    var state;
    var source;
    var otherSource;
    var profile;
    var categories = [];
    var index;
    var next;
    var hiddenButton;
    var lockedButton;
    var persisted;

    await waitFor(function () { return window.BuroApp.state.ready; }, 4000);
    state = window.BuroApp.state;
    source = { id: 'source-category-settings', name: 'Fonte ativa', type: 'XTREAM' };
    otherSource = { id: 'source-category-other', name: 'Outra fonte', type: 'XTREAM' };
    profile = {
        id: 'profile-category-settings', name: 'Sala', avatarKey: 'gold', isKids: false,
        sourceId: source.id, createdAt: Date.now()
    };
    for (index = 0; index < 325; index += 1) {
        categories.push({
            id: 'category-active-' + index, sourceId: source.id, contentType: 'MOVIE',
            name: 'Categoria ' + ('000' + index).slice(-3), sortOrder: index
        });
    }
    categories.push({
        id: 'category-active-adult', sourceId: source.id, contentType: 'MOVIE',
        name: 'XXX adultos', sortOrder: 325
    });
    for (index = 0; index < 12; index += 1) {
        categories.push({
            id: 'category-other-' + index, sourceId: otherSource.id, contentType: 'LIVE',
            name: 'Categoria de outra fonte ' + index, sortOrder: index
        });
    }

    state.sources = [source, otherSource];
    state.profiles = [profile];
    state.activeProfile = profile;
    state.activeSource = source;
    state.categories = categories;
    state.preferences.parentalPin = { salt: 'synthetic', hash: 'synthetic' };
    state.preferences.hiddenCategoryIds = [];
    state.preferences.lockedCategoryIds = [];
    state.screen = 'SHELL';
    state.section = 'SETTINGS';
    state.screenData = null;
    window.BuroApp.render();

    process.stdout.write('Gerenciamento completo de categorias\n');
    check('o cartão conta somente as 326 categorias da fonte ativa',
        window.document.querySelector('[data-action="category-settings"] p').textContent === '326');
    window.BuroApp._activate(window.document.querySelector('[data-action="category-settings"]'));
    check('a primeira página limita o DOM a 40 categorias',
        state.screen === 'CATEGORY_SETTINGS' &&
        window.document.querySelectorAll('.guard-row').length === 40 && state.screenData.page === 0);
    check('categorias de outra fonte nunca aparecem nos ajustes do perfil',
        window.document.body.textContent.indexOf('Categoria de outra fonte') === -1);
    check('o paginador anuncia nove páginas e o intervalo inicial',
        window.document.querySelector('.guard-pagination').textContent.indexOf('Página 1 de 9') >= 0 &&
        window.document.querySelector('.guard-pagination').textContent.indexOf('1–40 / 326') >= 0);

    for (index = 0; index < 8; index += 1) {
        next = window.document.querySelector('[data-action="category-settings-page-next"]');
        window.BuroApp._activate(next);
    }
    check('todas as categorias depois da antiga barreira de 300 são alcançáveis',
        state.screenData.page === 8 && window.document.querySelectorAll('.guard-row').length === 6 &&
        window.document.body.textContent.indexOf('Categoria 324') >= 0);
    check('na última página o foco retorna ao botão Página anterior',
        !window.document.querySelector('[data-action="category-settings-page-next"]') &&
        window.document.querySelector('[data-action="category-settings-page-previous"]').classList.contains('focused'));

    hiddenButton = window.document.querySelector('[data-action="category-hidden"][data-id="category-active-324"]');
    window.BuroApp._activate(hiddenButton);
    check('ocultar uma categoria além da posição 300 preserva página, estado e semântica',
        state.screenData.page === 8 && state.preferences.hiddenCategoryIds.indexOf('category-active-324') >= 0 &&
        window.document.querySelector('[data-action="category-hidden"][data-id="category-active-324"]')
            .getAttribute('aria-pressed') === 'true');
    persisted = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    check('a alteração de visibilidade continua persistida por perfil local',
        persisted.hiddenCategoryIds.indexOf('category-active-324') >= 0);

    lockedButton = window.document.querySelector('[data-action="category-locked"][data-id="category-active-324"]');
    window.BuroApp._activate(lockedButton);
    check('bloquear uma categoria além da posição 300 também preserva a página',
        state.screenData.page === 8 && state.preferences.lockedCategoryIds.indexOf('category-active-324') >= 0 &&
        window.document.querySelector('[data-action="category-locked"][data-id="category-active-324"]')
            .getAttribute('aria-pressed') === 'true');

    profile.isKids = true;
    window.BuroApp.render();
    check('perfil Kids não revela o nome de categoria adulta nos ajustes',
        window.document.body.textContent.indexOf('XXX adultos') === -1 &&
        window.document.querySelectorAll('.guard-row').length === 5);
    check('a remoção dinâmica de uma categoria ajusta a última página sem perder navegação',
        state.screenData.page === 8 &&
        window.document.querySelector('.guard-pagination').textContent.indexOf('321–325 / 325') >= 0);

    state.activeSource = otherSource;
    profile.sourceId = otherSource.id;
    state.screen = 'SHELL';
    state.section = 'SETTINGS';
    state.screenData = null;
    window.BuroApp.render();
    check('trocar a fonte ativa atualiza a contagem e não reutiliza a página anterior',
        window.document.querySelector('[data-action="category-settings"] p').textContent === '12');

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
