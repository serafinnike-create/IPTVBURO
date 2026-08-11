import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import {
  acknowledgeGooglePlayPurchase,
  inspectGooglePlayPurchase,
  openGooglePurchaseToken,
  purchaseTokenHash,
  sealGooglePurchaseToken,
  validateGooglePlayPurchaseResource,
} from '../src/google-play.js';

const ACCOUNT_ID = 'a'.repeat(64);
const PURCHASE_TOKEN = 'opaque-google-purchase-token-fixture-0001';

function resource(overrides = {}) {
  return {
    productLineItem: [{
      productId: 'iptvburo_730_days',
      productOfferDetails: {
        purchaseOptionId: 'rent_730_days',
        rentOfferDetails: {},
        quantity: 1,
        refundableQuantity: 1,
        consumptionState: 'CONSUMPTION_STATE_YET_TO_BE_CONSUMED',
      },
    }],
    purchaseStateContext: { purchaseState: 'PURCHASED' },
    obfuscatedExternalAccountId: ACCOUNT_ID,
    purchaseCompletionTime: '2026-08-10T08:00:00Z',
    acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    ...overrides,
  };
}

function config(overrides = {}) {
  return {
    packageName: 'com.lucasserafin94.iptvburo',
    productId: 'iptvburo_730_days',
    purchaseOptionId: 'rent_730_days',
    serviceAccountEmail: 'fixture@example.iam.gserviceaccount.com',
    privateKey: 'configured-by-network-test',
    acceptTestPurchases: false,
    ...overrides,
  };
}

test('only the server-owned 730-day rental option can become a Play entitlement', () => {
  const valid = validateGooglePlayPurchaseResource(resource(), ACCOUNT_ID, config());
  assert.equal(valid.ok, true);
  assert.equal(valid.state, 'PURCHASED');
  assert.equal(valid.refundableQuantity, 1);
  assert.equal(valid.completionTime.toISOString(), '2026-08-10T08:00:00.000Z');

  assert.equal(
    validateGooglePlayPurchaseResource(resource({ obfuscatedExternalAccountId: 'b'.repeat(64) }), ACCOUNT_ID, config()).reason,
    'purchase_account_mismatch',
  );
  assert.equal(
    validateGooglePlayPurchaseResource(resource({ purchaseStateContext: { purchaseState: 'PENDING' }, purchaseCompletionTime: undefined }), ACCOUNT_ID, config()).state,
    'PENDING',
  );
  assert.equal(
    validateGooglePlayPurchaseResource(resource({ productLineItem: [{
      productId: 'iptvburo_730_days',
      productOfferDetails: { purchaseOptionId: 'buy_forever', quantity: 1 },
    }] }), ACCOUNT_ID, config()).reason,
    'wrong_purchase_option',
  );
  assert.equal(
    validateGooglePlayPurchaseResource(resource({ testPurchaseContext: { fopType: 'TEST' } }), ACCOUNT_ID, config()).reason,
    'test_purchase_forbidden',
  );
  const refunded = resource();
  refunded.productLineItem[0].productOfferDetails.refundableQuantity = 0;
  assert.equal(validateGooglePlayPurchaseResource(refunded, ACCOUNT_ID, config()).state, 'REFUNDED');
  const consumed = resource();
  consumed.productLineItem[0].productOfferDetails.consumptionState = 'CONSUMPTION_STATE_CONSUMED';
  assert.equal(
    validateGooglePlayPurchaseResource(consumed, ACCOUNT_ID, config()).reason,
    'unexpected_consumption_state',
  );
});

test('purchase tokens are hashed for identity and encrypted before persistence', async () => {
  const key = crypto.getRandomValues(new Uint8Array(32));
  const keyBase64 = Buffer.from(key).toString('base64');
  const envelope = await sealGooglePurchaseToken(PURCHASE_TOKEN, keyBase64);
  assert.match(envelope, /^v1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
  assert.ok(!envelope.includes(PURCHASE_TOKEN));
  assert.equal(await openGooglePurchaseToken(envelope, keyBase64), PURCHASE_TOKEN);
  assert.equal((await purchaseTokenHash(PURCHASE_TOKEN)).length, 43);

  const tampered = `${envelope.slice(0, -1)}${envelope.endsWith('A') ? 'B' : 'A'}`;
  await assert.rejects(openGooglePurchaseToken(tampered, keyBase64));
});

test('the Worker authenticates as a service account and verifies directly with Google', async (t) => {
  const pair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const privateDer = await crypto.subtle.exportKey('pkcs8', pair.privateKey);
  const privateKey =
    '-----BEGIN PRIVATE KEY-----\n'
    + Buffer.from(privateDer).toString('base64').match(/.{1,64}/g).join('\n')
    + '\n-----END PRIVATE KEY-----';
  const env = {
    GOOGLE_PLAY_PACKAGE_NAME: 'com.lucasserafin94.iptvburo',
    GOOGLE_PLAY_PRODUCT_ID: 'iptvburo_730_days',
    GOOGLE_PLAY_PURCHASE_OPTION_ID: 'rent_730_days',
    GOOGLE_PLAY_ACCEPT_TEST_PURCHASES: 'false',
    GOOGLE_SERVICE_ACCOUNT_EMAIL: 'fixture@example.iam.gserviceaccount.com',
    GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY: privateKey,
  };
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options = {}) => {
    calls.push({ url: String(url), options });
    if (String(url) === 'https://oauth2.googleapis.com/token') {
      assert.match(String(options.body), /grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer/);
      return Response.json({ access_token: 'fixture-access-token-with-safe-length' });
    }
    if (String(url).includes('/purchases/productsv2/tokens/')) return Response.json(resource());
    if (String(url).endsWith(':acknowledge')) return new Response('', { status: 200 });
    throw new Error('UnexpectedGoogleRequest');
  });

  const inspected = await inspectGooglePlayPurchase(PURCHASE_TOKEN, ACCOUNT_ID, env);
  assert.equal(inspected.ok, true);
  assert.equal(inspected.state, 'PURCHASED');
  assert.equal(inspected.accessToken, 'fixture-access-token-with-safe-length');
  assert.equal(calls.length, 2);
  assert.match(calls[1].options.headers.authorization, /^Bearer /);

  assert.equal(
    await acknowledgeGooglePlayPurchase(PURCHASE_TOKEN, inspected.accessToken, env),
    true,
  );
  assert.equal(calls.length, 3);
  assert.equal(calls[2].options.method, 'POST');
});
