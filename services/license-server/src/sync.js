/**
 * Keeping a household's devices in step: favourites and where each film was left.
 *
 * ## Who may take part
 *
 * Only a device with a licence in force — paid, or in its trial. Sync is part of what the licence
 * buys, and tying it to the licence means there is no account, password or e-mail address: the
 * device's own key, already pinned at registration, is the identity. Every call is authorised by a
 * signature from that key over a one-time nonce (see `authorizeExistingDevice` in index.js).
 *
 * ## How devices find each other
 *
 * One device asks for an invite and shows a short code; another types it. Eight characters from
 * the same 32-letter alphabet the device codes use — readable aloud and typeable on a remote, and
 * about 40 bits, against 20 for six digits. The code lives ten minutes and only its hash is stored.
 *
 * ## What is stored
 *
 * Opaque records. The client names each one with a hash of what it is (a favourite, a position)
 * and of the title, so record keys carry no titles. The content is encrypted at rest with a key
 * derived from SYNC_ENCRYPTION_KEY and the group: someone holding this table holds ciphertext and
 * hashes. This is encryption at rest, not end-to-end — the Worker can read what it relays, and the
 * comment says so rather than implying more.
 *
 * ## Conflicts
 *
 * The newer edit wins, decided inside the upsert itself so two devices writing at once cannot both
 * believe they won. A deletion is a record too (a tombstone), or a favourite removed on the phone
 * would come back from the television on the next exchange.
 */

/** How long an invite code may be typed. */
const INVITE_TTL_MS = 10 * 60 * 1000;

/** A household, not a fleet. Also bounds what one leaked code could ever join. */
export const MAX_MEMBERS = 10;

/** Per request. Small enough to stay well inside a Worker's CPU budget. */
export const MAX_PUSH_RECORDS = 200;

/** Per response. Larger than a push, which is what makes paging by revision safe; see pull(). */
export const MAX_PULL_RECORDS = 500;

/** A favourite or a position is a few hundred bytes of JSON; this leaves room and stops abuse. */
export const MAX_DATA_LENGTH = 4096;

/** Enough for any real library; stops the table being used as free storage. */
export const MAX_GROUP_RECORDS = 20000;

/** A clock running ahead could otherwise make one device's edits win for ever. */
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_PATTERN = /^[A-HJ-NP-Z2-9]{4}-?[A-HJ-NP-Z2-9]{4}$/;
const RECORD_KEY_PATTERN = /^[A-Za-z0-9_-]{8,128}$/;

/** Whether this device's licence currently covers sync. */
export function licenceCoversSync(device, now) {
  if (!device) return false;
  const nowIso = now.toISOString();
  if (device.status === 'ACTIVE') return !device.expires_at || device.expires_at > nowIso;
  if (device.status === 'TRIAL') return Boolean(device.trial_ends_at) && device.trial_ends_at > nowIso;
  return false;
}

/** Status: whether this device is in a group, with how many devices, and the group's revision. */
export async function syncStatus(deviceId, env) {
  const membership = await membershipOf(deviceId, env);
  if (!membership) return { inGroup: false, members: 0, revision: 0 };
  const group = await env.DB.prepare('SELECT revision FROM sync_groups WHERE group_id = ?')
    .bind(membership.group_id).first();
  return {
    inGroup: true,
    members: await memberCount(membership.group_id, env),
    revision: Number(group?.revision ?? 0),
  };
}

/**
 * An invite to this device's group, creating the group if the device has none yet.
 *
 * One live code per group: asking again replaces the previous one, so a code read out by mistake
 * can be withdrawn simply by asking for a new one.
 */
export async function createInvite(deviceId, env, now) {
  let membership = await membershipOf(deviceId, env);
  if (!membership) {
    const groupId = randomHex(16);
    await env.DB.batch([
      env.DB.prepare('INSERT INTO sync_groups (group_id, revision, created_at) VALUES (?, 0, ?)')
        .bind(groupId, now.toISOString()),
      env.DB.prepare('INSERT INTO sync_members (device_id, group_id, joined_at) VALUES (?, ?, ?)')
        .bind(deviceId, groupId, now.toISOString()),
    ]);
    membership = { group_id: groupId };
  }
  if ((await memberCount(membership.group_id, env)) >= MAX_MEMBERS) {
    return { ok: false, error: 'group_full', status: 409 };
  }
  const code = generateCode();
  const expiresAt = new Date(now.getTime() + INVITE_TTL_MS).toISOString();
  await env.DB.batch([
    env.DB.prepare('DELETE FROM sync_invites WHERE group_id = ? OR expires_at < ?')
      .bind(membership.group_id, now.toISOString()),
    env.DB.prepare('INSERT INTO sync_invites (code_hash, group_id, expires_at) VALUES (?, ?, ?)')
      .bind(await codeHash(code), membership.group_id, expiresAt),
  ]);
  return { ok: true, code, expiresAt };
}

/**
 * Joins the group an invite code belongs to.
 *
 * A device that was already in another group leaves it; a group left empty is deleted with its
 * records, so nothing lingers that no device can reach. Joining does not merge the two libraries
 * on the server: the joining device pushes what it has on its next exchange, and the newer edit of
 * each record wins as always.
 */
export async function joinWithCode(deviceId, rawCode, env, now) {
  const code = normaliseCode(rawCode);
  if (!code) return { ok: false, error: 'bad_code', status: 400 };
  const invite = await env.DB.prepare(
    'SELECT group_id, expires_at FROM sync_invites WHERE code_hash = ?',
  ).bind(await codeHash(code)).first();
  if (!invite || invite.expires_at <= now.toISOString()) {
    return { ok: false, error: 'unknown_code', status: 404 };
  }
  const current = await membershipOf(deviceId, env);
  if (current?.group_id === invite.group_id) return { ok: true, members: await memberCount(invite.group_id, env) };
  if ((await memberCount(invite.group_id, env)) >= MAX_MEMBERS) {
    return { ok: false, error: 'group_full', status: 409 };
  }
  await env.DB.batch([
    env.DB.prepare('DELETE FROM sync_members WHERE device_id = ?').bind(deviceId),
    env.DB.prepare('INSERT INTO sync_members (device_id, group_id, joined_at) VALUES (?, ?, ?)')
      .bind(deviceId, invite.group_id, now.toISOString()),
  ]);
  if (current) await deleteGroupIfEmpty(current.group_id, env);
  return { ok: true, members: await memberCount(invite.group_id, env) };
}

/** Leaves the group. The device keeps its own data; the group keeps everyone else's. */
export async function leaveGroup(deviceId, env) {
  const current = await membershipOf(deviceId, env);
  if (!current) return { ok: true };
  await env.DB.prepare('DELETE FROM sync_members WHERE device_id = ?').bind(deviceId).run();
  await deleteGroupIfEmpty(current.group_id, env);
  return { ok: true };
}

/**
 * Pushes this device's changes and returns everything newer than `since`, in one round trip.
 *
 * Every record changed by one push is stamped with the same new revision. A device remembers the
 * highest revision it has seen and asks for what came after it — so it never re-downloads the
 * library, and an edit made while it was off is still found.
 */
export async function exchange(deviceId, body, env, now) {
  const membership = await membershipOf(deviceId, env);
  if (!membership) return { ok: false, error: 'not_in_group', status: 409 };
  const groupId = membership.group_id;

  const incoming = Array.isArray(body?.records) ? body.records : [];
  if (incoming.length > MAX_PUSH_RECORDS) return { ok: false, error: 'too_many_records', status: 413 };
  const records = [];
  for (const record of incoming) {
    const parsed = parseRecord(record, now);
    if (!parsed) return { ok: false, error: 'bad_record', status: 400 };
    records.push(parsed);
  }

  if (records.length > 0) {
    const stored = await env.DB.prepare('SELECT COUNT(*) AS total FROM sync_records WHERE group_id = ?')
      .bind(groupId).first();
    if (Number(stored?.total ?? 0) + records.length > MAX_GROUP_RECORDS) {
      return { ok: false, error: 'group_storage_full', status: 413 };
    }
    // The bump and every write in one batch, which D1 runs as a single transaction. Split, two
    // devices pushing at once could commit revision 6 before revision 5, and a third pulling in
    // between would record 6 as seen and never fetch 5. Each write reads the revision the bump
    // just set, inside the same transaction.
    const key = await groupKey(groupId, env);
    const statements = [
      env.DB.prepare('UPDATE sync_groups SET revision = revision + 1 WHERE group_id = ?').bind(groupId),
    ];
    for (const record of records) {
      const sealed = await seal(key, record.data);
      statements.push(
        env.DB.prepare(
          `INSERT INTO sync_records
             (group_id, record_key, ciphertext, nonce, updated_at_ms, deleted, revision)
           VALUES (?, ?, ?, ?, ?, ?, (SELECT revision FROM sync_groups WHERE group_id = ?))
           ON CONFLICT (group_id, record_key) DO UPDATE SET
             ciphertext = excluded.ciphertext,
             nonce = excluded.nonce,
             updated_at_ms = excluded.updated_at_ms,
             deleted = excluded.deleted,
             revision = excluded.revision
           WHERE excluded.updated_at_ms > sync_records.updated_at_ms`,
        ).bind(groupId, record.key, sealed.ciphertext, sealed.nonce, record.updatedAt, record.deleted ? 1 : 0, groupId),
      );
    }
    await env.DB.batch(statements);
  }

  return { ok: true, ...(await pull(groupId, body?.since, env)) };
}

/**
 * Records with a revision above `since`, oldest first.
 *
 * A page may end part-way through a revision. When it does, that last revision is dropped from the
 * page and `since` is set just before it, so the next call fetches it whole. One revision is at most
 * one push, which is smaller than a page, so every page makes progress.
 */
async function pull(groupId, rawSince, env) {
  const since = Number.isSafeInteger(rawSince) && rawSince >= 0 ? rawSince : 0;
  // Read first and used as a ceiling. Every revision up to it is fully committed, because a push
  // and its bump are one transaction; anything committed after this read is above it and is left
  // for the next pull rather than half-seen by this one.
  const group = await env.DB.prepare('SELECT revision FROM sync_groups WHERE group_id = ?').bind(groupId).first();
  const newest = Number(group?.revision ?? 0);
  const result = await env.DB.prepare(
    `SELECT record_key, ciphertext, nonce, updated_at_ms, deleted, revision
       FROM sync_records
      WHERE group_id = ? AND revision > ? AND revision <= ?
      ORDER BY revision, record_key
      LIMIT ?`,
  ).bind(groupId, since, newest, MAX_PULL_RECORDS + 1).all();
  let rows = result?.results ?? [];
  let hasMore = false;
  if (rows.length > MAX_PULL_RECORDS) {
    hasMore = true;
    rows = rows.slice(0, MAX_PULL_RECORDS);
    const lastRevision = rows[rows.length - 1].revision;
    const complete = rows.filter((row) => row.revision !== lastRevision);
    if (complete.length > 0) rows = complete;
  }
  const key = rows.length > 0 ? await groupKey(groupId, env) : null;
  const records = [];
  for (const row of rows) {
    const data = await open(key, row.ciphertext, row.nonce);
    if (data === null) continue;
    records.push({
      key: row.record_key,
      updatedAt: Number(row.updated_at_ms),
      deleted: Number(row.deleted) === 1,
      data,
    });
  }
  const revision = hasMore ? Number(rows[rows.length - 1]?.revision ?? since) : newest;
  return { revision, hasMore, records };
}

function parseRecord(record, now) {
  if (!record || typeof record !== 'object') return null;
  const key = typeof record.key === 'string' && RECORD_KEY_PATTERN.test(record.key) ? record.key : null;
  const updatedAt = Number.isSafeInteger(record.updatedAt) && record.updatedAt > 0 ? record.updatedAt : null;
  const deleted = record.deleted === true;
  const data = typeof record.data === 'string' ? record.data : deleted ? '' : null;
  if (!key || !updatedAt || data === null || data.length > MAX_DATA_LENGTH) return null;
  return {
    key,
    // Clamped, not refused: a device whose clock runs a little fast still syncs, but cannot
    // stamp an edit so far ahead that nothing written after it could ever win.
    updatedAt: Math.min(updatedAt, now.getTime() + MAX_CLOCK_SKEW_MS),
    deleted,
    data,
  };
}

async function membershipOf(deviceId, env) {
  return await env.DB.prepare('SELECT group_id FROM sync_members WHERE device_id = ?').bind(deviceId).first();
}

async function memberCount(groupId, env) {
  const row = await env.DB.prepare('SELECT COUNT(*) AS total FROM sync_members WHERE group_id = ?')
    .bind(groupId).first();
  return Number(row?.total ?? 0);
}

async function deleteGroupIfEmpty(groupId, env) {
  if ((await memberCount(groupId, env)) > 0) return;
  await env.DB.batch([
    env.DB.prepare('DELETE FROM sync_records WHERE group_id = ?').bind(groupId),
    env.DB.prepare('DELETE FROM sync_invites WHERE group_id = ?').bind(groupId),
    env.DB.prepare('DELETE FROM sync_groups WHERE group_id = ?').bind(groupId),
  ]);
}

/** Uppercase, dash restored, or null when it cannot be a code at all. */
export function normaliseCode(raw) {
  const compact = String(raw ?? '').toUpperCase().replace(/[\s-]/g, '');
  if (compact.length !== 8) return null;
  const code = `${compact.slice(0, 4)}-${compact.slice(4)}`;
  return CODE_PATTERN.test(code) ? code : null;
}

/** Rejection-sampled from the CSPRNG, so every code is equally likely. */
function generateCode() {
  let code = '';
  while (code.length < 8) {
    const [byte] = crypto.getRandomValues(new Uint8Array(1));
    if (byte >= 256 - (256 % CODE_ALPHABET.length)) continue;
    code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
  }
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

async function codeHash(code) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`iptvburo-sync-invite-v1:${code}`));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function randomHex(bytes) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * The group's at-rest key: HKDF over the Worker secret, salted with the group.
 *
 * HKDF rather than the PBKDF2 provisioning uses: that one protects a password typed by a person and
 * is derived once per request, while this runs on every exchange and has a full-entropy secret as
 * input, where stretching adds cost and nothing else.
 */
async function groupKey(groupId, env) {
  const secret = String(env?.SYNC_ENCRYPTION_KEY ?? '');
  if (!secret) throw new Error('SyncEncryptionKeyMissing');
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), 'HKDF', false, ['deriveKey']);
  return await crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: new TextEncoder().encode('iptvburo-sync-v1'),
      info: new TextEncoder().encode(groupId),
    },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

async function seal(key, plaintext) {
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, key, new TextEncoder().encode(plaintext)),
  );
  return { ciphertext: toBase64(ciphertext), nonce: toBase64(nonce) };
}

async function open(key, ciphertext, nonce) {
  try {
    const plaintext = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: fromBase64(nonce) },
      key,
      fromBase64(ciphertext),
    );
    return new TextDecoder().decode(plaintext);
  } catch {
    // A record sealed under another key or tampered with is skipped, never returned garbled.
    return null;
  }
}

function toBase64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  const binary = atob(String(value ?? ''));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
