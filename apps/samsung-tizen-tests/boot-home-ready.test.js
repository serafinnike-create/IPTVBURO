/* A Home e as capas do primeiro quadro precisam ficar prontas antes da SHELL. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
var pattern = /<script src="([^"]+)"><\/script>/g;
var files = [];
var match = pattern.exec(html);
var passed = 0;
var failures = [];

while (match) { files.push(match[1]); match = pattern.exec(html); }

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function wait(milliseconds) {
    return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
}

async function run() {
    var dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/' });
    var window = dom.window;
    var folded = false;
    var revealed = false;
    var year = new Date().getFullYear();
    var item = {
        id: 'movie:boot', sourceId: 'source-boot', categoryId: 'category-boot', contentType: 'MOVIE',
        name: 'Filme da abertura', year: year, rating: 8.2, addedAt: Date.now(), sortOrder: 0,
        logoUrl: 'https://image.tmdb.org/t/p/w342/boot-synthetic.jpg'
    };
    var result = {
        count: 1, sourceId: 'source-boot', categoryVisibility: { 'category-boot': true }, currentYear: year,
        currentReleases: [item], previousReleases: [], recent: [item], topRated: [item],
        movies: [item], series: [], categoryService: {}, byService: {}
    };

    files.forEach(function (file) { window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8')); });
    window.BuroApp.state.preferences = { language: 'pt-BR', section: 'HOME', hiddenCategoryIds: [] };
    window.BuroApp.state.sources = [{ id: 'source-boot', type: 'REMOTE_M3U', name: 'Fonte sintética' }];
    window.BuroApp.state.activeSource = window.BuroApp.state.sources[0];
    window.BuroApp.state.categories = [{
        id: 'category-boot', sourceId: 'source-boot', contentType: 'MOVIE', name: 'Filmes'
    }];
    /* Evita render: este contrato exercita o preparador isolado, antes de a
       raiz da aplicação ser ligada por init(). */
    window.BuroApp.state.screen = 'BOOT_TEST';
    window.BuroStorage.fold = function (store, reducer, seed, success) {
        setTimeout(function () { folded = true; success(result); }, 10);
    };
    window.Image = function () {
        var self = this;
        Object.defineProperty(this, 'src', {
            set: function () { setTimeout(function () { if (self.onload) { self.onload(); } }, 60); }
        });
    };

    process.stdout.write('Preparação bloqueante da primeira Home\n');
    window.BuroApp._prepareHomeForReveal(function () { revealed = true; });
    check('a Home não é liberada antes da leitura do catálogo', !revealed && !folded);
    await wait(25);
    check('a leitura termina, mas a capa visível ainda segura a liberação', folded && !revealed);
    check('a tela informa quantas capas iniciais estão prontas',
        window.BuroApp.state.boot.detail === '0/1 capas iniciais prontas');
    await wait(80);
    check('a Home só é liberada depois de a capa resolver', revealed);
    check('o resultado editorial já está no cache antes da SHELL',
        window.BuroApp._homeCache() && window.BuroApp._homeCache().result === result);
    check('a capa carregada já alimenta o mosaico da abertura',
        window.BuroApp.state.boot.previewArtwork.length === 1 &&
        window.BuroApp.state.boot.previewArtwork[0].indexOf('image.tmdb.org') >= 0);
    check('a etapa Home chega ao fim sem porcentagem simulada', window.BuroApp.state.boot.fraction === 1);

    process.stdout.write('Prateleiras TMDb configuradas também pertencem ao primeiro quadro\n');
    revealed = false;
    var heroRequested = false;
    var heroResolved = false;
    window.BuroTmdb.keyForProfile = function () { return 'synthetic-read-token'; };
    window.BuroTmdb.readShelfCache = function () { return null; };
    window.BuroTmdb.loadShelves = function (key, region, kind, locale, progress, success) {
        setTimeout(function () {
            success([{
                providerId: 8, providerName: 'Serviço sintético', providerLogoUrl: null,
                titles: new Array(12).fill(null).map(function (unused, index) {
                    return { tmdbId: 42 + index, isSeries: false, title: 'Capa pública ' + index, year: year,
                        posterUrl: 'https://image.tmdb.org/t/p/w342/tmdb-boot-synthetic-' + index + '.jpg' };
                })
            }]);
        }, 70);
        return { abort: function () {} };
    };
    window.BuroTmdb.loadTitle = function (key, candidate, isSeries, locale, success) {
        heroRequested = true;
        setTimeout(function () {
            heroResolved = true;
            success({ plot: 'Sinopse pronta antes da Home.', genre: 'Drama', duration: 96, rating: 7.7,
                posterUrl: 'https://image.tmdb.org/t/p/w342/hero-boot-poster.jpg',
                backdropUrl: 'https://image.tmdb.org/t/p/w1280/hero-boot-backdrop.jpg' });
        }, 80);
        return { abort: function () {} };
    };
    window.BuroApp._prepareHomeForReveal(function () { revealed = true; });
    await wait(35);
    check('a SHELL continua coberta enquanto a prateleira TMDb está na rede', !revealed);
    await wait(90);
    check('depois das prateleiras, o Hero real ainda segura a primeira Home', heroRequested && !heroResolved && !revealed);
    await wait(170);
    check('prateleiras, Hero e capas resolvem antes de liberar a Home', heroResolved && revealed);
    check('o mosaico usa doze capas reais e inclui o backdrop do Hero',
        window.BuroApp.state.boot.previewArtwork.length === 12 &&
        window.BuroApp.state.boot.previewArtwork.some(function (url) {
            return url.indexOf('hero-boot-backdrop.jpg') >= 0;
        }));
    check('o mosaico aproveita capas TMDb públicas sem persistir a chave',
        window.BuroApp.state.boot.previewArtwork.some(function (url) {
            return url.indexOf('tmdb-boot-synthetic-') >= 0;
        }) && window.localStorage.getItem('iptvburo.preferences.v1') === null);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write('Falhas: ' + failures.length + '\n');
        failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.stack + '\n');
    process.exitCode = 1;
});
