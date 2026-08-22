/*
  A qualidade das imagens numa televisão 4K.

  O app roda em 1080p e em 4K, e a viewport é fixa em 1920x1080: a TV amplia o
  resultado, então numa 4K cada pixel do layout vira dois na tela. O pôster de
  300px da ficha é desenhado com 600 pixels reais, e uma imagem de 342px de
  largura esticada até lá fica visivelmente borrada — o fundo, pedido em 1280,
  é esticado até 3840.

  Aqui se verifica que a TV 4K pede o degrau maior, que a 1080p continua
  pedindo o mesmo de antes, e que as peneiras de segurança do cache não recusam
  as URLs novas — recusar em silêncio deixaria a TV 4K sem prateleira nenhuma.
*/
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

/* Um TMDb carregado numa TV com a densidade indicada. */
function tmdbAt(pixelRatio) {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    Object.defineProperty(window, 'devicePixelRatio', { value: pixelRatio, configurable: true });
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'storage.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'network.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js', 'tmdb.js'), 'utf8'));
    return window;
}

var hd = tmdbAt(1);
var uhd = tmdbAt(2);

process.stdout.write('Numa TV 1080p nada muda\n');
check('o pôster continua sendo pedido em w342',
    hd.BuroTmdb.image('/abc.jpg', 'w342') === 'https://image.tmdb.org/t/p/w342/abc.jpg');
check('o fundo continua sendo pedido em w1280',
    hd.BuroTmdb.image('/abc.jpg', 'w1280') === 'https://image.tmdb.org/t/p/w1280/abc.jpg');

process.stdout.write('Numa TV 4K a imagem sobe um degrau\n');
/*
  O ponto do teste: 342 esticado até 600 pixels reais é o borrão que se vê na
  ficha do título. `w780` cobre esse tamanho com folga.
*/
check('o pôster passa a w780, que cobre os 600px reais da ficha',
    uhd.BuroTmdb.image('/abc.jpg', 'w342') === 'https://image.tmdb.org/t/p/w780/abc.jpg');
check('o fundo passa a original, que preenche 3840px sem esticar',
    uhd.BuroTmdb.image('/abc.jpg', 'w1280') === 'https://image.tmdb.org/t/p/original/abc.jpg');

process.stdout.write('O que já é pequeno na tela continua pequeno\n');
/*
  Subir estes seria gastar banda sem ninguém ver diferença: o logotipo do
  serviço tem 92px de origem para um selo, e a foto do elenco é um círculo.
*/
check('o logotipo de serviço continua em w92',
    uhd.BuroTmdb.image('/abc.jpg', 'w92') === 'https://image.tmdb.org/t/p/w92/abc.jpg');
check('a foto redonda do elenco continua em w185',
    uhd.BuroTmdb.image('/abc.jpg', 'w185') === 'https://image.tmdb.org/t/p/w185/abc.jpg');

process.stdout.write('As peneiras do cache aceitam o tamanho novo\n');
/*
  Sem isto a TV 4K guardaria o cache e o releria vazio: a peneira nomeava só
  w342, e uma URL w780 seria recusada em silêncio — prateleira de Assinaturas
  em branco justamente no aparelho melhor.
*/
(function () {
    var shelves = [{
        providerId: 8, providerName: 'Netflix',
        providerLogoUrl: 'https://image.tmdb.org/t/p/w92/logo.jpg',
        titles: [{ tmdbId: 1, title: 'Um Filme', year: 2024, isSeries: false,
            releaseDate: '2024-01-02', posterUrl: 'https://image.tmdb.org/t/p/w780/poster.jpg' }]
    }];
    uhd.BuroTmdb.writeShelfCache('BR', 'MOVIES', 'pt-BR', shelves);
    var back = uhd.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR');
    check('a prateleira volta do cache com a capa em w780',
        Boolean(back) && back[0].titles[0].posterUrl === 'https://image.tmdb.org/t/p/w780/poster.jpg');
}());

process.stdout.write('A peneira continua recusando o que não é do TMDb\n');
(function () {
    var bad = [{
        providerId: 8, providerName: 'Netflix', providerLogoUrl: null,
        titles: [{ tmdbId: 1, title: 'X', year: 2024, isSeries: false,
            releaseDate: '2024-01-02', posterUrl: 'https://cdn.malicioso.com/t/p/w780/x.jpg' }]
    }];
    uhd.BuroTmdb.clearShelfCache();
    uhd.BuroTmdb.writeShelfCache('BR', 'SERIES', 'pt-BR', bad);
    var back = uhd.BuroTmdb.readShelfCache('BR', 'SERIES', 'pt-BR');
    check('outro host não entra no cache, nem com o tamanho certo',
        !back || back[0].titles[0].posterUrl === null);
}());
check('um tamanho que o app não pede continua fora',
    uhd.BuroTmdb.readShelfCache && (function () {
        uhd.BuroTmdb.clearShelfCache();
        uhd.BuroTmdb.writeShelfCache('BR', 'MOVIES', 'pt-BR', [{
            providerId: 8, providerName: 'N', providerLogoUrl: null,
            titles: [{ tmdbId: 1, title: 'X', year: 2024, isSeries: false,
                releaseDate: '2024-01-02', posterUrl: 'https://image.tmdb.org/t/p/w9999/x.jpg' }]
        }]);
        var back = uhd.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR');
        return !back || back[0].titles[0].posterUrl === null;
    }()));

process.stdout.write('Uma densidade que a TV não informa não quebra nada\n');
(function () {
    var odd = tmdbAt(undefined);
    check('sem devicePixelRatio o app trata como 1080p',
        odd.BuroTmdb.image('/abc.jpg', 'w342') === 'https://image.tmdb.org/t/p/w342/abc.jpg');
    odd.close();
}());
check('caminho malformado continua devolvendo nulo, em qualquer densidade',
    uhd.BuroTmdb.image('../etc/passwd', 'w342') === null &&
    uhd.BuroTmdb.image('/a/../../b.jpg', 'w1280') === null);

hd.close();
uhd.close();

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
