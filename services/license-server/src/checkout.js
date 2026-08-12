/**
 * Creating a Stripe Checkout session.
 *
 * The purchase page asks this endpoint for a payment link rather than talking to Stripe itself, for
 * one reason: the price would otherwise live in the browser, where anyone can change it before it
 * is sent. Deciding the amount here means a customer cannot pay nine cents for a two-year licence.
 *
 * The device id travels in the session's metadata and comes back on the webhook. That is the whole
 * link between "somebody paid" and "this machine may run" — without it a payment arrives with no
 * way to know what it bought.
 */

/**
 * What the product costs, by currency.
 *
 * In the smallest unit, as Stripe expects: 990 is €9,90. Written out per currency rather than
 * converted, because a price is a decision and not an exchange rate — 99,90 in Brazil is deliberate.
 */
export const PRICES = Object.freeze({
  eur: { amount: 990, label: '€9,90' },
  usd: { amount: 990, label: '$9.90' },
  brl: { amount: 9990, label: 'R$99,90' },
});

/**
 * The immutable product contract copied into every Checkout Session.
 *
 * Webhooks validate these values before granting access. A signed Stripe event proves that Stripe
 * sent it; these fields prove that the event is for the product this Worker actually sells.
 */
export const LICENSE_PRODUCT = Object.freeze({
  id: 'iptvburo-device-2y',
  grantDays: 730,
});

/** The default when nothing better is known. */
const DEFAULT_CURRENCY = 'eur';

/**
 * Stripe's own locale codes.
 *
 * Their Checkout page renders in the locale given here. 'auto' would use the browser's, which is the
 * thing the explicit language choice exists to override — the customer running the app in English on
 * a Portuguese Windows.
 */
const STRIPE_LOCALE = { pt: 'pt-BR', en: 'en', de: 'de', it: 'it' };

/**
 * What the customer sees on Stripe's page.
 *
 * Translated, because this is the line item on the payment screen and the receipt afterwards. A
 * German customer reading a Portuguese product description at the moment of paying has been handed
 * a reason to stop.
 */
const PRODUCT_COPY = {
  pt: {
    name: 'IPTV BURO — 2 anos',
    description: 'Ativação de um dispositivo por 2 anos. Inclui todas as atualizações do período.',
  },
  en: {
    name: 'IPTV BURO — 2 years',
    description: 'Activates one device for 2 years. Includes every update in that period.',
  },
  de: {
    name: 'IPTV BURO — 2 Jahre',
    description: 'Aktiviert ein Gerät für 2 Jahre. Alle Updates in diesem Zeitraum inbegriffen.',
  },
  it: {
    name: 'IPTV BURO — 2 anni',
    description: 'Attiva un dispositivo per 2 anni. Include tutti gli aggiornamenti del periodo.',
  },
};

function productName(language) {
  return (PRODUCT_COPY[language] ?? PRODUCT_COPY.pt).name;
}

function productDescription(language) {
  return (PRODUCT_COPY[language] ?? PRODUCT_COPY.pt).description;
}

/**
 * Picks a currency from the country Cloudflare reports.
 *
 * A guess, and a safe one: the customer sees the amount before paying, and Stripe shows their card's
 * own currency at the end. Getting it wrong costs a moment of confusion, not a wrong charge.
 */
export function currencyForCountry(country) {
  if (country === 'BR') return 'brl';
  if (country === 'US' || country === 'CA') return 'usd';
  return DEFAULT_CURRENCY;
}

export function priceFor(currency) {
  return PRICES[currency] ?? PRICES[DEFAULT_CURRENCY];
}

/** Unlike [priceFor], this never falls back. Webhook validation must reject an unknown currency. */
export function exactPriceFor(currency) {
  return PRICES[String(currency ?? '').toLowerCase()] ?? null;
}

/**
 * Creates a Checkout session and returns the URL to send the customer to.
 *
 * Called by the purchase page. Uses Stripe's REST API directly rather than their SDK, which expects
 * Node's crypto and HTTP stack and does not run in a Worker.
 *
 * @param {string} deviceId the machine being activated, validated before it gets here
 * @param {string} currency one of the keys of PRICES
 * @param {object} env Worker environment, for STRIPE_SECRET_KEY and the return URLs
 * @param {string} origin where to send the customer back to, taken from the request they arrived on
 * @param {string} language the language the customer chose in the app, for the Stripe page itself
 */
export async function createCheckoutSession(deviceId, currency, env, origin, language = 'pt') {
  // Checked before the call rather than letting Stripe answer 401. An unconfigured server is a
  // deployment that has not finished, and it is worth telling apart from Stripe being down.
  if (!env.STRIPE_SECRET_KEY) {
    console.error('checkout attempted with no STRIPE_SECRET_KEY set');
    return null;
  }

  const price = priceFor(currency);

  // Derived from the incoming request rather than written down. A hardcoded address is one more
  // place to update when the domain changes, and the failure it causes — paying successfully and
  // landing on a dead page — is both invisible in testing and the worst possible moment for it.
  const returnTo = origin || env.PUBLIC_ORIGIN;

  // Stripe's API takes form encoding, not JSON, including for nested fields.
  const form = new URLSearchParams({
    mode: 'payment',
    'line_items[0][price_data][currency]': currency,
    'line_items[0][price_data][unit_amount]': String(price.amount),
    'line_items[0][price_data][product_data][name]': productName(language),
    'line_items[0][price_data][product_data][description]': productDescription(language),
    'line_items[0][quantity]': '1',

    // Stripe's own page — the buttons, the card fields, the receipt — in the same language as the
    // page the customer just left. Without this the flow is bilingual at the exact step where
    // hesitation costs the sale.
    locale: STRIPE_LOCALE[language] ?? 'auto',

    // On the session, so the webhook that confirms payment knows which machine to activate.
    'metadata[device_id]': deviceId,
    'metadata[product_id]': LICENSE_PRODUCT.id,
    'metadata[grant_days]': String(LICENSE_PRODUCT.grantDays),
    'metadata[currency]': currency,
    'metadata[amount_minor]': String(price.amount),
    // And on the payment intent, so a refund — which arrives naming a charge, not a session — can
    // also find its way back to the device. Without this, refunds could not be honoured
    // automatically.
    'payment_intent_data[metadata][device_id]': deviceId,
    'payment_intent_data[metadata][product_id]': LICENSE_PRODUCT.id,
    'payment_intent_data[metadata][grant_days]': String(LICENSE_PRODUCT.grantDays),
    'payment_intent_data[metadata][currency]': currency,
    'payment_intent_data[metadata][amount_minor]': String(price.amount),

    // Stripe replaces this literal placeholder after Checkout. The return page uses the id only to
    // read our own payment ledger; opening /obrigado by hand can therefore never claim success.
    success_url:
      `${returnTo}/obrigado?device=${encodeURIComponent(deviceId)}`
      + `&lang=${encodeURIComponent(language)}`
      + '&session_id={CHECKOUT_SESSION_ID}',
    cancel_url:
      `${returnTo}/comprar?device=${encodeURIComponent(deviceId)}`
      + `&lang=${encodeURIComponent(language)}`,

    // Collected because tax rules in the EU require it and a receipt needs somewhere to go. Nothing
    // else about the customer is asked for or stored here.
    'customer_creation': 'always',
  });

  const response = await fetch('https://api.stripe.com/v1/checkout/sessions', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${env.STRIPE_SECRET_KEY}`,
      'content-type': 'application/x-www-form-urlencoded',
      // Stripe retries safely against this: a customer who double-clicks does not get two sessions.
      'idempotency-key': `checkout-${deviceId}-${Math.floor(Date.now() / 60000)}`,
    },
    body: form,
  });

  if (!response.ok) {
    // The body can carry the request that failed, including the key in some error shapes, so only
    // the status is recorded.
    console.error('stripe session failed', response.status);
    return null;
  }

  const session = await response.json();
  if (!session?.id || !session?.url) return null;

  return {
    id: session.id,
    url: session.url,
    paymentIntentId:
      typeof session.payment_intent === 'string'
        ? session.payment_intent
        : session.payment_intent?.id ?? null,
  };
}
