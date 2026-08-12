/**
 * The customer-facing pages.
 *
 * Run with:  node --test
 *
 * Two things are worth pinning here, and neither is cosmetic:
 *
 * 1. **Escaping.** These pages interpolate a value that arrives in a query string. If it ever
 *    reaches the markup unescaped, the payment page — the one page where a customer is about to
 *    type card details — becomes a place to inject script. The device id is validated upstream, but
 *    an escape is the boundary's own job and not something to delegate to a check somewhere else.
 *
 * 2. **Not selling twice.** A device that is already active must not be shown a buy button. Taking
 *    a second payment for something the customer already owns is the fastest route to a chargeback,
 *    and a chargeback costs the fee plus the sale.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { buyPage, languageFrom, thanksPage } from '../src/pages.js';

const price = { amount: 990, label: '€9,90' };

function render(overrides = {}) {
  return buyPage({
    deviceId: 'FP86-XARB-9JZW',
    device: { status: 'TRIAL' },
    language: 'pt',
    currency: 'eur',
    price,
    ...overrides,
  });
}

test('the buy page shows the device and the price', () => {
  const page = render();

  assert.ok(page.includes('FP86-XARB-9JZW'), 'the device id must be visible to the customer');
  assert.ok(page.includes('€9,90'), 'the price must be stated before anyone is asked to pay');
  assert.ok(page.includes('action="/checkout"'), 'the buy button must lead somewhere');
});

test('a hostile device id cannot inject markup', () => {
  const page = render({ deviceId: '"><script>alert(1)</script>' });

  assert.ok(!page.includes('<script>'), 'a script tag reached the page unescaped');
  assert.ok(page.includes('&lt;script&gt;'), 'the value should appear escaped, not vanish');
  assert.ok(!page.includes('value=""><'), 'the attribute quote was broken out of');
});

test('an already active device is not sold to again', () => {
  const page = render({ device: { status: 'ACTIVE', expires_at: '2028-08-07T12:00:00Z' } });

  assert.ok(!page.includes('action="/checkout"'), 'an active device must not see a buy button');
  assert.ok(page.includes('<time datetime="2028-08-07T12:00:00Z">'), 'the expiry needs machine-readable time');
  assert.ok(page.includes('2028'), 'it should say when the licence runs out');
});

test('a page opened without a device explains rather than showing an empty form', () => {
  const page = render({ deviceId: null });

  assert.ok(!page.includes('action="/checkout"'));
  assert.ok(page.includes('QR') || page.includes('aplicativo'), 'it must say how to get here properly');
});

test('every shipped language renders', () => {
  for (const language of ['pt', 'en', 'de', 'it']) {
    const page = render({ language });
    assert.ok(page.includes(`lang="${language}"`), `${language} did not render`);
    assert.ok(page.includes('FP86-XARB-9JZW'));
  }
});

test('an unknown language falls back rather than breaking', () => {
  const page = render({ language: 'ja' });

  assert.ok(page.includes('FP86-XARB-9JZW'), 'the page must still render for an unsupported language');
});

test('language is detected from the browser header', () => {
  assert.equal(languageFrom('pt-BR,pt;q=0.9'), 'pt');
  assert.equal(languageFrom('en-GB,en;q=0.9'), 'en');
  assert.equal(languageFrom('de-DE'), 'de');
  assert.equal(languageFrom('it-IT'), 'it');
  assert.equal(languageFrom('en-US,en;q=0.8,pt-BR;q=0.1'), 'en');
  assert.equal(languageFrom('pt-BR;q=0.1,de-DE;q=0.9'), 'de');
  assert.equal(languageFrom('en;q=0,pt-BR;q=0.8'), 'pt');
  // Anything else gets the product's first language rather than a half-translated page.
  assert.equal(languageFrom('ja-JP'), 'pt');
  assert.equal(languageFrom(null), 'pt');
});

test('the paid return page tells the customer what to do next', () => {
  const page = thanksPage({
    language: 'pt',
    state: 'paid',
    deviceId: 'FP86-XARB-9JZW',
    expiresAt: '2028-08-07T12:00:00Z',
  });

  assert.ok(page.includes('IPTV') && page.includes('BURO'));
  // The one instruction that matters: the app has to be reopened to pick the licence up.
  assert.ok(page.toLowerCase().includes('aplicativo'));
  assert.ok(page.includes('Pagamento confirmado'));
  assert.ok(page.includes('FP86-XARB-9JZW'));
});

test('opening the thanks URL by hand never claims that payment succeeded', () => {
  const page = thanksPage({ language: 'pt' });

  assert.ok(page.includes('Pagamento não confirmado'));
  assert.ok(!page.includes('O seu dispositivo está ativo'));
});

test('a known pending checkout gives a safe retry instead of inviting another payment', () => {
  const page = thanksPage({
    language: 'pt',
    state: 'processing',
    deviceId: 'FP86-XARB-9JZW',
    retryUrl: '/obrigado?device=FP86-XARB-9JZW&session_id=cs_test_safe',
  });

  assert.ok(page.includes('Pagamento em processamento'));
  assert.ok(page.includes('não pague uma segunda vez'));
  assert.ok(page.includes('Verificar novamente'));
});

test('pages carry no external references', () => {
  // The content security policy on these responses is `default-src 'none'`. A stylesheet, font or
  // image from another host would silently fail to load in production while looking fine locally.
  const page = render();

  assert.ok(!page.includes('http://'), 'no plain-http references');
  assert.ok(!/<script/i.test(page), 'no scripts: the policy forbids them');
  assert.ok(!/<link[^>]+href/i.test(page), 'no external stylesheets');
});
