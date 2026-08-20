/* Contratos TMDb Samsung: segredo, normalização e degradação segura. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function run() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secrets = {};
    var requests = [];
    var titleResult;
    var personResult;
    var movieShelves;
    var seriesShelves;
    var weeklyShelves;
    var upcomingShelves;
    var subscriptionSelection;
    var expandedTitles;
    var titleFailure;
    var key = '1234567890abcdef1234567890abcdef';
    var sharedKey = 'abcdef1234567890abcdef1234567890';

    window.BuroStorage = {
        secureSave: function (id, value, success) { secrets[id] = value; success(); },
        secureGet: function (id) {
            if (!secrets[id]) { throw { name: 'NotFoundError' }; }
            return secrets[id];
        },
        secureRemove: function (id) {
            if (!secrets[id]) { throw { name: 'NotFoundError' }; }
            delete secrets[id];
        }
    };
    window.BuroNetwork = {
        json: function (options, success, failure) {
            requests.push(options);
            if (options.url.indexOf('/watch/providers/movie?') >= 0) {
                success({ results: [{ provider_id: 8, provider_name: 'Netflix', logo_path: '/netflix.jpg', display_priority: 1 }] });
            } else if (options.url.indexOf('/watch/providers/tv?') >= 0) {
                success({ results: [{ provider_id: 9, provider_name: 'Prime Video', logo_path: '/prime-video.jpg', display_priority: 1 }] });
            } else if (options.url.indexOf('/discover/tv?') >= 0) {
                success({ results: [{ id: 77, name: 'Série sintética', first_air_date: '2024-01-02', poster_path: '/tv.jpg', vote_average: 7.5 }] });
            } else if (options.url.indexOf('/discover/movie?') >= 0) {
                success({ results: options.url.indexOf('page=2') >= 0 ? [] : (options.url.indexOf('with_release_type=3') >= 0 ?
                    [{ id: 88, title: 'Cinema recente', release_date: '2026-07-01', poster_path: '/cinema.jpg' }] :
                    [{ id: 42, title: 'Filme sintético', release_date: '2025-02-03', poster_path: '/poster.jpg', vote_average: 8.4 }]) });
            } else if (options.url.indexOf('/movie/88/watch/providers?') >= 0) {
                success({ results: { BR: { rent: [{ provider_id: 3, provider_name: 'Loja' }] } } });
            } else if (options.url.indexOf('/movie/42/watch/providers?') >= 0) {
                success({ results: { BR: {
                    link: 'https://www.themoviedb.org/movie/42/watch',
                    flatrate: [{ provider_id: 8, provider_name: 'Netflix', logo_path: '/netflix.jpg' }],
                    ads: [{ provider_id: 9, provider_name: 'Plex', logo_path: '/plex.jpg' }],
                    rent: [{ provider_id: 3, provider_name: 'Apple TV', logo_path: '/apple-tv.jpg' }]
                } } });
            } else if (options.url.indexOf('/search/movie?') >= 0) {
                success({ results: [
                    { id: 9, title: 'Outro', release_date: '2020-01-01' },
                    { id: 42, title: 'Filme sintético', release_date: '2025-02-03' }
                ] });
            } else if (options.url.indexOf('/movie/42?') >= 0) {
                success({
                    id: 42, title: 'Filme sintético', overview: 'Sinopse pública', release_date: '2025-02-03',
                    backdrop_path: '/backdrop.jpg', poster_path: '/poster.jpg', vote_average: 8.4, runtime: 122,
                    genres: [{ name: 'Drama' }, { name: 'Aventura' }],
                    credits: { cast: [
                        { id: 7, name: 'Ana Exemplo', character: 'Lia', profile_path: '/ana.jpg' },
                        { id: 8, name: 'Bruno Exemplo', character: 'Caio', profile_path: 'https://evil.test/a.jpg' }
                    ] },
                    videos: { results: [
                        { site: 'YouTube', type: 'Teaser', key: 'Teaser12345' },
                        { site: 'YouTube', type: 'Trailer', key: 'Trailer98765' }
                    ] }
                });
            } else if (options.url.indexOf('/search/person?') >= 0) {
                success({ results: [{ id: 7, name: 'Ana Exemplo', profile_path: '/ana-large.jpg', known_for_department: 'Acting' }] });
            } else if (options.url.indexOf('/person/7/combined_credits?') >= 0) {
                success({ cast: [
                    { id: 10, media_type: 'movie', title: 'Menos popular', release_date: '2020-01-01', popularity: 2 },
                    { id: 11, media_type: 'tv', name: 'Série popular', first_air_date: '2024-01-01', poster_path: '/series.jpg', character: 'Eva', popularity: 20 },
                    { id: 12, media_type: 'tv', name: 'Série popular', popularity: 1 }
                ] });
            } else if (options.url.indexOf('/person/7?') >= 0) {
                success({ biography: 'Biografia pública', birthday: '1990-01-02', place_of_birth: 'Brasil' });
            } else if (options.url.indexOf('/configuration?') >= 0) {
                success({ images: {} });
            } else { failure({ code: 'NETWORK_ERROR' }); }
            return { abort: function () {} };
        }
    };

    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/tmdb.js'), 'utf8'));

    process.stdout.write('Chave por perfil e armazenamento seguro\n');
    check('aceita chave opaca limitada e rejeita espaços/URL',
        window.BuroTmdb.safeKey(key) === key && !window.BuroTmdb.safeKey('short') &&
        !window.BuroTmdb.safeKey('https://example.test/key'));
    /*
      As duas credenciais do TMDb.

      A v3 e uma chave curta que vai na query; a v4 e um JWT que so funciona no
      header Bearer — como api_key ela volta 401. O site mostra as duas na mesma
      pagina e a v4 e a que a pessoa costuma copiar, entao aceitar so uma
      significava engolir a credencial certa e nunca carregar nada.

      O token abaixo e sintetico: tres segmentos base64url que satisfazem a
      forma sem serem uma credencial de ninguem.
    */
    process.stdout.write('As duas formas de credencial do TMDb\n');
    var bearerToken = 'eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJzeW50aGV0aWMiLCJ2ZXJzaW9uIjoxfQ.c3ludGhldGljLXNpZ25hdHVyZQ';
    check('um token v4 e reconhecido pela forma de tres segmentos',
        window.BuroTmdb.isBearerToken(bearerToken) === true);
    check('uma chave v3 nao e confundida com token',
        window.BuroTmdb.isBearerToken(key) === false);
    check('o token v4 passa na validacao de formato',
        window.BuroTmdb.safeKey(bearerToken) === bearerToken);
    (function () {
        var bearerRequest = null;
        var keyRequest = null;
        var originalJson = window.BuroNetwork.json;
        window.BuroNetwork.json = function (options) { bearerRequest = options; return { abort: function () {} }; };
        window.BuroTmdb.validateKey(bearerToken, function () {}, function () {});
        window.BuroNetwork.json = function (options) { keyRequest = options; return { abort: function () {} }; };
        window.BuroTmdb.validateKey(key, function () {}, function () {});
        window.BuroNetwork.json = originalJson;
        check('o token v4 viaja no header Authorization',
            bearerRequest.headers.Authorization === 'Bearer ' + bearerToken);
        check('o token v4 nao entra na URL, que e o que vai parar em log',
            bearerRequest.url.indexOf(bearerToken) === -1 &&
            bearerRequest.url.indexOf('api_key') === -1);
        check('a chave v3 continua indo como api_key, sem header',
            keyRequest.url.indexOf('api_key=' + key) > 0 && !keyRequest.headers.Authorization);
    }());

    window.BuroTmdb.save('shared', 'profile-a', sharedKey, function () {}, function () {});
    check('chave compartilhada é fallback sem entrar no localStorage',
        window.BuroTmdb.keyForProfile('profile-a') === sharedKey && window.localStorage.length === 0);
    window.BuroTmdb.save('profile', 'profile-a', key, function () {}, function () {});
    check('chave do perfil tem precedência e configuração expõe apenas booleanos',
        window.BuroTmdb.keyForProfile('profile-a') === key &&
        JSON.stringify(window.BuroTmdb.configuration('profile-a')).indexOf(key) === -1 &&
        window.BuroTmdb.configuration('profile-a').profile && window.BuroTmdb.configuration('profile-a').shared);
    check('remoção do perfil volta ao fallback compartilhado',
        window.BuroTmdb.remove('profile', 'profile-a') && window.BuroTmdb.keyForProfile('profile-a') === sharedKey);

    process.stdout.write('Enriquecimento de título\n');
    window.BuroTmdb.loadTitle(key, { name: 'Filme sintético', year: 2025 }, false, 'pt-BR',
        function (value) { titleResult = value; }, function (error) { titleFailure = error; });
    check('busca escolhe o ano exato antes do primeiro resultado',
        requests.some(function (entry) { return entry.url.indexOf('/movie/42?') >= 0; }));
    check('detalhes normalizam sinopse, duração, gêneros e nota',
        !titleFailure && titleResult.tmdbId === 42 && titleResult.plot === 'Sinopse pública' &&
        titleResult.duration === 122 && titleResult.genre === 'Drama / Aventura' && titleResult.rating === 8.4);
    check('imagens sempre usam host e tamanhos fixos do TMDb',
        titleResult.backdropUrl === 'https://image.tmdb.org/t/p/w1280/backdrop.jpg' &&
        titleResult.posterUrl === 'https://image.tmdb.org/t/p/w342/poster.jpg' &&
        titleResult.castMembers[0].photoUrl === 'https://image.tmdb.org/t/p/w185/ana.jpg' &&
        titleResult.castMembers[1].photoUrl === null);
    check('trailer completo tem prioridade sobre teaser e vira somente ID', titleResult.youtubeTrailerId === 'Trailer98765');
    check('requisições enviam título e chave TMDb, nunca credenciais da fonte',
        requests.every(function (entry) {
            return entry.url.indexOf('username=') === -1 && entry.url.indexOf('password=') === -1 &&
                entry.url.indexOf('provider.test') === -1;
        }));

    process.stdout.write('Pessoa e filmografia\n');
    window.BuroTmdb.loadPerson(key, 'Ana Exemplo', 'pt-BR', function (value) { personResult = value; }, function () {});
    check('pessoa combina retrato, biografia e nascimento',
        personResult.name === 'Ana Exemplo' && personResult.biography === 'Biografia pública' &&
        personResult.birthday === '1990-01-02' && personResult.placeOfBirth === 'Brasil');
    check('filmografia ordena por popularidade, deduplica e preserva tipo/id',
        personResult.credits.length === 2 && personResult.credits[0].title === 'Série popular' &&
        personResult.credits[0].isSeries && personResult.credits[0].id === 11);
    check('validação consulta endpoint fixo sem revelar chave em estado persistido',
        (function () { var valid = false; window.BuroTmdb.validateKey(key, function () { valid = true; }, function () {}); return valid; }()) &&
        window.localStorage.length === 0);

    process.stdout.write('Cache diário das prateleiras públicas\n');
    var hasShelfCache = typeof window.BuroTmdb.readShelfCache === 'function' &&
        typeof window.BuroTmdb.writeShelfCache === 'function' &&
        typeof window.BuroTmdb.clearShelfCache === 'function';
    var cacheDay = new Date('2026-08-20T12:00:00');
    var nextDay = new Date('2026-08-21T12:00:00');
    var publicShelves = [{
        providerId: 8,
        providerName: 'Netflix',
        providerLogoUrl: 'https://image.tmdb.org/t/p/w92/netflix.jpg',
        titles: [{
            tmdbId: 42,
            isSeries: false,
            title: 'Filme sintético',
            year: 2025,
            releaseDate: '2025-02-03',
            posterUrl: 'https://image.tmdb.org/t/p/w342/poster.jpg',
            overview: 'Este texto não é necessário para desenhar a prateleira',
            url: 'https://provider.test/private?token=secret'
        }]
    }];
    var restoredShelves = null;
    var shelfCacheRaw = '';
    check('cliente expõe um cache separado do segredo TMDb', hasShelfCache);
    if (hasShelfCache) {
        window.BuroTmdb.clearShelfCache();
        window.BuroTmdb.writeShelfCache('BR', 'MOVIES', 'pt-BR', publicShelves, cacheDay);
        restoredShelves = window.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR', cacheDay);
        var cacheValues = [];
        for (var valueIndex = 0; valueIndex < window.localStorage.length; valueIndex += 1) {
            cacheValues.push(window.localStorage.getItem(window.localStorage.key(valueIndex)));
        }
        shelfCacheRaw = cacheValues.join('\n');
    }
    check('a resposta do mesmo dia, região, filtro e idioma abre sem rede',
        restoredShelves && restoredShelves.length === 1 && restoredShelves[0].providerId === 8 &&
        restoredShelves[0].providerName === 'Netflix' &&
        restoredShelves[0].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/netflix.jpg' &&
        restoredShelves[0].titles.length === 1 &&
        restoredShelves[0].titles[0].tmdbId === 42 && restoredShelves[0].titles[0].title === 'Filme sintético' &&
        restoredShelves[0].titles[0].releaseDate === '2025-02-03');
    check('o cache guarda apenas o cartão público e nunca chave, oferta ou URL arbitrária',
        shelfCacheRaw.indexOf(key) === -1 && shelfCacheRaw.indexOf(sharedKey) === -1 &&
        shelfCacheRaw.indexOf('provider.test') === -1 && shelfCacheRaw.indexOf('token=') === -1 &&
        shelfCacheRaw.indexOf('Este texto não é necessário') === -1 &&
        restoredShelves && !Object.prototype.hasOwnProperty.call(restoredShelves[0].titles[0], 'url') &&
        !Object.prototype.hasOwnProperty.call(restoredShelves[0].titles[0], 'overview'));
    if (hasShelfCache) {
        publicShelves[0].providerLogoUrl = 'https://evil.test/provider.jpg?token=secret';
        window.BuroTmdb.writeShelfCache('BR', 'UPCOMING', 'pt-BR', publicShelves, cacheDay);
    }
    check('o cache descarta marca fora do CDN e tamanho publicos do TMDb',
        hasShelfCache && window.BuroTmdb.readShelfCache('BR', 'UPCOMING', 'pt-BR', cacheDay)[0].providerLogoUrl === null);
    check('região, filtro e idioma diferentes nunca reutilizam a resposta errada',
        hasShelfCache && window.BuroTmdb.readShelfCache('DE', 'MOVIES', 'pt-BR', cacheDay) === null &&
        window.BuroTmdb.readShelfCache('BR', 'SERIES', 'pt-BR', cacheDay) === null &&
        window.BuroTmdb.readShelfCache('BR', 'MOVIES', 'de', cacheDay) === null);
    check('uma resposta do dia anterior expira em vez de aparecer como atual',
        hasShelfCache && window.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR', nextDay) === null);
    if (hasShelfCache) {
        window.BuroTmdb.writeShelfCache('BR', 'MOVIES', 'pt-BR', [], cacheDay);
    }
    check('resposta vazia não substitui um catálogo público válido',
        hasShelfCache && window.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR', cacheDay) !== null);
    if (hasShelfCache) {
        var shelfCacheKey = null;
        /* A chave é descoberta pelo efeito público do adapter, não duplicada do código de produção. */
        for (var cacheIndex = 0; cacheIndex < window.localStorage.length; cacheIndex += 1) {
            var candidate = window.localStorage.key(cacheIndex);
            if (candidate && candidate.indexOf('tmdb-shelves') >= 0) { shelfCacheKey = candidate; break; }
        }
        if (shelfCacheKey) { window.localStorage.setItem(shelfCacheKey, '{corrompido'); }
    }
    check('cache corrompido degrada para nova consulta sem derrubar a tela',
        hasShelfCache && window.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR', cacheDay) === null);
    if (hasShelfCache) { window.BuroTmdb.clearShelfCache(); }
    var hasShelfCacheEntry = false;
    for (var storageIndex = 0; storageIndex < window.localStorage.length; storageIndex += 1) {
        if (window.localStorage.key(storageIndex).indexOf('tmdb-shelves') >= 0) { hasShelfCacheEntry = true; }
    }
    check('limpar a chave TMDb pode remover todo o cache público associado', hasShelfCache && !hasShelfCacheEntry);

    process.stdout.write('Assinaturas e onde assistir\n');
    window.BuroTmdb.loadShelves(key, 'BR', 'MOVIES', 'pt-BR', function () {},
        function (value) { movieShelves = value; }, function () {});
    window.BuroTmdb.loadShelves(key, 'BR', 'SERIES', 'pt-BR', function () {},
        function (value) { seriesShelves = value; }, function () {});
    window.BuroTmdb.loadShelves(key, 'BR', 'THIS_WEEK', 'pt-BR', function () {},
        function (value) { weeklyShelves = value; }, function () {});
    window.BuroTmdb.loadShelves(key, 'BR', 'UPCOMING', 'pt-BR', function () {},
        function (value) { upcomingShelves = value; }, function () {});
    check('os quatro filtros usam diretório e tipo corretos',
        movieShelves[0].providerName === 'Netflix' && movieShelves[0].titles[0].tmdbId === 42 &&
        seriesShelves[0].providerName === 'Prime Video' && seriesShelves[0].titles[0].isSeries &&
        weeklyShelves[0].titles[0].title === 'Série sintética' &&
        upcomingShelves[0].providerName === 'coming-soon' && upcomingShelves[0].titles[0].tmdbId === 88);
    check('o diretorio conserva a marca publica w92 ao lado do nome do servico',
        movieShelves[0].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/netflix.jpg' &&
        seriesShelves[0].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/prime-video.jpg');
    check('descoberta sempre envia região e filtro de provedor quando aplicável',
        requests.some(function (entry) {
            return entry.url.indexOf('/discover/movie?') >= 0 && entry.url.indexOf('watch_region=BR') >= 0 &&
                entry.url.indexOf('with_watch_providers=8') >= 0;
        }) && requests.some(function (entry) {
            return entry.url.indexOf('/discover/tv?') >= 0 && entry.url.indexOf('air_date.gte') >= 0;
        }));
    check('cliente expõe o catálogo amplo e cancelável de um serviço',
        typeof window.BuroTmdb.loadServiceCatalogue === 'function');
    if (typeof window.BuroTmdb.loadServiceCatalogue === 'function') {
        window.BuroTmdb.loadServiceCatalogue(key, 8, 'BR', 'MOVIES', 'pt-BR',
            function (value) { expandedTitles = value; }, function () {});
        check('catálogo amplo percorre páginas, deduplica e para na primeira vazia',
            expandedTitles && expandedTitles.length === 1 && expandedTitles[0].tmdbId === 42 &&
            requests.some(function (entry) {
                return entry.url.indexOf('/discover/movie?') >= 0 && entry.url.indexOf('page=1') >= 0;
            }) && requests.some(function (entry) {
                return entry.url.indexOf('/discover/movie?') >= 0 && entry.url.indexOf('page=2') >= 0;
            }));
    }
    window.BuroTmdb.loadSubscriptionTitle(key, movieShelves[0].titles[0], 'BR', 'pt-BR',
        function (value) { subscriptionSelection = value; }, function () {});
    check('detalhe combina página e buckets de disponibilidade sem inventar preço',
        subscriptionSelection.details.tmdbId === 42 && subscriptionSelection.offers.length === 3 &&
        subscriptionSelection.offers.every(function (offer) {
            return offer.price === undefined && offer.requiresAttribution === true;
        }));
    check('cada oferta conserva a mesma marca publica do servico',
        subscriptionSelection.offers[0].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/netflix.jpg' &&
        subscriptionSelection.offers[1].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/plex.jpg' &&
        subscriptionSelection.offers[2].providerLogoUrl === 'https://image.tmdb.org/t/p/w92/apple-tv.jpg');
    check('serviço conhecido recebe busca oficial e desconhecido usa somente fallback TMDb confiável',
        subscriptionSelection.offers[0].url.indexOf('https://www.netflix.com/search?q=') === 0 &&
        window.BuroTmdb.providerTarget('Serviço desconhecido', 'Filme', 'https://evil.test/watch') === null &&
        window.BuroTmdb.providerTarget('Serviço desconhecido', 'Filme', 'https://www.themoviedb.org/movie/42')
            .indexOf('https://www.themoviedb.org/movie/42') === 0);
    check('região fica limitada às opções traduzidas',
        window.BuroTmdb.safeRegion('de') === 'DE' && window.BuroTmdb.safeRegion('XX') === 'BR');
    check('manifesto declara somente a capability necessária para abrir ofertas externas',
        fs.readFileSync(path.join(APP_DIR, 'config.xml'), 'utf8').indexOf(
            'http://tizen.org/privilege/application.launch') >= 0);

    dom.window.close();
    process.stdout.write('\n' + passed + ' verificações aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
}

run();
