/*
  Acertar a legenda que dessincronizou.

  Numa lista IPTV isso acontece com frequencia: o provedor remuxa o arquivo e o
  offset embutido deixa de valer. Sem ajuste a unica saida e desligar a legenda.

  O que este teste guarda:

  - o ajuste e cumulativo, porque acertar leva varios toques;
  - o valor fica gravado **por fonte** — quando uma lista dessincroniza e porque
    o provedor trata tudo do mesmo jeito, entao o que acertou um filme acerta o
    resto; global faria duas fontes com problemas diferentes brigarem;
  - ha um caminho de volta ao original, senao um exagero so se corrige contando
    toques ao contrario;
  - o teto existe: um atraso de um minuto nao acerta legenda nenhuma e so
    esconderia o texto.

  Nao ha equivalente para o audio, e isso e deliberado: o AVPlay nao expoe
  atraso de faixa. Um controle que nao funciona e pior do que nenhum.
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

async function run() {
    var window = loadApp();
    var sourceA = { id: 'source-a', name: 'Fonte A', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null };
    var sourceB = { id: 'source-b', name: 'Fonte B', type: 'XTREAM', channelCount: 1, createdAt: 2, updatedAt: null };

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 6000);

    window.BuroApp.state.sources = [sourceA, sourceB];
    window.BuroApp.state.activeSource = sourceA;

    process.stdout.write('O ajuste comeca em zero e acumula\n');
    check('sem ajuste, o atraso e zero',
        window.BuroPlayer.subtitleOffset() === 0);
    window.BuroApp._applySubtitleOffset(500);
    check('meio segundo depois',
        window.BuroPlayer.subtitleOffset() === 500);
    /* Cumulativo: acertar uma legenda leva varios toques, e cada um soma ao
       anterior em vez de substituir. */
    window.BuroApp._applySubtitleOffset(window.BuroPlayer.subtitleOffset() + 500);
    check('outro toque leva a um segundo',
        window.BuroPlayer.subtitleOffset() === 1000);
    window.BuroApp._applySubtitleOffset(window.BuroPlayer.subtitleOffset() - 1500);
    check('e o sentido inverso atravessa o zero',
        window.BuroPlayer.subtitleOffset() === -500);

    process.stdout.write('O exagero tem teto\n');
    /* Um minuto de atraso nao acerta legenda nenhuma: so esconderia o texto ate
       a cena ja ter passado. */
    window.BuroApp._applySubtitleOffset(60000);
    check('nao passa de dez segundos',
        window.BuroPlayer.subtitleOffset() === 10000);
    window.BuroApp._applySubtitleOffset(-60000);
    check('nem de dez segundos para tras',
        window.BuroPlayer.subtitleOffset() === -10000);

    process.stdout.write('O valor fica gravado por fonte\n');
    window.BuroApp._applySubtitleOffset(1500);
    check('a fonte ativa guarda o ajuste',
        window.BuroApp.state.preferences.subtitleOffsets['source-a'] === 1500);

    /*
      A outra fonte comeca limpa. Se o valor fosse global, uma lista que
      dessincroniza empurraria a legenda de outra que estava certa.
    */
    window.BuroApp.state.activeSource = sourceB;
    window.BuroApp._restoreSubtitleOffset();
    check('a outra fonte comeca sem ajuste',
        window.BuroPlayer.subtitleOffset() === 0);

    window.BuroApp.state.activeSource = sourceA;
    window.BuroApp._restoreSubtitleOffset();
    check('e voltar a primeira traz o ajuste dela de volta',
        window.BuroPlayer.subtitleOffset() === 1500);

    process.stdout.write('Ha caminho de volta ao original\n');
    /* Sem isto, desfazer um exagero exigiria contar toques ao contrario. */
    window.BuroApp._applySubtitleOffset(0);
    check('zerar limpa o ajuste',
        window.BuroPlayer.subtitleOffset() === 0);
    check('e apaga o valor gravado, em vez de guardar um zero',
        window.BuroApp.state.preferences.subtitleOffsets['source-a'] === undefined);

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
    process.stderr.write('Falha na suite de sincronia de legenda: ' + error.stack + '\n');
    process.exit(1);
});
