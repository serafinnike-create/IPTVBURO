/*
  Licença: registro, validação e resgate de chave.

  O servidor não aceita "sou o dispositivo X" — cada chamada carrega uma prova
  ECDSA assinada com a chave privada desta TV (ver identity.js), sobre um nonce
  novo. Isso é o que impede alguém de regravar uma requisição capturada.

  A resposta é um documento assinado pelo servidor. Verificamos a assinatura
  contra uma chave pública embutida no app: sem essa conferência, quem
  controlasse a rede da TV poderia forjar uma licença válida, e apontar o app
  para outro servidor passaria a funcionar.

  Por que ECDSA e não Ed25519, como Android e Windows: Ed25519 só chegou ao
  Chromium na versão 137, e as TVs Samsung em uso vão do Chrome 47 ao M130.
  O servidor assina o mesmo documento nos dois algoritmos; aqui lemos o campo
  `signatureEcdsa`.
*/
var BuroLicense = (function () {
    'use strict';

    var BASE = 'https://iptvburo.iptvburo.workers.dev';
    var ENDPOINTS = {
        register: BASE + '/v1/register',
        validate: BASE + '/v1/validate',
        redeem: BASE + '/v1/redeem'
    };

    /*
      Metade pública do par ECDSA do servidor (`SIGNING_KEY_ECDSA`).

      Enquanto estiver vazia o app não consegue verificar nada e trata a
      licença como indisponível — nunca como válida.

      Obtida com `node services/license-server/public-key-ecdsa.mjs`, que a lê
      do deployment e a confere contra uma assinatura ao vivo. Não copie da
      saída de `generate-keys.mjs`: nada ali garante que aquele par é o que
      está em produção, e uma chave descasada só falha na TV, na ativação.

      Trocá-la invalida as licenças de todas as TVs instaladas. É release com
      migração, não manutenção.
    */
    var SERVER_PUBLIC_KEY_ECDSA =
        'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEFSVy3bJwdyua38aFtwbt/2TLY7eupVEu9Zy161+1LeW' +
        'IQaIzwntZTV3yUIZZptcfl38jltMVUN6ppDTCpK4zA==';

    var VERIFY_PARAMS = { name: 'ECDSA', hash: 'SHA-256' };
    var KEY_PARAMS = { name: 'ECDSA', namedCurve: 'P-256' };
    var ALIAS = 'iptvburo.license.v1';

    /* Quanto tempo o app funciona sem falar com o servidor. Além disso, uma
       licença paga uma vez poderia rodar em dez TVs com a rede desligada. */
    var OFFLINE_WINDOW_MS = 14 * 24 * 60 * 60 * 1000;

    var STATES = {
        UNREGISTERED: 'UNREGISTERED', TRIAL: 'TRIAL', ACTIVE: 'ACTIVE',
        GRACE: 'GRACE', REVOKED: 'REVOKED', REFUNDED: 'REFUNDED',
        EXPIRED: 'EXPIRED', UNAVAILABLE: 'UNAVAILABLE'
    };

    var cached = null;

    function configured() {
        return SERVER_PUBLIC_KEY_ECDSA.length > 0;
    }

    function base64ToBytes(value) {
        var binary = window.atob(value);
        var bytes = new Uint8Array(binary.length);
        var index;
        for (index = 0; index < binary.length; index += 1) {
            bytes[index] = binary.charCodeAt(index);
        }
        return bytes;
    }

    function readStored() {
        var raw;
        if (typeof tizen === 'undefined' || !tizen.keymanager) { return null; }
        try { raw = tizen.keymanager.getData({ name: ALIAS }); }
        catch (missing) { return null; }
        try { return JSON.parse(raw); }
        catch (corrupt) { return null; }
    }

    function writeStored(record) {
        if (typeof tizen === 'undefined' || !tizen.keymanager) { return; }
        try {
            tizen.keymanager.saveData(ALIAS, JSON.stringify(record), null,
                function () {}, function () {});
        } catch (ignored) { /* Sem persistência a licença é revalidada no próximo início. */ }
    }

    /*
      Aceita o documento apenas se a assinatura confere.

      O `payload` é verificado exatamente como chegou: reserializar o JSON
      antes de conferir produziria bytes diferentes e derrubaria toda
      assinatura válida.
    */
    function verifyEnvelope(envelope, done, failed) {
        var payloadBytes;
        if (!configured()) { failed({ code: 'LICENSE_KEY_MISSING' }); return; }
        if (!envelope || !envelope.payload || !envelope.signatureEcdsa) {
            failed({ code: 'LICENSE_UNSIGNED' });
            return;
        }
        payloadBytes = new window.TextEncoder().encode(envelope.payload);

        window.crypto.subtle.importKey(
            'spki', base64ToBytes(SERVER_PUBLIC_KEY_ECDSA), KEY_PARAMS, false, ['verify']
        ).then(function (key) {
            return window.crypto.subtle.verify(
                VERIFY_PARAMS, key, base64ToBytes(envelope.signatureEcdsa), payloadBytes
            );
        }).then(function (valid) {
            var document;
            if (!valid) { failed({ code: 'LICENSE_SIGNATURE_INVALID' }); return; }
            try { document = JSON.parse(envelope.payload); }
            catch (malformed) { failed({ code: 'LICENSE_MALFORMED' }); return; }
            done(document);
        })['catch'](function () { failed({ code: 'LICENSE_VERIFY_FAILED' }); });
    }

    /*
      O nonce do documento tem de ser o que enviamos.

      Sem esta conferência uma resposta antiga e legítima — capturada quando a
      licença ainda valia — poderia ser reapresentada para sempre.
    */
    function acceptDocument(document, expectedNonce, done, failed) {
        var record;
        if (expectedNonce && document.nonce !== expectedNonce) {
            failed({ code: 'LICENSE_NONCE_MISMATCH' });
            return;
        }
        record = {
            state: document.state || STATES.UNAVAILABLE,
            deviceId: document.deviceId || '',
            trialEndsAt: document.trialEndsAt || null,
            expiresAt: document.expiresAt || null,
            serverTime: document.serverTime || null,
            verifiedAt: new Date().toISOString()
        };
        cached = record;
        writeStored(record);
        done(record);
    }

    function call(url, body, nonce, done, failed) {
        BuroNetwork.json({
            url: url,
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }, function (envelope) {
            verifyEnvelope(envelope, function (document) {
                acceptDocument(document, nonce, done, failed);
            }, failed);
        }, function (error) {
            /*
              Só o código e o status HTTP. A mensagem da plataforma pode
              conter a URL, com usuário e senha embutidos.

              O status viaja porque distingue casos que exigem respostas
              opostas: 404 é uma TV que nunca se registrou e deve ganhar o
              período de teste; qualquer outro é uma falha em que registrar
              seria errado.
            */
            failed({
                code: (error && error.code) || 'LICENSE_UNREACHABLE',
                status: (error && error.status) || 0
            });
        });
    }

    function withProof(action, done, failed) {
        var nonce = BuroIdentity.newNonce();
        BuroIdentity.ensure(function (identity) {
            BuroIdentity.sign(action, nonce, function (proof) {
                done({
                    deviceId: identity.deviceId,
                    nonce: nonce,
                    proof: proof,
                    installationId: identity.installationId,
                    publicKey: identity.publicKey
                }, nonce);
            }, failed);
        }, failed);
    }

    function register(done, failed) {
        withProof('register', function (body, nonce) {
            call(ENDPOINTS.register, body, nonce, done, failed);
        }, failed);
    }

    function validate(done, failed) {
        withProof('validate', function (body, nonce) {
            /* O registro é que carrega a identidade completa. */
            delete body.publicKey;
            delete body.installationId;
            call(ENDPOINTS.validate, body, nonce, done, failed);
        }, failed);
    }

    function redeem(keyCode, done, failed) {
        var code = String(keyCode || '').trim().toUpperCase();
        if (!code) { failed({ code: 'KEY_REQUIRED' }); return; }
        withProof('redeem', function (body, nonce) {
            delete body.publicKey;
            delete body.installationId;
            body.key = code;
            call(ENDPOINTS.redeem, body, nonce, done, failed);
        }, failed);
    }

    function snapshot() {
        if (!cached) { cached = readStored(); }
        return cached;
    }

    /*
      Decide se o app pode reproduzir. Mesma política do Windows, na mesma
      ordem — a sequência importa: uma licença devolvida para de valer na hora,
      mesmo dentro da janela offline.
    */
    function decide(nowMillis) {
        var record = snapshot();
        var now = typeof nowMillis === 'number' ? nowMillis : Date.now();
        var verifiedAt;
        var expiry;

        if (!configured()) { return { allowed: false, reason: 'UNAVAILABLE' }; }
        if (!record) { return { allowed: false, reason: 'UNREGISTERED' }; }

        if (record.state === STATES.REVOKED || record.state === STATES.REFUNDED) {
            return { allowed: false, reason: 'REVOKED' };
        }

        verifiedAt = record.verifiedAt ? Date.parse(record.verifiedAt) : 0;
        if (verifiedAt && now - verifiedAt > OFFLINE_WINDOW_MS) {
            return { allowed: false, reason: 'NEEDS_VERIFICATION' };
        }

        if (record.state === STATES.ACTIVE || record.state === STATES.GRACE) {
            expiry = record.expiresAt ? Date.parse(record.expiresAt) : 0;
            if (expiry && now > expiry) { return { allowed: false, reason: 'EXPIRED' }; }
            return { allowed: true, trial: false, expiresAt: record.expiresAt };
        }

        if (record.state === STATES.TRIAL) {
            expiry = record.trialEndsAt ? Date.parse(record.trialEndsAt) : 0;
            if (expiry && now > expiry) { return { allowed: false, reason: 'TRIAL_ENDED' }; }
            return { allowed: true, trial: true, expiresAt: record.trialEndsAt };
        }

        if (record.state === STATES.EXPIRED) { return { allowed: false, reason: 'EXPIRED' }; }
        return { allowed: false, reason: 'UNAVAILABLE' };
    }

    function deviceId() {
        var record = snapshot();
        return record ? record.deviceId : '';
    }

    function clearForTesting() { cached = null; }

    /* Só para os testes: permite injetar a chave pública sem um release. */
    function useServerKeyForTesting(base64) { SERVER_PUBLIC_KEY_ECDSA = base64 || ''; }

    return {
        configured: configured,
        register: register,
        validate: validate,
        redeem: redeem,
        snapshot: snapshot,
        decide: decide,
        deviceId: deviceId,
        STATES: STATES,
        clearForTesting: clearForTesting,
        useServerKeyForTesting: useServerKeyForTesting
    };
}());
