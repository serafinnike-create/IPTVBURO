/**
 * The admin panel's access control and key generation.
 *
 * What this protects is the ability to give the product away for free, so the token check gets more
 * attention than the features behind it.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { grantDevice, isAdmin } from '../src/admin.js';
import { adminPage } from '../src/admin-page.js';

/** A minimal stand-in for a Worker request: only the headers are read. */
function request(authorization) {
  return { headers: { get: (name) => (name === 'authorization' ? authorization : null) } };
}

const env = { ADMIN_TOKEN: 'a-long-random-admin-token-value' };

test('the correct token is accepted', () => {
  assert.equal(isAdmin(request(`Bearer ${env.ADMIN_TOKEN}`), env), true);
});

test('a wrong token is refused', () => {
  assert.equal(isAdmin(request('Bearer wrong-token-of-same-length!!'), env), false);
});

test('no token is refused', () => {
  assert.equal(isAdmin(request(null), env), false);
  assert.equal(isAdmin(request(''), env), false);
  assert.equal(isAdmin(request('Bearer '), env), false);
});

test('a token without the Bearer prefix is refused', () => {
  assert.equal(isAdmin(request(env.ADMIN_TOKEN), env), false);
});

/**
 * A prefix of the real token must not pass.
 *
 * This is what a character-at-a-time guess looks like, and the length check plus the constant-time
 * comparison are what stop it.
 */
test('a correct prefix is refused', () => {
  assert.equal(isAdmin(request('Bearer a-long-random'), env), false);
});

/**
 * With no token configured, nothing is admin.
 *
 * An unconfigured Worker must fail closed. Treating "no token set" as "no check needed" would leave
 * the panel open to anyone who found the URL — and that is exactly the state a fresh deployment is
 * in before the secret is set.
 */
test('an unconfigured server refuses everyone', () => {
  assert.equal(isAdmin(request('Bearer anything'), {}), false);
  assert.equal(isAdmin(request('Bearer anything'), { ADMIN_TOKEN: '' }), false);
});

test('the admin page carries no token of its own', () => {
  const page = adminPage();

  // The page is served to anyone who asks — it is a login box. It must therefore contain nothing
  // that would be useful to somebody who has not signed in.
  assert.ok(!page.includes(env.ADMIN_TOKEN));
  assert.ok(page.includes('Token de acesso'), 'it should ask for one');
});

test('the admin page escapes values from the database', () => {
  const page = adminPage();

  // Notes are free text typed by hand, and device ids come from the database. The panel builds its
  // table with string concatenation, so an escape function has to exist and be used.
  assert.ok(page.includes('function esc('), 'the panel must escape what it renders');
  assert.ok(page.includes('esc(device.note'), 'notes are free text and must be escaped');
  assert.ok(page.includes('esc(device.device_id)'), 'device ids must be escaped');
});

test('the token is kept in session storage, not a cookie', () => {
  const page = adminPage();

  // A cookie on this origin would be sent with every request to the customer-facing pages too,
  // which have no business seeing an admin credential.
  assert.ok(page.includes('sessionStorage'), 'expected sessionStorage');
  assert.ok(!page.includes('document.cookie'), 'the token must not live in a cookie');
});

test('the admin panel identifies hardware and separates support actions', () => {
  const page = adminPage();

  for (const field of ['manufacturer', 'model', 'os_version', 'app_version', 'last_seen_at', 'source']) {
    assert.ok(page.includes(field), `the device card should render ${field}`);
  }
  assert.ok(page.includes('Detalhes e histórico'));
  assert.ok(page.includes('Bloquear'));
  assert.ok(page.includes('Apagar da lista'));
  assert.ok(page.includes('Restaurar na lista'));
});

test('deleting a device is presented as audited archival rather than physical deletion', () => {
  const page = adminPage();

  assert.ok(page.includes("api('/admin/archive'"));
  assert.ok(page.includes('histórico será preservado'));
  assert.ok(page.includes('impedir novo teste gratuito'));
});

test('a manual grant extends a future expiry instead of shortening it', async () => {
  const writes = [];
  const futureExpiry = '2099-01-01T00:00:00.000Z';
  const database = {
    prepare(sql) {
      const statement = {
        args: [],
        bind(...args) { this.args = args; return this; },
        async first() { return { expires_at: futureExpiry }; },
        async run() { writes.push({ sql, args: this.args }); },
      };
      return statement;
    },
  };

  await grantDevice('FP86-XARB-9JZW', 30, 'support extension', { DB: database });

  const deviceWrite = writes.find(({ sql }) => sql.includes('INSERT INTO devices'));
  assert.ok(deviceWrite, 'the device must be updated');
  assert.equal(
    deviceWrite.args[4],
    new Date(Date.parse(futureExpiry) + 30 * 86400000).toISOString(),
    'the new days should start after the time already owned',
  );
});

test('a manual grant rejects unbounded durations', async () => {
  await assert.rejects(
    grantDevice('FP86-XARB-9JZW', 36500, 'not actually unlimited', { DB: {} }),
    /between 1 and 3650/,
  );
});
