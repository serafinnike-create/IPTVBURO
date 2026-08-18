/* O sino e a central de avisos. Mesmas regras do NotificationCentre compartilhado. */
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

function loadApp(preferences) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secureData = {};
    window.indexedDB = new fakeIndexedDb.IDBFactory();
    if (preferences) {
        window.localStorage.setItem('iptvburo.preferences.v1', JSON.stringify(preferences));
    }
    window.tizen = {
        ApplicationControl: function (operation, uri) { this.operation = operation; this.uri = uri; },
        keymanager: {
            getDataAliasList: function () { return []; },
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

function notice(id, values) {
    var base = { id: id, kind: 'REMINDER', title: 'Aviso ' + id, createdAt: 1000, read: false };
    Object.keys(values || {}).forEach(function (key) { base[key] = values[key]; });
    return base;
}

async function run() {
    var window;
    var N;
    var rows;
    var many;
    var index;

    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    N = window.BuroNotifications;

    process.stdout.write('Nada entra na central sem id, tipo e título\n');
    check('um aviso sem id é recusado', N.notification({ kind: 'REMINDER', title: 'x' }) === null);
    check('um aviso sem título é recusado', N.notification({ id: 'a', kind: 'REMINDER' }) === null);
    check('um tipo desconhecido é recusado',
        N.notification({ id: 'a', kind: 'PROMOCAO', title: 'x' }) === null);
    check('os três tipos conhecidos passam',
        ['REMINDER', 'NEW_EPISODE', 'NEW_SEASON'].every(function (kind) {
            return N.notification({ id: 'a', kind: kind, title: 'x' }) !== null;
        }));

    process.stdout.write('O mesmo id não entra duas vezes\n');
    rows = N.add(N.add([], notice('reminder-digest:2026-08-18')), notice('reminder-digest:2026-08-18'));
    check('reconstruir o digest não duplica o aviso', rows.length === 1);
    rows = N.add(N.markAllRead(rows), notice('reminder-digest:2026-08-18'));
    check('reconstruir não marca como não lido o que já foi lido',
        rows.length === 1 && rows[0].read === true);

    process.stdout.write('O id do digest é a data, não os títulos\n');
    check('o id do digest depende só do dia',
        N.reminderDigestId('2026-08-18') === 'reminder-digest:2026-08-18');
    check('dois dias diferentes são dois avisos',
        N.add(N.add([], notice(N.reminderDigestId('2026-08-18'))),
            notice(N.reminderDigestId('2026-08-19'))).length === 2);
    check('episódio e temporada têm ids próprios',
        N.episodeId('serie', 2, 5) === 'episode:serie:s2:e5' &&
        N.seasonId('serie', 2) === 'season:serie:s2');

    process.stdout.write('A contagem conta o que não foi lido\n');
    rows = [notice('a'), notice('b', { read: true }), notice('c')];
    check('só o não lido é contado', N.unreadCount(rows) === 2);
    check('marcar todos zera a contagem', N.unreadCount(N.markAllRead(rows)) === 0);

    process.stdout.write('Mais novo primeiro\n');
    rows = N.newestFirst([notice('a', { createdAt: 10 }), notice('b', { createdAt: 30 }),
        notice('c', { createdAt: 20 })]);
    check('a ordem é decrescente por data',
        rows[0].id === 'b' && rows[1].id === 'c' && rows[2].id === 'a');

    process.stdout.write('O teto descarta os lidos antes dos não lidos\n');
    many = [];
    for (index = 0; index < N.MAX_HELD; index += 1) {
        many.push(notice('lido-' + index, { read: true, createdAt: 100 + index }));
    }
    many.push(notice('novidade', { read: false, createdAt: 1 }));
    rows = N.trimmed(many);
    check('o teto é respeitado', rows.length === N.MAX_HELD);
    check('a novidade não lida sobrevive, mesmo sendo a mais antiga',
        rows.some(function (row) { return row.id === 'novidade'; }));
    check('quem saiu foi um já lido',
        rows.filter(function (row) { return row.read; }).length === N.MAX_HELD - 1);

    process.stdout.write('Remover apaga, não esconde\n');
    rows = N.remove([notice('a'), notice('b')], 'a');
    check('o aviso removido sai da lista',
        rows.length === 1 && rows[0].id === 'b');
    check('limpar deixa a central vazia', N.clear().length === 0);

    process.stdout.write('Um registro corrompido não derruba a central\n');
    check('lixo gravado é descartado em silêncio',
        N.sanitize([null, 'texto', { id: 'ok', kind: 'REMINDER', title: 'Bom' }, { id: '' }]).length === 1);
    check('ids repetidos no que foi gravado colapsam em um',
        N.sanitize([notice('a'), notice('a')]).length === 1);
    window.close();

    process.stdout.write('O sino aparece no shell e conta o que está guardado\n');
    window = loadApp({
        language: 'pt-BR', languageSelected: true, acceptedLegal: true,
        notifications: [notice('reminder-digest:2026-08-18', { title: 'Você tem títulos esperando' })]
    });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"], .shell'));
    }, 6000);
    if (window.document.querySelector('[data-action="profile-form"]')) {
        window.BuroApp._activate(window.document.querySelector('[data-action="profile-form"]'));
        await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 6000);
        window.document.getElementById('profile-name').value = 'Casa';
        window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    }
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 6000);
    check('o sino está na barra superior',
        Boolean(window.document.querySelector('.topbar-bell')));
    check('o marcador mostra o não lido',
        Boolean(window.document.querySelector('.bell-badge')) &&
        window.document.querySelector('.bell-badge').textContent === '1');
    check('o sino é alcançável pelo D-pad',
        window.document.querySelector('.topbar-bell').classList.contains('focusable'));

    process.stdout.write('Abrir o sino mostra os avisos e permite marcar como lidos\n');
    window.BuroApp._activate(window.document.querySelector('.topbar-bell'));
    await waitFor(function () { return window.BuroApp.state.screen === 'NOTIFICATIONS'; }, 6000);
    check('a lista mostra o aviso guardado',
        window.document.querySelectorAll('.notice-row').length === 1 &&
        window.document.querySelector('.notice-row').textContent.indexOf('esperando') >= 0);
    check('o aviso não lido é marcado como tal',
        window.document.querySelector('.notice-row').classList.contains('unread'));
    check('a tela diz que a TV não avisa com o app fechado',
        window.document.body.textContent.indexOf('aplicativo fechado') >= 0);
    window.BuroApp._activate(window.document.querySelector('[data-action="notifications-read"]'));
    await waitFor(function () {
        return window.BuroNotifications.unreadCount(window.BuroApp.state.preferences.notifications) === 0;
    }, 6000);
    check('marcar todos como lidos apaga o marcador',
        !window.document.querySelector('.bell-badge'));
    check('o estado lido foi gravado nas preferências',
        JSON.parse(window.localStorage.getItem('iptvburo.preferences.v1'))
            .notifications[0].read === true);

    process.stdout.write('Remover um aviso o apaga de verdade\n');
    window.BuroApp._activate(window.document.querySelector('[data-action="notification-remove"]'));
    await waitFor(function () {
        return window.BuroApp.state.preferences.notifications.length === 0;
    }, 6000);
    check('a lista fica vazia', window.BuroApp.state.preferences.notifications.length === 0);
    check('a tela vazia explica o que apareceria ali',
        Boolean(window.document.querySelector('.empty-state')));
    window.close();

    process.stdout.write('Os textos existem nos cinco idiomas\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    check('cada idioma tem os textos do sino',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return ['notificationsTitle', 'notificationsEmpty', 'notificationsEmptyBody',
                'notificationsUnread', 'notificationsMarkAllRead', 'notificationsOpen',
                'notificationKindReminder', 'notificationKindEpisode', 'notificationKindSeason',
                'notificationsBackground'].every(function (key) {
                var value = window.BuroI18n.t(key);
                return Boolean(value) && value !== key;
            });
        }));
    window.BuroI18n.setLanguage('pt-BR');
    window.close();

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exitCode = 1;
});
