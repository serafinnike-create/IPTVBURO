/*
  A chave compilada no app é mesmo a do servidor em produção?

  Esta é a única pergunta que os outros testes não podem responder: eles geram
  um par próprio e verificam o app contra ele, o que passa mesmo se o app
  carregar a chave errada. Aqui a resposta vem do deployment.

  Uma chave descasada não falha em lugar nenhum até a TV do cliente tentar
  ativar — e aí falha para todo mundo ao mesmo tempo.

  Precisa de rede. Sem conexão o teste é pulado em vez de falhar, para não
  quebrar a suíte de quem trabalha offline.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var nodeCrypto = require('node:crypto');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var ENDPOINT = 'https://iptvburo.iptvburo.workers.dev/v1/signing-key-check';
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

/* Lê a constante direto do fonte: importar o módulo exigiria um DOM inteiro
   para uma string. */
function embeddedKey() {
    var source = fs.readFileSync(path.join(APP_DIR, 'js', 'license.js'), 'utf8');
    var block = source.match(/var SERVER_PUBLIC_KEY_ECDSA\s*=\s*([\s\S]*?);/);
    if (!block) { return ''; }
    var pieces = block[1].match(/'([^']*)'/g) || [];
    return pieces.map(function (piece) { return piece.slice(1, -1); }).join('');
}

/* O servidor exige 22 caracteres base64url; base64 comum volta como bad_nonce. */
function freshNonce() {
    return Buffer.from(nodeCrypto.webcrypto.getRandomValues(new Uint8Array(16)))
        .toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}

function withoutPadding(value) { return String(value).replace(/=+$/, ''); }

async function run() {
    var key = embeddedKey();

    process.stdout.write('Chave do servidor compilada no app\n');
    check('a chave está preenchida', key.length > 0);
    check('tem o formato de uma chave pública ECDSA P-256',
        /^MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/.test(key));

    if (!key) {
        process.stdout.write('\nSem chave embutida; nada a conferir contra o servidor.\n');
        process.exitCode = 1;
        return;
    }

    var response;
    try {
        response = await fetch(ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nonce: freshNonce() })
        });
    } catch (offline) {
        process.stdout.write('\nSem rede: a conferência contra o deployment foi pulada.\n');
        process.stdout.write(passed + ' testes locais passaram.\n');
        return;
    }

    if (!response.ok) {
        process.stdout.write('\nO servidor respondeu ' + response.status +
            '; a conferência foi pulada.\n');
        process.stdout.write(passed + ' testes locais passaram.\n');
        return;
    }

    var envelope = await response.json();

    process.stdout.write('Conferência contra o deployment\n');
    check('o deployment publica uma chave ECDSA',
        Boolean(envelope.publicKeyEcdsa));
    check('a chave do app é a mesma do servidor em produção',
        withoutPadding(envelope.publicKeyEcdsa) === withoutPadding(key));

    /*
      A prova que importa: a chave do app valida uma assinatura que o servidor
      acabou de produzir. Comparar strings pega a chave errada; isto pega
      também um par corrompido na cópia.
    */
    var imported = await nodeCrypto.webcrypto.subtle.importKey(
        'spki', Buffer.from(key, 'base64'),
        { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']
    );
    var verified = await nodeCrypto.webcrypto.subtle.verify(
        { name: 'ECDSA', hash: 'SHA-256' }, imported,
        Buffer.from(envelope.signatureEcdsa, 'base64'),
        new TextEncoder().encode(envelope.payload)
    );
    check('a chave do app verifica uma assinatura real do servidor', verified);

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
