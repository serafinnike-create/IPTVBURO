/*
  app.js - foco por D-pad e ligacao com o player.

  Regra de TV: o app controla o foco manualmente. O :focus do navegador
  nao e confiavel aqui, entao mantemos um indice e aplicamos a classe
  .focused no cartao ativo.

  NENHUMA lista privada, credencial ou URL assinada entra neste repositorio.
  As fontes abaixo sao streams de teste publicos e estaveis, usados apenas
  para validar que a AVPlay inicia numa TV real.
*/
(function () {
    'use strict';

    var SOURCES = [
        {
            name: 'Big Buck Bunny',
            kind: 'HLS · teste público',
            url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
        },
        {
            name: 'Tears of Steel',
            kind: 'HLS · teste público',
            url: 'https://test-streams.mux.dev/test_001/stream.m3u8'
        },
        {
            name: 'Fonte inválida',
            kind: 'Teste de erro',
            url: 'https://exemplo.invalido/stream.m3u8'
        }
    ];

    var focusIndex = 0;
    var cards = [];
    var overlay = document.getElementById('player-overlay');
    var statusLabel = document.getElementById('player-status');

    function renderCards() {
        var list = document.getElementById('channel-list');

        SOURCES.forEach(function (source, index) {
            var item = document.createElement('li');
            item.className = 'card';
            item.setAttribute('data-index', String(index));

            var name = document.createElement('span');
            name.className = 'card-name';
            name.textContent = source.name;

            var kind = document.createElement('span');
            kind.className = 'card-kind';
            kind.textContent = source.kind;

            item.appendChild(name);
            item.appendChild(kind);
            list.appendChild(item);
            cards.push(item);
        });

        applyFocus();
    }

    function applyFocus() {
        cards.forEach(function (card, index) {
            if (index === focusIndex) {
                card.classList.add('focused');
            } else {
                card.classList.remove('focused');
            }
        });
    }

    function moveFocus(delta) {
        var next = focusIndex + delta;
        /*
          Sem wrap-around: numa TV o foco deve PARAR na borda.
          Se ele desse a volta, o usuario perderia a nocao de onde esta.
        */
        if (next < 0 || next >= cards.length) {
            return;
        }
        focusIndex = next;
        applyFocus();
    }

    function showStatus(message, isError) {
        overlay.hidden = false;
        statusLabel.textContent = message;
        if (isError) {
            statusLabel.classList.add('error');
        } else {
            statusLabel.classList.remove('error');
        }
    }

    function enterPlayback() {
        var source = SOURCES[focusIndex];
        document.body.classList.add('playing');
        showStatus('Abrindo ' + source.name + '…', false);
        BuroPlayer.play(source.url);
    }

    function exitPlayback() {
        BuroPlayer.stop();
        document.body.classList.remove('playing');
        overlay.hidden = true;
    }

    function onKeyDown(event) {
        var K = BuroKeys.CODES;

        // Durante a reproducao o RETURN volta para a lista.
        if (document.body.classList.contains('playing')) {
            if (event.keyCode === K.RETURN) {
                exitPlayback();
            }
            return;
        }

        switch (event.keyCode) {
            case K.LEFT:
                moveFocus(-1);
                break;
            case K.RIGHT:
                moveFocus(1);
                break;
            case K.ENTER:
                enterPlayback();
                break;
            case K.RETURN:
                /*
                  Na tela inicial, RETURN fecha o app.
                  A Samsung exige esse comportamento na certificacao.
                */
                if (window.tizen && tizen.application) {
                    tizen.application.getCurrentApplication().exit();
                }
                break;
            default:
                break;
        }
    }

    function init() {
        BuroKeys.registerMediaKeys();
        BuroPlayer.setListeners({
            onStatus: function (message) { showStatus(message, false); },
            onError: function (message) {
                showStatus(message, true);
                // O erro fica visivel, mas devolvemos a navegacao ao usuario.
                document.body.classList.remove('playing');
            }
        });

        renderCards();
        document.addEventListener('keydown', onKeyDown);
    }

    window.addEventListener('load', init);
}());
