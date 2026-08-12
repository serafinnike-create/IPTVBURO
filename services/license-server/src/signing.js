/**
 * Signing licence documents.
 *
 * The client verifies every answer against a public key compiled into its binary, so a document
 * this server did not sign is worthless — which is what makes pointing the app at a different
 * server pointless.
 *
 * Ed25519, because the client uses the JDK's implementation and the signatures are 64 bytes. Web
 * Crypto in Workers supports it natively; nothing is bundled.
 */

/**
 * Signs a licence document.
 *
 * The exact bytes signed are the exact bytes sent. This matters more than it looks: if the server
 * signed a canonical form and sent a re-serialised one, every signature would fail on the client
 * for reasons nobody could see. The payload string is produced once and used for both.
 *
 * @param {object} document the licence, as a plain object
 * @param {string} privateKeyPkcs8Base64 the signing key, from the Worker's secrets
 * @returns {Promise<{payload: string, signature: string}>}
 */
export async function signLicense(document, privateKeyPkcs8Base64) {
  // Keys in a fixed order, so the same document always produces the same bytes. Not required for
  // correctness — the client verifies whatever arrives — but it makes two identical licences
  // compare equal in logs and tests, which is worth the three lines.
  const payload = JSON.stringify(document, Object.keys(document).sort());

  const key = await crypto.subtle.importKey(
    'pkcs8',
    base64ToBytes(privateKeyPkcs8Base64),
    { name: 'Ed25519' },
    false,
    ['sign'],
  );

  const signature = await crypto.subtle.sign(
    { name: 'Ed25519' },
    key,
    new TextEncoder().encode(payload),
  );

  return { payload, signature: bytesToBase64(new Uint8Array(signature)) };
}

/**
 * Generates a key pair, printed once and then kept.
 *
 * Run this locally, put the private half in the Worker's secrets and the public half in the
 * client's `LicenseEndpoints.SERVER_PUBLIC_KEY`. The private key is never in this repository and
 * never leaves the Worker.
 *
 * Rotating it invalidates every licence already issued, because installed clients verify against
 * the key they were built with. That is a release, not a maintenance task.
 */
export async function generateKeyPair() {
  const pair = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']);
  const privateKey = await crypto.subtle.exportKey('pkcs8', pair.privateKey);
  const publicKey = await crypto.subtle.exportKey('spki', pair.publicKey);
  return {
    privateKeyPkcs8Base64: bytesToBase64(new Uint8Array(privateKey)),
    publicKeySpkiBase64: bytesToBase64(new Uint8Array(publicKey)),
  };
}

function base64ToBytes(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function bytesToBase64(bytes) {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}
