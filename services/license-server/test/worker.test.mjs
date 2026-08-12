/**
 * Behavioural tests for the Worker boundary.
 *
 * These use Node's in-memory SQLite as a small D1 adapter. They exercise the actual fetch handler,
 * HMAC verification and SQL without a Cloudflare account, Stripe key or network request.
 */

import { strict as assert } from 'node:assert';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker, { reconcileGooglePlayPurchases } from '../src/index.js';
import { LICENSE_PRODUCT } from '../src/checkout.js';
import { purchaseTokenHash } from '../src/google-play.js';
import { generateKeyPair } from '../src/signing.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const MIGRATION = readFileSync(
  new URL('../migrations/0001_stripe_payment_ledger.sql', import.meta.url),
  'utf8',
);
const DEVICE_MIGRATION = readFileSync(
  new URL('../migrations/0002_device_possession.sql', import.meta.url),
  'utf8',
);
const DISPUTE_MIGRATION = readFileSync(
  new URL('../migrations/0003_stripe_dispute_lifecycle.sql', import.meta.url),
  'utf8',
);
const GOOGLE_PLAY_MIGRATION = readFileSync(
  new URL('../migrations/0004_google_play_purchase_ledger.sql', import.meta.url),
  'utf8',
);
const DEVICE_PROFILE_MIGRATION = readFileSync(
  new URL('../migrations/0006_device_admin_profile.sql', import.meta.url),
  'utf8',
);
const WEBHOOK_SECRET = 'local-fixture-webhook-signing-secret';
const signingKeys = await generateKeyPair();
const googleServicePair = await crypto.subtle.generateKey(
  {
    name: 'RSASSA-PKCS1-v1_5',
    modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]),
    hash: 'SHA-256',
  },
  true,
  ['sign', 'verify'],
);
const googleServicePrivateDer = await crypto.subtle.exportKey('pkcs8', googleServicePair.privateKey);
const googleServicePrivateKey =
  '-----BEGIN PRIVATE KEY-----\n'
  + Buffer.from(googleServicePrivateDer).toString('base64').match(/.{1,64}/g).join('\n')
  + '\n-----END PRIVATE KEY-----';
const deviceIdentityA = await generateTestDeviceIdentity('11111111-1111-4111-8111-111111111111');
const deviceIdentityB = await generateTestDeviceIdentity('22222222-2222-4222-8222-222222222222');
const DEVICE_A = deviceIdentityA.deviceId;
const DEVICE_B = deviceIdentityB.deviceId;
let proofNonceCounter = 0;

class LocalD1Statement {
  constructor(database, sql) {
    this.statement = database.prepare(sql);
    this.values = [];
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  first() {
    return this.statement.get(...this.values) ?? null;
  }

  all() {
    return { results: this.statement.all(...this.values) };
  }

  run() {
    const result = this.statement.run(...this.values);
    return { success: true, meta: { changes: Number(result.changes) } };
  }
}

class LocalD1 {
  constructor(schema = SCHEMA) {
    this.database = new DatabaseSync(':memory:');
    this.database.exec(schema);
  }

  prepare(sql) {
    return new LocalD1Statement(this.database, sql);
  }

  batch(statements) {
    this.database.exec('BEGIN IMMEDIATE');
    try {
      const results = statements.map((statement) => statement.run());
      this.database.exec('COMMIT');
      return results;
    } catch (error) {
      this.database.exec('ROLLBACK');
      throw error;
    }
  }

  close() {
    this.database.close();
  }
}

async function generateTestDeviceIdentity(installationId) {
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
    publicKey: bytesToBase64(publicKeyBytes),
  };
}

function bytesToBase64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function bytesToBase64Url(bytes) {
  return bytesToBase64(bytes).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

function nextProofNonce() {
  proofNonceCounter += 1;
  return proofNonceCounter.toString(36).padStart(22, '0');
}

async function deviceProofBody(identity, action, { nonce = nextProofNonce(), registration = false } = {}) {
  const canonical = `iptvburo-device-proof-v1\n${action}\n${identity.deviceId}\n${nonce}`;
  const signature = new Uint8Array(await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    identity.privateKey,
    new TextEncoder().encode(canonical),
  ));
  return {
    deviceId: identity.deviceId,
    nonce,
    proof: bytesToBase64Url(signature),
    ...(registration
      ? { installationId: identity.installationId, publicKey: identity.publicKey }
      : {}),
  };
}

async function googlePlayProofBody(identity, purchaseToken, accountId, nonce = nextProofNonce()) {
  const tokenHash = await purchaseTokenHash(purchaseToken);
  const canonical =
    `iptvburo-google-play-purchase-v1\n${identity.deviceId}\n${nonce}\n${tokenHash}\n${accountId}`;
  const signature = new Uint8Array(await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    identity.privateKey,
    new TextEncoder().encode(canonical),
  ));
  return {
    deviceId: identity.deviceId,
    nonce,
    proof: bytesToBase64Url(signature),
    purchaseToken,
    accountId,
  };
}

function identityForDevice(deviceId) {
  if (deviceId === DEVICE_A) return deviceIdentityA;
  if (deviceId === DEVICE_B) return deviceIdentityB;
  return null;
}

async function registerDevice(env, identity) {
  const existing = row(env, 'SELECT public_key FROM devices WHERE device_id = ?', identity.deviceId);
  if (existing?.public_key) return;
  const body = await deviceProofBody(identity, 'register', { registration: true });
  const response = await worker.fetch(postJson('/v1/register', body), env);
  const responseBody = await response.clone().text();
  assert.equal(response.status, 200, responseBody);
}

function environment() {
  return {
    DB: new LocalD1(),
    SIGNING_KEY: signingKeys.privateKeyPkcs8Base64,
    STRIPE_WEBHOOK_SECRET: WEBHOOK_SECRET,
    STRIPE_SECRET_KEY: 'local-fixture-api-key',
    STRIPE_MODE: 'test',
    GOOGLE_PLAY_PACKAGE_NAME: 'com.lucasserafin94.iptvburo',
    GOOGLE_PLAY_PRODUCT_ID: 'iptvburo_730_days',
    GOOGLE_PLAY_PURCHASE_OPTION_ID: 'buy-730-days',
    GOOGLE_PLAY_ACCEPT_TEST_PURCHASES: 'false',
    GOOGLE_SERVICE_ACCOUNT_EMAIL: 'fixture@example.iam.gserviceaccount.com',
    GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY: googleServicePrivateKey,
    GOOGLE_TOKEN_ENCRYPTION_KEY: Buffer.alloc(32, 7).toString('base64'),
  };
}

function postJson(path, body) {
  return new Request(`https://local.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function stripeEvent({
  id,
  type,
  object,
  created = Date.parse('2026-08-08T12:00:00Z') / 1000,
  livemode = false,
}) {
  return { id, type, created, livemode, data: { object } };
}

function checkoutSession({
  id,
  paymentIntentId,
  deviceId = DEVICE_A,
  paymentStatus = 'paid',
  currency = 'eur',
  amountMinor = 990,
  productId = LICENSE_PRODUCT.id,
  grantDays = LICENSE_PRODUCT.grantDays,
}) {
  return {
    id,
    object: 'checkout.session',
    mode: 'payment',
    payment_status: paymentStatus,
    payment_intent: paymentIntentId,
    currency,
    amount_total: amountMinor,
    metadata: {
      device_id: deviceId,
      product_id: productId,
      grant_days: String(grantDays),
      currency,
      amount_minor: String(amountMinor),
    },
  };
}

function disputeObject({
  id,
  chargeId,
  paymentIntentId,
  status = 'needs_response',
  amountMinor = 990,
  currency = 'eur',
  created = Date.parse('2026-08-08T12:00:00Z') / 1000,
} = {}) {
  return {
    id,
    object: 'dispute',
    charge: chargeId,
    payment_intent: paymentIntentId,
    amount: amountMinor,
    currency,
    status,
    created,
    metadata: {},
  };
}

function signedWebhook(event, {
  extraV1 = [],
  secret = WEBHOOK_SECRET,
  timestamp = Math.floor(Date.now() / 1000),
} = {}) {
  const payload = JSON.stringify(event);
  const valid = createHmac('sha256', secret).update(`${timestamp}.${payload}`).digest('hex');
  // Put the valid value last: the test then proves verification tries every v1 during key rotation.
  const signatures = [...extraV1, valid].map((value) => `v1=${value}`).join(',');
  return new Request('https://local.test/v1/stripe-webhook', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'stripe-signature': `t=${timestamp},${signatures}`,
    },
    body: payload,
  });
}

function row(env, sql, ...values) {
  return env.DB.prepare(sql).bind(...values).first();
}

function count(env, sql, ...values) {
  return Number(row(env, sql, ...values)?.n ?? 0);
}

async function deliver(env, event, options) {
  const response = await worker.fetch(signedWebhook(event, options), env);
  const body = await response.json();
  return { response, body };
}

async function pay(env, {
  eventId = 'evt_paid_001',
  sessionId = 'cs_paid_001',
  paymentIntentId = 'pi_paid_001',
  deviceId = DEVICE_A,
  type = 'checkout.session.completed',
  created,
} = {}) {
  const identity = identityForDevice(deviceId);
  if (identity) await registerDevice(env, identity);
  const event = stripeEvent({
    id: eventId,
    type,
    object: checkoutSession({ id: sessionId, paymentIntentId, deviceId }),
    created,
  });
  return await deliver(env, event);
}

test('the fresh schema and the P0 migration both apply cleanly', () => {
  const fresh = new LocalD1();
  fresh.close();

  const legacy = new LocalD1(`
    CREATE TABLE devices (device_id TEXT PRIMARY KEY, stripe_session_id TEXT);
    CREATE TABLE events (id INTEGER PRIMARY KEY, device_id TEXT, kind TEXT, detail TEXT, created_at TEXT);
  `);
  legacy.database.exec(MIGRATION);
  legacy.database.exec(MIGRATION);
  legacy.database.exec(DEVICE_MIGRATION);
  legacy.database.exec(DISPUTE_MIGRATION);
  legacy.database.exec(DISPUTE_MIGRATION);
  legacy.database.exec(GOOGLE_PLAY_MIGRATION);
  legacy.database.exec(DEVICE_PROFILE_MIGRATION);
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'stripe_events'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'payment_disputes'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM pragma_table_info('devices') WHERE name = 'public_key'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'device_proof_nonces'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'google_play_purchases'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM pragma_table_info('devices') WHERE name = 'google_purchase_token_hash'")
      .get(),
  );
  assert.ok(
    legacy.database
      .prepare("SELECT name FROM pragma_table_info('devices') WHERE name = 'model'")
      .get(),
  );
  legacy.close();
});

test('non-object JSON is rejected as a bad proof request rather than becoming an internal error', async () => {
  const env = environment();
  try {
    for (const path of ['/v1/register', '/v1/validate', '/v1/redeem']) {
      const response = await worker.fetch(postJson(path, null), env);
      assert.equal(response.status, 400, path);
      assert.notEqual((await response.json()).error, 'internal', path);
    }
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces'), 0);
  } finally {
    env.DB.close();
  }
});

test('the signing-key check proves the deployed key without creating a trial device', async () => {
  const env = environment();
  try {
    const nonce = nextProofNonce();
    const response = await worker.fetch(postJson('/v1/signing-key-check', { nonce }), env);
    assert.equal(response.status, 200);
    const envelope = await response.json();
    const key = await crypto.subtle.importKey(
      'spki',
      Buffer.from(signingKeys.publicKeySpkiBase64, 'base64'),
      { name: 'Ed25519' },
      false,
      ['verify'],
    );
    assert.equal(
      await crypto.subtle.verify(
        'Ed25519',
        key,
        Buffer.from(envelope.signature, 'base64'),
        new TextEncoder().encode(envelope.payload),
      ),
      true,
    );
    assert.deepEqual(JSON.parse(envelope.payload), {
      nonce,
      purpose: 'iptvburo-signing-key-check-v1',
    });
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM devices'), 0);
  } finally {
    env.DB.close();
  }
});

test('route rate limiting rejects abusive registration before it reaches D1', async () => {
  const env = environment();
  env.REGISTRATION_RATE_LIMITER = { limit: async () => ({ success: false }) };
  try {
    const request = postJson('/v1/register', { invalid: true });
    request.headers.set('cf-connecting-ip', '192.0.2.10');
    const response = await worker.fetch(request, env);
    assert.equal(response.status, 429);
    assert.equal(response.headers.get('retry-after'), '60');
    assert.equal((await response.json()).error, 'rate_limited');
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM devices'), 0);
  } finally {
    env.DB.close();
  }
});

test('chunked form bodies cannot bypass the checkout and activation size limit', async () => {
  const env = environment();
  const oversized = `device=${'A'.repeat(65 * 1024)}`;
  try {
    for (const path of ['/checkout', '/ativar']) {
      const response = await worker.fetch(
        new Request(`https://local.test${path}`, {
          method: 'POST',
          headers: { 'content-type': 'application/x-www-form-urlencoded' },
          body: oversized,
        }),
        env,
      );
      assert.equal(response.status, 413);
      assert.equal((await response.json()).error, 'bad_or_oversized_form');
    }
  } finally {
    env.DB.close();
  }
});

test('Google Play is verified server-side, consumed and restored only to the same account id', async (t) => {
  const env = environment();
  const purchaseToken = 'opaque-play-token-worker-integration-0001';
  const accountId = 'c'.repeat(64);
  const completion = '2026-08-10T08:00:00Z';
  let consumptionCalls = 0;
  let consumptionState = 'CONSUMPTION_STATE_YET_TO_BE_CONSUMED';
  let refundableQuantity = 1;
  t.mock.method(globalThis, 'fetch', async (url) => {
    const target = String(url);
    if (target === 'https://oauth2.googleapis.com/token') {
      return Response.json({ access_token: 'worker-integration-access-token' });
    }
    if (target.includes('/purchases/productsv2/tokens/')) {
      return Response.json({
        productLineItem: [{
          productId: env.GOOGLE_PLAY_PRODUCT_ID,
          productOfferDetails: {
            purchaseOptionId: env.GOOGLE_PLAY_PURCHASE_OPTION_ID,
            quantity: 1,
            refundableQuantity,
            consumptionState,
          },
        }],
        purchaseStateContext: { purchaseState: 'PURCHASED' },
        obfuscatedExternalAccountId: accountId,
        purchaseCompletionTime: completion,
        acknowledgementState: consumptionState === 'CONSUMPTION_STATE_CONSUMED'
          ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'
          : 'ACKNOWLEDGEMENT_STATE_PENDING',
      });
    }
    if (target.endsWith(':consume')) {
      consumptionCalls += 1;
      consumptionState = 'CONSUMPTION_STATE_CONSUMED';
      return new Response('', { status: 200 });
    }
    throw new Error('UnexpectedGooglePlayCall');
  });

  try {
    await registerDevice(env, deviceIdentityA);
    const first = await worker.fetch(
      postJson(
        '/v1/google-play/purchase',
        await googlePlayProofBody(deviceIdentityA, purchaseToken, accountId),
      ),
      env,
    );
    assert.equal(first.status, 200, await first.clone().text());
    const firstEnvelope = await first.json();
    assert.equal(JSON.parse(firstEnvelope.payload).state, 'ACTIVE');
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
    const stored = row(env, 'SELECT * FROM google_play_purchases');
    assert.ok(stored.token_ciphertext.startsWith('v1.'));
    assert.ok(!stored.token_ciphertext.includes(purchaseToken));
    assert.equal(stored.status, 'PURCHASED');
    assert.equal(stored.acknowledgement_state, 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED');
    assert.equal(stored.expires_at, '2028-08-09T08:00:00.000Z');

    // Reinstallation on the same Android account hash may move the entitlement, but the old
    // installation is revoked atomically so one Play purchase never licenses two devices.
    await registerDevice(env, deviceIdentityB);
    const restored = await worker.fetch(
      postJson(
        '/v1/google-play/purchase',
        await googlePlayProofBody(deviceIdentityB, purchaseToken, accountId),
      ),
      env,
    );
    assert.equal(restored.status, 200, await restored.clone().text());
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_B).status, 'ACTIVE');
    assert.equal(row(env, 'SELECT device_id FROM google_play_purchases').device_id, DEVICE_B);
    assert.equal(consumptionCalls, 1);

    // A full refund is visible as zero refundable quantity even if Google's top-level purchase
    // state still says PURCHASED. The hourly server reconciliation revokes it without app help.
    refundableQuantity = 0;
    const reconciled = await reconcileGooglePlayPurchases(env, 50, new Date('2026-08-11T00:00:00Z'));
    assert.deepEqual(reconciled, { checked: 1, changed: 1 });
    assert.equal(row(env, 'SELECT status FROM google_play_purchases').status, 'REFUNDED');
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_B).status, 'REVOKED');
    assert.equal(
      count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'google_play_refunded'"),
      1,
    );
  } finally {
    env.DB.close();
  }
});

test('a consumed Google Play token cannot create a new entitlement without a prior ledger row', async (t) => {
  const env = environment();
  const purchaseToken = 'opaque-play-token-consumed-without-ledger-0001';
  const accountId = 'd'.repeat(64);
  t.mock.method(globalThis, 'fetch', async (url) => {
    const target = String(url);
    if (target === 'https://oauth2.googleapis.com/token') {
      return Response.json({ access_token: 'worker-integration-access-token' });
    }
    if (target.includes('/purchases/productsv2/tokens/')) {
      return Response.json({
        productLineItem: [{
          productId: env.GOOGLE_PLAY_PRODUCT_ID,
          productOfferDetails: {
            purchaseOptionId: env.GOOGLE_PLAY_PURCHASE_OPTION_ID,
            quantity: 1,
            refundableQuantity: 1,
            consumptionState: 'CONSUMPTION_STATE_CONSUMED',
          },
        }],
        purchaseStateContext: { purchaseState: 'PURCHASED' },
        obfuscatedExternalAccountId: accountId,
        purchaseCompletionTime: '2026-08-10T08:00:00Z',
        acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
      });
    }
    throw new Error('UnexpectedGooglePlayCall');
  });

  try {
    await registerDevice(env, deviceIdentityA);
    const response = await worker.fetch(
      postJson(
        '/v1/google-play/purchase',
        await googlePlayProofBody(deviceIdentityA, purchaseToken, accountId),
      ),
      env,
    );
    assert.equal(response.status, 409);
    assert.equal((await response.json()).error, 'purchase_already_consumed');
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM google_play_purchases'), 0);
  } finally {
    env.DB.close();
  }
});

test('registration derives the public code, pins P-256 and stores no reported MAC', async () => {
  const env = environment();
  try {
    const body = {
      ...await deviceProofBody(deviceIdentityA, 'register', { registration: true }),
      macAddress: '02:00:00:00:00:01',
    };
    const response = await worker.fetch(postJson('/v1/register', body), env);
    assert.equal(response.status, 200);

    const stored = row(
      env,
      'SELECT public_key, mac_address FROM devices WHERE device_id = ?',
      DEVICE_A,
    );
    assert.equal(stored.public_key, deviceIdentityA.publicKey);
    assert.equal(stored.mac_address, null);
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces'), 1);
  } finally {
    env.DB.close();
  }
});

test('a false UUID-key derivation is rejected before its nonce is claimed', async () => {
  const env = environment();
  try {
    const nonce = nextProofNonce();
    const mismatchedIdentity = { ...deviceIdentityA, deviceId: DEVICE_B };
    const bad = await deviceProofBody(mismatchedIdentity, 'register', { nonce, registration: true });
    const rejected = await worker.fetch(postJson('/v1/register', bad), env);
    assert.equal(rejected.status, 400);
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces WHERE nonce = ?', nonce), 0);

    const corrected = await deviceProofBody(deviceIdentityA, 'register', { nonce, registration: true });
    const accepted = await worker.fetch(postJson('/v1/register', corrected), env);
    assert.equal(accepted.status, 200);
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces WHERE nonce = ?', nonce), 1);
  } finally {
    env.DB.close();
  }
});

test('proof action substitution fails and a valid proof is accepted only once', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    const nonce = nextProofNonce();
    const wrongAction = await deviceProofBody(deviceIdentityA, 'redeem', { nonce });
    const rejected = await worker.fetch(postJson('/v1/validate', wrongAction), env);
    assert.equal(rejected.status, 401);
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces WHERE nonce = ?', nonce), 0);

    const valid = await deviceProofBody(deviceIdentityA, 'validate', { nonce });
    const accepted = await worker.fetch(postJson('/v1/validate', valid), env);
    const replayed = await worker.fetch(postJson('/v1/validate', valid), env);
    assert.equal(accepted.status, 200);
    assert.equal(replayed.status, 409);
    assert.deepEqual(await replayed.json(), { error: 'proof_replayed' });
  } finally {
    env.DB.close();
  }
});

test('concurrent copies of one valid proof produce one validation', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    const proof = await deviceProofBody(deviceIdentityA, 'validate');
    const responses = await Promise.all([
      worker.fetch(postJson('/v1/validate', proof), env),
      worker.fetch(postJson('/v1/validate', proof), env),
    ]);

    assert.deepEqual(responses.map((response) => response.status).sort(), [200, 409]);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'validated'"), 1);
  } finally {
    env.DB.close();
  }
});

test('a legacy row without a key cannot be claimed with its public code', async () => {
  const env = environment();
  try {
    const now = '2026-08-08T12:00:00.000Z';
    env.DB.prepare(
      `INSERT INTO devices (
         device_id, public_key, status, first_seen_at, trial_ends_at, updated_at
       ) VALUES (?, NULL, 'ACTIVE', ?, ?, ?)`,
    )
      .bind(DEVICE_A, now, '2028-08-07T12:00:00.000Z', now)
      .run();

    const nonce = nextProofNonce();
    const registration = await deviceProofBody(deviceIdentityA, 'register', {
      nonce,
      registration: true,
    });
    const registerResponse = await worker.fetch(postJson('/v1/register', registration), env);
    assert.equal(registerResponse.status, 409);
    assert.deepEqual(await registerResponse.json(), { error: 'identity_upgrade_required' });
    assert.equal(row(env, 'SELECT public_key FROM devices WHERE device_id = ?', DEVICE_A).public_key, null);
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM device_proof_nonces WHERE nonce = ?', nonce), 0);

    const validation = await deviceProofBody(deviceIdentityA, 'validate');
    const validateResponse = await worker.fetch(postJson('/v1/validate', validation), env);
    assert.equal(validateResponse.status, 409);
    assert.deepEqual(await validateResponse.json(), { error: 'identity_upgrade_required' });
  } finally {
    env.DB.close();
  }
});

test('CORS is limited to the public app API and sensitive responses are never cached', async () => {
  const env = environment();
  try {
    const preflight = await worker.fetch(new Request('https://local.test/v1/validate', {
      method: 'OPTIONS',
      headers: {
        origin: 'https://app.example',
        'access-control-request-method': 'POST',
        'access-control-request-headers': 'content-type',
      },
    }), env);
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers.get('access-control-allow-origin'), '*');
    assert.equal(preflight.headers.get('access-control-allow-headers'), 'content-type');
    assert.equal(preflight.headers.get('cache-control'), 'no-store');

    const publicApi = await worker.fetch(postJson('/v1/validate', {}), env);
    assert.equal(publicApi.status, 400);
    assert.equal(publicApi.headers.get('access-control-allow-origin'), '*');
    assert.equal(publicApi.headers.get('cache-control'), 'no-store');

    const adminPreflight = await worker.fetch(new Request('https://local.test/admin/summary', {
      method: 'OPTIONS',
      headers: { origin: 'https://hostile.example' },
    }), env);
    assert.equal(adminPreflight.status, 405);
    assert.equal(adminPreflight.headers.get('access-control-allow-origin'), null);

    const playPreflight = await worker.fetch(new Request('https://local.test/v1/google-play/purchase', {
      method: 'OPTIONS',
      headers: { origin: 'https://hostile.example' },
    }), env);
    assert.equal(playPreflight.status, 405);
    assert.equal(playPreflight.headers.get('access-control-allow-origin'), null);

    const adminPage = await worker.fetch(new Request('https://local.test/admin'), env);
    assert.equal(adminPage.status, 200);
    assert.equal(adminPage.headers.get('access-control-allow-origin'), null);
    assert.equal(adminPage.headers.get('cache-control'), 'no-store');

    const unauthorizedAdmin = await worker.fetch(
      new Request('https://local.test/admin/summary'),
      env,
    );
    assert.equal(unauthorizedAdmin.status, 401);
    assert.equal(unauthorizedAdmin.headers.get('access-control-allow-origin'), null);
    assert.equal(unauthorizedAdmin.headers.get('cache-control'), 'no-store');
  } finally {
    env.DB.close();
  }
});

test('opening the Checkout return URL without a ledger session never claims success', async () => {
  const env = environment();
  try {
    const response = await worker.fetch(
      new Request(`https://local.test/obrigado?device=${DEVICE_A}`, {
        headers: { 'accept-language': 'pt-BR' },
      }),
      env,
    );
    const page = await response.text();

    assert.equal(response.status, 400);
    assert.ok(page.includes('Pagamento não confirmado'));
    assert.ok(!page.includes('O seu dispositivo está ativo'));
  } finally {
    env.DB.close();
  }
});

test('a known pending Checkout renders processing and a paid one renders confirmed', async () => {
  const env = environment();
  try {
    const sessionId = 'cs_return_state_001';
    const paymentIntentId = 'pi_return_state_001';
    const now = '2026-08-08T12:00:00.000Z';
    env.DB.prepare(
      `INSERT INTO payments (
         checkout_session_id, payment_intent_id, device_id, product_id, amount_minor, currency,
         status, created_at, updated_at
       ) VALUES (?, ?, ?, ?, 990, 'eur', 'PENDING', ?, ?)`,
    )
      .bind(sessionId, paymentIntentId, DEVICE_A, LICENSE_PRODUCT.id, now, now)
      .run();

    const returnUrl =
      `https://local.test/obrigado?device=${DEVICE_A}&session_id=${sessionId}`;
    const pending = await worker.fetch(
      new Request(returnUrl, { headers: { 'accept-language': 'pt-BR' } }),
      env,
    );
    assert.equal(pending.status, 202);
    assert.ok((await pending.text()).includes('Pagamento em processamento'));

    const paid = await pay(env, { sessionId, paymentIntentId, eventId: 'evt_return_state_001' });
    assert.equal(paid.response.status, 200);

    const confirmed = await worker.fetch(
      new Request(returnUrl, { headers: { 'accept-language': 'pt-BR' } }),
      env,
    );
    const confirmedPage = await confirmed.text();
    assert.equal(confirmed.status, 200);
    assert.ok(confirmedPage.includes('Pagamento confirmado'));
    assert.ok(confirmedPage.includes(DEVICE_A));
  } finally {
    env.DB.close();
  }
});

test('validate authenticates an already registered device without consuming the body twice', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    const validation = await deviceProofBody(deviceIdentityA, 'validate');
    const response = await worker.fetch(
      postJson('/v1/validate', validation),
      env,
    );
    assert.equal(response.status, 200);
    const envelope = await response.json();
    const payload = JSON.parse(envelope.payload);
    assert.equal(payload.deviceId, DEVICE_A);
    assert.equal(payload.state, 'TRIAL');
    assert.equal(payload.nonce, validation.nonce);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'TRIAL');
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'registered'"), 1);
  } finally {
    env.DB.close();
  }
});

test('a valid signature is accepted when another v1 signature is also present', async () => {
  const env = environment();
  try {
    const event = stripeEvent({ id: 'evt_rotation_001', type: 'customer.created', object: { id: 'cus_1' } });
    const { response } = await deliver(env, event, { extraV1: ['0'.repeat(64)] });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('access-control-allow-origin'), null);
    assert.equal(response.headers.get('cache-control'), 'no-store');

    const rejected = await worker.fetch(signedWebhook(event, { secret: 'wrong-local-secret' }), env);
    assert.equal(rejected.status, 400);

    const stale = await worker.fetch(signedWebhook(event, {
      timestamp: Math.floor(Date.now() / 1000) - 301,
    }), env);
    assert.equal(stale.status, 400);
  } finally {
    env.DB.close();
  }
});

test('a signed live event cannot enter a test ledger and an undeclared mode fails closed', async () => {
  const env = environment();
  try {
    const liveEvent = stripeEvent({
      id: 'evt_wrong_mode_001',
      type: 'customer.created',
      object: { id: 'cus_wrong_mode' },
      livemode: true,
    });
    const wrongMode = await deliver(env, liveEvent);
    assert.equal(wrongMode.response.status, 400);
    assert.equal(wrongMode.body.error, 'wrong_stripe_mode');
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM stripe_events'), 0);

    delete env.STRIPE_MODE;
    const noMode = await deliver(env, { ...liveEvent, livemode: false });
    assert.equal(noMode.response.status, 503);
    assert.equal(noMode.body.error, 'stripe_mode_unconfigured');
    assert.equal(count(env, 'SELECT COUNT(*) AS n FROM stripe_events'), 0);
  } finally {
    env.DB.close();
  }
});

test('checkout ignores a tampered currency field and keeps the server-owned euro contract', async (t) => {
  const env = environment();
  let captured;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    captured = { url, options };
    return new Response(
      JSON.stringify({
        id: 'cs_checkout_001',
        url: 'https://checkout.stripe.com/c/pay/local-fixture',
        payment_intent: 'pi_checkout_001',
      }),
      { status: 200, headers: { 'content-type': 'application/json' } },
    );
  });

  try {
    await registerDevice(env, deviceIdentityA);
    const form = new URLSearchParams({ device: DEVICE_A, currency: 'brl' });
    const response = await worker.fetch(
      new Request('https://local.test/checkout', {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: form,
      }),
      env,
    );
    assert.equal(response.status, 303);
    assert.equal(response.headers.get('location'), 'https://checkout.stripe.com/c/pay/local-fixture');
    assert.equal(response.headers.get('cache-control'), 'no-store');

    const sent = new URLSearchParams(captured.options.body);
    assert.equal(sent.get('line_items[0][price_data][currency]'), 'eur');
    assert.equal(sent.get('line_items[0][price_data][unit_amount]'), '990');
    assert.ok(sent.get('success_url').includes('session_id={CHECKOUT_SESSION_ID}'));
    assert.equal(sent.get('metadata[product_id]'), LICENSE_PRODUCT.id);
    assert.equal(sent.get('metadata[grant_days]'), '730');
    assert.equal(sent.get('metadata[amount_minor]'), '990');
    assert.equal(sent.get('payment_intent_data[metadata][device_id]'), DEVICE_A);

    const payment = row(env, 'SELECT * FROM payments WHERE checkout_session_id = ?', 'cs_checkout_001');
    assert.equal(payment.status, 'PENDING');
    assert.equal(payment.amount_minor, 990);
    assert.equal(payment.currency, 'eur');
  } finally {
    env.DB.close();
  }
});

test('Checkout refuses an unknown or legacy public code before contacting Stripe', async (t) => {
  const env = environment();
  const fetchMock = t.mock.method(globalThis, 'fetch', async () => {
    throw new Error('Stripe must not be contacted');
  });
  try {
    const form = (device) => new Request('https://local.test/checkout', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ device }),
    });

    const unknown = await worker.fetch(form(DEVICE_A), env);
    assert.equal(unknown.status, 404);
    assert.deepEqual(await unknown.json(), { error: 'not_registered' });

    const now = '2026-08-08T12:00:00.000Z';
    env.DB.prepare(
      `INSERT INTO devices (
         device_id, public_key, status, first_seen_at, trial_ends_at, updated_at
       ) VALUES (?, NULL, 'ACTIVE', ?, ?, ?)`,
    )
      .bind(DEVICE_A, now, '2028-08-07T12:00:00.000Z', now)
      .run();
    const legacy = await worker.fetch(form(DEVICE_A), env);
    assert.equal(legacy.status, 409);
    assert.deepEqual(await legacy.json(), { error: 'identity_upgrade_required' });
    assert.equal(fetchMock.mock.callCount(), 0);
  } finally {
    env.DB.close();
  }
});

test('checkout.session.completed does not activate an unpaid session', async () => {
  const env = environment();
  try {
    const event = stripeEvent({
      id: 'evt_unpaid_001',
      type: 'checkout.session.completed',
      object: checkoutSession({
        id: 'cs_unpaid_001',
        paymentIntentId: 'pi_unpaid_001',
        paymentStatus: 'unpaid',
      }),
    });
    const { response, body } = await deliver(env, event);
    assert.equal(response.status, 200);
    assert.equal(body.ignored, true);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM devices WHERE status = 'ACTIVE'"), 0);
    const ledger = row(env, 'SELECT status, detail FROM stripe_events WHERE event_id = ?', event.id);
    assert.deepEqual({ ...ledger }, { status: 'IGNORED', detail: 'payment_not_paid' });
  } finally {
    env.DB.close();
  }
});

test('a paid Checkout activates once and duplicate events do not extend it', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    const event = stripeEvent({
      id: 'evt_paid_once',
      type: 'checkout.session.completed',
      object: checkoutSession({ id: 'cs_paid_once', paymentIntentId: 'pi_paid_once' }),
    });
    const first = await deliver(env, event);
    const firstExpiry = row(env, 'SELECT expires_at FROM devices WHERE device_id = ?', DEVICE_A).expires_at;
    const second = await deliver(env, event);
    const secondExpiry = row(env, 'SELECT expires_at FROM devices WHERE device_id = ?', DEVICE_A).expires_at;

    assert.equal(first.response.status, 200);
    assert.equal(first.body.activated, true);
    assert.equal(second.body.duplicate, true);
    assert.equal(secondExpiry, firstExpiry);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'purchased'"), 1);
    const paid = row(env, 'SELECT paid_at FROM payments WHERE checkout_session_id = ?', 'cs_paid_once');
    assert.equal(
      new Date(firstExpiry).getTime() - new Date(paid.paid_at).getTime(),
      LICENSE_PRODUCT.grantDays * 24 * 60 * 60 * 1000,
    );

    const sameSessionLaterEvent = stripeEvent({
      id: 'evt_paid_same_session_async',
      type: 'checkout.session.async_payment_succeeded',
      object: checkoutSession({ id: 'cs_paid_once', paymentIntentId: 'pi_paid_once' }),
      created: Date.parse('2026-08-10T12:00:00Z') / 1000,
    });
    const later = await deliver(env, sameSessionLaterEvent);
    assert.equal(later.body.duplicate, true);
    assert.equal(row(env, 'SELECT expires_at FROM devices WHERE device_id = ?', DEVICE_A).expires_at, firstExpiry);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'purchased'"), 1);
  } finally {
    env.DB.close();
  }
});

test('checkout.session.async_payment_succeeded activates a paid delayed method', async () => {
  const env = environment();
  try {
    const result = await pay(env, {
      eventId: 'evt_async_paid_001',
      sessionId: 'cs_async_paid_001',
      paymentIntentId: 'pi_async_paid_001',
      type: 'checkout.session.async_payment_succeeded',
    });
    assert.equal(result.response.status, 200);
    assert.equal(result.body.activated, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
  } finally {
    env.DB.close();
  }
});

test('every server-owned Checkout contract field is enforced before activation', async () => {
  const invalidSessions = [
    ['mode', (session) => { session.mode = 'subscription'; }],
    ['amount', (session) => { session.amount_total = 1; }],
    ['amount-metadata', (session) => { session.metadata.amount_minor = '1'; }],
    ['currency', (session) => { session.currency = 'gbp'; session.metadata.currency = 'gbp'; }],
    ['currency-metadata', (session) => { session.metadata.currency = 'usd'; }],
    ['product', (session) => { session.metadata.product_id = 'different-product'; }],
    ['grant', (session) => { session.metadata.grant_days = '729'; }],
    ['payment-intent', (session) => { session.payment_intent = null; }],
  ];
  for (const [suffix, invalidate] of invalidSessions) {
    const env = environment();
    try {
      const idSuffix = suffix.replaceAll('-', '_');
      const session = checkoutSession({
        id: `cs_bad_${idSuffix}`,
        paymentIntentId: `pi_bad_${idSuffix}`,
      });
      invalidate(session);
      const event = stripeEvent({
        id: `evt_bad_${idSuffix}`,
        type: 'checkout.session.completed',
        object: session,
      });
      const result = await deliver(env, event);
      assert.equal(result.body.ignored, true, suffix);
      assert.equal(count(env, "SELECT COUNT(*) AS n FROM devices WHERE status = 'ACTIVE'"), 0, suffix);
    } finally {
      env.DB.close();
    }
  }
});

test('partial refunds are audited without revoking; a later full refund revokes', async () => {
  const env = environment();
  try {
    await pay(env);
    const partial = stripeEvent({
      id: 'evt_refund_partial_001',
      type: 'charge.refunded',
      object: {
        id: 'ch_paid_001',
        object: 'charge',
        payment_intent: 'pi_paid_001',
        amount: 990,
        amount_refunded: 100,
        refunded: false,
        currency: 'eur',
        metadata: {},
      },
    });
    const partialResult = await deliver(env, partial);
    assert.equal(partialResult.body.revoked, false);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_paid_001').status,
      'PARTIALLY_REFUNDED',
    );
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'refund_partial'"), 1);

    const full = stripeEvent({
      id: 'evt_refund_full_001',
      type: 'charge.refunded',
      object: {
        ...partial.data.object,
        amount_refunded: 990,
        refunded: true,
      },
    });
    const fullResult = await deliver(env, full);
    assert.equal(fullResult.body.revoked, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REFUNDED');
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_paid_001').status,
      'REFUNDED',
    );
  } finally {
    env.DB.close();
  }
});

test('a full refund overrides DISPUTED and a later won event cannot restore access', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_refund_dispute_purchase',
      sessionId: 'cs_refund_dispute_purchase',
      paymentIntentId: 'pi_refund_dispute_purchase',
    });
    await deliver(env, stripeEvent({
      id: 'evt_refund_dispute_opened',
      type: 'charge.dispute.created',
      object: disputeObject({
        id: 'dp_refund_dispute',
        chargeId: 'ch_refund_dispute',
        paymentIntentId: 'pi_refund_dispute_purchase',
      }),
    }));
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_refund_dispute_purchase').status,
      'DISPUTED',
    );

    const refunded = await deliver(env, stripeEvent({
      id: 'evt_refund_dispute_full',
      type: 'charge.refunded',
      object: {
        id: 'ch_refund_dispute',
        object: 'charge',
        payment_intent: 'pi_refund_dispute_purchase',
        amount: 990,
        amount_refunded: 990,
        refunded: true,
        currency: 'eur',
        metadata: {},
      },
    }));
    assert.equal(refunded.response.status, 200);
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_refund_dispute_purchase').status,
      'REFUNDED',
    );
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REFUNDED');

    await deliver(env, stripeEvent({
      id: 'evt_refund_dispute_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_refund_dispute',
        chargeId: 'ch_refund_dispute',
        paymentIntentId: 'pi_refund_dispute_purchase',
        status: 'won',
      }),
    }));
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_refund_dispute_purchase').status,
      'REFUNDED',
    );
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REFUNDED');
  } finally {
    env.DB.close();
  }
});

test('impossible refund totals are ignored without changing the entitlement', async () => {
  for (const [suffix, amountRefunded] of [['zero', 0], ['above_total', 991]]) {
    const env = environment();
    try {
      const paymentIntentId = `pi_refund_invalid_${suffix}`;
      await pay(env, {
        eventId: `evt_paid_refund_invalid_${suffix}`,
        sessionId: `cs_refund_invalid_${suffix}`,
        paymentIntentId,
      });
      const result = await deliver(env, stripeEvent({
        id: `evt_refund_invalid_${suffix}`,
        type: 'charge.refunded',
        object: {
          id: `ch_refund_invalid_${suffix}`,
          object: 'charge',
          payment_intent: paymentIntentId,
          amount: 990,
          amount_refunded: amountRefunded,
          refunded: amountRefunded >= 990,
          currency: 'eur',
          metadata: {},
        },
      }));

      assert.equal(result.response.status, 200, suffix);
      assert.equal(result.body.ignored, true, suffix);
      assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
      const payment = row(
        env,
        'SELECT status, amount_refunded_minor FROM payments WHERE payment_intent_id = ?',
        paymentIntentId,
      );
      assert.deepEqual({ ...payment }, { status: 'PAID', amount_refunded_minor: 0 }, suffix);
      assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind IN ('refunded', 'refund_partial')"), 0);
    } finally {
      env.DB.close();
    }
  }
});

test('an out-of-order refund is retried and applied after its Checkout payment arrives', async () => {
  const env = environment();
  try {
    const refund = stripeEvent({
      id: 'evt_refund_before_checkout',
      type: 'charge.refunded',
      object: {
        id: 'ch_refund_before_checkout',
        object: 'charge',
        payment_intent: 'pi_refund_before_checkout',
        amount: 990,
        amount_refunded: 990,
        refunded: true,
        currency: 'eur',
        metadata: {},
      },
    });

    const early = await deliver(env, refund);
    assert.equal(early.response.status, 503);
    assert.equal(early.body.error, 'payment_not_recorded');
    assert.deepEqual(
      { ...row(env, 'SELECT status, attempt_count FROM stripe_events WHERE event_id = ?', refund.id) },
      { status: 'FAILED', attempt_count: 1 },
    );

    await pay(env, {
      eventId: 'evt_checkout_after_refund',
      sessionId: 'cs_checkout_after_refund',
      paymentIntentId: 'pi_refund_before_checkout',
    });
    const retried = await deliver(env, refund);
    assert.equal(retried.response.status, 200);
    assert.equal(retried.body.revoked, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REFUNDED');
    assert.deepEqual(
      { ...row(env, 'SELECT status, attempt_count FROM stripe_events WHERE event_id = ?', refund.id) },
      { status: 'PROCESSED', attempt_count: 2 },
    );
  } finally {
    env.DB.close();
  }
});

test('an out-of-order dispute with no metadata is retried and later correlated by PaymentIntent', async () => {
  const env = environment();
  try {
    const dispute = stripeEvent({
      id: 'evt_dispute_before_checkout',
      type: 'charge.dispute.created',
      object: {
        id: 'dp_before_checkout',
        object: 'dispute',
        charge: 'ch_dispute_before_checkout',
        payment_intent: 'pi_dispute_before_checkout',
        amount: 990,
        currency: 'eur',
        status: 'needs_response',
        metadata: {},
      },
    });

    const early = await deliver(env, dispute);
    assert.equal(early.response.status, 503);
    await pay(env, {
      eventId: 'evt_checkout_after_dispute',
      sessionId: 'cs_checkout_after_dispute',
      paymentIntentId: 'pi_dispute_before_checkout',
    });
    const retried = await deliver(env, dispute);
    assert.equal(retried.response.status, 200);
    assert.equal(retried.body.revoked, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
  } finally {
    env.DB.close();
  }
});

test('a dispute correlates by persisted PaymentIntent even with empty dispute metadata', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_dispute_purchase',
      sessionId: 'cs_dispute_purchase',
      paymentIntentId: 'pi_dispute_purchase',
    });
    const dispute = stripeEvent({
      id: 'evt_dispute_001',
      type: 'charge.dispute.created',
      object: {
        id: 'dp_001',
        object: 'dispute',
        charge: 'ch_dispute_purchase',
        payment_intent: 'pi_dispute_purchase',
        amount: 990,
        currency: 'eur',
        status: 'needs_response',
        metadata: {},
      },
    });
    const result = await deliver(env, dispute);
    assert.equal(result.response.status, 200);
    assert.equal(result.body.revoked, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
    const payment = row(env, 'SELECT status, dispute_id FROM payments WHERE payment_intent_id = ?', 'pi_dispute_purchase');
    assert.deepEqual({ ...payment }, { status: 'DISPUTED', dispute_id: 'dp_001' });
  } finally {
    env.DB.close();
  }
});

test('a dispute with a nullable PaymentIntent resolves its Charge through Stripe', async (t) => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_dispute_lookup_purchase',
      sessionId: 'cs_dispute_lookup_purchase',
      paymentIntentId: 'pi_dispute_lookup_purchase',
    });
    const fetchMock = t.mock.method(globalThis, 'fetch', async (url, options) => {
      assert.equal(url, 'https://api.stripe.com/v1/charges/ch_dispute_lookup');
      assert.equal(options.method, 'GET');
      assert.equal(options.headers.authorization, `Bearer ${env.STRIPE_SECRET_KEY}`);
      return Response.json({
        id: 'ch_dispute_lookup',
        object: 'charge',
        payment_intent: 'pi_dispute_lookup_purchase',
        metadata: { product_id: LICENSE_PRODUCT.id },
      });
    });

    const result = await deliver(env, stripeEvent({
      id: 'evt_dispute_lookup',
      type: 'charge.dispute.created',
      object: {
        id: 'dp_dispute_lookup',
        object: 'dispute',
        charge: 'ch_dispute_lookup',
        payment_intent: null,
        amount: 990,
        currency: 'eur',
        status: 'needs_response',
        metadata: {},
      },
    }));

    assert.equal(fetchMock.mock.callCount(), 1);
    assert.equal(result.response.status, 200);
    assert.equal(result.body.revoked, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
    const payment = row(
      env,
      'SELECT status, charge_id, dispute_id FROM payments WHERE payment_intent_id = ?',
      'pi_dispute_lookup_purchase',
    );
    assert.deepEqual(
      { ...payment },
      { status: 'DISPUTED', charge_id: 'ch_dispute_lookup', dispute_id: 'dp_dispute_lookup' },
    );
  } finally {
    env.DB.close();
  }
});

test('a dispute with a nullable PaymentIntent uses a locally known Charge without a network call', async (t) => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_dispute_local_purchase',
      sessionId: 'cs_dispute_local_purchase',
      paymentIntentId: 'pi_dispute_local_purchase',
    });
    env.DB.prepare('UPDATE payments SET charge_id = ? WHERE payment_intent_id = ?')
      .bind('ch_dispute_local', 'pi_dispute_local_purchase')
      .run();
    const fetchMock = t.mock.method(globalThis, 'fetch', async () => {
      throw new Error('network must not be used');
    });

    const result = await deliver(env, stripeEvent({
      id: 'evt_dispute_local',
      type: 'charge.dispute.created',
      object: {
        id: 'dp_dispute_local',
        object: 'dispute',
        charge: 'ch_dispute_local',
        payment_intent: null,
        amount: 990,
        currency: 'eur',
        status: 'needs_response',
        metadata: {},
      },
    }));

    assert.equal(fetchMock.mock.callCount(), 0);
    assert.equal(result.response.status, 200);
    assert.equal(result.body.revoked, true);
  } finally {
    env.DB.close();
  }
});

test('a won dispute restores the current purchase that it suspended', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_dispute_won_purchase',
      sessionId: 'cs_dispute_won_purchase',
      paymentIntentId: 'pi_dispute_won_purchase',
    });
    const opened = await deliver(env, stripeEvent({
      id: 'evt_dispute_won_opened',
      type: 'charge.dispute.created',
      object: disputeObject({
        id: 'dp_dispute_won',
        chargeId: 'ch_dispute_won',
        paymentIntentId: 'pi_dispute_won_purchase',
      }),
    }));
    const closed = await deliver(env, stripeEvent({
      id: 'evt_dispute_won_closed',
      type: 'charge.dispute.closed',
      created: Date.parse('2026-08-09T12:00:00Z') / 1000,
      object: disputeObject({
        id: 'dp_dispute_won',
        chargeId: 'ch_dispute_won',
        paymentIntentId: 'pi_dispute_won_purchase',
        status: 'won',
      }),
    }));

    assert.equal(opened.body.revoked, true);
    assert.equal(closed.response.status, 200);
    assert.equal(closed.body.status, 'won');
    assert.equal(closed.body.restored, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_dispute_won_purchase').status,
      'PAID',
    );
    assert.deepEqual(
      { ...row(env, 'SELECT status, suspended_entitlement FROM payment_disputes WHERE dispute_id = ?', 'dp_dispute_won') },
      { status: 'won', suspended_entitlement: 1 },
    );
  } finally {
    env.DB.close();
  }
});

test('a lost dispute keeps the current purchase revoked', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_dispute_lost_purchase',
      sessionId: 'cs_dispute_lost_purchase',
      paymentIntentId: 'pi_dispute_lost_purchase',
    });
    await deliver(env, stripeEvent({
      id: 'evt_dispute_lost_opened',
      type: 'charge.dispute.created',
      object: disputeObject({
        id: 'dp_dispute_lost',
        chargeId: 'ch_dispute_lost',
        paymentIntentId: 'pi_dispute_lost_purchase',
      }),
    }));
    const closed = await deliver(env, stripeEvent({
      id: 'evt_dispute_lost_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_dispute_lost',
        chargeId: 'ch_dispute_lost',
        paymentIntentId: 'pi_dispute_lost_purchase',
        status: 'lost',
      }),
    }));

    assert.equal(closed.body.status, 'lost');
    assert.equal(closed.body.restored, false);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_dispute_lost_purchase').status,
      'DISPUTED',
    );
  } finally {
    env.DB.close();
  }
});

test('all disputes for a payment must clear before warning_closed restores access', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_multiple_disputes_purchase',
      sessionId: 'cs_multiple_disputes_purchase',
      paymentIntentId: 'pi_multiple_disputes_purchase',
    });
    for (const suffix of ['a', 'b']) {
      await deliver(env, stripeEvent({
        id: `evt_multiple_dispute_${suffix}_opened`,
        type: 'charge.dispute.created',
        object: disputeObject({
          id: `dp_multiple_${suffix}`,
          chargeId: 'ch_multiple_disputes',
          paymentIntentId: 'pi_multiple_disputes_purchase',
          amountMinor: 495,
        }),
      }));
    }

    const firstClosed = await deliver(env, stripeEvent({
      id: 'evt_multiple_dispute_a_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_multiple_a',
        chargeId: 'ch_multiple_disputes',
        paymentIntentId: 'pi_multiple_disputes_purchase',
        amountMinor: 495,
        status: 'won',
      }),
    }));
    assert.equal(firstClosed.body.restored, false);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');

    const secondClosed = await deliver(env, stripeEvent({
      id: 'evt_multiple_dispute_b_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_multiple_b',
        chargeId: 'ch_multiple_disputes',
        paymentIntentId: 'pi_multiple_disputes_purchase',
        amountMinor: 495,
        status: 'warning_closed',
      }),
    }));
    assert.equal(secondClosed.body.status, 'warning_closed');
    assert.equal(secondClosed.body.restored, true);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
  } finally {
    env.DB.close();
  }
});

test('a closed event delivered before created cannot later revoke the purchase', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_out_of_order_close_purchase',
      sessionId: 'cs_out_of_order_close_purchase',
      paymentIntentId: 'pi_out_of_order_close_purchase',
    });
    const closed = await deliver(env, stripeEvent({
      id: 'evt_out_of_order_dispute_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_out_of_order_closed',
        chargeId: 'ch_out_of_order_closed',
        paymentIntentId: 'pi_out_of_order_close_purchase',
        status: 'won',
      }),
    }));
    const openedLate = await deliver(env, stripeEvent({
      id: 'evt_out_of_order_dispute_opened_late',
      type: 'charge.dispute.created',
      object: disputeObject({
        id: 'dp_out_of_order_closed',
        chargeId: 'ch_out_of_order_closed',
        paymentIntentId: 'pi_out_of_order_close_purchase',
      }),
    }));

    assert.equal(closed.body.restored, false);
    assert.equal(openedLate.body.revoked, false);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
    assert.equal(
      row(env, 'SELECT status FROM payment_disputes WHERE dispute_id = ?', 'dp_out_of_order_closed').status,
      'won',
    );
  } finally {
    env.DB.close();
  }
});

test('a later administrative revocation blocks dispute restoration', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_admin_block_purchase',
      sessionId: 'cs_admin_block_purchase',
      paymentIntentId: 'pi_admin_block_purchase',
    });
    await deliver(env, stripeEvent({
      id: 'evt_admin_block_dispute_opened',
      type: 'charge.dispute.created',
      object: disputeObject({
        id: 'dp_admin_block',
        chargeId: 'ch_admin_block',
        paymentIntentId: 'pi_admin_block_purchase',
      }),
    }));
    env.DB.prepare(
      "INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, 'revoked', 'support', ?)",
    )
      .bind(DEVICE_A, '2026-08-09T12:00:00.000Z')
      .run();

    const closed = await deliver(env, stripeEvent({
      id: 'evt_admin_block_dispute_closed',
      type: 'charge.dispute.closed',
      object: disputeObject({
        id: 'dp_admin_block',
        chargeId: 'ch_admin_block',
        paymentIntentId: 'pi_admin_block_purchase',
        status: 'won',
      }),
    }));

    assert.equal(closed.body.restored, false);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'REVOKED');
    assert.equal(
      row(env, 'SELECT status FROM payments WHERE payment_intent_id = ?', 'pi_admin_block_purchase').status,
      'PAID',
    );
  } finally {
    env.DB.close();
  }
});

test('an older Checkout event cannot replace a newer current purchase', async () => {
  const env = environment();
  try {
    const newer = await pay(env, {
      eventId: 'evt_newer_delivered_first',
      sessionId: 'cs_newer_delivered_first',
      paymentIntentId: 'pi_newer_delivered_first',
      created: Date.parse('2026-08-10T12:00:00Z') / 1000,
    });
    const older = await pay(env, {
      eventId: 'evt_older_delivered_last',
      sessionId: 'cs_older_delivered_last',
      paymentIntentId: 'pi_older_delivered_last',
      created: Date.parse('2026-08-08T12:00:00Z') / 1000,
    });

    assert.equal(newer.body.activated, true);
    assert.equal(older.body.activated, false);
    assert.equal(older.body.superseded, true);
    const device = row(
      env,
      'SELECT status, stripe_session_id, purchased_at FROM devices WHERE device_id = ?',
      DEVICE_A,
    );
    assert.deepEqual(
      { ...device },
      {
        status: 'ACTIVE',
        stripe_session_id: 'cs_newer_delivered_first',
        purchased_at: '2026-08-10T12:00:00.000Z',
      },
    );
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM payments WHERE status = 'PAID'"), 2);
  } finally {
    env.DB.close();
  }
});

test('reversing an older payment does not revoke a newer current purchase', async () => {
  const env = environment();
  try {
    await pay(env, {
      eventId: 'evt_old_purchase',
      sessionId: 'cs_old_purchase',
      paymentIntentId: 'pi_old_purchase',
      created: Date.parse('2026-08-08T12:00:00Z') / 1000,
    });
    await pay(env, {
      eventId: 'evt_new_purchase',
      sessionId: 'cs_new_purchase',
      paymentIntentId: 'pi_new_purchase',
      created: Date.parse('2026-08-10T12:00:00Z') / 1000,
    });

    const refund = stripeEvent({
      id: 'evt_old_refund',
      type: 'charge.refunded',
      object: {
        id: 'ch_old_purchase',
        payment_intent: 'pi_old_purchase',
        amount: 990,
        amount_refunded: 990,
        refunded: true,
        currency: 'eur',
        metadata: {},
      },
    });
    const reversal = await deliver(env, refund);

    const device = row(env, 'SELECT status, stripe_session_id FROM devices WHERE device_id = ?', DEVICE_A);
    assert.equal(reversal.body.revoked, false);
    assert.deepEqual({ ...device }, { status: 'ACTIVE', stripe_session_id: 'cs_new_purchase' });
  } finally {
    env.DB.close();
  }
});

test('the phone portal redeems only for an identity the app already pinned', async () => {
  const env = environment();
  try {
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('PORT-AL12', 30, '2026-08-08T12:00:00Z')`,
    ).run();
    const portalRequest = (device) => new Request('https://local.test/ativar', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ device, key: 'PORT-AL12', lang: 'pt' }),
    });

    const unknown = await worker.fetch(portalRequest(DEVICE_A), env);
    assert.equal(unknown.status, 400);
    assert.equal(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'PORT-AL12'").redeemed_by, null);

    await registerDevice(env, deviceIdentityA);
    const accepted = await worker.fetch(portalRequest(DEVICE_A), env);
    assert.equal(accepted.status, 200);
    assert.equal(row(env, 'SELECT status FROM devices WHERE device_id = ?', DEVICE_A).status, 'ACTIVE');
    assert.equal(
      row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'PORT-AL12'").redeemed_by,
      DEVICE_A,
    );
  } finally {
    env.DB.close();
  }
});

test('the API cannot redeem from a public code without a possession proof', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('NO-PROOF', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const response = await worker.fetch(
      postJson('/v1/redeem', { deviceId: DEVICE_A, key: 'NO-PROOF' }),
      env,
    );
    assert.equal(response.status, 400);
    assert.equal(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'NO-PROOF'").redeemed_by, null);
  } finally {
    env.DB.close();
  }
});

test('a redemption key is single-use under concurrent requests', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    await registerDevice(env, deviceIdentityB);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('ABCD-EFGH', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const [proofA, proofB] = await Promise.all([
      deviceProofBody(deviceIdentityA, 'redeem'),
      deviceProofBody(deviceIdentityB, 'redeem'),
    ]);
    const responses = await Promise.all([
      worker.fetch(postJson('/v1/redeem', { ...proofA, key: 'ABCD-EFGH' }), env),
      worker.fetch(postJson('/v1/redeem', { ...proofB, key: 'ABCD-EFGH' }), env),
    ]);
    assert.deepEqual(responses.map((response) => response.status).sort(), [200, 409]);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM devices WHERE status = 'ACTIVE'"), 1);
    assert.equal(count(env, "SELECT COUNT(*) AS n FROM events WHERE kind = 'redeemed'"), 1);
    assert.ok(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'ABCD-EFGH'").redeemed_by);
  } finally {
    env.DB.close();
  }
});

/**
 * The admin panel must not be indexable.
 *
 * It is a login box at a fixed, guessable path. Nothing behind it is reachable without the token,
 * so appearing in a search result is not a breach — but a panel that turns up in a search for the
 * product is an invitation to sit and try the box, and there is no reason to extend that invitation.
 */
test('the admin page asks search engines to stay away', async () => {
  const env = environment();
  try {
    const response = await worker.fetch(new Request('https://local.test/admin'), env);

    assert.equal(response.status, 200);
    const robots = response.headers.get('x-robots-tag') ?? '';
    assert.match(robots, /noindex/);
    assert.match(robots, /nofollow/);
  } finally {
    env.DB.close();
  }
});

test('an authenticated device refreshes support-safe model information on validation', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    const body = {
      ...await deviceProofBody(deviceIdentityA, 'validate'),
      deviceProfile: {
        deviceType: 'ANDROID_TV',
        platform: 'ANDROID',
        manufacturer: 'Samsung\nElectronics',
        model: 'QN90D',
        osVersion: 'Android 14 (API 34)',
        appVersion: '0.2.0-alpha.9',
        serialNumber: 'must-not-be-stored',
      },
    };
    const response = await worker.fetch(postJson('/v1/validate', body), env);
    assert.equal(response.status, 200);

    const stored = row(
      env,
      `SELECT device_type, platform, manufacturer, model, os_version, app_version, last_seen_at
       FROM devices WHERE device_id = ?`,
      DEVICE_A,
    );
    assert.deepEqual(
      { ...stored, last_seen_at: Boolean(stored.last_seen_at) },
      {
        device_type: 'ANDROID_TV',
        platform: 'ANDROID',
        manufacturer: 'Samsung Electronics',
        model: 'QN90D',
        os_version: 'Android 14 (API 34)',
        app_version: '0.2.0-alpha.9',
        last_seen_at: true,
      },
    );
    assert.equal(
      env.DB.database.prepare("SELECT COUNT(*) AS n FROM pragma_table_info('devices') WHERE name = 'serialNumber'").get().n,
      0,
    );
  } finally {
    env.DB.close();
  }
});

/** The panel grants licences, so a browser must refuse plain http to it after the first visit. */
test('html responses carry HSTS', async () => {
  const env = environment();
  try {
    const response = await worker.fetch(new Request('https://local.test/admin'), env);

    assert.match(response.headers.get('strict-transport-security') ?? '', /max-age=\d+/);
  } finally {
    env.DB.close();
  }
});

/**
 * The page itself is public; everything that reads or writes data is not.
 *
 * Worth pinning rather than assuming: the panel is one route away from the tables that decide who
 * has paid, and "the login box is served to anyone" must never quietly become "the data is too".
 */
test('every admin data route refuses an unauthenticated caller', async () => {
  const env = environment();
  try {
    const paths = [
      '/admin/summary', '/admin/search', '/admin/list', '/admin/device',
      '/admin/archive', '/admin/restore', '/admin/keys',
    ];
    for (const path of paths) {
      const response = await worker.fetch(new Request(`https://local.test${path}`), env);
      assert.equal(response.status, 401, `${path} should refuse an anonymous caller`);
    }
  } finally {
    env.DB.close();
  }
});

/**
 * The buyer can redeem their own key again.
 *
 * A customer reinstalled, their device id changed, and the key they had paid for came back
 * "already used" — against their own licence, bought hours earlier. The only way out was to buy it
 * a second time. Re-redeeming must extend the licence they already own.
 */
test('the device that owns a key may redeem it again', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('OWN-KEY', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const first = await worker.fetch(
      postJson('/v1/redeem', { ...(await deviceProofBody(deviceIdentityA, 'redeem')), key: 'OWN-KEY' }),
      env,
    );
    assert.equal(first.status, 200);

    const second = await worker.fetch(
      postJson('/v1/redeem', { ...(await deviceProofBody(deviceIdentityA, 'redeem')), key: 'OWN-KEY' }),
      env,
    );

    assert.equal(second.status, 200, 'the owner must not be locked out of their own key');
    assert.equal(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'OWN-KEY'").redeemed_by, DEVICE_A);
    assert.equal(row(env, `SELECT status FROM devices WHERE device_id = '${DEVICE_A}'`).status, 'ACTIVE');
  } finally {
    env.DB.close();
  }
});

/**
 * And nobody else may, which is the property the key is sold on.
 *
 * Worth pinning beside the test above: the change that lets the owner back in must never become a
 * change that lets a second machine in. A key posted publicly still unlocks exactly one device.
 */
test('a second device is still refused a key that another device owns', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    await registerDevice(env, deviceIdentityB);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('MINE-ONLY', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const owner = await worker.fetch(
      postJson('/v1/redeem', { ...(await deviceProofBody(deviceIdentityA, 'redeem')), key: 'MINE-ONLY' }),
      env,
    );
    assert.equal(owner.status, 200);

    const intruder = await worker.fetch(
      postJson('/v1/redeem', { ...(await deviceProofBody(deviceIdentityB, 'redeem')), key: 'MINE-ONLY' }),
      env,
    );

    assert.equal(intruder.status, 409);
    assert.equal(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'MINE-ONLY'").redeemed_by, DEVICE_A);
    assert.equal(row(env, `SELECT status FROM devices WHERE device_id = '${DEVICE_B}'`).status, 'TRIAL');
  } finally {
    env.DB.close();
  }
});

/**
 * The activation screen can describe a key before spending it.
 *
 * It used to say nothing: a customer pasted a code and learned only whether it worked. The three
 * states below are what they actually need — how many days it grants, and whether it is free,
 * already theirs, or held by another machine.
 */
test('key info reports days and availability without redeeming', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('LOOK-ONLY', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const response = await worker.fetch(
      postJson('/v1/key-info', { ...(await deviceProofBody(deviceIdentityA, 'validate')), key: 'LOOK-ONLY' }),
      env,
    );

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.state, 'available');
    assert.equal(body.grantDays, 30);
    // Looking must not spend it.
    assert.equal(row(env, "SELECT redeemed_by FROM redemption_keys WHERE key_code = 'LOOK-ONLY'").redeemed_by, null);
  } finally {
    env.DB.close();
  }
});

/** The customer's own key reads as theirs, not as unavailable. */
test('key info tells the owner the key is theirs', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, redeemed_by, created_at)
       VALUES ('IS-MINE', 30, '${DEVICE_A}', '2026-08-08T12:00:00Z')`,
    ).run();

    const response = await worker.fetch(
      postJson('/v1/key-info', { ...(await deviceProofBody(deviceIdentityA, 'validate')), key: 'IS-MINE' }),
      env,
    );

    assert.equal((await response.json()).state, 'yours');
  } finally {
    env.DB.close();
  }
});

/**
 * Another device's key reads as in use, and never says whose.
 *
 * The owning device id is somebody else's business: returning it would turn a mistyped code into a
 * disclosure about another customer.
 */
test('key info never reveals which device holds a key', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    await registerDevice(env, deviceIdentityB);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, redeemed_by, created_at)
       VALUES ('SOMEONE-ELSE', 30, '${DEVICE_B}', '2026-08-08T12:00:00Z')`,
    ).run();

    const response = await worker.fetch(
      postJson('/v1/key-info', { ...(await deviceProofBody(deviceIdentityA, 'validate')), key: 'SOMEONE-ELSE' }),
      env,
    );

    const raw = await response.text();
    assert.equal(JSON.parse(raw).state, 'in_use');
    assert.equal(raw.includes(DEVICE_B), false, 'the owning device must never be disclosed');
  } finally {
    env.DB.close();
  }
});

/** Without a signed proof this would be an oracle for guessing keys. */
test('key info refuses a caller with no device proof', async () => {
  const env = environment();
  try {
    await registerDevice(env, deviceIdentityA);
    env.DB.prepare(
      `INSERT INTO redemption_keys (key_code, grant_days, created_at)
       VALUES ('GUESS-ME', 30, '2026-08-08T12:00:00Z')`,
    ).run();

    const response = await worker.fetch(
      postJson('/v1/key-info', { deviceId: DEVICE_A, key: 'GUESS-ME' }),
      env,
    );

    assert.equal(response.status, 400);
  } finally {
    env.DB.close();
  }
});
