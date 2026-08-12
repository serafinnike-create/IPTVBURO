/**
 * The site: the front page, the four steps, and activating with a key.
 *
 * The drawings are checked structurally rather than visually — that they are inline, that they carry
 * no external reference, and that they use the palette instead of hardcoded colours. A test cannot
 * tell whether a drawing looks good, but it can tell whether it will render at all under a policy
 * that forbids loading anything.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { activatePage, buyPage, homePage } from '../src/pages.js';
import {
  buroMark,
  cardPayment,
  phoneScanning,
  screenWithCode,
  unlockedApp,
} from '../src/illustrations.js';

const drawings = { screenWithCode, phoneScanning, cardPayment, unlockedApp };

test('the front page explains the product and the trial', () => {
  const page = homePage({ language: 'pt' });

  assert.ok(page.includes('IPTV'), 'the product should be named');
  assert.ok(/7 dias/.test(page), 'the free trial is the reason to keep reading');
  assert.ok(page.includes('Como funciona'), 'the steps belong here');
  assert.ok(page.includes('href="/ativar'), 'a visitor with a key needs a real action');
  assert.ok(
    page.includes('/assets/buro-cinematic-multiscreen-hero.webp'),
    'the cinematic hero should be a local, self-hosted asset',
  );
});

test('the front page renders in every language', () => {
  for (const language of ['pt', 'en', 'de', 'it']) {
    const page = homePage({ language });
    assert.ok(page.includes(`lang="${language}"`), `${language} did not render`);
    assert.ok(!page.includes('undefined'), `${language} has a missing string`);
  }
});

test('the four steps appear on the buy page', () => {
  const page = buyPage({
    deviceId: 'FP86-XARB-9JZW',
    device: { status: 'TRIAL' },
    language: 'pt',
    currency: 'eur',
    price: { amount: 990, label: '€9,90' },
  });

  assert.ok(page.includes('Como funciona'));
  assert.ok(page.includes('<svg'), 'the drawings should be inline');
  // Four numbered steps.
  assert.equal((page.match(/class="step-number"/g) ?? []).length, 4);
});

/**
 * An active device sees no steps.
 *
 * They describe how to buy, and this customer already has. Leaving them visible is the same mistake
 * as leaving the buy button visible — it suggests there is something left to do.
 */
test('an already active device is not shown how to buy', () => {
  const page = buyPage({
    deviceId: 'FP86-XARB-9JZW',
    device: { status: 'ACTIVE', expires_at: '2028-08-07T12:00:00Z' },
    language: 'pt',
    currency: 'eur',
    price: { amount: 990, label: '€9,90' },
  });

  assert.ok(!page.includes('Como funciona'), 'an owner does not need the buying steps');
});

test('every drawing is self-contained', () => {
  for (const [name, draw] of Object.entries(drawings)) {
    const svg = draw();

    assert.ok(svg.includes('<svg'), `${name} is not an svg`);
    // The policy on these pages is `default-src 'none'`. Anything fetched would silently not load.
    assert.ok(!/https?:\/\//.test(svg), `${name} references something external`);
    assert.ok(!/<image|xlink:href/.test(svg), `${name} embeds a bitmap`);
    assert.ok(!/<script/i.test(svg), `${name} contains script`);
  }
});

test('the drawings use the palette rather than fixed colours', () => {
  for (const [name, draw] of Object.entries(drawings)) {
    const svg = draw();
    assert.ok(svg.includes('var(--'), `${name} does not follow the palette`);
  }
});

test('the drawings are hidden from screen readers', () => {
  // They repeat what the text beside them already says. Announcing them twice is noise.
  for (const [name, draw] of Object.entries(drawings)) {
    assert.ok(draw().includes('aria-hidden'), `${name} should not be announced`);
  }
});

test('the product mark is decorative beside the visible wordmark', () => {
  // The header already says "IPTV BURO" in text. Announcing the adjacent mark repeats the name.
  assert.ok(buroMark().includes('aria-hidden'), 'the repeated mark should not be announced');
  assert.ok(buroMark().includes('focusable="false"'), 'the SVG must not enter keyboard focus');
});

test('the activation form asks for a device and a key', () => {
  const page = activatePage({ deviceId: 'FP86-XARB-9JZW', language: 'pt' });

  assert.ok(page.includes('action="/ativar"'));
  assert.ok(page.includes('name="device"'));
  assert.ok(page.includes('name="key"'));
  assert.ok(page.includes('FP86-XARB-9JZW'), 'a known device should be filled in already');
});

test('the activation form shows why it failed', () => {
  const page = activatePage({
    deviceId: 'FP86-XARB-9JZW',
    language: 'pt',
    message: 'Esse código já foi usado.',
  });

  assert.ok(page.includes('já foi usado'), 'the reason must reach the customer');
  assert.ok(page.includes('name="key"'), 'and the form must still be there to retry');
});

test('a successful activation says what to do next', () => {
  const page = activatePage({ language: 'pt', done: true });

  assert.ok(page.includes('ativado'));
  assert.ok(page.toLowerCase().includes('aplicativo'), 'the app must be reopened to notice');
  assert.ok(!page.includes('name="key"'), 'and the form is finished with');
});

test('the activation form escapes what it echoes back', () => {
  const page = activatePage({
    deviceId: '"><script>alert(1)</script>',
    language: 'pt',
    message: '<script>alert(2)</script>',
  });

  assert.ok(!page.includes('<script>alert'), 'markup reached the page unescaped');
});

test('the site pages load nothing from outside', () => {
  for (const page of [homePage({ language: 'pt' }), activatePage({ language: 'pt' })]) {
    assert.ok(!/https?:\/\//.test(page), 'no external references');
    assert.ok(!/<script/i.test(page), 'no scripts: the policy forbids them');
    assert.ok(!/<link[^>]+href/i.test(page), 'no external stylesheets');
  }
});

/**
 * No third-party brand marks.
 *
 * GDD 9 §10 forbids copying brand logos, and a commercial page carrying another company's mark is a
 * trademark problem regardless of how the mark was produced. Naming Stripe in a sentence is fine and
 * necessary — drawing their logo is not.
 */
test('no third-party logos are drawn', () => {
  const everything = [
    homePage({ language: 'pt' }),
    activatePage({ language: 'pt' }),
    ...Object.values(drawings).map((draw) => draw()),
  ].join('');

  for (const brand of ['visa', 'mastercard', 'paypal', 'google play', 'app store', 'roku']) {
    assert.ok(
      !everything.toLowerCase().includes(brand),
      `${brand} appears — brand marks must not be reproduced`,
    );
  }
});
