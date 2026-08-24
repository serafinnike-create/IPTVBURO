/*
  Identidade criptográfica desta instalação.

  O servidor de licença não aceita "sou o dispositivo X" — ele exige uma prova
  assinada a cada chamada. Este módulo cria e guarda o par de chaves usado para
  produzir essa assinatura, seguindo o mesmo contrato do Android e do Windows
  (ADR-004): um UUID de instalação mais um par ECDSA P-256.

  A chave privada nunca sai do KeyManager do Tizen e nunca é enviada. O que
  viaja é a chave pública, o UUID e uma assinatura sobre um texto canônico.

  O identificador visível ao usuário é derivado, não sorteado: SHA-256 sobre a
  chave pública mais o UUID, recortado em Base32. Isso deixa o servidor
  reconferir que o identificador pertence mesmo àquela chave.
*/
var BuroIdentity = (function () {
    'use strict';

    var ALIAS = 'iptvburo.device.identity.v1';

    /* Sem I, O, 0 e 1: o usuário lê este código de uma TV e digita noutro
       lugar, e esses caracteres se confundem. Igual ao servidor. */
    var ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    var PROOF_PREFIX = 'iptvburo-device-proof-v1';
    var KEY_PARAMS = { name: 'ECDSA', namedCurve: 'P-256' };
    var SIGN_PARAMS = { name: 'ECDSA', hash: 'SHA-256' };

    var cached = null;

    function available() {
        return Boolean(
            window.crypto && window.crypto.subtle &&
            window.crypto.subtle.generateKey && window.TextEncoder
        );
    }

    function secureAvailable() {
        return Boolean(
            typeof tizen !== 'undefined' && tizen.keymanager && tizen.keymanager.saveData
        );
    }

    function bytesToBase64(bytes) {
        var binary = '';
        var index;
        for (index = 0; index < bytes.length; index += 1) {
            binary += String.fromCharCode(bytes[index]);
        }
        return window.btoa(binary);
    }

    /* Nonces e provas atravessam JSON, mas o contrato HTTP usa Base64URL sem
       padding. As chaves SPKI/PKCS8 continuam no Base64 padrao exigido pelo
       servidor e pelo KeyManager. */
    function bytesToBase64Url(bytes) {
        return bytesToBase64(bytes)
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=+$/, '');
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

    /* UUID v4 a partir de bytes aleatórios reais. Math.random não serve: o
       identificador precisa ser imprevisível para não colidir nem ser adivinhado. */
    function randomUuid() {
        var bytes = new Uint8Array(16);
        var hex = [];
        var index;
        window.crypto.getRandomValues(bytes);
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        for (index = 0; index < 16; index += 1) {
            hex.push(('0' + bytes[index].toString(16)).slice(-2));
        }
        return hex.slice(0, 4).join('') + '-' + hex.slice(4, 6).join('') + '-' +
            hex.slice(6, 8).join('') + '-' + hex.slice(8, 10).join('') + '-' +
            hex.slice(10, 16).join('');
    }

    /*
      Mesma derivação do servidor: doze caracteres Base32 do digest, em três
      grupos de quatro. Precisa bater byte a byte, senão o registro é recusado
      com `bad_identity`.
    */
    function deriveDeviceId(publicKeyBytes, installationId, done, failed) {
        var uuidBytes = new window.TextEncoder().encode(installationId);
        var material = new Uint8Array(publicKeyBytes.length + uuidBytes.length);
        material.set(publicKeyBytes, 0);
        material.set(uuidBytes, publicKeyBytes.length);

        window.crypto.subtle.digest('SHA-256', material).then(function (buffer) {
            var digest = new Uint8Array(buffer);
            var bitBuffer = 0;
            var bitCount = 0;
            var code = '';
            var index;
            for (index = 0; index < digest.length && code.length < 12; index += 1) {
                bitBuffer = (bitBuffer << 8) | digest[index];
                bitCount += 8;
                while (bitCount >= 5 && code.length < 12) {
                    bitCount -= 5;
                    code += ALPHABET[(bitBuffer >>> bitCount) & 31];
                }
                bitBuffer = bitCount === 0 ? 0 : bitBuffer & ((1 << bitCount) - 1);
            }
            done(code.slice(0, 4) + '-' + code.slice(4, 8) + '-' + code.slice(8, 12));
        })['catch'](function () { failed({ code: 'IDENTITY_DIGEST_FAILED' }); });
    }

    function readStored() {
        var raw;
        if (!secureAvailable()) { return null; }
        try {
            raw = tizen.keymanager.getData({ name: ALIAS });
        } catch (missing) {
            return null;
        }
        try { return JSON.parse(raw); }
        catch (corrupt) { return null; }
    }

    function writeStored(record, done, failed) {
        try {
            tizen.keymanager.saveData(ALIAS, JSON.stringify(record), null, function () {
                done(record);
            }, function () { failed({ code: 'SECURE_STORE_UNAVAILABLE' }); });
        } catch (error) {
            failed({ code: 'SECURE_STORE_UNAVAILABLE' });
        }
    }

    function create(done, failed) {
        var installationId = randomUuid();
        var keyPair;
        var publicKeyBytes;

        window.crypto.subtle.generateKey(KEY_PARAMS, true, ['sign', 'verify'])
            .then(function (generated) {
                keyPair = generated;
                return window.crypto.subtle.exportKey('spki', generated.publicKey);
            })
            .then(function (spki) {
                publicKeyBytes = new Uint8Array(spki);
                /*
                  A chave privada é exportada uma única vez para caber no
                  KeyManager, que guarda texto. Ela não volta ao servidor nem
                  ao disco comum — o KeyManager é o cofre da plataforma.
                */
                return window.crypto.subtle.exportKey('pkcs8', keyPair.privateKey);
            })
            .then(function (pkcs8) {
                var privateKeyBase64 = bytesToBase64(new Uint8Array(pkcs8));
                deriveDeviceId(publicKeyBytes, installationId, function (deviceId) {
                    writeStored({
                        installationId: installationId,
                        deviceId: deviceId,
                        publicKey: bytesToBase64(publicKeyBytes),
                        privateKey: privateKeyBase64
                    }, done, failed);
                }, failed);
            })['catch'](function () { failed({ code: 'IDENTITY_GENERATION_FAILED' }); });
    }

    /*
      Devolve a identidade desta TV, criando-a na primeira chamada.

      Fica em cache porque toda requisição ao servidor precisa dela, e ler o
      KeyManager a cada chamada é caro numa TV.
    */
    function ensure(done, failed) {
        var stored;
        if (cached) { done(cached); return; }
        if (!available()) { failed({ code: 'WEB_CRYPTO_UNAVAILABLE' }); return; }
        if (!secureAvailable()) { failed({ code: 'SECURE_STORE_UNAVAILABLE' }); return; }

        stored = readStored();
        if (stored && stored.deviceId && stored.privateKey) {
            cached = stored;
            done(cached);
            return;
        }
        create(function (record) { cached = record; done(cached); }, failed);
    }

    /* Aleatório e novo a cada chamada: o servidor recusa um nonce repetido,
       que é o que impede alguém de regravar uma requisição capturada. */
    function newNonce() {
        var bytes = new Uint8Array(16);
        window.crypto.getRandomValues(bytes);
        return bytesToBase64Url(bytes);
    }

    /*
      Assina o texto canônico que o servidor reconstrói do seu lado. Qualquer
      divergência de formato derruba a verificação, então o prefixo, a ação, o
      identificador e o nonce entram exatamente nesta ordem.
    */
    function sign(action, nonce, done, failed) {
        ensure(function (identity) {
            var canonical = PROOF_PREFIX + '\n' + action + '\n' +
                identity.deviceId + '\n' + nonce;
            var payload = new window.TextEncoder().encode(canonical);

            window.crypto.subtle.importKey(
                'pkcs8', base64ToBytes(identity.privateKey), KEY_PARAMS, false, ['sign']
            ).then(function (key) {
                return window.crypto.subtle.sign(SIGN_PARAMS, key, payload);
            }).then(function (signature) {
                done(bytesToBase64Url(new Uint8Array(signature)));
            })['catch'](function () { failed({ code: 'PROOF_SIGNING_FAILED' }); });
        }, failed);
    }

    /* Só o que pode aparecer na interface. A chave privada nunca sai daqui. */
    function publicSummary() {
        if (!cached) { return null; }
        return { deviceId: cached.deviceId, installationId: cached.installationId };
    }

    function clearForTesting() { cached = null; }

    return {
        available: available,
        ensure: ensure,
        sign: sign,
        newNonce: newNonce,
        publicSummary: publicSummary,
        clearForTesting: clearForTesting
    };
}());
