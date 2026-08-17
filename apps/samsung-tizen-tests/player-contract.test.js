const fs = require('fs');
const path = require('path');
const vm = require('vm');

const playerSource = fs.readFileSync(path.join(__dirname, '..', 'samsung-tizen', 'js', 'player.js'), 'utf8');
let passed = 0;
let failed = 0;

function check(name, condition) {
    if (condition) {
        passed += 1;
        process.stdout.write('PASS ' + name + '\n');
    } else {
        failed += 1;
        process.stderr.write('FAIL ' + name + '\n');
    }
}

function harness(prepareError, withDisplayMethod, withSilentSubtitle) {
    const displayMethods = [];
    const statuses = [];
    const errors = [];
    const subtitleCues = [];
    const silentSubtitleValues = [];
    let playbackCallbacks = null;
    let state = 'NONE';
    const avplay = {
        getState: function () { return state; },
        open: function () { state = 'IDLE'; },
        setListener: function (callbacks) { playbackCallbacks = callbacks; },
        setDisplayRect: function () {},
        prepareAsync: function (success, failure) {
            if (prepareError) { failure(prepareError); return; }
            state = 'READY';
            success();
        },
        play: function () { state = 'PLAYING'; },
        stop: function () { state = 'IDLE'; },
        close: function () { state = 'NONE'; }
    };
    if (withDisplayMethod !== false) {
        avplay.setDisplayMethod = function (method) { displayMethods.push(method); };
    }
    if (withSilentSubtitle !== false) {
        avplay.setSilentSubtitle = function (value) { silentSubtitleValues.push(value); };
    }
    const context = vm.createContext({ webapis: { avplay: avplay } });
    vm.runInContext(playerSource, context, { filename: 'player.js' });
    context.BuroPlayer.setListeners({
        onStatus: function (code, value) { statuses.push({ code: code, value: value }); },
        onError: function (error) { errors.push(error); },
        onSubtitle: function (value, duration) { subtitleCues.push({ value: value, duration: duration }); }
    });
    return {
        player: context.BuroPlayer,
        displayMethods: displayMethods,
        statuses: statuses,
        errors: errors,
        subtitleCues: subtitleCues,
        silentSubtitleValues: silentSubtitleValues,
        callbacks: function () { return playbackCallbacks; }
    };
}

process.stdout.write('Contrato AVPlay\n');
const display = harness(null, true);
display.player.play('https://media.public.test/video.m3u8');
check('modo inicial preserva proporcao',
    display.displayMethods.join(',') === 'PLAYER_DISPLAY_MODE_LETTER_BOX');
check('somente os tres modos documentados ficam publicos',
    display.player.displayModes().join(',') === 'LETTER_BOX,FULL_SCREEN,AUTO_ASPECT_RATIO');
display.player.cycleDisplayMode();
display.player.cycleDisplayMode();
display.player.cycleDisplayMode();
check('ciclo usa os tres valores nativos e retorna ao original',
    display.displayMethods.join(',') === [
        'PLAYER_DISPLAY_MODE_LETTER_BOX',
        'PLAYER_DISPLAY_MODE_FULL_SCREEN',
        'PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO',
        'PLAYER_DISPLAY_MODE_LETTER_BOX'
    ].join(','));
check('mudanca de formato emite apenas codigo e identificador sanitizados',
    display.statuses.filter(function (entry) { return entry.code === 'DISPLAY_MODE'; }).length === 4 &&
    JSON.stringify(display.statuses).indexOf('media.public.test') === -1);
display.callbacks().onsubtitlechange(2400, 'Legenda AVPlay');
check('modo silencioso nativo preserva eventos para a camada de legenda estilizada',
    display.player.styledSubtitlesAvailable() &&
    display.silentSubtitleValues.indexOf(true) >= 0 &&
    display.subtitleCues[display.subtitleCues.length - 1].value === 'Legenda AVPlay' &&
    display.subtitleCues[display.subtitleCues.length - 1].duration === 2400);
display.player.disableSubtitles();
display.callbacks().onsubtitlechange(2400, 'Nao deve aparecer');
check('desligar legenda limpa a camada e ignora eventos posteriores',
    display.subtitleCues[display.subtitleCues.length - 1].value === '');

const withoutDisplay = harness(null, false);
withoutDisplay.player.play('https://media.public.test/video.m3u8');
check('capability fica oculta quando o firmware nao expoe setDisplayMethod',
    !withoutDisplay.player.displayModeAvailable() && !withoutDisplay.player.cycleDisplayMode());

const withoutStyledSubtitles = harness(null, false, false);
withoutStyledSubtitles.player.play('https://media.public.test/video.m3u8');
check('preferencias visuais ficam condicionadas ao modo silencioso do firmware',
    !withoutStyledSubtitles.player.styledSubtitlesAvailable());

function failureCode(error) {
    const current = harness(error, false);
    current.player.play('https://media.public.test/video.m3u8');
    return current.errors.length ? current.errors[0].code : '';
}

check('InvalidAccessError vira fonte indisponivel',
    failureCode({ name: 'InvalidAccessError' }) === 'PLAYBACK_SOURCE_UNAVAILABLE');
check('arquivo inexistente vira fonte indisponivel',
    failureCode('PLAYER_ERROR_NO_SUCH_FILE') === 'PLAYBACK_SOURCE_UNAVAILABLE');
check('NetworkError continua sendo conexao',
    failureCode({ name: 'NetworkError' }) === 'PLAYBACK_CONNECTION');
check('NotSupportedError vira formato nao suportado',
    failureCode({ name: 'NotSupportedError' }) === 'PLAYBACK_UNSUPPORTED');
check('erro desconhecido permanece generico',
    failureCode({ name: 'UnknownError' }) === 'PLAYBACK_UNKNOWN');

process.stdout.write('\n' + passed + ' aprovados, ' + failed + ' falharam\n');
if (failed) { process.exitCode = 1; }
