/*
  keys.js - teclas do controle remoto.

  Numa TV nao existe clique: TUDO acontece por keydown.
  O controle Samsung envia keyCodes proprios, diferentes de um teclado de PC.
  As setas e o ENTER seguem o padrao de teclado; as teclas de midia nao.
*/
var BuroKeys = (function () {
    'use strict';

    var CODES = {
        LEFT: 37,
        UP: 38,
        RIGHT: 39,
        DOWN: 40,
        ENTER: 13,
        // RETURN (voltar) e exclusiva da TV; nao existe em teclado de PC.
        RETURN: 10009,
        PLAY_PAUSE: 10252,
        PLAY: 415,
        PAUSE: 19,
        STOP: 413,
        REWIND: 412,
        FAST_FORWARD: 417,
        RED: 403,
        GREEN: 404,
        YELLOW: 405,
        BLUE: 406
    };

    /*
      Por padrao a TV so entrega as setas e o ENTER.
      Teclas de midia (Play, Pause, Stop) precisam ser registradas
      explicitamente, senao o sistema as consome antes do app.
    */
    function registerMediaKeys() {
        if (!window.tizen || !tizen.tvinputdevice) {
            return; // Navegador comum (desenvolvimento no PC).
        }
        ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaRewind', 'MediaFastForward',
            'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue'].forEach(function (key) {
            try {
                tizen.tvinputdevice.registerKey(key);
            } catch (e) { /* A tecla pode não existir neste modelo de TV. */ }
        });
    }

    return {
        CODES: CODES,
        registerMediaKeys: registerMediaKeys
    };
}());
