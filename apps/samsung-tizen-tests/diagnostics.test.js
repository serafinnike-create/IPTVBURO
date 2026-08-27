/*
  O que a televisão diz sobre a ligação de quem está a ver.

  Tudo aqui é sobre não mentir. Quem for informado de que a sua ligação está boa
  vai concluir que o aplicativo está com defeito; quem for informado de que está
  má sem estar vai pagar por uma linha mais rápida que não muda nada. Os dois
  são piores do que "não foi possível medir".

  Os limites têm de ser os mesmos do Windows e do Android: uma televisão que
  chamasse "boa" a mesma ligação que o Windows chama "ruim" seria o produto a
  discutir consigo próprio.
*/
'use strict';

var path = require('path');
var fs = require('fs');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var diagnostics = require(path.join(APP_DIR, 'js', 'diagnostics.js'));

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

process.stdout.write('Os limites que decidem o veredito\n');

/* O número que o dono deu: abaixo de 10 é onde começa a travar. */
check('abaixo de 10 Mbit/s é problema',
    diagnostics.downloadVerdict(9.9) === 'PROBLEM');
check('10 já não é problema, mas ainda é aviso',
    diagnostics.downloadVerdict(10) === 'WARNING');
check('15 é boa para 1080p',
    diagnostics.downloadVerdict(15) === 'GOOD' && diagnostics.qualityCeiling(20) === 'hd');
check('30 é o mínimo oferecido para 4K',
    diagnostics.qualityCeiling(30) === 'uhd' && diagnostics.qualityCeiling(29.9) === 'hd');

process.stdout.write('\nLatência e perda, julgadas à parte da velocidade\n');

/* Uma linha rápida com latência terrível trava em cada troca de canal. */
check('60 ms é bom, 61 já é aviso',
    diagnostics.pingVerdict(60) === 'GOOD' && diagnostics.pingVerdict(61) === 'WARNING');
check('acima de 150 ms é problema',
    diagnostics.pingVerdict(151) === 'PROBLEM');
check('qualquer perda merece ser dita',
    diagnostics.lossVerdict(0) === 'GOOD' &&
    diagnostics.lossVerdict(0.5) === 'WARNING' &&
    diagnostics.lossVerdict(2) === 'PROBLEM');

process.stdout.write('\nO veredito é a pior leitura, nunca a média\n');

/*
  Uma ligação com 200 Mbit/s e 8% de perda é uma ligação que trava. Fazer a
  média disso daria "tudo bem" a quem está a ver a imagem cortar.
*/
check('uma leitura má decide tudo',
    diagnostics.overall([
        { severity: 'GOOD' }, { severity: 'GOOD' }, { severity: 'PROBLEM' }
    ]) === 'PROBLEM');
check('um teste limpo diz que está limpo',
    diagnostics.overall([{ severity: 'GOOD' }]) === 'GOOD');

process.stdout.write('\nUma medição que falhou nunca vira uma saudável\n');

check('sem leitura, o download é problema',
    diagnostics.downloadVerdict(null) === 'PROBLEM');
check('sem leitura, a qualidade é desconhecida',
    diagnostics.qualityCeiling(null) === 'unknown');
/*
  Uma transferência que acabou num instante mediu o buffer, não a rede. Dizer
  900 Mbit/s a quem está a ver a imagem travar destrói a credibilidade de todas
  as outras linhas do ecrã.
*/
check('uma amostra curta demais não reporta nada',
    diagnostics.megabitsPerSecond(4000000, 12) === null);
check('uma transferência real vira megabits por segundo',
    Math.abs(diagnostics.megabitsPerSecond(12500000, 2000) - 50) < 0.1);

process.stdout.write('\nA mediana, que um valor isolado não arrasta\n');

check('a mediana ignora um pico isolado',
    diagnostics.median([20, 22, 21, 900, 19]) === 21);
check('sem amostras, não há mediana',
    diagnostics.median([]) === null);
check('a perda é contada sobre o que foi tentado',
    diagnostics.lossPercent({ samples: [10, 12], attempted: 4 }) === 50);

process.stdout.write('\nA plataforma pode não saber, e isso é uma resposta\n');

/*
  A Tizen só expõe o tipo de ligação em algumas versões. Uma que não expõe não
  pode inventar: 'wired' errado manda a pessoa procurar um cabo que não existe.
*/
check('sem a API de rede, o tipo de ligação fica desconhecido',
    ['unknown', 'none'].indexOf(diagnostics.linkKind()) >= 0);
check('a leitura de rede nunca rebenta sem a API',
    typeof diagnostics.network() === 'object');

process.stdout.write('\nOs mesmos limites das outras plataformas\n');

/*
  Lidos do modelo partilhado em Kotlin, que o Windows e o Android usam.

  Sem isto, os tres numeros vivem em dois ficheiros que ninguem compara: mudar o
  limite do 4K no Windows e esquecer a televisao daria a mesma ligacao dois
  vereditos diferentes, e o produto passaria a discutir consigo proprio.
*/
var shared = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'packages', 'domain-model', 'src', 'commonMain',
        'kotlin', 'com', 'lucasserafin94', 'iptvburo', 'domain', 'model', 'ConnectionDiagnostics.kt'),
    'utf8'
);

function sharedNumber(name) {
    var match = shared.match(new RegExp('const val ' + name + ' = ([0-9.]+)'));
    return match ? parseFloat(match[1]) : null;
}

check('o limite de ligacao fraca e o mesmo do Windows',
    sharedNumber('POOR_DOWNLOAD_MBPS') === 10);
check('o limite de 1080p e o mesmo do Windows',
    sharedNumber('HD_DOWNLOAD_MBPS') === 15);
check('o limite de 4K e o mesmo do Windows',
    sharedNumber('UHD_DOWNLOAD_MBPS') === 30);
check('os limites de latencia sao os mesmos',
    sharedNumber('GOOD_PING_MS') === 60 && sharedNumber('POOR_PING_MS') === 150);
check('o limite de perda de pacotes e o mesmo',
    sharedNumber('POOR_PACKET_LOSS_PERCENT') === 2.0);

/* E a televisao concorda com eles, nos dois sentidos. */
check('a televisao aplica exatamente esses limites',
    diagnostics.downloadVerdict(sharedNumber('POOR_DOWNLOAD_MBPS') - 0.1) === 'PROBLEM' &&
    diagnostics.downloadVerdict(sharedNumber('HD_DOWNLOAD_MBPS')) === 'GOOD' &&
    diagnostics.qualityCeiling(sharedNumber('UHD_DOWNLOAD_MBPS')) === 'uhd');

process.stdout.write('\nO ecra existe e esta ligado ao botao\n');

/*
  Um scan da fonte, e nao um teste de janela: o ecra vive dentro de um app.js de
  onze mil linhas que precisa de um DOM inteiro para correr. O que importa aqui e
  que as pontas estejam ligadas — um botao que nao chama nada, ou um ecra que
  ninguem desenha, compilam perfeitamente e nao fazem nada.
*/
var appSource = fs.readFileSync(path.join(APP_DIR, 'js', 'app.js'), 'utf8');
var indexSource = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');

check('o modulo do diagnostico e carregado pela pagina',
    indexSource.indexOf('js/diagnostics.js') >= 0);
check('o botao aparece na barra superior',
    appSource.indexOf('diagnosticsChipHtml()') >= 0 &&
    appSource.indexOf('function diagnosticsChipHtml') >= 0);
check('o botao chama o ecra',
    appSource.indexOf("action === 'diagnostics'") >= 0 &&
    appSource.indexOf('function openDiagnostics') >= 0);
check('o ecra e desenhado pelo roteador',
    appSource.indexOf("state.screen === 'DIAGNOSTICS'") >= 0 &&
    appSource.indexOf('function renderDiagnostics') >= 0);
check('testar de novo e fechar estao ligados',
    appSource.indexOf("action === 'diagnostics-run'") >= 0 &&
    appSource.indexOf("action === 'diagnostics-close'") >= 0);
/* O efeito de carregamento que faltava: sem ele, uma falha rapida parece um
   botao que nao fez nada. Foi assim que foi relatado no Windows. */
check('o ecra mostra as linhas antes de as ter',
    appSource.indexOf('DIAGNOSTICS_ROWS') >= 0 &&
    appSource.indexOf('DIAGNOSTICS_MINIMUM_MS') >= 0);
/* Um stream consumiria uma das ligacoes simultaneas da conta e pararia a
   televisao do outro quarto. */
check('a medicao usa o catalogo, nao um stream',
    diagnostics.probeUrl({ server: 'http://p.invalid', username: 'u', password: 'p' })
        .indexOf('get_vod_streams') >= 0);
check('a credencial vai codificada no endereco da medicao',
    diagnostics.probeUrl({ server: 'http://p.invalid', username: 'a b', password: 'x&y' })
        .indexOf('a%20b') >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write('Falhas: ' + failures.length + '\n');
    failures.forEach(function (label) { process.stdout.write('  - ' + label + '\n'); });
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
