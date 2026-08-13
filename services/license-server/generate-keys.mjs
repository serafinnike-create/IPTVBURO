/**
 * Generates the licence signing key pair. Run once, keep the output.
 *
 *   node generate-keys.mjs
 *
 * The private half goes into the Worker's secrets:
 *
 *   npx wrangler secret put SIGNING_KEY
 *
 * The public half goes into the client, in
 * `apps/desktop/.../license/LicenseEndpoints.kt`, as `SERVER_PUBLIC_KEY`.
 *
 * ## Treat the private key as the product
 *
 * Anyone holding it can issue licences that every installed client accepts. It belongs in the
 * Worker's secrets and in whatever you use for passwords — nowhere else, and never in this
 * repository, which is public.
 *
 * Rotating it invalidates every licence already issued, because installed clients verify against
 * the key they were built with. That is a release with a migration, not a maintenance task, so
 * generate this once and keep it safe.
 */

import { webcrypto } from 'node:crypto';

const pair = await webcrypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);

const privateKey = Buffer.from(
  await webcrypto.subtle.exportKey('pkcs8', pair.privateKey),
).toString('base64');

const publicKey = Buffer.from(
  await webcrypto.subtle.exportKey('spki', pair.publicKey),
).toString('base64');

/*
 * A second pair, for clients that cannot verify Ed25519.
 *
 * Smart TVs are the reason. Ed25519 only reached Chromium in version 137, and Samsung TVs in use
 * today run engines from Chrome 47 to M130 — none of which can verify the signature above. The
 * server signs the same payload twice so the TV verifies real cryptography instead of trusting
 * whatever the network hands it.
 */
const ecdsaPair = await webcrypto.subtle.generateKey(
  { name: 'ECDSA', namedCurve: 'P-256' },
  true,
  ['sign', 'verify'],
);

const ecdsaPrivateKey = Buffer.from(
  await webcrypto.subtle.exportKey('pkcs8', ecdsaPair.privateKey),
).toString('base64');

const ecdsaPublicKey = Buffer.from(
  await webcrypto.subtle.exportKey('spki', ecdsaPair.publicKey),
).toString('base64');

console.log('');
console.log('=== PRIVATE (Worker secret: SIGNING_KEY) — never commit this ===');
console.log(privateKey);
console.log('');
console.log('=== PUBLIC (client: LicenseEndpoints.SERVER_PUBLIC_KEY) ===');
console.log(publicKey);
console.log('');
console.log(
  `=== PRIVATE ECDSA (Worker secret: SIGNING_KEY_ECDSA) — ${ecdsaPrivateKey.length} chars, never commit this ===`,
);
console.log(ecdsaPrivateKey);
console.log('');
console.log('=== PUBLIC ECDSA (TV client: BuroLicense SERVER_PUBLIC_KEY_ECDSA) ===');
console.log(ecdsaPublicKey);
console.log('');
/*
 * The keys are also written straight to disk, in UTF-8 with no BOM.
 *
 * Two things went wrong repeatedly when this was left to the operator. Pasting a 184-character
 * key into the interactive `wrangler secret put` prompt truncates silently on some terminals, and
 * redirecting this output with PowerShell's `>` writes UTF-16 — which makes every key read back
 * at twice its length, with a null byte between each character. Neither is visible in the
 * terminal, and the server accepts whatever arrives, so the failure only appears much later as a
 * licence that never verifies.
 *
 * Writing the files here removes both steps from the operator's hands.
 */
const { writeFileSync } = await import('node:fs');
writeFileSync('ecdsa-private.txt', ecdsaPrivateKey, { encoding: 'utf8' });
writeFileSync('ed25519-private.txt', privateKey, { encoding: 'utf8' });

console.log('--- Setting SIGNING_KEY_ECDSA ---');
console.log('');
console.log(`The private ECDSA key is ${ecdsaPrivateKey.length} characters and starts MIGHAgEAMBMG.`);
console.log('It is in ecdsa-private.txt: UTF-8, no BOM, no trailing newline.');
console.log('');
console.log('PowerShell has no working stdin redirect for this — `<` is a reserved operator and');
console.log('`Get-Content | ` appends a CRLF — so route it through cmd:');
console.log('');
console.log('  cmd /c "npx wrangler secret put SIGNING_KEY_ECDSA < ecdsa-private.txt"');
console.log('  npx wrangler deploy');
console.log('  node public-key-ecdsa.mjs');
console.log('  Remove-Item ecdsa-private.txt, ed25519-private.txt -Force');
console.log('');
console.log('Never paste the key into the interactive prompt, and never redirect this output with');
console.log('PowerShell `>`: the prompt truncates silently and `>` writes UTF-16, which doubles');
console.log('every key. Both were mistaken for a deployment problem.');
console.log('');
