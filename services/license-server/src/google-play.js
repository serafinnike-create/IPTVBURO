/**
 * Server-side Google Play purchase verification.
 *
 * The Android client is only a courier for the opaque purchase token. Google is queried from the
 * Worker with a service account, and only the configured PURCHASED buy option can become an
 * entitlement. The Worker consumes the purchase only after durably granting 730 days so the user
 * can buy another term later. Tokens are never logged and are encrypted before D1 persistence.
 */

const ANDROID_PUBLISHER_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const GOOGLE_OAUTH_AUDIENCE = 'https://oauth2.googleapis.com/token';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const TOKEN_AAD = new TextEncoder().encode('iptvburo-google-play-token-v1');
const PURCHASE_STATES = new Set(['PURCHASED', 'PENDING', 'CANCELLED']);

export async function inspectGooglePlayPurchase(
  purchaseToken,
  expectedAccountId,
  env,
  reusableAccessToken = null,
) {
  const token = validPurchaseToken(purchaseToken);
  if (!token) return { ok: false, reason: 'bad_purchase_token' };
  if (!validObfuscatedAccountId(expectedAccountId)) {
    return { ok: false, reason: 'bad_account_id' };
  }

  const config = googlePlayConfiguration(env);
  if (!config) return { ok: false, reason: 'google_play_unconfigured', retryable: true };
  const accessToken = reusableAccessToken || await serviceAccountAccessToken(config);
  if (!accessToken) return { ok: false, reason: 'google_auth_failed', retryable: true };

  const endpoint =
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
    + `${encodeURIComponent(config.packageName)}/purchases/productsv2/tokens/`
    + encodeURIComponent(token);
  const response = await fetch(endpoint, {
    headers: { authorization: `Bearer ${accessToken}`, accept: 'application/json' },
  });
  if (!response.ok) {
    const reason = response.status === 404
      ? 'purchase_not_found'
      : response.status === 400
        ? 'purchase_token_rejected'
        : response.status === 401 || response.status === 403
          ? 'google_api_denied'
          : 'google_api_failed';
    return {
      ok: false,
      reason,
      retryable: response.status === 429 || response.status >= 500,
    };
  }

  const resource = await response.json();
  const validated = validateGooglePlayPurchaseResource(resource, expectedAccountId, config);
  if (!validated.ok) return validated;
  return {
    ...validated,
    purchaseTokenHash: await purchaseTokenHash(token),
    accessToken,
  };
}

export function validateGooglePlayPurchaseResource(resource, expectedAccountId, configOrEnv) {
  const config = configOrEnv?.packageName ? configOrEnv : googlePlayConfiguration(configOrEnv);
  if (!config) return { ok: false, reason: 'google_play_unconfigured', retryable: true };

  const purchaseState = String(resource?.purchaseStateContext?.purchaseState ?? '');
  if (!PURCHASE_STATES.has(purchaseState)) return { ok: false, reason: 'bad_purchase_state' };
  if (resource?.obfuscatedExternalAccountId !== expectedAccountId) {
    return { ok: false, reason: 'purchase_account_mismatch' };
  }
  if (resource?.testPurchaseContext && !config.acceptTestPurchases) {
    return { ok: false, reason: 'test_purchase_forbidden' };
  }

  const lines = Array.isArray(resource?.productLineItem) ? resource.productLineItem : [];
  if (lines.length !== 1) return { ok: false, reason: 'unexpected_line_items' };
  const line = lines[0];
  const offer = line?.productOfferDetails;
  if (line?.productId !== config.productId) return { ok: false, reason: 'wrong_product' };
  if (offer?.purchaseOptionId !== config.purchaseOptionId) {
    return { ok: false, reason: 'wrong_purchase_option' };
  }
  if (offer?.rentOfferDetails || offer?.preorderOfferDetails) {
    return { ok: false, reason: 'buy_purchase_required' };
  }
  if (Number(offer?.quantity ?? 1) !== 1) return { ok: false, reason: 'bad_quantity' };
  if (purchaseState === 'PURCHASED' && offer?.refundableQuantity === undefined) {
    return { ok: false, reason: 'missing_refundable_quantity' };
  }
  const refundableQuantity = Number(offer?.refundableQuantity ?? 1);
  if (!Number.isInteger(refundableQuantity) || refundableQuantity < 0 || refundableQuantity > 1) {
    return { ok: false, reason: 'bad_refundable_quantity' };
  }
  const consumptionState = String(offer?.consumptionState ?? '');
  if (
    purchaseState === 'PURCHASED' &&
    consumptionState !== 'CONSUMPTION_STATE_YET_TO_BE_CONSUMED' &&
    consumptionState !== 'CONSUMPTION_STATE_CONSUMED'
  ) {
    return { ok: false, reason: 'unexpected_consumption_state' };
  }

  const completion = resource?.purchaseCompletionTime;
  const completionTime = typeof completion === 'string' ? new Date(completion) : null;
  if (purchaseState === 'PURCHASED' && (!completionTime || Number.isNaN(completionTime.getTime()))) {
    return { ok: false, reason: 'missing_completion_time' };
  }

  // Google keeps the purchase state and exposes full/partial refunds through this quantity. This
  // product is forced to quantity one, so zero means the entitlement was fully refunded.
  const state = purchaseState === 'PURCHASED' && refundableQuantity === 0
    ? 'REFUNDED'
    : purchaseState;

  return {
    ok: true,
    state,
    productId: config.productId,
    purchaseOptionId: config.purchaseOptionId,
    accountId: expectedAccountId,
    completionTime,
    refundableQuantity,
    acknowledgementState: String(resource?.acknowledgementState ?? ''),
    consumptionState,
    testPurchase: Boolean(resource?.testPurchaseContext),
  };
}

export async function consumeGooglePlayPurchase(purchaseToken, accessToken, env) {
  const token = validPurchaseToken(purchaseToken);
  const config = googlePlayConfiguration(env);
  if (!token || !accessToken || !config) return false;
  const endpoint =
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
    + `${encodeURIComponent(config.packageName)}/purchases/products/`
    + `${encodeURIComponent(config.productId)}/tokens/${encodeURIComponent(token)}:consume`;
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${accessToken}`,
      'content-type': 'application/json',
    },
    body: '{}',
  });
  return response.ok;
}

export async function acknowledgeGooglePlayPurchase(purchaseToken, accessToken, env) {
  const token = validPurchaseToken(purchaseToken);
  const config = googlePlayConfiguration(env);
  if (!token || !accessToken || !config) return false;
  const endpoint =
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
    + `${encodeURIComponent(config.packageName)}/purchases/products/`
    + `${encodeURIComponent(config.productId)}/tokens/${encodeURIComponent(token)}:acknowledge`;
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${accessToken}`,
      'content-type': 'application/json',
    },
    body: '{}',
  });
  return response.ok;
}

export async function purchaseTokenHash(purchaseToken) {
  const token = validPurchaseToken(purchaseToken);
  if (!token) throw new Error('InvalidGooglePurchaseToken');
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
  return base64Url(new Uint8Array(digest));
}

export async function sealGooglePurchaseToken(purchaseToken, encryptionKeyBase64) {
  const token = validPurchaseToken(purchaseToken);
  const keyBytes = decodeBase64(encryptionKeyBase64);
  if (!token || !keyBytes || keyBytes.byteLength !== 32) throw new Error('InvalidGoogleTokenKey');
  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: TOKEN_AAD },
    key,
    new TextEncoder().encode(token),
  );
  return `v1.${base64Url(iv)}.${base64Url(new Uint8Array(ciphertext))}`;
}

export async function openGooglePurchaseToken(envelope, encryptionKeyBase64) {
  const parts = typeof envelope === 'string' ? envelope.split('.') : [];
  const keyBytes = decodeBase64(encryptionKeyBase64);
  const iv = parts.length === 3 ? decodeBase64Url(parts[1]) : null;
  const ciphertext = parts.length === 3 ? decodeBase64Url(parts[2]) : null;
  if (parts[0] !== 'v1' || !keyBytes || keyBytes.byteLength !== 32 || iv?.byteLength !== 12 || !ciphertext) {
    throw new Error('InvalidGoogleTokenEnvelope');
  }
  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['decrypt']);
  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv, additionalData: TOKEN_AAD },
    key,
    ciphertext,
  );
  const token = new TextDecoder().decode(plaintext);
  if (!validPurchaseToken(token)) throw new Error('InvalidGooglePurchaseToken');
  return token;
}

function googlePlayConfiguration(env) {
  const packageName = String(env?.GOOGLE_PLAY_PACKAGE_NAME ?? '').trim();
  const productId = String(env?.GOOGLE_PLAY_PRODUCT_ID ?? '').trim();
  const purchaseOptionId = String(env?.GOOGLE_PLAY_PURCHASE_OPTION_ID ?? '').trim();
  const serviceAccountEmail = String(env?.GOOGLE_SERVICE_ACCOUNT_EMAIL ?? '').trim();
  const privateKey = String(env?.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY ?? '').replaceAll('\\n', '\n').trim();
  if (
    !/^[A-Za-z0-9_.]+(?:\.[A-Za-z0-9_.]+)+$/.test(packageName) ||
    !/^[A-Za-z0-9_.-]{3,128}$/.test(productId) ||
    !/^[A-Za-z0-9_.-]{1,128}$/.test(purchaseOptionId) ||
    !/^[^\s@]+@[^\s@]+\.iam\.gserviceaccount\.com$/.test(serviceAccountEmail) ||
    !privateKey.includes('BEGIN PRIVATE KEY')
  ) {
    return null;
  }
  return {
    packageName,
    productId,
    purchaseOptionId,
    serviceAccountEmail,
    privateKey,
    acceptTestPurchases: String(env?.GOOGLE_PLAY_ACCEPT_TEST_PURCHASES ?? '').toLowerCase() === 'true',
  };
}

async function serviceAccountAccessToken(config) {
  try {
    const now = Math.floor(Date.now() / 1000);
    const header = base64Url(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
    const claims = base64Url(new TextEncoder().encode(JSON.stringify({
      iss: config.serviceAccountEmail,
      scope: ANDROID_PUBLISHER_SCOPE,
      aud: GOOGLE_OAUTH_AUDIENCE,
      iat: now,
      exp: now + 3_600,
    })));
    const signingInput = `${header}.${claims}`;
    const privateKey = await crypto.subtle.importKey(
      'pkcs8',
      pemPrivateKeyBytes(config.privateKey),
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['sign'],
    );
    const signature = await crypto.subtle.sign(
      'RSASSA-PKCS1-v1_5',
      privateKey,
      new TextEncoder().encode(signingInput),
    );
    const assertion = `${signingInput}.${base64Url(new Uint8Array(signature))}`;
    const response = await fetch(TOKEN_ENDPOINT, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion,
      }),
    });
    if (!response.ok) return null;
    const body = await response.json();
    return typeof body?.access_token === 'string' && body.access_token.length >= 20
      ? body.access_token
      : null;
  } catch {
    return null;
  }
}

function validPurchaseToken(value) {
  if (typeof value !== 'string' || value.length < 16 || value.length > 4_096) return null;
  return /^[\x21-\x7E]+$/.test(value) ? value : null;
}

function validObfuscatedAccountId(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}

function pemPrivateKeyBytes(pem) {
  const base64 = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '');
  const decoded = decodeBase64(base64);
  if (!decoded) throw new Error('InvalidGooglePrivateKey');
  return decoded;
}

function decodeBase64(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) return null;
  try {
    const binary = atob(value);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function decodeBase64Url(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]+$/.test(value)) return null;
  const standard = value.replaceAll('-', '+').replaceAll('_', '/');
  return decodeBase64(standard.padEnd(Math.ceil(standard.length / 4) * 4, '='));
}

function base64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}
