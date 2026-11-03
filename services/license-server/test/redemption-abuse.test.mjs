/**
 * Getting more out of a redemption key than it grants.
 *
 * A key is single use: it binds to the first device that redeems it. These tests pin what single use
 * has to mean beyond "a second device is refused" — the owner typing it again must not restart its
 * term or undo a revocation, a key must never be able to take time away from a licence already in
 * force, and the website's form must not be an unlimited way to try codes.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import { generateKeyPair } from '../src/signing.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const signingKeys = await generateKeyPair();
const DAY = 24 * 60 * 60 * 1000;

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
    this.database.exec('BEGIN IMMEDIATE');
    try {
      const out = [];
      for (const statement of statements) out.push(await statement.run());
      this.database.exec('COMMIT');
      return out;
    } catch (error) {
      this.database.exec('ROLLBACK');
      throw error;
    }
  }

  row(sql, ...values) {
    return this.database.prepare(sql).get(...values) ?? null;
  }

  exec(sql) {
    this.database.exec(sql);
  }
}

function environment(extra = {}) {
  return {
    DB: new LocalD1(),
    SIGNING_KEY: signingKeys.privateKeyPkcs8Base64,
    STRIPE_WEBHOOK_SECRET: 'fixture',
    STRIPE_SECRET_KEY: 'fixture',
    ...extra,
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

/** An identity of the shape the client produces, derived the way the server re-derives it. */
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

let nonceCounter = 0;

async function proofBody(who, action, registration = false) {
  nonceCounter += 1;
  const nonce = `r${nonceCounter.toString(36)}`.padStart(22, '0');
  const canonical = `iptvburo-device-proof-v1\n${action}\n${who.deviceId}\n${nonce}`;
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      { name: 'ECDSA', hash: 'SHA-256' },
      who.privateKey,
      new TextEncoder().encode(canonical),
    ),
  );
  return {
    deviceId: who.deviceId,
    nonce,
    proof: base64Url(signature),
    ...(registration ? { installationId: who.installationId, publicKey: who.publicKey } : {}),
  };
}

function post(path, body, headers = {}) {
  return new Request(`https://local.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
}

function form(path, fields, headers = {}) {
  return new Request(`https://local.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded', ...headers },
    body: new URLSearchParams(fields).toString(),
  });
}

async function register(env, who) {
  const response = await worker.fetch(post('/v1/register', await proofBody(who, 'register', true)), env);
  assert.equal(response.status, 200);
}

async function redeem(env, who, key) {
  return await worker.fetch(post('/v1/redeem', { ...(await proofBody(who, 'redeem')), key }), env);
}

function addKey(env, code, days) {
  env.DB.exec(
    `INSERT INTO redemption_keys (key_code, grant_days, created_at)
     VALUES ('${code}', ${days}, '2026-01-01T00:00:00.000Z')`,
  );
}

function device(env, deviceId) {
  return env.DB.row('SELECT * FROM devices WHERE device_id = ?', deviceId);
}

test('redeeming your own key again does not restart its term', async () => {
  const env = environment();
  const owner = await identity('31111111-1111-4111-8111-111111111111');
  await register(env, owner);
  addKey(env, 'AAAA-BBBB', 30);

  assert.equal((await redeem(env, owner, 'AAAA-BBBB')).status, 200);
  // Pretend the key was redeemed long ago and has nearly run out.
  const nearlyOver = new Date(Date.now() + DAY).toISOString();
  env.DB.exec(`UPDATE devices SET expires_at = '${nearlyOver}' WHERE device_id = '${owner.deviceId}'`);

  const again = await redeem(env, owner, 'AAAA-BBBB');
  assert.equal(again.status, 200, 'the owner is still answered, not told their key is used');
  assert.equal(device(env, owner.deviceId).expires_at, nearlyOver, 'typing it again must add nothing');
});

test('redeeming your own key again does not undo a revocation', async () => {
  const env = environment();
  const owner = await identity('32222222-2222-4222-8222-222222222222');
  await register(env, owner);
  addKey(env, 'CCCC-DDDD', 30);
  assert.equal((await redeem(env, owner, 'CCCC-DDDD')).status, 200);

  env.DB.exec(`UPDATE devices SET status = 'REVOKED' WHERE device_id = '${owner.deviceId}'`);

  const again = await redeem(env, owner, 'CCCC-DDDD');
  assert.equal(again.status, 409);
  assert.equal((await again.json()).error, 'already_used');
  assert.equal(device(env, owner.deviceId).status, 'REVOKED');
});

test('a key adds to a licence in force instead of cutting it short', async () => {
  const env = environment();
  const customer = await identity('33333333-3333-4333-8333-333333333333');
  await register(env, customer);
  const paidUntil = new Date(Date.now() + 700 * DAY);
  env.DB.exec(
    `UPDATE devices SET status = 'ACTIVE', expires_at = '${paidUntil.toISOString()}',
       purchased_at = '2026-01-01T00:00:00.000Z', stripe_session_id = 'cs_test_paid'
     WHERE device_id = '${customer.deviceId}'`,
  );
  addKey(env, 'EEEE-FFFF', 1);

  // The website form needs only the public device code, which is printed on the customer's screen.
  const response = await worker.fetch(
    form('/ativar', { device: customer.deviceId, key: 'EEEE-FFFF', lang: 'en' }),
    env,
  );
  assert.equal(response.status, 200);

  const after = device(env, customer.deviceId);
  assert.equal(after.status, 'ACTIVE');
  assert.equal(after.expires_at, new Date(paidUntil.getTime() + DAY).toISOString());
  // Still linked to the purchase, so refunding it still finds this device.
  assert.equal(after.stripe_session_id, 'cs_test_paid');
});

test('a key on an expired device counts from today and drops the stale purchase link', async () => {
  const env = environment();
  const customer = await identity('34444444-4444-4444-8444-444444444444');
  await register(env, customer);
  env.DB.exec(
    `UPDATE devices SET status = 'EXPIRED', expires_at = '2025-01-01T00:00:00.000Z',
       stripe_session_id = 'cs_test_old'
     WHERE device_id = '${customer.deviceId}'`,
  );
  addKey(env, 'GGGG-HHHH', 30);

  const before = Date.now();
  assert.equal((await redeem(env, customer, 'GGGG-HHHH')).status, 200);
  const after = device(env, customer.deviceId);
  assert.equal(after.status, 'ACTIVE');
  const expiry = Date.parse(after.expires_at);
  assert.ok(expiry >= before + 30 * DAY && expiry <= Date.now() + 30 * DAY);
  assert.equal(after.stripe_session_id, null);
});

test('a failed redemption on the website is recorded like one in the app', async () => {
  const env = environment();
  const customer = await identity('35555555-5555-4555-8555-555555555555');
  await register(env, customer);

  const response = await worker.fetch(
    form('/ativar', { device: customer.deviceId, key: 'NOPE-NOPE', lang: 'pt' }),
    env,
  );
  assert.equal(response.status, 400);
  const failures = env.DB.row(
    "SELECT COUNT(*) AS n FROM events WHERE device_id = ? AND kind = 'redeem_failed'",
    customer.deviceId,
  );
  assert.equal(Number(failures.n), 1);
});

test('the website redemption form is rate limited with the app redemption', async () => {
  const buckets = [];
  const env = environment({
    REGISTRATION_RATE_LIMITER: {
      limit: async ({ key }) => {
        buckets.push(key);
        return { success: false };
      },
    },
  });
  const headers = { 'cf-connecting-ip': '203.0.113.9' };

  const web = await worker.fetch(form('/ativar', { device: 'AAAA-AAAA-AAAA', key: 'X' }, headers), env);
  const app = await worker.fetch(post('/v1/redeem', { key: 'X' }, headers), env);
  const info = await worker.fetch(post('/v1/key-info', { key: 'X' }, headers), env);

  assert.equal(web.status, 429);
  assert.equal(app.status, 429);
  assert.equal(info.status, 429);
  assert.deepEqual(new Set(buckets), new Set(['key-guess:203.0.113.9']));

  // Opening the page is not a guess.
  const page = await worker.fetch(new Request('https://local.test/ativar', { headers }), env);
  assert.equal(page.status, 200);
});

test('only recent failed redemptions count towards the alert', async () => {
  const env = environment();
  const customer = await identity('36666666-6666-4666-8666-666666666666');
  await register(env, customer);
  // Four failures an hour ago, on the same day: outside the fifteen-minute window.
  const anHourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  for (let index = 0; index < 4; index += 1) {
    env.DB.exec(
      `INSERT INTO events (device_id, kind, detail, created_at)
       VALUES ('${customer.deviceId}', 'redeem_failed', 'unknown_key', '${anHourAgo}')`,
    );
  }

  await redeem(env, customer, 'NOPE-NOPE');
  const alerts = env.DB.row(
    "SELECT COUNT(*) AS n FROM security_alerts WHERE kind = 'REPEATED_INVALID_KEYS'",
  );
  assert.equal(Number(alerts.n), 0);
});
