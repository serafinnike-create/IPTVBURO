/**
 * The activation pages' frame: the language links, the way between buying and redeeming, and the
 * purchase page that used to be a dead end without a device.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { activatePage, buyPage, pairPage, thanksPage } from '../src/pages.js';

const price = { amount: 990, label: '€9,90' };

test('the buy page without a device asks for one instead of stopping', () => {
  const page = buyPage({ deviceId: null, language: 'en', price });

  assert.ok(/<form method="GET" action="\/comprar"/.test(page), 'there must be somewhere to type the code');
  assert.ok(page.includes('name="device"'));
  assert.ok(page.includes('name="lang" value="en"'), 'the language must survive the submission');
  assert.ok(!page.includes('action="/checkout"'), 'nothing is sold before a device is known');
});

test('changing language keeps the device the customer came with', () => {
  const page = buyPage({ deviceId: 'FP86-XARB-9JZW', device: { status: 'TRIAL' }, language: 'pt', price });

  for (const language of ['pt', 'en', 'de', 'it']) {
    assert.ok(
      page.includes(`href="/comprar?device=FP86-XARB-9JZW&amp;lang=${language}"`),
      `the ${language} link drops the device`,
    );
  }
  assert.ok(page.includes('aria-current="page">PT<'), 'the current language is marked');
});

test('a pairing code survives a language change', () => {
  const page = pairPage({ language: 'de', code: '123456', kind: 'open_title' });

  assert.ok(page.includes('href="/parear?kind=open_title&amp;code=123456&amp;lang=it"'));
});

test('a waiting payment keeps its session across a language change', () => {
  const page = thanksPage({
    language: 'pt',
    state: 'processing',
    deviceId: 'FP86-XARB-9JZW',
    retryUrl: '/obrigado?device=FP86-XARB-9JZW&session_id=cs_test_safe',
  });

  assert.ok(page.includes('href="/obrigado?device=FP86-XARB-9JZW&amp;session_id=cs_test_safe&amp;lang=en"'));
});

test('buying and redeeming each point to the other once', () => {
  const buy = buyPage({ deviceId: 'FP86-XARB-9JZW', device: { status: 'TRIAL' }, language: 'it', price });
  const redeem = activatePage({ deviceId: 'FP86-XARB-9JZW', language: 'it' });

  assert.ok(buy.includes('href="/ativar?device=FP86-XARB-9JZW&amp;lang=it"'));
  assert.ok(redeem.includes('href="/comprar?device=FP86-XARB-9JZW&amp;lang=it"'));
});

test('an active device is offered neither payment nor a key', () => {
  const page = buyPage({
    deviceId: 'FP86-XARB-9JZW',
    device: { status: 'ACTIVE', expires_at: '2028-08-07T12:00:00Z' },
    language: 'pt',
    price,
  });

  assert.ok(page.includes('já está ativo'));
  assert.ok(!page.includes('/ativar?device='), 'an owner does not need a key');
});

test('every new string exists in all four languages', () => {
  for (const language of ['pt', 'en', 'de', 'it']) {
    const pages = [
      buyPage({ deviceId: null, language, price }),
      buyPage({ deviceId: 'FP86-XARB-9JZW', device: { status: 'TRIAL' }, language, price }),
      activatePage({ deviceId: null, language }),
      thanksPage({ language, state: 'paid', deviceId: 'FP86-XARB-9JZW' }),
    ];
    for (const page of pages) {
      assert.ok(!page.includes('undefined'), `${language} has a missing string`);
      assert.ok(!/https?:\/\//.test(page), `${language} references another host`);
    }
  }
});
