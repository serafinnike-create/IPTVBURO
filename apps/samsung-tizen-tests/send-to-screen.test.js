/*
  "Enviar à tela": mandar um título da TV para o celular ou o computador.

  Pedido do usuário com captura da ficha, 2026-08-22: a barra tinha Favoritar,
  Lembrete, Trailer e Compartilhar, e faltava esta.

  Cast não existe no Tizen Web Runtime — não há como abrir um socket à espera.
  Mas o aplicativo do Windows também não transmite fluxo: ele entrega a
  identidade do título e a outra ponta abre da lista dela, "so the other end
  plays from its own list and this machine's credentials stay here". Isso a TV
  pode fazer, pelo mesmo pareamento por código que já traz chaves do celular —
  aqui no sentido inverso.

  O que este teste guarda acima de tudo: o que sai da TV. Uma tela que exporta
  um título não pode exportar junto o endereço do provedor nem a senha.
*/
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

/* Cada chamada ao Worker, para se conferir o que a TV mandou. */
var calls = [];

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
    /* O Worker, respondendo como responde de verdade. */
    window.BuroNetwork.json = function (options, success, failure) {
        var body = options.body ? JSON.parse(options.body) : null;
        calls.push({ url: options.url, body: body });
        if (String(options.url).indexOf('/v1/pair/start') >= 0) {
            window.setTimeout(function () {
                success({ code: '482913', kind: body.kind, expiresInSeconds: 300 });
            }, 0);
        } else if (String(options.url).indexOf('/v1/pair/submit') >= 0) {
            window.setTimeout(function () { success({ ok: true, kind: body.kind }); }, 0);
        } else if (failure) {
            window.setTimeout(function () { failure({ code: 'UNEXPECTED' }); }, 0);
        }
        return { abort: function () {} };
    };
    window.BuroApp.init();
    return window;
}

function activate(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
}

async function reachShell(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000);
    activate(window, '[data-action="profile-form"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 8000);
    window.document.getElementById('profile-name').value = 'Casa';
    activate(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 8000);
}

/* A ficha de um filme aberta, com a fonte tendo credencial guardada. */
function openMovieDetails(window) {
    var source = { id: 's1', name: 'Fonte', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null };
    var item = window.BuroDomain.createItem({
        sourceId: 's1', providerItemId: '9', name: 'Duna',
        categoryId: 'c1', contentType: 'MOVIE', year: 2021, rating: 8,
        logoUrl: 'https://image.tmdb.org/t/p/w342/duna.jpg',
        locator: { kind: 'xtream', contentType: 'MOVIE', providerItemId: '9' }
    });
    item.id = 'item-duna';
    window.BuroApp.state.sources = [source];
    window.BuroApp.state.activeSource = source;
    window.BuroApp.state.screen = 'SHELL';
    window.BuroApp.state.section = 'MOVIES';
    window.BuroApp.state.screenData = {
        kind: 'movie', parent: item,
        details: { title: 'Duna', plot: 'Paul Atreides chega a Arrakis.' }
    };
    return new Promise(function (resolve, reject) {
        window.BuroStorage.secureSave('s1', {
            server: 'https://provider.test:8080', username: 'meuusuario', password: 'minhasenha'
        }, function () { window.BuroApp.render(); resolve(item); }, reject);
    });
}

async function run() {
    var window;
    var submitted;

    process.stdout.write('A ficha oferece enviar o título a outro aparelho\n');
    window = loadApp();
    await reachShell(window);
    await openMovieDetails(window);
    check('a barra de ações tem "Enviar à tela"',
        Boolean(window.document.querySelector('[data-action="send-to-screen"]')));
    check('e ela é alcançável pelo D-pad',
        window.document.querySelector('[data-action="send-to-screen"]')
            .classList.contains('focusable'));
    /* A ordem dos glifos acompanha o Android, com compartilhar por último —
       `reminders-app.test.js` verifica isso, e a nova ação não pode alterá-la. */
    check('compartilhar continua sendo o último glifo',
        (function () {
            var glyphs = Array.prototype.slice.call(
                window.document.querySelectorAll('.detail-action-bar [data-action]')
            ).map(function (node) { return node.getAttribute('data-action'); });
            return glyphs[glyphs.length - 1] === 'share' &&
                glyphs.indexOf('send-to-screen') === glyphs.length - 2;
        }()));

    process.stdout.write('Publicar o título rende um código para o outro aparelho\n');
    calls.length = 0;
    activate(window, '[data-action="send-to-screen"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.pair-code'));
    }, 8000);
    check('a TV pediu um código do tipo certo',
        calls[0].url.indexOf('/v1/pair/start') > 0 && calls[0].body.kind === 'open_title');
    check('e publicou o título sob esse código',
        calls[1].url.indexOf('/v1/pair/submit') > 0 && calls[1].body.code === '482913');
    check('os seis dígitos aparecem na tela',
        window.document.querySelector('.pair-code').textContent === '482913');
    check('com QR, para não ser preciso digitar o endereço',
        Boolean(window.document.querySelector('.pair-qr svg')));
    check('e o endereço por extenso, para quem não lê o QR',
        window.document.querySelector('.pair-url').textContent.indexOf('/parear') > 0);
    /*
      Nada de espera: publicado o código, o lado da TV terminou. Mostrar
      "aguardando" seria anunciar uma espera que não existe.
    */
    check('a tela não finge esperar por algo',
        !window.document.querySelector('.pair-waiting'));

    process.stdout.write('O que sai da TV é o título, e nada mais\n');
    submitted = JSON.stringify(calls[1].body);
    /*
      O ponto do teste. Uma tela que exporta um título não pode exportar junto
      o endereço do provedor, o usuário ou a senha — é o mesmo motivo pelo qual
      o Windows manda a identidade e nunca o fluxo.
    */
    check('o endereço do provedor não viaja',
        submitted.indexOf('provider.test') < 0);
    check('usuário e senha não viajam',
        submitted.indexOf('meuusuario') < 0 && submitted.indexOf('minhasenha') < 0);
    check('nem o id interno do item, que só vale nesta TV',
        submitted.indexOf('item-duna') < 0);
    check('o que viaja identifica o título pelo nome e pelo ano',
        submitted.indexOf('Duna') > 0 && submitted.indexOf('2021') > 0);

    process.stdout.write('O servidor fora do ar não deixa a tela vazia\n');
    window.close();
    calls.length = 0;
    window = loadApp();
    await reachShell(window);
    await openMovieDetails(window);
    window.BuroNetwork.json = function (options, success, failure) {
        window.setTimeout(function () { failure({ code: 'NETWORK', status: 0 }); }, 0);
        return { abort: function () {} };
    };
    activate(window, '[data-action="send-to-screen"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('.empty-state'));
    }, 8000);
    check('a falha vira mensagem, e não uma tela em branco',
        Boolean(window.document.querySelector('.empty-state')));
    check('e o usuário continua com uma saída',
        Boolean(window.document.querySelector('[data-action="back"]')));
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

run().then(function () {
    process.exit(process.exitCode || 0);
}).catch(function (error) {
    process.stdout.write('ERRO: ' + (error && error.stack ? error.stack : error) + '\n');
    process.exit(1);
});
