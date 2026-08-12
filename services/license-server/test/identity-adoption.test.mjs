/**
 * Adopting a device row that predates cryptographic identity.
 *
 * ## The bug this fixes
 *
 * Rows written by an earlier protocol version have no `public_key`. The server refused every one of
 * them with `identity_upgrade_required`, on the reasoning that possession of the short public device
 * code must not be enough to claim somebody's licence.
 *
 * That reasoning is right for a row somebody paid for. Applied to a bare trial it locked the device
 * out permanently: it could not register, because registering was the thing being refused, and there
 * was no other route to an identity. A real device hit this and showed "could not verify your
 * licence" — a message about the network, for a problem that had nothing to do with the network.
 *
 * The distinction now made is between a row that represents something of value and a row that does
 * not. These tests pin both sides, because getting either wrong is expensive: too strict locks
 * customers out, too loose lets anyone who reads a device code off a screen take the licence.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import { generateKeyPair } from '../src/signing.js';

// schema.sql is the current shape, migrations included. The migration files exist to bring an older
// deployed database up to it, and applying them on top here would fail on columns already present.
const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');

const signingKeys = await generateKeyPair();

class Statement {
  constructor(database, sql) {
    this.statement = database.prepare(sql);
    this.values = [];
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  async first() {
    return this.statement.get(...this.values) ?? null;
  }

  async all() {
    return { results: this.statement.all(...this.values) };
  }

  async run() {
    const result = this.statement.run(...this.values);
    return { meta: { changes: Number(result.changes ?? 0) } };
  }
}

class LocalD1 {
  constructor() {
    this.database = new DatabaseSync(':memory:');
    this.database.exec(SCHEMA);
  }

  prepare(sql) {
    return new Statement(this.database, sql);
  }

  async batch(statements) {
    const results = [];
    for (const statement of statements) results.push(await statement.run());
    return results;
  }
}

function environment() {
  return {
    DB: new LocalD1(),
    SIGNING_KEY: signingKeys.privateKeyPkcs8Base64,
    STRIPE_WEBHOOK_SECRET: 'fixture',
    STRIPE_SECRET_KEY: 'fixture',
  };
}

function base64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64Url(bytes) {
  return base64(bytes).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

/**
 * A device identity of the shape the client produces.
 *
 * The derivation has to match the server's exactly — SHA-256 over the public key followed by the
 * installation id, then base32 over the bit stream rather than one character per byte. Getting it
 * wrong produces `bad_identity`, which is the server correctly refusing a code that does not derive
 * from the key presented alongside it.
 */
async function identity(installationId) {
  const pair = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify'],
  );
  const publicKeyBytes = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey));
  const installationBytes = new TextEncoder().encode(installationId);
  const material = new Uint8Array(publicKeyBytes.length + installationBytes.length);
  material.set(publicKeyBytes, 0);
  material.set(installationBytes, publicKeyBytes.length);

  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', material));
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let bitBuffer = 0;
  let bitCount = 0;
  let code = '';
  for (const byte of digest) {
    bitBuffer = (bitBuffer << 8) | byte;
    bitCount += 8;
    while (bitCount >= 5 && code.length < 12) {
      bitCount -= 5;
      code += alphabet[(bitBuffer >>> bitCount) & 31];
    }
    bitBuffer = bitCount === 0 ? 0 : bitBuffer & ((1 << bitCount) - 1);
    if (code.length === 12) break;
  }

  return {
    installationId,
    deviceId: `${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 12)}`,
    privateKey: pair.privateKey,
    publicKey: base64(publicKeyBytes),
  };
}

async function registerBody(who, nonce) {
  const canonical = `iptvburo-device-proof-v1\nregister\n${who.deviceId}\n${nonce}`;
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      { name: 'ECDSA', hash: 'SHA-256' },
      who.privateKey,
      new TextEncoder().encode(canonical),
    ),
  );

  return {
    deviceId: who.deviceId,
    installationId: who.installationId,
    publicKey: who.publicKey,
    nonce,
    proof: base64Url(signature),
  };
}

function post(path, body) {
  return new Request(`https://local.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function nonce(seed) {
  return seed.padEnd(22, 'x').slice(0, 22);
}

/** Writes a row of the shape an earlier protocol version left behind: no public key. */
function seedLegacyRow(env, deviceId, overrides = {}) {
  const now = new Date().toISOString();
  const fields = {
    status: 'TRIAL',
    first_seen_at: now,
    trial_ends_at: new Date(Date.now() + 5 * 86400000).toISOString(),
    purchased_at: null,
    expires_at: null,
    stripe_session_id: null,
    note: null,
    ...overrides,
  };

  env.DB.database
    .prepare(
      `INSERT INTO devices
         (device_id, public_key, status, first_seen_at, trial_ends_at, purchased_at, expires_at,
          stripe_session_id, note, updated_at)
       VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .run(
      deviceId,
      fields.status,
      fields.first_seen_at,
      fields.trial_ends_at,
      fields.purchased_at,
      fields.expires_at,
      fields.stripe_session_id,
      fields.note,
      now,
    );
}

function deviceRow(env, deviceId) {
  return env.DB.database.prepare('SELECT * FROM devices WHERE device_id = ?').get(deviceId);
}

test('a bare legacy trial is adopted by a device that proves an identity', async () => {
  const env = environment();
  const who = await identity('33333333-3333-4333-8333-333333333333');
  seedLegacyRow(env, who.deviceId);

  const response = await worker.fetch(post('/v1/register', await registerBody(who, nonce('a1'))), env);

  assert.equal(response.status, 200, await response.clone().text());
  assert.equal(deviceRow(env, who.deviceId).public_key, who.publicKey, 'the key should be pinned');
});

/**
 * Adoption is not a way to obtain fresh days.
 *
 * The row keeps the dates it already had. Otherwise deleting the local licence file and letting the
 * device re-register would reset the trial — the exact attack the server-side trial exists to stop.
 */
test('adoption preserves the original trial dates', async () => {
  const env = environment();
  const who = await identity('44444444-4444-4444-8444-444444444444');
  const firstSeen = '2026-01-01T00:00:00.000Z';
  const trialEnds = '2026-01-08T00:00:00.000Z';
  seedLegacyRow(env, who.deviceId, { first_seen_at: firstSeen, trial_ends_at: trialEnds });

  await worker.fetch(post('/v1/register', await registerBody(who, nonce('b1'))), env);

  const row = deviceRow(env, who.deviceId);
  assert.equal(row.first_seen_at, firstSeen, 'adoption must not reset when the device was first seen');
  assert.equal(row.trial_ends_at, trialEnds, 'adoption must not extend the trial');
});

/**
 * A paid row is still protected.
 *
 * This is the case the original refusal was written for, and it must keep working: anyone who read a
 * device code off somebody's screen could otherwise register and take their licence.
 */
test('a legacy row that was paid for is refused', async () => {
  const env = environment();
  const who = await identity('55555555-5555-4555-8555-555555555555');
  seedLegacyRow(env, who.deviceId, {
    status: 'ACTIVE',
    purchased_at: '2026-02-01T00:00:00.000Z',
    expires_at: '2028-02-01T00:00:00.000Z',
    stripe_session_id: 'cs_test_fixture',
  });

  const response = await worker.fetch(post('/v1/register', await registerBody(who, nonce('c1'))), env);

  assert.equal(response.status, 409);
  assert.equal((await response.json()).error, 'identity_upgrade_required');
  assert.equal(deviceRow(env, who.deviceId).public_key, null, 'the row must be left untouched');
});

test('a legacy row granted by hand is refused', async () => {
  const env = environment();
  const who = await identity('66666666-6666-4666-8666-666666666666');
  // No Stripe session — an admin grant. Still worth something, so still protected.
  seedLegacyRow(env, who.deviceId, {
    status: 'ACTIVE',
    purchased_at: '2026-02-01T00:00:00.000Z',
    expires_at: '2027-02-01T00:00:00.000Z',
    note: 'paid cash',
  });

  const response = await worker.fetch(post('/v1/register', await registerBody(who, nonce('d1'))), env);

  assert.equal(response.status, 409);
});

test('a revoked legacy row is refused', async () => {
  const env = environment();
  const who = await identity('77777777-7777-4777-8777-777777777777');
  seedLegacyRow(env, who.deviceId, { status: 'REVOKED' });

  const response = await worker.fetch(post('/v1/register', await registerBody(who, nonce('e1'))), env);

  // Adopting a revoked row would undo the revocation, which is the opposite of what it means.
  assert.equal(response.status, 409);
});

/**
 * Only one device can adopt a row.
 *
 * Two machines that derive the same short code — vanishingly unlikely, but the whole point of
 * pinning a key is not to rely on that — must not both end up holding it. The second is refused as
 * a mismatch, because by then the row has a key that is not theirs.
 */
test('a second device cannot adopt a row that is already claimed', async () => {
  const env = environment();
  const first = await identity('88888888-8888-4888-8888-888888888888');
  seedLegacyRow(env, first.deviceId);

  const claimed = await worker.fetch(post('/v1/register', await registerBody(first, nonce('f1'))), env);
  assert.equal(claimed.status, 200);

  // A different key presenting the same device code.
  const second = await identity('99999999-9999-4999-8999-999999999999');
  const impostor = { ...second, deviceId: first.deviceId };
  const response = await worker.fetch(post('/v1/register', await registerBody(impostor, nonce('f2'))), env);

  // 400 rather than 401, and that is the stronger answer: the code is checked against the key it
  // arrives with before the database is consulted at all. An impostor is turned away for presenting
  // a code that does not derive from their own key, without the server having to look anything up.
  assert.equal(response.status, 400, 'the second device must be refused');
  assert.equal((await response.json()).error, 'bad_identity');
  assert.equal(deviceRow(env, first.deviceId).public_key, first.publicKey, 'the first key must hold');
});

test('adoption is recorded', async () => {
  const env = environment();
  const who = await identity('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
  seedLegacyRow(env, who.deviceId);

  await worker.fetch(post('/v1/register', await registerBody(who, nonce('g1'))), env);

  // Six months from now, "why does this device hold this row?" needs an answer in the table.
  const events = env.DB.database
    .prepare("SELECT kind FROM events WHERE device_id = ? AND kind = 'identity_adopted'")
    .all(who.deviceId);
  assert.equal(events.length, 1, 'the adoption should appear in the event log');
});

test('a device with no legacy row registers normally', async () => {
  const env = environment();
  const who = await identity('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');

  const response = await worker.fetch(post('/v1/register', await registerBody(who, nonce('h1'))), env);

  assert.equal(response.status, 200);
  assert.equal(deviceRow(env, who.deviceId).status, 'TRIAL');
});
