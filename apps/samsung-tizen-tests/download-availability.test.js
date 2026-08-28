/*
  Baixar um filme: o botao, quando ele existe, e o que falta quando nao existe.

  O botao exige pendrive ou HD montado, e isso nao e capricho — o armazenamento
  interno do aplicativo Tizen nao comporta video, entao a unica gravacao possivel
  e num volume removivel (ADR-008).

  O que estava errado era o silencio. Sem USB o botao simplesmente sumia da
  ficha, e quem procurava concluia que o aplicativo nao baixa — ou que esta
  quebrado, ja que o Windows e o celular baixam.

  Este teste guarda os tres estados: com USB o botao aparece e baixa; sem USB a
  ficha diz o que ligar; e num canal ao vivo continua calada, porque ali nem um
  pendrive ajudaria.
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

/*
  `usb` decide o que a TV imaginaria tem montado. `downloads` decide se a API de
  download existe — as duas coisas sao independentes, e a ficha precisa
  distinguir uma da outra.
*/
function loadApp(options) {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var scripts = [];
    var match;
    var secureData = {};
    var started = [];
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
    if (options.downloadApi) {
        window.tizen.download = {
            start: function (request) { started.push(request); return started.length; },
            cancel: function () {},
            setListener: function () {}
        };
        window.tizen.DownloadRequest = function (url, destination, fileName) {
            this.url = url; this.destination = destination; this.fileName = fileName;
        };
        window.tizen.systeminfo = { getCapability: function () { return true; } };
        window.tizen.filesystem = {
            listStorages: function (onList) { onList(options.storages || []); },
            addStorageStateChangeListener: function () { return 1; },
            resolve: function (uri, onOk) {
                onOk({ toURI: function () { return 'file:///' + uri; },
                    resolve: function () { return null; },
                    createDirectory: function () { return { toURI: function () { return 'file:///x'; } }; } });
            }
        };
    }
    scripts.forEach(function (script) { window.eval(fs.readFileSync(path.join(APP_DIR, script), 'utf8')); });
    window.BuroApp.init();
    window._started = started;
    return window;
}

var mountedUsb = [{ label: 'removable_usb1', type: 'EXTERNAL', state: 'MOUNTED' }];

async function openMovie(window) {
    var source = { id: 's', name: 'Fonte', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null,
        serverUrl: 'https://provider.test', username: 'u', password: 'p' };
    var category = { id: 'c', sourceId: 's', providerCategoryId: 'm', name: 'Filmes',
        contentType: 'MOVIE', sortOrder: 0 };
    var profile = { id: 'p', name: 'Casa', avatarKey: 'gold', isKids: false, sourceId: 's', createdAt: 1 };
    var movie = window.BuroDomain.createItem({
        sourceId: 's', providerItemId: 'm1', name: 'Filme de teste', categoryId: 'c',
        contentType: 'MOVIE', sortOrder: 0, year: 2024,
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: 'm1', extension: 'mp4' }
    });
    await call(window, window.BuroStorage.replaceSourceCatalogue, [source, [category], [movie], true]);
    /*
      As credenciais vivem no KeyManager, e nao no objeto da fonte: a URL de
      download e montada com elas no instante do pedido e some em seguida — a
      mesma resolucao tardia da reproducao. Sem isto, `startDownloadItem` falha
      com SOURCE_UNRESOLVED, que e o comportamento correto para uma fonte sem
      credencial gravada.
    */
    await new Promise(function (resolve, reject) {
        /* `server`, e nao `serverUrl`: e o nome que `resolvePlayback` le. Escrevi
           o outro primeiro e a URL saiu "undefined/movie/..." — o teste estava
           errado, nao o aplicativo. */
        window.BuroStorage.secureSave('s', {
            server: 'https://provider.test', username: 'u', password: 'p'
        }, resolve, reject);
    });
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.categories = [category];
    window.BuroApp.state.items = [movie];
    window.BuroApp.state.profiles = [profile];
    window.BuroApp.state.activeProfile = profile;
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = {
        kind: 'movie', parent: movie, details: { title: movie.name, plot: 'Sinopse' }
    };
    window.BuroApp.render();
    return movie;
}

function bodyText(window) {
    return window.document.body.textContent.replace(/\s+/g, ' ');
}

async function run() {
    var window;

    process.stdout.write('Sem pendrive, a ficha diz o que ligar\n');
    /*
      O defeito que o usuario viu: a barra de acoes tinha Favoritado, Remover,
      Enviar a tela e Compartilhar — e nenhum download, sem explicacao.
    */
    window = loadApp({ downloadApi: true, storages: [] });
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openMovie(window);
    check('o botao de baixar nao aparece sem volume montado',
        !window.document.querySelector('[data-action="download"]'));
    check('mas a ficha explica que falta um pendrive',
        bodyText(window).indexOf(window.BuroI18n.t('downloadNeedsUsb')) >= 0);
    window.close();

    process.stdout.write('Numa TV sem a API, a mensagem e outra\n');
    /*
      Distinguir os dois importa: sem pendrive a pessoa resolve ligando um; sem
      a API, ligar um cabo nao mudaria nada, e manda-la procurar um seria
      desperdicar o tempo dela.
    */
    window = loadApp({ downloadApi: false });
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openMovie(window);
    check('diz que a TV nao permite, e nao que falta pendrive',
        bodyText(window).indexOf(window.BuroI18n.t('downloadUnsupported')) >= 0 &&
        bodyText(window).indexOf(window.BuroI18n.t('downloadNeedsUsb')) < 0);
    window.close();

    process.stdout.write('Com pendrive, o botao aparece e baixa\n');
    window = loadApp({ downloadApi: true, storages: mountedUsb });
    await waitFor(function () {
        return window.BuroApp.state.ready && window.BuroApp.state.screen !== 'BOOT';
    }, 15000);
    await openMovie(window);
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="download"]'));
    }, 6000);
    check('o botao de baixar aparece',
        Boolean(window.document.querySelector('[data-action="download"]')));
    /* E a dica some: ela existe para explicar a ausencia, nao para acompanhar
       o botao. */
    check('e a explicacao some, porque nao ha mais o que explicar',
        bodyText(window).indexOf(window.BuroI18n.t('downloadNeedsUsb')) < 0);

    window.BuroApp._activate(window.document.querySelector('[data-action="download"]'));
    await waitFor(function () { return window._started.length > 0; }, 6000);
    check('acionar o botao pede o download ao sistema',
        window._started.length === 1);
    /*
      A URL do provedor e produzida no instante do download e nao e guardada —
      a mesma resolucao tardia da reproducao. Aqui so se verifica que ela chegou
      montada ao pedido, e que o destino e o volume removivel.
    */
    check('com a URL resolvida e o destino no volume removivel',
        String(window._started[0].url).indexOf('provider.test') >= 0 &&
        String(window._started[0].destination).length > 0);
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
    process.stderr.write('Falha na suite de download: ' + error.stack + '\n');
    process.exit(1);
});
