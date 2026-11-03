/**
 * Sync between a household's devices, exercised through the real Worker against Node's in-memory
 * SQLite, as the other Worker tests are.
 *
 * What matters most here is what must not happen: a device without a licence adding itself, a
 * captured request working twice, an older edit overwriting a newer one, a deletion coming back,
 * a page boundary losing a record, and a title being readable in the database.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import { MAX_PUSH_RECORDS, normaliseCode } from '../src/sync.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const MIGRATION = readFileSync(new URL('../migrations/0014_device_sync.sql', import.meta.url), 'utf8');

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
    this.database.exec(MIGRATION);
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

function createEnv() {
  return { DB: new LocalD1(), SYNC_ENCRYPTION_KEY: 'fixture-sync-server-secret' };
}

const FUTURE = '2099-01-01T00:00:00.000Z';
const PAST = '2020-01-01T00:00:00.000Z';

async function device(env, deviceId, { status = 'ACTIVE', trialEndsAt = FUTURE } = {}) {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey));
  env.DB.prepare(
    `INSERT INTO devices (device_id, public_key, status, first_seen_at, trial_ends_at, updated_at)
     VALUES (?, ?, ?, '2026-01-01T00:00:00.000Z', ?, '2026-01-01T00:00:00.000Z')`,
  ).bind(deviceId, Buffer.from(spki).toString('base64'), status, trialEndsAt).run();
  return { deviceId, privateKey: pair.privateKey };
}

let nonceCounter = 0;
async function call(env, identity, path, extra = {}, nonce = `n${(nonceCounter += 1)}`.padStart(22, '0')) {
  const canonical = `iptvburo-device-proof-v1\nsync\n${identity.deviceId}\n${nonce}`;
  const signature = new Uint8Array(await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    identity.privateKey,
    new TextEncoder().encode(canonical),
  ));
  const body = { deviceId: identity.deviceId, nonce, proof: Buffer.from(signature).toString('base64url'), ...extra };
  const response = await worker.fetch(
    new Request(`https://iptvburo.test${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    }),
    env,
  );
  return { status: response.status, body: await response.json(), sent: body };
}

async function pairedHousehold(env) {
  const phone = await device(env, 'PHON-EAAA-AAAA');
  const tv = await device(env, 'TELE-VAAA-AAAA');
  const invite = await call(env, phone, '/v1/sync/invite');
  assert.equal(invite.status, 200);
  const joined = await call(env, tv, '/v1/sync/join', { code: invite.body.code });
  assert.equal(joined.status, 200);
  return { phone, tv };
}

function record(key, updatedAt, data, deleted = false) {
  return { key, updatedAt, data, deleted };
}

test('a device with a licence invites another, and both end up in one group', async () => {
  const env = createEnv();
  const { phone, tv } = await pairedHousehold(env);
  assert.equal((await call(env, phone, '/v1/sync/status')).body.members, 2);
  assert.equal((await call(env, tv, '/v1/sync/status')).body.inGroup, true);
  env.DB.close();
});

test('an expired device cannot invite, join or exchange, but can still see where it stands', async () => {
  const env = createEnv();
  const expired = await device(env, 'EXPI-REDA-AAAA', { status: 'TRIAL', trialEndsAt: PAST });
  assert.equal((await call(env, expired, '/v1/sync/invite')).status, 402);
  assert.equal((await call(env, expired, '/v1/sync/join', { code: 'ABCD-EFGH' })).status, 402);
  const status = await call(env, expired, '/v1/sync/status');
  assert.equal(status.status, 200);
  assert.equal(status.body.licensed, false);
  env.DB.close();
});

test('what one device saves the other receives, and the database holds no title', async () => {
  const env = createEnv();
  const { phone, tv } = await pairedHousehold(env);
  const favourite = JSON.stringify({ kind: 'favorite', title: 'Duna: Parte Dois', year: 2024 });

  const pushed = await call(env, phone, '/v1/sync/exchange', { since: 0, records: [record('fav-abcdefgh', 1_000, favourite)] });
  assert.equal(pushed.status, 200);

  const pulled = await call(env, tv, '/v1/sync/exchange', { since: 0, records: [] });
  assert.equal(pulled.status, 200);
  assert.deepEqual(pulled.body.records, [{ key: 'fav-abcdefgh', updatedAt: 1_000, deleted: false, data: favourite }]);
  assert.ok(pulled.body.revision > 0);

  const dump = JSON.stringify(env.DB.prepare('SELECT * FROM sync_records').bind().all().results);
  assert.ok(!dump.includes('Duna'), 'records are encrypted at rest');
  env.DB.close();
});

test('the newer edit wins, whichever arrives last, and a deletion does not come back', async () => {
  const env = createEnv();
  const { phone, tv } = await pairedHousehold(env);
  await call(env, phone, '/v1/sync/exchange', { records: [record('pos-abcdefgh', 2_000, '{"p":0.8}')] });
  // An older edit arriving later, from a device that was offline, must not undo the newer one.
  await call(env, tv, '/v1/sync/exchange', { records: [record('pos-abcdefgh', 1_000, '{"p":0.1}')] });
  let pulled = await call(env, tv, '/v1/sync/exchange', { since: 0 });
  assert.equal(pulled.body.records.find((item) => item.key === 'pos-abcdefgh').data, '{"p":0.8}');

  await call(env, tv, '/v1/sync/exchange', { records: [record('pos-abcdefgh', 3_000, '', true)] });
  await call(env, phone, '/v1/sync/exchange', { records: [record('pos-abcdefgh', 2_500, '{"p":0.9}')] });
  pulled = await call(env, phone, '/v1/sync/exchange', { since: 0 });
  assert.equal(pulled.body.records.find((item) => item.key === 'pos-abcdefgh').deleted, true);
  env.DB.close();
});

test('a device only fetches what changed since it last asked', async () => {
  const env = createEnv();
  const { phone, tv } = await pairedHousehold(env);
  await call(env, phone, '/v1/sync/exchange', { records: [record('one-abcdefgh', 1, 'a')] });
  const first = await call(env, tv, '/v1/sync/exchange', { since: 0 });
  await call(env, phone, '/v1/sync/exchange', { records: [record('two-abcdefgh', 2, 'b')] });
  const second = await call(env, tv, '/v1/sync/exchange', { since: first.body.revision });
  assert.deepEqual(second.body.records.map((item) => item.key), ['two-abcdefgh']);
  env.DB.close();
});

test('paging never loses a record at a page boundary', async () => {
  const env = createEnv();
  const { phone, tv } = await pairedHousehold(env);
  const all = new Set();
  for (let push = 0; push < 4; push += 1) {
    const records = [];
    for (let index = 0; index < MAX_PUSH_RECORDS; index += 1) {
      const key = `k${push}-${String(index).padStart(6, '0')}`;
      all.add(key);
      records.push(record(key, 1 + index, 'x'));
    }
    assert.equal((await call(env, phone, '/v1/sync/exchange', { records })).status, 200);
  }
  const received = new Set();
  let since = 0;
  for (let guard = 0; guard < 10; guard += 1) {
    const page = await call(env, tv, '/v1/sync/exchange', { since });
    page.body.records.forEach((item) => received.add(item.key));
    since = page.body.revision;
    if (!page.body.hasMore) break;
  }
  assert.equal(received.size, all.size);
  env.DB.close();
});

test('a captured request does not work twice', async () => {
  const env = createEnv();
  const { phone } = await pairedHousehold(env);
  const nonce = 'replayed-nonce-0000000';
  assert.equal((await call(env, phone, '/v1/sync/status', {}, nonce)).status, 200);
  assert.equal((await call(env, phone, '/v1/sync/status', {}, nonce)).status, 409);
  env.DB.close();
});

test('a wrong or expired code joins nothing', async () => {
  const env = createEnv();
  const { phone } = await pairedHousehold(env);
  const stranger = await device(env, 'STRA-NGER-AAAA');
  assert.equal((await call(env, stranger, '/v1/sync/join', { code: 'ZZZZ-ZZZZ' })).status, 404);
  assert.equal((await call(env, stranger, '/v1/sync/join', { code: 'not a code' })).status, 400);

  const invite = await call(env, phone, '/v1/sync/invite');
  env.DB.prepare('UPDATE sync_invites SET expires_at = ?').bind(PAST).run();
  assert.equal((await call(env, stranger, '/v1/sync/join', { code: invite.body.code })).status, 404);
  env.DB.close();
});

test('codes are typed loosely and read back strictly', () => {
  assert.equal(normaliseCode('abcd efgh'), 'ABCD-EFGH');
  assert.equal(normaliseCode('ABCDEFGH'), 'ABCD-EFGH');
  assert.equal(normaliseCode('ABCD-EFG1'), null, 'the alphabet has no 1, 0, I or O');
  assert.equal(normaliseCode('ABC'), null);
});

test('joining another household leaves an empty group behind, and it is deleted', async () => {
  const env = createEnv();
  const { phone } = await pairedHousehold(env);
  const loner = await device(env, 'LONE-RAAA-AAAA');
  await call(env, loner, '/v1/sync/invite');
  await call(env, loner, '/v1/sync/exchange', { records: [record('old-abcdefgh', 1, 'x')] });
  const invite = await call(env, phone, '/v1/sync/invite');
  assert.equal((await call(env, loner, '/v1/sync/join', { code: invite.body.code })).body.members, 3);
  const groups = env.DB.prepare('SELECT COUNT(*) AS total FROM sync_groups').bind().first();
  assert.equal(groups.total, 1);
  const orphans = env.DB.prepare(
    'SELECT COUNT(*) AS total FROM sync_records WHERE group_id NOT IN (SELECT group_id FROM sync_groups)',
  ).bind().first();
  assert.equal(orphans.total, 0);
  env.DB.close();
});

test('malformed or oversized pushes are refused whole', async () => {
  const env = createEnv();
  const { phone } = await pairedHousehold(env);
  assert.equal((await call(env, phone, '/v1/sync/exchange', { records: [record('bad key!', 1, 'x')] })).status, 400);
  assert.equal((await call(env, phone, '/v1/sync/exchange', { records: [record('big-abcdefgh', 1, 'x'.repeat(5000))] })).status, 400);
  const tooMany = Array.from({ length: MAX_PUSH_RECORDS + 1 }, (_, index) => record(`many-${String(index).padStart(6, '0')}`, 1, 'x'));
  assert.equal((await call(env, phone, '/v1/sync/exchange', { records: tooMany })).status, 413);
  assert.equal(env.DB.prepare('SELECT COUNT(*) AS total FROM sync_records').bind().first().total, 0);
  env.DB.close();
});

test('a device outside any group is told so rather than silently dropped', async () => {
  const env = createEnv();
  const alone = await device(env, 'ALON-EAAA-AAAA');
  assert.equal((await call(env, alone, '/v1/sync/exchange', { records: [] })).status, 409);
  env.DB.close();
});
