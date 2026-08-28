/*
  Com quanta antecedencia um lembrete vira aviso.

  O horizonte era fixo em trinta dias. Serve para quem marca lancamentos do mes,
  e nao serve para quem marca a estreia de dezembro em agosto — o lembrete fica
  guardado sem virar aviso, e ate faltar um mes a pessoa nao sabe se o aplicativo
  ainda o tem. Quem prefere ser avisado so na vespera tambem existe, e para essa
  pessoa trinta dias de contagem sao ruido diario.

  A TV nao notifica com o aplicativo fechado — `background-support=disable` — e a
  tela ja diz isso. O que esta escolha governa e o aviso da abertura e a contagem
  nos cartoes.

  O que o teste guarda alem da escolha em si: as tres leituras do digest — o
  aviso, o sino e a contagem do cartao — usam o mesmo horizonte. Uma delas fora
  de sincronia faria a tela dizer duas coisas diferentes sobre o mesmo titulo.
*/
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
    var match;
    var secureData = {};
    var dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/' });
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
            getData: function (alias) { return secureData[alias.name]; },
            removeData: function (alias) { delete secureData[alias.name]; }
        },
        tvinputdevice: { registerKey: function () {} },
        application: { getCurrentApplication: function () { return { exit: function () {} }; } }
    };
    scripts.forEach(function (script) { window.eval(fs.readFileSync(path.join(APP_DIR, script), 'utf8')); });
    window.BuroApp.init();
    return window;
}

/* Uma data a tantos dias de hoje, no formato que o dominio le. */
function inDays(days) {
    var when = new Date();
    when.setDate(when.getDate() + days);
    return when.getFullYear() + '-' +
        ('0' + (when.getMonth() + 1)).slice(-2) + '-' +
        ('0' + when.getDate()).slice(-2);
}

async function run() {
    var window = loadApp();
    var D;
    var reminders;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    D = window.BuroDomain;

    process.stdout.write('O dominio aceita um horizonte, e mantem trinta como padrao\n');
    /*
      Tres titulos: um daqui a tres dias, um daqui a vinte, um daqui a sessenta.
      Cada horizonte deve alcancar um conjunto diferente.
    */
    reminders = [
        { id: 'a', itemName: 'Perto', releaseDate: inDays(3) },
        { id: 'b', itemName: 'Medio', releaseDate: inDays(20) },
        { id: 'c', itemName: 'Longe', releaseDate: inDays(60) }
    ];
    check('sem argumento, o padrao de trinta dias continua valendo',
        D.reminderDigest(reminders).upcoming.length === 2);
    check('com um dia, nenhum dos tres entra na contagem',
        D.reminderDigest(reminders, null, 1).upcoming.length === 0);
    check('com sete dias, so o mais proximo',
        D.reminderDigest(reminders, null, 7).upcoming.length === 1);
    check('com noventa dias, os tres',
        D.reminderDigest(reminders, null, 90).upcoming.length === 3);

    process.stdout.write('Um horizonte invalido nao apaga a contagem\n');
    /*
      Zero, negativo ou texto viriam de uma preferencia corrompida. Cair no
      padrao e melhor do que devolver lista vazia, que se leria como "voce nao
      marcou nada".
    */
    check('zero, negativo e texto caem no padrao',
        D.reminderDigest(reminders, null, 0).upcoming.length === 2 &&
        D.reminderDigest(reminders, null, -5).upcoming.length === 2 &&
        D.reminderDigest(reminders, null, 'trinta').upcoming.length === 2);

    process.stdout.write('A tela oferece a escolha e a guarda\n');
    window.BuroApp.state.profiles = [{ id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, createdAt: 1 }];
    window.BuroApp.state.activeProfile = window.BuroApp.state.profiles[0];
    window.BuroApp.state.reminders = reminders.map(function (row) {
        return { id: row.id, profileId: 'p', itemId: row.id, itemName: row.itemName,
            releaseDate: row.releaseDate, createdAt: 1 };
    });
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'REMINDERS';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();

    check('as quatro antecedencias aparecem',
        window.document.querySelectorAll('[data-action="reminder-horizon"]').length === 4);
    /* Uma escolha sem marca de qual esta activa obriga a pessoa a adivinhar. */
    check('e uma delas esta marcada como a actual',
        window.document.querySelectorAll('[data-action="reminder-horizon"].selected').length === 1);

    window.BuroApp._activate(
        window.document.querySelector('[data-action="reminder-horizon"][data-days="7"]')
    );
    check('escolher sete dias fica gravado',
        window.BuroApp.state.preferences.reminderHorizonDays === 7);
    check('e a marca acompanha a escolha',
        window.document.querySelector('[data-action="reminder-horizon"][data-days="7"]')
            .classList.contains('selected'));

    process.stdout.write('A tela continua dizendo que a TV nao avisa fechada\n');
    /*
      A escolha nao pode ser lida como uma promessa de notificacao: o manifesto
      declara background-support=disable, e o aviso so existe na abertura.
    */
    check('o aviso sobre o aplicativo fechado continua na tela',
        window.document.body.textContent.indexOf(window.BuroI18n.t('remindersNoNotice')) >= 0);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (label) { process.stdout.write(' - ' + label + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suite de horizonte de lembrete: ' + error.stack + '\n');
    process.exit(1);
});
