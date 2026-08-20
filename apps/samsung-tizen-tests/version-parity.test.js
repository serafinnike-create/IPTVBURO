/* Version parity is a product contract: preview maturity is expressed by the
   release manifest status, not by showing a different product number on TV. */
var fs = require('fs');
var path = require('path');

var repo = path.join(__dirname, '..', '..');
var androidBuild = fs.readFileSync(path.join(repo, 'apps', 'android-tv', 'build.gradle.kts'), 'utf8');
var desktopBuild = fs.readFileSync(path.join(repo, 'apps', 'desktop', 'build.gradle.kts'), 'utf8');
var tizenConfig = fs.readFileSync(path.join(repo, 'apps', 'samsung-tizen', 'config.xml'), 'utf8');
var tizenApp = fs.readFileSync(path.join(repo, 'apps', 'samsung-tizen', 'js', 'app.js'), 'utf8');
var releaseManifest = JSON.parse(fs.readFileSync(path.join(repo, 'packages', 'release-manifest', 'platforms.json'), 'utf8'));
var failures = [];
var passed = 0;

function check(label, condition, detail) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label + (detail ? ' — ' + detail : '')); process.stdout.write('  FALHA ' + label + '\n'); }
}

function capture(text, expression, label) {
    var match = expression.exec(text);
    if (!match) { failures.push('não foi possível ler ' + label); return null; }
    return match[1];
}

var androidVersion = capture(androidBuild, /versionName\s*=\s*"([^"]+)"/, 'a versão Android');
var desktopVersion = capture(desktopBuild, /^version\s*=\s*"([^"]+)"/m, 'a versão Windows');
var tizenVersion = capture(tizenConfig, /<widget[\s\S]*?\sversion="([^"]+)"/, 'a versão Tizen');
var fallbackVersion = capture(tizenApp, /APP_VERSION_FALLBACK\s*=\s*'([^']+)'/, 'o fallback Tizen');
var samsungRelease = releaseManifest.platforms.filter(function (platform) {
    return platform.id === 'samsung-tizen';
})[0];
var manifestVersion = samsungRelease && samsungRelease.version ?
    String(samsungRelease.version).replace(/-preview$/, '') : null;

process.stdout.write('Numeração compartilhada do produto\n');
check('Android e Windows usam a mesma versão', androidVersion && androidVersion === desktopVersion,
    String(androidVersion) + ' / ' + String(desktopVersion));
check('Samsung usa a mesma versão do Android e Windows', tizenVersion && tizenVersion === androidVersion,
    String(tizenVersion) + ' / ' + String(androidVersion));
check('o fallback mostrado em Configurações acompanha o config.xml', fallbackVersion === tizenVersion,
    String(fallbackVersion) + ' / ' + String(tizenVersion));
check('o release manifest acompanha o pacote Samsung', manifestVersion === tizenVersion,
    String(manifestVersion) + ' / ' + String(tizenVersion));
check('a maturidade Samsung continua explícita como preview',
    samsungRelease && samsungRelease.status === 'HARDWARE_VALIDATION' && /-preview$/.test(samsungRelease.version || ''));

if (failures.length) {
    process.stdout.write('\n' + failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
}
