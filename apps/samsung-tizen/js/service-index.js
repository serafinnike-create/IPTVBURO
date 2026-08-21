/*
  Quais títulos da sua lista cada serviço carrega.

  Porte do `ServiceTitleIndex.kt` do Windows, e existe pelo mesmo motivo: uma
  lista que arquiva por gênero — "Filmes | Ação", "Filmes | Drama" — não nomeia
  serviço nenhum, então o seletor de Serviço ficava desativado justamente na aba
  onde alguém pergunta "o que tem na Netflix". As categorias não sabem
  responder; o TMDb sabe.

  A direção importa: perguntamos a cada serviço o que ele tem e cruzamos com a
  lista do usuário, em vez de perguntar sobre cada título — são poucos serviços
  e dezenas de milhares de títulos, e o outro sentido seria uma requisição por
  filme.
*/
var BuroServiceIndex = (function () {
    'use strict';

    function trim(value) {
        if (typeof BuroDomain !== 'undefined' && BuroDomain.trim) { return BuroDomain.trim(value); }
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /*
      O nome reduzido ao que dá para comparar.

      O provedor escreve "72 Horas em Miami 4K [DV][HDR]" e o TMDb escreve
      "72 Horas em Miami": sem tirar acento, pontuação e os marcadores de
      qualidade, quase nada casaria.
    */
    function normalisedForMatching(value) {
        var text = trim(value).toLowerCase();
        /*
          Colchetes e o ano entre parênteses saem antes de dobrar os acentos.

          `BuroDomain.foldAccents` já tira a pontuação, e depois dele não há mais
          parênteses para casar: o ano ficava solto no nome. O TMDb manda
          "Duna (2021)" e a lista manda "Duna 4K", que viravam "duna 2021" e
          "duna" e nunca casavam. O ano continua sendo exigido — só que pelo
          campo próprio, que é onde ele significa alguma coisa.
        */
        text = text
            .replace(/\[[^\]]*\]/g, ' ')
            .replace(/\((?:19|20)\d{2}\)/g, ' ');
        if (typeof BuroDomain !== 'undefined' && BuroDomain.foldAccents) {
            text = BuroDomain.foldAccents(text).toLowerCase();
        }
        return text
            .replace(/\b(4k|hdr|dv|fhd|hd|sd|dublado|legendado|l|multi)\b/g, ' ')
            .replace(/[^0-9a-z]+/g, ' ')
            .replace(/^\s+|\s+$/g, '');
    }

    /*
      Monta o índice a partir do que cada serviço lista e do que a lista tem.

      `serviceTitles` é a resposta do TMDb: rótulo do serviço para os títulos que
      ele carrega. `library` são os itens do usuário.

      O ano é exigido dos dois lados, e não tratado como opcional: sem ele "Duna"
      casa igualmente com o filme de 1984 e com o de 2021, e um filtro que
      confunde dois filmes em silêncio é pior do que um que perde um deles.
    */
    function build(serviceTitles, library) {
        var byName = {};
        var result = {};
        if (!serviceTitles || !library || !library.length) { return create({}); }
        /* Indexado uma vez por nome: a lista tem dezenas de milhares de linhas e
           há vários serviços, então o laço aninhado seriam milhões de
           comparações. */
        library.forEach(function (item) {
            var key = normalisedForMatching(item && item.name);
            if (!key) { return; }
            if (!byName[key]) { byName[key] = []; }
            byName[key].push({ year: Number(item && item.year) || null, id: item && item.id });
        });
        Object.keys(serviceTitles).forEach(function (service) {
            (serviceTitles[service] || []).forEach(function (title) {
                var key = normalisedForMatching(title && title.title);
                var year = Number(title && title.year) || null;
                var candidates = key ? byName[key] : null;
                if (!candidates || !year) { return; }
                candidates.forEach(function (candidate) {
                    if (candidate.year !== year || !candidate.id) { return; }
                    if (!result[service]) { result[service] = {}; }
                    result[service][candidate.id] = true;
                });
            });
        });
        return create(result);
    }

    function create(map) {
        return {
            /* Os serviços que este índice conhece, do que carrega mais para o que
               carrega menos: um serviço com trinta títulos é mais útil no topo do
               seletor do que um com dois. */
            services: function () {
                return Object.keys(map).sort(function (left, right) {
                    return Object.keys(map[right]).length - Object.keys(map[left]).length ||
                        left.localeCompare(right);
                });
            },
            /* Vazio significa que o filtro não deve ser oferecido. */
            isEmpty: function () { return Object.keys(map).length === 0; },
            /* Quantos títulos da lista casaram, para o usuário julgar a cobertura. */
            countFor: function (service) {
                return map[service] ? Object.keys(map[service]).length : 0;
            },
            has: function (service, itemId) {
                return Boolean(map[service] && map[service][itemId]);
            },
            idsFor: function (service) {
                return map[service] ? Object.keys(map[service]) : [];
            }
        };
    }

    return {
        build: build,
        empty: function () { return create({}); },
        normalisedForMatching: normalisedForMatching
    };
}());
