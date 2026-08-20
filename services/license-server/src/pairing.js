/**
 * Handing a television something that is painful to type on a remote control.
 *
 * A TMDb key is 32 characters, or 239 if it is the v4 token most people copy. Entering either on a
 * Samsung remote means driving a cursor around an on-screen keyboard once per character. People
 * give up, and the app that needs the key to show artwork, cast and synopsis stays bare — which
 * reads as the app being poor rather than the key being missing.
 *
 * ## Why a server, when both devices are on the same sofa
 *
 * The Tizen Web Runtime gives JavaScript no listening socket, so nothing can connect *to* the
 * television. It can only make outbound HTTPS requests. So the television posts a code, the phone
 * posts a payload against that code, and the television collects it. This Worker is a letterbox.
 *
 * ## What that forces
 *
 * The payload is the user's own credential, so it is encrypted with a key derived from the pairing
 * code itself. The code never reaches storage — only its hash does. Somebody holding this database
 * holds ciphertext and a hash, and neither yields the payload without the six digits that are on
 * the television screen and nowhere else.
 *
 * A row lives five minutes, is delivered exactly once, and is deleted on collection. Six digits is
 * a million values, which is guessable at volume; the brake is the attempt counter, not the length.
 *
 * Nothing here identifies a person. No account, no device id, no address.
 */

/** Long enough to walk to the phone, short enough that a stolen code is usually already dead. */
const TTL_SECONDS = 5 * 60;

/** Wrong guesses a code tolerates before it is refused for good. */
const MAX_ATTEMPTS = 5;

/** What a phone may send. A television waiting for a key must not be handed a title to play. */
const KINDS = new Set(['tmdb_key', 'critics_key', 'open_title']);

/**
 * The ceiling on a payload.
 *
 * A TMDb v4 token is 239 characters and a title line is shorter still. This is generous enough for
 * both and small enough that the table cannot be used as free storage.
 */
const MAX_PAYLOAD_LENGTH = 1024;

const CODE_PATTERN = /^[0-9]{6}$/;

/**
 * A six-digit code, drawn from the platform's CSPRNG.
 *
 * Rejection sampling rather than a modulo: taking `% 1000000` from a 32-bit draw makes the low
 * codes fractionally likelier, and a biased code space is a smaller code space.
 */
function generateCode() {
  const limit = 1000000;
  const ceiling = Math.floor(0xffffffff / limit) * limit;
  const buffer = new Uint32Array(1);
  let draw;
  do {
    crypto.getRandomValues(buffer);
    draw = buffer[0];
  } while (draw >= ceiling);
  return String(draw % limit).padStart(6, '0');
}

function encodeBase64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function decodeBase64(value) {
  try {
    return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

/**
 * The lookup key for a code: a hash, never the code.
 *
 * The code is also the encryption key's material, so storing it beside the ciphertext would make
 * the encryption ornamental.
 */
async function codeHash(code) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`pairing:${code}`));
  return encodeBase64(new Uint8Array(digest));
}

/**
 * An AES-GCM key derived from the pairing code.
 *
 * PBKDF2 with a fixed salt and a low iteration count, which needs saying plainly: this is not
 * protecting a password. The secret has five minutes of life and five guesses, and the work factor
 * that matters is the attempt counter in the database, not the cost of one derivation. A high
 * count here would only slow the television down.
 */
async function derivePayloadKey(code) {
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(code),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: new TextEncoder().encode('iptvburo-pairing-v1'),
      iterations: 100000,
      hash: 'SHA-256',
    },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

async function encryptPayload(code, plaintext) {
  const key = await derivePayloadKey(code);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: nonce },
      key,
      new TextEncoder().encode(plaintext),
    ),
  );
  return { payload: encodeBase64(ciphertext), nonce: encodeBase64(nonce) };
}

async function decryptPayload(code, payloadBase64, nonceBase64) {
  const ciphertext = decodeBase64(payloadBase64);
  const nonce = decodeBase64(nonceBase64);
  if (!ciphertext || !nonce) return null;
  try {
    const key = await derivePayloadKey(code);
    const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce }, key, ciphertext);
    return new TextDecoder().decode(plaintext);
  } catch {
    // A failed tag means the code was wrong or the row was tampered with. Both are "no".
    return null;
  }
}

function nowIso() {
  return new Date().toISOString();
}

function expiryIso() {
  return new Date(Date.now() + TTL_SECONDS * 1000).toISOString();
}

/**
 * Removes rows past their expiry.
 *
 * Run on every pairing request rather than by a cron: the table is only ever a handful of rows, and
 * a scheduled job would be another moving part to keep alive for a job this small.
 */
async function sweepExpired(env) {
  await env.DB.prepare('DELETE FROM pairing_requests WHERE expires_at <= ?1').bind(nowIso()).run();
}

/**
 * The television asks for a code to display.
 *
 * Retries on collision because the primary key is the hash: two televisions drawing the same six
 * digits in the same five minutes is unlikely but not impossible, and the loser would otherwise
 * receive a code that already belongs to somebody else's pending payload.
 */
export async function handlePairingStart(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'malformed_body' }, 400);
  }

  const kind = String(body?.kind ?? '');
  if (!KINDS.has(kind)) return json({ error: 'unknown_kind' }, 400);

  await sweepExpired(env);

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const code = generateCode();
    const hash = await codeHash(code);
    const result = await env.DB
      .prepare(
        `INSERT OR IGNORE INTO pairing_requests (code, kind, attempts, created_at, expires_at)
         VALUES (?1, ?2, 0, ?3, ?4)`,
      )
      .bind(hash, kind, nowIso(), expiryIso())
      .run();
    if (result?.meta?.changes > 0) {
      return json({ code, kind, expiresInSeconds: TTL_SECONDS });
    }
  }

  return json({ error: 'code_unavailable' }, 503);
}

/**
 * The phone sends the payload for a code.
 *
 * The row is looked up by hash, so a wrong code simply finds nothing — and finding nothing is
 * reported the same way as an exhausted one, because telling the difference would say whether a
 * guessed code exists.
 */
export async function handlePairingSubmit(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'malformed_body' }, 400);
  }

  const code = String(body?.code ?? '').trim();
  const payload = String(body?.payload ?? '');
  if (!CODE_PATTERN.test(code)) return json({ error: 'invalid_code' }, 400);
  if (!payload || payload.length > MAX_PAYLOAD_LENGTH) return json({ error: 'invalid_payload' }, 400);

  await sweepExpired(env);

  const hash = await codeHash(code);
  const row = await env.DB
    .prepare(
      `SELECT kind, attempts, payload FROM pairing_requests
       WHERE code = ?1 AND expires_at > ?2 AND claimed_at IS NULL`,
    )
    .bind(hash, nowIso())
    .first();

  if (!row) return json({ error: 'unknown_code' }, 404);
  if (row.attempts >= MAX_ATTEMPTS) return json({ error: 'too_many_attempts' }, 429);
  // One payload per code. A second sender must not overwrite what the first left waiting.
  if (row.payload) return json({ error: 'already_sent' }, 409);

  const kind = String(body?.kind ?? row.kind);
  if (kind !== row.kind) {
    // The television asked for one thing and is being sent another. Counted as an attempt: this is
    // what probing a code you do not own looks like.
    await env.DB
      .prepare('UPDATE pairing_requests SET attempts = attempts + 1 WHERE code = ?1')
      .bind(hash)
      .run();
    return json({ error: 'kind_mismatch' }, 409);
  }

  const encrypted = await encryptPayload(code, payload);
  const updated = await env.DB
    .prepare(
      `UPDATE pairing_requests SET payload = ?2, payload_nonce = ?3
       WHERE code = ?1 AND payload IS NULL AND claimed_at IS NULL`,
    )
    .bind(hash, encrypted.payload, encrypted.nonce)
    .run();

  if (!updated?.meta?.changes) return json({ error: 'already_sent' }, 409);
  return json({ ok: true, kind: row.kind });
}

/**
 * The television collects, once.
 *
 * Deleted on the way out rather than marked and swept later: a payload that survives its delivery
 * is a payload that can be delivered twice.
 *
 * Polling with nothing waiting is the normal case, not an error — the television asks every couple
 * of seconds while the code is on screen — so an empty answer is 200 with `pending`.
 */
export async function handlePairingClaim(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'malformed_body' }, 400);
  }

  const code = String(body?.code ?? '').trim();
  if (!CODE_PATTERN.test(code)) return json({ error: 'invalid_code' }, 400);

  await sweepExpired(env);

  const hash = await codeHash(code);
  const row = await env.DB
    .prepare(
      `SELECT kind, payload, payload_nonce FROM pairing_requests
       WHERE code = ?1 AND expires_at > ?2 AND claimed_at IS NULL`,
    )
    .bind(hash, nowIso())
    .first();

  if (!row) return json({ error: 'unknown_code' }, 404);
  if (!row.payload) return json({ status: 'pending' });

  // Claim before decrypting, and only if still unclaimed: two televisions polling the same code
  // must not both walk away with the payload.
  const claimed = await env.DB
    .prepare(
      `UPDATE pairing_requests SET claimed_at = ?2
       WHERE code = ?1 AND claimed_at IS NULL AND payload IS NOT NULL`,
    )
    .bind(hash, nowIso())
    .run();
  if (!claimed?.meta?.changes) return json({ status: 'pending' });

  const plaintext = await decryptPayload(code, row.payload, row.payload_nonce);
  await env.DB.prepare('DELETE FROM pairing_requests WHERE code = ?1').bind(hash).run();

  if (plaintext === null) return json({ error: 'undecipherable' }, 500);
  return json({ status: 'ready', kind: row.kind, payload: plaintext });
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
  });
}

export const PAIRING_INTERNALS = Object.freeze({
  TTL_SECONDS,
  MAX_ATTEMPTS,
  MAX_PAYLOAD_LENGTH,
  KINDS,
  generateCode,
  codeHash,
  encryptPayload,
  decryptPayload,
});
