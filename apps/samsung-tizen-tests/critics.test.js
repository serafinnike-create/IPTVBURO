/* Notas da crítica (OMDb). Nenhuma chave real e nenhum dado de provedor. */
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

function omdbPayload() {
    return {
        Response: 'True',
        Title: 'Filme Sintético',
        Ratings: [
            { Source: 'Internet Movie Database', Value: '8.7/10' },
            { Source: 'Rotten Tomatoes', Value: '83%' },
            { Source: 'Metacritic', Value: '73/100' }
        ]
    };
}

async function run() {
    var window;
    var scores;
    var requests;

    window = loadApp({ language: 'pt-BR', languageSelected: true });
    await waitFor(function () {
        return Boolean(window.document.querySelector('[data-action="legal-accept"]'));
    }, 6000);

    process.stdout.write('As três notas são lidas do formato do OMDb\n');
    scores = window.BuroCritics._scoresFrom(omdbPayload());
    check('o Tomatometer vem como porcentagem inteira', scores.tomatometer === 83);
    check('o Metascore normaliza a escala /100 para porcentagem', scores.metascore === 73);
    check('a nota do IMDb fica em dez, como o IMDb apresenta', scores.imdbRating === 8.7);

    process.stdout.write('Cada nota é opcional por conta própria\n');
    scores = window.BuroCritics._scoresFrom({
        Response: 'True',
        Ratings: [{ Source: 'Internet Movie Database', Value: '6.1/10' }]
    });
    check('um filme só com nota do IMDb ainda rende uma fileira',
        scores.hasAny && scores.imdbRating === 6.1);
    check('a lacuna fica como lacuna, não como zero',
        scores.tomatometer === null && scores.metascore === null);
    check('um título sem nota nenhuma não vira fileira de traços',
        window.BuroCritics._scoresFrom({ Response: 'True', Ratings: [] }) === null);

    process.stdout.write('Valores fora de formato são descartados, não chutados\n');
    check('porcentagem acima de cem é recusada', window.BuroCritics._parsePercent('120%') === null);
    check('texto sem número é recusado', window.BuroCritics._parsePercent('muito bom') === null);
    check('as duas formas conhecidas são aceitas',
        window.BuroCritics._parsePercent('45%') === 45 &&
        window.BuroCritics._parsePercent('62/100') === 62);

    process.stdout.write('Cada fonte recebe uma marca textual e uma cor verificavel\n');
    check('Rotten Tomatoes usa RT sobre o vermelho da fonte',
        typeof window.BuroCritics.markFor === 'function' &&
        window.BuroCritics.markFor('tomatometer', 83).initials === 'RT' &&
        window.BuroCritics.markFor('tomatometer', 83).accent === '#FA320A' &&
        window.BuroCritics.markFor('tomatometer', 83).ink === '#FFFFFF');
    check('IMDb usa a sigla completa com tinta escura sobre amarelo',
        typeof window.BuroCritics.markFor === 'function' &&
        window.BuroCritics.markFor('imdb', 8.7).initials === 'IMDb' &&
        window.BuroCritics.markFor('imdb', 8.7).accent === '#F5C518' &&
        window.BuroCritics.markFor('imdb', 8.7).ink === '#111111');
    check('Metascore respeita exatamente as faixas publica favoravel mista e desfavoravel',
        typeof window.BuroCritics.markFor === 'function' &&
        window.BuroCritics.markFor('metascore', 61).accent === '#00CE7A' &&
        window.BuroCritics.markFor('metascore', 60).accent === '#FFBD3F' &&
        window.BuroCritics.markFor('metascore', 40).accent === '#FFBD3F' &&
        window.BuroCritics.markFor('metascore', 39).accent === '#FF6874');
    check('fonte desconhecida nao ganha uma marca inventada',
        typeof window.BuroCritics.markFor === 'function' &&
        window.BuroCritics.markFor('desconhecida', 90) === null);

    process.stdout.write('O id do IMDb é conferido antes de gastar requisição\n');
    check('um id bem formado passa', window.BuroCritics.safeImdbId('tt0111161') === 'tt0111161');
    check('um id curto é recusado', window.BuroCritics.safeImdbId('tt111') === null);
    check('um id com injeção de query é recusado',
        window.BuroCritics.safeImdbId('tt0111161&apikey=x') === null);
    check('o TMDb aplica a mesma regra ao id que extrai',
        window.BuroTmdb.safeImdbId('tt0111161') === 'tt0111161' &&
        window.BuroTmdb.safeImdbId('nao-e-id') === null);

    process.stdout.write('Sem chave, nada é pedido e nada é mostrado\n');
    requests = [];
    window.BuroNetwork.json = function (options, success) {
        requests.push(options); success({}); return { abort: function () {} };
    };
    scores = 'nao-chamado';
    window.BuroCritics.scoresFor('tt0111161', function (value) { scores = value; });
    check('sem chave o resultado é ausência, não erro', scores === null);
    check('sem chave nenhuma requisição sai da TV', requests.length === 0);

    process.stdout.write('Com chave, a nota é buscada e a chave não vaza\n');
    await new Promise(function (resolve, reject) {
        window.BuroCritics.save('abcd1234', resolve, reject);
    });
    check('a chave fica no cofre de segredos', window.BuroCritics.configured() === true);
    check('a chave não fica em localStorage',
        JSON.stringify(window.localStorage).indexOf('abcd1234') === -1);
    requests = [];
    window.BuroNetwork.json = function (options, success) {
        requests.push(options);
        setTimeout(function () { success(omdbPayload()); }, 0);
        return { abort: function () {} };
    };
    scores = undefined;
    window.BuroCritics.scoresFor('tt0111161', function (value) { scores = value; });
    await waitFor(function () { return scores !== undefined; }, 4000);
    check('as notas chegam do OMDb', scores && scores.tomatometer === 83 && scores.metascore === 73);
    check('o id pedido é o do título, e só ele',
        requests.length === 1 && requests[0].url.indexOf('i=tt0111161') > 0);
    check('a resposta é limitada em tamanho, como o resto da rede do app',
        Number(requests[0].maxBytes) > 0 && Number(requests[0].maxBytes) <= 1024 * 1024);

    process.stdout.write('A mesma pergunta não é feita duas vezes\n');
    requests = [];
    scores = undefined;
    window.BuroCritics.scoresFor('tt0111161', function (value) { scores = value; });
    check('a segunda visita ao título usa o que já estava em memória',
        requests.length === 0 && scores && scores.tomatometer === 83);

    process.stdout.write('Falha do OMDb custa a fileira e mais nada\n');
    window.BuroNetwork.json = function (options, success, failure) {
        setTimeout(function () { failure({ code: 'NETWORK_ERROR' }); }, 0);
        return { abort: function () {} };
    };
    scores = undefined;
    window.BuroCritics.scoresFor('tt0068646', function (value) { scores = value; });
    await waitFor(function () { return scores !== undefined; }, 4000);
    check('uma falha de rede vira ausência de fileira', scores === null);
    check('a falha não é memorizada como resposta',
        window.BuroCritics.cached('tt0068646') === undefined);

    process.stdout.write('O OMDb relata erro com HTTP 200 no corpo\n');
    window.BuroNetwork.json = function (options, success) {
        setTimeout(function () { success({ Response: 'False', Error: 'Movie not found!' }); }, 0);
        return { abort: function () {} };
    };
    scores = undefined;
    window.BuroCritics.scoresFor('tt0071562', function (value) { scores = value; });
    await waitFor(function () { return scores !== undefined; }, 4000);
    check('um "Response: False" é lido como ausência, não como sucesso', scores === null);

    process.stdout.write('Remover a chave apaga também o que foi guardado\n');
    check('remover a chave dá certo', window.BuroCritics.remove() === true);
    check('a chave saiu do cofre', window.BuroCritics.configured() === false);
    check('as notas guardadas saíram junto',
        window.BuroCritics.cached('tt0111161') === undefined);

    process.stdout.write('A fileira aparece nas configurações em cinco idiomas\n');
    check('o cartão da crítica existe nas configurações',
        ['pt-BR', 'en', 'de', 'it', 'es'].every(function (language) {
            window.BuroI18n.setLanguage(language);
            return Boolean(window.BuroI18n.t('criticsTitle')) &&
                window.BuroI18n.t('criticsTitle') !== 'criticsTitle' &&
                Boolean(window.BuroI18n.t('criticsAbsent')) &&
                window.BuroI18n.t('criticsAbsent') !== 'criticsAbsent';
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
