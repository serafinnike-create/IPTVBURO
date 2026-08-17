/*
  Contratos de identidade e licença. Nenhum dado real de servidor.

  O que estes testes provam: que a prova assinada bate com o formato que o
  servidor verifica, que o identificador é derivado do mesmo jeito, e que uma
  licença sem assinatura válida nunca é aceita.

  O que eles NÃO provam: que o servidor real responde. Isso exige a chave
  pública ECDSA implantada e uma TV.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;
var nodeCrypto = require('node:crypto');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function bytesToBase64(bytes) { return Buffer.from(bytes).toString('base64'); }

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var secure = {};

    /*
      O jsdom define `crypto` como getter, então uma atribuição direta é
      ignorada em silêncio e o módulo conclui que não há Web Crypto.
    */
    Object.defineProperty(window, 'crypto', {
        value: nodeCrypto.webcrypto, configurable: true, writable: true
    });
    window.TextEncoder = TextEncoder;
    window.btoa = function (value) { return Buffer.from(value, 'binary').toString('base64'); };
    window.atob = function (value) { return Buffer.from(value, 'base64').toString('binary'); };
    window.tizen = {
        keymanager: {
            getDataAliasList: function () {
                return Object.keys(secure).map(function (name) { return { name: name }; });
            },
            saveData: function (name, value, password, success) { secure[name] = value; success(); },
            getData: function (alias) {
                if (!secure[alias.name]) { throw { name: 'NotFoundError' }; }
                return secure[alias.name];
            },
            removeData: function (alias) { delete secure[alias.name]; }
        }
    };

    ['js/domain.js', 'js/network.js', 'js/identity.js', 'js/license.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });

    window.__secure = secure;
    return window;
}

/* Mesma derivação do servidor (index.js: deriveDeviceId). Reimplementada aqui
   de propósito: se as duas divergirem, o teste falha em vez de deixar o
   registro ser recusado só em produção. */
async function serverDeriveDeviceId(publicKeyBytes, installationId) {
    var ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    var uuidBytes = new TextEncoder().encode(installationId);
    var material = new Uint8Array(publicKeyBytes.length + uuidBytes.length);
    material.set(publicKeyBytes, 0);
    material.set(uuidBytes, publicKeyBytes.length);
    var digest = new Uint8Array(await nodeCrypto.webcrypto.subtle.digest('SHA-256', material));

    var bitBuffer = 0;
    var bitCount = 0;
    var code = '';
    for (var index = 0; index < digest.length && code.length < 12; index += 1) {
        bitBuffer = (bitBuffer << 8) | digest[index];
        bitCount += 8;
        while (bitCount >= 5 && code.length < 12) {
            bitCount -= 5;
            code += ALPHABET[(bitBuffer >>> bitCount) & 31];
        }
        bitBuffer = bitCount === 0 ? 0 : bitBuffer & ((1 << bitCount) - 1);
    }
    return code.slice(0, 4) + '-' + code.slice(4, 8) + '-' + code.slice(8, 12);
}

function promised(fn) {
    return new Promise(function (resolve, reject) { fn(resolve, reject); });
}

async function run() {
    var window = makeWindow();
    var identity;
    var proof;
    var serverKeys;
    var decision;

    process.stdout.write('Identidade do dispositivo\n');
    identity = await promised(function (ok, no) { window.BuroIdentity.ensure(ok, no); });

    check('o identificador tem o formato que o servidor aceita',
        /^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(identity.deviceId));
    check('a instalação é um UUID v4',
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
            .test(identity.installationId));

    check('o identificador é derivado da chave pública, como o servidor recalcula',
        identity.deviceId === await serverDeriveDeviceId(
            new Uint8Array(Buffer.from(identity.publicKey, 'base64')),
            identity.installationId
        ));

    check('a chave privada fica no KeyManager, nunca no armazenamento comum',
        JSON.stringify(window.localStorage).indexOf(identity.privateKey) === -1 &&
        JSON.stringify(window.__secure).indexOf(identity.privateKey) >= 0);

    check('o resumo público não carrega a chave privada', (function () {
        var summary = JSON.stringify(window.BuroIdentity.publicSummary());
        return summary.indexOf(identity.privateKey) === -1 &&
            summary.indexOf('privateKey') === -1;
    }()));

    process.stdout.write('Prova assinada\n');
    proof = await promised(function (ok, no) {
        window.BuroIdentity.sign('register', 'nonce-fixo', ok, no);
    });

    check('a prova verifica contra a chave pública do dispositivo', await (async function () {
        var key = await nodeCrypto.webcrypto.subtle.importKey(
            'spki', Buffer.from(identity.publicKey, 'base64'),
            { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']
        );
        /* Exatamente o texto que o servidor reconstrói em canonicalDeviceProof. */
        var canonical = 'iptvburo-device-proof-v1\nregister\n' + identity.deviceId + '\nnonce-fixo';
        return nodeCrypto.webcrypto.subtle.verify(
            { name: 'ECDSA', hash: 'SHA-256' }, key,
            Buffer.from(proof, 'base64'), new TextEncoder().encode(canonical)
        );
    }()));

    check('uma prova de outra ação não serve', await (async function () {
        var key = await nodeCrypto.webcrypto.subtle.importKey(
            'spki', Buffer.from(identity.publicKey, 'base64'),
            { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']
        );
        var canonical = 'iptvburo-device-proof-v1\nvalidate\n' + identity.deviceId + '\nnonce-fixo';
        return !(await nodeCrypto.webcrypto.subtle.verify(
            { name: 'ECDSA', hash: 'SHA-256' }, key,
            Buffer.from(proof, 'base64'), new TextEncoder().encode(canonical)
        ));
    }()));

    check('cada chamada usa um nonce novo',
        window.BuroIdentity.newNonce() !== window.BuroIdentity.newNonce());

    process.stdout.write('Verificação da licença\n');
    serverKeys = await nodeCrypto.webcrypto.subtle.generateKey(
        { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']
    );
    var serverPublic = bytesToBase64(new Uint8Array(
        await nodeCrypto.webcrypto.subtle.exportKey('spki', serverKeys.publicKey)
    ));

    /*
      A chave do servidor está compilada no app, então `configured()` é
      verdadeiro. O que precisa continuar valendo é que ela sozinha não libera
      nada: sem uma licença verificada, a decisão é negar.
    */
    check('a chave pública do servidor está embutida no app',
        window.BuroLicense.configured());
    check('ter a chave não basta: sem licença verificada nada é liberado',
        window.BuroLicense.decide().allowed === false);

    /* Uma chave vazia é o estado de um build mal preparado, e também não libera. */
    window.BuroLicense.useServerKeyForTesting('');
    check('um build sem a chave do servidor recusa tudo',
        !window.BuroLicense.configured() &&
        window.BuroLicense.decide().allowed === false);

    window.BuroLicense.useServerKeyForTesting(serverPublic);

    async function signedEnvelope(document, key) {
        var payload = JSON.stringify(document, Object.keys(document).sort());
        var signature = await nodeCrypto.webcrypto.subtle.sign(
            { name: 'ECDSA', hash: 'SHA-256' }, key || serverKeys.privateKey,
            new TextEncoder().encode(payload)
        );
        return { payload: payload, signatureEcdsa: bytesToBase64(new Uint8Array(signature)) };
    }

    /* Intercepta a rede: nenhuma requisição real sai daqui. */
    function respondWith(envelope) {
        window.BuroNetwork.json = function (options, success) { success(envelope); };
    }

    var nonceSeen = null;
    var originalSign = window.BuroIdentity.sign;
    window.BuroIdentity.sign = function (action, nonce, ok, no) {
        nonceSeen = nonce;
        originalSign(action, nonce, ok, no);
    };

    respondWith(await signedEnvelope({
        deviceId: identity.deviceId, state: 'ACTIVE',
        serverTime: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 86400000).toISOString(),
        nonce: 'sera-substituido'
    }));

    check('uma licença cujo nonce não bate é recusada', await promised(function (ok) {
        window.BuroLicense.validate(function () { ok(false); }, function (error) {
            ok(error.code === 'LICENSE_NONCE_MISMATCH');
        });
    }));

    /* Agora com o nonce correto, capturado da própria assinatura. */
    window.BuroNetwork.json = async function (options, success) {
        success(await signedEnvelope({
            deviceId: identity.deviceId, state: 'ACTIVE',
            serverTime: new Date().toISOString(),
            expiresAt: new Date(Date.now() + 86400000).toISOString(),
            nonce: nonceSeen
        }));
    };

    check('uma licença ativa e bem assinada é aceita', await promised(function (ok) {
        window.BuroLicense.validate(function (record) { ok(record.state === 'ACTIVE'); },
            function () { ok(false); });
    }));
    check('a licença aceita libera a reprodução', window.BuroLicense.decide().allowed === true);

    /* Assinada por outra chave: é o que um servidor forjado produziria. */
    var attackerKeys = await nodeCrypto.webcrypto.subtle.generateKey(
        { name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']
    );
    window.BuroNetwork.json = async function (options, success) {
        success(await signedEnvelope({
            deviceId: identity.deviceId, state: 'ACTIVE',
            serverTime: new Date().toISOString(), nonce: nonceSeen
        }, attackerKeys.privateKey));
    };
    check('uma licença assinada por outra chave é recusada', await promised(function (ok) {
        window.BuroLicense.validate(function () { ok(false); }, function (error) {
            ok(error.code === 'LICENSE_SIGNATURE_INVALID');
        });
    }));

    /* Sem assinatura nenhuma: o caso de um intermediário na rede. */
    respondWith({ payload: JSON.stringify({ state: 'ACTIVE' }) });
    check('uma resposta sem assinatura é recusada', await promised(function (ok) {
        window.BuroLicense.validate(function () { ok(false); }, function (error) {
            ok(error.code === 'LICENSE_UNSIGNED');
        });
    }));

    process.stdout.write('Primeiro boot desta TV\n');
    /*
      O servidor responde 404 a um dispositivo que nunca se apresentou. É esse
      caso — e só ele — que deve virar um registro, porque é o que cria o
      período de teste. Distinguir pelo status evita registrar quando a
      internet caiu ou o servidor está fora.
    */
    /* A prova é assinada com crypto.subtle, então o resultado chega depois. */
    function validateFailure(networkError) {
        return promised(function (resolve) {
            window.BuroNetwork.json = function (options, success, failure) {
                failure(networkError);
            };
            window.BuroLicense.validate(
                function () { resolve(null); },
                function (error) { resolve(error); }
            );
        });
    }

    var unknownDevice = await validateFailure({
        code: 'HTTP_ERROR', status: 404, message: 'HTTP_ERROR'
    });
    check('uma TV desconhecida é reconhecida pelo status 404',
        Boolean(unknownDevice) && unknownDevice.status === 404);

    var offline = await validateFailure({
        code: 'NETWORK_ERROR', status: 0, message: 'NETWORK_ERROR'
    });
    check('uma falha de rede não se parece com uma TV desconhecida',
        Boolean(offline) && offline.status !== 404);

    var leaky = await validateFailure({
        code: 'HTTP_ERROR', status: 500,
        message: 'https://provider.test/u/senha-secreta falhou'
    });
    check('a falha carrega apenas código e status, nunca a mensagem original',
        Boolean(leaky) && leaky.code === 'HTTP_ERROR' && leaky.status === 500 &&
        JSON.stringify(leaky).indexOf('senha-secreta') === -1);

    process.stdout.write('Política de acesso\n');
    window.BuroIdentity.sign = originalSign;

    function decideWith(state, extra) {
        var record = { state: state, deviceId: identity.deviceId,
            verifiedAt: new Date().toISOString() };
        Object.keys(extra || {}).forEach(function (key) { record[key] = extra[key]; });
        window.tizen.keymanager.saveData('iptvburo.license.v1', JSON.stringify(record),
            null, function () {}, function () {});
        window.BuroLicense.clearForTesting();
        return window.BuroLicense.decide();
    }

    decision = decideWith('REVOKED');
    check('uma licença devolvida bloqueia na hora, mesmo dentro da janela offline',
        !decision.allowed && decision.reason === 'REVOKED');

    decision = decideWith('TRIAL', { trialEndsAt: new Date(Date.now() + 86400000).toISOString() });
    check('um teste dentro do prazo é liberado', decision.allowed && decision.trial === true);

    decision = decideWith('TRIAL', { trialEndsAt: new Date(Date.now() - 1000).toISOString() });
    check('um teste vencido bloqueia',
        !decision.allowed && decision.reason === 'TRIAL_ENDED');

    decision = decideWith('ACTIVE', { expiresAt: new Date(Date.now() - 1000).toISOString() });
    check('uma assinatura vencida bloqueia',
        !decision.allowed && decision.reason === 'EXPIRED');

    /* Quinze dias sem falar com o servidor: além da janela de catorze. */
    var stale = { state: 'ACTIVE', deviceId: identity.deviceId,
        verifiedAt: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000).toISOString() };
    window.tizen.keymanager.saveData('iptvburo.license.v1', JSON.stringify(stale),
        null, function () {}, function () {});
    window.BuroLicense.clearForTesting();
    decision = window.BuroLicense.decide();
    check('além da janela offline o app exige nova verificação',
        !decision.allowed && decision.reason === 'NEEDS_VERIFICATION');

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.message + '\n');
    process.exitCode = 1;
});
