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
                success({ results: [{ provider_id: 8, provider_name: 'Netflix', display_priority: 1 }] });
            } else if (options.url.indexOf('/watch/providers/tv?') >= 0) {
                success({ results: [{ provider_id: 9, provider_name: 'Prime Video', display_priority: 1 }] });
            } else if (options.url.indexOf('/discover/tv?') >= 0) {
                success({ results: [{ id: 77, name: 'Série sintética', first_air_date: '2024-01-02', poster_path: '/tv.jpg', vote_average: 7.5 }] });
            } else if (options.url.indexOf('/discover/movie?') >= 0) {
                success({ results: options.url.indexOf('with_release_type=3') >= 0 ?
                    [{ id: 88, title: 'Cinema recente', release_date: '2026-07-01', poster_path: '/cinema.jpg' }] :
                    [{ id: 42, title: 'Filme sintético', release_date: '2025-02-03', poster_path: '/poster.jpg', vote_average: 8.4 }] });
            } else if (options.url.indexOf('/movie/88/watch/providers?') >= 0) {
                success({ results: { BR: { rent: [{ provider_id: 3, provider_name: 'Loja' }] } } });
            } else if (options.url.indexOf('/movie/42/watch/providers?') >= 0) {
                success({ results: { BR: {
                    link: 'https://www.themoviedb.org/movie/42/watch',
                    flatrate: [{ provider_id: 8, provider_name: 'Netflix' }],
                    ads: [{ provider_id: 9, provider_name: 'Plex' }], rent: [{ provider_id: 3, provider_name: 'Apple TV' }]
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
    check('descoberta sempre envia região e filtro de provedor quando aplicável',
        requests.some(function (entry) {
            return entry.url.indexOf('/discover/movie?') >= 0 && entry.url.indexOf('watch_region=BR') >= 0 &&
                entry.url.indexOf('with_watch_providers=8') >= 0;
        }) && requests.some(function (entry) {
            return entry.url.indexOf('/discover/tv?') >= 0 && entry.url.indexOf('air_date.gte') >= 0;
        }));
    window.BuroTmdb.loadSubscriptionTitle(key, movieShelves[0].titles[0], 'BR', 'pt-BR',
        function (value) { subscriptionSelection = value; }, function () {});
    check('detalhe combina página e buckets de disponibilidade sem inventar preço',
        subscriptionSelection.details.tmdbId === 42 && subscriptionSelection.offers.length === 3 &&
        subscriptionSelection.offers.every(function (offer) {
            return offer.price === undefined && offer.requiresAttribution === true;
        }));
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
