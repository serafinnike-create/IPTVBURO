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

// Only support-safe columns leave D1. SELECT * previously returned the pinned public key, machine
// anchor and purchase-token hash to the browser even though the page did not render them. An admin
// token authorises support work; it is not a reason to widen the consequence of a browser leak.
const ADMIN_DEVICE_COLUMNS = `
  devices.device_id, devices.status, devices.first_seen_at, devices.trial_ends_at,
  devices.purchased_at, devices.expires_at, devices.note, devices.updated_at,
  devices.device_type, devices.platform, devices.manufacturer, devices.model,
  devices.os_version, devices.app_version, devices.last_seen_at,
  devices.activation_country, devices.last_country, devices.country_updated_at,
  devices.display_name, devices.customer_name, devices.customer_email,
  devices.order_reference, devices.support_note,
  devices.archived_at, devices.archived_note,
  CASE
    WHEN devices.google_purchase_token_hash IS NOT NULL THEN 'GOOGLE_PLAY'
    WHEN devices.stripe_session_id IS NOT NULL THEN 'STRIPE'
    WHEN EXISTS (
      SELECT 1 FROM redemption_keys
      WHERE redemption_keys.redeemed_by = devices.device_id
    ) THEN 'ACTIVATION_KEY'
    WHEN devices.purchased_at IS NOT NULL THEN 'MANUAL'
    ELSE 'TRIAL'
  END AS source`;

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

const DEVICE_LIST_LIMIT = 100;

/**
 * How a device list is ordered.
 *
 * "recent" (default) surfaces whoever just used the app — the shape support needs when chasing a
 * fresh report. "expiring" surfaces whoever is about to lose access — the shape needed to reach a
 * customer before they churn, which recency ordering buries at random depths in the list.
 */
function deviceOrderBy(sort) {
  return sort === 'expiring'
    // Devices with no expiry/trial date sort last, not first — NULL would otherwise collate before
    // every real date and bury the list under rows there is nothing to act on.
    ? `ORDER BY COALESCE(expires_at, trial_ends_at) IS NULL,
               COALESCE(expires_at, trial_ends_at) ASC`
    : 'ORDER BY COALESCE(last_seen_at, updated_at) DESC';
}

/**
 * Every device in a given state, newest first (or soonest-to-expire, see [deviceOrderBy]).
 *
 * The summary counts were the only way in, and a count is not a list: seeing "3 em teste" with no
 * way to find out which three made the panel read as broken. This is what the numbers link to.
 *
 * Capped at [DEVICE_LIST_LIMIT] and paired with the true count so the panel can say "mostrando 100
 * de 340" instead of silently presenting a truncated list as the whole answer.
 */
export async function devicesByStatus(status, env, sort) {
  const wanted = String(status ?? '').toUpperCase();
  const orderBy = deviceOrderBy(sort);

  if (wanted === 'ARCHIVED') {
    const [{ results }, total] = await Promise.all([
      env.DB.prepare(
        `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices
         WHERE archived_at IS NOT NULL ORDER BY archived_at DESC LIMIT ${DEVICE_LIST_LIMIT}`,
      ).all(),
      env.DB.prepare('SELECT COUNT(*) AS n FROM devices WHERE archived_at IS NOT NULL').first(),
    ]);
    return { devices: results ?? [], total: Number(total?.n ?? 0) };
  }

  if (wanted === 'ALL') {
    const [{ results }, total] = await Promise.all([
      env.DB.prepare(
        `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices
         WHERE archived_at IS NULL ${orderBy} LIMIT ${DEVICE_LIST_LIMIT}`,
      ).all(),
      env.DB.prepare('SELECT COUNT(*) AS n FROM devices WHERE archived_at IS NULL').first(),
    ]);
    return { devices: results ?? [], total: Number(total?.n ?? 0) };
  }

  if (!['TRIAL', 'ACTIVE', 'EXPIRED', 'REVOKED', 'REFUNDED'].includes(wanted)) {
    return { devices: [], total: 0 };
  }

  const [{ results }, total] = await Promise.all([
    env.DB.prepare(
      `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices
       WHERE status = ? AND archived_at IS NULL ${orderBy} LIMIT ${DEVICE_LIST_LIMIT}`,
    )
      .bind(wanted)
      .all(),
    env.DB.prepare('SELECT COUNT(*) AS n FROM devices WHERE status = ? AND archived_at IS NULL')
      .bind(wanted)
      .first(),
  ]);
  return { devices: results ?? [], total: Number(total?.n ?? 0) };
}

/** Everyone who has paid, for the third summary figure. */
export async function paidDevices(env, sort) {
  const orderBy = sort === 'expiring'
    ? `ORDER BY COALESCE(expires_at, trial_ends_at) IS NULL,
               COALESCE(expires_at, trial_ends_at) ASC`
    : 'ORDER BY purchased_at DESC';
  const [{ results }, total] = await Promise.all([
    env.DB.prepare(
      `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices
       WHERE archived_at IS NULL
         AND (stripe_session_id IS NOT NULL OR google_purchase_token_hash IS NOT NULL)
       ${orderBy} LIMIT ${DEVICE_LIST_LIMIT}`,
    ).all(),
    env.DB.prepare(
      `SELECT COUNT(*) AS n FROM devices WHERE archived_at IS NULL
       AND (stripe_session_id IS NOT NULL OR google_purchase_token_hash IS NOT NULL)`,
    ).first(),
  ]);
  return { devices: results ?? [], total: Number(total?.n ?? 0) };
}

/** Finds devices by code, model, manufacturer, platform, country, legacy MAC or note. */
export async function searchDevices(query, env) {
  const term = `%${String(query ?? '').trim().toUpperCase()}%`;
  const { results } = await env.DB.prepare(
    `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices
     WHERE UPPER(device_id) LIKE ?
        OR UPPER(COALESCE(mac_address, '')) LIKE ?
        OR UPPER(COALESCE(note, '')) LIKE ?
        OR UPPER(COALESCE(manufacturer, '')) LIKE ?
        OR UPPER(COALESCE(model, '')) LIKE ?
        OR UPPER(COALESCE(platform, '')) LIKE ?
        OR UPPER(COALESCE(device_type, '')) LIKE ?
        OR UPPER(COALESCE(app_version, '')) LIKE ?
        OR UPPER(COALESCE(activation_country, '')) LIKE ?
        OR UPPER(COALESCE(last_country, '')) LIKE ?
     ORDER BY archived_at IS NOT NULL, COALESCE(last_seen_at, updated_at) DESC
     LIMIT 50`,
  )
    .bind(term, term, term, term, term, term, term, term, term, term)
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
       stripe_session_id = NULL,
       google_purchase_token_hash = NULL,
       archived_at = NULL,
       archived_note = NULL,
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
 * Removes a device from ordinary lists without deleting its identity, payment or trial history.
 *
 * Physical deletion would let the same installation return as new and receive another trial. An
 * archive is therefore also a revocation, and can be restored later without reconstructing data.
 */
export async function archiveDevice(deviceId, note, env) {
  const now = new Date().toISOString();
  const reason = String(note ?? '').trim().slice(0, 200) || 'removed from admin list';
  const result = await env.DB.prepare(
    `UPDATE devices SET
       status = 'REVOKED', archived_at = ?, archived_note = ?, note = ?, updated_at = ?
     WHERE device_id = ? AND archived_at IS NULL`,
  )
    .bind(now, reason, reason, now, deviceId)
    .run();
  if (Number(result?.meta?.changes ?? 0) === 0) return false;
  await env.DB.batch([
    // Financial dispute recovery treats a later administrative revocation as authoritative. An
    // archive must carry the same marker or a won dispute could silently reactivate a device the
    // administrator explicitly removed.
    env.DB.prepare(
      'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
    ).bind(deviceId, 'revoked', `archived: ${reason}`.slice(0, 200), now),
    env.DB.prepare(
      'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
    ).bind(deviceId, 'archived', reason, now),
  ]);
  return true;
}

/** Restores visibility only. A restored device remains blocked until explicitly granted. */
export async function restoreDevice(deviceId, env) {
  const now = new Date().toISOString();
  const result = await env.DB.prepare(
    `UPDATE devices SET archived_at = NULL, archived_note = NULL, updated_at = ?
     WHERE device_id = ? AND archived_at IS NOT NULL`,
  ).bind(now, deviceId).run();
  if (Number(result?.meta?.changes ?? 0) === 0) return false;
  await env.DB.prepare(
    'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
  ).bind(deviceId, 'restored', 'restored to admin list; entitlement remains revoked', now).run();
  return true;
}

/** One support-safe device record and its append-only event history. */
export async function deviceDetails(deviceId, env) {
  const device = await env.DB.prepare(
    `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices WHERE device_id = ?`,
  ).bind(deviceId).first();
  if (!device) return null;
  const { results } = await env.DB.prepare(
    `SELECT kind, detail, created_at FROM events
     WHERE device_id = ? ORDER BY created_at DESC, id DESC LIMIT 50`,
  ).bind(deviceId).all();
  return { device, events: results ?? [] };
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
  const active = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM devices WHERE status = 'ACTIVE' AND archived_at IS NULL",
  ).first();
  const trial = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM devices WHERE status = 'TRIAL' AND archived_at IS NULL",
  ).first();
  const paid = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM devices
     WHERE archived_at IS NULL
       AND (stripe_session_id IS NOT NULL OR google_purchase_token_hash IS NOT NULL)`,
  ).first();
  const revoked = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM devices WHERE status = 'REVOKED' AND archived_at IS NULL",
  ).first();
  const expired = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM devices WHERE status = 'EXPIRED' AND archived_at IS NULL",
  ).first();
  const archived = await env.DB.prepare(
    'SELECT COUNT(*) AS n FROM devices WHERE archived_at IS NOT NULL',
  ).first();
  return {
    active: active?.n ?? 0,
    trial: trial?.n ?? 0,
    paid: paid?.n ?? 0,
    revoked: revoked?.n ?? 0,
    expired: expired?.n ?? 0,
    archived: archived?.n ?? 0,
  };
}

/** Updates optional support labels; licence state and payment ownership are deliberately untouched. */
export async function updateDeviceSupport(deviceId, values, env) {
  const clean = {
    displayName: supportText(values?.displayName, 80),
    customerName: supportText(values?.customerName, 100),
    customerEmail: supportEmail(values?.customerEmail),
    orderReference: supportText(values?.orderReference, 100),
    supportNote: supportText(values?.supportNote, 500),
  };
  const result = await env.DB.prepare(
    `UPDATE devices SET display_name = ?, customer_name = ?, customer_email = ?,
       order_reference = ?, support_note = ?, updated_at = ? WHERE device_id = ?`,
  ).bind(
    clean.displayName, clean.customerName, clean.customerEmail,
    clean.orderReference, clean.supportNote, new Date().toISOString(), deviceId,
  ).run();
  return Number(result?.meta?.changes ?? 0) > 0;
}

export async function recordAdminAudit(actor, action, deviceId, detail, country, env) {
  await env.DB.prepare(
    `INSERT INTO admin_audit (actor, action, device_id, detail, country, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(
    supportText(actor, 80) || 'Administrador',
    supportText(action, 60) || 'unknown',
    deviceId || null,
    supportText(detail, 500),
    /^[A-Z]{2}$/.test(String(country ?? '').toUpperCase()) ? String(country).toUpperCase() : null,
    new Date().toISOString(),
  ).run();
}

export async function listAdminAudit(env) {
  const { results } = await env.DB.prepare(
    `SELECT actor, action, device_id, detail, country, created_at
     FROM admin_audit ORDER BY created_at DESC, id DESC LIMIT 200`,
  ).all();
  return results ?? [];
}

export async function financialOverview(env) {
  const { results: stripe } = await env.DB.prepare(
    `SELECT payments.device_id, payments.status, payments.amount_minor,
       payments.amount_refunded_minor, payments.currency, payments.paid_at,
       payments.created_at, payments.updated_at, devices.last_country
     FROM payments LEFT JOIN devices ON devices.device_id = payments.device_id
     ORDER BY payments.created_at DESC LIMIT 200`,
  ).all();
  const { results: googlePlay } = await env.DB.prepare(
    `SELECT google_play_purchases.device_id, google_play_purchases.status,
       google_play_purchases.purchase_completed_at, google_play_purchases.expires_at,
       google_play_purchases.test_purchase, google_play_purchases.created_at,
       google_play_purchases.updated_at, devices.last_country
     FROM google_play_purchases
     LEFT JOIN devices ON devices.device_id = google_play_purchases.device_id
     ORDER BY google_play_purchases.created_at DESC LIMIT 200`,
  ).all();
  const { results: monthly } = await env.DB.prepare(
    `SELECT substr(COALESCE(paid_at, created_at), 1, 7) AS month, currency,
       COUNT(*) AS payments,
       SUM(CASE WHEN status <> 'PENDING' THEN amount_minor ELSE 0 END) AS gross_minor,
       SUM(amount_refunded_minor) AS refunded_minor
     FROM payments GROUP BY month, currency ORDER BY month DESC, currency LIMIT 36`,
  ).all();
  return { stripe: stripe ?? [], googlePlay: googlePlay ?? [], monthly: monthly ?? [] };
}

export async function recordSecurityAlert(deviceId, kind, severity, detail, env) {
  const existing = await env.DB.prepare(
    `SELECT id FROM security_alerts
     WHERE COALESCE(device_id, '') = COALESCE(?, '') AND kind = ? AND resolved_at IS NULL
       AND observed_at >= datetime('now', '-24 hours') LIMIT 1`,
  ).bind(deviceId || null, kind).first();
  if (existing) return false;
  await env.DB.prepare(
    `INSERT INTO security_alerts (device_id, kind, severity, detail, observed_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).bind(
    deviceId || null,
    supportText(kind, 60) || 'unknown',
    ['INFO', 'WARNING', 'CRITICAL'].includes(severity) ? severity : 'INFO',
    supportText(detail, 500),
    new Date().toISOString(),
  ).run();
  return true;
}

export async function recordFailedRedemption(deviceId, reason, env) {
  const now = new Date().toISOString();
  await env.DB.prepare(
    'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
  ).bind(deviceId, 'redeem_failed', supportText(reason, 100), now).run();
  const recent = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM events WHERE device_id = ? AND kind = 'redeem_failed'
       AND created_at >= datetime('now', '-15 minutes')`,
  ).bind(deviceId).first();
  if (Number(recent?.n ?? 0) >= 5) {
    await recordSecurityAlert(
      deviceId, 'REPEATED_INVALID_KEYS', 'WARNING',
      `${recent.n} tentativas de chave recusadas em 15 minutos`, env,
    );
  }
}

export async function listSecurityAlerts(env) {
  const { results } = await env.DB.prepare(
    `SELECT id, device_id, kind, severity, detail, observed_at, resolved_at, resolution_note
     FROM security_alerts ORDER BY resolved_at IS NULL DESC, observed_at DESC LIMIT 200`,
  ).all();
  return results ?? [];
}

export async function resolveSecurityAlert(id, note, env) {
  const result = await env.DB.prepare(
    `UPDATE security_alerts SET resolved_at = ?, resolution_note = ?
     WHERE id = ? AND resolved_at IS NULL`,
  ).bind(new Date().toISOString(), supportText(note, 300) || 'revisado', Number(id)).run();
  return Number(result?.meta?.changes ?? 0) > 0;
}

/** Support-safe recovery export. Cryptographic keys, purchase tokens and unused activation codes stay out. */
export async function adminBackup(env) {
  const { results: devices } = await env.DB.prepare(
    `SELECT ${ADMIN_DEVICE_COLUMNS} FROM devices ORDER BY updated_at DESC`,
  ).all();
  const { results: events } = await env.DB.prepare(
    'SELECT device_id, kind, detail, created_at FROM events ORDER BY created_at DESC LIMIT 10000',
  ).all();
  const finances = await financialOverview(env);
  const audit = await listAdminAudit(env);
  return {
    format: 'iptvburo-admin-backup-v1',
    createdAt: new Date().toISOString(),
    devices: devices ?? [],
    events: events ?? [],
    finances,
    audit,
  };
}

function supportText(value, maximum) {
  const clean = String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim();
  return clean ? clean.slice(0, maximum) : null;
}

function supportEmail(value) {
  const clean = supportText(value, 254);
  if (!clean) return null;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean) ? clean.toLowerCase() : null;
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
