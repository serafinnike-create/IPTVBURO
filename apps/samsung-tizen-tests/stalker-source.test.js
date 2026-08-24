/* Ligação do portal Stalker/Ministra à interface. Nenhum dado real de provedor. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var fakeIndexedDb = require('fake-indexeddb');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
/* A ordem vem do index.html, para a suíte não quebrar quando um módulo novo
   entra no app. Ver platform-failures.test.js. */
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
    window.__secureData = secureData;
    return window;
}

/* O app age por _activate, o mesmo caminho do ENTER no controle remoto. */
function click(window, selector) {
    var element = window.document.querySelector(selector);
    if (!element) { throw new Error('elemento ausente: ' + selector); }
    window.BuroApp._activate(element);
    return element;
}

function typeInto(window, selector, value) {
    var input = window.document.querySelector(selector);
    if (!input) { throw new Error('campo ausente: ' + selector); }
    input.value = value;
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    return input;
}

/*
  Um portal sintético. Responde ao handshake, ao estado da conta, às categorias
  e ao get_ordered_list, e guarda tudo o que foi pedido para que o teste possa
  afirmar o que saiu da TV — incluindo o que não deve sair.
*/
function portal(window, overrides) {
    var calls = [];
    var options = overrides || {};
    window.BuroNetwork.json = function (request, success, failure) {
        var url = String(request.url || '');
        calls.push(request);
        function answer(payload) { setTimeout(function () { success(payload); }, 0); }
        if (options.offline) {
            setTimeout(function () { failure({ code: 'NETWORK_ERROR' }); }, 0);
            return { abort: function () {} };
        }
        if (options.rejectMac) {
            setTimeout(function () { failure({ code: 'AUTH_REJECTED', status: 403 }); }, 0);
            return { abort: function () {} };
        }
        if (url.indexOf('action=handshake') >= 0) {
            answer({ js: { token: options.token || 'synthetic-token' } });
        } else if (url.indexOf('action=get_main_info') >= 0) {
            answer({ js: options.account || { phone: '', status: 0 } });
        } else if (url.indexOf('action=get_genres') >= 0) {
            answer({ js: [{ id: '*', title: 'Todos' }, { id: '5', title: 'Notícias' }] });
        } else if (url.indexOf('action=get_categories') >= 0) {
            answer({ js: [{ id: '7', title: 'Ação' }] });
        } else if (url.indexOf('action=get_ordered_list') >= 0) {
            if (options.totalItems) {
                (function () {
                    var match = /[?&]p=(\d+)/.exec(url);
                    var page = Math.max(1, Number(match && match[1]) || 1);
                    var pageSize = 200;
                    var start = (page - 1) * pageSize;
                    var end = Math.min(Number(options.totalItems), start + pageSize);
                    var rows = [];
                    var index;
                    for (index = start; index < end; index += 1) {
                        rows.push({
                            id: String(1000 + index), name: 'Canal Sintético ' + index,
                            cmd: 'ffmpeg http://portal.synthetic.invalid/stream/' + index + '?token=segredo',
                            screenshot_uri: 'http://portal.synthetic.invalid/art/' + index + '.png'
                        });
                    }
                    answer({ js: { total_items: Number(options.totalItems), max_page_items: pageSize, data: rows } });
                }());
                return { abort: function () {} };
            }
            answer({
                js: {
                    total_items: 1,
                    data: [{
                        id: '900', name: 'Canal Sintético', year: '2024', rating_imdb: '7.5',
                        cmd: 'ffmpeg http://portal.synthetic.invalid/stream/900?token=segredo-de-uso-unico',
                        screenshot_uri: 'http://portal.synthetic.invalid/art/900.png'
                    }]
                }
            });
        } else if (url.indexOf('action=create_link') >= 0) {
            answer({ js: { cmd: 'ffmpeg http://portal.synthetic.invalid/play/900?token=uso-unico' } });
        } else {
            answer({ js: {} });
        }
        return { abort: function () {} };
    };
    return calls;
}

async function reachSourceForm(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);
    click(window, '[data-action="legal-accept"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 6000);
    click(window, '[data-action="profile-form"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('#profile-name'));
    }, 6000);
    window.document.getElementById('profile-name').value = 'Casa';
    click(window, '[data-action="profile-save"]');
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 6000);
    click(window, '.nav-list [data-action="section"][data-section="SOURCES"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="source-add"]'));
    }, 6000);
    click(window, '[data-action="source-add"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-type="STALKER"]'));
    }, 6000);
}

async function run() {
    var window;
    var calls;
    var persisted;
    var hint;
    var sessionTokens;
    var category;
    var storedCategory;
    var pageCalls;

    process.stdout.write('O portal aparece como fonte disponível\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await reachSourceForm(window);
    check('Stalker/Ministra é oferecido junto com Xtream e M3U',
        Boolean(window.document.querySelector('[data-action="source-form"][data-type="STALKER"]')));
    check('o cartão do portal deixou de ser um botão morto',
        !window.document.querySelector('.choice-card[data-action="unavailable"]'));

    click(window, '[data-action="source-form"][data-type="STALKER"]');
    await waitFor(function () {
        return Boolean(window.document.querySelector('#source-mac'));
    }, 4000);

    process.stdout.write('O formulário pede o que o portal precisa\n');
    check('o endereço do portal e o MAC têm campos próprios',
        Boolean(window.document.querySelector('#source-portal')) &&
        Boolean(window.document.querySelector('#source-mac')));
    check('usuário e senha aparecem marcados como opcionais',
        Boolean(window.document.querySelector('.form-optional')) &&
        Boolean(window.document.querySelector('#source-username')) &&
        Boolean(window.document.querySelector('#source-password')));
    check('o aviso de privacidade do MAC está visível antes de conectar',
        Boolean(window.document.querySelector('.form-note.privacy')));

    process.stdout.write('O MAC é conferido enquanto se digita\n');
    typeInto(window, '#source-mac', '00-1a-79-ab-cd-ef');
    hint = window.document.querySelector('#source-mac-hint');
    check('um MAC com separadores livres é reconhecido e mostrado normalizado',
        hint.className.indexOf('ok') >= 0 && hint.textContent.indexOf('00:1A:79:AB:CD:EF') >= 0);
    typeInto(window, '#source-mac', 'nao-e-um-mac');
    check('um MAC inválido é apontado antes de qualquer ida à rede',
        window.document.querySelector('#source-mac-hint').className.indexOf('error') >= 0);

    process.stdout.write('O aviso de HTTP aparece antes de conectar\n');
    typeInto(window, '#source-portal', 'https://portal.synthetic.invalid/c/');
    check('um portal HTTPS não dispara aviso',
        window.document.querySelector('#source-http-warning').hidden === true);
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    check('um portal HTTP aberto avisa o usuário',
        window.document.querySelector('#source-http-warning').hidden === false);

    process.stdout.write('Conectar importa o catálogo do portal\n');
    calls = portal(window);
    typeInto(window, '#source-name', 'Portal de teste');
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    typeInto(window, '#source-mac', '00:1A:79:AB:CD:EF');
    click(window, '[data-action="source-connect"][data-type="STALKER"]');
    await waitFor(function () {
        return window.BuroApp.state.sources.some(function (source) {
            return source.type === 'STALKER';
        });
    }, 6000);
    check('a fonte do portal foi gravada',
        window.BuroApp.state.sources.filter(function (source) {
            return source.type === 'STALKER';
        }).length === 1);
    check('o handshake aconteceu antes do catálogo',
        calls.length > 0 && String(calls[0].url).indexOf('action=handshake') >= 0);
    check('o estado da conta foi conferido antes de importar',
        calls.some(function (request) { return String(request.url).indexOf('action=get_main_info') >= 0; }));
    check('as três verticais foram importadas',
        ['LIVE', 'MOVIE', 'SERIES'].every(function (contentType) {
            return window.BuroApp.state.categories.some(function (category) {
                return category.contentType === contentType;
            });
        }));
    check('a pseudo-categoria "todos" do portal não virou categoria',
        !window.BuroApp.state.categories.some(function (category) {
            return category.providerCategoryId === '*';
        }));
    check('a identidade MAG só existe em memória, no cabeçalho da chamada',
        calls.every(function (request) {
            return String(request.clientUserAgent || '').indexOf('MAG') >= 0 ||
                String(request.url).indexOf('handshake') < 0;
        }));

    process.stdout.write('O segredo do portal não vaza para o catálogo\n');
    persisted = JSON.stringify(window.BuroApp.state);
    check('o MAC não aparece no estado do catálogo',
        persisted.indexOf('00:1A:79:AB:CD:EF') === -1 && persisted.indexOf('001A79ABCDEF') === -1);
    check('o token da sessão não aparece no estado do catálogo',
        persisted.indexOf('synthetic-token') === -1);
    check('o MAC fica no cofre de segredos, não no armazenamento comum',
        JSON.stringify(window.__secureData).indexOf('00:1A:79:AB:CD:EF') >= 0);

    process.stdout.write('Abrir a categoria busca itens sob demanda\n');
    (function () {
        var state = window.BuroApp.state;
        var category = state.categories.filter(function (row) { return row.contentType === 'LIVE'; })[0];
        window.BuroApp._openCategory(category.id);
    }());
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'category' && (data.items || []).length > 0;
    }, 6000);
    check('a categoria do portal traz os itens quando é aberta',
        window.BuroApp.state.screenData.items[0].name === 'Canal Sintético');
    check('o comando de reprodução não é gravado junto com o item',
        JSON.stringify(window.BuroApp.state.screenData.items).indexOf('ffmpeg') === -1);
    check('a arte privada do portal não é persistida',
        JSON.stringify(window.BuroApp.state.screenData.items).indexOf('portal.synthetic.invalid') === -1);

    process.stdout.write('A sessão é reaproveitada e refeita quando vence\n');
    sessionTokens = calls.filter(function (request) {
        return String(request.url).indexOf('action=handshake') >= 0;
    }).length;
    check('abrir a categoria não refez o handshake com a sessão ainda válida',
        sessionTokens === 1);
    window.close();

    process.stdout.write('A categoria percorre todas as páginas remotas sem reter comandos\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await reachSourceForm(window);
    click(window, '[data-action="source-form"][data-type="STALKER"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#source-mac')); }, 4000);
    calls = portal(window, { totalItems: 450 });
    typeInto(window, '#source-name', 'Portal paginado');
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    typeInto(window, '#source-mac', '00:1A:79:AB:CD:EF');
    click(window, '[data-action="source-connect"][data-type="STALKER"]');
    await waitFor(function () {
        return window.BuroApp.state.sources.some(function (source) { return source.type === 'STALKER'; });
    }, 6000);
    category = window.BuroApp.state.categories.filter(function (row) { return row.contentType === 'LIVE'; })[0];
    click(window, '.nav-list [data-action="section"][data-section="LIVE"]');
    window.BuroApp._openCategory(category.id);
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'category' && data.items.length === 200 && data.catalogueRemoteHasMore;
    }, 6000);
    check('a primeira página mostra 200 de 450 e oferece continuação remota',
        window.BuroApp.state.screenData.catalogueTotalCount === 450 &&
        Boolean(window.document.querySelector('[data-action="category-load-more"]')));
    click(window, '[data-action="category-load-more"]');
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'category' && data.items.length === 400 && !data.catalogueLoadingMore;
    }, 6000);
    click(window, '[data-action="category-load-more"]');
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'category' && data.items.length === 450 && !data.catalogueLoadingMore;
    }, 6000);
    pageCalls = calls.filter(function (request) {
        return String(request.url).indexOf('action=get_ordered_list') >= 0;
    }).map(function (request) {
        var match = /[?&]p=(\d+)/.exec(String(request.url));
        return Number(match && match[1]);
    });
    check('a TV pediu exatamente as páginas 1, 2 e 3 ao portal', pageCalls.join(',') === '1,2,3');
    check('as três páginas formam 450 identidades únicas',
        new Set(window.BuroApp.state.screenData.items.map(function (item) { return item.id; })).size === 450);
    check('o botão desaparece quando o total remoto termina',
        !window.BuroApp.state.screenData.catalogueRemoteHasMore &&
        !window.document.querySelector('[data-action="category-load-more"]'));
    check('a grade materializa no máximo uma página visual de cards',
        window.document.querySelectorAll('.media-card').length <= 200);
    check('nenhum comando, token de stream ou arte privada entrou no estado',
        !/ffmpeg|segredo|portal\.synthetic\.invalid\/art/.test(JSON.stringify(window.BuroApp.state)));
    storedCategory = await new Promise(function (resolve, reject) {
        window.BuroStorage.get('categories', category.id, resolve, reject);
    });
    check('a página remota concluída fica registrada para a próxima abertura',
        storedCategory.stalkerLoadedPage === 3 && storedCategory.stalkerTotalItems === 450 &&
        storedCategory.stalkerPageSize === 200);
    window.BuroApp._openCategory(category.id);
    await waitFor(function () {
        var data = window.BuroApp.state.screenData;
        return data && data.kind === 'category' && data.items.length === 200;
    }, 6000);
    check('reabrir usa o catálogo local e não repete a primeira página remota',
        calls.filter(function (request) {
            return String(request.url).indexOf('action=get_ordered_list') >= 0;
        }).length === 3 && !window.BuroApp.state.screenData.catalogueRemoteHasMore);
    window.close();

    process.stdout.write('Cada falha do portal tem a sua própria mensagem\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await reachSourceForm(window);
    click(window, '[data-action="source-form"][data-type="STALKER"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#source-mac')); }, 4000);
    portal(window, { offline: true });
    typeInto(window, '#source-name', 'Portal fora do ar');
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    typeInto(window, '#source-mac', '00:1A:79:AB:CD:EF');
    click(window, '[data-action="source-connect"][data-type="STALKER"]');
    await waitFor(function () {
        var message = window.document.querySelector('#source-form-message');
        return message && message.className.indexOf('error') >= 0;
    }, 6000);
    check('um portal inalcançável fala de rede, não de MAC recusado',
        window.document.querySelector('#source-form-message').textContent ===
            window.BuroI18n.t('stalkerErrorNetwork'));
    check('nenhuma fonte foi gravada quando o portal não respondeu',
        window.BuroApp.state.sources.length === 0);
    window.close();

    process.stdout.write('Um MAC recusado pelo portal e dito com todas as letras\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await reachSourceForm(window);
    click(window, '[data-action="source-form"][data-type="STALKER"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#source-mac')); }, 6000);
    portal(window, { rejectMac: true });
    typeInto(window, '#source-name', 'Portal que recusa');
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    typeInto(window, '#source-mac', '00:1A:79:AB:CD:EF');
    click(window, '[data-action="source-connect"][data-type="STALKER"]');
    await waitFor(function () {
        var message = window.document.querySelector('#source-form-message');
        return message && message.className.indexOf('error') >= 0;
    }, 6000);
    check('um MAC nao registrado manda falar com o provedor, nao conferir a internet',
        window.document.querySelector('#source-form-message').textContent ===
            window.BuroI18n.t('stalkerErrorUnauthorised'));
    window.close();

    process.stdout.write('Uma assinatura bloqueada é detectada na importação\n');
    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await reachSourceForm(window);
    click(window, '[data-action="source-form"][data-type="STALKER"]');
    await waitFor(function () { return Boolean(window.document.querySelector('#source-mac')); }, 4000);
    portal(window, { account: { status: 2, blocked: '1' } });
    typeInto(window, '#source-name', 'Portal bloqueado');
    typeInto(window, '#source-portal', 'http://portal.synthetic.invalid/c/');
    typeInto(window, '#source-mac', '00:1A:79:AB:CD:EF');
    click(window, '[data-action="source-connect"][data-type="STALKER"]');
    await waitFor(function () {
        var message = window.document.querySelector('#source-form-message');
        return message && message.className.indexOf('error') >= 0;
    }, 6000);
    check('uma assinatura bloqueada é dita como bloqueada',
        window.document.querySelector('#source-form-message').textContent ===
            window.BuroI18n.t('stalkerErrorBlocked'));
    check('um catálogo não é importado de uma assinatura bloqueada',
        window.BuroApp.state.sources.length === 0 &&
        window.BuroApp.state.categories.length === 0);
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
