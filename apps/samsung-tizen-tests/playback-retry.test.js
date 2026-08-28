/* Recuperação automática limitada antes do painel manual do player. */
'use strict';

var fs = require('fs');
var path = require('path');
var vm = require('vm');
var sourcePath = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'playback-retry.js');
var source = fs.existsSync(sourcePath) ? fs.readFileSync(sourcePath, 'utf8') : '';
var sandbox = { setTimeout: setTimeout, clearTimeout: clearTimeout };
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function settle(milliseconds) { return new Promise(function (resolve) { setTimeout(resolve, milliseconds); }); }

async function run() {
    process.stdout.write('Recuperação automática limitada do player Samsung\n');
    check('o módulo de recuperação existe', Boolean(source));
    if (source) { vm.runInNewContext(source, sandbox, { filename: sourcePath }); }
    check('o módulo publica o controlador real', Boolean(sandbox.BuroPlaybackRetry && sandbox.BuroPlaybackRetry.create));
    if (!sandbox.BuroPlaybackRetry) { throw new Error('PLAYBACK_RETRY_MODULE_MISSING'); }

    var attempts = 0;
    var retry = sandbox.BuroPlaybackRetry.create({ delayMs: 10, maxRetries: 1 });
    check('a primeira falha de conexão agenda uma tentativa',
        retry.schedule({ code: 'PLAYBACK_CONNECTION' }, function () { attempts += 1; }));
    check('uma segunda falha não cria loop antes de resetar',
        !retry.schedule({ code: 'PLAYBACK_CONNECTION' }, function () { attempts += 100; }));
    await settle(25);
    check('a tentativa agendada executa exatamente uma vez', attempts === 1);
    check('formato incompatível vai direto para a ação manual',
        !retry.schedule({ code: 'PLAYBACK_UNSUPPORTED' }, function () { attempts += 100; }));

    retry.reset();
    check('uma nova sessão recupera o orçamento de uma tentativa',
        retry.schedule({ code: 'PLAYBACK_CONNECTION' }, function () { attempts += 1; }));
    retry.reset();
    await settle(25);
    check('sair da reprodução cancela a tentativa pendente', attempts === 1);

    process.stdout.write('\nTodos os ' + passed + ' testes passaram.\n');
    if (failures.length) {
        process.stderr.write('Falharam: ' + failures.join('; ') + '\n');
        process.exit(1);
    }
}

run().catch(function (error) {
    process.stderr.write(String(error && error.message || error) + '\n');
    process.exit(1);
});
