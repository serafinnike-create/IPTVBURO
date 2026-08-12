import { strict as assert } from 'node:assert';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

const stripeEvents = [
  'checkout.session.completed',
  'checkout.session.async_payment_succeeded',
  'charge.refunded',
  'charge.dispute.created',
  'charge.dispute.closed',
];

test('deployment instructions and helper list every Stripe event the worker handles', async () => {
  const [helper, instructions] = await Promise.all([
    readFile(new URL('../configurar-webhook.ps1', import.meta.url), 'utf8'),
    readFile(new URL('../IMPLANTAR.md', import.meta.url), 'utf8'),
  ]);

  for (const event of stripeEvents) {
    assert.ok(helper.includes(event), `${event} is missing from configurar-webhook.ps1`);
    assert.ok(instructions.includes(event), `${event} is missing from IMPLANTAR.md`);
  }
});
