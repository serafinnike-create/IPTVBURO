/* Contratos do trailer incorporado. Usa somente IDs sintéticos. */
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

function wait(milliseconds) {
    return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
}

async function run() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var dom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var frame = window.document.getElementById('trailer-frame');
    var overlay = window.document.getElementById('trailer-overlay');
    var commands = [];
    var safeId = 'AbCdEf12345';

    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/trailer.js'), 'utf8'));
    window.BuroTrailer.init();

    process.stdout.write('Saneamento do identificador YouTube\n');
    check('aceita ID público simples', window.BuroTrailer.sanitize(safeId) === safeId);
    check('aceita youtu.be e remove parâmetros',
        window.BuroTrailer.sanitize('https://youtu.be/' + safeId + '?t=4') === safeId);
    check('aceita watch e shorts em hosts oficiais',
        window.BuroTrailer.sanitize('https://www.youtube.com/watch?v=' + safeId) === safeId &&
        window.BuroTrailer.sanitize('https://m.youtube.com/shorts/' + safeId) === safeId);
    check('rejeita host parecido, esquema ativo e ID inválido',
        window.BuroTrailer.sanitize('https://www.youtube.com.evil.test/watch?v=' + safeId) === null &&
        window.BuroTrailer.sanitize('javascript:alert(1)') === null &&
        window.BuroTrailer.sanitize('short') === null);

    process.stdout.write('Player incorporado e isolado\n');
    check('API fica disponível somente após encontrar o iframe', window.BuroTrailer.available());
    check('referência inválida não abre o overlay', !window.BuroTrailer.open('https://evil.test/video', 'Inválido', {}));
    check('trailer válido abre em domínio sem cookies', window.BuroTrailer.open(safeId, 'Filme sintético', {
        title: 'Trailer', loading: 'Carregando', playing: 'Reproduzindo',
        playingMuted: 'Sem som', paused: 'Pausado', ended: 'Fim', error: 'Indisponível', hint: 'Ajuda'
    }) && !overlay.hidden && frame.src.indexOf('https://www.youtube-nocookie.com/embed/' + safeId) === 0);
    check('URL do iframe não contém segredo nem referência fornecida pelo usuário',
        frame.src.indexOf('evil.test') === -1 && frame.src.indexOf('username') === -1 &&
        frame.src.indexOf('password') === -1);
    check('trailer não grava estado no armazenamento local', window.localStorage.length === 0);

    frame.contentWindow.postMessage = function (payload, origin) {
        commands.push({ payload: JSON.parse(payload), origin: origin });
    };
    frame.dispatchEvent(new window.Event('load'));
    await wait(500);
    check('handshake e reprodução usam sempre a origem exata sem cookies',
        commands.length >= 5 && commands.every(function (entry) {
            return entry.origin === 'https://www.youtube-nocookie.com';
        }) && commands.some(function (entry) { return entry.payload.event === 'listening'; }) &&
        commands.some(function (entry) { return entry.payload.func === 'playVideo'; }));

    window.dispatchEvent(new window.MessageEvent('message', {
        origin: 'https://www.youtube-nocookie.com', source: frame.contentWindow,
        data: JSON.stringify({ event: 'infoDelivery', info: { currentTime: 20, duration: 100, playerState: 1 } })
    }));
    check('mensagem confiável atualiza status, tempo e progresso',
        window.document.getElementById('trailer-status').textContent === 'Sem som' &&
        window.document.getElementById('trailer-elapsed').textContent === '00:20' &&
        parseFloat(window.document.getElementById('trailer-progress').style.width) === 20);

    window.dispatchEvent(new window.MessageEvent('message', {
        origin: 'https://attacker.test', source: frame.contentWindow,
        data: JSON.stringify({ event: 'infoDelivery', info: { currentTime: 99, duration: 100, playerState: 2 } })
    }));
    check('mensagem de origem não confiável é ignorada',
        window.document.getElementById('trailer-elapsed').textContent === '00:20');

    window.BuroTrailer.toggleMute();
    window.BuroTrailer.togglePlayback();
    window.BuroTrailer.seekBy(10000);
    check('controles D-pad geram áudio, pausa e avanço no iframe',
        commands.some(function (entry) { return entry.payload.func === 'unMute'; }) &&
        commands.some(function (entry) { return entry.payload.func === 'pauseVideo'; }) &&
        commands.some(function (entry) {
            return entry.payload.func === 'seekTo' && entry.payload.args[0] === 30;
        }));

    process.stdout.write('Trailers automaticos do banner e Descobrir\n');
    var background = window.document.createElement('iframe');
    var backgroundStage = window.document.createElement('span');
    var backgroundHero = window.document.createElement('section');
    backgroundHero.className = 'real-home-hero';
    backgroundStage.className = 'hero-trailer-stage';
    background.className = 'hero-trailer';
    background.setAttribute('data-trailer-item-id', 'synthetic-item');
    backgroundStage.appendChild(background);
    backgroundHero.appendChild(backgroundStage);
    window.document.body.appendChild(backgroundHero);
    var failedItem = null;
    var playingItem = null;
    window.BuroTrailer.observeBackgroundFrames(function (itemId) { failedItem = itemId; }, function (itemId) { playingItem = itemId; });
    window.dispatchEvent(new window.MessageEvent('message', {
        origin: 'https://www.youtube-nocookie.com', source: background.contentWindow,
        data: JSON.stringify({ event: 'onStateChange', info: 1 })
    }));
    await wait(3100);
    check('PLAYING revela o iframe somente depois do tempo de assentamento',
        background.classList.contains('trailer-ready') &&
        !background.classList.contains('trailer-failed') &&
        backgroundStage.classList.contains('trailer-ready') &&
        backgroundHero.classList.contains('hero-trailer-playing') &&
        playingItem === 'synthetic-item');

    var failingBackground = window.document.createElement('iframe');
    failingBackground.className = 'discover-trailer';
    failingBackground.setAttribute('data-trailer-item-id', 'synthetic-failure');
    window.document.body.appendChild(failingBackground);
    window.BuroTrailer.observeBackgroundFrames(function (itemId) { failedItem = itemId; });
    window.dispatchEvent(new window.MessageEvent('message', {
        origin: 'https://www.youtube-nocookie.com', source: failingBackground.contentWindow,
        data: JSON.stringify({ event: 'onError', info: 150 })
    }));
    check('erro mantem a capa e memoriza o item que falhou',
        failingBackground.classList.contains('trailer-failed') &&
        failingBackground.src === 'about:blank' && failedItem === 'synthetic-failure');

    window.dispatchEvent(new window.MessageEvent('message', {
        origin: 'https://www.youtube-nocookie.com', source: frame.contentWindow,
        data: JSON.stringify({ event: 'onError', info: 150 })
    }));
    check('erro do provedor vira mensagem controlada',
        window.document.getElementById('trailer-status').textContent === 'Indisponível');
    check('fechar destrói a mídia e mantém os detalhes fora do armazenamento',
        window.BuroTrailer.close() && overlay.hidden && frame.src === 'about:blank' &&
        !window.BuroTrailer.isOpen() && window.localStorage.length === 0);

    dom.window.close();

    process.stdout.write('Fallback de widget Tizen sem origem HTTP\n');
    var fileDom = new JSDOM(html, {
        runScripts: 'outside-only', pretendToBeVisual: true, url: 'file:///opt/usr/apps/IPTVBURO/index.html'
    });
    var fileWindow = fileDom.window;
    fileWindow.eval(fs.readFileSync(path.join(APP_DIR, 'js/domain.js'), 'utf8'));
    fileWindow.eval(fs.readFileSync(path.join(APP_DIR, 'js/trailer.js'), 'utf8'));
    fileWindow.BuroTrailer.init();
    check('widget file continua anunciando trailer disponível', fileWindow.BuroTrailer.available());
    check('widget abre o embed com controles próprios e ajuda honesta',
        fileWindow.BuroTrailer.open(safeId, 'Filme sintético', {
            title: 'Trailer', loading: 'Carregando', hint: 'Teclas da API',
            fallbackHint: 'Use os controles do vídeo · RETURN voltar'
        }) &&
        fileWindow.document.getElementById('trailer-frame').src.indexOf('controls=1') >= 0 &&
        fileWindow.document.getElementById('trailer-frame').src.indexOf('enablejsapi=1') < 0 &&
        fileWindow.document.getElementById('trailer-hint').textContent ===
            'Use os controles do vídeo · RETURN voltar');
    fileWindow.BuroTrailer.close();
    fileDom.window.close();

    process.stdout.write('\n' + passed + ' verificações aprovadas.\n');
    if (failures.length) {
        process.stderr.write(failures.length + ' falha(s): ' + failures.join('; ') + '\n');
        process.exitCode = 1;
    }
}

run().catch(function (error) {
    process.stderr.write((error && error.stack) || String(error));
    process.exitCode = 1;
});
