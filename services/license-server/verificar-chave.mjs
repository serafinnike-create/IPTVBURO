/**
 * Checks that the public key compiled into the app matches the private key the Worker holds.
 *
 * Run against the deployed server:
 *
 *     node verificar-chave.mjs https://iptvburo.iptvburo.workers.dev
 *
 * ## Why this exists
 *
 * These two values are set in different places at different times — the private half by
 * `wrangler secret put`, the public half by editing a Kotlin constant. Nothing connects them, so a
 * regenerated key pair silently breaks every client: the server keeps signing, the app keeps
 * verifying, and every signature fails.
 *
 * The failure is invisible from either side alone. The server's logs show successful requests. The
 * app blocks every customer with "could not verify your licence", which reads exactly like a network
 * problem. Only comparing the two reveals it, which is what this does.
 *
 * It needs no secrets: it asks the server to sign something and checks the result against the key
 * the app carries.
 */

import { readFileSync } from 'node:fs';
import { webcrypto as crypto } from 'node:crypto';

const origin = process.argv[2];
if (!origin) {
  console.error('usage: node verificar-chave.mjs https://<your-worker-domain>');
  process.exit(2);
}

/** The key the shipped app verifies against. Read from source so the two cannot drift apart. */
function publicKeyFromClient() {
  const source = readFileSync(
    new URL(
      '../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/LicenseEndpoints.kt',
      import.meta.url,
    ),
    'utf8',
  );
  const match = source.match(/SERVER_PUBLIC_KEY\s*=\s*"([^"]+)"/);
  if (!match) throw new Error('SERVER_PUBLIC_KEY not found in LicenseEndpoints.kt');
  return match[1];
}

/**
 * Asks the server for any signed answer.
 *
 * The dedicated check endpoint signs a fixed-purpose challenge. It does not create a trial, touch
 * the device ledger or produce a document that an application could mistake for a licence.
 */
async function signedAnswerFrom(base) {
  const nonce = Buffer.from(crypto.getRandomValues(new Uint8Array(16))).toString('base64url');
  const response = await fetch(`${base}/v1/signing-key-check`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ nonce }),
  });

  const text = await response.text();
  if (!response.ok) {
    return { ok: false, status: response.status, body: text.slice(0, 200) };
  }

  const body = JSON.parse(text);
  if (!body.payload || !body.signature) {
    return { ok: false, status: response.status, body: 'answer carried no signature' };
  }
  return { ok: true, payload: body.payload, signature: body.signature };
}

async function verify(publicKeyBase64, payload, signatureBase64) {
  const key = await crypto.subtle.importKey(
    'spki',
    Buffer.from(publicKeyBase64, 'base64'),
    { name: 'Ed25519' },
    false,
    ['verify'],
  );

  return crypto.subtle.verify(
    'Ed25519',
    key,
    Buffer.from(signatureBase64, 'base64'),
    new TextEncoder().encode(payload),
  );
}

const clientKey = publicKeyFromClient();
console.log(`chave no app : ${clientKey.slice(0, 24)}...`);
console.log(`servidor     : ${origin}`);
console.log('');

const answer = await signedAnswerFrom(origin.replace(/\/$/, ''));

if (!answer.ok) {
  console.error(`O servidor respondeu ${answer.status} e nao assinou nada.`);
  console.error(answer.body);
  process.exit(1);
}

const valid = await verify(clientKey, answer.payload, answer.signature);

if (valid) {
  console.log('OK — a chave do app verifica as licencas deste servidor.');
  process.exit(0);
}

console.error('FALHOU — a chave do app NAO verifica as licencas deste servidor.');
console.error('');
console.error('Todos os clientes ficariam bloqueados com "nao foi possivel verificar a licenca".');
console.error('Corrija assim: gere um par novo, ponha a metade privada no Worker com');
console.error('`wrangler secret put SIGNING_KEY`, cole a publica em LicenseEndpoints.kt, e');
console.error('reconstrua o app. As duas metades tem de vir da mesma execucao de generate-keys.');
process.exit(1);
