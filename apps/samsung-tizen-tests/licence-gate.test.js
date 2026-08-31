/*
  Uma assinatura vencida bloqueia o aplicativo.

  A TV mostrava "Assinatura vencida" numa etiqueta no topo e continuava servindo
  filmes e series — a etiqueta informava sem impedir nada. Quem nao pagou
  assistia igual.

  O aplicativo do Windows resolve isto antes de compor qualquer coisa, para que
  "um aplicativo bloqueado nunca componha um catalogo que nao tem direito de
  mostrar". Aqui a guarda vive no comeco do `render`, que e o equivalente: todo
  caminho que leve a desenhar passa por ela.

  O que este teste guarda alem do bloqueio: **o que continua alcancavel**. Uma
  guarda que prende a pessoa sem saida e um defeito diferente, nao uma correcao.
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

/* A licenca responde o que o teste quiser, sem servidor nem relogio. */
function setLicence(window, allowed, reason) {
    window.BuroLicense.decide = function () {
        return allowed ? { allowed: true, trial: false } : { allowed: false, reason: reason };
    };
}

async function run() {
    var window = loadApp();
    var source = { id: 's', name: 'Fonte', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null };
    var category = { id: 'c', sourceId: 's', providerCategoryId: 'm', name: 'Filmes',
        contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var movie;
    var played = 0;

    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);

    movie = window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'm1', name: 'Filme de teste', categoryId: 'c',
        contentType: 'MOVIE', sortOrder: 0, year: 2024,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm1' }
    });
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], [movie], true]);

    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = [movie];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroPlayer.play = function () { played += 1; return true; };

    process.stdout.write('Com licenca valida, o catalogo aparece\n');
    setLicence(window, true);
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('a aba de filmes abre normalmente',
        window.BuroApp.state.screen === 'SHELL');

    process.stdout.write('Vencida, o catalogo some e a ativacao toma a tela\n');
    /*
      O defeito que o usuario viu: a etiqueta dizia "Assinatura vencida" e a
      ficha de uma serie continuava ali, com o botao de assistir.
    */
    setLicence(window, false, 'EXPIRED');
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('a tela vira a de licenca',
        window.BuroApp.state.screen === 'LICENCE');
    check('e o catalogo nao esta desenhado por tras',
        !window.document.querySelector('.media-card'));

    process.stdout.write('A tela de bloqueio da os dois caminhos de saida\n');
    /*
      Pagar ou receber uma chave — foi o que o usuario pediu, e as duas coisas
      precisam estar na tela: o QR leva a compra, o campo aceita a chave que o
      admin gera no site.
    */
    check('mostra o QR de compra',
        Boolean(window.document.querySelector('.licence-qr svg')));
    check('e o campo para digitar uma chave',
        Boolean(window.document.getElementById('licence-key')));
    /* O codigo do aparelho e o que o usuario dita por telefone para o admin
       liberar do painel. */
    check('e o codigo deste aparelho',
        Boolean(window.document.querySelector('.licence-device strong')));

    process.stdout.write('Nem por atalho se chega ao conteudo\n');
    /*
      A navegacao por controle remoto alcanca secoes sem passar por um clique.
      Trocar `state.section` a mao e o equivalente mais direto disso.
    */
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'LIVE';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('ir para Ao Vivo tambem cai no bloqueio',
        window.BuroApp.state.screen === 'LICENCE');

    /*
      E nao se reproduz por tecla de midia. Este caminho nao passa por um
      desenho antes de tocar, entao a guarda do `render` sozinha nao o cobriria.
    */
    played = 0;
    window.BuroApp._playItem(movie.id);
    await new Promise(function (resolve) { setTimeout(resolve, 120); });
    check('e nenhuma reproducao comeca',
        played === 0);

    process.stdout.write('O que vem antes da licenca continua alcancavel\n');
    /*
      Uma guarda que prende a pessoa sem saida e um defeito diferente. Os
      diagnosticos ficam de fora de proposito: quando a licenca nao valida por
      falta de rede, e ali que se descobre por que.
    */
    window.BuroApp.state.screen = 'DIAGNOSTICS';
    window.BuroApp.state.screenData = {};
    window.BuroApp.render();
    check('os diagnosticos continuam abrindo',
        window.BuroApp.state.screen === 'DIAGNOSTICS');

    process.stdout.write('Nem toda recusa e culpa do cliente\n');
    /*
      `decide()` devolve `allowed: false` por seis razoes muito diferentes, e a
      primeira versao desta guarda tratou todas igual — o que trancou dezenas de
      suites que nunca registram aparelho. Tres delas nao sao o servidor dizendo
      que alguem deixou de pagar:

      - um build sem a chave publica do servidor e um problema de empacotamento;
      - um aparelho ainda nao registrado esta no meio do registro, que e
        assincrono e silencioso na abertura — bloquear ali trancaria a primeira
        execucao antes de a resposta chegar, e para sempre sem rede;
      - uma licenca que nao pode ser reconferida agora quase sempre e falta de
        internet, e quem pagou nao deve perder o que tem por causa disso.
    */
    ['UNAVAILABLE', 'UNREGISTERED', 'NEEDS_VERIFICATION'].forEach(function (reason) {
        setLicence(window, false, reason);
        window.BuroApp.state.screen = 'SHELL';
        window.BuroApp.state.section = 'MOVIES';
        window.BuroApp.state.screenData = null;
        window.BuroApp.render();
        check(reason + ' nao tranca o aplicativo',
            window.BuroApp.state.screen === 'SHELL');
    });

    /* E as tres em que o servidor de facto disse nao trancam. */
    ['TRIAL_ENDED', 'EXPIRED', 'REVOKED'].forEach(function (reason) {
        setLicence(window, false, reason);
        window.BuroApp.state.screen = 'SHELL';
        window.BuroApp.state.section = 'MOVIES';
        window.BuroApp.state.screenData = null;
        window.BuroApp.render();
        check(reason + ' tranca',
            window.BuroApp.state.screen === 'LICENCE');
    });

    process.stdout.write('Pagar devolve o catalogo\n');
    setLicence(window, true);
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = null;
    window.BuroApp.render();
    check('com a licenca de volta, o catalogo volta',
        window.BuroApp.state.screen === 'SHELL');

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
    process.stderr.write('Falha na suite de bloqueio: ' + error.stack + '\n');
    process.exit(1);
});
