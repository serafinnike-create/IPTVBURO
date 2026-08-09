/**
 * Buying a second free trial.
 *
 * The installation identity is a random id in one file. Deleting it produces a device the server has
 * never met, and seven more free days — repeatable for ever, by anybody who finds the folder. This
 * is the cheapest attack on the whole system: no patching, no network, no knowledge.
 *
 * The client keeps first-seen markers in three places and reports the earliest. The server counts
 * the trial from that date rather than from the moment of registration.
 *
 * The property that makes this safe to accept from an untrusted client: the date can only ever
 * *shorten* a trial, never extend one. Lying gains nothing.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import { generateKeyPair } from '../src/signing.js';

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
    const out = [];
    for (const statement of statements) out.push(await statement.run());
    return out;
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

async function registerBody(who, firstSeen) {
  nonceCounter += 1;
  const nonce = nonceCounter.toString(36).padStart(22, '0');
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
    ...(firstSeen ? { firstSeen } : {}),
  };
}

function post(body) {
  return new Request('https://local.test/v1/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function deviceRow(env, deviceId) {
  return env.DB.database.prepare('SELECT * FROM devices WHERE device_id = ?').get(deviceId);
}

const DAY = 86_400_000;

test('a fresh machine gets a full seven days', async () => {
  const env = environment();
  const who = await identity('11111111-1111-4111-8111-111111111111');

  const response = await worker.fetch(post(await registerBody(who)), env);
  assert.equal(response.status, 200, await response.clone().text());

  const row = deviceRow(env, who.deviceId);
  const days = (Date.parse(row.trial_ends_at) - Date.now()) / DAY;
  assert.ok(days > 6.9 && days < 7.1, `expected seven days, got ${days}`);
});

/**
 * A reinstall does not buy more time.
 *
 * The new identity looks like an unknown device, but the markers say the machine was first seen five
 * days ago — so two days remain, not seven.
 */
test('a machine that was seen five days ago has two days left', async () => {
  const env = environment();
  const who = await identity('22222222-2222-4222-8222-222222222222');
  const fiveDaysAgo = new Date(Date.now() - 5 * DAY).toISOString();

  await worker.fetch(post(await registerBody(who, fiveDaysAgo)), env);

  const row = deviceRow(env, who.deviceId);
  const days = (Date.parse(row.trial_ends_at) - Date.now()) / DAY;
  assert.ok(days > 1.9 && days < 2.1, `expected two days, got ${days}`);
});

test('a machine seen longer ago than the trial gets none of it', async () => {
  const env = environment();
  const who = await identity('33333333-3333-4333-8333-333333333333');
  const tenDaysAgo = new Date(Date.now() - 10 * DAY).toISOString();

  await worker.fetch(post(await registerBody(who, tenDaysAgo)), env);

  const row = deviceRow(env, who.deviceId);
  assert.ok(Date.parse(row.trial_ends_at) < Date.now(), 'the trial should already be over');
});

test('a valid old marker is not forgotten after one year', async () => {
  const env = environment();
  const who = await identity('38383838-3838-4383-8383-383838383838');
  const oldInstall = '2025-01-02T03:04:05.000Z';

  await worker.fetch(post(await registerBody(who, oldInstall)), env);

  const row = deviceRow(env, who.deviceId);
  assert.equal(row.first_seen_at, oldInstall);
  assert.ok(Date.parse(row.trial_ends_at) < Date.now(), 'an old install must not receive a new trial');
});

/**
 * A future date is ignored.
 *
 * The one direction the claim could be abused: reporting tomorrow to postpone the trial's start and
 * stretch it to eight days. Clamped to the past, so the worst a lie achieves is the ordinary seven.
 */
test('a first-seen date in the future is ignored', async () => {
  const env = environment();
  const who = await identity('44444444-4444-4444-8444-444444444444');
  const tomorrow = new Date(Date.now() + DAY).toISOString();

  await worker.fetch(post(await registerBody(who, tomorrow)), env);

  const row = deviceRow(env, who.deviceId);
  const days = (Date.parse(row.trial_ends_at) - Date.now()) / DAY;
  assert.ok(days < 7.1, `a future claim must not extend the trial, got ${days}`);
});

test('rubbish in the field is ignored rather than failing the registration', async () => {
  const env = environment();

  // A device that cannot register is a customer locked out. Whatever arrives here, the worst
  // outcome must be an ordinary seven-day trial.
  for (const [index, value] of ['not-a-date', '', '0', 'null', '9999999999999999'].entries()) {
    const who = await identity(`5555555${index}-5555-4555-8555-555555555555`);
    const response = await worker.fetch(post(await registerBody(who, value)), env);

    assert.equal(response.status, 200, `refused "${value}"`);
    const row = deviceRow(env, who.deviceId);
    const days = (Date.parse(row.trial_ends_at) - Date.now()) / DAY;
    assert.ok(days > 6.9 && days < 7.1, `"${value}" produced ${days} days`);
  }
});

/**
 * Registering twice still does not restart anything.
 *
 * The existing defence, retested because the new code path sits beside it: `ON CONFLICT DO NOTHING`
 * means the first row wins whatever the second request claims.
 */
test('a second registration cannot lengthen an existing trial', async () => {
  const env = environment();
  const who = await identity('66666666-6666-4666-8666-666666666666');
  const sixDaysAgo = new Date(Date.now() - 6 * DAY).toISOString();

  await worker.fetch(post(await registerBody(who, sixDaysAgo)), env);
  const firstEnd = deviceRow(env, who.deviceId).trial_ends_at;

  // Now claiming to be brand new.
  await worker.fetch(post(await registerBody(who)), env);

  assert.equal(deviceRow(env, who.deviceId).trial_ends_at, firstEnd, 'the trial moved');
});

test('an older claim is recorded so support can see it', async () => {
  const env = environment();
  const who = await identity('77777777-7777-4777-8777-777777777777');
  const threeDaysAgo = new Date(Date.now() - 3 * DAY).toISOString();

  await worker.fetch(post(await registerBody(who, threeDaysAgo)), env);

  const event = env.DB.database
    .prepare("SELECT detail FROM events WHERE device_id = ? AND kind = 'registered'")
    .get(who.deviceId);
  assert.ok(event?.detail?.includes('first seen'), 'a reinstall should be visible in the log');
});
