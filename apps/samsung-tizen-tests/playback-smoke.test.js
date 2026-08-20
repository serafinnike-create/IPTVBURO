/* Contract for the isolated, public-media AVPlay smoke test. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var repo = path.resolve(__dirname, '..', '..');
var fixture = path.join(__dirname, 'fixtures', 'playback-smoke');
var runnerPath = path.join(repo, 'scripts', 'run-samsung-playback-smoke.ps1');
var files = {
    config: path.join(fixture, 'config.xml'),
    html: path.join(fixture, 'index.html'),
    smoke: path.join(fixture, 'smoke.js'),
    runner: runnerPath
};
var content = {};
var passed = 0;
var failures = [];
var smokePackage;

Object.keys(files).forEach(function (key) {
    content[key] = fs.existsSync(files[key]) ? fs.readFileSync(files[key], 'utf8') : '';
});

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function exerciseRemoteSeekKeys() {
    var dom = new JSDOM('<!doctype html><body><span id="state"></span><span id="position"></span><span id="result"></span></body>', {
        runScripts: 'outside-only',
        url: 'http://127.0.0.1/'
    });
    var window = dom.window;
    var offsets = [];
    window.BuroKeys = {
        CODES: { LEFT: 37, RIGHT: 39, PLAY_PAUSE: 10252, PLAY: 415, PAUSE: 19, REWIND: 412, FAST_FORWARD: 417 },
        registerMediaKeys: function () {}
    };
    window.BuroPlayer = {
        setListeners: function () {},
        play: function () {},
        stop: function () {},
        togglePause: function () {},
        seekBy: function (milliseconds) { offsets.push(milliseconds); }
    };
    window.eval(content.smoke);
    window.dispatchEvent(new window.Event('load'));
    [37, 412, 39, 417].forEach(function (keyCode) {
        var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
        Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
        window.document.dispatchEvent(event);
    });
    window.dispatchEvent(new window.Event('unload'));
    window.close();
    return offsets;
}

function exerciseNativeTrackFlow() {
    var dom = new JSDOM('<!doctype html><body><span id="state"></span><span id="position"></span><span id="result"></span></body>', {
        runScripts: 'outside-only',
        url: 'http://127.0.0.1/'
    });
    var window = dom.window;
    var listeners;
    var selections = [];
    var reports = [];
    window.Image = function () {};
    Object.defineProperty(window.Image.prototype, 'src', {
        set: function (value) { reports.push(String(value)); }
    });
    window.BuroKeys = {
        CODES: { LEFT: 37, RIGHT: 39, PLAY_PAUSE: 10252, PLAY: 415, PAUSE: 19, REWIND: 412, FAST_FORWARD: 417 },
        registerMediaKeys: function () {}
    };
    window.BuroPlayer = {
        setListeners: function (next) { listeners = next; },
        play: function () {},
        stop: function () {},
        togglePause: function () {},
        seekBy: function () {},
        trackOptions: function (type) {
            return type === 'AUDIO' ? [
                { index: 10, label: 'English 1', selected: true },
                { index: 11, label: 'English 2', selected: false }
            ] : [
                { index: 21, label: 'English', selected: true },
                { index: 22, label: 'Français', selected: false }
            ];
        },
        selectTrack: function (type, index) {
            selections.push([type, index]);
            listeners.onStatus(type === 'AUDIO' ? 'AUDIO_SELECTED' : 'SUBTITLE_SELECTED');
            return true;
        }
    };
    function key(keyCode) {
        var event = new window.KeyboardEvent('keydown', { bubbles: true, cancelable: true });
        Object.defineProperty(event, 'keyCode', { get: function () { return keyCode; } });
        window.document.dispatchEvent(event);
    }
    window.eval(content.smoke);
    window.dispatchEvent(new window.Event('load'));
    listeners.onStatus('PREPARING');
    listeners.onStatus('PLAYING');
    listeners.onTime(1500);
    listeners.onStatus('PAUSED');
    listeners.onStatus('PLAYING');
    key(39);
    listeners.onStatus('SEEK_FORWARD');
    listeners.onTime(31500);
    /* A VM pode continuar avançando enquanto recompõe o buffer antes da próxima tecla. */
    listeners.onTime(41000);
    key(37);
    listeners.onStatus('SEEK_BACK');
    listeners.onTime(31000);
    key(417);
    listeners.onStatus('SEEK_FORWARD');
    listeners.onTime(61000);
    listeners.onTime(70000);
    key(412);
    listeners.onStatus('SEEK_BACK');
    listeners.onTime(60000);
    window.dispatchEvent(new window.Event('unload'));
    window.close();
    return { selections: selections, reports: reports };
}

process.stdout.write('Smoke AVPlay isolado no emulador Samsung\n');
smokePackage = /<tizen:application[\s\S]*?package="([A-Za-z0-9]+)"/.exec(content.config);
check('o fixture de diagnóstico existe sem duplicar o player de produção',
    Boolean(content.config && content.html && content.smoke) &&
    !fs.existsSync(path.join(fixture, 'player.js')) && !fs.existsSync(path.join(fixture, 'js', 'player.js')));
check('o smoke usa um package/application id diferente do app IPTV BURO',
    smokePackage && smokePackage[1].length === 10 &&
    new RegExp('id="' + smokePackage[1] + '\\.PlaybackSmoke"').test(content.config) &&
    !/IPTVBUROxx\.IPTVBURO/.test(content.config));
check('a origem da fixture pública possui allowlist restrita',
    /<access\s+origin="https:\/\/devstreaming-cdn\.apple\.com"/.test(content.config) &&
    !/<access\s+origin="\*"/.test(content.config));
check('a URL HLS de teste é HTTPS e não contém credencial nem token',
    /https:\/\/devstreaming-cdn\.apple\.com\/[A-Za-z0-9_./-]+\.m3u8/.test(content.smoke) &&
    !/(username|password|passwd|token|authorization|cookie)\s*[:=]/i.test(content.smoke));
check('o diagnóstico persiste estados e progresso sem persistir a URL',
    /localStorage\.setItem\(['"]iptvburo\.avplay-smoke['"]/.test(content.smoke) &&
    /onStatus/.test(content.smoke) && /onTime/.test(content.smoke) &&
    !/localStorage\.setItem\([^\n]*(url|stream|source)/i.test(content.smoke));
check('o sucesso exige PLAYING e tempo de reprodução avançando',
    /sawPlaying/.test(content.smoke) && /maxPositionMs/.test(content.smoke) &&
    /maxPositionMs\s*>?=\s*1000/.test(content.smoke));
check('o runner copia exatamente o adapter AVPlay do app de produção',
    /apps[\\/]samsung-tizen[\\/]js[\\/]player\.js/.test(content.runner) &&
    /Copy-Item[\s\S]{0,240}player\.js/.test(content.runner));
check('o smoke copia o mapeamento de controle remoto do app de produção',
    /js\/keys\.js/.test(content.html) &&
    /apps[\\/]samsung-tizen[\\/]js[\\/]keys\.js/.test(content.runner) &&
    /Copy-Item[\s\S]{0,240}keys\.js/.test(content.runner));
check('o smoke registra as teclas de mídia e trata PlayPause e seta direita',
    /privilege\/tv\.inputdevice/.test(content.config) &&
    /BuroKeys\.registerMediaKeys\(\)/.test(content.smoke) &&
    /PLAY_PAUSE[\s\S]{0,180}BuroPlayer\.togglePause\(\)/.test(content.smoke) &&
    /RIGHT[\s\S]{0,180}BuroPlayer\.seekBy\(30000\)/.test(content.smoke));
check('o sucesso exige pausa, retomada e avanço confirmado pelo AVPlay',
    /sawPaused/.test(content.smoke) && /sawResumed/.test(content.smoke) &&
    /sawSeekForward/.test(content.smoke) && /seekBaselineMs/.test(content.smoke) &&
    /maxPositionMs\s*>?=\s*result\.seekBaselineMs\s*\+\s*15000/.test(content.smoke));
check('o runner compila, assina, instala e inicia somente o pacote de smoke',
    /build-web/.test(content.runner) && /package['"],?\s*'-t'/.test(content.runner) &&
    smokePackage && new RegExp(smokePackage[1] + '\\.PlaybackSmoke').test(content.runner) &&
    !/IPTVBUROxx\.IPTVBURO/.test(content.runner));
check('a espera por resultado AVPlay possui timeout explícito',
    /SmokeTimeoutSeconds/.test(content.runner) && /AddSeconds\(\$SmokeTimeoutSeconds\)/.test(content.runner));
check('o runner coleta resultado da VM e falha quando o smoke não passa',
    /iptvburo\.avplay-smoke/.test(content.runner) && /AVPLAY_SMOKE_PASS/.test(content.runner) &&
    /throw[\s\S]{0,180}AVPlay/.test(content.runner));
check('o runner envia PlayPause duas vezes e avanço pelo ECP',
    /tools[\\/]emulator[\\/]bin[\\/]ecp-cli\.bat/.test(content.runner) &&
    /\[int\]\$PlayPauseEcpCode\s*=\s*244/.test(content.runner) &&
    /\[int\]\$RightEcpCode\s*=\s*106/.test(content.runner) &&
    /AVPLAY_SMOKE_READY[\s\S]{0,300}keycode[\s\S]{0,100}\$PlayPauseEcpCode/.test(content.runner) &&
    /AVPLAY_SMOKE_PAUSED[\s\S]{0,300}keycode[\s\S]{0,100}\$PlayPauseEcpCode/.test(content.runner) &&
    /AVPLAY_SMOKE_RESUMED[\s\S]{0,300}keycode[\s\S]{0,100}\$RightEcpCode/.test(content.runner));
check('a automação nunca reseta nem exclui a VM',
    !/\bem-cli(?:\.bat)?\s+(reset|delete)\b/i.test(content.runner) &&
    !/@\('(reset|delete)'/.test(content.runner));

check('seta esquerda e teclas dedicadas preservam o contrato -10 s e +30 s',
    JSON.stringify(exerciseRemoteSeekKeys()) === JSON.stringify([-10000, -10000, 30000, 30000]));
check('o sucesso exige retrocesso pela seta e pelas duas teclas dedicadas',
    /sawSeekBack/.test(content.smoke) && /sawFastForward/.test(content.smoke) && /sawRewind/.test(content.smoke) &&
    /AVPLAY_SMOKE_SEEK_BACK/.test(content.smoke) && /AVPLAY_SMOKE_FAST_FORWARD/.test(content.smoke));
check('o runner encadeia esquerda, fast-forward e rewind usando os codigos da skin',
    /\[int\]\$LeftEcpCode\s*=\s*105/.test(content.runner) &&
    /\[int\]\$FastForwardEcpCode\s*=\s*208/.test(content.runner) &&
    /\[int\]\$RewindEcpCode\s*=\s*168/.test(content.runner) &&
    /AVPLAY_SMOKE_SEEK_FORWARD[\s\S]{0,360}\$LeftEcpCode/.test(content.runner) &&
    /AVPLAY_SMOKE_SEEK_BACK[\s\S]{0,360}\$FastForwardEcpCode/.test(content.runner) &&
    /AVPLAY_SMOKE_FAST_FORWARD[\s\S]{0,360}\$RewindEcpCode/.test(content.runner));
var trackFlow = exerciseNativeTrackFlow();
check('depois dos controles o smoke seleciona outra faixa de audio e outra legenda',
    JSON.stringify(trackFlow.selections) === JSON.stringify([['AUDIO', 11], ['TEXT', 22]]));
check('o resultado final registra quantas faixas o AVPlay realmente enumerou',
    trackFlow.reports.some(function (url) {
        return url.indexOf('result=AVPLAY_SMOKE_PASS') >= 0 &&
            url.indexOf('audioTracks=2') >= 0 && url.indexOf('subtitleTracks=2') >= 0;
    }));
check('o pacote de diagnóstico é removido mesmo quando o smoke falha',
    /finally\s*\{[\s\S]*?\.Stop\(\)[\s\S]*?uninstall[\s\S]*?BUROSMK001\.PlaybackSmoke[\s\S]*?'-t'\s*,\s*\$VmName[\s\S]*?\}/.test(content.runner));

if (failures.length) {
    process.stderr.write('\nFalharam: ' + failures.join('; ') + '\n');
    process.exit(1);
}
process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
