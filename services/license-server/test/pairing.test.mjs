/**
 * Pairing a television with a phone.
 *
 * Exercises the real fetch handler against Node's in-memory SQLite, like the other Worker tests:
 * no Cloudflare account, no network. What is being checked is mostly what must *not* happen — a
 * payload delivered twice, a code guessed cheaply, a secret readable from the database.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import { PAIRING_INTERNALS } from '../src/pairing.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const PAIRING_MIGRATION = readFileSync(
  new URL('../migrations/0009_device_pairing.sql', import.meta.url),
  'utf8',
);

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
  constructor() {
    this.database = new DatabaseSync(':memory:');
    this.database.exec(SCHEMA);
    this.database.exec(PAIRING_MIGRATION);
  }

  prepare(sql) {
    return new LocalD1Statement(this.database, sql);
  }

  close() {
    this.database.close();
  }
}

function createEnv() {
  return { DB: new LocalD1() };
}

function post(path, body) {
  return new Request(`https://iptvburo.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function startPairing(env, kind = 'tmdb_key') {
  const response = await worker.fetch(post('/v1/pair/start', { kind }), env);
  assert.equal(response.status, 200);
  return await response.json();
}

test('a television is given a six digit code it can read out loud', async () => {
  const env = createEnv();
  const started = await startPairing(env);
  assert.match(started.code, /^[0-9]{6}$/);
  assert.equal(started.kind, 'tmdb_key');
  assert.equal(started.expiresInSeconds, PAIRING_INTERNALS.TTL_SECONDS);
  env.DB.close();
});

test('a kind the app does not offer is refused before a row exists', async () => {
  const env = createEnv();
  const response = await worker.fetch(post('/v1/pair/start', { kind: 'arbitrary' }), env);
  assert.equal(response.status, 400);
  const rows = env.DB.prepare('SELECT COUNT(*) AS total FROM pairing_requests').bind().first();
  assert.equal(rows.total, 0);
  env.DB.close();
});

test('the phone sends a key and the television collects it once', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);

  const submitted = await worker.fetch(
    post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'synthetic-tmdb-key' }),
    env,
  );
  assert.equal(submitted.status, 200);

  const claimed = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal(claimed.status, 200);
  const body = await claimed.json();
  assert.equal(body.status, 'ready');
  assert.equal(body.kind, 'tmdb_key');
  assert.equal(body.payload, 'synthetic-tmdb-key');

  // Delivered once. A second reader must find nothing, not the same secret again.
  const again = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal(again.status, 404);
  env.DB.close();
});

test('the row is gone the moment it is delivered', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'k' }), env);
  await worker.fetch(post('/v1/pair/claim', { code }), env);
  const rows = env.DB.prepare('SELECT COUNT(*) AS total FROM pairing_requests').bind().first();
  assert.equal(rows.total, 0);
  env.DB.close();
});

test('polling before the phone has sent anything is not an error', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  const response = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).status, 'pending');
  env.DB.close();
});

test('neither the code nor the payload is readable from the database', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  const secret = 'eyJhbGciOiJIUzI1NiJ9.synthetic.token';
  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: secret }), env);

  const row = env.DB.prepare('SELECT * FROM pairing_requests').bind().first();
  const dump = JSON.stringify(row);
  assert.equal(dump.includes(secret), false, 'the payload must not be stored in the clear');
  assert.equal(dump.includes(code), false, 'the code is the key material and must not be stored');
  assert.equal(row.code.length > 6, true, 'the lookup key is a hash, not the code');
  env.DB.close();
});

test('a wrong code cannot read a payload waiting for a different one', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'secret' }), env);

  const wrong = String((Number(code) + 1) % 1000000).padStart(6, '0');
  const response = await worker.fetch(post('/v1/pair/claim', { code: wrong }), env);
  assert.equal(response.status, 404);

  // And the real code still works: a failed guess must not consume the payload.
  const claimed = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal((await claimed.json()).payload, 'secret');
  env.DB.close();
});

test('a second phone cannot overwrite a payload already waiting', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'first' }), env);
  const second = await worker.fetch(
    post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'second' }),
    env,
  );
  assert.equal(second.status, 409);

  const claimed = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal((await claimed.json()).payload, 'first');
  env.DB.close();
});

test('a television waiting for a key is not handed a title to play', async () => {
  const env = createEnv();
  const { code } = await startPairing(env, 'tmdb_key');
  const response = await worker.fetch(
    post('/v1/pair/submit', { code, kind: 'open_title', payload: 'movie:42' }),
    env,
  );
  assert.equal(response.status, 409);

  // Counted as an attempt, because probing somebody else's code looks exactly like this.
  const row = env.DB.prepare('SELECT attempts FROM pairing_requests').bind().first();
  assert.equal(row.attempts, 1);
  env.DB.close();
});

test('guessing runs out of attempts long before the code space does', async () => {
  const env = createEnv();
  const { code } = await startPairing(env, 'tmdb_key');
  for (let attempt = 0; attempt < PAIRING_INTERNALS.MAX_ATTEMPTS; attempt += 1) {
    await worker.fetch(post('/v1/pair/submit', { code, kind: 'open_title', payload: 'x' }), env);
  }
  const response = await worker.fetch(
    post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'real' }),
    env,
  );
  assert.equal(response.status, 429);
  env.DB.close();
});

test('an expired code is refused and swept away', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  env.DB
    .prepare('UPDATE pairing_requests SET expires_at = ?1')
    .bind(new Date(Date.now() - 1000).toISOString())
    .run();

  const response = await worker.fetch(
    post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'late' }),
    env,
  );
  assert.equal(response.status, 404);
  const rows = env.DB.prepare('SELECT COUNT(*) AS total FROM pairing_requests').bind().first();
  assert.equal(rows.total, 0, 'expired rows do not accumulate');
  env.DB.close();
});

test('a payload larger than any real credential is refused', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  const response = await worker.fetch(
    post('/v1/pair/submit', {
      code,
      kind: 'tmdb_key',
      payload: 'x'.repeat(PAIRING_INTERNALS.MAX_PAYLOAD_LENGTH + 1),
    }),
    env,
  );
  assert.equal(response.status, 400);
  env.DB.close();
});

test('a malformed code never reaches the database', async () => {
  const env = createEnv();
  for (const code of ['', '12345', '1234567', 'abcdef', "1' OR '1'='1"]) {
    const response = await worker.fetch(
      post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'x' }),
      env,
    );
    assert.equal(response.status, 400, `refused: ${code}`);
  }
  env.DB.close();
});

test('the v4 token that motivated this fits through intact', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  // The shape of a TMDb v4 read token: three base64url segments, 239 characters in practice.
  const token = `${'e'.repeat(20)}.${'y'.repeat(180)}.${'s'.repeat(37)}`;
  assert.equal(token.length, 239);

  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: token }), env);
  const claimed = await worker.fetch(post('/v1/pair/claim', { code }), env);
  assert.equal((await claimed.json()).payload, token);
  env.DB.close();
});

test('two televisions polling the same code do not both walk away with it', async () => {
  const env = createEnv();
  const { code } = await startPairing(env);
  await worker.fetch(post('/v1/pair/submit', { code, kind: 'tmdb_key', payload: 'once' }), env);

  const [first, second] = await Promise.all([
    worker.fetch(post('/v1/pair/claim', { code }), env),
    worker.fetch(post('/v1/pair/claim', { code }), env),
  ]);
  const bodies = [await first.json(), await second.json()];
  const delivered = bodies.filter((body) => body.status === 'ready');
  assert.equal(delivered.length, 1, 'exactly one reader receives the payload');
  env.DB.close();
});

test('codes are drawn without the bias a modulo would introduce', async () => {
  // Not a statistical test: rejection sampling is the thing being asserted, and what is checkable
  // cheaply is that the generator spans the range and does not repeat itself trivially.
  const seen = new Set();
  for (let draw = 0; draw < 500; draw += 1) {
    const code = PAIRING_INTERNALS.generateCode();
    assert.match(code, /^[0-9]{6}$/);
    seen.add(code);
  }
  assert.equal(seen.size > 450, true, 'draws are not collapsing onto a few values');
});

test('the payload key comes from the code, so the wrong code decrypts nothing', async () => {
  const encrypted = await PAIRING_INTERNALS.encryptPayload('123456', 'secret');
  const right = await PAIRING_INTERNALS.decryptPayload('123456', encrypted.payload, encrypted.nonce);
  const wrong = await PAIRING_INTERNALS.decryptPayload('654321', encrypted.payload, encrypted.nonce);
  assert.equal(right, 'secret');
  assert.equal(wrong, null);
});

test('each row gets its own nonce', async () => {
  const first = await PAIRING_INTERNALS.encryptPayload('123456', 'secret');
  const second = await PAIRING_INTERNALS.encryptPayload('123456', 'secret');
  assert.notEqual(first.nonce, second.nonce, 'a reused nonce with a related key is the failure');
  assert.notEqual(first.payload, second.payload);
});
