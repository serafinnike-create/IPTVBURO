/*
  player.js - wrapper sobre a AVPlay (engine de video da Samsung).

  A AVPlay NAO e o <video> do HTML. Ela e uma maquina de estados nativa,
  e chamar os metodos fora de ordem gera erro. A ordem valida e:

      open(url) -> setDisplayRect() -> prepare() -> play()
      stop() -> close()

  O video e desenhado numa camada de HARDWARE atras da pagina web,
  por isso o CSS do body precisa ser transparente para ele aparecer.
*/
var BuroPlayer = (function () {
    'use strict';

    var listeners = { onStatus: function () {}, onError: function () {} };
    var isOpen = false;

    function available() {
        return typeof webapis !== 'undefined' && webapis.avplay;
    }

    function status(message) {
        listeners.onStatus(message);
    }

    function fail(message) {
        isOpen = false;
        listeners.onError(message);
    }

    /*
      A AVPlay reporta buffering e erros de rede aqui.
      Sem estes callbacks o app trava em silencio quando o stream cai.
    */
    function buildCallbacks() {
        return {
            onbufferingstart: function () { status('Carregando…'); },
            onbufferingcomplete: function () { status('Reproduzindo'); },
            onstreamcompleted: function () {
                status('Transmissao encerrada');
                stop();
            },
            onerror: function (error) {
                // Nunca logamos a URL: ela pode conter credenciais do usuario.
                fail('Falha na reproducao (' + (error && error.name ? error.name : 'desconhecida') + ')');
            }
        };
    }

    function play(url) {
        if (!available()) {
            fail('AVPlay indisponivel: rode este app numa TV ou no emulador Tizen.');
            return;
        }

        try {
            // Uma sessao anterior precisa ser encerrada antes de abrir outra.
            stop();

            webapis.avplay.open(url);
            webapis.avplay.setListener(buildCallbacks());
            // A area do video e definida em coordenadas logicas 1920x1080.
            webapis.avplay.setDisplayRect(0, 0, 1920, 1080);

            isOpen = true;
            status('Preparando…');

            // prepareAsync nao bloqueia a UI; prepare() sincrono congelaria a tela.
            webapis.avplay.prepareAsync(
                function () {
                    webapis.avplay.play();
                    status('Reproduzindo');
                },
                function (error) {
                    fail('Nao foi possivel preparar o stream (' +
                        (error && error.name ? error.name : 'erro') + ')');
                }
            );
        } catch (e) {
            fail('Erro ao iniciar: ' + (e && e.name ? e.name : 'desconhecido'));
        }
    }

    function stop() {
        if (!available() || !isOpen) {
            return;
        }
        try {
            webapis.avplay.stop();
            webapis.avplay.close();
        } catch (e) {
            console.warn('Falha ao encerrar a sessao de video', e);
        }
        isOpen = false;
    }

    function isPlaying() {
        return isOpen;
    }

    function setListeners(next) {
        listeners.onStatus = next.onStatus || listeners.onStatus;
        listeners.onError = next.onError || listeners.onError;
    }

    return {
        play: play,
        stop: stop,
        isPlaying: isPlaying,
        setListeners: setListeners,
        available: available
    };
}());
