/*
  O teste de ligacao da televisao.

  Quem esta a ver um filme que trava nao sabe se a culpa e do Wi-Fi, do provedor
  ou do aplicativo. Sem resposta, conclui que o aplicativo esta com defeito. Cada
  leitura aqui vira uma frase que a pessoa consegue usar.

  Os limites sao os mesmos das outras plataformas, de proposito: uma televisao que
  chamasse "boa" a mesma ligacao que o Windows chama "ruim" seria o produto a
  discutir consigo proprio.
*/
var BuroDiagnostics = (function () {
    'use strict';

    /*
      Velocidades, em megabits por segundo, que separam um veredito do seguinte.
      Escolhidas pelo que o aplicativo reproduz, nao por numeros redondos: 1080p
      de um provedor tipico fica perto de 8, 4K perto de 25, e a TV ao vivo nao
      tem buffer para absorver uma queda.
    */
    var POOR_DOWNLOAD_MBPS = 10;
    var HD_DOWNLOAD_MBPS = 15;
    var UHD_DOWNLOAD_MBPS = 30;

    /*
      Latencia nao reduz a velocidade, mas atrasa cada troca de canal e cada
      recuperacao de um segmento perdido — que e o que a pessoa sente como
      "fica travando" numa ligacao cuja velocidade parece boa.
    */
    var GOOD_PING_MS = 60;
    var POOR_PING_MS = 150;

    /* Qualquer perda constante e um problema: um stream nao pede duas vezes. */
    var POOR_LOSS_PERCENT = 2;

    /* Abaixo disto, um transferencia diz mais sobre o buffer do que sobre a rede. */
    var MINIMUM_SAMPLE_MS = 250;

    /* Quanto tempo a medicao pode correr antes de reportar o que leu. */
    var BUDGET_MS = 6000;

    /* Idas e voltas suficientes para ver perda sem fazer a pessoa esperar. */
    var PING_ATTEMPTS = 6;

    function megabitsPerSecond(bytes, milliseconds) {
        if (!bytes || bytes <= 0 || milliseconds < MINIMUM_SAMPLE_MS) { return null; }
        return (bytes * 8) / (milliseconds / 1000) / 1000000;
    }

    function downloadVerdict(mbps) {
        if (mbps === null || mbps === undefined) { return 'PROBLEM'; }
        if (mbps < POOR_DOWNLOAD_MBPS) { return 'PROBLEM'; }
        if (mbps < HD_DOWNLOAD_MBPS) { return 'WARNING'; }
        return 'GOOD';
    }

    function pingVerdict(ms) {
        if (ms === null || ms === undefined) { return 'PROBLEM'; }
        if (ms > POOR_PING_MS) { return 'PROBLEM'; }
        if (ms > GOOD_PING_MS) { return 'WARNING'; }
        return 'GOOD';
    }

    function lossVerdict(percent) {
        if (percent === null || percent === undefined) { return 'WARNING'; }
        if (percent >= POOR_LOSS_PERCENT) { return 'PROBLEM'; }
        if (percent > 0) { return 'WARNING'; }
        return 'GOOD';
    }

    /*
      Conservador de proposito: dizer que a ligacao aguenta 4K quando ela engasga
      e pior do que nao dizer nada, porque a pessoa passa a noite a culpar o
      aplicativo.
    */
    function qualityCeiling(mbps) {
        if (mbps === null || mbps === undefined) { return 'unknown'; }
        if (mbps < POOR_DOWNLOAD_MBPS) { return 'unstable'; }
        if (mbps < HD_DOWNLOAD_MBPS) { return 'sd'; }
        if (mbps < UHD_DOWNLOAD_MBPS) { return 'hd'; }
        return 'uhd';
    }

    /*
      O veredito e a pior leitura, nunca a media.

      Uma ligacao com 200 Mbit/s e 8% de perda e uma ligacao que trava. Fazer a
      media disso daria "tudo bem" a quem esta a ver a imagem cortar.
    */
    function overall(findings) {
        var i;
        for (i = 0; i < findings.length; i += 1) {
            if (findings[i].severity === 'PROBLEM') { return 'PROBLEM'; }
        }
        for (i = 0; i < findings.length; i += 1) {
            if (findings[i].severity === 'WARNING') { return 'WARNING'; }
        }
        return 'GOOD';
    }

    /*
      Como a televisao chegou a rede, quando a plataforma souber dizer.

      A Tizen expoe isto so em algumas versoes, e uma que nao expoe nao pode
      inventar: 'unknown' e uma resposta honesta, 'wired' errado mandaria a
      pessoa procurar um cabo que nao existe.
    */
    function linkKind() {
        try {
            if (typeof webapis !== 'undefined' && webapis.network) {
                var type = webapis.network.getActiveConnectionType();
                if (type === webapis.network.NetworkActiveConnectionType.WIFI) { return 'wireless'; }
                if (type === webapis.network.NetworkActiveConnectionType.CELLULAR) { return 'wireless'; }
                if (type === webapis.network.NetworkActiveConnectionType.ETHERNET) { return 'wired'; }
                if (type === webapis.network.NetworkActiveConnectionType.DISCONNECTED) { return 'none'; }
            }
        } catch (ignore) {
            /* Uma API ausente nao pode derrubar o teste inteiro. */
        }
        if (typeof navigator !== 'undefined' && navigator.onLine === false) { return 'none'; }
        return 'unknown';
    }

    /* Endereco, mascara e gateway, quando a plataforma os der. */
    function network() {
        var result = { kind: linkKind(), address: null, netmask: null, gateway: null };
        try {
            if (typeof webapis !== 'undefined' && webapis.network) {
                result.address = webapis.network.getIp() || null;
                result.netmask = webapis.network.getSubnetMask() || null;
                result.gateway = webapis.network.getGateway() || null;
            }
        } catch (ignore) {
            /* Sem estes o teste continua util: as leituras de rede e que importam. */
        }
        return result;
    }

    /*
      Mede a ligacao ao provedor lendo do proprio catalogo.

      Contra o provedor do cliente, e nao contra um servidor de teste, porque a
      pergunta real e "a minha internet esta lenta ou o meu provedor esta lento".
      Pelo catalogo e nao por um stream: um stream consumiria uma das ligacoes
      simultaneas da conta e pararia a televisao do outro quarto.
    */
    /*
      O endereco que este teste usa, montado aqui.

      Podia vir de xtream.js, mas o diagnostico e uma coisa a parte e nao vale a
      pena atar os dois modulos por uma linha. O catalogo e nao um stream: um
      stream consumiria uma das ligacoes simultaneas da conta e pararia a
      televisao do outro quarto.
    */
    function probeUrl(secret) {
        return secret.server + '/player_api.php?username=' + encodeURIComponent(secret.username) +
            '&password=' + encodeURIComponent(secret.password) + '&action=get_vod_streams';
    }

    function measureTransfer(url, done) {
        var started = new Date().getTime();
        BuroNetwork.request({
            url: url,
            method: 'GET',
            timeoutMs: BUDGET_MS,
            pinHost: true,
            maxBytes: 40 * 1024 * 1024
        }, function (text) {
            var took = new Date().getTime() - started;
            /* O comprimento do texto e a melhor medida de bytes que o XHR da. */
            done({ bytes: text ? text.length : 0, milliseconds: took });
        }, function () {
            done(null);
        });
    }

    /*
      Idas e voltas ao provedor. As que falham sao contadas, nao lancadas: uma
      ligacao que perde um pedido em dez e exatamente o que a pessoa precisa de
      saber, e um erro substituiria isso por "o teste falhou".
    */
    function measureLatency(url, attempts, done) {
        var samples = [];
        var attempted = 0;

        function next() {
            if (attempted >= attempts) {
                done({ samples: samples, attempted: attempted });
                return;
            }
            attempted += 1;
            var started = new Date().getTime();
            BuroNetwork.request({
                url: url,
                method: 'GET',
                timeoutMs: 3000,
                pinHost: true,
                maxBytes: 4 * 1024 * 1024
            }, function () {
                samples.push(new Date().getTime() - started);
                next();
            }, function () {
                next();
            });
        }

        next();
    }

    function median(values) {
        if (!values.length) { return null; }
        var sorted = values.slice().sort(function (a, b) { return a - b; });
        return sorted[Math.floor(sorted.length / 2)];
    }

    function lossPercent(sample) {
        if (!sample || sample.attempted <= 0) { return null; }
        return (sample.attempted - sample.samples.length) * 100 / sample.attempted;
    }

    return {
        megabitsPerSecond: megabitsPerSecond,
        downloadVerdict: downloadVerdict,
        pingVerdict: pingVerdict,
        lossVerdict: lossVerdict,
        qualityCeiling: qualityCeiling,
        overall: overall,
        linkKind: linkKind,
        network: network,
        probeUrl: probeUrl,
        measureTransfer: measureTransfer,
        measureLatency: measureLatency,
        median: median,
        lossPercent: lossPercent,
        PING_ATTEMPTS: PING_ATTEMPTS,
        BUDGET_MS: BUDGET_MS
    };
}());

if (typeof module !== 'undefined' && module.exports) {
    module.exports = BuroDiagnostics;
}
