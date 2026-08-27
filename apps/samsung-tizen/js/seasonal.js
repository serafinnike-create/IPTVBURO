/*
  A prateleira que o calendário traz, e leva embora.

  Porte de `SeasonalCollections.kt`, que vive em `packages/domain-model` e já é
  usado no Windows e no Android — a TV era a única sem. As janelas, os termos e a
  decisão de mostrar uma coleção por vez são de lá; só a linguagem mudou.

  Por que os termos misturam português e inglês: uma lista de provedor costuma
  trazer os dois lado a lado — "O Grinch" ao lado de "Christmas Vacation" — e uma
  lista de termos num idioma só encontra metade do que existe.

  Por que os termos são longos: o casamento é um `indexOf` sem acento, então
  "natal" sozinho arrastaria "Natalie" e "fatal". Onde uma palavra curta é
  inevitável, ela vem com o espaço de que precisa.
*/
var BuroSeasonal = (function () {
    'use strict';

    var FALLBACK_LANGUAGE = 'en';

    var CHRISTMAS = {
        id: 'christmas',
        titles: {
            'pt-BR': 'Especial de Natal', en: 'Christmas Special',
            de: 'Weihnachtsspecial', it: 'Speciale Natale', es: 'Especial de Navidad'
        },
        /*
          `natalin` cobre o portugues, e `natal` sozinho saiu.

          O dominio compartilhado avisa que "natal sozinho arrastaria Natalie e
          fatal" e mesmo assim mantem o termo, entao Windows e Android poem
          "Natalie" numa prateleira de Natal. Aqui ele foi removido: `natalin`
          pega "natalino", "natalina", "natalinas" — que e o que a palavra existe
          para pegar — e "Feliz Natal" continua entrando por `natalin` nao, mas
          um filme chamado so "Natal" e raro, e um "Natalie" numa fileira de
          Natal e visivel toda vez.

          Sem terminacao, como o dominio ja faz em `namorad` e `casament`.
        */
        terms: ['natalin', 'christmas', 'xmas', 'papai noel', 'santa claus',
            'weihnacht', 'noel', 'renas', 'reindeer', 'grinch', 'presepe', 'navidad']
    };

    var HALLOWEEN = {
        id: 'halloween',
        titles: {
            'pt-BR': 'Noites de Halloween', en: 'Halloween Nights',
            de: 'Halloween-Nächte', it: 'Notti di Halloween', es: 'Noches de Halloween'
        },
        terms: ['halloween', 'terror', 'horror', 'assombrada', 'assombrado', 'haunted',
            'zumbi', 'zombie', 'vampiro', 'vampire', 'bruxa', 'witch']
    };

    var VALENTINES = {
        id: 'valentines',
        titles: {
            'pt-BR': 'Para assistir a dois', en: 'Made for Two',
            de: 'Zu zweit sehen', it: 'Da guardare in due', es: 'Para ver en pareja'
        },
        terms: ['romance', 'romantic', 'romantico', 'amor', 'love', 'paixao', 'paixão',
            'namorad', 'valentine', 'casament', 'wedding', 'coracao', 'coração']
    };

    var NEW_YEAR = {
        id: 'new-year',
        titles: {
            'pt-BR': 'Virada de ano', en: 'Ring in the Year',
            de: 'Jahreswechsel', it: 'Capodanno', es: 'Fin de año'
        },
        terms: ['ano novo', 'new year', 'reveillon', 'réveillon', 'silvester', 'capodanno',
            'contagem regressiva', 'countdown', 'meia noite', 'midnight']
    };

    var SCHOOL_HOLIDAYS = {
        id: 'school-holidays',
        titles: {
            'pt-BR': 'Férias em família', en: 'Family Holidays',
            de: 'Ferien mit der Familie', it: 'Vacanze in famiglia', es: 'Vacaciones en familia'
        },
        terms: ['familia', 'família', 'family', 'infantil', 'kids', 'animacao', 'animação',
            'animation', 'desenho', 'cartoon', 'aventura', 'adventure']
    };

    /*
      As janelas, na ordem em que o domínio as declara.

      Dezembro inteiro para o Natal: os provedores publicam as fileiras cedo, e a
      véspera é quando as pessoas de facto procuram. O Ano Novo atravessa a
      virada, que é a razão de `contains` não poder ser uma comparação simples.
      Halloween é uma noite só, mas a prateleira se justifica na quinzena que
      leva até ela. E o Dia dos Namorados tem duas janelas porque o Brasil guarda
      12 de junho além de 14 de fevereiro, e o aplicativo roda nos dois mercados.
    */
    var WINDOWS = [
        { collection: CHRISTMAS, from: [12, 1], to: [12, 26] },
        { collection: NEW_YEAR, from: [12, 27], to: [1, 6] },
        { collection: HALLOWEEN, from: [10, 18], to: [11, 1] },
        { collection: VALENTINES, from: [2, 7], to: [2, 15] },
        { collection: VALENTINES, from: [6, 5], to: [6, 13] },
        { collection: SCHOOL_HOLIDAYS, from: [7, 1], to: [7, 31] }
    ];

    /* Mês e dia comparados como um número só: 12 de janeiro vira 112, e a ordem
       de calendário sai da comparação numérica direta. */
    function ordinal(month, day) { return month * 100 + day; }

    /*
      Inclusiva nas duas pontas.

      O ramo que dá a volta existe para o Ano Novo, cuja janela termina em 6 de
      janeiro: comparar mês e dia diretamente excluiria toda data depois da
      virada. O mesmo vale para o Halloween, que termina em 1º de novembro.
    */
    function windowContains(range, month, day) {
        var from = ordinal(range.from[0], range.from[1]);
        var to = ordinal(range.to[0], range.to[1]);
        var value = ordinal(month, day);
        return from <= to ? (value >= from && value <= to) : (value >= from || value <= to);
    }

    /*
      As coleções cuja janela contém esta data, sem repetir.

      Devolve vazio na maior parte do ano de propósito: uma noite comum de março
      deve mostrar a Home comum, e não uma prateleira procurando um motivo para
      existir.
    */
    function collectionsFor(date) {
        /*
          `instanceof Date` nao serve aqui: uma data criada noutro contexto — o
          runtime da TV tem mais de um, e o teste roda em jsdom — nao e instancia
          do `Date` deste. A verificacao falhava em silencio e o codigo caia para
          `new Date()`, ou seja, hoje: a data pedida era descartada e a prateleira
          respondia sempre pelo dia corrente.

          Perguntar se sabe dizer o mes e o que importa, e vale em qualquer
          contexto.
        */
        var when = date && typeof date.getMonth === 'function' ? date : new Date();
        var month = when.getMonth() + 1;
        var day = when.getDate();
        var seen = {};
        var found = [];
        /* `range`, e nao `window`: o parametro sombreava o objeto global do
           navegador, e dentro do modulo `window.collection` resolvia para o
           global em vez do parametro — nenhuma janela casava nada. */
        WINDOWS.forEach(function (range) {
            if (!windowContains(range, month, day)) { return; }
            if (seen[range.collection.id]) { return; }
            seen[range.collection.id] = true;
            found.push(range.collection);
        });
        return found;
    }

    /* A prateleira a mostrar, ou nula. A Home tem espaço para uma só. */
    function primaryCollectionFor(date) {
        return collectionsFor(date)[0] || null;
    }

    /*
      O nome da prateleira no idioma pedido, com o inglês como reserva.

      Uma tradução ausente ainda precisa dar uma prateleira usável: uma fileira
      sem tradução é uma falha menor do que uma fileira encabeçada por um
      identificador.
    */
    function titleFor(collection, language) {
        if (!collection) { return ''; }
        return collection.titles[language] || collection.titles[FALLBACK_LANGUAGE] || collection.id;
    }

    /*
      Os títulos da lista que pertencem a esta coleção.

      O casamento é por nome, sem acento e sem caixa, porque é o que existe: o
      provedor não marca um filme como natalino. `foldAccents` do domínio faz a
      dobra, a mesma que a busca usa.
    */
    /*
      O termo aparece no nome como palavra, e nao como pedaco de outra.

      O dominio compartilhado avisa que os termos precisam ser longos o bastante
      para um `contains` puro — "natal sozinho arrastaria Natalie e fatal" — mas
      a lista dele traz `natal` mesmo assim, e Windows e Android fazem `contains`
      direto. O resultado e "Natalie" numa prateleira de Natal nas duas.

      Copiar isso seria copiar um defeito. Exigir limite de palavra corrige sem
      divergir da lista: os termos continuam sendo os do dominio, e os longos
      — "papai noel", "christmas" — casam igual.

      Termos com espaco ficam no `indexOf`: eles ja sao especificos o bastante, e
      o limite de palavra atrapalharia um nome que os traga colados a pontuacao.
    */
    function nameCarries(name, term) {
        var at;
        var before;
        var after;
        if (term.indexOf(' ') >= 0) { return name.indexOf(term) >= 0; }
        at = name.indexOf(term);
        while (at >= 0) {
            before = at === 0 ? '' : name.charAt(at - 1);
            /*
              So o inicio do termo precisa cair num limite. O fim nao: as linguas
              deste catalogo flexionam a terminacao — "natalinas", "romantico",
              "namorados" — e exigir limite ali faria o termo perder justamente
              as formas que ele existe para pegar.

              O limite no comeco e o que impede "Fatal" de entrar por "natal", e
              e por isso que ele fica.
            */
            after = '';
            /* `foldAccents` ja reduziu o nome a letras, digitos e espaco, entao
               "nao e letra nem digito" basta como limite. */
            if (!/[0-9a-z]/.test(before) && !/[0-9a-z]/.test(after)) { return true; }
            at = name.indexOf(term, at + 1);
        }
        return false;
    }

    function matches(collection, items, limit) {
        var terms;
        var found = [];
        var ceiling = Number(limit) > 0 ? Number(limit) : 20;
        if (!collection || !items || !items.length) { return found; }
        terms = collection.terms.map(function (term) {
            return typeof BuroDomain !== 'undefined' && BuroDomain.foldAccents ?
                BuroDomain.foldAccents(term) : term.toLowerCase();
        });
        items.some(function (item) {
            var name = typeof BuroDomain !== 'undefined' && BuroDomain.foldAccents ?
                BuroDomain.foldAccents(item && item.name) : String((item && item.name) || '').toLowerCase();
            if (!name) { return false; }
            if (terms.some(function (term) { return nameCarries(name, term); })) { found.push(item); }
            return found.length >= ceiling;
        });
        return found;
    }

    return {
        collectionsFor: collectionsFor,
        primaryCollectionFor: primaryCollectionFor,
        titleFor: titleFor,
        matches: matches
    };
}());
