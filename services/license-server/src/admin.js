/**
 * The admin panel: searching devices, granting access by hand, issuing keys.
 *
 * This is where the cases that are not a card payment get handled — someone who paid cash, a friend
 * who asked to try it, a device that needs a few more days while a support problem is sorted out.
 *
 * ## Access
 *
 * One token, sent as a bearer header or typed into the login box, checked in constant time. There
 * is no account system because there is one administrator, and a login form with users and password
 * resets would be more code to get wrong than the thing it protects.
 *
 * The token grants the ability to give the product away, so it belongs in a password manager and
 * nowhere else. It is a Worker secret, never in this repository.
 *
 * ## Every action is recorded
 *
 * Grants and revocations write to the events table with a note. Six months from now, "why is this
 * device active?" has an answer, and that is exactly when the question gets asked.
 */

/**
 * Checks the admin token without leaking how much of a guess was right.
 *
 * A string comparison that stops at the first difference reveals, through timing, the length of a
 * correct prefix — enough to recover a token one character at a time. Across a network the signal
 * is small but it is real, and this token gives away the product.
 */
export function isAdmin(request, env) {
  const expected = env.ADMIN_TOKEN;
  if (!expected) return false;

  const header = request.headers.get('authorization') ?? '';
  const provided = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (provided.length !== expected.length) return false;

  let difference = 0;
  for (let index = 0; index < provided.length; index += 1) {
    difference |= provided.charCodeAt(index) ^ expected.charCodeAt(index);
  }
  return difference === 0;
}

/**
 * Every device in a given state, newest first.
 *
 * The summary counts were the only way in, and a count is not a list: seeing "3 em teste" with no
 * way to find out which three made the panel read as broken. This is what the numbers link to.
 */
export async function devicesByStatus(status, env) {
  const wanted = String(status ?? '').toUpperCase();
  if (!['TRIAL', 'ACTIVE', 'EXPIRED', 'REVOKED', 'REFUNDED'].includes(wanted)) return [];

  const { results } = await env.DB.prepare(
    'SELECT * FROM devices WHERE status = ? ORDER BY updated_at DESC LIMIT 100',
  )
    .bind(wanted)
    .all();
  return results ?? [];
}

/** Everyone who has paid, for the third summary figure. */
export async function paidDevices(env) {
  const { results } = await env.DB.prepare(
    'SELECT * FROM devices WHERE stripe_session_id IS NOT NULL ORDER BY purchased_at DESC LIMIT 100',
  ).all();
  return results ?? [];
}

/** Finds devices by identifier, MAC or note. Support searches by whatever the customer read out. */
export async function searchDevices(query, env) {
  const term = `%${String(query ?? '').trim().toUpperCase()}%`;
  const { results } = await env.DB.prepare(
    `SELECT * FROM devices
     WHERE UPPER(device_id) LIKE ?
        OR UPPER(COALESCE(mac_address, '')) LIKE ?
        OR UPPER(COALESCE(note, '')) LIKE ?
     ORDER BY updated_at DESC
     LIMIT 50`,
  )
    .bind(term, term, term)
    .all();
  return results ?? [];
}

/**
 * Grants a device access by hand.
 *
 * [days] rather than an end date: "give them a month" is how the decision is actually made, and
 * computing the date here removes a chance to typo one.
 *
 * The note is required by convention rather than by the schema — a grant without a reason is one
 * nobody can explain later — and the caller's interface asks for it.
 */
export async function grantDevice(deviceId, days, note, env) {
  const now = new Date();
  const safeDays = Number(days);
  if (!Number.isInteger(safeDays) || safeDays < 1 || safeDays > 3650) {
    throw new RangeError('grant days must be between 1 and 3650');
  }

  // "Give more time" must never shorten time the customer already owns. Extend from a future
  // expiry when one exists; otherwise start from now. The lookup is only an admin convenience —
  // payment/redeem paths use their own atomic rules.
  const existing = await env.DB.prepare('SELECT expires_at FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  const existingExpiry = Date.parse(existing?.expires_at ?? '');
  const startsAt = Number.isFinite(existingExpiry) && existingExpiry > now.getTime()
    ? existingExpiry
    : now.getTime();
  const expiresAt = new Date(startsAt + safeDays * 86400000);

  await env.DB.prepare(
    `INSERT INTO devices (device_id, status, first_seen_at, trial_ends_at, purchased_at, expires_at, note, updated_at)
     VALUES (?, 'ACTIVE', ?, ?, ?, ?, ?, ?)
     ON CONFLICT(device_id) DO UPDATE SET
       status = 'ACTIVE',
       expires_at = excluded.expires_at,
       note = excluded.note,
       updated_at = excluded.updated_at`,
  )
    .bind(
      deviceId,
      now.toISOString(),
      now.toISOString(),
      now.toISOString(),
      expiresAt.toISOString(),
      note || 'manual grant',
      now.toISOString(),
    )
    .run();

  await env.DB.prepare(
    'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
  )
    .bind(deviceId, 'granted', `${safeDays}d: ${note || ''}`.slice(0, 200), now.toISOString())
    .run();
}

/**
 * Withdraws access.
 *
 * Used for a refund handled outside Stripe, or a licence issued in error. The row is not deleted —
 * a device that vanishes from the table simply registers again and gets a fresh trial, which is the
 * opposite of what revoking means.
 */
export async function revokeDevice(deviceId, note, env) {
  const now = new Date().toISOString();
  await env.DB.prepare(
    "UPDATE devices SET status = 'REVOKED', note = ?, updated_at = ? WHERE device_id = ?",
  )
    .bind(note || 'revoked', now, deviceId)
    .run();
  await env.DB.prepare(
    'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
  )
    .bind(deviceId, 'revoked', String(note ?? '').slice(0, 200), now)
    .run();
}

/**
 * Creates keys to hand out.
 *
 * Generated here rather than chosen, because a memorable key is a guessable key: 40 bits of
 * randomness from an alphabet without the characters people misread, in the `XXXX-XXXX` shape that
 * survives being written on paper and read back.
 */
export async function createKeys(count, days, note, env) {
  const now = new Date().toISOString();
  const created = [];

  for (let index = 0; index < Math.min(count, 100); index += 1) {
    const code = randomKey();
    await env.DB.prepare(
      'INSERT INTO redemption_keys (key_code, grant_days, created_at, note) VALUES (?, ?, ?, ?)',
    )
      .bind(code, days, now, note || null)
      .run();
    created.push(code);
  }

  return created;
}

/**
 * Cancels a key that has not been used.
 *
 * A code sent to the wrong person, or generated by mistake, is otherwise live for ever with no way
 * to take it back. Cancelling is a deletion rather than a flag: an unused key holds no history worth
 * keeping, and a deleted row cannot be redeemed by any code path, present or future.
 *
 * A **redeemed** key is deliberately left alone. Deleting it would break the link between a device
 * and how it was activated, and would let the same code be issued again; the way to take back what a
 * redeemed key granted is to revoke the device it activated.
 *
 * @returns true when a key was cancelled, false when it did not exist or had already been used
 */
export async function cancelKey(keyCode, env) {
  const code = String(keyCode ?? '').trim().toUpperCase();
  if (!code) return false;

  const result = await env.DB.prepare(
    'DELETE FROM redemption_keys WHERE key_code = ? AND redeemed_by IS NULL',
  )
    .bind(code)
    .run();

  const cancelled = Number(result?.meta?.changes ?? 0) > 0;
  if (cancelled) {
    // Recorded against the code rather than a device, because no device was ever involved. Without
    // this, a key that vanishes from the list has no explanation six months later.
    await env.DB.prepare(
      'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
    )
      .bind(null, 'key_cancelled', code, new Date().toISOString())
      .run();
  }
  return cancelled;
}

export async function listKeys(env) {
  const { results } = await env.DB.prepare(
    'SELECT * FROM redemption_keys ORDER BY created_at DESC LIMIT 100',
  ).all();
  return results ?? [];
}

/** The history of one device, for the questions that arrive months later. */
export async function deviceHistory(deviceId, env) {
  const { results } = await env.DB.prepare(
    'SELECT * FROM events WHERE device_id = ? ORDER BY created_at DESC LIMIT 100',
  )
    .bind(deviceId)
    .all();
  return results ?? [];
}

/** How the business is doing, in the three numbers worth seeing on arrival. */
export async function summary(env) {
  const active = await env.DB.prepare("SELECT COUNT(*) AS n FROM devices WHERE status = 'ACTIVE'").first();
  const trial = await env.DB.prepare("SELECT COUNT(*) AS n FROM devices WHERE status = 'TRIAL'").first();
  const paid = await env.DB.prepare(
    'SELECT COUNT(*) AS n FROM devices WHERE stripe_session_id IS NOT NULL',
  ).first();
  return { active: active?.n ?? 0, trial: trial?.n ?? 0, paid: paid?.n ?? 0 };
}

/**
 * No 0/O and no 1/I: these get written down and read back, and those are the pairs people confuse.
 */
function randomKey() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  const body = Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join('');
  return `${body.slice(0, 4)}-${body.slice(4, 8)}`;
}
