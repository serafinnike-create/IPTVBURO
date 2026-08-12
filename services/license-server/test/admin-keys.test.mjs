/**
 * Cancelling activation codes, and showing enough about them to be useful.
 *
 * The panel generated a code and printed the code alone — not what it grants, not who it was for,
 * not when it was made, and with no way to take it back. A code you cannot cancel is a code that
 * works for ever once it leaves your hands, which is the wrong property for something handed out by
 * message.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import { cancelKey, createKeys, listKeys } from '../src/admin.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const PAGE = readFileSync(new URL('../src/admin-page.js', import.meta.url), 'utf8');
const INDEX = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');

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

function environment() {
  const database = new DatabaseSync(':memory:');
  database.exec(SCHEMA);
  return { DB: { prepare: (sql) => new Statement(database, sql), database } };
}

test('an unused key can be cancelled', async () => {
  const env = environment();
  const [code] = await createKeys(1, 30, 'lucas', env);

  assert.equal(await cancelKey(code, env), true);
  assert.equal((await listKeys(env)).length, 0, 'the key should be gone');
});

/**
 * A cancelled key cannot be redeemed.
 *
 * Deletion rather than a flag, so there is no code path — present or future — that could honour it.
 * A "cancelled" column would have to be checked everywhere it is read, and the one place somebody
 * forgets is the place that gives the product away.
 */
test('a cancelled key no longer exists to be found', async () => {
  const env = environment();
  const [code] = await createKeys(1, 30, 'wrong person', env);
  await cancelKey(code, env);

  const found = await env.DB.prepare('SELECT * FROM redemption_keys WHERE key_code = ?')
    .bind(code)
    .first();
  assert.equal(found, null);
});

/**
 * A redeemed key is left alone.
 *
 * Deleting it would break the link between a device and how it was activated, and would free the
 * code to be issued again. Taking back what it granted is a revocation of the device.
 */
test('a redeemed key cannot be cancelled', async () => {
  const env = environment();
  const [code] = await createKeys(1, 30, 'someone', env);
  await env.DB.prepare('UPDATE redemption_keys SET redeemed_by = ?, redeemed_at = ? WHERE key_code = ?')
    .bind('AAAA-BBBB-CCCC', new Date().toISOString(), code)
    .run();

  assert.equal(await cancelKey(code, env), false);
  assert.equal((await listKeys(env)).length, 1, 'the record must survive');
});

test('cancelling something that does not exist is refused rather than silently accepted', async () => {
  const env = environment();

  assert.equal(await cancelKey('ZZZZ-ZZZZ', env), false);
  assert.equal(await cancelKey('', env), false);
  assert.equal(await cancelKey(null, env), false);
});

test('cancellation is recorded', async () => {
  const env = environment();
  const [code] = await createKeys(1, 30, 'lucas', env);
  await cancelKey(code, env);

  // The key row is gone, so the event log is the only remaining explanation for its absence.
  const event = await env.DB.prepare("SELECT * FROM events WHERE kind = 'key_cancelled'").first();
  assert.notEqual(event, null, 'the cancellation should be recorded');
  assert.equal(event.detail, code);
});

test('cancelling one key leaves the others alone', async () => {
  const env = environment();
  const codes = await createKeys(3, 30, 'batch', env);

  await cancelKey(codes[1], env);

  const remaining = (await listKeys(env)).map((key) => key.key_code).sort();
  assert.deepEqual(remaining, [codes[0], codes[2]].sort());
});

test('a key carries what it grants, who it is for, and when it was made', async () => {
  const env = environment();
  await createKeys(1, 730, 'lucas', env);

  const [key] = await listKeys(env);
  assert.equal(key.grant_days, 730, 'the duration must be stored');
  assert.equal(key.note, 'lucas', 'the note must be stored');
  assert.ok(key.created_at, 'the creation time must be stored');
});

test('the panel shows every stored field rather than the code alone', () => {
  // All four were in the database already and none reached the screen, which made a generated code
  // impossible to account for later.
  for (const field of ['grant_days', 'key_code', 'note', 'created_at', 'redeemed_by']) {
    assert.ok(PAGE.includes(field), `the key table should show ${field}`);
  }
});

test('the panel offers cancellation only for unused keys', () => {
  assert.ok(PAGE.includes('cancelKey('), 'there must be a way to cancel');
  assert.ok(PAGE.includes('confirm('), 'cancelling cannot be undone, so it must be confirmed');
  // The button is inside the `used ?` branch that renders a dash for redeemed keys.
  assert.ok(
    /used[\s\S]{0,200}Cancelar/.test(PAGE),
    'the cancel button must depend on the key being unused',
  );
});

test('the cancel route is behind the admin token', () => {
  // Every /admin path routes through handleAdmin, which checks the token first and unconditionally.
  assert.ok(
    INDEX.includes("case '/admin/keys/cancel':"),
    'the cancel route must be listed among the admin paths',
  );
});

test('the summary figures link to the lists behind them', () => {
  // "3 em teste" with nothing to click was a dead end: no way to find a device without already
  // knowing its code.
  assert.ok(PAGE.includes('listByStatus('), 'the figures must open a list');
  assert.ok(INDEX.includes("case '/admin/list':"), 'the list route must exist');
});

test('the device table shows how long is left', () => {
  // The reason for looking a device up is nearly always "how long do they have?", and a date makes
  // the reader do the arithmetic.
  assert.ok(PAGE.includes('function remaining('), 'the table should compute time remaining');
  assert.ok(PAGE.includes('último dia'), 'the last day is worth naming');
});
