/* Contract of the one-command Samsung emulator runner. */
'use strict';

var fs = require('fs');
var path = require('path');

var runnerPath = path.resolve(__dirname, '..', '..', 'scripts', 'run-samsung-emulator.ps1');
var runner = fs.readFileSync(runnerPath, 'utf8');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('Runner Samsung a partir da VM desligada\n');
check('o runner possui uma VM Samsung padrão configurável',
    /\[string\]\$VmName\s*=\s*'T-samsung-10\.0-x86_64'/.test(runner));
check('a espera do boot possui limite explícito e validado',
    /ValidateRange\(30,\s*600\)[\s\S]{0,80}\$BootTimeoutSeconds/.test(runner));
check('o em-cli faz parte das dependências verificadas',
    /tools[\\/]emulator[\\/]bin[\\/]em-cli\.bat/.test(runner) &&
    /\$emCli/.test(runner));
check('sem alvo online o runner inicia a VM escolhida',
    /@\('launch',\s*'-n',\s*\$VmName\)/.test(runner));
check('o runner espera o SDB sem laço infinito',
    /function\s+Wait-ForOnlineDevice/.test(runner) &&
    /AddSeconds\(\$TimeoutSeconds\)/.test(runner));
check('uma VM recém-iniciada ganha tempo para concluir os serviços da TV',
    /Start-Sleep\s+-Seconds\s+20/.test(runner));
check('a automação nunca reseta nem exclui o disco da VM',
    !/@\('(reset|delete)'/.test(runner) &&
    !/\bem-cli(?:\.bat)?\s+(reset|delete)\b/i.test(runner));

if (failures.length) {
    process.stderr.write('\nFalharam: ' + failures.join('; ') + '\n');
    process.exit(1);
}
process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
