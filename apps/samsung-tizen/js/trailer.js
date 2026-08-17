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

    function embedUrl(id) {
        return 'https://www.youtube-nocookie.com/embed/' + encodeURIComponent(id) +
            '?autoplay=1&mute=1&controls=0&playsinline=1&rel=0&loop=0&enablejsapi=1';
    }

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
        hintLabel.textContent = copy.hint || '';
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
        open: open,
        close: close,
        togglePlayback: togglePlayback,
        seekBy: seekBy,
        toggleMute: toggleMute,
        isOpen: isOpen,
        sanitize: sanitize,
        currentVideoId: currentVideoId
    };
}());
