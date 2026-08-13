/**
 * The ECDSA signature is an addition, and must never be able to take the service down.
 *
 * Smart TVs cannot verify Ed25519, so licences carry a second signature for them. Android and
 * Windows verify the Ed25519 one and ignore this field entirely — which is exactly why a problem
 * with it must not reach them.
 *
 * It did. `SIGNING_KEY_ECDSA` was set in production to a value that could not be imported, the
 * exception escaped `signLicenseEcdsa`, and `/v1/validate` answered 500 to every device. Each one
 * fell back to "offline verification" and would have been locked out when its grace expired: a
 * total outage caused by an optional extra for a platform that was not even asking.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { generateEcdsaKeyPair, generateKeyPair, signLicense, signLicenseEcdsa } from '../src/signing.js';

const PAYLOAD = '{"deviceId":"TEST-TEST-TEST","state":"TRIAL"}';

test('a valid key still produces a signature', async () => {
  const { privateKeyPkcs8Base64 } = await generateEcdsaKeyPair();

  const signature = await signLicenseEcdsa(PAYLOAD, privateKeyPkcs8Base64);

  assert.ok(signature, 'a usable key must sign');
  assert.equal(typeof signature, 'string');
});

test('an absent key is not an error', async () => {
  assert.equal(await signLicenseEcdsa(PAYLOAD, null), null);
  assert.equal(await signLicenseEcdsa(PAYLOAD, undefined), null);
  assert.equal(await signLicenseEcdsa(PAYLOAD, ''), null);
});

/**
 * The case that caused the outage.
 *
 * Each of these is something a person can plausibly paste into `wrangler secret put`: the public
 * half instead of the private one, a PEM complete with its header lines, an Ed25519 key where a
 * P-256 one was wanted, or a truncated copy. None of them may throw.
 */
test('a broken key degrades to no signature instead of throwing', async () => {
  const ecdsa = await generateEcdsaKeyPair();
  const ed25519 = await generateKeyPair();
  const broken = [
    'not-base64-at-all!!',
    'AAAA',
    ecdsa.publicKeySpkiBase64,
    ed25519.privateKeyPkcs8Base64,
    ecdsa.privateKeyPkcs8Base64.slice(0, 40),
    `-----BEGIN PRIVATE KEY-----\n${ecdsa.privateKeyPkcs8Base64}\n-----END PRIVATE KEY-----`,
  ];

  for (const key of broken) {
    const signature = await signLicenseEcdsa(PAYLOAD, key);
    assert.equal(signature, null, `an unusable key must yield null: ${String(key).slice(0, 16)}…`);
  }
});

/**
 * The Ed25519 signature is what actually grants access, so it must be unaffected.
 *
 * If a broken ECDSA key could stop this one being produced, the degradation above would be no
 * improvement on the outage it replaces.
 */
test('the Ed25519 signature is produced regardless of the ECDSA key', async () => {
  const { privateKeyPkcs8Base64 } = await generateKeyPair();

  const signed = await signLicense({ deviceId: 'TEST-TEST-TEST', state: 'TRIAL' }, privateKeyPkcs8Base64);
  const ecdsa = await signLicenseEcdsa(signed.payload, 'not-a-key');

  assert.ok(signed.signature, 'the licence must still be signed');
  assert.equal(ecdsa, null);
});
