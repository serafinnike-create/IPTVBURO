/*
  voice.js — ditar a busca pelo microfone do controle.

  Muita TV Samsung tem microfone no controle, e digitar num teclado de tela
  com as setas e o pior jeito de escrever "Senhor dos Aneis". Falar e o
  caminho natural, e o aparelho ja tem o que e preciso.

  Duas vias, e o app usa a que existir:

  A primeira e a Web Speech API. Onde o firmware a expoe, o aplicativo
  escuta e recebe o texto direto, sem sair da tela.

  A segunda e o proprio teclado da TV: um campo `type="search"` faz o teclado
  virtual da Samsung mostrar o botao de microfone. Nao e o app que escuta —
  e o sistema — mas o resultado chega no campo do mesmo jeito, e funciona em
  modelos onde a API nao existe.

  Por que nao pedir privilegio de microfone: nao e preciso. A Web Speech API
  usa o canal de voz que a plataforma ja gerencia, e o teclado e do sistema.
  Um privilegio a mais na loja pediria uma justificacao que o aplicativo nao
  tem.

  O que este modulo NAO faz: gravar audio, guardar o que foi dito, ou mandar
  qualquer coisa para fora. O reconhecimento e da plataforma e o resultado e
  um texto que vai para o campo de busca e morre ali.
*/
var BuroVoice = (function () {
    'use strict';

    var recogniser = null;
    var listening = false;

    /* O construtor tem prefixo em quase todo lugar, e nome limpo em alguns
       firmwares recentes. */
    function constructor() {
        if (typeof window === 'undefined') { return null; }
        return window.SpeechRecognition || window.webkitSpeechRecognition || null;
    }

    function available() { return Boolean(constructor()); }

    function isListening() { return listening; }

    /*
      Escuta uma vez e devolve o que ouviu.

      `interimResults` fica desligado: numa TV o texto parcial aparece e some
      enquanto a pessoa ainda fala, o que se le como se o aparelho estivesse
      errando. Uma resposta so, quando ela existe, e mais calma.

      `continuous` tambem: a busca e uma frase, nao um ditado.
    */
    function listen(language, onResult, onError) {
        var Recognition = constructor();
        if (!Recognition) { onError({ code: 'VOICE_UNAVAILABLE' }); return false; }
        stop();
        try { recogniser = new Recognition(); }
        catch (ignoredCreate) { onError({ code: 'VOICE_UNAVAILABLE' }); return false; }
        recogniser.lang = String(language || 'pt-BR');
        recogniser.continuous = false;
        recogniser.interimResults = false;
        recogniser.maxAlternatives = 1;
        recogniser.onresult = function (event) {
            var results = event && event.results;
            var text = results && results[0] && results[0][0] ? results[0][0].transcript : '';
            listening = false;
            recogniser = null;
            onResult(String(text || ''));
        };
        recogniser.onerror = function (event) {
            var reason = event && event.error ? String(event.error) : 'unknown';
            listening = false;
            recogniser = null;
            /*
              `no-speech` e `aborted` nao sao falhas: sao a pessoa desistindo,
              ou o silencio. Tratar os dois como erro encheria a tela de aviso
              vermelho por causa de uma pausa.
            */
            onError({ code: reason === 'no-speech' || reason === 'aborted' ?
                'VOICE_CANCELLED' : 'VOICE_FAILED', reason: reason });
        };
        recogniser.onend = function () { listening = false; };
        try { recogniser.start(); }
        catch (ignoredStart) {
            listening = false; recogniser = null;
            onError({ code: 'VOICE_UNAVAILABLE' });
            return false;
        }
        listening = true;
        return true;
    }

    function stop() {
        if (!recogniser) { return; }
        try { recogniser.abort(); } catch (ignoredAbort) { /* Ja terminou. */ }
        recogniser = null;
        listening = false;
    }

    return {
        available: available,
        isListening: isListening,
        listen: listen,
        stop: stop
    };
}());
