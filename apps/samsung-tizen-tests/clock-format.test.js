/* Paridade do relogio do cabecalho com o Windows: 24h por padrao e 12h opcional. */
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

function installFixedClock(window) {
    var NativeDate = window.Date;
    var fixed = new NativeDate(2026, 7, 24, 21, 5, 0, 0);
    function FixedDate() {
        var args = Array.prototype.slice.call(arguments);
        var BoundDate;
        if (!args.length) { return new NativeDate(fixed.getTime()); }
        BoundDate = Function.prototype.bind.apply(NativeDate, [null].concat(args));
        return new BoundDate();
    }
    FixedDate.prototype = NativeDate.prototype;
    FixedDate.now = function () { return fixed.getTime(); };
    FixedDate.parse = NativeDate.parse;
    FixedDate.UTC = NativeDate.UTC;
    window.Date = FixedDate;
}

function loadApp(preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    installFixedClock(window);
    window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences));
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
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
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
    return element;
}

async function createProfileAndOpenSettings(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 6000);
    click(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 6000);
    window.document.getElementById('profile-name').value = 'Casa';
    click(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.topbar-clock strong')); }, 6000);
    click(window, '.nav-list [data-action="section"][data-section="SETTINGS"]');
}

function key(window, keyCode) {
    window.BuroApp._onKeyDown({ keyCode: keyCode, preventDefault: function () {} });
}

function report() {
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return false;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
    return true;
}

async function run() {
    var window;
    var stored;
    var clock12;
    var clock24;

    process.stdout.write('O formato de 24 horas continua sendo o padrao\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true, acceptedLegal: true });
    await createProfileAndOpenSettings(window);
    check('o cabecalho nasce em 24 horas',
        window.document.querySelector('.topbar-clock strong').textContent === '21:05');
    clock24 = window.document.querySelector('[data-action="clock-format"][data-value="24"]');
    clock12 = window.document.querySelector('[data-action="clock-format"][data-value="12"]');
    check('as duas escolhas ficam visiveis nas configuracoes', Boolean(clock24) && Boolean(clock12));
    check('24 horas anuncia a selecao atual',
        clock24 && clock24.getAttribute('aria-pressed') === 'true' &&
        clock12 && clock12.getAttribute('aria-pressed') === 'false');
    if (!clock24 || !clock12) {
        window.close();
        report();
        return;
    }

    process.stdout.write('Escolher 12 horas atualiza e persiste o cabecalho\n');
    window.BuroApp._activate(clock12);
    check('o cabecalho usa h:mm AM/PM como o Windows',
        window.document.querySelector('.topbar-clock strong').textContent === '9:05 PM');
    stored = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    check('a escolha de 12 horas foi persistida', stored.uses24HourClock === false);
    check('a escolha visual e acessivel acompanha o valor salvo',
        window.document.querySelector('[data-action="clock-format"][data-value="12"]')
            .getAttribute('aria-pressed') === 'true');
    window.close();

    process.stdout.write('A escolha sobrevive a uma nova abertura\n');
    window = loadApp(stored);
    await createProfileAndOpenSettings(window);
    check('a nova abertura restaura 12 horas',
        window.document.querySelector('.topbar-clock strong').textContent === '9:05 PM');

    process.stdout.write('O controle remoto consegue voltar para 24 horas\n');
    window.BuroApp._focusAction('clock-format');
    key(window, window.BuroKeys.CODES.ENTER);
    check('ENTER aplica a opcao focada',
        window.document.querySelector('.topbar-clock strong').textContent === '21:05');
    stored = JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'));
    check('a escolha feita pelo D-pad tambem e persistida', stored.uses24HourClock === true);

    process.stdout.write('Os cinco idiomas explicam a preferencia\n');
    check('rotulo, dica e escolhas existem em todos os idiomas',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['clockLabel', 'clockHint', 'clock24h', 'clock12h'].every(function (name) {
                var value = window.BuroI18n.t(name);
                return Boolean(value) && value !== name;
            });
        }));
    window.close();

    report();
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exitCode = 1;
});
