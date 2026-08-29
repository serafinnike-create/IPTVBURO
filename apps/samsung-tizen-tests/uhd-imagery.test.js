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

function tmdbAt(pixelRatio, panel, display) {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    Object.defineProperty(window, 'devicePixelRatio', { value: pixelRatio, configurable: true });
    if (panel || display) {
        window.tizen = { systeminfo: { getPropertyValue: function (property, success, failure) {
            if (property === 'PANEL' && panel) { success(panel); return; }
            if (property === 'DISPLAY' && display) { success(display); return; }
            failure({ name: 'NotSupportedError' });
        } } };
    }
    ['domain.js', 'storage.js', 'network.js', 'display-quality.js', 'tmdb.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, 'js', file), 'utf8'));
    });
    return window;
}

var hd = tmdbAt(1);
var uhdRatio = tmdbAt(2);
var uhdPanel = tmdbAt(1, { panelWidth: 3840, panelHeight: 2160 });
var eightKPanel = tmdbAt(1, { panelWidth: 7680, panelHeight: 4320 });
var uhdDisplay = tmdbAt(1, null, { resolutionWidth: 3840, resolutionHeight: 2160 });

process.stdout.write('1080p preserva os tamanhos economicos\n');
check('poster FHD usa w342', hd.BuroTmdb.image('/abc.jpg', 'w342') ===
    'https://image.tmdb.org/t/p/w342/abc.jpg');
check('fundo FHD usa w1280', hd.BuroTmdb.image('/abc.jpg', 'w1280') ===
    'https://image.tmdb.org/t/p/w1280/abc.jpg');

process.stdout.write('UHD pede fontes maiores sem alterar o layout\n');
check('poster UHD usa w780', uhdRatio.BuroTmdb.image('/abc.jpg', 'w342') ===
    'https://image.tmdb.org/t/p/w780/abc.jpg');
check('fundo UHD usa original', uhdRatio.BuroTmdb.image('/abc.jpg', 'w1280') ===
    'https://image.tmdb.org/t/p/original/abc.jpg');
check('logo UHD usa w185', uhdRatio.BuroTmdb.image('/abc.jpg', 'w92') ===
    'https://image.tmdb.org/t/p/w185/abc.jpg');
check('foto do elenco UHD usa w342', uhdRatio.BuroTmdb.image('/abc.jpg', 'w185') ===
    'https://image.tmdb.org/t/p/w342/abc.jpg');

process.stdout.write('A resolucao fisica vem das APIs Tizen\n');
check('PANEL detecta UHD com pixelRatio 1', uhdPanel.BuroDisplayQuality.info().tier === 'uhd' &&
    uhdPanel.BuroTmdb.image('/abc.jpg', 'w342').indexOf('/w780/') > 0);
check('PANEL detecta 8K', eightKPanel.BuroDisplayQuality.info().tier === '8k' &&
    eightKPanel.BuroDisplayQuality.info().scale === 4);
check('8K mantem o teto seguro de imagem',
    eightKPanel.BuroTmdb.image('/abc.jpg', 'w1280').indexOf('/original/') > 0);
check('DISPLAY e o fallback quando PANEL nao existe',
    uhdDisplay.BuroDisplayQuality.info().tier === 'uhd' &&
    uhdDisplay.BuroDisplayQuality.info().source === 'display');
check('o tier fica visivel para diagnostico no DOM',
    eightKPanel.document.documentElement.getAttribute('data-display-quality') === '8k');

process.stdout.write('Cache e validacao continuam seguros\n');
(function () {
    var shelves = [{ providerId: 8, providerName: 'Netflix',
        providerLogoUrl: 'https://image.tmdb.org/t/p/w92/logo.jpg',
        titles: [{ tmdbId: 1, title: 'Um Filme', year: 2024, isSeries: false,
            releaseDate: '2024-01-02', posterUrl: 'https://image.tmdb.org/t/p/w342/poster.jpg' }] }];
    uhdPanel.BuroTmdb.writeShelfCache('BR', 'MOVIES', 'pt-BR', shelves);
    var back = uhdPanel.BuroTmdb.readShelfCache('BR', 'MOVIES', 'pt-BR');
    check('cache FHD existente e promovido para poster e logo UHD', Boolean(back) &&
        back[0].providerLogoUrl.indexOf('/w185/') > 0 && back[0].titles[0].posterUrl.indexOf('/w780/') > 0);
}());
check('caminho malformado continua bloqueado',
    eightKPanel.BuroTmdb.image('../etc/passwd', 'w342') === null &&
    eightKPanel.BuroTmdb.image('/a/../../b.jpg', 'w1280') === null);

[hd, uhdRatio, uhdPanel, eightKPanel, uhdDisplay].forEach(function (window) { window.close(); });
process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exitCode = 1;
} else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
