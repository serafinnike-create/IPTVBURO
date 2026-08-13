/**
 * Prints the ECDSA public key of the deployed Worker.
 *
 *   node public-key-ecdsa.mjs
 *
 * The key comes from the deployment itself, so what it prints is provably the key in production
 * rather than whatever happens to be in a local file. That distinction matters: a public key from
 * a different pair verifies nothing, and the failure would only appear on a TV, at activation.
 *
 * The private key is never read or typed. The output is public — it ships compiled into every
 * installed client — so it is safe to paste anywhere.
 */

import { webcrypto } from 'node:crypto';

const BASE = process.argv[2] ?? 'https://iptvburo.iptvburo.workers.dev';

// The server wants exactly 22 base64url characters — 16 random bytes with the padding stripped.
// Standard base64 is rejected as a bad nonce, which reads as a deployment problem rather than a
// malformed request, so it is worth getting right here.
const nonce = Buffer.from(webcrypto.getRandomValues(new Uint8Array(16)))
  .toString('base64')
  .replace(/\+/g, '-')
  .replace(/\//g, '_')
  .replace(/=+$/, '');

const response = await fetch(`${BASE}/v1/signing-key-check`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ nonce }),
});

if (!response.ok) {
  console.error(`The Worker answered ${response.status}. Is ${BASE} the right deployment?`);
  process.exit(1);
}

const envelope = await response.json();

if (!envelope.publicKeyEcdsa) {
  console.error('');
  if (envelope.ecdsaUnavailable) {
    console.error(envelope.ecdsaUnavailable);
    console.error('');
    console.error('Fix it with:');
    console.error('');
    console.error('  node generate-keys.mjs                     # copy the PRIVATE ECDSA block');
    console.error('  npx wrangler secret put SIGNING_KEY_ECDSA');
    console.error('');
  } else {
    console.error('This deployment predates the change that publishes the ECDSA key. Ship the');
    console.error('current code and run this again:');
    console.error('');
    console.error('  npx wrangler deploy');
    console.error('');
  }
  process.exit(1);
}

// Proof that the printed key belongs to the signature the deployment produces. Without this the
// script could happily print a key that verifies nothing.
const key = await webcrypto.subtle.importKey(
  'spki',
  Buffer.from(envelope.publicKeyEcdsa, 'base64'),
  { name: 'ECDSA', namedCurve: 'P-256' },
  false,
  ['verify'],
);
const verified = await webcrypto.subtle.verify(
  { name: 'ECDSA', hash: 'SHA-256' },
  key,
  Buffer.from(envelope.signatureEcdsa, 'base64'),
  new TextEncoder().encode(envelope.payload),
);

if (!verified) {
  console.error('The deployment returned a key that does not verify its own signature. Do not');
  console.error('use it — redeploy and try again.');
  process.exit(1);
}

console.error('');
console.error('Verified against a live signature from the deployment.');
console.error('Paste the line below into apps/samsung-tizen/js/license.js, as');
console.error('SERVER_PUBLIC_KEY_ECDSA:');
console.error('');
console.log(envelope.publicKeyEcdsa);
console.error('');
