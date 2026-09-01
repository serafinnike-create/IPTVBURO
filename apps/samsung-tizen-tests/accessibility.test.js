/* WCAG 2.1 AA regression checks for the packaged Samsung shell. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
var css = fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8');
var document = new JSDOM(html).window.document;
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function luminance(hex) {
    var channels = String(hex).match(/[0-9a-f]{2}/gi).map(function (part) {
        var value = parseInt(part, 16) / 255;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrast(first, second) {
    var a = luminance(first);
    var b = luminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

process.stdout.write('Estrutura e regiões dinâmicas\n');
check('a raiz do aplicativo não anuncia novamente toda a tela a cada render',
    !document.getElementById('app').hasAttribute('aria-live'));
check('toast possui região atômica e educada por padrão',
    document.getElementById('toast').getAttribute('role') === 'status' &&
    document.getElementById('toast').getAttribute('aria-live') === 'polite' &&
    document.getElementById('toast').getAttribute('aria-atomic') === 'true');
check('erro do player é um diálogo assertivo nomeado e descrito',
    document.getElementById('player-error-panel').getAttribute('role') === 'alertdialog' &&
    document.getElementById('player-error-panel').getAttribute('aria-labelledby') === 'player-error-title' &&
    document.getElementById('player-error-panel').getAttribute('aria-describedby') === 'player-error-message');
check('menus e trailer são diálogos modais com nome acessível',
    document.getElementById('player-menu').getAttribute('role') === 'dialog' &&
    document.getElementById('player-menu').getAttribute('aria-modal') === 'true' &&
    document.getElementById('trailer-overlay').getAttribute('role') === 'dialog' &&
    document.getElementById('trailer-overlay').getAttribute('aria-labelledby') === 'trailer-title');
check('player e trailer expõem progresso numérico',
    document.getElementById('player-timeline').getAttribute('role') === 'progressbar' &&
    document.getElementById('player-timeline').getAttribute('aria-valuemin') === '0' &&
    document.getElementById('player-timeline').getAttribute('aria-valuemax') === '100' &&
    document.getElementById('trailer-timeline').getAttribute('role') === 'progressbar');
check('player oferece uma barra de controles utilizável sem teclas coloridas',
    document.getElementById('player-action-bar').getAttribute('role') === 'toolbar' &&
    document.querySelectorAll('#player-action-bar [data-player-action]').length >= 9 &&
    Boolean(document.querySelector('[data-player-action="play-pause"]')) &&
    Boolean(document.querySelector('[data-player-action="audio"]')) &&
    Boolean(document.querySelector('[data-player-action="subtitles"]')));
check('cores do controle são aceleradores e cada ação conserva texto próprio',
    Array.prototype.every.call(document.querySelectorAll('.player-action-button.tone-red, .player-action-button.tone-green, .player-action-button.tone-yellow, .player-action-button.tone-blue'), function (button) {
        return Boolean(button.getAttribute('data-player-action'));
    }));
check('iframe do trailer tem título e decoração do boot fica fora da árvore acessível',
    Boolean(document.getElementById('trailer-frame').getAttribute('title')) &&
    document.querySelector('.boot-backdrop').getAttribute('aria-hidden') === 'true' &&
    document.querySelector('.boot-dots').getAttribute('aria-hidden') === 'true');
check('boot é uma região atômica com progresso numérico nomeado',
    document.querySelector('.boot-panel').getAttribute('role') === 'status' &&
    document.querySelector('.boot-panel').getAttribute('aria-live') === 'polite' &&
    document.querySelector('.boot-panel').getAttribute('aria-atomic') === 'true' &&
    document.querySelector('.boot-progress').getAttribute('role') === 'progressbar' &&
    Boolean(document.querySelector('.boot-progress').getAttribute('aria-label')));

process.stdout.write('Contraste, foco e alvos\n');
check('texto principal, secundário e discreto passam 4,5:1 nas superfícies padrão',
    contrast('#F4F1EA', '#08090A') >= 4.5 &&
    contrast('#B8B4AC', '#111214') >= 4.5 &&
    contrast('#85827C', '#111214') >= 4.5);
check('ação primária e mensagens de erro passam 4,5:1',
    contrast('#111319', '#D6A956') >= 4.5 && contrast('#FF6B6B', '#111214') >= 4.5);
check('foco possui borda clara adicional e modo de alto contraste dedicado',
    /\.focusable\.focused\s*\{[^}]*border-color:\s*var\(--ivory\)/s.test(css) &&
    /body\.high-contrast\s*\{[^}]*--text:\s*#fff/s.test(css));
check('movimento reduzido elimina transição e escala do foco',
    /body\.reduced-motion \.focusable\s*\{[^}]*transition:\s*none/s.test(css) &&
    /body\.reduced-motion \.focusable\.focused\s*\{[^}]*transform:\s*none/s.test(css) &&
    /body\.reduced-motion \.player-action-button\.focused\s*\{[^}]*transform:\s*none/s.test(css));
check('movimento reduzido também desliga animações do boot',
    /body\.reduced-motion \.boot-indicator\s*\{[^}]*animation:\s*none/s.test(css) &&
    /body\.reduced-motion \.boot-mark\s*\{[^}]*animation:\s*none/s.test(css));
check('movimento reduzido também desliga a entrada da carta de Descobrir',
    /body\.reduced-motion \.discover-card\.current\s*\{[^}]*animation:\s*none/s.test(css));
check('movimento reduzido também desliga placeholder e revelação das capas',
    /body\.reduced-motion \.buro-image-frame[^{]*\{[^}]*animation:\s*none;\s*transition:\s*none/s.test(css) &&
    /body\.reduced-motion \.buro-progressive-image[^{]*\{[^}]*animation:\s*none;\s*transition:\s*none/s.test(css));
check('controles compactos continuam acima do alvo mínimo de 44 px',
    /\.cast-chip\s*\{[^}]*min-height:\s*46px/s.test(css) &&
    /\.filter-chip\s*\{[^}]*min-height:\s*54px/s.test(css) &&
    /\.subtitle-choice\s*\{[^}]*height:\s*70px/s.test(css) &&
    /\.episode-download-action\s*\{[^}]*min-height:\s*46px/s.test(css) &&
    /\.discover-action\s*\{[^}]*min-width:\s*176px;\s*min-height:\s*70px/s.test(css) &&
    /\.player-action-button\s*\{[^}]*height:\s*56px/s.test(css) &&
    /\.button\s*\{[^}]*min-height:\s*64px/s.test(css));

if (failures.length) {
    process.stdout.write('\n' + failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
    process.exitCode = 1;
} else { process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n'); }
