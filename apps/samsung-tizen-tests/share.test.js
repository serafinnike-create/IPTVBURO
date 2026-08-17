/* Contratos do compartilhamento Samsung: recomendação pública, QR e nenhum segredo. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var jsQR = require('jsqr');
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
    var payload;
    var matrix;
    var parsed;
    var last;
    function decode(matrixValue) {
        var quiet = 4;
        var scale = 8;
        var width = (matrixValue.size + quiet * 2) * scale;
        var pixels = new Uint8ClampedArray(width * width * 4);
        var x;
        var y;
        var pixelX;
        var pixelY;
        var dark;
        var offset;
        for (pixelY = 0; pixelY < width; pixelY += 1) {
            for (pixelX = 0; pixelX < width; pixelX += 1) {
                x = Math.floor(pixelX / scale) - quiet; y = Math.floor(pixelY / scale) - quiet;
                dark = x >= 0 && y >= 0 && x < matrixValue.size && y < matrixValue.size && matrixValue.get(x, y);
                offset = (pixelY * width + pixelX) * 4;
                pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = dark ? 0 : 255;
                pixels[offset + 3] = 255;
            }
        }
        return jsQR(pixels, width, width, { inversionAttempts: 'dontInvert' });
    }
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/qr.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/share.js'), 'utf8'));

    process.stdout.write('Identidade compartilhável\n');
    check('identidade remove marcadores de qualidade como o domínio Kotlin',
        window.BuroShare.identity('MOVIE', '[4K] O Auto da Compadecida + Extras (2024) DUAL', 2024) ===
            'movie:o-auto-da-compadecida-extras:2024');
    payload = window.BuroShare.build({
        kind: 'MOVIE', title: 'O Auto da Compadecida + Extras', year: 2024,
        artworkUrl: 'https://image.tmdb.org/t/p/w342/poster.jpg',
        description: 'Uma recomendação pública.\nSem dados privados.',
        streamUrl: 'https://provider.test/movie/user/password/42.mkv',
        username: 'user', password: 'password'
    });
    check('link usa a página pública e preserva título, ano e imagem TMDb',
        payload.webUrl.indexOf('https://iptvburo.pages.dev/t/?') === 0 &&
        payload.webUrl.indexOf('t=O%20Auto%20da%20Compadecida%20%2B%20Extras') >= 0 &&
        payload.webUrl.indexOf('y=2024') >= 0 && payload.webUrl.indexOf('image.tmdb.org') >= 0);
    check('stream, provedor, usuário e senha jamais entram no link ou QR',
        [payload.webUrl, payload.qr.url].every(function (value) {
            return value.indexOf('provider.test') === -1 && value.indexOf('password') === -1 &&
                value.indexOf('username') === -1 && value.indexOf('streamUrl') === -1;
        }));
    check('descrição é normalizada e limitada',
        window.BuroShare.build({ kind: 'SERIES', title: 'Série', description: 'x'.repeat(500) })
            .description.length === window.BuroShare.maxDescription + 1);
    check('arte de fonte, userinfo e host parecido são rejeitados',
        !window.BuroShare.publicArtwork('https://provider.test/poster.jpg') &&
        !window.BuroShare.publicArtwork('https://user:pass@image.tmdb.org/poster.jpg') &&
        !window.BuroShare.publicArtwork('https://image.tmdb.org.evil.test/poster.jpg'));
    parsed = window.BuroShare.parse(payload.webUrl);
    check('link é interpretável pelo mesmo contrato sem relaxar a imagem',
        parsed.identity === payload.identity && parsed.title === payload.title && parsed.year === 2024 &&
        parsed.artworkUrl.indexOf('image.tmdb.org') >= 0);

    process.stdout.write('Matriz QR local\n');
    matrix = window.BuroQr.encode('https://iptvburo.pages.dev/t/?id=movie%3Ateste&t=Teste');
    last = matrix.size - 1;
    check('QR escolhe uma versão válida e fica dentro do limite 1–10',
        matrix && (matrix.size - 17) % 4 === 0 && matrix.size >= 21 && matrix.size <= 57);
    check('os três localizadores e o módulo escuro estão presentes',
        matrix.get(0, 0) && matrix.get(3, 3) && matrix.get(last - 6, 0) &&
        matrix.get(0, last - 6) && matrix.get(8, matrix.size - 8));
    check('padrão de temporização alterna nos dois eixos',
        (function () {
            var index;
            for (index = 8; index < matrix.size - 8; index += 1) {
                if (matrix.get(index, 6) !== (index % 2 === 0) || matrix.get(6, index) !== (index % 2 === 0)) { return false; }
            }
            return true;
        }()));
    check('SVG é local, vetorial e não faz requisição para serviço de QR',
        payload.qr.svg.indexOf('<svg') === 0 && payload.qr.svg.indexOf('<path') >= 0 &&
        payload.qr.svg.indexOf('http') === -1 && payload.qr.svg.indexOf('<img') === -1);
    check('um decodificador independente lê exatamente o link longo de versão 10',
        payload.qr.matrix.size === 57 && decode(payload.qr.matrix) && decode(payload.qr.matrix).data === payload.qr.url);
    check('quando a sinopse não cabe, o QR compacto conserva identidade e título',
        payload.qr.url.indexOf('id=') >= 0 && payload.qr.url.indexOf('t=') >= 0 &&
        Boolean(window.BuroQr.encode(payload.qr.url)));
    check('texto além da versão suportada falha fechado', !window.BuroQr.encode('A'.repeat(400)));

    dom.window.close();
    process.stdout.write('\n' + passed + ' verificações aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
}

run();
