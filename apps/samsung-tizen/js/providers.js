/*
  Serviços de streaming reconhecidos pelo nome da categoria.

  Porte do `ProviderIdentity.kt` e do `CategoryLabel.kt` do Windows. A mesma
  lista dos dois lados de propósito: se a TV reconhecesse um conjunto diferente
  de serviços, "só Netflix" mostraria coisas diferentes em cada aparelho e
  nenhum dos dois estaria obviamente errado.

  O que isto resolve na TV: a aba Filmes mostrava só a fileira de categorias que
  o provedor mandou — "Filmes | Lançamentos", "Filmes | 4K", "Filmes | Netflix"
  — sem separar o que é gênero do que é serviço. Quem quer saber o que tem na
  Netflix tinha que procurar a categoria certa no meio das outras.
*/
var BuroProviders = (function () {
    'use strict';

    function trim(value) {
        if (typeof BuroDomain !== 'undefined' && BuroDomain.trim) { return BuroDomain.trim(value); }
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /*
      "Max" como palavra inteira.

      Delimitado em vez de um `indexOf` simples, para que uma categoria chamada
      "Cinemax" ou "Max Series" não seja marcada como o serviço de streaming.
    */
    var MAX_PROVIDER = /(^|[ |\-])max([ |\-]|$)/;

    /* Marca, nome e cor de cada serviço. A cor é a oficial da marca, a mesma do
       app do Windows, para que o mesmo serviço tenha a mesma cor nos dois. */
    var SERVICES = [
        { test: function (v) { return v.indexOf('netflix') >= 0; }, mark: 'N', label: 'Netflix', colour: '#E50914' },
        { test: function (v) { return v.indexOf('amazon') >= 0 || v.indexOf('prime video') >= 0; },
            mark: 'AP', label: 'Prime Video', colour: '#00A8E1' },
        { test: function (v) { return v.indexOf('disney') >= 0; }, mark: 'D+', label: 'Disney+', colour: '#113CCF' },
        { test: function (v) { return v.indexOf('globoplay') >= 0; }, mark: 'G', label: 'Globoplay', colour: '#E30613' },
        { test: function (v) { return v.indexOf('discovery') >= 0; }, mark: 'D', label: 'Discovery+', colour: '#0077C8' },
        { test: function (v) { return v.indexOf('apple tv') >= 0 || v.indexOf('apple+') >= 0; },
            mark: '', label: 'Apple TV+', colour: '#E8E8ED' },
        { test: function (v) { return v.indexOf('hbo') >= 0; }, mark: 'HBO', label: 'HBO', colour: '#991EEB' },
        { test: function (v) { return v.indexOf('paramount') >= 0; }, mark: 'P+', label: 'Paramount+', colour: '#0064FF' },
        { test: function (v) { return v.indexOf('star+') >= 0 || v.indexOf('star plus') >= 0; },
            mark: 'S+', label: 'Star+', colour: '#1D1D6E' },
        { test: function (v) { return v.indexOf('crunchyroll') >= 0; }, mark: 'CR', label: 'Crunchyroll', colour: '#F47521' },
        { test: function (v) { return MAX_PROVIDER.test(v); }, mark: 'M', label: 'Max', colour: '#0046FF' }
    ];

    /* Separadores que os provedores usam entre a seção e o nome da categoria. */
    var SEPARATORS = ['|', ':', '»', '›', '/'];

    /* Palavras que nomeiam a seção, não a categoria: "Filmes | Ação" vira "Ação". */
    var SECTION_WORDS = {
        filme: true, filmes: true, movie: true, movies: true, vod: true,
        serie: true, series: true, 'tv shows': true, novelas: true,
        canal: true, canais: true, channel: true, channels: true,
        live: true, 'ao vivo': true, aovivo: true
    };

    /* Minúsculas, sem acento e sem pontuação nas pontas — os provedores são
       inconsistentes nos três. */
    function normalisedForSection(value) {
        var text = String(value == null ? '' : value).toLowerCase();
        if (typeof BuroDomain !== 'undefined' && BuroDomain.foldAccents) {
            text = BuroDomain.foldAccents(text).toLowerCase();
        }
        return text.replace(/^[^0-9a-z]+/, '').replace(/[^0-9a-z]+$/, '');
    }

    function hasLetterOrDigit(value) {
        return /[0-9A-Za-zÀ-ÿ]/.test(String(value == null ? '' : value));
    }

    /*
      O nome da categoria sem o prefixo da seção.

      "Filmes | Ação" vira "Ação"; "FILMES LANÇAMENTOS" vira "LANÇAMENTOS".
      A cauda precisa ter alguma letra ou número: "Filmes |" é um nome mal
      formado do provedor, não um prefixo mais categoria, e tirar o prefixo
      deixaria um chip chamado "|".
    */
    function categoryLabel(name) {
        var label = trim(name);
        var separatorIndex = -1;
        var strippedBySeparator = false;
        var firstSpace;
        var head;
        var tail;
        SEPARATORS.forEach(function (character) {
            var position = label.indexOf(character);
            if (position > 0 && (separatorIndex < 0 || position < separatorIndex)) { separatorIndex = position; }
        });
        if (separatorIndex > 0) {
            head = trim(label.substring(0, separatorIndex));
            tail = trim(label.substring(separatorIndex + 1));
            if (hasLetterOrDigit(tail) && SECTION_WORDS[normalisedForSection(head)]) {
                label = tail;
                strippedBySeparator = true;
            }
        }
        /* Só quando o separador não achou nada para tirar. "Canais | Filmes e
           Séries" nomeia uma categoria cujo próprio nome começa com palavra de
           seção, e rodar os dois passos deixava "e Séries" — um chip que se lê
           como fragmento porque é um. */
        firstSpace = strippedBySeparator ? -1 : label.indexOf(' ');
        if (firstSpace > 0) {
            head = normalisedForSection(label.substring(0, firstSpace));
            tail = trim(label.substring(firstSpace + 1));
            if (hasLetterOrDigit(tail) && SECTION_WORDS[head]) { label = tail; }
        }
        return label || trim(name);
    }

    /* O serviço que este nome de categoria nomeia, ou null se for um gênero. */
    function identityFor(categoryName) {
        var normalized = String(categoryName == null ? '' : categoryName).toLowerCase();
        var found = null;
        if (!trim(normalized)) { return null; }
        SERVICES.some(function (service) {
            if (service.test(normalized)) {
                found = { mark: service.mark, label: service.label, colour: service.colour };
                return true;
            }
            return false;
        });
        return found;
    }

    /* O caminho inverso: de um rótulo já resolvido ("Netflix") de volta à marca.
       Passa pelo mesmo `identityFor` para que exista uma lista só. */
    function identityForLabel(label) { return identityFor(label); }

    /*
      Separa as categorias em gêneros e serviços.

      Um serviço aparece uma vez só, mesmo que a lista traga "Netflix",
      "Netflix 4K" e "Netflix Legendado": o seletor pergunta "qual serviço", e o
      qualificador pertence à arrumação do provedor, não à pergunta.
    */
    function split(categories) {
        var genres = [];
        var providers = [];
        var seen = {};
        (categories || []).forEach(function (category) {
            var identity = identityFor(category && category.name);
            var choice = {
                id: category && category.id,
                categoryId: category && category.id,
                label: categoryLabel(category && category.name),
                provider: identity
            };
            if (!identity) { genres.push(choice); return; }
            if (seen[identity.label]) { return; }
            seen[identity.label] = true;
            choice.label = identity.label;
            providers.push(choice);
        });
        return { genres: genres, providers: providers, hasProviders: providers.length > 0 };
    }

    /* Todas as categorias que pertencem a um serviço, e não só a primeira: o
       filtro precisa alcançar "Netflix 4K" quando o usuário escolheu "Netflix". */
    function categoryIdsForLabel(categories, label) {
        var wanted = trim(label);
        var ids = [];
        (categories || []).forEach(function (category) {
            var identity = identityFor(category && category.name);
            if (identity && identity.label === wanted) { ids.push(category.id); }
        });
        return ids;
    }

    return {
        identityFor: identityFor,
        identityForLabel: identityForLabel,
        categoryLabel: categoryLabel,
        split: split,
        categoryIdsForLabel: categoryIdsForLabel,
        services: function () { return SERVICES.map(function (s) { return s.label; }); }
    };
}());
