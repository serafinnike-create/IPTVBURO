/*
  Os desenhos da barra de navegação.

  Eram letras — "M" para Filmes, "S" para Séries, "?" para Pesquisa — porque na
  faixa horizontal antiga não havia largura para mais que um caractere, e nem
  isso: a regra `.nav-item .nav-icon { display: none }` os escondia. Com a
  navegação na lateral existe espaço, e o aplicativo do Windows usa pictogramas.

  Traço e não preenchimento, uma cor só herdada do texto ao lado: assim o ícone
  acompanha o estado do item — apagado em repouso, dourado quando selecionado,
  escuro sobre o marfim do foco — sem precisar de uma segunda paleta.

  Desenhados à mão numa grade de 24 e não copiados de nenhum conjunto: são
  formas geométricas simples, do mesmo vocabulário que qualquer biblioteca de
  ícones usaria, e nenhum reproduz marca de terceiro.
*/
var BuroIcons = (function () {
    'use strict';

    /* `stroke-width` um pouco acima do usual porque isto é lido a três metros. */
    var PATHS = {
        /* Casa: telhado e corpo. */
        home: '<path d="M4 11 12 4l8 7"/><path d="M6 10v9h12v-9"/>',
        /* Antena de transmissão sobre um aparelho. */
        live: '<rect x="3" y="9" width="18" height="11" rx="2"/><path d="M8 6 12 2l4 4"/>',
        /* Claquete de cinema. */
        movies: '<rect x="3" y="8" width="18" height="12" rx="2"/><path d="M3 8 6 4h12l-3 4"/><path d="M9 8 12 4"/>',
        /* Pilha de telas, uma atrás da outra. */
        series: '<rect x="6" y="7" width="15" height="12" rx="2"/><path d="M3 9v9a2 2 0 0 0 2 2h11"/>',
        /* Bússola: o círculo e a agulha. */
        discover: '<circle cx="12" cy="12" r="9"/><path d="m15 9-2 5-5 2 2-5z"/>',
        /* Lupa. */
        search: '<circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/>',
        /* Coração — a seção é a dos favoritos. */
        myBuro: '<path d="M12 20s-7-4.4-7-9.2A3.8 3.8 0 0 1 12 8a3.8 3.8 0 0 1 7 2.8C19 15.6 12 20 12 20z"/>',
        /* Botão de tocar dentro de um círculo. */
        continueWatching: '<circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4z"/>',
        /* Relógio com o ponteiro para trás: histórico. */
        history: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
        /* Sino. */
        reminders: '<path d="M18 16H6l1.5-2.5V10a4.5 4.5 0 0 1 9 0v3.5z"/><path d="M10 19a2 2 0 0 0 4 0"/>',
        /* Seta para baixo sobre uma base. */
        downloads: '<path d="M12 4v10"/><path d="m8 11 4 4 4-4"/><path d="M5 19h14"/>',
        /* Silhueta de pessoa. */
        profiles: '<circle cx="12" cy="8" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
        /* Pasta: as fontes de catálogo. */
        sources: '<path d="M3 8a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
        /* Engrenagem simplificada: roda e eixo. */
        settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M6 6l2 2M16 16l2 2M18 6l-2 2M8 16l-2 2"/>',
        /* Cartão com uma marca: as assinaturas. */
        subscriptions: '<rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18"/><path d="M7 15h4"/>',
        /* Nota musical. */
        music: '<path d="M9 18V6l10-2v12"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="16" r="2"/>'
    };

    /*
      O desenho de uma seção, ou uma cadeia vazia.

      Vazia e não um símbolo genérico: um ícone inventado para uma seção nova
      diria menos que nenhum, e o rótulo ao lado já nomeia o destino.
    */
    function svg(name) {
        var path = PATHS[name];
        if (!path) { return ''; }
        return '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" ' +
            'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' +
            path + '</svg>';
    }

    function has(name) { return Object.prototype.hasOwnProperty.call(PATHS, name); }

    function names() { return Object.keys(PATHS); }

    return { svg: svg, has: has, names: names };
}());
