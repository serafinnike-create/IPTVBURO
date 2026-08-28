/* Shared provider artwork must become a title card, matching the Kotlin domain. */
'use strict';

var fs = require('fs');
var path = require('path');
var vm = require('vm');
var file = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'placeholder-artwork.js');
var context = {};
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function detect(values) { return context.BuroPlaceholderArtwork.detect(values); }

process.stdout.write('Capas genéricas repetidas pelo provedor\n');
if (!fs.existsSync(file)) {
    process.stdout.write('  FALHA o contrato Samsung de capa genérica existe\n');
    process.exit(1);
}
vm.runInNewContext(fs.readFileSync(file, 'utf8'), context, { filename: file });

check('o limite Samsung acompanha exatamente o domínio Kotlin',
    context.BuroPlaceholderArtwork.SHARED_COVER_THRESHOLD === 25);
check('vinte e quatro cópias ainda podem ser duplicações reais',
    detect(Array(24).fill('https://art.test/same-film.jpg')).length === 0);
check('vinte e cinco usos transformam a imagem em placeholder',
    detect(Array(25).fill('https://art.test/generic.jpg'))[0] === 'https://art.test/generic.jpg');
check('uma capa própria de cada título permanece',
    detect(Array.from({ length: 500 }, function (_, index) {
        return 'https://art.test/poster-' + index + '.jpg';
    })).length === 0);
check('espaços ao redor não dividem a contagem',
    detect(Array.from({ length: 30 }, function (_, index) {
        return index % 2 ? 'https://art.test/a.jpg' : ' https://art.test/a.jpg ';
    }))[0] === 'https://art.test/a.jpg');
check('nulo e vazio não viram um placeholder universal',
    detect([null, '', '   ', null]).length === 0);
check('capa genérica e capas reais são separadas', (function () {
    var real = Array.from({ length: 80 }, function (_, index) { return 'https://art.test/' + index + '.jpg'; });
    return detect(real.concat(Array(400).fill('https://art.test/XXX-ADULT'))).join('|') ===
        'https://art.test/XXX-ADULT';
}()));
check('nomes especiais de objeto não corrompem o contador',
    detect(Array(25).fill('__proto__'))[0] === '__proto__');

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
    failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
    process.exitCode = 1;
} else {
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}
