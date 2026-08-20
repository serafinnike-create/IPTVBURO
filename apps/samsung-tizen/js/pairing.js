/*
  Receber do celular o que é penoso digitar no controle remoto.

  Uma chave do TMDb tem 32 caracteres, ou 239 se for o token v4 que a maioria
  copia do site. Digitar qualquer uma delas num controle Samsung é arrastar um
  cursor pelo teclado da tela, uma letra por vez. As pessoas desistem, e o app
  que precisa da chave para mostrar arte, elenco e sinopse continua vazio — o
  que se lê como "o app é fraco" e não como "falta a chave".

  O runtime web do Tizen não abre socket de escuta, então ninguém consegue falar
  *com* a TV; ela só faz requisições de saída. Por isso a TV pede um código, o
  celular envia o dado contra esse código, e a TV busca o resultado. O servidor
  é uma caixa de correio, e é o mesmo que já responde pelo licenciamento.

  A TV pergunta a cada dois segundos enquanto o código está na tela, e desiste
  quando ele vence. Nada aqui fica gravado: o código vive na memória da tela.
*/
var BuroPairing = (function () {
    'use strict';

    var BASE = 'https://iptvburo.iptvburo.workers.dev';
    var POLL_INTERVAL_MS = 2000;
    var REQUEST_TIMEOUT_MS = 12000;
    var MAX_RESPONSE_BYTES = 16 * 1024;
    /* O servidor aceita três; a TV só pede estes dois por enquanto. */
    var KINDS = { tmdb_key: true, critics_key: true };

    function post(path, body, success, failure) {
        return BuroNetwork.json({
            url: BASE + path,
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(body),
            maxBytes: MAX_RESPONSE_BYTES,
            timeoutMs: REQUEST_TIMEOUT_MS
        }, success, failure);
    }

    /*
      Pede um código e fica perguntando até ele chegar ou vencer.

      Devolve um controle com `cancel`: sair da tela precisa parar o relógio, ou
      a TV segue perguntando por um código que ninguém mais está vendo.
    */
    function start(kind, callbacks) {
        var handlers = callbacks || {};
        var timer = null;
        var request = null;
        var stopped = false;
        var code = null;
        var deadline = 0;

        function stop() {
            stopped = true;
            if (timer) { window.clearTimeout(timer); timer = null; }
            if (request && request.abort) { request.abort(); }
            request = null;
        }

        function fail(error) {
            if (stopped) { return; }
            stop();
            if (handlers.failure) { handlers.failure(error || { code: 'PAIRING_FAILED' }); }
        }

        function poll() {
            if (stopped) { return; }
            if (Date.now() > deadline) { fail({ code: 'PAIRING_EXPIRED' }); return; }
            request = post('/v1/pair/claim', { code: code }, function (payload) {
                request = null;
                if (stopped) { return; }
                if (payload && payload.status === 'ready') {
                    stop();
                    if (handlers.success) { handlers.success(String(payload.payload || '')); }
                    return;
                }
                /* `pending` é o caso normal: ninguém enviou ainda. */
                timer = window.setTimeout(poll, POLL_INTERVAL_MS);
            }, function (error) {
                request = null;
                if (stopped) { return; }
                /* Um código que sumiu do servidor venceu; qualquer outra falha é
                   de rede e merece outra tentativa em vez de derrubar a tela. */
                if (error && error.status === 404) { fail({ code: 'PAIRING_EXPIRED' }); return; }
                timer = window.setTimeout(poll, POLL_INTERVAL_MS);
            });
        }

        if (!KINDS[kind]) {
            if (handlers.failure) { handlers.failure({ code: 'PAIRING_KIND_INVALID' }); }
            return { cancel: function () {} };
        }

        request = post('/v1/pair/start', { kind: kind }, function (payload) {
            var seconds = Number(payload && payload.expiresInSeconds) || 300;
            request = null;
            if (stopped) { return; }
            code = String(payload && payload.code || '');
            if (!/^[0-9]{6}$/.test(code)) { fail({ code: 'PAIRING_FAILED' }); return; }
            deadline = Date.now() + seconds * 1000;
            if (handlers.code) { handlers.code(code, seconds); }
            timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }, function (error) {
            request = null;
            fail(error);
        });

        return { cancel: stop };
    }

    /* O endereço que o usuário digita no celular. Curto de propósito: ele vai
       ser lido de longe e digitado à mão. */
    function phoneUrl() { return BASE.replace(/^https:\/\//, '') + '/parear'; }

    return { start: start, phoneUrl: phoneUrl, BASE: BASE };
}());
