/* A central de avisos do sino. Mesmas regras do NotificationCentre compartilhado. */
var BuroNotifications = (function () {
    'use strict';

    /* Suficiente para uma temporada de novidades, pouco para rolar de uma vez. */
    var MAX_HELD = 50;
    var MAX_TITLE = 240;
    var MAX_BODY = 400;
    var KINDS = { REMINDER: true, NEW_EPISODE: true, NEW_SEASON: true };

    function trim(value) {
        if (typeof BuroDomain !== 'undefined' && BuroDomain.trim) { return BuroDomain.trim(value); }
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /*
      O id de um digest de lembretes num dia.

      Chaveado pela data, não pelos títulos: o digest é "você tem coisas
      esperando", um por dia, e quem descartou o aviso não deve vê-lo voltar
      porque marcou outro filme uma hora depois.
    */
    function reminderDigestId(isoDate) {
        return 'reminder-digest:' + trim(isoDate);
    }

    /* Um aviso por episódio, quantas vezes ele for notado. */
    function episodeId(seriesKey, season, episode) {
        return 'episode:' + trim(seriesKey) + ':s' + Number(season) + ':e' + Number(episode);
    }

    function seasonId(seriesKey, season) {
        return 'season:' + trim(seriesKey) + ':s' + Number(season);
    }

    /*
      Um aviso saneado, ou null.

      Nada entra na central sem id, tipo conhecido e título: uma linha sem
      título seria um espaço em branco que o usuário não consegue interpretar,
      e um id ausente quebraria a regra que impede duplicatas.
    */
    function notification(input) {
        var id = trim(input && input.id);
        var kind = trim(input && input.kind);
        var title = trim(input && input.title);
        var body = trim(input && input.body);
        var createdAt = Number(input && input.createdAt);
        if (!id || !KINDS[kind] || !title) { return null; }
        return {
            id: id.substring(0, 200),
            kind: kind,
            title: title.substring(0, MAX_TITLE),
            body: body ? body.substring(0, MAX_BODY) : null,
            createdAt: isFinite(createdAt) && createdAt > 0 ? Math.floor(createdAt) : 0,
            read: Boolean(input && input.read)
        };
    }

    /* Lê uma central gravada, descartando o que não é aviso válido. */
    function sanitize(rows) {
        var seen = {};
        var result = [];
        (Array.isArray(rows) ? rows : []).forEach(function (row) {
            var value = notification(row);
            if (!value || seen[value.id]) { return; }
            seen[value.id] = true;
            result.push(value);
        });
        return result;
    }

    /* Mais novo primeiro, a única ordem em que uma lista de novidades cabe. */
    function newestFirst(rows) {
        return sanitize(rows).slice().sort(function (left, right) {
            return (right.createdAt - left.createdAt) || left.id.localeCompare(right.id);
        });
    }

    function unreadCount(rows) {
        var count = 0;
        sanitize(rows).forEach(function (row) { if (!row.read) { count += 1; } });
        return count;
    }

    /*
      Acrescenta um aviso, a menos que o id já esteja guardado.

      A regra sobre a qual tudo se apoia: o digest de lembretes é reconstruído a
      cada abertura, então sem isto o sino encheria de cópias de uma só novidade.
      Uma entrada existente fica como está — inclusive se já foi lida — porque
      quem já viu algo não pode tê-lo marcado como não lido por uma reconstrução.
    */
    function add(rows, input) {
        var current = sanitize(rows);
        var value = notification(input);
        var exists = false;
        if (!value) { return current; }
        current.forEach(function (row) { if (row.id === value.id) { exists = true; } });
        if (exists) { return current; }
        return trimmed(current.concat([value]));
    }

    function markAllRead(rows) {
        return sanitize(rows).map(function (row) {
            return {
                id: row.id, kind: row.kind, title: row.title, body: row.body,
                createdAt: row.createdAt, read: true
            };
        });
    }

    /* O usuário pediu para sair, então sai — não é escondido. */
    function remove(rows, id) {
        var target = trim(id);
        return sanitize(rows).filter(function (row) { return row.id !== target; });
    }

    function clear() { return []; }

    /*
      Descarta os mais antigos quando a lista passa de MAX_HELD.

      Um sino que ninguém esvazia cresceria sem fim, e o centésimo lembrete mais
      antigo não serve a ninguém. Os lidos saem primeiro: algo ainda não lido é
      novidade que o usuário não viu, e descartá-lo para guardar um item mais
      antigo que ele já leu seria exatamente ao contrário.
    */
    function trimmed(rows) {
        var current = sanitize(rows);
        var ordered;
        var dropped = {};
        var surplus = current.length - MAX_HELD;
        if (surplus <= 0) { return current; }
        ordered = current.slice().sort(function (left, right) {
            if (left.read !== right.read) { return left.read ? -1 : 1; }
            return left.createdAt - right.createdAt;
        });
        ordered.slice(0, surplus).forEach(function (row) { dropped[row.id] = true; });
        return current.filter(function (row) { return !dropped[row.id]; });
    }

    return {
        MAX_HELD: MAX_HELD,
        reminderDigestId: reminderDigestId,
        episodeId: episodeId,
        seasonId: seasonId,
        notification: notification,
        sanitize: sanitize,
        newestFirst: newestFirst,
        unreadCount: unreadCount,
        add: add,
        markAllRead: markAllRead,
        remove: remove,
        clear: clear,
        trimmed: trimmed
    };
}());
