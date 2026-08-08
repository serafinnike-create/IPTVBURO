/**
 * Which language the site speaks.
 *
 * The app knows something no HTTP header can express: which language the customer chose *in the
 * app*. Somebody running IPTV BURO in English on a Portuguese Windows sends `Accept-Language: pt`
 * with every request, and would land on a Portuguese payment page.
 *
 * It matters most for the QR code, where the page opens on a phone that may have nothing to do with
 * the computer being licensed.
 *
 * The chain has to hold end to end — purchase page, Stripe, and the page they come back to. A
 * language that survives two hops and is lost on the third is a purchase that ends in the wrong
 * language, which is the last impression the customer keeps.
 */

import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { activatePage, buyPage, homePage, languageForRequest, languageFrom } from '../src/pages.js';

const checkoutSource = readFileSync(new URL('../src/checkout.js', import.meta.url), 'utf8');
const indexSource = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');

/** A request carrying only the header the browser would send. */
function request(acceptLanguage) {
  return { headers: { get: (name) => (name === 'accept-language' ? acceptLanguage : null) } };
}

const price = { amount: 990, label: '€9,90' };

function buy(language) {
  return buyPage({
    deviceId: 'FP86-XARB-9JZW',
    device: { status: 'TRIAL' },
    language,
    currency: 'eur',
    price,
  });
}

test('an explicit language wins over the browser header', () => {
  const url = new URL('https://example.com/comprar?device=FP86-XARB-9JZW&lang=en');

  assert.equal(languageForRequest(request('pt-BR,pt;q=0.9'), url), 'en');
});

test('every shipped language can be requested explicitly', () => {
  for (const language of ['pt', 'en', 'de', 'it']) {
    const url = new URL(`https://example.com/comprar?lang=${language}`);
    assert.equal(languageForRequest(request('pt-BR'), url), language);
  }
});

test('a regional tag is accepted', () => {
  // The app sends its own tags, which are regional: pt-BR, not pt.
  const url = new URL('https://example.com/comprar?lang=pt-BR');

  assert.equal(languageForRequest(request('en'), url), 'pt');
});

/**
 * An unusable value falls back to the header, not to Portuguese.
 *
 * A mangled link should still produce the best available guess rather than throwing away the one
 * piece of information that did arrive intact.
 */
test('an unsupported or malformed language falls back to the header', () => {
  for (const bad of ['ja', '', 'xx-YY', '../../etc', '<script>']) {
    const url = new URL(`https://example.com/comprar?lang=${encodeURIComponent(bad)}`);
    assert.equal(languageForRequest(request('de-DE'), url), 'de', `failed for "${bad}"`);
  }
});

test('with no parameter at all the header still decides', () => {
  const url = new URL('https://example.com/comprar');

  assert.equal(languageForRequest(request('it-IT'), url), 'it');
  assert.equal(languageForRequest(request(null), url), 'pt');
});

test('the header parser is unchanged', () => {
  assert.equal(languageFrom('en-GB,en;q=0.9'), 'en');
  assert.equal(languageFrom('ja-JP'), 'pt');
});

/**
 * Every form carries the language forward.
 *
 * A POST has no query string. Without a hidden field, submitting any form on the site silently
 * reverts to the browser's language — exactly the case the explicit choice exists to override.
 */
test('the buy form carries the language', () => {
  const page = buy('en');

  assert.ok(page.includes('name="lang"'), 'the buy form must carry the language');
  assert.ok(page.includes('value="en"'), 'and the right one');
});

test('the activation form carries the language', () => {
  const page = activatePage({ deviceId: 'FP86-XARB-9JZW', language: 'de' });

  assert.ok(page.includes('name="lang"'));
  assert.ok(page.includes('value="de"'));
});

test('links between pages keep the language', () => {
  const page = homePage({ language: 'it' });

  assert.ok(page.includes('/ativar?lang=it'), 'the activation link must not drop it');
});

test('the language survives the round trip through Stripe', () => {
  // The customer leaves the site entirely and comes back. Both return addresses must carry it, or
  // a successful purchase ends on a page in a language they did not choose.
  assert.ok(/success_url:[\s\S]{0,200}lang=/.test(checkoutSource), 'success_url drops the language');
  assert.ok(/cancel_url:[\s\S]{0,200}lang=/.test(checkoutSource), 'cancel_url drops the language');
});

/**
 * Stripe's own page is in the same language.
 *
 * Their Checkout page defaults to the browser's locale — the thing being overridden. Leaving it
 * would make the flow bilingual at precisely the step where hesitation costs the sale.
 */
test('stripe checkout is asked for the right locale', () => {
  assert.ok(checkoutSource.includes('locale:'), 'Stripe must be told which language to render in');
  assert.ok(checkoutSource.includes("pt: 'pt-BR'"), 'Brazilian Portuguese, not European');
});

test('the product name and description are translated', () => {
  // This is the line item on the payment screen and on the receipt afterwards.
  for (const fragment of ['2 years', '2 Jahre', '2 anni', '2 anos']) {
    assert.ok(checkoutSource.includes(fragment), `missing product copy: ${fragment}`);
  }
});

test('the checkout handler passes the language on', () => {
  assert.ok(
    /createCheckoutSession\([^)]*language/.test(indexSource),
    'the language must reach createCheckoutSession',
  );
});

test('a submitted language is validated before use', () => {
  // It arrives from a form field, so it is customer-controlled and reaches a URL and Stripe's API.
  assert.ok(
    indexSource.includes("['pt', 'en', 'de', 'it'].includes(submitted)"),
    'submitted language must be checked against the shipped set',
  );
});

test('pages render fully in the requested language', () => {
  const expectations = {
    pt: 'Pagar com cartão',
    en: 'Pay by card',
    de: 'Mit Karte bezahlen',
    it: 'Paga con carta',
  };

  for (const [language, phrase] of Object.entries(expectations)) {
    const page = buy(language);
    assert.ok(page.includes(`lang="${language}"`), `${language} did not render`);
    assert.ok(page.includes(phrase), `${language} is missing its own buy button text`);
    assert.ok(!page.includes('undefined'), `${language} has a missing string`);
  }
});
