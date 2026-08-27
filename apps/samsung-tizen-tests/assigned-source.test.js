/*
  A lista que o vendedor configurou no painel, aplicada na abertura.

  Quem vende IPTV vende para gente que não consegue cadastrar um servidor Xtream
  no controle remoto: três campos, um deles uma senha. O cliente lê o código do
  aparelho na tela de Licença e manda por mensagem; quem vendeu preenche no
  painel; na próxima abertura a lista está lá.

  O que este teste guarda acima de tudo: **a escolha de quem já configurou a
  própria lista não pode ser trocada por baixo**. Um aplicativo que substitui a
  fonte de alguém sozinho é pior do que um aplicativo que não provisiona nada.
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

var ASSIGNED = {
    server: 'http://meuprovedor.com:8080',
    username: 'cliente123',
    password: 'senha-do-cliente'
};

function loadApp(options) {
    var settings = options || {};
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
    /* Nada sai desta máquina: o app só fala com o Worker pelo que se simula aqui. */
    window.__confirmations = [];
    window.BuroLicense.fetchAssignedSource = function (done, failed) {
        window.__fetched = (window.__fetched || 0) + 1;
        if (settings.fetchFails) { failed({ code: 'NETWORK' }); return; }
        done(settings.assigned === undefined ? ASSIGNED : settings.assigned);
    };
    window.BuroLicense.confirmAssignedSource = function (errorCode) {
        window.__confirmations.push(errorCode || null);
    };
    window.BuroXtream.authenticate = function (secret, success, failure) {
        window.__authenticated = secret;
        if (settings.authFails) { failure({ code: 'AUTH_REJECTED' }); return; }
        success({ username: secret.username, status: 'Active' });
    };
    window.BuroXtream.loadCategories = function (secret, contentType, success) {
        success([{
            id: 'cat-' + contentType, providerCategoryId: '1',
            name: 'Filmes | Ação', contentType: contentType, sortOrder: 0
        }]);
    };
    window.BuroXtream.loadItems = function (secret, sourceId, contentType, category, success) {
        success([], {});
    };
    window.BuroApp.init();
    return window;
}

async function reachShell(window) {
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="profile-form"]'));
    }, 8000);
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-form"]'));
    await waitFor(function () { return Boolean(window.document.querySelector('#profile-name')); }, 8000);
    window.document.getElementById('profile-name').value = 'Casa';
    window.BuroApp._activate(window.document.querySelector('[data-action="profile-save"]'));
    await waitFor(function () { return Boolean(window.document.querySelector('.shell')); }, 8000);
}

async function run() {
    var window;
    var usable;

    process.stdout.write('A lista do painel chega sozinha na abertura\n');
    window = loadApp();
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.BuroApp.state.sources.length === 1; }, 8000);

    check('a fonte é criada com o que o painel enviou',
        window.__authenticated.server === ASSIGNED.server &&
        window.__authenticated.username === ASSIGNED.username &&
        window.__authenticated.password === ASSIGNED.password);
    /* O nome sai do endereço, nunca da credencial: ele aparece na lista de
       fontes, e não pode virar um lugar onde a senha se lê. */
    check('a fonte recebe um nome legível, sem credencial',
        window.BuroApp.state.sources[0].name === 'meuprovedor.com');
    check('as categorias do provedor entram junto',
        window.BuroApp.state.categories.length === 3);
    /*
      O servidor só apaga o que guardou depois da confirmação: uma entrega que
      se perde precisa poder ser tentada de novo na abertura seguinte.
    */
    check('a TV confirma ao servidor, sem erro',
        window.__confirmations.length === 1 && window.__confirmations[0] === null);
    window.close();

    process.stdout.write('Quem já tem lista recebe a nova ao lado da dele\n');
    window = loadApp();
    await reachShell(window);
    /*
      O ponto mais importante deste arquivo, e o que ele guarda mudou de forma.

      Antes o aplicativo recusava qualquer envio se já houvesse uma fonte. A
      intenção era não trocar a lista de quem tinha configurado a própria, e o
      efeito era outro: quem vendeu não conseguia mandar nem a segunda assinatura
      que o cliente acabara de comprar, nem a substituta de um endereço que caiu.

      O que continua valendo é o que importa: **nada do cliente é apagado**. A
      lista dele permanece, a nova entra ao lado, e só a fonte selecionada muda.
      Uma entrega que apagasse a lista de alguém seria pior do que uma que não
      chega, porque não há como desfazer.
    */
    window.BuroApp.state.sources = [{
        id: 'minha', name: 'Minha lista', type: 'XTREAM', channelCount: 1, createdAt: 1, updatedAt: null
    }];
    window.__fetched = 0;
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.BuroApp.state.sources.length > 1; }, 8000)
        .catch(function () {});
    check('agora pergunta ao servidor mesmo com lista configurada',
        window.__fetched === 1);
    check('a lista do cliente continua lá',
        window.BuroApp.state.sources.some(function (item) { return item.id === 'minha'; }));
    check('e a enviada entra ao lado, sem apagar nada',
        window.BuroApp.state.sources.length === 2);
    window.close();

    process.stdout.write('Sem nada para aplicar, a abertura segue igual\n');
    window = loadApp({ assigned: null });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await new Promise(function (resolve) { window.setTimeout(resolve, 120); });
    /* O caso comum: toda abertura de todo aparelho que nunca foi provisionado. */
    check('nenhuma fonte é criada',
        window.BuroApp.state.sources.length === 0);
    check('e o servidor não recebe confirmação de coisa nenhuma',
        window.__confirmations.length === 0);
    window.close();

    process.stdout.write('Credencial que não autentica vira aviso ao painel\n');
    window = loadApp({ authFails: true });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.__confirmations.length === 1; }, 8000);
    /*
      Validar antes de gravar importa mais aqui do que no formulário: quem
      digitou está olhando para a tela e vê o erro. Isto acontece sozinho, e uma
      credencial errada gravada em silêncio daria ao cliente um aplicativo que
      abre e não mostra nada.
    */
    check('a fonte não é criada com credencial que o provedor recusa',
        window.BuroApp.state.sources.length === 0);
    /* Quem vendeu precisa saber, porque é quem pode trocar o endereço. */
    check('e o painel é avisado do motivo',
        window.__confirmations[0] === 'AUTH_REJECTED');
    window.close();

    process.stdout.write('Servidor fora do ar não estraga a abertura\n');
    window = loadApp({ fetchFails: true });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await new Promise(function (resolve) { window.setTimeout(resolve, 120); });
    check('o aplicativo abre normalmente, sem fonte e sem alarde',
        window.BuroApp.state.sources.length === 0 &&
        Boolean(window.document.querySelector('.shell')));
    check('e nada é confirmado, para a próxima abertura tentar de novo',
        window.__confirmations.length === 0);
    window.close();

    process.stdout.write('As chaves de metadados vêm no mesmo pacote\n');
    window = loadApp({
        assigned: {
            server: ASSIGNED.server, username: ASSIGNED.username, password: ASSIGNED.password,
            metadataKey: 'chave-tmdb-de-teste-0123', criticsKey: '190c5dc3'
        }
    });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.BuroApp.state.sources.length === 1; }, 8000);
    await new Promise(function (resolve) { window.setTimeout(resolve, 80); });
    /*
      Pelo mesmo motivo da lista: quem não consegue cadastrar um servidor também
      não vai criar conta no TMDb. Assim o aplicativo chega mostrando capa,
      elenco e sinopse.
    */
    check('a chave de metadados é guardada com a lista',
        window.BuroTmdb.keyForProfile(window.BuroApp.state.activeProfile.id) ===
            'chave-tmdb-de-teste-0123');
    check('e a chave da crítica também',
        window.BuroCritics.configured());
    window.close();

    process.stdout.write('Uma chave que a pessoa já escolheu não é substituída\n');
    window = loadApp({
        assigned: {
            server: ASSIGNED.server, username: ASSIGNED.username, password: ASSIGNED.password,
            metadataKey: 'chave-do-vendedor-01234'
        }
    });
    await reachShell(window);
    await new Promise(function (resolve, reject) {
        window.BuroTmdb.save('shared', null, 'chave-que-a-pessoa-escolheu', resolve, reject);
    });
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.BuroApp.state.sources.length === 1; }, 8000);
    await new Promise(function (resolve) { window.setTimeout(resolve, 80); });
    /* Quem configurou a própria conta fez uma escolha, e ela vale mais do que a
       nossa: a lista é aplicada, a chave dela fica. */
    check('a chave configurada pela pessoa sobrevive ao provisionamento',
        window.BuroTmdb.keyForProfile(window.BuroApp.state.activeProfile.id) ===
            'chave-que-a-pessoa-escolheu');
    check('e a lista do painel foi aplicada mesmo assim',
        window.BuroApp.state.sources.length === 1);
    window.close();

    process.stdout.write('\n');
    /*
      Uma entrega que so traz uma chave.

      Quem vende precisava preencher endereco, usuario e senha para entregar
      qualquer coisa - mesmo so uma chave do TMDb a um cliente cuja lista ja
      funciona. Relatado no painel como "nao deixa eu enviar so api tmdb preciso
      enviar tudo".
    */
    process.stdout.write('\nUma entrega que nao traz conexao\n');
    window = loadApp({ assigned: { metadataKey: 'chave-do-vendedor' } });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.__confirmations.length === 1; }, 8000);

    check('a chave do vendedor e aplicada',
        window.BuroTmdb.keyForProfile(window.BuroApp.state.activeProfile.id) === 'chave-do-vendedor');
    /* Sem conexao nao ha o que gravar: uma fonte em branco seria uma linha
       inutil que o cliente teria de apagar. */
    check('e nenhuma fonte vazia e criada',
        window.BuroApp.state.sources.length === 0);
    /* Confirmada mesmo assim, senao o painel mostra pendente para sempre uma
       entrega que ja foi aplicada. */
    check('a entrega e confirmada como aplicada',
        window.__confirmations.length === 1 && window.__confirmations[0] === null);
    window.close();

    process.stdout.write('\nO nome que quem vendeu escolheu\n');
    window = loadApp({
        assigned: {
            server: ASSIGNED.server, username: ASSIGNED.username,
            password: ASSIGNED.password, listLabel: 'Plano Familia'
        }
    });
    await reachShell(window);
    window.BuroApp._applyAssignedSource();
    await waitFor(function () { return window.BuroApp.state.sources.length === 1; }, 8000);

    /* O endereco e como o servidor se chama; o nome e como o cliente comprou. */
    check('a fonte recebe o nome escolhido no painel, nao o endereco',
        window.BuroApp.state.sources[0].name === 'Plano Familia');
    window.close();

    /*
      A regra que decide se a lista de alguem e substituida, exercitada direto:
      o resto deste arquivo substitui fetchAssignedSource por um duble, entao
      nada acima passa por ela.
    */
    process.stdout.write('\nO que a TV aceita de uma entrega\n');
    window = loadApp();
    usable = window.BuroLicense.usableAssignedSourceForTesting;

    check('so uma chave basta', usable({ metadataKey: 'k' }) !== null);
    check('so um nome basta', usable({ listLabel: 'Plano Familia' }) !== null);
    check('uma conexao inteira passa', usable(ASSIGNED) !== null);
    /* Meia credencial nao e entrega parcial: e uma lista que nunca abre, e o
       erro apareceria na TV do cliente e nao no painel de quem vendeu. Cada caso
       leva uma chave junto, senao seria a regra do pacote vazio a recusar - e
       este teste passaria com a regra da credencial apagada. */
    check('endereco sem senha e recusado',
        usable({ server: ASSIGNED.server, username: 'u', metadataKey: 'k' }) === null);
    check('senha sem endereco e recusada',
        usable({ username: 'u', password: 'p', metadataKey: 'k' }) === null);
    check('um pacote vazio e recusado', usable({}) === null && usable(null) === null);
    window.close();

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
