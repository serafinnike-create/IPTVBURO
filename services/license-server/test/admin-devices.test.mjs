import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import {
  archiveDevice,
  adminBackup,
  deviceDetails,
  devicesByStatus,
  financialOverview,
  listAdminAudit,
  listSecurityAlerts,
  recordAdminAudit,
  recordSecurityAlert,
  resolveSecurityAlert,
  restoreDevice,
  searchDevices,
  summary,
  updateDeviceSupport,
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
    activationCountry: 'BR',
    lastCountry: 'DE',
    ...overrides,
  };
  env.DB.database.prepare(
    `INSERT INTO devices (
       device_id, status, first_seen_at, trial_ends_at, updated_at,
       manufacturer, model, platform, device_type, activation_country, last_country
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
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
    row.activationCountry,
    row.lastCountry,
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
  assert.equal(found.activation_country, 'BR');
  assert.equal(found.last_country, 'DE');
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

test('support labels and audit do not alter entitlement or expose private identity fields', async () => {
  const env = environment();
  const deviceId = insertDevice(env, { status: 'ACTIVE' });
  env.DB.database.prepare(
    'UPDATE devices SET public_key = ?, machine_anchor = ?, stripe_session_id = ? WHERE device_id = ?',
  ).run('private-key-material', 'machine-anchor', 'cs_fixture', deviceId);

  assert.equal(await updateDeviceSupport(deviceId, {
    displayName: 'TV da sala',
    customerName: 'Cliente Teste',
    customerEmail: 'CLIENTE@example.com',
    orderReference: 'PED-42',
    supportNote: 'Ligou sobre atualização',
  }, env), true);
  await recordAdminAudit('Lucas', 'DEVICE_SUPPORT_UPDATED', deviceId, 'PED-42', 'BR', env);

  const details = await deviceDetails(deviceId, env);
  assert.equal(details.device.display_name, 'TV da sala');
  assert.equal(details.device.customer_email, 'cliente@example.com');
  assert.equal(details.device.status, 'ACTIVE');
  assert.equal(Object.hasOwn(details.device, 'public_key'), false);
  const [audit] = await listAdminAudit(env);
  assert.equal(audit.actor, 'Lucas');
  assert.equal(audit.device_id, deviceId);
});

test('finance reports exact Stripe ledger amounts and never invents a Google Play price', async () => {
  const env = environment();
  const deviceId = insertDevice(env, { status: 'ACTIVE' });
  env.DB.database.prepare(
    `INSERT INTO payments (
       checkout_session_id, device_id, product_id, amount_minor, currency, status,
       amount_refunded_minor, paid_at, created_at, updated_at
     ) VALUES ('cs_fixture', ?, 'iptvburo-device-2y', 990, 'eur', 'PARTIALLY_REFUNDED',
       100, '2026-08-12T12:00:00Z', '2026-08-12T12:00:00Z', '2026-08-12T12:00:00Z')`,
  ).run(deviceId);
  env.DB.database.prepare(
    `INSERT INTO google_play_purchases (
       purchase_token_hash, token_ciphertext, device_id, product_id, purchase_option_id,
       obfuscated_account_id, status, acknowledgement_state, test_purchase, created_at, updated_at
     ) VALUES ('hash', 'encrypted', ?, 'iptvburo_730_days', 'buy-730-days', 'account',
       'PURCHASED', 'ACKNOWLEDGED', 0, '2026-08-12T12:00:00Z', '2026-08-12T12:00:00Z')`,
  ).run(deviceId);

  const finance = await financialOverview(env);
  assert.equal(finance.stripe[0].amount_minor, 990);
  assert.equal(finance.stripe[0].amount_refunded_minor, 100);
  assert.equal(Object.hasOwn(finance.googlePlay[0], 'amount_minor'), false);
});

test('security alerts deduplicate for 24 hours and can be resolved with an audit note', async () => {
  const env = environment();
  const deviceId = insertDevice(env);
  assert.equal(await recordSecurityAlert(deviceId, 'RAPID_COUNTRY_CHANGE', 'WARNING', 'BR → DE', env), true);
  assert.equal(await recordSecurityAlert(deviceId, 'RAPID_COUNTRY_CHANGE', 'WARNING', 'DE → BR', env), false);
  const [alert] = await listSecurityAlerts(env);
  assert.equal(alert.resolved_at, null);
  assert.equal(await resolveSecurityAlert(alert.id, 'VPN confirmada', env), true);
  const [resolved] = await listSecurityAlerts(env);
  assert.ok(resolved.resolved_at);
  assert.equal(resolved.resolution_note, 'VPN confirmada');
});

test('backup omits device keys, purchase tokens and unused activation codes', async () => {
  const env = environment();
  const deviceId = insertDevice(env);
  env.DB.database.prepare(
    'UPDATE devices SET public_key = ?, machine_anchor = ?, google_purchase_token_hash = ? WHERE device_id = ?',
  ).run('secret-key', 'secret-anchor', 'secret-token-hash', deviceId);
  env.DB.database.prepare(
    `INSERT INTO redemption_keys (key_code, grant_days, created_at)
     VALUES ('SECRET-KEY', 30, '2026-08-12T12:00:00Z')`,
  ).run();

  const serialized = JSON.stringify(await adminBackup(env));
  assert.equal(serialized.includes('secret-key'), false);
  assert.equal(serialized.includes('secret-anchor'), false);
  assert.equal(serialized.includes('secret-token-hash'), false);
  assert.equal(serialized.includes('SECRET-KEY'), false);
});
