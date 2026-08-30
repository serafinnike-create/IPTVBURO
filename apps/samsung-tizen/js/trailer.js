/* Trailer YouTube incorporado, isolado do catálogo e sem persistência. */
var BuroTrailer = (function () {
    'use strict';

    var TRUSTED_ORIGINS = {
        'https://www.youtube-nocookie.com': true,
        'https://www.youtube.com': true
    };
    var overlay;
    var appRoot;
    var frame;
    var titleLabel;
    var statusLabel;
    var hintLabel;
    var progress;
    var elapsed;
    var durationLabel;
    var initialized = false;
    var opened = false;
    var currentId = null;
    var playerState = -1;
    var currentSeconds = 0;
    var durationSeconds = 0;
    var muted = true;
    var copy = {};
    var readyTimer = null;
    var failureTimer = null;

    function clean(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /* Equivalente ao adapter Android: somente ID, youtu.be e hosts YouTube. */
    function sanitize(reference) {
        return BuroDomain.sanitizeYouTubeReference(reference);
    }

    /*
      A origem que o IFrame API exige, quando ela existe.

      O erro 153 do YouTube e recusa por origem: com `enablejsapi=1` o player
      confere quem o incorporou, e um widget Tizen carregado de `file://` tem
      origem `null`. O parametro nunca era enviado, entao a maioria dos
      trailers falhava com "Video player configuration error".

      Onde a pagina tem uma origem de verdade — o emulador servido por http,
      um firmware que usa esquema proprio — ela e enviada e a API funciona.
      Onde nao tem, o proximo comentario explica o que acontece.
    */
    function pageOrigin() {
        var origin;
        if (typeof window === 'undefined' || !window.location) { return null; }
        origin = window.location.origin || '';
        /* `file://` e `about:` produzem "null" como texto, ou vazio. Nenhum
           dos dois serve ao YouTube. */
        if (!origin || origin === 'null' || origin.indexOf('http') !== 0) { return null; }
        return origin;
    }

    /*
      Com origem, o embed usa a API e o overlay controla a reproducao. Sem
      origem, o embed vai sem `enablejsapi` — e ai o YouTube nao tem o que
      recusar, e o trailer toca.

      O que se perde nesse caminho e o controle por tecla: play, pause, seek
      e mudo passam a nao responder, porque nao ha canal para pedi-los. O que
      se ganha e o trailer existir. Um controle que funciona sobre um video
      que nao carrega nao vale nada, e era isso que havia antes.

      `autoplay=1&mute=1` cobre o caso sem API: o trailer comeca sozinho e em
      silencio, que e o comportamento que o overlay produzia com a API.
    */
    function embedUrl(id) {
        var origin = pageOrigin();
        var base = 'https://www.youtube-nocookie.com/embed/' + encodeURIComponent(id) +
            '?autoplay=1&mute=1&controls=' + (origin ? '0' : '1') +
            '&playsinline=1&rel=0&loop=0';
        /* Sem a API os controles do proprio YouTube ficam ligados: sao a
           unica forma de pausar o que a tecla ja nao pausa. */
        return origin ? base + '&enablejsapi=1&origin=' + encodeURIComponent(origin) : base;
    }

    /*
      O embed do banner: sem controlos, em repeticao, e calado ao arrancar.

      Separado do overlay porque a pergunta e outra. O overlay e um trailer que
      alguem pediu, com teclas para pausar; o banner e o filme a apresentar-se
      sozinho, e um controlo por cima dele so estorva.

      Calado nao por escolha: nenhum motor deixa um video arrancar sozinho com
      audio. Pedido com som (mute=0), o banner nao arrancava de todo - ficava um
      botao de play parado por cima de uma imagem, que foi exatamente o que se
      viu no Windows com o mesmo embed. Arranca em silencio e o raiseBannerSound
      levanta o som assim que ele ja esta a tocar, que e o mais perto que uma
      pagina chega de comecar com som.

      Para quando a pessoa sai do ecra inicial, porque o iframe deixa de existir
      com ele.
    */
    function bannerEmbedUrl(id) {
        var origin = pageOrigin();
        var base = 'https://www.youtube-nocookie.com/embed/' + encodeURIComponent(id) +
            '?autoplay=1&mute=1&controls=0&playsinline=1&rel=0&loop=1&playlist=' +
            encodeURIComponent(id) + '&modestbranding=1';
        return origin ? base + '&enablejsapi=1&origin=' + encodeURIComponent(origin) : base;
    }

    /*
      Levanta o som do banner depois de ele ja estar a tocar.

      O motor ja concedeu o arranque nessa altura, por isso subir o volume a
      seguir nao e um pedido novo. Tenta outra vez num temporizador alem do
      evento: o player responde quando lhe apetece, e uma tentativa unica que
      chegue cedo demais deixava o banner mudo para sempre. Para assim que
      resultar, ou ao fim de dez segundos.
    */
    function raiseBannerSound(frame) {
        if (!frame || !frame.contentWindow) { return; }
        var done = false;
        var tries = 0;

        function send(message) {
            if (!frame.contentWindow) { return; }
            try { frame.contentWindow.postMessage(JSON.stringify(message), '*'); } catch (e) {}
        }
        function listen() { send({ event: 'listening', id: 1 }); }
        function raise() {
            if (done) { return; }
            send({ event: 'command', func: 'unMute', args: [] });
            send({ event: 'command', func: 'setVolume', args: [100] });
        }

        window.addEventListener('message', function (event) {
            var data;
            try { data = JSON.parse(event.data); } catch (e) { return; }
            /* 1 e "a tocar": o arranque ja foi concedido, o som e seguro. */
            if (data && data.info && data.info.playerState === 1) { raise(); done = true; }
        });

        listen();
        /*
          O temporizador so pede o estado ao player. Nunca chama raise().

          Um unMute que chegue antes de o video estar a tocar e lido como um
          pedido para arrancar com audio, e o motor recusa-o - o que se ve e o
          botao de play do YouTube parado por cima de uma imagem, com o som ja
          ligado. Foi assim que apareceu no Windows, com este mesmo desenho.

          O som sobe num sitio so: na mensagem que diz que ja esta a tocar.
        */
        var timer = setInterval(function () {
            listen();
            tries += 1;
            if (done || tries > 20) { clearInterval(timer); }
        }, 500);
    }

    /* O overlay pergunta isto para saber se as teclas de transporte tem a
       quem falar. */
    function apiAvailable() { return pageOrigin() !== null; }

    function formatTime(value) {
        var total = Math.max(0, Math.floor(Number(value) || 0));
        var minutes = Math.floor(total / 60);
        var seconds = total % 60;
        return (minutes < 10 ? '0' : '') + minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
    }

    function updateTimeline() {
        var percent = durationSeconds > 0 ? Math.min(100, currentSeconds / durationSeconds * 100) : 0;
        if (progress) { progress.style.width = percent.toFixed(2) + '%'; }
        if (elapsed) { elapsed.textContent = formatTime(currentSeconds); }
        if (durationLabel) { durationLabel.textContent = durationSeconds > 0 ? formatTime(durationSeconds) : '--:--'; }
        if (progress && progress.parentNode) {
            progress.parentNode.setAttribute('aria-valuenow', String(Math.round(percent)));
            progress.parentNode.setAttribute('aria-valuetext', formatTime(currentSeconds) + ' / ' +
                (durationSeconds > 0 ? formatTime(durationSeconds) : '--:--'));
        }
    }

    function statusText() {
        if (playerState === -2) { return copy.error || copy.loading; }
        if (playerState === 1) { return muted ? copy.playingMuted : copy.playing; }
        if (playerState === 2) { return copy.paused; }
        if (playerState === 0) { return copy.ended; }
        if (playerState === 3) { return copy.loading; }
        return copy.loading;
    }

    function updateStatus() {
        if (statusLabel) { statusLabel.textContent = statusText() || ''; }
        updateTimeline();
    }

    function postCommand(name, args) {
        var target;
        if (!opened || !frame || !frame.contentWindow || !name) { return false; }
        target = frame.contentWindow;
        try {
            target.postMessage(JSON.stringify({ event: 'command', func: name, args: args || [] }),
                'https://www.youtube-nocookie.com');
            return true;
        } catch (ignoredPost) { return false; }
    }

    function postPayload(payload) {
        if (!opened || !frame || !frame.contentWindow) { return false; }
        try {
            frame.contentWindow.postMessage(JSON.stringify(payload), 'https://www.youtube-nocookie.com');
            return true;
        } catch (ignoredPost) { return false; }
    }

    function beginPlayback() {
        if (!opened) { return; }
        /* O protocolo do IFrame requer um listener antes dos comandos. */
        postPayload({ event: 'listening', id: 'iptvburo-trailer' });
        postCommand('addEventListener', ['onStateChange']);
        postCommand('addEventListener', ['onError']);
        postCommand('mute');
        postCommand('playVideo');
    }

    function onFrameLoad() {
        if (!opened || !currentId || frame.src === 'about:blank') { return; }
        if (readyTimer) { window.clearTimeout(readyTimer); }
        readyTimer = window.setTimeout(beginPlayback, 450);
    }

    function onMessage(event) {
        var payload;
        var info;
        if (!opened || !frame || event.source !== frame.contentWindow || !TRUSTED_ORIGINS[event.origin]) { return; }
        if (typeof event.data === 'string' && event.data.length > 16384) { return; }
        try { payload = typeof event.data === 'string' ? JSON.parse(event.data) : event.data; }
        catch (ignoredJson) { return; }
        if (!payload || typeof payload !== 'object') { return; }
        if (payload.event === 'onStateChange') {
            playerState = Number(payload.info);
        } else if (payload.event === 'onError') {
            playerState = -2;
        } else if (payload.event === 'infoDelivery' && payload.info && typeof payload.info === 'object') {
            info = payload.info;
            if (isFinite(Number(info.currentTime))) { currentSeconds = Math.max(0, Number(info.currentTime)); }
            if (isFinite(Number(info.duration))) { durationSeconds = Math.max(0, Number(info.duration)); }
            if (isFinite(Number(info.playerState))) { playerState = Number(info.playerState); }
        } else { return; }
        updateStatus();
    }

    function init() {
        if (initialized) { return; }
        overlay = document.getElementById('trailer-overlay');
        appRoot = document.getElementById('app');
        frame = document.getElementById('trailer-frame');
        titleLabel = document.getElementById('trailer-title');
        statusLabel = document.getElementById('trailer-status');
        hintLabel = document.getElementById('trailer-hint');
        progress = document.getElementById('trailer-progress');
        elapsed = document.getElementById('trailer-elapsed');
        durationLabel = document.getElementById('trailer-duration');
        if (!overlay || !frame) { return; }
        initialized = true;
        frame.addEventListener('load', onFrameLoad);
        window.addEventListener('message', onMessage);
    }

    function available() {
        /* Em widget Tizen a origem costuma ser `file://`/null. Nesse caso o
           embed sem API continua válido e mostra os controles do próprio
           YouTube; bloquear aqui tornava inalcançável justamente o fallback
           construído por `embedUrl`. */
        return initialized && Boolean(frame && frame.contentWindow && window.postMessage);
    }

    function open(reference, title, labels) {
        var id = sanitize(reference);
        if (!available() || !id) { return false; }
        copy = labels || {};
        currentId = id;
        playerState = -1;
        currentSeconds = 0;
        durationSeconds = 0;
        muted = true;
        opened = true;
        titleLabel.textContent = clean(title) || 'IPTV BURO';
        hintLabel.textContent = apiAvailable() ? (copy.hint || '') :
            (copy.fallbackHint || copy.hint || '');
        updateStatus();
        overlay.hidden = false;
        if (appRoot) { appRoot.setAttribute('aria-hidden', 'true'); }
        document.body.classList.add('trailer-playing');
        frame.title = copy.title || 'Trailer';
        frame.src = embedUrl(id);
        if (failureTimer) { window.clearTimeout(failureTimer); }
        failureTimer = window.setTimeout(function () {
            if (opened && playerState === -1) { playerState = -2; updateStatus(); }
        }, 15000);
        return true;
    }

    function close() {
        if (!opened) { return false; }
        opened = false;
        currentId = null;
        if (readyTimer) { window.clearTimeout(readyTimer); readyTimer = null; }
        if (failureTimer) { window.clearTimeout(failureTimer); failureTimer = null; }
        if (frame) { frame.src = 'about:blank'; }
        if (overlay) { overlay.hidden = true; }
        if (appRoot) { appRoot.removeAttribute('aria-hidden'); }
        document.body.classList.remove('trailer-playing');
        return true;
    }

    function togglePlayback() {
        if (!opened) { return false; }
        if (playerState === 1) { postCommand('pauseVideo'); playerState = 2; }
        else { postCommand('playVideo'); playerState = 1; }
        updateStatus();
        return true;
    }

    function seekBy(milliseconds) {
        var target;
        if (!opened) { return false; }
        target = Math.max(0, currentSeconds + (Number(milliseconds) || 0) / 1000);
        if (durationSeconds > 0) { target = Math.min(durationSeconds, target); }
        currentSeconds = target;
        postCommand('seekTo', [target, true]);
        updateTimeline();
        return true;
    }

    function toggleMute() {
        if (!opened) { return false; }
        muted = !muted;
        postCommand(muted ? 'mute' : 'unMute');
        updateStatus();
        return true;
    }

    function isOpen() { return opened; }
    function currentVideoId() { return currentId; }

    return {
        init: init,
        available: available,
        apiAvailable: apiAvailable,
        open: open,
        close: close,
        togglePlayback: togglePlayback,
        seekBy: seekBy,
        toggleMute: toggleMute,
        isOpen: isOpen,
        sanitize: sanitize,
        bannerEmbedUrl: bannerEmbedUrl,
        raiseBannerSound: raiseBannerSound,
        currentVideoId: currentVideoId
    };
}());
