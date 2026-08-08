/**
 * What the customer sees when something goes wrong, and what the browser is allowed to send.
 *
 * Both cases here were live bugs on the first deployment. Neither showed up in the unit tests
 * because both live at a boundary the tests did not cross: one in a CORS header, one in a code path
 * that only runs when Stripe is unreachable.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { checkoutUnavailablePage } from '../src/pages.js';

const indexSource = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');

/** Cross-origin access belongs to the public app API, never the same-origin admin or webhook. */
test('cross-origin public endpoints do not advertise admin or Stripe headers', () => {
  const declaration = indexSource.match(/const PUBLIC_CORS_PATHS = new Set\(\[([^\]]*)\]\)/);
  assert.ok(declaration, 'PUBLIC_CORS_PATHS not found');

  const paths = declaration[1].match(/'[^']+'/g).map((quoted) => quoted.slice(1, -1));

  // Only /v1 app endpoints. The rule rather than a fixed list, because the set legitimately grows:
  // what must never happen is an admin or Stripe path appearing here, since advertising those
  // cross-origin would let any page on the internet call them from a visitor's browser.
  for (const path of paths) {
    assert.ok(path.startsWith('/v1/'), `${path} must not be exposed cross-origin`);
    assert.ok(!path.startsWith('/v1/stripe'), 'the Stripe webhook is server-to-server');
  }
  assert.ok(!paths.includes('/admin'), 'the admin panel must never be cross-origin');
  assert.ok(!paths.some((path) => path.startsWith('/admin/')), 'nor any admin route');

  assert.match(indexSource, /'access-control-allow-headers': 'content-type'/);
  assert.doesNotMatch(
    indexSource,
    /'access-control-allow-headers':\s*'[^']*(authorization|stripe-signature)/,
  );
});

/**
 * A failed checkout must be a page, not JSON.
 *
 * Someone who pressed a payment button and received `{"error":"checkout_unavailable"}` cannot tell
 * whether their card was charged. That uncertainty is the thing that produces a support message, and
 * it costs more than the failure itself.
 */
test('a failed checkout answers with a page', () => {
  assert.ok(
    indexSource.includes('checkoutUnavailablePage'),
    'the checkout failure path must render a page',
  );
  assert.ok(
    !/checkout_unavailable/.test(indexSource),
    'the raw JSON error should no longer be reachable',
  );
});

test('the failure page says nothing was charged, in every language', () => {
  // The words differ, but each translation must contain the reassurance. Checking for the phrase in
  // each language is what stops a new translation shipping with the sentence quietly dropped.
  const reassurance = {
    pt: /não foi cobrado|nada foi cobrado/i,
    en: /have not been charged/i,
    de: /nichts abgebucht/i,
    it: /non ti è stato addebitato/i,
  };

  for (const [language, pattern] of Object.entries(reassurance)) {
    const page = checkoutUnavailablePage({ language, deviceId: 'FP86-XARB-9JZW' });
    assert.ok(pattern.test(page), `${language} does not tell the customer they were not charged`);
    assert.ok(page.includes(`lang="${language}"`), `${language} did not render`);
  }
});

test('the failure page offers a way back', () => {
  const page = checkoutUnavailablePage({ language: 'pt', deviceId: 'FP86-XARB-9JZW' });

  assert.ok(page.includes('action="/comprar"'), 'there must be a way to try again');
  assert.ok(page.includes('FP86-XARB-9JZW'), 'the device must survive the retry');
});

test('the failure page escapes the device id', () => {
  const page = checkoutUnavailablePage({ language: 'pt', deviceId: '"><script>alert(1)</script>' });

  assert.ok(!page.includes('<script>alert'), 'a script tag reached the page unescaped');
});

test('the failure page survives having no device', () => {
  // Reached when the form posted something invalid. It must render rather than print "undefined".
  const page = checkoutUnavailablePage({ language: 'pt', deviceId: null });

  assert.ok(!page.includes('undefined'), 'a missing device must not print as undefined');
  assert.ok(!page.includes('null'), 'nor as null');
});

/**
 * Return URLs come from the request, not from a constant.
 *
 * The first deployment had `iptvburo.workers.dev` written into the checkout code while the Worker
 * actually served `iptvburo.iptvburo.workers.dev`. A customer would have paid successfully and been
 * returned to a domain that does not answer — the worst possible moment for a dead link, and one
 * nothing in the buy flow would have revealed beforehand.
 */
test('checkout return urls are derived from the incoming request', () => {
  const checkoutSource = readFileSync(new URL('../src/checkout.js', import.meta.url), 'utf8');

  assert.ok(
    !/https:\/\/[a-z.]*workers\.dev/.test(checkoutSource),
    'no hardcoded address: the return url must follow the request',
  );
  assert.ok(indexSource.includes('url.origin'), 'the origin should be passed in from the request');
});

/**
 * An unconfigured Stripe key fails before the request rather than after.
 *
 * Letting Stripe answer 401 works, but it makes "the deployment is not finished" look identical to
 * "Stripe is having an outage" in the logs — and the first is fixed in a minute while the second is
 * waited out.
 */
test('a missing stripe key is caught before the call', () => {
  const checkoutSource = readFileSync(new URL('../src/checkout.js', import.meta.url), 'utf8');

  assert.ok(
    /if\s*\(!env\.STRIPE_SECRET_KEY\)/.test(checkoutSource),
    'the key should be checked before calling Stripe',
  );
});
