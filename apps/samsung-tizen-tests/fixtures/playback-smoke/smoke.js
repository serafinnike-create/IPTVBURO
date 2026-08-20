/* Public, test-only AVPlay smoke. Never included in the production WGT. */
(function () {
    'use strict';

    var PUBLIC_HLS_FIXTURE = 'https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8';
    var REPORT_ENDPOINT = 'http://10.0.2.2:43127/iptvburo.avplay-smoke';
    var STORAGE_KEY = 'iptvburo.avplay-smoke';
    var result = {
        state: 'BOOTING',
        sawPreparing: false,
        sawPlaying: false,
        sawPaused: false,
        sawResumed: false,
        sawSeekForward: false,
        sawSeekBack: false,
        sawFastForward: false,
        sawRewind: false,
        sawAudioSelected: false,
        sawSubtitleSelected: false,
        audioTrackCount: 0,
        subtitleTrackCount: 0,
        seekBaselineMs: 0,
        seekForwardInputMs: 0,
        seekBackInputMs: 0,
        fastForwardInputMs: 0,
        rewindInputMs: 0,
        seekForwardPositionMs: 0,
        seekBackPositionMs: 0,
        fastForwardPositionMs: 0,
        currentPositionMs: 0,
        maxPositionMs: 0,
        error: ''
    };
    var reportedReady = false;
    var reportedSeekForward = false;
    var reportedSeekBack = false;
    var reportedFastForward = false;
    var reportedPass = false;
    var trackSelectionStarted = false;
    var targetSubtitleIndex = null;
    var reportImages = [];
    var lastRemoteAction = '';

    function byId(id) { return document.getElementById(id); }

    function persist() {
        try { localStorage.setItem('iptvburo.avplay-smoke', JSON.stringify(result)); }
        catch (ignoredStorage) { /* External report remains authoritative. */ }
    }

    function report(outcome) {
        var image = new Image();
        var query = '?result=' + encodeURIComponent(outcome) +
            '&state=' + encodeURIComponent(result.state) +
            '&position=' + encodeURIComponent(result.currentPositionMs) +
            '&maxPosition=' + encodeURIComponent(result.maxPositionMs) +
            '&audioTracks=' + encodeURIComponent(result.audioTrackCount) +
            '&subtitleTracks=' + encodeURIComponent(result.subtitleTrackCount) +
            '&error=' + encodeURIComponent(result.error);
        reportImages.push(image);
        if (reportImages.length > 8) { reportImages.shift(); }
        image.src = REPORT_ENDPOINT + query + '&nonce=' + String(Date.now());
    }

    function render(message) {
        document.body.setAttribute('data-smoke-state', result.state);
        byId('state').textContent = result.state;
        byId('position').textContent = String(result.currentPositionMs) + ' ms';
        byId('result').textContent = message || 'Validando reprodução…';
        persist();
    }

    function selectNativeTracks() {
        var audioTracks;
        var subtitleTracks;
        if (trackSelectionStarted) { return; }
        audioTracks = BuroPlayer.trackOptions('AUDIO');
        subtitleTracks = BuroPlayer.trackOptions('TEXT');
        result.audioTrackCount = audioTracks.length;
        result.subtitleTrackCount = subtitleTracks.length;
        if (audioTracks.length < 2 || subtitleTracks.length < 2) {
            fail({ code: 'TRACKS_MISSING_A' + audioTracks.length + '_T' + subtitleTracks.length });
            return;
        }
        trackSelectionStarted = true;
        targetSubtitleIndex = subtitleTracks[1].index;
        result.state = 'TRACKS_ENUMERATED';
        render('Native tracks enumerated; selecting alternate audio.');
        report('AVPLAY_SMOKE_TRACKS_ENUMERATED');
        if (!BuroPlayer.selectTrack('AUDIO', audioTracks[1].index)) {
            fail({ code: 'AUDIO_SELECT_FAILED' });
        }
    }

    function passIfReady() {
        if (!reportedReady && result.sawPreparing && result.sawPlaying && result.maxPositionMs >= 1000) {
            reportedReady = true;
            result.seekBaselineMs = result.maxPositionMs;
            result.state = 'READY';
            render('Envie Play/Pause pelo controle.');
            report('AVPLAY_SMOKE_READY');
            return;
        }
        if (!reportedSeekForward && result.sawSeekForward &&
                result.currentPositionMs >= result.seekForwardInputMs + 15000 &&
                result.currentPositionMs >= result.seekBaselineMs + 15000 &&
                result.maxPositionMs >= result.seekBaselineMs + 15000) {
            reportedSeekForward = true;
            result.seekForwardPositionMs = result.currentPositionMs;
            result.state = 'SEEK_FORWARD_CONFIRMED';
            render('Forward seek confirmed; send Left.');
            report('AVPLAY_SMOKE_SEEK_FORWARD');
            return;
        }
        if (!reportedSeekBack && reportedSeekForward && result.sawSeekBack &&
                result.currentPositionMs <= result.seekBackInputMs - 5000) {
            reportedSeekBack = true;
            result.seekBackPositionMs = result.currentPositionMs;
            result.state = 'SEEK_BACK_CONFIRMED';
            render('Backward seek confirmed; send Fast Forward.');
            report('AVPLAY_SMOKE_SEEK_BACK');
            return;
        }
        if (!reportedFastForward && reportedSeekBack && result.sawFastForward &&
                result.currentPositionMs >= result.fastForwardInputMs + 15000) {
            reportedFastForward = true;
            result.fastForwardPositionMs = result.currentPositionMs;
            result.state = 'FAST_FORWARD_CONFIRMED';
            render('Fast Forward confirmed; send Rewind.');
            report('AVPLAY_SMOKE_FAST_FORWARD');
            return;
        }
        if (!trackSelectionStarted && reportedReady && result.sawPaused && result.sawResumed &&
                reportedSeekForward && reportedSeekBack && reportedFastForward && result.sawRewind &&
                result.currentPositionMs <= result.rewindInputMs - 5000) {
            selectNativeTracks();
            return;
        }
        if (!reportedPass && trackSelectionStarted && result.sawAudioSelected && result.sawSubtitleSelected) {
            reportedPass = true;
            result.state = 'PASS';
            render('AVPlay pausou, retomou e avançou pelo controle.');
            report('AVPLAY_SMOKE_PASS');
        }
    }

    function onRemoteKey(event) {
        var K = BuroKeys.CODES;
        if (event.keyCode === K.PLAY_PAUSE || event.keyCode === K.PLAY || event.keyCode === K.PAUSE) {
            BuroPlayer.togglePause();
            event.preventDefault();
        } else if (event.keyCode === K.LEFT || event.keyCode === K.REWIND) {
            lastRemoteAction = event.keyCode === K.REWIND ? 'REWIND' : 'LEFT';
            result[lastRemoteAction === 'REWIND' ? 'rewindInputMs' : 'seekBackInputMs'] = result.currentPositionMs;
            BuroPlayer.seekBy(-10000);
            event.preventDefault();
        } else if (event.keyCode === K.RIGHT || event.keyCode === K.FAST_FORWARD) {
            lastRemoteAction = event.keyCode === K.FAST_FORWARD ? 'FAST_FORWARD' : 'RIGHT';
            result[lastRemoteAction === 'FAST_FORWARD' ? 'fastForwardInputMs' : 'seekForwardInputMs'] = result.currentPositionMs;
            BuroPlayer.seekBy(30000);
            event.preventDefault();
        }
    }

    function fail(error) {
        result.state = 'FAIL';
        result.error = String(error && error.code || 'PLAYBACK_UNKNOWN').substring(0, 80);
        render('Falha nativa: ' + result.error);
        report('AVPLAY_SMOKE_FAIL');
    }

    function start() {
        BuroPlayer.setListeners({
            onStatus: function (status) {
                result.state = String(status || 'UNKNOWN').substring(0, 40);
                if (status === 'PREPARING') { result.sawPreparing = true; }
                if (status === 'PAUSED') {
                    result.sawPaused = true;
                    report('AVPLAY_SMOKE_PAUSED');
                }
                if (status === 'PLAYING') {
                    result.sawPlaying = true;
                    if (result.sawPaused && !result.sawResumed) {
                        result.sawResumed = true;
                        report('AVPLAY_SMOKE_RESUMED');
                    }
                }
                if (status === 'SEEK_FORWARD') {
                    if (lastRemoteAction === 'FAST_FORWARD') { result.sawFastForward = true; }
                    else if (lastRemoteAction === 'RIGHT') { result.sawSeekForward = true; }
                    lastRemoteAction = '';
                }
                if (status === 'SEEK_BACK') {
                    if (lastRemoteAction === 'REWIND') { result.sawRewind = true; }
                    else if (lastRemoteAction === 'LEFT') { result.sawSeekBack = true; }
                    lastRemoteAction = '';
                }
                if (status === 'AUDIO_SELECTED' && trackSelectionStarted && !result.sawAudioSelected) {
                    result.sawAudioSelected = true;
                    report('AVPLAY_SMOKE_AUDIO_SELECTED');
                    if (!BuroPlayer.selectTrack('TEXT', targetSubtitleIndex)) {
                        fail({ code: 'SUBTITLE_SELECT_FAILED' });
                        return;
                    }
                }
                if (status === 'SUBTITLE_SELECTED' && trackSelectionStarted) {
                    result.sawSubtitleSelected = true;
                    report('AVPLAY_SMOKE_SUBTITLE_SELECTED');
                }
                render();
                report('AVPLAY_SMOKE_PROGRESS');
                passIfReady();
            },
            onTime: function (positionMs) {
                result.currentPositionMs = Math.max(0, Number(positionMs) || 0);
                result.maxPositionMs = Math.max(result.maxPositionMs, result.currentPositionMs);
                render();
                passIfReady();
            },
            onError: fail,
            onComplete: function () {
                result.state = 'ENDED';
                render('Stream concluído antes da prova de progresso.');
                if (!reportedPass) { report('AVPLAY_SMOKE_FAIL'); }
            }
        });
        BuroKeys.registerMediaKeys();
        document.addEventListener('keydown', onRemoteKey);
        render('Abrindo fixture pública autorizada…');
        BuroPlayer.play(PUBLIC_HLS_FIXTURE, 0);
    }

    window.addEventListener('load', start);
    window.addEventListener('unload', function () {
        document.removeEventListener('keydown', onRemoteKey);
        BuroPlayer.stop();
    });
}());
