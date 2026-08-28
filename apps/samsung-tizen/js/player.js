/* AVPlay state adapter. Playback URLs stay only in AVPlay memory. */
var BuroPlayer = (function () {
    'use strict';

    var listeners = {
        onStatus: function () {}, onError: function () {}, onTime: function () {},
        onSubtitle: function () {}, onComplete: function () {}
    };
    var phase = 'IDLE';
    var session = 0;

    /*
      Os mesmos numeros do Windows e do Android, de proposito: uma televisao que
      aguentasse uma queda de ligacao pior do que o computador seria o produto a
      discutir consigo proprio.
    */
    var ON_DEMAND_BUFFER_MS = 120000;
    var LIVE_BUFFER_MS = 1500;
    /* Quanto tem de chegar antes de a imagem comecar. Pequeno de proposito: o
       buffer deve encher por tras de uma imagem que ja esta no ar, e nao antes. */
    var PLAY_BUFFER_MS = 2000;
    var audioTrackPosition = -1;
    var subtitleTrackPosition = -1;
    var subtitlesSilent = false;
    var playbackRate = 1;
    var displayMode = 'LETTER_BOX';
    var DISPLAY_MODES = [
        { id: 'LETTER_BOX', api: 'PLAYER_DISPLAY_MODE_LETTER_BOX' },
        { id: 'FULL_SCREEN', api: 'PLAYER_DISPLAY_MODE_FULL_SCREEN' },
        { id: 'AUTO_ASPECT_RATIO', api: 'PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO' }
    ];

    function available() {
        return typeof webapis !== 'undefined' && Boolean(webapis.avplay);
    }

    var subtitleOffsetMs = 0;

    function styledSubtitlesAvailable() {
        return available() && typeof webapis.avplay.setSilentSubtitle === 'function';
    }

    function clearSubtitleCue() { listeners.onSubtitle('', 0); }

    /*
      O atraso da legenda, em milissegundos.

      Numa lista IPTV a legenda dessincroniza do audio com frequencia: o
      provedor remuxa o arquivo e o offset embutido deixa de valer. Sem ajuste a
      unica saida e desligar a legenda.

      setSubtitlePosition existe em parte dos firmwares e resolve na fonte,
      antes de o texto chegar. Onde nao existe, subtitleOffsetMs guarda o
      valor e quem desenha a legenda o aplica — a que chega adiantada espera, e
      a atrasada aparece de imediato porque o tempo dela ja passou.

      Nao ha equivalente para o audio: o AVPlay nao expoe atraso de faixa, e
      fingir um controle que nao funciona seria pior do que nao ter nenhum.
    */
    function subtitleOffsetAvailable() {
        return available() && typeof webapis.avplay.setSubtitlePosition === 'function';
    }

    function setSubtitleOffset(milliseconds) {
        var value = Math.max(-10000, Math.min(10000, Number(milliseconds) || 0));
        subtitleOffsetMs = value;
        if (subtitleOffsetAvailable()) {
            try { webapis.avplay.setSubtitlePosition(value); }
            catch (ignoredOffset) { /* Fica o atraso aplicado no desenho. */ }
        }
        return value;
    }

    function subtitleOffset() { return subtitleOffsetMs; }

    function status(code, value) { listeners.onStatus(code, value); }

    function safeClose() {
        var current;
        clearSubtitleCue();
        if (!available()) { phase = 'IDLE'; return; }
        try {
            current = webapis.avplay.getState ? webapis.avplay.getState() : phase;
            if (current === 'PLAYING' || current === 'PAUSED' || current === 'READY') {
                webapis.avplay.stop();
            }
        } catch (ignoredStop) { /* close abaixo continua sendo necessário. */ }
        try { webapis.avplay.close(); } catch (ignoredClose) { /* Já estava fechado. */ }
        phase = 'IDLE';
    }

    function fail(error) {
        safeClose();
        listeners.onError(error && error.code ? error : { code: 'PLAYBACK_UNKNOWN' });
    }

    function classifyFailure(error) {
        var value = String(error && (error.name || error.message || error) || '').toLowerCase();
        if (/invalidaccess|no[_\s-]*such[_\s-]*file|not[_\s-]*found|\b(401|403|404|410)\b/.test(value)) {
            return { code: 'PLAYBACK_SOURCE_UNAVAILABLE' };
        }
        if (/network|connection|timeout|ioerror/.test(value)) { return { code: 'PLAYBACK_CONNECTION' }; }
        if (/codec|decoder|format|unsupported|not[_\s-]*supported/.test(value)) { return { code: 'PLAYBACK_UNSUPPORTED' }; }
        return { code: 'PLAYBACK_UNKNOWN' };
    }

    function displayModeAvailable() {
        return available() && typeof webapis.avplay.setDisplayMethod === 'function';
    }

    function displayModeEntry(id) {
        var result = DISPLAY_MODES[0];
        DISPLAY_MODES.some(function (entry) {
            if (entry.id === id) { result = entry; return true; }
            return false;
        });
        return result;
    }

    function applyDisplayMode(id) {
        var entry = displayModeEntry(id);
        if (!displayModeAvailable()) { return false; }
        try {
            webapis.avplay.setDisplayMethod(entry.api);
            displayMode = entry.id;
            status('DISPLAY_MODE', displayMode);
            return true;
        } catch (ignoredDisplayMode) {
            status('UNAVAILABLE');
            return false;
        }
    }

    function cycleDisplayMode() {
        var index;
        if (!displayModeAvailable() || (phase !== 'OPEN' && phase !== 'PLAYING' && phase !== 'PAUSED')) { return false; }
        index = DISPLAY_MODES.map(function (entry) { return entry.id; }).indexOf(displayMode);
        return applyDisplayMode(DISPLAY_MODES[(index + 1) % DISPLAY_MODES.length].id);
    }

    function callbacks(token) {
        return {
            onbufferingstart: function () { if (token === session) { status('BUFFERING'); } },
            onbufferingprogress: function (percent) {
                if (token === session) { status('BUFFERING', Math.round(Number(percent) || 0)); }
            },
            onbufferingcomplete: function () { if (token === session) { status('PLAYING'); } },
            oncurrentplaytime: function (time) {
                var duration = 0;
                if (token !== session) { return; }
                try { duration = Number(webapis.avplay.getDuration()) || 0; } catch (ignoredDuration) {}
                listeners.onTime(Number(time) || 0, duration);
            },
            onevent: function () {},
            onsubtitlechange: function (duration, value) {
                if (token === session && !subtitlesSilent) {
                    listeners.onSubtitle(String(value || ''), Math.max(0, Number(duration) || 0));
                }
            },
            ondrmevent: function () {},
            onstreamcompleted: function () {
                if (token === session) { clearSubtitleCue(); status('ENDED'); listeners.onComplete(); safeClose(); }
            },
            onerror: function (error) {
                if (token === session) {
                    fail(classifyFailure(error));
                }
            }
        };
    }

    /*
      Quanto o leitor le a frente da imagem.

      Um filme e um ficheiro: o leitor pode estar dois minutos a frente e nem dar
      por uma ligacao que cai e volta — que e a falha mais visivel deste aplicativo.
      Um canal ao vivo nao tem "a frente" para ler, entao o mesmo buffer nao compra
      nada e custa um arranque mais tardio e uma imagem atrasada.

      A API pode nao existir em televisoes mais antigas, e isso nao pode impedir a
      reproducao: sem ela o leitor fica exatamente como estava.
    */
    function applyReadAhead(isLive) {
        var millis = isLive ? LIVE_BUFFER_MS : ON_DEMAND_BUFFER_MS;
        try {
            if (webapis.avplay.setBufferingParam) {
                webapis.avplay.setBufferingParam('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_TIME', PLAY_BUFFER_MS);
                webapis.avplay.setBufferingParam('PLAYER_BUFFER_FOR_RESUME', 'PLAYER_BUFFER_SIZE_IN_TIME', millis);
            }
        } catch (ignore) {
            /* Uma televisao sem esta API reproduz na mesma, com o buffer que ja tinha. */
        }
    }

    function play(url, startPositionMs, isLive) {
        var token;
        var initialPosition = Math.max(0, Number(startPositionMs) || 0);
        if (!available()) {
            fail({ code: 'PLAYBACK_UNSUPPORTED' });
            return;
        }
        session += 1;
        token = session;
        audioTrackPosition = -1;
        subtitleTrackPosition = -1;
        subtitlesSilent = false;
        playbackRate = 1;
        displayMode = 'LETTER_BOX';
        safeClose();
        try {
            webapis.avplay.open(url);
            phase = 'OPEN';
            webapis.avplay.setListener(callbacks(token));
            webapis.avplay.setDisplayRect(0, 0, 1920, 1080);
            if (displayModeAvailable()) { applyDisplayMode(displayMode); }
            /* Antes de preparar: o buffer tem de estar definido quando o fluxo abre. */
            applyReadAhead(isLive !== false);
            status('PREPARING');
            webapis.avplay.prepareAsync(function () {
                var started = false;
                function startPrepared() {
                    if (started || token !== session) { return; }
                    started = true;
                    try {
                        /*
                          Silent subtitle mode keeps AVPlay's native glyph layer hidden while
                          preserving onsubtitlechange events. The shell can then apply the same
                          size/colour/background preferences as Android without parsing the stream.
                        */
                        if (styledSubtitlesAvailable()) { webapis.avplay.setSilentSubtitle(true); }
                        webapis.avplay.play();
                        phase = 'PLAYING';
                        status('PLAYING');
                    } catch (error) { fail(classifyFailure(error)); }
                }
                if (token !== session) { return; }
                if (initialPosition > 0 && typeof webapis.avplay.seekTo === 'function') {
                    status('RESUMING');
                    try { webapis.avplay.seekTo(initialPosition, startPrepared, startPrepared); }
                    catch (ignoredInitialSeek) { startPrepared(); }
                } else { startPrepared(); }
            }, function (error) {
                if (token === session) { fail(classifyFailure(error)); }
            });
        } catch (error) { fail(classifyFailure(error)); }
    }

    function stop() {
        session += 1;
        safeClose();
    }

    function pause() {
        if (!available() || phase !== 'PLAYING') { return; }
        try { webapis.avplay.pause(); phase = 'PAUSED'; status('PAUSED'); }
        catch (error) { fail(classifyFailure(error)); }
    }

    function resume() {
        if (!available() || phase !== 'PAUSED') { return; }
        try { webapis.avplay.play(); phase = 'PLAYING'; status('PLAYING'); }
        catch (error) { fail(classifyFailure(error)); }
    }

    function togglePause() {
        if (phase === 'PLAYING') { pause(); }
        else if (phase === 'PAUSED') { resume(); }
    }

    function seekBy(offsetMs) {
        var amount = Math.abs(Number(offsetMs) || 0);
        if (!available() || (phase !== 'PLAYING' && phase !== 'PAUSED') || !amount) { return; }
        try {
            if (offsetMs > 0 && webapis.avplay.jumpForward) {
                webapis.avplay.jumpForward(amount, function () { status('SEEK_FORWARD'); }, function () {});
            } else if (offsetMs < 0 && webapis.avplay.jumpBackward) {
                webapis.avplay.jumpBackward(amount, function () { status('SEEK_BACK'); }, function () {});
            }
        } catch (ignoredSeek) { status('UNAVAILABLE'); }
    }

    function tracksOf(type) {
        var tracks = [];
        if (!available() || !webapis.avplay.getTotalTrackInfo) { return tracks; }
        try {
            tracks = webapis.avplay.getTotalTrackInfo().filter(function (track) { return track.type === type; });
        } catch (ignoredTracks) { tracks = []; }
        return tracks;
    }

    function trackLabel(track, position) {
        var info = {};
        var label;
        try { info = track.extra_info ? JSON.parse(track.extra_info) : {}; } catch (ignoredInfo) { info = {}; }
        label = info.track_lang || info.language || info.lang || info.title || info.name;
        label = String(label || '').replace(/^\s+|\s+$/g, '').substring(0, 48);
        return label || '#' + (position + 1);
    }

    function trackOptions(type) {
        return tracksOf(type).map(function (track, position) {
            return {
                index: Number(track.index),
                label: trackLabel(track, position),
                selected: type === 'AUDIO' ? position === audioTrackPosition :
                    (!subtitlesSilent && position === subtitleTrackPosition)
            };
        });
    }

    function selectTrack(type, trackIndex) {
        var tracks = tracksOf(type);
        var position = -1;
        var selector;
        tracks.some(function (track, index) {
            if (Number(track.index) === Number(trackIndex)) { position = index; return true; }
            return false;
        });
        selector = webapis.avplay.setSelectTrack || webapis.avplay.selectTrack;
        if (position < 0 || !selector) { status('UNAVAILABLE'); return false; }
        try {
            if (type === 'TEXT') {
                subtitlesSilent = false;
                if (styledSubtitlesAvailable()) { webapis.avplay.setSilentSubtitle(true); }
                subtitleTrackPosition = position;
            } else { audioTrackPosition = position; }
            selector.call(webapis.avplay, type, tracks[position].index);
            status(type === 'AUDIO' ? 'AUDIO_SELECTED' : 'SUBTITLE_SELECTED', (position + 1) + '/' + tracks.length);
            return true;
        } catch (ignoredSelect) { status('UNAVAILABLE'); return false; }
    }

    function cycleTrack(type) {
        var tracks = tracksOf(type);
        var position;
        var track;
        if (!tracks.length || !(webapis.avplay.setSelectTrack || webapis.avplay.selectTrack)) {
            status(type === 'AUDIO' ? 'NO_AUDIO_TRACKS' : 'NO_SUBTITLE_TRACKS'); return;
        }
        if (type === 'AUDIO') {
            audioTrackPosition = (audioTrackPosition + 1) % tracks.length;
            position = audioTrackPosition;
        } else {
            subtitlesSilent = false;
            subtitleTrackPosition = (subtitleTrackPosition + 1) % tracks.length;
            position = subtitleTrackPosition;
        }
        track = tracks[position];
        selectTrack(type, track.index);
    }

    function toggleSubtitles() {
        if (!styledSubtitlesAvailable()) { cycleTrack('TEXT'); return; }
        subtitlesSilent = !subtitlesSilent;
        try {
            webapis.avplay.setSilentSubtitle(true);
            if (subtitlesSilent) { clearSubtitleCue(); }
            status(subtitlesSilent ? 'SUBTITLES_OFF' : 'SUBTITLES_ON');
        }
        catch (ignoredSubtitle) { status('UNAVAILABLE'); }
    }

    function disableSubtitles() {
        if (!styledSubtitlesAvailable()) { return false; }
        subtitlesSilent = true;
        try { webapis.avplay.setSilentSubtitle(true); clearSubtitleCue(); status('SUBTITLES_OFF'); return true; }
        catch (ignoredSubtitle) { status('UNAVAILABLE'); return false; }
    }

    function playbackRates() {
        if (!available() || typeof webapis.avplay.setSpeed !== 'function') { return []; }
        /* AVPlay recebe multiplicadores inteiros; 1x e 2x são a interseção útil com o Media3. */
        return [1, 2];
    }

    function setPlaybackRate(rate) {
        rate = Number(rate);
        if (playbackRates().indexOf(rate) < 0 || (phase !== 'PLAYING' && phase !== 'PAUSED')) { return false; }
        try {
            webapis.avplay.setSpeed(rate);
            playbackRate = rate;
            status('SPEED', rate);
            return true;
        } catch (ignoredSpeed) {
            status('UNAVAILABLE');
            return false;
        }
    }

    function setListeners(next) {
        next = next || {};
        listeners.onStatus = next.onStatus || listeners.onStatus;
        listeners.onError = next.onError || listeners.onError;
        listeners.onTime = next.onTime || listeners.onTime;
        listeners.onSubtitle = next.onSubtitle || listeners.onSubtitle;
        listeners.onComplete = next.onComplete || listeners.onComplete;
    }

    return {
        play: play,
        stop: stop,
        pause: pause,
        resume: resume,
        togglePause: togglePause,
        seekBy: seekBy,
        cycleAudio: function () { cycleTrack('AUDIO'); },
        cycleSubtitle: function () { cycleTrack('TEXT'); },
        trackOptions: trackOptions,
        selectTrack: selectTrack,
        disableSubtitles: disableSubtitles,
        toggleSubtitles: toggleSubtitles,
        setSubtitleOffset: setSubtitleOffset,
        subtitleOffset: subtitleOffset,
        playbackRates: playbackRates,
        playbackRate: function () { return playbackRate; },
        setPlaybackRate: setPlaybackRate,
        playbackRateAvailable: function () { return playbackRates().length > 0; },
        displayModeAvailable: displayModeAvailable,
        styledSubtitlesAvailable: styledSubtitlesAvailable,
        displayMode: function () { return displayMode; },
        displayModes: function () { return DISPLAY_MODES.map(function (entry) { return entry.id; }); },
        cycleDisplayMode: cycleDisplayMode,
        isPlaying: function () { return phase === 'PLAYING' || phase === 'PAUSED' || phase === 'OPEN'; },
        setListeners: setListeners,
        available: available
    };
}());
