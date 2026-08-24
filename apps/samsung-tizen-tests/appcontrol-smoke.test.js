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
check('a espera tem timeout e a fixture temporaria sempre e removida',
    /SmokeTimeoutSeconds/.test(content.runner) &&
    /ObservationSeconds/.test(content.runner) && /Start-Sleep -Seconds \$ObservationSeconds/.test(content.runner) &&
    /finally\s*\{[\s\S]*?uninstall[\s\S]*?BUROCTL001\.AppControlSmoke/.test(content.runner));
check('o runner nao apaga nem reinicia a VM',
    !/\bem-cli(?:\.bat)?\s+(reset|delete)\b/i.test(content.runner) &&
    !/@\('(reset|delete)'/.test(content.runner));

if (failures.length) {
    process.stderr.write('\nFalharam: ' + failures.join('; ') + '\n');
    process.exit(1);
}
process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
