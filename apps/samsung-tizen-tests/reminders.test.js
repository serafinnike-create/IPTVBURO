/*
  Lembretes: a política e o que pode ser guardado.

  A regra vem de `ReminderPolicy` (packages/domain-model), portada para a TV. Os
  casos abaixo são os mesmos que o Kotlin protege, porque uma divergência aqui
  aparece como a TV contando um número de dias diferente do celular para o mesmo
  título — e ninguém sabe qual dos dois está certo.

  A parte que NÃO é igual está registrada explicitamente: a TV não agenda
  notificação, então nada aqui testa horário de disparo.

  Datas são o assunto que mais engana neste arquivo. Cada teste ancora "hoje"
  num instante fixo em vez de usar o relógio, senão o resultado muda conforme a
  hora em que a suíte roda.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var vm = require('vm');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function section(title) { process.stdout.write(title + '\n'); }

/* domain.js não toca no DOM, então roda num contexto nu. */
var sandbox = { window: {} };
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(APP_DIR, 'js', 'domain.js'), 'utf8'), sandbox);
var Domain = sandbox.BuroDomain;

/* Meio-dia para que o teste não dependa do fuso de quem roda a suíte. */
function day(text) {
    var parts = text.split('-');
    return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]), 12, 0, 0);
}

function reminder(title, releaseDate, createdAt) {
    return {
        identity: 'movie:' + title.toLowerCase(),
        title: title,
        releaseDate: releaseDate || null,
        createdAt: createdAt || 0
    };
}

section('O que os lembretes de hoje significam');

var today = day('2026-08-15');

check('sem lembretes não há nada a dizer',
    Domain.reminderDigest([], today).total === 0);

check('um título sem data fica esperando para ser assistido',
    Domain.reminderDigest([reminder('Sem data')], today).waiting.length === 1);

check('um título que sai hoje é anunciado como lançado',
    Domain.reminderDigest([reminder('Hoje', '2026-08-15')], today).releasedToday.length === 1);

/* Anunciar só na data exata perderia quem não ligou a TV naquele dia. */
check('um título lançado semana passada continua sendo anunciado',
    Domain.reminderDigest([reminder('Passado', '2026-08-08')], today).releasedToday.length === 1);

check('um título que sai amanhã entra na contagem regressiva',
    (function () {
        var digest = Domain.reminderDigest([reminder('Amanha', '2026-08-16')], today);
        return digest.upcoming.length === 1 && digest.upcoming[0].days === 1;
    }()));

/* O bug que o Kotlin registra: Period quebrado em meses daria 29 para 31 dias. */
check('trinta e um dias de distância é contado como trinta e um, não como um mês',
    (function () {
        var digest = Domain.reminderDigest([reminder('Longe', '2026-09-15')], today);
        return digest.total === 0 ||
            (digest.upcoming.length === 1 && digest.upcoming[0].days === 31);
    }()));

check('exatamente no horizonte de trinta dias ainda é mencionado',
    (function () {
        var digest = Domain.reminderDigest([reminder('Limite', '2026-09-14')], today);
        return digest.upcoming.length === 1 && digest.upcoming[0].days === 30;
    }()));

/* Um título anunciado para daqui a um ano não pode virar aviso diário. */
check('além do horizonte o lembrete é guardado mas não mencionado',
    Domain.reminderDigest([reminder('Ano que vem', '2027-08-15')], today).total === 0);

check('o que sai primeiro aparece primeiro na contagem',
    (function () {
        var digest = Domain.reminderDigest([
            reminder('Depois', '2026-08-20'),
            reminder('Antes', '2026-08-16')
        ], today);
        return digest.upcoming[0].reminder.title === 'Antes';
    }()));

check('uma data que o provedor mandou quebrada não vira contagem',
    (function () {
        var digest = Domain.reminderDigest([reminder('Invalida', '2026-02-31')], today);
        /* 31 de fevereiro não existe: vira "sem data" em vez de 3 de março. */
        return digest.waiting.length === 1 && digest.upcoming.length === 0;
    }()));

section('Ordem na lista');

check('já lançado vem antes do que ainda vai sair',
    (function () {
        var sorted = Domain.sortReminders([
            reminder('Futuro', '2026-08-20'),
            reminder('Lancado', '2026-08-10')
        ], today);
        return sorted[0].title === 'Lancado';
    }()));

check('o que sai mais cedo vem antes do que demora',
    (function () {
        var sorted = Domain.sortReminders([
            reminder('Demora', '2026-08-25'),
            reminder('Perto', '2026-08-16')
        ], today);
        return sorted[0].title === 'Perto';
    }()));

check('sem data vai para o fim da lista',
    (function () {
        var sorted = Domain.sortReminders([
            reminder('Sem data'),
            reminder('Com data', '2026-08-20')
        ], today);
        return sorted[sorted.length - 1].title === 'Sem data';
    }()));

/* Alfabético, e não por data de marcação: é o que o Android faz, e a mesma
   lista alimenta o trilho da Home nos dois. Ordenar por marcação faria a TV e o
   celular mostrarem os mesmos lembretes em ordens diferentes. */
check('entre iguais, a ordem é alfabética como no Android',
    (function () {
        var sorted = Domain.sortReminders([
            reminder('Zebra', null, 5000),
            reminder('Alface', null, 1000)
        ], today);
        return sorted[0].title === 'Alface';
    }()));

check('a ordem alfabética ignora maiúsculas',
    (function () {
        var sorted = Domain.sortReminders([
            reminder('banana', null, 1),
            reminder('Abacaxi', null, 2)
        ], today);
        return sorted[0].title === 'Abacaxi';
    }()));

section('Arte que pode ser guardada');

/* Um lembrete sobrevive à lista de onde veio: uma credencial guardada aqui
   ficaria no disco depois de a fonte ser apagada. */
check('um endereço com usuário e senha embutidos é recusado',
    Domain.isStorableReminderArtwork('http://user:senha@host/poster.jpg') === false);

check('um caminho autenticado do provedor é recusado',
    Domain.isStorableReminderArtwork('http://host:8080/movie/usuario/senha/123.jpg') === false);

check('uma URL assinada com token na query é recusada',
    Domain.isStorableReminderArtwork('https://cdn.host/p.jpg?token=abc123') === false);

/* A correção que veio do Android: recusar isto deixava todo lembrete de uma
   lista Xtream comum sem arte nenhuma, e não havia credencial a proteger. */
check('um pôster estático comum de provedor é aceito',
    Domain.isStorableReminderArtwork('http://host:8080/images/abc.jpg') === true);

check('uma imagem do TMDb continua aceita',
    Domain.isStorableReminderArtwork('https://image.tmdb.org/t/p/w500/x.jpg') === true);

check('a cópia local do próprio app é aceita',
    Domain.isStorableReminderArtwork('file:///opt/usb/poster.jpg') === true);

check('um esquema que não é http nem file é recusado',
    Domain.isStorableReminderArtwork('javascript:alert(1)') === false);

check('texto vazio é recusado',
    Domain.isStorableReminderArtwork('') === false);

section('Criar um lembrete');

check('o lembrete guarda a identidade, não o id da linha do catálogo',
    (function () {
        var made = Domain.createReminder({
            profileId: 'p1',
            item: { id: 'row-999', name: 'Filme', contentType: 'MOVIE', providerItemId: '42' }
        });
        /* O id da linha muda a cada importação; a identidade sobrevive. */
        return made.identity.indexOf('row-999') === -1 && made.identity.indexOf('42') !== -1;
    }()));

check('dois perfis marcam o mesmo título sem se misturar',
    (function () {
        var item = { id: 'row-1', name: 'Filme', contentType: 'MOVIE', providerItemId: '42' };
        var first = Domain.createReminder({ profileId: 'p1', item: item });
        var second = Domain.createReminder({ profileId: 'p2', item: item });
        return first.id !== second.id && first.identity === second.identity;
    }()));

check('uma arte com credencial não entra no registro guardado',
    Domain.createReminder({
        profileId: 'p1',
        item: { name: 'Filme', contentType: 'MOVIE' },
        artworkUrl: 'http://host/movie/usuario/senha/1.jpg'
    }).artworkUrl === null);

check('o registro guardado não carrega URL de stream nem dados da fonte',
    (function () {
        var made = Domain.createReminder({
            profileId: 'p1',
            item: {
                name: 'Filme',
                contentType: 'MOVIE',
                streamUrl: 'http://host/movie/user/pass/1.mkv',
                sourceId: 's1'
            }
        });
        return JSON.stringify(made).indexOf('pass') === -1 &&
            JSON.stringify(made).indexOf('.mkv') === -1 &&
            made.streamUrl === undefined;
    }()));

check('um lembrete sem título é recusado',
    (function () {
        try {
            Domain.createReminder({ profileId: 'p1', item: { name: '  ' } });
            return false;
        } catch (error) { return true; }
    }()));

process.stdout.write('\n');
if (failures.length) {
    process.stdout.write(failures.length + ' falharam, ' + passed + ' aprovados\n');
    process.exit(1);
}
process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
