/* Notas da crítica, do OMDb. A chave fica no KeyManager e nunca entra em logs nem no catálogo. */
var BuroCritics = (function () {
    'use strict';

    var API_BASE = 'https://www.omdbapi.com/';
    var SECRET_ID = 'critics-shared';
    var MAX_RESPONSE_BYTES = 256 * 1024;
    var TIMEOUT_MS = 10000;
    var CACHE_LIMIT = 120;

    var SOURCE_ROTTEN_TOMATOES = 'Rotten Tomatoes';
    var SOURCE_METACRITIC = 'Metacritic';
    var SOURCE_IMDB = 'Internet Movie Database';
    var METASCORE_FAVOURABLE = 61;
    var METASCORE_MIXED = 40;

    /*
      As notas ficam em memória, por id do IMDb.

      O TMDb publica uma nota — a média dos usuários dele. A fileira de três que
      os aplicativos de celular mostram vem de três empresas que calculam coisas
      diferentes. A Rotten Tomatoes não tem API gratuita; o OMDb republica o
      número dela junto com os outros dois, indexado pelo id do IMDb, e é por
      isso que a fonte aqui é o OMDb.

      Um teto de entradas porque isso roda numa TV que fica dias ligada: sem o
      teto, navegar por um catálogo grande faz o mapa crescer sem parar.
    */
    var cache = {};
    var cacheOrder = [];

    function clean(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /* A chave do OMDb é um token curto hexadecimal-ish; o formato é conferido
       aqui para não gastar uma requisição sabendo que ela vai ser recusada. */
    function safeKey(value) {
        var key = clean(value);
        return /^[A-Za-z0-9]{6,64}$/.test(key) ? key : null;
    }

    function safeImdbId(value) {
        var id = clean(value);
        return /^tt\d{7,}$/.test(id) ? id : null;
    }

    function readSecret() {
        var value;
        try {
            value = BuroStorage.secureGet(SECRET_ID);
            return safeKey(value && value.apiKey);
        } catch (ignoredSecret) { return null; }
    }

    function key() { return readSecret(); }

    function configured() { return Boolean(readSecret()); }

    function save(value, success, failure) {
        var apiKey = safeKey(value);
        if (!apiKey) { failure({ code: 'CRITICS_KEY_INVALID' }); return; }
        BuroStorage.secureSave(SECRET_ID, { apiKey: apiKey }, success, function () {
            failure({ code: 'SECURE_STORE_UNAVAILABLE' });
        });
    }

    function remove() {
        try { BuroStorage.secureRemove(SECRET_ID); clear(); return true; }
        catch (ignoredRemove) { return false; }
    }

    function clear() {
        cache = {};
        cacheOrder = [];
    }

    function url(imdbId, apiKey) {
        return API_BASE + '?i=' + encodeURIComponent(imdbId) + '&apikey=' + encodeURIComponent(apiKey);
    }

    /*
      "83%" ou "73/100" como porcentagem inteira.

      As duas formas aparecem no mesmo array, de fontes diferentes, e significam
      a mesma coisa para quem lê. Qualquer outra é descartada em vez de chutada.
    */
    function parsePercent(value) {
        var trimmed = clean(value);
        var number = null;
        if (/%$/.test(trimmed)) { number = parseInt(trimmed.replace(/%$/, ''), 10); }
        else if (trimmed.indexOf('/100') >= 0) { number = parseInt(trimmed.split('/')[0], 10); }
        if (number == null || !isFinite(number) || number < 0 || number > 100) { return null; }
        return number;
    }

    function ratingFor(rows, source) {
        var found = null;
        rows.forEach(function (row) {
            if (row && clean(row.Source) === source) { found = clean(row.Value); }
        });
        return found;
    }

    /*
      Identidade visual das três fontes, equivalente ao contrato do Windows.

      Letras e cor, nunca uma cópia dos logotipos registrados. O Metascore usa
      as faixas públicas da própria fonte; pintar 32 de verde contradiz o número
      e seria pior do que omitir a cor.
    */
    function markFor(kind, score) {
        var percent;
        if (kind === 'tomatometer') {
            return { initials: 'RT', accent: '#FA320A', ink: '#FFFFFF' };
        }
        if (kind === 'imdb') {
            return { initials: 'IMDb', accent: '#F5C518', ink: '#111111' };
        }
        if (kind !== 'metascore') { return null; }
        percent = Number(score);
        if (!isFinite(percent) || percent < 0 || percent > 100) { return null; }
        return {
            initials: 'MC',
            accent: percent >= METASCORE_FAVOURABLE ? '#00CE7A' :
                (percent >= METASCORE_MIXED ? '#FFBD3F' : '#FF6874'),
            ink: '#111111'
        };
    }

    /*
      Cada nota é opcional por conta própria: o OMDb costuma ter nota do IMDb
      para filme sem Tomatometer, e mostrar a lacuna é honesto de um jeito que
      substituir pelo número de outra empresa não seria.
    */
    function scoresFrom(payload) {
        var rows = payload && Array.isArray(payload.Ratings) ? payload.Ratings : [];
        var imdbText = ratingFor(rows, SOURCE_IMDB);
        var imdbRating = imdbText ? Number(String(imdbText).split('/')[0].replace(',', '.')) : null;
        var scores = {
            tomatometer: parsePercent(ratingFor(rows, SOURCE_ROTTEN_TOMATOES)),
            metascore: parsePercent(ratingFor(rows, SOURCE_METACRITIC)),
            imdbRating: isFinite(imdbRating) && imdbRating > 0 && imdbRating <= 10 ? imdbRating : null
        };
        scores.hasAny = scores.tomatometer != null || scores.metascore != null || scores.imdbRating != null;
        return scores.hasAny ? scores : null;
    }

    function remember(imdbId, scores) {
        if (!cache[imdbId]) { cacheOrder.push(imdbId); }
        cache[imdbId] = scores;
        while (cacheOrder.length > CACHE_LIMIT) {
            delete cache[cacheOrder.shift()];
        }
    }

    function cached(imdbId) {
        var id = safeImdbId(imdbId);
        return id && Object.prototype.hasOwnProperty.call(cache, id) ? cache[id] : undefined;
    }

    /*
      As notas de um título, ou null quando não há nada para mostrar.

      Null em vez de objeto vazio pelo mesmo motivo do resto: uma fileira que não
      pode ser preenchida deve estar ausente, não virar um painel de traços.

      Qualquer falha vira null e nada mais. Isso é um acréscimo a uma tela que já
      está mostrando o filme para o usuário, então limite de uso ou TV sem rede
      custa a fileira e mais nada.
    */
    function scoresFor(imdbId, success) {
        var id = safeImdbId(imdbId);
        var apiKey = readSecret();
        var known;
        if (!id || !apiKey) { success(null); return null; }
        known = cached(id);
        if (known !== undefined) { success(known); return null; }
        return BuroNetwork.json({
            url: url(id, apiKey), maxBytes: MAX_RESPONSE_BYTES, timeoutMs: TIMEOUT_MS,
            headers: { Accept: 'application/json' }
        }, function (payload) {
            /* O OMDb relata falha no corpo com HTTP 200:
               {"Response":"False","Error":"..."} */
            var ok = payload && String(payload.Response || '').toLowerCase() === 'true';
            var scores = ok ? scoresFrom(payload) : null;
            remember(id, scores);
            success(scores);
        }, function () {
            /* Uma falha de rede não é cacheada: a próxima visita tenta de novo. */
            success(null);
        });
    }

    return {
        safeKey: safeKey, safeImdbId: safeImdbId, key: key, configured: configured,
        save: save, remove: remove, clear: clear, scoresFor: scoresFor, markFor: markFor,
        cached: cached, _scoresFrom: scoresFrom, _parsePercent: parsePercent
    };
}());
