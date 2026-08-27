/*
  O manifesto de capabilities descreve o app que existe?

  `packages/platform-capabilities/samsung-tizen.json` é um arquivo estático: nada
  no runtime o corrige. Quem o lê para decidir o que mostrar acredita nele.

  Este é o tipo de arquivo que envelhece em silêncio. Uma função é implementada,
  o manifesto continua dizendo que ela não existe, e nada falha — até alguém
  esconder uma tela que funciona, ou prometer uma que não. Foi exatamente o que
  aconteceu com `seek`, declarado falso enquanto js/player.js já posicionava a
  retomada e saltava -10s/+30s.

  Então aqui o manifesto é conferido contra o código e o config.xml, não contra
  outra cópia da mesma afirmação. Cada teste aponta para a evidência que sustenta
  o valor declarado.

  O que este teste NÃO faz: afirmar que a função roda numa TV Samsung. Ele mede
  intenção do código, não hardware. Codecs, HDR e as APIs tizen.* continuam
  exigindo um aparelho físico.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var MANIFEST_PATH = path.resolve(
    __dirname, '..', '..', 'packages', 'platform-capabilities', 'samsung-tizen.json'
);
var SCHEMA_PATH = path.resolve(
    __dirname, '..', '..', 'packages', 'contracts', 'platform-capabilities.schema.json'
);

var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function section(title) { process.stdout.write(title + '\n'); }

function appSource(name) {
    return fs.readFileSync(path.join(APP_DIR, 'js', name), 'utf8');
}

var manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'));
var schema = JSON.parse(fs.readFileSync(SCHEMA_PATH, 'utf8'));
var config = fs.readFileSync(path.join(APP_DIR, 'config.xml'), 'utf8');

/* Validação estrutural mínima: o schema é draft 2020-12, mas a suíte não tem
   validador e não vale adicionar uma dependência por um arquivo. O que importa
   aqui é o contrato que o schema realmente impõe — chaves obrigatórias,
   booleanos e a recusa de campos inventados. */
function validate(value, rules, trail) {
    var problems = [];
    (rules.required || []).forEach(function (key) {
        if (!(key in value)) { problems.push(trail + '.' + key + ' ausente'); }
    });
    Object.keys(value).forEach(function (key) {
        var rule = rules.properties && rules.properties[key];
        if (!rule) {
            if (rules.additionalProperties === false) {
                problems.push(trail + '.' + key + ' não faz parte do schema');
            }
            return;
        }
        if (rule.type === 'boolean' && typeof value[key] !== 'boolean') {
            problems.push(trail + '.' + key + ' não é boolean');
        }
        if (rule.type === 'string' && typeof value[key] !== 'string') {
            problems.push(trail + '.' + key + ' não é string');
        }
        if (rule.type === 'object' && value[key] && typeof value[key] === 'object') {
            problems = problems.concat(validate(value[key], rule, trail + '.' + key));
        }
    });
    return problems;
}

section('Forma do manifesto');

var structural = validate(manifest, schema, 'samsung-tizen');
check('o manifesto satisfaz o schema de capabilities', structural.length === 0);
if (structural.length) {
    structural.forEach(function (problem) { process.stdout.write('        ' + problem + '\n'); });
}
check('o manifesto se identifica como samsung-tizen', manifest.platformId === 'samsung-tizen');

section('Reprodução');

var player = appSource('player.js');

/* O AVPlay é a engine; declarar outra coisa desorienta quem for depurar. */
check('a engine declarada é a que o player usa',
    manifest.playback.engine === 'Samsung AVPlay' && /webapis\.avplay/.test(player));

check('seek declarado corresponde a seekTo/jumpForward/jumpBackward no player',
    manifest.playback.seek === (
        /webapis\.avplay\.seekTo/.test(player) &&
        /webapis\.avplay\.jumpForward/.test(player) &&
        /webapis\.avplay\.jumpBackward/.test(player)
    ));

/* Multiview e PiP exigem um segundo decodificador. Nada no app abre dois
   streams, e prometer isso num manifesto lido por outra tela seria inventar
   uma função. */
check('multiview continua desligado porque o app abre um stream por vez',
    manifest.playback.multiview === false);
check('pip continua desligado', manifest.playback.pip === false);

/* HDR não é uma decisão de código: depende do painel e do ano da TV. Enquanto
   ninguém mediu num aparelho, o único valor honesto é falso. */
check('hdr permanece falso até medição em hardware', manifest.playback.hdr === false);

section('Navegação');

var app = appSource('app.js');

check('dpad declarado corresponde ao tratamento de teclas do controle',
    manifest.navigation.dpad === true &&
    /document\.addEventListener\('keydown'/.test(app));

/* Teclado e ponteiro não são o caminho principal, mas existem: recusá-los no
   manifesto faria uma tela esconder o campo de busca ou o clique. */
check('keyboard declarado corresponde a campos de texto reais',
    manifest.navigation.keyboard === /<input id="search-query"/.test(app));
check('mouse declarado corresponde a listeners de clique',
    manifest.navigation.mouse === /addEventListener\('click'/.test(app));

/* Nenhuma TV Samsung suportada tem tela sensível ao toque. */
check('touch permanece falso', manifest.navigation.touch === false);

section('Offline');

var downloads = appSource('downloads.js');

check('offline declarado corresponde à existência do adapter de download',
    manifest.offline.supported === /function enabled\s*\(/.test(downloads));

/* O campo estático diz que a função existe; quem decide se ela aparece é o
   runtime. Se este gate sumir, o manifesto vira uma promessa vazia. */
check('a disponibilidade real continua atrás de BuroDownloads.enabled()',
    /BuroUsb\.hasStorage\(\)/.test(downloads) &&
    /BuroDownloads\.enabled\(\)/.test(app));

check('seasonQueue declarado corresponde ao download em lote de série/temporada',
    manifest.offline.seasonQueue === /bulk-download-confirm/.test(app));

/* O manifesto Tizen desliga o background; a fila só avança com o app aberto. */
check('backgroundJobs é falso porque o config.xml desabilita background',
    manifest.offline.backgroundJobs === false &&
    /background-support="disable"/.test(config));

check('smartDownloads permanece falso', manifest.offline.smartDownloads === false);

section('Notas');

check('as notas registram que nada foi validado em TV física',
    manifest.notes.some(function (note) { return /nao foi validad|nenhuma capability foi validada/i.test(note); }));

/* Um valor verdadeiro sem explicação é o que produz a próxima divergência. */
check('offline verdadeiro vem acompanhado da condição de USB',
    manifest.offline.supported === false ||
    manifest.notes.some(function (note) { return /USB/i.test(note) && /removivel|removível|montado/i.test(note); }));

/*
  As duas notas abaixo ja envelheceram uma vez cada.

  O cabecalho deste arquivo conta o caso do `seek`, declarado falso enquanto o
  player ja saltava. Aconteceu de novo em dois lugares, e por isso eles ganham
  teste: uma nota que descreve a ausencia de uma funcao *implementada* e pior do
  que nenhuma nota, porque quem a le decide nao procurar o codigo.
*/
check('a nota de capas descreve o cache em USB que existe',
    manifest.notes.some(function (note) {
        return /capas|artwork/i.test(note) && /USB/i.test(note);
    }) && appSource('app.js').indexOf('BuroArtworkCache') >= 0);

/*
  Cast de fluxo continua impossivel e a nota deve continuar dizendo isso. O que
  ela nao pode mais dizer e que a TV nao oferece nada: 'Enviar a tela' existe e
  manda a identidade do titulo, como no Windows.
*/
check('a nota de Cast separa o fluxo, impossivel, do envio de titulo, que existe',
    manifest.notes.some(function (note) {
        return /BURO Cast/i.test(note) && /Enviar a tela|Enviar à tela/i.test(note);
    }) && appSource('app.js').indexOf('send-to-screen') >= 0);

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falharam, ' + passed + ' aprovados\n');
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
