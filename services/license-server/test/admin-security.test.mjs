import { strict as assert } from 'node:assert';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import {
  authenticateAdmin,
  beginMfaSetup,
  confirmMfaSetup,
  createAdminSession,
} from '../src/admin-security.js';
import { listAdminAudit } from '../src/admin.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');

class Statement {
  constructor(database, sql) { this.statement = database.prepare(sql); this.values = []; }
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
    ADMIN_TOKEN: 'fixture-admin-token-with-entropy',
    ADMIN_MFA_ENCRYPTION_KEY: Buffer.alloc(32, 9).toString('base64'),
    DB: { prepare: (sql) => new Statement(database, sql), database },
  };
}

function request(token, session = '') {
  return new Request('https://local.test/admin/session', {
    headers: { authorization: `Bearer ${token}`, 'x-admin-session': session },
  });
}

test('a bearer token alone never authorises data or mutation routes, enrolled or not', async () => {
  const env = environment();
  const adminRequest = request(env.ADMIN_TOKEN);
  // Before MFA is even set up, the token must not unlock the admin API — otherwise a leaked
  // token is the whole story for an administrator who has simply not enrolled yet, and once a
  // provisioning payload can carry a customer's own Xtream password, "not yet enrolled" is not
  // an acceptable gap. It still opens the setup endpoints, which call isAdmin() directly.
  assert.equal(await authenticateAdmin(adminRequest, env), null);

  const setup = await beginMfaSetup(adminRequest, env);
  assert.equal(setup.ok, true);
  assert.match(setup.secret, /^[A-Z2-7]{32}$/);

  const wrong = await confirmMfaSetup(adminRequest, '000000', env);
  assert.equal(wrong.ok, false);
  const code = totp(setup.secret, Date.now());
  const confirmed = await confirmMfaSetup(adminRequest, code, env);
  assert.equal(confirmed.ok, true);

  assert.equal(await authenticateAdmin(adminRequest, env), null);
  const signedIn = await createAdminSession(adminRequest, { actor: 'Lucas', code }, env);
  assert.equal(signedIn.ok, true);
  const session = await authenticateAdmin(request(env.ADMIN_TOKEN, signedIn.token), env);
  assert.equal(session.actor, 'Lucas');
  assert.equal(session.method, 'session');
});

test('a rejected sign-in is recorded, a successful one is not', async () => {
  const env = environment();
  await createAdminSession(request('wrong-token-entirely'), {}, env);
  const afterBadToken = await listAdminAudit(env);
  assert.equal(afterBadToken.filter((entry) => entry.action === 'ADMIN_LOGIN_REJECTED').length, 1);
  assert.equal(afterBadToken[0].detail, 'bad_token');

  const signedIn = await createAdminSession(request(env.ADMIN_TOKEN), { actor: 'Lucas' }, env);
  assert.equal(signedIn.ok, true);
  const afterGoodLogin = await listAdminAudit(env);
  assert.equal(afterGoodLogin.filter((entry) => entry.action === 'ADMIN_LOGIN_REJECTED').length, 1);
});

function totp(secret, nowMs) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let buffer = 0;
  let bits = 0;
  const output = [];
  for (const character of secret) {
    buffer = (buffer << 5) | alphabet.indexOf(character);
    bits += 5;
    if (bits >= 8) {
      output.push((buffer >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(Math.floor(nowMs / 30000)));
  const digest = createHmac('sha1', Buffer.from(output)).update(message).digest();
  const offset = digest[digest.length - 1] & 15;
  const binary = (digest.readUInt32BE(offset) & 0x7fffffff) % 1_000_000;
  return String(binary).padStart(6, '0');
}
