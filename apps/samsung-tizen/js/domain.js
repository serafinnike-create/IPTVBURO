/* Domain contracts shared conceptually with packages/domain-model. ES5 only. */
var BuroDomain = (function () {
    'use strict';

    var CONTENT = {
        LIVE: 'LIVE',
        MOVIE: 'MOVIE',
        SERIES: 'SERIES',
        EPISODE: 'EPISODE',
        UNKNOWN: 'UNKNOWN'
    };

    var SOURCE = {
        LOCAL_M3U: 'LOCAL_M3U',
        REMOTE_M3U: 'REMOTE_M3U',
        XTREAM: 'XTREAM',
        STALKER: 'STALKER'
    };

    var SECTIONS = [
        'HOME', 'LIVE', 'MOVIES', 'SERIES', 'SEARCH', 'MY_BURO',
        'CONTINUE_WATCHING', 'HISTORY', 'PROFILES', 'SOURCES', 'SETTINGS'
    ];

    function clamp(value, minimum, maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    function trim(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    function foldAccents(value) {
        return trim(value).toLowerCase()
            .replace(/[àáâãäå]/g, 'a')
            .replace(/[èéêë]/g, 'e')
            .replace(/[ìíîï]/g, 'i')
            .replace(/[òóôõö]/g, 'o')
            .replace(/[ùúûü]/g, 'u')
            .replace(/[ç]/g, 'c')
            .replace(/[ñ]/g, 'n')
            .replace(/[^a-z0-9]+/g, ' ')
            .replace(/^\s+|\s+$/g, '');
    }

    /*
      A mesma identidade de prateleira de `LibraryMatching.kt`.

      Ela e mais agressiva do que a identidade de playback de proposito: aqui
      uma chave igual apenas evita desenhar quatro posters identicos. Os itens
      continuam intactos no IndexedDB e a preferencia permite voltar a lista
      bruta. Numeros e subtitulos longos ficam, portanto sequencias diferentes
      nao desaparecem.
    */
    function shelfDeduplicationKey(value) {
        var working = String(value == null ? '' : value);
        var parts;
        var longest;
        if (working.indexOf('|') >= 0) {
            parts = working.split('|');
            longest = parts[0] || '';
            parts.forEach(function (part) {
                if (trim(part).length > trim(longest).length) { longest = part; }
            });
            working = longest;
        }
        working = working.replace(/[\[(][A-Za-z0-9]{1,4}[\])]/g, ' ');
        working = working.replace(/\s+[A-Za-z]{1,2}\s*$/g, ' ');
        working = working.replace(/\b(hevc|h\.?26[45]|x26[45]|av1|dv|hdr10?\+?|atmos|multi|ac3|aac|eac3)\b/gi, ' ');
        working = working.toLowerCase()
            .replace(/[\[(]?\b(19|20)\d{2}\b[\])]?/g, ' ')
            .replace(/\b(4k|uhd|hd|sd|fhd|1080p?|720p?|480p?|2160p?|dublado|dual|legendado|leg|nacional|dub|bluray|blu-ray|webrip|web-dl|webdl|hdrip|dvdrip|remux|imax|extended|remaster(?:ed|izado)?)\b/gi, ' ');
        return foldAccents(working).replace(/[^a-z0-9]+/g, '');
    }

    function shelfItemDeduplicationKey(item) {
        var title = shelfDeduplicationKey(item && item.name);
        var year = Number(item && item.year);
        var safeYear = year >= 1800 && year <= 3000 ? String(Math.floor(year)) : '';
        if (!title) { return ''; }
        return String(item && item.contentType || CONTENT.UNKNOWN) + ':' + title + ':' + safeYear;
    }

    function collapseShelfDuplicates(items) {
        var seen = Object.create(null);
        return (items || []).filter(function (item) {
            var key = shelfItemDeduplicationKey(item);
            if (!key) { return true; }
            if (Object.prototype.hasOwnProperty.call(seen, key)) { return false; }
            seen[key] = true;
            return true;
        });
    }

    function stableHash(value) {
        var text = String(value == null ? '' : value);
        var hash = 2166136261;
        var index;
        for (index = 0; index < text.length; index += 1) {
            hash ^= text.charCodeAt(index);
            hash += (hash << 1) + (hash << 4) + (hash << 7) +
                (hash << 8) + (hash << 24);
        }
        return (hash >>> 0).toString(36);
    }

    function id(prefix, seed) {
        return prefix + '-' + stableHash(seed);
    }

    function safeId(value) {
        return /^[A-Za-z0-9._-]{1,120}$/.test(String(value || ''));
    }

    function safeYouTubeId(value) {
        var candidate = trim(value);
        return /^[A-Za-z0-9_-]{6,32}$/.test(candidate) ? candidate : null;
    }

    function queryValue(query, name) {
        var rows = String(query || '').replace(/^\?/, '').split('&');
        var result = null;
        rows.some(function (row) {
            var parts = row.split('=');
            var key;
            try { key = decodeURIComponent(parts.shift() || ''); }
            catch (ignoredKey) { key = ''; }
            if (key !== name) { return false; }
            try { result = decodeURIComponent(parts.join('=') || ''); }
            catch (ignoredValue) { result = null; }
            return true;
        });
        return result;
    }

    /* Aceita somente os formatos públicos que o adapter Kotlin aceita. */
    function sanitizeYouTubeReference(reference) {
        var candidate = trim(reference);
        var anchor;
        var host;
        var path;
        var parts;
        var candidateId = safeYouTubeId(candidate);
        var index;
        if (candidateId) { return candidateId; }
        if (candidate.length < 6 || candidate.length > 512 || !/^https?:\/\//i.test(candidate)) { return null; }
        try {
            anchor = document.createElement('a');
            anchor.href = candidate;
            host = String(anchor.hostname || '').toLowerCase().replace(/\.$/, '');
            path = String(anchor.pathname || '').replace(/^\/+/, '');
            parts = path.split('/').filter(function (part) { return Boolean(part); });
            if (host === 'youtu.be') { return safeYouTubeId(parts[0]); }
            if (host !== 'youtube.com' && !/\.youtube\.com$/.test(host)) { return null; }
            candidateId = safeYouTubeId(queryValue(anchor.search, 'v'));
            if (candidateId) { return candidateId; }
            for (index = 0; index + 1 < parts.length; index += 1) {
                if (parts[index] === 'embed' || parts[index] === 'shorts') {
                    return safeYouTubeId(parts[index + 1]);
                }
            }
        } catch (ignoredUrl) { return null; }
        return null;
    }

    function contentIdentity(item) {
        var type = item && item.contentType ? item.contentType : CONTENT.UNKNOWN;
        var provider = item && item.providerItemId ? item.providerItemId : '';
        var year = item && item.year ? String(item.year) : '';
        if (provider) {
            return type.toLowerCase() + ':' + provider;
        }
        return type.toLowerCase() + ':' + foldAccents(item && item.name) + ':' + year;
    }

    function createProfile(input) {
        var name = trim(input && input.name);
        if (!name) {
            throw new Error('PROFILE_NAME_REQUIRED');
        }
        return {
            id: input.id || id('profile', name + ':' + Date.now()),
            name: name.substring(0, 40),
            avatarKey: trim(input.avatarKey) || 'gold',
            isKids: Boolean(input.isKids),
            sourceId: input.sourceId || null,
            createdAt: input.createdAt || Date.now()
        };
    }

    function createSourceMetadata(input) {
        var sourceType = input && input.type;
        var name = trim(input && input.name);
        if (!name) {
            throw new Error('SOURCE_NAME_REQUIRED');
        }
        if (sourceType !== SOURCE.LOCAL_M3U && sourceType !== SOURCE.REMOTE_M3U &&
                sourceType !== SOURCE.XTREAM && sourceType !== SOURCE.STALKER) {
            throw new Error('SOURCE_TYPE_INVALID');
        }
        return {
            id: input.id || id('source', sourceType + ':' + name + ':' + Date.now()),
            name: name.substring(0, 80),
            type: sourceType,
            channelCount: Number(input.channelCount) || 0,
            createdAt: input.createdAt || Date.now(),
            updatedAt: input.updatedAt || null
        };
    }

    function createItem(input) {
        var name = trim(input && input.name);
        var addedAt = Number(input && input.addedAt);
        var sortOrder = Number(input && input.sortOrder);
        if (!name) {
            throw new Error('ITEM_NAME_REQUIRED');
        }
        if (!isFinite(addedAt) || addedAt <= 0) { addedAt = Date.now(); }
        if (!isFinite(sortOrder) || sortOrder < 0) { sortOrder = 0; }
        return {
            id: input.id || id('item', input.sourceId + ':' + input.providerItemId + ':' + name),
            sourceId: String(input.sourceId || ''),
            categoryId: input.categoryId == null ? null : String(input.categoryId),
            name: name.substring(0, 240),
            contentType: input.contentType || CONTENT.UNKNOWN,
            providerItemId: input.providerItemId == null ? null : String(input.providerItemId),
            locator: input.locator || null,
            /*
              A capa, peneirada aqui e não só em quem chama.

              O item é gravado, então esta URL vai para o disco. A peneira recusa
              usuário:senha@host, os caminhos autenticados do provedor
              (/movie/<usuario>/<senha>/<id>) e toda query string que não seja
              puro dimensionamento de imagem — a mesma regra dos lembretes. Fica
              neste ponto porque é por onde todo item passa: um adapter novo que
              esqueça de filtrar não consegue gravar credencial por descuido.
            */
            logoUrl: isStorableReminderArtwork(input && input.logoUrl) ?
                trim(input.logoUrl) : null,
            genre: trim(input.genre) || null,
            year: input.year == null ? null : Number(input.year),
            rating: input.rating == null ? null : Number(input.rating),
            sortOrder: Math.floor(sortOrder),
            addedAt: Math.floor(addedAt)
        };
    }

    /* Mesma proteção usada pelo Android antes de abrir uma variante pesada no player. */
    function hasHighRiskVideoTag(value) {
        return /(?:\b4k\b|\buhd\b|\bhevc\b|\bh\.?265\b|\[dv\]|\[hdr\])/i.test(String(value || ''));
    }

    function compatibilityTitlePrefix(value) {
        return trim(String(value || '')
            .replace(/(?:\b4k\b|\buhd\b|\bhevc\b|\bh\.?265\b|\[dv\]|\[hdr\])/ig, ' ')
            .replace(/\[[^\]]+\]/g, ' ')
            .replace(/\s+/g, ' '));
    }

    function availableGenres(items) {
        var labels = {};
        (items || []).forEach(function (item) {
            String(item.genre || '').split(/[,\/|;]/).forEach(function (part) {
                var clean = trim(part);
                var key = foldAccents(clean);
                if (clean && !labels[key]) { labels[key] = clean; }
            });
        });
        return Object.keys(labels).map(function (key) { return labels[key]; }).sort(function (a, b) {
            return foldAccents(a) < foldAccents(b) ? -1 : (foldAccents(a) > foldAccents(b) ? 1 : 0);
        });
    }

    function availableYears(items) {
        var found = {};
        (items || []).forEach(function (item) {
            var year = Number(item.year);
            if (year >= 1800 && year <= 3000) { found[year] = true; }
        });
        return Object.keys(found).map(Number).sort(function (a, b) { return b - a; });
    }

    function applyCatalogueFilter(items, filter) {
        var selected = filter || {};
        var genre = foldAccents(selected.genre || '');
        var year = selected.year == null ? null : Number(selected.year);
        var sort = selected.sort || 'provider';
        var rows = (items || []).filter(function (item) {
            return (!genre || foldAccents(item.genre || '').indexOf(genre) >= 0) &&
                (year == null || Number(item.year) === year);
        });
        function title(row) { return foldAccents(row.name || ''); }
        function tie(a, b) {
            var byTitle = title(a) < title(b) ? -1 : (title(a) > title(b) ? 1 : 0);
            if (byTitle) { return byTitle; }
            return String(a.id) < String(b.id) ? -1 : (String(a.id) > String(b.id) ? 1 : 0);
        }
        if (sort === 'title-asc') { rows.sort(tie); }
        else if (sort === 'title-desc') {
            rows.sort(function (a, b) {
                var aTitle = title(a);
                var bTitle = title(b);
                if (aTitle !== bTitle) { return aTitle < bTitle ? 1 : -1; }
                return String(a.id) < String(b.id) ? -1 : (String(a.id) > String(b.id) ? 1 : 0);
            });
        } else if (sort === 'year-desc' || sort === 'year-asc') {
            rows.sort(function (a, b) {
                var aYear = Number(a.year);
                var bYear = Number(b.year);
                var aMissing = !aYear;
                var bMissing = !bYear;
                if (aMissing !== bMissing) { return aMissing ? 1 : -1; }
                if (aYear !== bYear) { return sort === 'year-desc' ? bYear - aYear : aYear - bYear; }
                return tie(a, b);
            });
        } else if (sort === 'rating-desc') {
            rows.sort(function (a, b) {
                var aRating = Number(a.rating);
                var bRating = Number(b.rating);
                var aMissing = !isFinite(aRating) || a.rating == null;
                var bMissing = !isFinite(bRating) || b.rating == null;
                if (aMissing !== bMissing) { return aMissing ? 1 : -1; }
                if (aRating !== bRating) { return bRating - aRating; }
                return tie(a, b);
            });
        }
        return rows;
    }

    function playbackPercent(positionMs, durationMs) {
        var duration = Number(durationMs) || 0;
        if (duration <= 0) { return 0; }
        return clamp((Number(positionMs) || 0) / duration, 0, 1);
    }

    function playbackCompleted(positionMs, durationMs) {
        var duration = Number(durationMs) || 0;
        var position = clamp(Number(positionMs) || 0, 0, Math.max(0, duration));
        var percent = playbackPercent(position, duration);
        if (duration <= 0) { return false; }
        return percent >= 0.90 || (duration >= 600000 && percent >= 0.50 && duration - position <= 300000);
    }

    /* Mantém os mesmos limites de PlaybackProgressPolicy no domínio Kotlin. */
    function resumeDecision(progress, seekable) {
        var position;
        var duration;
        var percent;
        if (!progress || progress.completed || seekable === false) { return { kind: 'start', positionMs: 0 }; }
        position = Number(progress.positionMs) || 0;
        duration = Number(progress.durationMs) || 0;
        percent = playbackPercent(position, duration);
        if (duration > 0 && position >= 30000 && percent >= 0.02 && !playbackCompleted(position, duration)) {
            return { kind: 'resume', positionMs: position, percent: percent };
        }
        return { kind: 'start', positionMs: 0 };
    }

    /*
      Lembretes.

      Porte da mesma política de `ReminderPolicy` (packages/domain-model), com uma
      diferença de plataforma que muda o que a função entrega.

      No Android o que sustenta um lembrete é a notificação diária. Numa TV isso
      não existe: o config.xml declara `background-support="disable"`, então nada
      roda com o app fechado, e uma TV desligada não avisa ninguém. Portanto aqui
      o digest não agenda nada — ele é lido uma vez, quando o app abre, e vira o
      aviso que o usuário vê ao entrar. A lista marcada continua sendo a função
      principal.

      Por isso `nextNotificationAt` não foi portado: sem agendador, uma função que
      calcula um horário seria uma promessa que ninguém cumpre.
    */

    /* Além disso um título anunciado para daqui a um ano viraria contagem
       regressiva todo dia até chegar. Passando disso o lembrete continua
       guardado — ele ainda aparece quando a data chega — só deixa de ser
       mencionado no aviso. Mesmo valor do Android. */
    var COUNTDOWN_HORIZON_DAYS = 30;

    /*
      Os horizontes que a interface oferece.

      Um dia e "so na vespera"; noventa cobre uma temporada inteira anunciada
      com antecedencia. Sao poucos de proposito: um campo numerico livre
      exigiria o teclado da TV para uma escolha que cabe em quatro botoes.
    */
    var REMINDER_HORIZON_CHOICES = [1, 7, 30, 90];

    /* Compara datas pelo dia local, não pelo instante: dois horários do mesmo dia
       precisam contar como zero dia de diferença, e um Date guarda o milissegundo.
       `releaseDate` chega como 'YYYY-MM-DD', que é o que o provedor devolve. */
    /* Aceita um Date ou o texto 'YYYY-MM-DD'. O teste é por comportamento e não
       por `instanceof`: um Date vindo de outro contexto de execução — o iframe
       do trailer, ou o sandbox dos testes — falha no `instanceof` mesmo sendo
       uma data perfeitamente válida, e o resultado seria tratá-la como ausente. */
    function startOfLocalDay(value) {
        var date = value && typeof value.getFullYear === 'function' ?
            value : parseReleaseDate(value);
        if (!date || isNaN(date.getTime())) { return null; }
        return new Date(date.getFullYear(), date.getMonth(), date.getDate());
    }

    function parseReleaseDate(value) {
        var text = trim(value);
        var parts;
        var year;
        var month;
        var day;
        var date;
        if (!text) { return null; }
        parts = text.substring(0, 10).split('-');
        if (parts.length !== 3) { return null; }
        year = parseInt(parts[0], 10);
        month = parseInt(parts[1], 10);
        day = parseInt(parts[2], 10);
        if (!isFinite(year) || !isFinite(month) || !isFinite(day)) { return null; }
        /* Construção local e conferência: `new Date(2026, 1, 31)` vira 3 de março
           em silêncio, e uma data inválida do provedor não deve virar contagem. */
        date = new Date(year, month - 1, day);
        if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
            return null;
        }
        return date;
    }

    function daysBetween(from, to) {
        /* Divisão por dia inteiro sobre datas já normalizadas ao meio-dia local.
           Somar 12 h antes da divisão absorve a hora que o horário de verão tira
           ou acrescenta, senão uma virada de fuso vira um dia a mais ou a menos. */
        var start = new Date(from.getFullYear(), from.getMonth(), from.getDate(), 12);
        var end = new Date(to.getFullYear(), to.getMonth(), to.getDate(), 12);
        return Math.round((end.getTime() - start.getTime()) / 86400000);
    }

    /*
      O que os lembretes de hoje significam.

      Devolve sempre a mesma forma; `total` em zero é o "silêncio" do Android —
      nada a dizer, e o app não deve mostrar aviso nenhum.
    */
    /*
      `horizonDays` e opcional e cai no padrao de trinta.

      Era fixo. Trinta dias serve para quem marca lancamentos, e nao serve
      para quem marca a estreia da temporada de dezembro em agosto: o
      lembrete fica guardado, sem virar aviso, ate faltar um mes — e ate la
      a pessoa nao sabe se o aplicativo ainda o tem.

      Quem prefere ser avisado so na vespera tambem existe, e para essa
      pessoa trinta dias de contagem regressiva sao ruido diario.
    */
    function reminderDigest(reminders, now, horizonDays) {
        var horizon = Number(horizonDays) > 0 ? Number(horizonDays) : COUNTDOWN_HORIZON_DAYS;
        /* Um relógio impossível não pode derrubar a Home: sem hoje utilizável,
           ninguém consegue dizer o que já saiu, então tudo fica apenas marcado. */
        var today = startOfLocalDay(now || new Date()) || startOfLocalDay(new Date());
        var waiting = [];
        var releasedToday = [];
        var upcoming = [];
        (reminders || []).forEach(function (reminder) {
            var release = reminder && reminder.releaseDate ? startOfLocalDay(reminder.releaseDate) : null;
            var days;
            if (!release) {
                /* Sem data: "me lembre disto", algo que já está na biblioteca.
                   Uma data que o provedor mandou quebrada cai aqui também —
                   `startOfLocalDay` devolve null e o lembrete continua na lista
                   em vez de virar uma contagem inventada ou derrubar a tela. */
                waiting.push(reminder);
                return;
            }
            days = daysBetween(today, release);
            if (days <= 0) {
                /* No dia e em qualquer dia depois. Anunciar só na data exata
                   perderia quem não abriu o app naquele dia, e "já saiu"
                   continua verdadeiro depois. */
                releasedToday.push(reminder);
                return;
            }
            if (days <= horizon) {
                upcoming.push({ reminder: reminder, days: days });
            }
        });
        upcoming.sort(function (left, right) { return left.days - right.days; });
        return {
            waiting: waiting,
            releasedToday: releasedToday,
            upcoming: upcoming,
            total: waiting.length + releasedToday.length + upcoming.length
        };
    }

    /*
      Como um título marcado aparece na lista.

      Já saiu primeiro, depois o que está mais perto de sair, e por último o que
      não tem data. Dentro de cada grupo, ordem alfabética.

      O desempate é por título e não pela data de marcação de propósito: é o que
      o Android faz, e a mesma lista alimenta o trilho da Home nos dois. Ordenar
      por marcação faria a TV e o celular mostrarem os mesmos lembretes em ordens
      diferentes, sem que nenhum estivesse errado.
    */
    function sortReminders(reminders, now) {
        var today = startOfLocalDay(now || new Date()) || startOfLocalDay(new Date());
        return (reminders || []).slice().sort(function (left, right) {
            var leftRank = reminderRank(left, today);
            var rightRank = reminderRank(right, today);
            if (leftRank !== rightRank) { return leftRank - rightRank; }
            return String(left.title || '').toLowerCase()
                .localeCompare(String(right.title || '').toLowerCase());
        });
    }

    function reminderRank(reminder, today) {
        var release = reminder && reminder.releaseDate ? startOfLocalDay(reminder.releaseDate) : null;
        var days;
        /* Sem data — ou com uma data que não existe — vai para o fim. */
        if (!release) { return 1000000; }
        days = daysBetween(today, release);
        /* Lançados agrupam em -1 para que a ordem entre eles caia na data de
           marcação, e não em quanto tempo faz que saíram. */
        return days <= 0 ? -1 : days;
    }

    /*
      A arte de um lembrete pode ser guardada?

      Um lembrete sobrevive à lista de onde veio, então um endereço com a
      credencial do assinante deixaria um segredo no disco muito depois da fonte
      ter sido apagada. Mesma correção aplicada no Android: o teste é pela
      credencial, não pelo host.

      Recusar tudo que não fosse TMDb — a regra do link de compartilhamento —
      seria estrito demais aqui. Um compartilhamento viaja para outra pessoa; um
      lembrete nunca sai do aparelho. Numa lista Xtream comum o pôster é um
      endereço estático simples, e recusá-lo fazia cada lembrete ficar sem arte
      nenhuma, sendo que ali não havia credencial alguma para proteger.
    */
    /* Parametros de imagem que nao identificam ninguem: dimensao, recorte,
       qualidade, formato e versao. Qualquer outro nome derruba a URL. */
    var IMAGE_QUERY_KEYS = {
        w: true, h: true, width: true, height: true, size: true, s: true,
        q: true, quality: true, fit: true, crop: true, format: true, fm: true,
        v: true, ver: true, version: true, rev: true, dpr: true
    };

    function onlySizingParameters(url) {
        var query = url.substring(url.indexOf('?') + 1);
        var parts;
        if (!query || query.length > 200) { return false; }
        parts = query.split('&');
        /* Poucos parametros: uma URL assinada costuma trazer varios, e limitar
           aqui fecha a porta para uma combinacao inesperada. */
        if (parts.length > 4) { return false; }
        return parts.every(function (part) {
            var name = part.split('=')[0].toLowerCase();
            var value = part.substring(part.indexOf('=') + 1);
            if (!name || !IMAGE_QUERY_KEYS[name]) { return false; }
            /* O valor tambem e conferido: `w=300` passa, `w=<algo longo>` nao —
               um token nao vira inofensivo por estar num parametro de nome
               conhecido. */
            return /^[A-Za-z0-9._-]{1,16}$/.test(value);
        });
    }

    function isStorableReminderArtwork(value) {
        var url = trim(value);
        if (!url || url.length > 4096) { return false; }
        if (/^file:\/\//i.test(url)) { return true; }
        if (!/^https?:\/\//i.test(url)) { return false; }
        /* user:senha@host — a forma mais direta de carregar uma credencial. */
        if (/^https?:\/\/[^\/\s]*@/i.test(url)) { return false; }
        /*
          A query string, quando ela e so tamanho de imagem.

          Recusar toda query era seguro e caro demais: muito CDN de capa serve
          `?w=300`, `?v=2` ou `?format=webp`, que nao carregam credencial
          nenhuma, e essas capas ficavam sem ser gravadas — apareciam enquanto a
          memoria as tinha e sumiam depois, que era o "algumas capas nao
          carregam".

          A lista abaixo e de nomes permitidos, nao de nomes proibidos: um
          parametro que nao esteja nela recusa a URL inteira. Assim um nome novo
          de token nunca passa por esquecimento — o custo de errar para o lado
          seguro e uma capa a menos, e o de errar para o outro e uma credencial
          no disco.
        */
        if (url.indexOf('?') !== -1 && !onlySizingParameters(url)) { return false; }
        /* Os caminhos autenticados do próprio provedor, montados como
           /movie/<usuario>/<senha>/<id> — a forma que originou a preocupação. */
        if (/\/(live|movie|series)\//i.test(url)) { return false; }
        return true;
    }

    /* Um lembrete guardável: só o que a tela precisa, e nada da fonte. */
    function createReminder(input) {
        var item = input && input.item ? input.item : null;
        var identity = input && input.identity ? input.identity : (item ? contentIdentity(item) : '');
        var name = trim(input && input.title) || trim(item && item.name);
        if (!identity || !name) { throw new Error('REMINDER_IDENTITY_REQUIRED'); }
        return {
            id: (input && input.profileId ? input.profileId : '') + ':' + identity,
            profileId: input && input.profileId ? input.profileId : '',
            identity: identity,
            title: name.substring(0, 200),
            contentType: (item && item.contentType) || (input && input.contentType) || CONTENT.UNKNOWN,
            artworkUrl: isStorableReminderArtwork(input && input.artworkUrl) ?
                trim(input.artworkUrl) : null,
            releaseDate: parseReleaseDate(input && input.releaseDate) ?
                trim(input.releaseDate).substring(0, 10) : null,
            createdAt: (input && input.createdAt) || Date.now()
        };
    }

    /*
      O mesmo baralho finito de DiscoveryDeck.kt.

      O domínio decide quais títulos entram e em que ordem; a tela decide como
      desenhá-los. Manter esta parte pura evita que IndexedDB, rede ou foco do
      controle remoto alterem uma recomendação no meio da rodada.
    */
    var DISCOVERY_DECK_SIZE = 15;
    var DISCOVERY_SURPRISE_SLOTS = 2;
    var DISCOVERY_RATING_WEIGHT = 0.35;
    var DISCOVERY_SESSION_WEIGHT = 1.2;

    function discoveryGenre(value) {
        var raw = trim(value).toLowerCase();
        if (!raw || raw.length > 60) { return null; }
        raw = foldAccents(raw).replace(/\s+/g, '-');
        return raw || null;
    }

    function discoveryTasteWeights(taste) {
        var counted = {};
        function add(values, amount) {
            (values || []).forEach(function (value) {
                var key = discoveryGenre(value);
                if (key) { counted[key] = (counted[key] || 0) + amount; }
            });
        }
        add(taste && taste.favouriteGenres, 2);
        add(taste && taste.watchedGenres, 1);
        Object.keys(counted).forEach(function (key) {
            counted[key] = Math.min(1, counted[key] / 10);
            if (counted[key] <= 0) { delete counted[key]; }
        });
        return counted;
    }

    function discoverySessionLeaningFor(session, genres) {
        var leaning = session && session.leaningByGenre ? session.leaningByGenre : {};
        var keys = Object.keys(leaning);
        var strongest = 0;
        var best = null;
        keys.forEach(function (key) { strongest = Math.max(strongest, Math.abs(Number(leaning[key]) || 0)); });
        if (!strongest) { return 0; }
        (genres || []).forEach(function (value) {
            var key = discoveryGenre(value);
            var scoreValue;
            if (!key || !Object.prototype.hasOwnProperty.call(leaning, key)) { return; }
            scoreValue = Number(leaning[key]) || 0;
            if (best === null || scoreValue > best) { best = scoreValue; }
        });
        return best === null ? 0 : best / strongest;
    }

    function discoverySessionAfter(session, genres, verdict) {
        var current = session && session.leaningByGenre ? session.leaningByGenre : {};
        var next = {};
        var delta = verdict === 'KEPT' ? 2 : -1;
        Object.keys(current).forEach(function (key) { next[key] = Number(current[key]) || 0; });
        (genres || []).forEach(function (value) {
            var key = discoveryGenre(value);
            if (key) { next[key] = clamp((next[key] || 0) + delta, -6, 6); }
        });
        return { leaningByGenre: next };
    }

    function discoveryScoreWithWeights(candidate, weights, session) {
        var genreScore = 0;
        var rating = Number(candidate && candidate.rating) || 0;
        (candidate && candidate.genres || []).forEach(function (value) {
            var key = discoveryGenre(value);
            if (key) { genreScore += Number(weights[key]) || 0; }
        });
        return genreScore + clamp(rating / 10, 0, 1) * DISCOVERY_RATING_WEIGHT +
            discoverySessionLeaningFor(session, candidate && candidate.genres || []) * DISCOVERY_SESSION_WEIGHT;
    }

    function discoveryScore(candidate, taste, session) {
        return discoveryScoreWithWeights(candidate, discoveryTasteWeights(taste || {}), session || {});
    }

    function discoverySeenMap(seenIds) {
        var found = {};
        if (Array.isArray(seenIds)) {
            seenIds.forEach(function (idValue) { found[String(idValue)] = true; });
        } else if (seenIds) {
            Object.keys(seenIds).forEach(function (idValue) {
                if (seenIds[idValue]) { found[String(idValue)] = true; }
            });
        }
        return found;
    }

    function discoveryDeck(candidates, taste, session, shuffleSeed) {
        var seen = discoverySeenMap(taste && taste.seenIds);
        var unique = {};
        var eligible = [];
        var weights = discoveryTasteWeights(taste || {});
        var hasSession = Boolean(session && session.leaningByGenre && Object.keys(session.leaningByGenre).length);
        var ranked;
        var matched;
        var strangers;
        var surprises;
        var offset;
        (candidates || []).forEach(function (candidate) {
            var candidateId = trim(candidate && candidate.id);
            if (!candidateId || seen[candidateId] || unique[candidateId]) { return; }
            unique[candidateId] = true;
            eligible.push(candidate);
        });
        if (!eligible.length) { return []; }

        if (!Object.keys(weights).length && !hasSession) {
            return eligible.slice().sort(function (left, right) {
                var ratingDifference = (Number(right.rating) || 0) - (Number(left.rating) || 0);
                if (ratingDifference) { return ratingDifference; }
                return String(left.title || '') < String(right.title || '') ? -1 :
                    (String(left.title || '') > String(right.title || '') ? 1 : 0);
            }).slice(0, DISCOVERY_DECK_SIZE);
        }

        ranked = eligible.map(function (candidate) {
            return { candidate: candidate, score: discoveryScoreWithWeights(candidate, weights, session || {}) };
        }).sort(function (left, right) {
            if (right.score !== left.score) { return right.score - left.score; }
            return String(left.candidate.title || '') < String(right.candidate.title || '') ? -1 :
                (String(left.candidate.title || '') > String(right.candidate.title || '') ? 1 : 0);
        }).map(function (entry) { return entry.candidate; });

        matched = ranked.slice(0, DISCOVERY_DECK_SIZE - DISCOVERY_SURPRISE_SLOTS);
        strangers = ranked.filter(function (candidate) {
            if (matched.indexOf(candidate) >= 0) { return false; }
            return !(candidate.genres || []).some(function (value) {
                var key = discoveryGenre(value);
                return key && Object.prototype.hasOwnProperty.call(weights, key);
            });
        });
        if (strangers.length) {
            offset = ((Number(shuffleSeed) || 0) % strangers.length + strangers.length) % strangers.length;
            surprises = strangers.slice(offset).concat(strangers.slice(0, offset)).slice(0, DISCOVERY_SURPRISE_SLOTS);
        } else {
            surprises = ranked.slice(DISCOVERY_DECK_SIZE - DISCOVERY_SURPRISE_SLOTS, DISCOVERY_DECK_SIZE);
        }
        unique = {};
        return matched.concat(surprises).filter(function (candidate) {
            if (unique[candidate.id]) { return false; }
            unique[candidate.id] = true;
            return true;
        }).slice(0, DISCOVERY_DECK_SIZE);
    }

    function redactMessage(error) {
        var message = error && error.message ? error.message : String(error || 'UNKNOWN');
        return message
            .replace(/https?:\/\/[^\s)]+/gi, '<url>')
            .replace(/([?&](username|password|token|auth|key)=)[^&\s]+/gi, '$1<redacted>')
            .replace(/(authorization|cookie)\s*[:=]\s*[^,\s]+/gi, '$1=<redacted>')
            .substring(0, 240);
    }

    /*
      A mistura do banner, igual a do Windows.

      Ordenado so por pontuacao, o banner enche-se do que o catalogo tem mais, e
      quem passa por vinte titulos do mesmo ano e da mesma prateleira nao fica a
      saber o que mais ha la dentro. Isto mantem a ordem de qualidade dentro de
      cada especie e so decide quantos de cada aparecem: os lancamentos a
      frente, sempre com um filme e uma serie quando existem os dois; dois
      antigos; dois de meio-termo; e um anime.

      As vagas sao um objetivo, nao uma exigencia: o que nenhuma reclama fica
      atras pela mesma ordem, por isso um catalogo sem titulos antigos leva mais
      lancamentos em vez de mostrar menos. Espelha o HeroSelection.mixed do
      modelo partilhado, para os tres apps nao divergirem.
    */
    var HERO_NEW_RELEASE_YEARS = 2;
    var HERO_OLD_RELEASE_YEARS = 15;
    var HERO_OLD_SLOTS = 2;
    var HERO_MIDDLE_SLOTS = 2;
    var HERO_ANIME_SLOTS = 1;
    var HERO_ANIME_WORDS = ['anime', 'animacao japonesa', 'animação japonesa'];

    function heroIsAnime(item) {
        var categories = (item && item.categoryIds) || [];
        var name = String((item && item.categoryName) || '');
        var haystack = categories.concat([name]).join(' ').toLowerCase();
        return HERO_ANIME_WORDS.some(function (word) { return haystack.indexOf(word) >= 0; });
    }

    /* Um titulo sem ano conta como meio-termo, e nao como antigo: os
       fornecedores deixam o campo vazio a toda a hora. */
    function heroAgeBand(item, thisYear) {
        var year = Number(item && item.year);
        if (!year) { return 'middle'; }
        if (year >= thisYear - HERO_NEW_RELEASE_YEARS) { return 'new'; }
        if (year < thisYear - HERO_OLD_RELEASE_YEARS) { return 'old'; }
        return 'middle';
    }

    function heroIsSeries(item) {
        return Boolean(item) && item.contentType === CONTENT.SERIES;
    }

    function mixHeroRotation(rotation, thisYear) {
        var rows = rotation || [];
        if (rows.length <= HERO_OLD_SLOTS + HERO_MIDDLE_SLOTS + HERO_ANIME_SLOTS) { return rows; }

        var taken = {};
        var picked = [];

        function take(limit, predicate) {
            var count = 0;
            rows.forEach(function (item) {
                if (count >= limit || taken[item.id] || !predicate(item)) { return; }
                picked.push(item);
                taken[item.id] = true;
                count += 1;
            });
        }

        /* Um de cada: um banner so de filmes diz que a app nao tem series. */
        take(1, function (item) {
            return heroAgeBand(item, thisYear) === 'new' && !heroIsSeries(item);
        });
        take(1, function (item) {
            return heroAgeBand(item, thisYear) === 'new' && heroIsSeries(item);
        });
        take(HERO_ANIME_SLOTS, heroIsAnime);
        take(HERO_OLD_SLOTS, function (item) {
            return heroAgeBand(item, thisYear) === 'old';
        });
        take(HERO_MIDDLE_SLOTS, function (item) {
            return heroAgeBand(item, thisYear) === 'middle';
        });

        rows.forEach(function (item) {
            if (!taken[item.id]) { picked.push(item); taken[item.id] = true; }
        });
        return picked;
    }

    return {
        CONTENT: CONTENT,
        mixHeroRotation: mixHeroRotation,
        heroIsAnime: heroIsAnime,
        SOURCE: SOURCE,
        SECTIONS: SECTIONS,
        clamp: clamp,
        trim: trim,
        foldAccents: foldAccents,
        shelfDeduplicationKey: shelfDeduplicationKey,
        shelfItemDeduplicationKey: shelfItemDeduplicationKey,
        collapseShelfDuplicates: collapseShelfDuplicates,
        stableHash: stableHash,
        id: id,
        safeId: safeId,
        sanitizeYouTubeReference: sanitizeYouTubeReference,
        contentIdentity: contentIdentity,
        hasHighRiskVideoTag: hasHighRiskVideoTag,
        compatibilityTitlePrefix: compatibilityTitlePrefix,
        createProfile: createProfile,
        createSourceMetadata: createSourceMetadata,
        createItem: createItem,
        availableGenres: availableGenres,
        availableYears: availableYears,
        applyCatalogueFilter: applyCatalogueFilter,
        playbackPercent: playbackPercent,
        playbackCompleted: playbackCompleted,
        resumeDecision: resumeDecision,
        COUNTDOWN_HORIZON_DAYS: COUNTDOWN_HORIZON_DAYS,
        REMINDER_HORIZON_CHOICES: REMINDER_HORIZON_CHOICES,
        DISCOVERY_DECK_SIZE: DISCOVERY_DECK_SIZE,
        DISCOVERY_SURPRISE_SLOTS: DISCOVERY_SURPRISE_SLOTS,
        discoveryDeck: discoveryDeck,
        discoveryScore: discoveryScore,
        discoverySessionAfter: discoverySessionAfter,
        discoverySessionLeaningFor: discoverySessionLeaningFor,
        createReminder: createReminder,
        reminderDigest: reminderDigest,
        sortReminders: sortReminders,
        isStorableReminderArtwork: isStorableReminderArtwork,
        parseReleaseDate: parseReleaseDate,
        redactMessage: redactMessage
    };
}());
