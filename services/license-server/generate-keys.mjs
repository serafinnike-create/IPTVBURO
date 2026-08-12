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

console.log('');
console.log('=== PRIVATE (Worker secret: SIGNING_KEY) — never commit this ===');
console.log(privateKey);
console.log('');
console.log('=== PUBLIC (client: LicenseEndpoints.SERVER_PUBLIC_KEY) ===');
console.log(publicKey);
console.log('');
