/* Contract for the real Tizen App Control delivery smoke test. */
'use strict';

var fs = require('fs');
var path = require('path');
var repo = path.resolve(__dirname, '..', '..');
var fixture = path.join(__dirname, 'fixtures', 'appcontrol-smoke');
var files = {
    config: path.join(fixture, 'config.xml'),
    html: path.join(fixture, 'index.html'),
    smoke: path.join(fixture, 'smoke.js'),
    runner: path.join(repo, 'scripts', 'run-samsung-appcontrol-smoke.ps1'),
    reportServer: path.join(repo, 'scripts', 'samsung-appcontrol-report-server.js'),
    productionConfig: path.join(repo, 'apps', 'samsung-tizen', 'config.xml')
};
var content = {};
var passed = 0;
var failures = [];

Object.keys(files).forEach(function (key) {
    content[key] = fs.existsSync(files[key]) ? fs.readFileSync(files[key], 'utf8') : '';
});
function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('Smoke de App Control Samsung Tizen\n');
check('o app de producao exporta view somente para o esquema iptvburo',
    /<tizen:operation name="http:\/\/tizen\.org\/appcontrol\/operation\/view"\/>/.test(content.productionConfig) &&
    /<tizen:uri name="iptvburo"\/>/.test(content.productionConfig) &&
    !/<tizen:uri name="https?"\/>/.test(content.productionConfig));
check('o remetente usa pacote isolado do app de producao',
    /package="BUROCTL001"/.test(content.config) &&
    /id="BUROCTL001\.AppControlSmoke"/.test(content.config) &&
    !/IPTVBUROxx\.IPTVBURO/.test(content.config));
check('o remetente declara apenas internet e application.launch',
    /privilege\/internet/.test(content.config) && /privilege\/application\.launch/.test(content.config) &&
    (content.config.match(/<tizen:privilege /g) || []).length === 2);
check('a prova usa somente identidade publica sintetica, sem stream ou segredo',
    /iptvburo:\/\/title\?id=movie%3Auri-probe%3A2099&t=Teste%20de%20link&y=2099/.test(content.smoke) &&
    !/(username|password|passwd|token|authorization|cookie|\.m3u8|\/live\/)/i.test(content.smoke));
check('o runner aceita uma identidade publica configuravel somente apos validacao estrita',
    /\[string\]\$PublicProbeUri\s*=/.test(content.runner) &&
    /function Assert-PublicProbeUri/.test(content.runner) &&
    /@\('id', 't', 'y'\) -notcontains \$name/.test(content.runner) &&
    /\^\(movie\|series\|episode\|live\|unknown\)/.test(content.runner) &&
    /Assert-PublicProbeUri -Value \$PublicProbeUri/.test(content.runner) &&
    /ConvertTo-Json -Compress \$PublicProbeUri/.test(content.runner) &&
    /\[IO\.File\]::WriteAllText\(\$smokePath/.test(content.runner) &&
    /valor omitido/.test(content.runner));
check('o esquema e resolvido implicitamente antes da entrega explicita ao app encontrado',
    /http:\/\/tizen\.org\/appcontrol\/operation\/view/.test(content.smoke) &&
    /findAppControl\(control,/.test(content.smoke) &&
    /application\.id === TARGET_APP_ID/.test(content.smoke) &&
    /launchAppControl\(control, TARGET_APP_ID,/.test(content.smoke));
check('o runner compila, assina, instala e inicia apenas o remetente',
    /build-web/.test(content.runner) && /package['"],\s*'-t'/.test(content.runner) &&
    /BUROCTL001\.AppControlSmoke/.test(content.runner) &&
    !/run[^\n]*IPTVBUROxx\.IPTVBURO/.test(content.runner));
check('o resultado positivo depende do callback real de launchAppControl',
    /APPCONTROL_LAUNCH_PASS/.test(content.smoke) &&
    /APPCONTROL_SMOKE_PASS/.test(content.runner) &&
    /\$result -ne 'APPCONTROL_LAUNCH_PASS'/.test(content.runner));
check('o diagnostico separa inicio, filtro encontrado e callback final sem dados do catalogo',
    /APPCONTROL_STAGE_STARTED/.test(content.smoke) &&
    /APPCONTROL_STAGE_FILTER_FOUND/.test(content.smoke) &&
    /getElementById\('status'\)/.test(content.smoke) && /id="status"/.test(content.html) &&
    /request\.open\([^\n]*false\)/.test(content.smoke) && /Access-Control-Allow-Origin/.test(content.reportServer) &&
    /APPCONTROL_STAGE_\[A-Z0-9_\]/.test(content.reportServer) &&
    /ultima etapa segura/.test(content.runner) &&
    !/(PUBLIC_PROBE_URI|identity|title)/.test(content.reportServer));
check('a espera tem timeout e a fixture temporaria sempre e removida',
    /SmokeTimeoutSeconds/.test(content.runner) &&
    /ObservationSeconds/.test(content.runner) && /Start-Sleep -Seconds \$ObservationSeconds/.test(content.runner) &&
    /finally\s*\{[\s\S]*?uninstall[\s\S]*?BUROCTL001\.AppControlSmoke/.test(content.runner));
check('o canal de retorno usa Node, porta livre, origem sincronizada e encerramento autenticado',
    /\[int\]\$ReportPort\s*=\s*0/.test(content.runner) &&
    /Start-Process -FilePath \$node/.test(content.runner) && /-WindowStyle Hidden -PassThru/.test(content.runner) &&
    /'"' \+ \$reportServerScript \+ '"'/.test(content.runner) &&
    /'"' \+ \$reportResultFile \+ '"'/.test(content.runner) &&
    /Wait-ReportServerReady/.test(content.runner) && /Wait-SmokeResultFile/.test(content.runner) &&
    /10\.0\.2\.2:\$activeReportPort/.test(content.runner) &&
    /<access origin=/.test(content.runner) && /WriteAllText\(\$configPath/.test(content.runner) &&
    /shutdown\?nonce=\$shutdownNonce/.test(content.runner) &&
    /\^APPCONTROL_\[A-Z0-9_\]\{1,180\}\$/.test(content.reportServer) &&
    /parsed\.pathname === '\/shutdown'/.test(content.reportServer) &&
    /server\.listen\(port, '0\.0\.0\.0'/.test(content.reportServer));
check('o modo frio desliga somente uma VM Tizen pelo Power oficial e a relanca',
    /\[switch\]\$ColdStart/.test(content.runner) &&
    /\[int\]\$PowerEcpCode\s*=\s*116/.test(content.runner) &&
    /Get-Process -Name 'emulator-x86_64'/.test(content.runner) &&
    /\.Path\.StartsWith\(\$studioPrefix/.test(content.runner) &&
    /\$emulators\.Count -ne 1/.test(content.runner) &&
    /ecp-cli\.bat/.test(content.runner) &&
    /'--target', \$Vm, 'keycode', \[string\]\$PowerCode/.test(content.runner) &&
    /Wait-ForOfflineDevice/.test(content.runner) && /WaitForExit\(60000\)/.test(content.runner) &&
    /Restart-EmulatorForColdStart/.test(content.runner) &&
    /@\('launch', '-n', \$Vm\)/.test(content.runner) &&
    /Wait-ForOnlineDevice/.test(content.runner));
check('o runner nunca reseta, apaga ou encerra a VM de forma forcada',
    !/\bem-cli(?:\.bat)?\s+(reset|delete)\b/i.test(content.runner) &&
    !/@\('(reset|delete)'/.test(content.runner) &&
    !/Stop-Process|\.Kill\(|CloseMainWindow/.test(content.runner));

if (failures.length) {
    process.stderr.write('\nFalharam: ' + failures.join('; ') + '\n');
    process.exit(1);
}
process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
