/*
  Quanto falta da assinatura da lista, que nao e a licenca da app.

  O painel Xtream manda exp_date em user_info, e o app lia-o e deitava-o fora:
  as tres chamadas a authenticate recebiam o objecto e ignoravam-no. Sem isso o
  utilizador so descobre que a lista acabou quando ela deixa de responder.

  As duas assinaturas ficam lado a lado no topo e sao faceis de confundir, por
  isso o texto diz sempre de qual fala. E uma linha que nao expira -- que chega
  como vazio, "0" ou "null" conforme o painel -- nao pode aparecer como vencida:
  seria um aviso inventado a partir de um campo por preencher.

  As funcoes sao extraidas da fonte em vez de reescritas: uma copia aqui passaria
  no teste mesmo que a do app deixasse de existir.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'app.js');
var XTREAM = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'xtream.js');
var I18N = path.resolve(__dirname, '..', 'samsung-tizen', 'js', 'i18n.js');

var source = fs.readFileSync(APP, 'utf8');
var xtreamSource = fs.readFileSync(XTREAM, 'utf8');
var i18nSource = fs.readFileSync(I18N, 'utf8');

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

/* A funcao real que guarda a data, extraida da fonte. */
function extract(name, from) {
    var start = from.indexOf('function ' + name + '(');
    var depth = 0;
    var index;
    var opened = false;
    if (start < 0) { return null; }
    for (index = start; index < from.length; index += 1) {
        if (from[index] === '{') { depth += 1; opened = true; }
        else if (from[index] === '}') {
            depth -= 1;
            if (opened && depth === 0) { return from.slice(start, index + 1); }
        }
    }
    return null;
}

var body = extract('rememberPlaylistExpiry', source);
var state = { playlistExpiresAt: 'nao tocado' };
/* eslint-disable no-new-func */
var remember = new Function('state', body + '\nreturn rememberPlaylistExpiry;')(state);

process.stdout.write('O que o painel diz sobre o fim da assinatura\n');

remember({ expiresAt: 1790000000 });
check('uma data a serio e guardada', state.playlistExpiresAt === 1790000000);

remember({ expiresAt: null });
check('sem data fica nulo', state.playlistExpiresAt === null);

remember({ expiresAt: '' });
check('vazio fica nulo', state.playlistExpiresAt === null);

remember({ expiresAt: '0' });
check('zero fica nulo, nao uma data em 1970', state.playlistExpiresAt === null);

remember({});
check('conta sem campo nenhum fica nulo', state.playlistExpiresAt === null);

remember(null);
check('sem conta fica nulo', state.playlistExpiresAt === null);

process.stdout.write('\nO cliente tambem nao deixa passar um zero\n');

check('o xtream.js so aceita exp_date positivo',
    xtreamSource.indexOf('(isFinite(expiry) && expiry > 0) ? expiry : null') >= 0);

process.stdout.write('\nO chip esta ligado ao ecra\n');

check('a data e guardada nas tres autenticacoes',
    (source.match(/rememberPlaylistExpiry\(account\);/g) || []).length === 3);
check('o chip e desenhado ao lado do da licenca',
    source.indexOf('licenceChipHtml() + playlistChipHtml()') >= 0);
/* Sem regra de "so no ultimo mes": saber quando a lista acaba e uma coisa que se
   consulta a qualquer altura, nao so quando ja e quase tarde. */
check('o chip aparece sempre que ha uma data',
    source.indexOf('if (days > 30) { return \'\'; }') < 0);
check('a data e escrita no idioma do utilizador',
    source.indexOf('toLocaleDateString(BuroI18n.language()') >= 0);

process.stdout.write('\nO texto existe nos cinco idiomas\n');

['playlistDaysLeft', 'playlistDayLeft', 'playlistExpired'].forEach(function (key) {
    check(key + ' em cinco idiomas',
        (i18nSource.match(new RegExp(key + ':', 'g')) || []).length === 5);
});

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
