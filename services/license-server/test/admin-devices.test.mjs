import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import {
  archiveDevice,
  deviceDetails,
  devicesByStatus,
  restoreDevice,
  searchDevices,
  summary,
} from '../src/admin.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');

class Statement {
  constructor(database, sql) {
    this.statement = database.prepare(sql);
    this.values = [];
  }

  bind(...values) { this.values = values; return this; }
  async first() { return this.statement.get(...this.values) ?? null; }
  async all() { return { results: this.statement.all(...this.values) }; }
  async run() {
    const result = this.statement.run(...this.values);
    return { meta: { changes: Number(result.changes ?? 0) } };
  }
}

function environment() {
  const database = new DatabaseSync(':memory:');
  database.exec(SCHEMA);
  return {
    DB: {
      prepare: (sql) => new Statement(database, sql),
      batch: async (statements) => await Promise.all(statements.map((statement) => statement.run())),
      database,
    },
  };
}

function insertDevice(env, overrides = {}) {
  const row = {
    deviceId: 'ABCD-EFGH-JKLM',
    status: 'TRIAL',
    firstSeen: '2026-08-12T12:00:00.000Z',
    trialEnds: '2026-08-19T12:00:00.000Z',
    updated: '2026-08-12T12:00:00.000Z',
    manufacturer: 'Samsung',
    model: 'QN90D',
    platform: 'ANDROID',
    deviceType: 'ANDROID_TV',
    ...overrides,
  };
  env.DB.database.prepare(
    `INSERT INTO devices (
       device_id, status, first_seen_at, trial_ends_at, updated_at,
       manufacturer, model, platform, device_type
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    row.deviceId,
    row.status,
    row.firstSeen,
    row.trialEnds,
    row.updated,
    row.manufacturer,
    row.model,
    row.platform,
    row.deviceType,
  );
  return row.deviceId;
}

test('admin search finds model and returns only support-safe device fields', async () => {
  const env = environment();
  const deviceId = insertDevice(env);
  env.DB.database.prepare(
    'UPDATE devices SET public_key = ?, machine_anchor = ?, google_purchase_token_hash = ? WHERE device_id = ?',
  ).run('secret-public-key', 'private-machine-anchor', 'purchase-token-hash', deviceId);

  const [found] = await searchDevices('qn90', env);
  assert.equal(found.device_id, deviceId);
  assert.equal(found.model, 'QN90D');
  assert.equal(found.source, 'GOOGLE_PLAY');
  for (const privateField of ['public_key', 'machine_anchor', 'google_purchase_token_hash', 'mac_address']) {
    assert.equal(Object.hasOwn(found, privateField), false, `${privateField} must not leave D1`);
  }
});

test('admin delete archives and blocks without erasing trial or history', async () => {
  const env = environment();
  const deviceId = insertDevice(env);

  assert.equal(await archiveDevice(deviceId, 'duplicate test install', env), true);
  assert.equal((await devicesByStatus('ALL', env)).length, 0);
  const [archived] = await devicesByStatus('ARCHIVED', env);
  assert.equal(archived.device_id, deviceId);
  assert.equal(archived.status, 'REVOKED');
  assert.ok(archived.archived_at);

  const details = await deviceDetails(deviceId, env);
  assert.equal(details.device.first_seen_at, '2026-08-12T12:00:00.000Z');
  assert.equal(details.events[0].kind, 'archived');
});

test('restoring an archived device does not silently reactivate it', async () => {
  const env = environment();
  const deviceId = insertDevice(env);
  await archiveDevice(deviceId, 'cleanup', env);

  assert.equal(await restoreDevice(deviceId, env), true);
  const [restored] = await devicesByStatus('REVOKED', env);
  assert.equal(restored.device_id, deviceId);
  assert.equal(restored.status, 'REVOKED');
  assert.equal(restored.archived_at, null);
});

test('summary includes blocked, expired and archived operational queues', async () => {
  const env = environment();
  insertDevice(env, { deviceId: 'AAAA-BBBB-CCCC', status: 'ACTIVE' });
  insertDevice(env, { deviceId: 'DDDD-EEEE-FFFF', status: 'EXPIRED' });
  const archivedId = insertDevice(env, { deviceId: 'GGGG-HHHH-JJJJ' });
  await archiveDevice(archivedId, 'old test', env);

  assert.deepEqual(await summary(env), {
    active: 1,
    trial: 0,
    paid: 0,
    revoked: 0,
    expired: 1,
    archived: 1,
  });
});
