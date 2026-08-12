import { signLicense } from './signing.js';
import {
  consumeGooglePlayPurchase,
  inspectGooglePlayPurchase,
  openGooglePurchaseToken,
  purchaseTokenHash,
  sealGooglePurchaseToken,
} from './google-play.js';
import {
  createCheckoutSession,
  currencyForCountry,
  exactPriceFor,
  LICENSE_PRODUCT,
  priceFor,
} from './checkout.js';
import {
  activatePage,
  buyPage,
  checkoutUnavailablePage,
  copyFor,
  homePage,
  languageForRequest,
  thanksPage,
} from './pages.js';
import { adminPage } from './admin-page.js';
import {
  archiveDevice,
  cancelKey,
  createKeys,
  deviceDetails,
  devicesByStatus,
  grantDevice,
  isAdmin,
  listKeys,
  paidDevices,
  revokeDevice,
  restoreDevice,
  searchDevices,
  summary,
} from './admin.js';

/**
 * IPTV BURO licence server.
 *
 * The app asks this service, at every launch, whether it may run. Everything the client stores is
 * signed here, so a customer can read and copy their licence file and gain nothing by it.
 *
 * ## The rules, in one place
 *
 * A device the server has never seen gets seven days. Payment turns that into two years. A refund
 * revokes it immediately. Manual grants and redemption keys exist for the cases that are not a card
 * payment — a friend trying it, someone who paid cash.
 *
 * The trial is measured **by this server's clock**. That is the whole reason the trial cannot be
 * extended by changing a computer's date: the date the client reports is never read.
 *
 * ## What is deliberately not here
 *
 * No customer names, no email addresses, no card details. Stripe holds those, and it is where a
 * refund conversation happens anyway. This service knows a device identifier and what it is owed.
 */

/** Seven free days. Matches LicensePolicy.TRIAL_DURATION in the client. */
const TRIAL_DAYS = 7;

/** Two years, as the product sells. Matches LicensePolicy.PAID_DURATION. */
const PAID_DAYS = LICENSE_PRODUCT.grantDays;

/** Java Instant's UTC wire shape. Rejects Date.parse quirks such as "0" becoming January 2000. */
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;

/** A crashed event claim can be retried instead of remaining PROCESSING for ever. */
const STRIPE_EVENT_CLAIM_TIMEOUT_MS = 5 * 60 * 1000;

/** Valid proof nonces remain remembered long enough to make captured requests useless. */
const DEVICE_PROOF_RETENTION_DAYS = 30;

/** Opportunistic cleanup is bounded so a licence check never turns into an unbounded delete. */
const DEVICE_PROOF_CLEANUP_LIMIT = 100;

/** Public requests are intentionally tiny; reject oversized bodies before parsing or signing. */
const MAX_DEVICE_API_BODY_BYTES = 32 * 1024;
const MAX_FORM_BODY_BYTES = 64 * 1024;
const MAX_STRIPE_WEBHOOK_BODY_BYTES = 512 * 1024;

/** Hourly refund reconciliation is bounded so one cron invocation cannot exhaust Worker limits. */
const GOOGLE_PLAY_RECONCILIATION_LIMIT = 50;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Only the browser-facing public app endpoints need CORS. Admin, Checkout, Stripe and Google
    // purchase verification are same-origin or native/server-to-server, so advertising them
    // cross-origin would widen the attack surface without enabling a product capability.
    if (request.method === 'OPTIONS') {
      if (!PUBLIC_CORS_PATHS.has(url.pathname)) {
        return new Response(null, { status: 405, headers: { 'cache-control': 'no-store' } });
      }
      return new Response(null, {
        status: 204,
        headers: { ...corsHeaders(), 'cache-control': 'no-store' },
      });
    }

    const limited = await enforceRouteRateLimit(request, env, url);
    if (limited) return PUBLIC_CORS_PATHS.has(url.pathname) ? withCors(limited) : limited;

    try {
      switch (url.pathname) {
        case '/v1/register':
          return withCors(await handleRegister(request, env));
        case '/v1/validate':
          return withCors(await handleValidate(request, env));
        case '/v1/redeem':
          return withCors(await handleRedeem(request, env));
        case '/v1/key-info':
          return withCors(await handleKeyInfo(request, env));
        case '/v1/google-play/purchase':
          return withCors(await handleGooglePlayPurchase(request, env));
        case '/v1/signing-key-check':
          return await handleSigningKeyCheck(request, env);
        case '/v1/stripe-webhook':
          return await handleStripeWebhook(request, env);

        // The customer-facing pages. Served from the same origin as the API so there is no CORS to
        // configure and nothing to keep in sync with a separate static host.
        case '/':
          return await handleHome(request, env);
        case '/comprar':
        case '/buy':
          return await handleBuyPage(request, env);
        case '/checkout':
          return await handleCheckout(request, env);
        case '/ativar':
        case '/activate':
          return await handleActivatePage(request, env);
        case '/obrigado':
        case '/thanks':
          return await handleThanksPage(request, env);

        case '/health':
          return json({ ok: true, time: new Date().toISOString() });

        // What this visitor would actually be charged.
        //
        // The app asked this question of the operating system and got a different answer: Windows
        // reports the machine's locale, which follows where somebody is *from*, while the charge
        // follows where the request comes *from*. A customer with a Brazilian Windows sitting in
        // Portugal saw R$99,90 in the app and €9,90 on the payment page — the price changing between
        // the click and the checkout, which is the moment trust is most easily lost.
        //
        // Public and unauthenticated: it reveals a price list that is printed on the buy page anyway.
        case '/v1/price':
          return withCors(await handlePrice(request));

        // The admin panel. Every route below the page itself checks the token; the page is served
        // to anyone because it is a login box with no data in it.
        case '/admin':
          // Its own policy: this page needs inline script to work, which the customer-facing pages
          // deliberately forbid. Kept separate rather than loosening the shared one, so the payment
          // page — the one that matters — keeps the tighter rule.
          return html(adminPage(), 200, ADMIN_CSP);
        case '/admin/summary':
        case '/admin/search':
        case '/admin/list':
        case '/admin/device':
        case '/admin/grant':
        case '/admin/revoke':
        case '/admin/archive':
        case '/admin/restore':
        case '/admin/keys':
        case '/admin/keys/cancel':
          return await handleAdmin(request, env, url);

        default:
          return json({ error: 'not_found' }, 404);
      }
    } catch (error) {
      // Never the message. An exception from D1 or Stripe can carry a query, a key fragment or a
      // customer's details, and this response goes to whoever asked — including someone probing.
      console.error('request failed', error?.name);
      const failure = json({ error: 'internal' }, 500);
      return PUBLIC_CORS_PATHS.has(url.pathname) ? withCors(failure) : failure;
    }
  },

  async scheduled(_controller, env, context) {
    context.waitUntil(reconcileGooglePlayPurchases(env));
  },
};

/**
 * Every admin action, behind one token check.
 *
 * The check is first and unconditional. Putting it in each handler would work until somebody adds a
 * sixth route and forgets — and the thing being protected is the ability to give the product away.
 */
async function handleAdmin(request, env, url) {
  if (!isAdmin(request, env)) {
    // 401 with no detail. Saying whether the token was missing or merely wrong tells someone
    // probing which of the two they are dealing with.
    return json({ error: 'unauthorized' }, 401);
  }

  switch (url.pathname) {
    case '/admin/summary':
      return json(await summary(env));

    case '/admin/search':
      return json({ devices: await searchDevices(url.searchParams.get('q'), env) });

    case '/admin/device': {
      const deviceId = validDeviceId(url.searchParams.get('device'));
      if (!deviceId) return json({ error: 'bad_device' }, 400);
      const details = await deviceDetails(deviceId, env);
      return details ? json(details) : json({ error: 'not_found' }, 404);
    }

    case '/admin/list': {
      // What the summary counts link to. `paid` is not a status but a question about the ledger,
      // so it is named separately rather than being squeezed into the status filter.
      const wanted = String(url.searchParams.get('status') ?? '').toLowerCase();
      const devices = wanted === 'paid'
        ? await paidDevices(env)
        : await devicesByStatus(wanted, env);
      return json({ devices });
    }

    case '/admin/grant': {
      const body = await readJson(request);
      const deviceId = validDeviceId(body.device);
      if (!deviceId) return json({ error: 'bad_device' }, 400);
      // Bounded: a typo in the days field should not create a licence outliving everyone involved.
      const days = Math.max(1, Math.min(Number(body.days) || 30, 36_500));
      await grantDevice(deviceId, days, String(body.note ?? '').slice(0, 200), env);
      return json({ ok: true, days });
    }

    case '/admin/revoke': {
      const body = await readJson(request);
      const deviceId = validDeviceId(body.device);
      if (!deviceId) return json({ error: 'bad_device' }, 400);
      await revokeDevice(deviceId, String(body.note ?? '').slice(0, 200), env);
      return json({ ok: true });
    }

    case '/admin/archive': {
      const body = await readJson(request);
      const deviceId = validDeviceId(body.device);
      if (!deviceId) return json({ error: 'bad_device' }, 400);
      const archived = await archiveDevice(deviceId, String(body.note ?? '').slice(0, 200), env);
      return archived ? json({ ok: true }) : json({ error: 'not_found' }, 404);
    }

    case '/admin/restore': {
      const body = await readJson(request);
      const deviceId = validDeviceId(body.device);
      if (!deviceId) return json({ error: 'bad_device' }, 400);
      const restored = await restoreDevice(deviceId, env);
      return restored ? json({ ok: true }) : json({ error: 'not_found' }, 404);
    }

    case '/admin/keys/cancel': {
      const body = await readJson(request);
      const cancelled = await cancelKey(body.key, env);
      // 404 rather than a silent success: "already used" and "never existed" both mean the code is
      // still out there doing whatever it was going to do, and the panel should say so.
      return cancelled ? json({ ok: true }) : json({ error: 'not_cancellable' }, 404);
    }

    case '/admin/keys': {
      if (request.method !== 'POST') return json({ keys: await listKeys(env) });
      const body = await readJson(request);
      const days = Math.max(1, Math.min(Number(body.days) || 30, 36_500));
      const count = Math.max(1, Math.min(Number(body.count) || 1, 50));
      const keys = await createKeys(count, days, String(body.note ?? '').slice(0, 200), env);
      return json({ keys });
    }

    default:
      return json({ error: 'not_found' }, 404);
  }
}

/**
 * The price this request would be charged.
 *
 * One authority for a number that appears in three places — the app's blocking screen, the buy page
 * and Stripe — and that must be identical in all of them. Deriving it separately in the client was
 * the bug: two rules disagreeing produced a price that changed when the customer clicked.
 */
async function handlePrice(request) {
  const currency = currencyForCountry(request.cf?.country);
  const price = priceFor(currency);

  return json({
    currency,
    // The formatted label, so the client never has to know how a currency is punctuated. "R$ 99,90"
    // and "€9,90" differ in symbol, separator and placement, and getting that right in four
    // languages is work the client should not repeat.
    label: price.label,
    // The raw amount as well, for anything that needs to compare rather than display.
    amountMinor: price.amount,
    termDays: LICENSE_PRODUCT.grantDays,
  });
}

/**
 * Signs a fixed-purpose challenge without registering a disposable trial device.
 *
 * This is an operational key-pair check, not a licence: clients reject the payload because it has
 * no device or entitlement state. Keeping the schema fixed also avoids exposing an arbitrary
 * signing oracle while still proving that the deployed private key matches the key pinned in apps.
 */
async function handleSigningKeyCheck(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
  const body = await readJson(request);
  const nonce = validProofNonce(body.nonce);
  if (!nonce) return json({ error: 'bad_nonce' }, 400);
  if (!env.SIGNING_KEY) return json({ error: 'signing_unavailable' }, 503);

  const signed = await signLicense(
    { purpose: 'iptvburo-signing-key-check-v1', nonce },
    env.SIGNING_KEY,
  );
  return json({ payload: signed.payload, signature: signed.signature });
}

/**
 * The front page.
 *
 * A visitor with a device in the query string is going to buy, so they are sent to the purchase page
 * rather than being made to read an explanation and find a link. Everyone else gets the explanation.
 */
async function handleHome(request, env) {
  const url = new URL(request.url);
  if (validDeviceId(url.searchParams.get('device'))) return await handleBuyPage(request, env);

  return html(homePage({ language: languageForRequest(request, url) }));
}

/**
 * Redeeming a key on the website.
 *
 * The same operation the app performs, offered here because a key is often given to somebody whose
 * app is already locked — and the locked screen is a bad place to type. Both paths run through the
 * same [handleRedeem] logic, so single-use is enforced in one place rather than two.
 */
async function handleActivatePage(request, env) {
  const url = new URL(request.url);

  if (request.method !== 'POST') {
    return html(activatePage({
      deviceId: validDeviceId(url.searchParams.get('device')),
      language: languageForRequest(request, url),
    }));
  }

  const form = await readUrlEncodedForm(request, MAX_FORM_BODY_BYTES);
  if (!form) return json({ error: 'bad_or_oversized_form' }, 413);

  // A POST has no query string, so the language rides in a hidden field. Without it, submitting the
  // form would silently drop back to the browser's language — which is precisely the case the
  // explicit choice exists to override.
  const submitted = String(form.get('lang') ?? '').toLowerCase();
  if (['pt', 'en', 'de', 'it'].includes(submitted)) url.searchParams.set('lang', submitted);
  const language = languageForRequest(request, url);
  const t = copyFor(language);

  const deviceId = validDeviceId(form.get('device'));
  const keyCode = String(form.get('key') ?? '').trim().toUpperCase();

  if (!deviceId) {
    return html(activatePage({ deviceId: null, language, message: t.keyBadDevice }), 400);
  }

  const outcome = await redeemKey(deviceId, keyCode, env, new Date());

  if (outcome.ok) return html(activatePage({ language, done: true }));

  // Each failure says which one it was. "Invalid" for all three sends somebody to support who could
  // have worked it out — a used key and a mistyped key call for completely different next steps.
  const message = outcome.error === 'unknown_key' ? t.keyUnknown
    : outcome.error === 'already_used' ? t.keyUsed
    : outcome.error === 'key_expired' ? t.keyExpiredMsg
    : t.keyFailed;

  return html(activatePage({ deviceId, language, message }), 400);
}

/**
 * The purchase page.
 *
 * The device arrives in the query string, put there by the app's QR code and its buy button, so the
 * customer never types a fourteen-character identifier. A page opened without one says so rather
 * than showing an empty form nobody can complete.
 *
 * The device's current status is looked up and shown, which is what stops the commonest support
 * question — "did my payment work?" — and what lets an already-active device be told so instead of
 * being sold the same thing twice.
 */
async function handleBuyPage(request, env) {
  const url = new URL(request.url);
  const deviceId = validDeviceId(url.searchParams.get('device'));
  const language = languageForRequest(request, url);

  // Cloudflare tells us the country the request came from, which is a better first guess at
  // currency than the browser's language: someone in Brazil with an English browser should still
  // see reais.
  const currency = currencyForCountry(request.cf?.country);

  const device = deviceId
    ? await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?').bind(deviceId).first()
    : null;

  return html(
    buyPage({
      deviceId,
      device,
      language,
      currency,
      price: priceFor(currency),
    }),
  );
}

/**
 * Turns the buy button into a Stripe Checkout redirect.
 *
 * The price is decided here rather than submitted by the form. A hidden field holding an amount is
 * a field a customer can edit, and the first person to notice would pay nine cents.
 */
async function handleCheckout(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
  const url = new URL(request.url);
  const form = await readUrlEncodedForm(request, MAX_FORM_BODY_BYTES);
  if (!form) return json({ error: 'bad_or_oversized_form' }, 413);

  // The buy form carries the language, because a POST has no query string to read it from. It has to
  // survive the trip through Stripe as well: the customer comes back to /obrigado, and coming back
  // to a page in a language they did not choose is a poor last impression of a purchase.
  const submitted = String(form.get('lang') ?? '').toLowerCase();
  if (['pt', 'en', 'de', 'it'].includes(submitted)) url.searchParams.set('lang', submitted);
  const language = languageForRequest(request, url);

  const deviceId = validDeviceId(form.get('device'));
  if (!deviceId) return json({ error: 'bad_device' }, 400);

  // Checkout may be opened with a public code, but that code must already belong to a pinned app
  // identity. Otherwise a typo or guessed code could create a paid row nobody can prove ownership of.
  const device = await env.DB.prepare('SELECT public_key FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  if (!device) return json({ error: 'not_registered' }, 404);
  if (!device.public_key) return json({ error: 'identity_upgrade_required' }, 409);

  // Price and currency are server decisions. A hidden form field is still a customer-controlled
  // value, so changing the HTML cannot select a different regional price.
  const currency = currencyForCountry(request.cf?.country);

  const checkout = await createCheckoutSession(deviceId, currency, env, url.origin, language);

  // A customer who pressed a buy button and got a page of JSON has no idea whether they were charged.
  // Payment failing is bad; not knowing whether it failed is what produces the support message.
  if (!checkout) return html(checkoutUnavailablePage({ language, deviceId }), 502);

  await rememberPendingPayment(env, checkout, deviceId, currency, new Date());
  // A Checkout URL contains a short-lived Stripe session id. Do not let a shared browser or proxy
  // retain that redirect after the customer leaves this page.
  return new Response(null, {
    status: 303,
    headers: { location: checkout.url, 'cache-control': 'no-store' },
  });
}

/**
 * The browser return is informational; only the signed webhook grants access.
 *
 * Stripe can redirect before the webhook commits, so a known pending session is a normal
 * "processing" state. A missing, mismatched or invented id is never allowed to look successful.
 */
async function handleThanksPage(request, env) {
  const url = new URL(request.url);
  const language = languageForRequest(request, url);
  const deviceId = validDeviceId(url.searchParams.get('device'));
  const sessionId = stripeObjectId(url.searchParams.get('session_id'));

  if (!deviceId || !sessionId) {
    return html(thanksPage({ language, state: 'unverified', deviceId }), 400);
  }

  const payment = await env.DB.prepare(
    'SELECT * FROM payments WHERE checkout_session_id = ? AND device_id = ?',
  )
    .bind(sessionId, deviceId)
    .first();
  if (!payment) {
    return html(thanksPage({ language, state: 'unverified', deviceId }), 404);
  }

  const device = await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  const isActivePurchase =
    ['PAID', 'PARTIALLY_REFUNDED'].includes(payment.status) &&
    device?.status === 'ACTIVE' &&
    device?.stripe_session_id === sessionId;
  const state = isActivePurchase ? 'paid' : payment.status === 'PENDING' ? 'processing' : 'unverified';
  const retryUrl =
    `/obrigado?device=${encodeURIComponent(deviceId)}`
    + `&session_id=${encodeURIComponent(sessionId)}`;

  return html(
    thanksPage({
      language,
      state,
      deviceId,
      expiresAt: isActivePurchase ? device.expires_at : null,
      retryUrl,
    }),
    state === 'processing' ? 202 : state === 'paid' ? 200 : 409,
  );
}

/** Records Stripe's immutable ids before the browser leaves for Checkout. */
async function rememberPendingPayment(env, checkout, deviceId, currency, now) {
  const price = exactPriceFor(currency);
  if (!price) throw new Error('UnsupportedCheckoutCurrency');

  await env.DB.prepare(
    `INSERT INTO payments (
       checkout_session_id, payment_intent_id, device_id, product_id, amount_minor, currency,
       status, created_at, updated_at
     ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
     ON CONFLICT(checkout_session_id) DO UPDATE SET
       payment_intent_id = COALESCE(payments.payment_intent_id, excluded.payment_intent_id),
       updated_at = excluded.updated_at
     WHERE payments.device_id = excluded.device_id
       AND payments.product_id = excluded.product_id
       AND payments.amount_minor = excluded.amount_minor
       AND payments.currency = excluded.currency`,
  )
    .bind(
      checkout.id,
      checkout.paymentIntentId,
      deviceId,
      LICENSE_PRODUCT.id,
      price.amount,
      currency,
      iso(now),
      iso(now),
    )
    .run();

  const stored = await env.DB.prepare('SELECT * FROM payments WHERE checkout_session_id = ?')
    .bind(checkout.id)
    .first();
  if (
    !stored ||
    stored.device_id !== deviceId ||
    stored.product_id !== LICENSE_PRODUCT.id ||
    Number(stored.amount_minor) !== price.amount ||
    stored.currency !== currency
  ) {
    throw new Error('CheckoutLedgerMismatch');
  }
}

/**
 * First contact from a device, which is what starts a trial.
 *
 * Idempotent: a device that registers twice does not get a second trial. The `first_seen_at` on the
 * existing row wins, and the response is the same licence it already had. Without that, deleting
 * the client's local file and letting it register again would hand out seven fresh days — the
 * attack the client's three markers guard against locally, refused here as well.
 */
async function handleRegister(request, env) {
  const body = await readJson(request);
  const now = new Date();
  const proof = await verifyRegistrationProof(body);
  if (!proof.ok) return json({ error: proof.error }, proof.status);

  const existing = await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?')
    .bind(proof.deviceId)
    .first();
  if (existing && !existing.public_key) {
    // A row with no cryptographic identity. Two very different things look like this, and treating
    // them the same locked real devices out of trials they were entitled to.
    //
    // **A row worth protecting** — one that was paid for, granted by hand, or redeemed. Possession
    // of the fourteen-character public code must never be enough to claim it, or anyone who saw
    // somebody's screen could take their licence. These still require an audited transfer.
    //
    // **A row worth nothing** — a bare TRIAL left by an earlier protocol version, holding no
    // entitlement anybody paid for. Refusing these protects nothing and blocks the device from ever
    // registering again: it cannot upgrade, because upgrading is what it is being refused.
    const worthProtecting =
      existing.status !== 'TRIAL' ||
      existing.stripe_session_id != null ||
      existing.purchased_at != null;

    if (worthProtecting) {
      return json({ error: 'identity_upgrade_required' }, 409);
    }

    // Adopt the row. The trial keeps its original `first_seen_at` and `trial_ends_at`, so this is
    // not a way to obtain fresh days — the device gets exactly the time it already had.
    const adopted = await env.DB.prepare(
      `UPDATE devices SET public_key = ?, updated_at = ?
       WHERE device_id = ? AND public_key IS NULL AND status = 'TRIAL'`,
    )
      .bind(proof.publicKey, iso(now), proof.deviceId)
      .run();

    // Conditional on public_key still being null, so two devices racing here cannot both adopt.
    // The loser falls through to the equality check below and is refused as a mismatch.
    if (statementChanges(adopted) > 0) {
      await recordEvent(env, proof.deviceId, 'identity_adopted', null, now);
      existing.public_key = proof.publicKey;
    }
  }
  if (existing && !timingSafeEqual(existing.public_key, proof.publicKey)) {
    return json({ error: 'invalid_proof' }, 401);
  }
  if (!(await claimDeviceProof(env, proof.deviceId, proof.nonce, 'register', now))) {
    return json({ error: 'proof_replayed' }, 409);
  }

  // When this machine says it was first seen, from the markers the client keeps in three places.
  //
  // Reported by the client and therefore not trusted — but it can only ever *shorten* a trial, never
  // extend one, so lying gains nothing. A reinstall produces a new random installation id and looks
  // like a device the server has never met; without this, deleting one folder buys seven more free
  // days, repeatable for ever.
  //
  // A date in the future would postpone the trial's start, so it is ignored. A strict wire shape is
  // used before Date.parse: Java sends an ISO UTC Instant, while JavaScript also accepts surprising
  // inputs such as "0" as January 2000. Valid old markers remain valid indefinitely; forgetting one
  // after a year would let a yearly reinstall buy another trial.
  const claimedText = String(body.firstSeen ?? '');
  const claimed = UTC_INSTANT.test(claimedText) ? Date.parse(claimedText) : Number.NaN;
  const usable = Number.isFinite(claimed) && claimed < now.getTime();
  const claimedFirstSeen = usable ? new Date(claimed) : now;

  // What the server itself remembers about this machine, which beats anything the client says.
  //
  // The client's own markers are the honest path and cover the ordinary case, but they live on the
  // same disk as everything else: deleting them was the whole attack. The installation id is now
  // derived from the machine rather than drawn at random, so the same machine arrives with the same
  // anchor however many times its files are removed — and the trial it started weeks ago is still
  // here, in the server's own records, with the server's own dates.
  //
  // Only the earliest matters. A machine may hold several device rows over its life, and the first
  // trial it ever started is the one that decides how much of the week is left.
  const anchor = proof.installationId ?? null;
  const remembered = anchor
    ? await env.DB.prepare(
        `SELECT MIN(first_seen_at) AS earliest FROM devices
         WHERE machine_anchor = ? AND device_id <> ?`,
      )
        .bind(anchor, proof.deviceId)
        .first()
    : null;

  const rememberedAt = remembered?.earliest ? Date.parse(remembered.earliest) : Number.NaN;
  // The earlier of the two wins, because a trial cannot start twice. A returning machine keeps the
  // clock it already started; a genuinely new one is unaffected, having nothing to be found.
  const firstSeen =
    Number.isFinite(rememberedAt) && rememberedAt < claimedFirstSeen.getTime()
      ? new Date(rememberedAt)
      : claimedFirstSeen;

  // Counted from whichever is earlier. A machine that reports having been seen nine days ago has
  // already used its week.
  const trialEnds = addDays(firstSeen, TRIAL_DAYS);
  const inserted = await env.DB.prepare(
    `INSERT INTO devices
       (device_id, public_key, status, first_seen_at, trial_ends_at, updated_at, machine_anchor)
     VALUES (?, ?, 'TRIAL', ?, ?, ?, ?)
     ON CONFLICT(device_id) DO NOTHING`,
  )
    // first_seen_at records what the machine reported, so support can see a device that arrived
    // claiming to be older than its registration — the signature of a reinstall.
    //
    // machine_anchor is what makes the next such arrival recognisable at all: it is the stable
    // installation id, and matching on it is how a deleted-files reinstall is reunited with the
    // trial it already used.
    .bind(proof.deviceId, proof.publicKey, iso(firstSeen), iso(trialEnds), iso(now), anchor)
    .run();

  const wasInserted = statementChanges(inserted) > 0;
  if (wasInserted) {
    // Noted when the claim is older than now: worth being able to find later, and the only place
    // the difference between a fresh install and a returning one is visible.
    const detail = firstSeen < now ? `first seen ${iso(firstSeen)}` : null;
    await recordEvent(env, proof.deviceId, 'registered', detail, now);
  }

  // Close the small race between the pre-check and INSERT. Only the already-pinned same key may
  // receive a licence; a colliding public code can never replace the winner.
  const registered = await env.DB.prepare('SELECT public_key FROM devices WHERE device_id = ?')
    .bind(proof.deviceId)
    .first();
  if (!registered?.public_key) return json({ error: 'identity_upgrade_required' }, 409);
  if (!timingSafeEqual(registered.public_key, proof.publicKey)) {
    return json({ error: 'invalid_proof' }, 401);
  }
  await observeDevice(env, proof.deviceId, body.deviceProfile, request.cf?.country, wasInserted, now);
  return await respondWithLicense(env, proof.deviceId, proof.nonce, now);
}

/**
 * The question asked at every launch: what is this device entitled to right now?
 *
 * A trial that has run out is flipped to EXPIRED here rather than left as TRIAL, so the state in
 * the table always matches reality and a support query does not have to do the arithmetic.
 */
async function handleValidate(request, env) {
  const body = await readJson(request);
  const now = new Date();
  const authorization = await authorizeExistingDevice(body, 'validate', env, now);
  if (!authorization.ok) return json({ error: authorization.error }, authorization.status);

  await observeDevice(env, authorization.deviceId, body.deviceProfile, request.cf?.country, false, now);
  await expireIfDue(env, authorization.device, now);
  await recordEvent(env, authorization.deviceId, 'validated', null, now);
  return await respondWithLicense(env, authorization.deviceId, authorization.nonce, now);
}

/**
 * Redeems a key issued by hand.
 *
 * Single use. A key posted publicly must unlock exactly one device, not every install that finds
 * it, so redemption binds the key to the device that used it and refuses it afterwards.
 */
async function handleRedeem(request, env) {
  const body = await readJson(request);
  const keyCode = String(body.key ?? '').trim().toUpperCase();
  if (!keyCode) return json({ error: 'bad_request' }, 400);

  const now = new Date();
  const authorization = await authorizeExistingDevice(body, 'redeem', env, now);
  if (!authorization.ok) return json({ error: authorization.error }, authorization.status);
  await observeDevice(env, authorization.deviceId, body.deviceProfile, request.cf?.country, false, now);

  const outcome = await redeemKey(authorization.deviceId, keyCode, env, now);
  if (!outcome.ok) return json({ error: outcome.error }, outcome.status);

  return await respondWithLicense(env, authorization.deviceId, authorization.nonce, now);
}

/**
 * Describes a key without spending it, so the app can say what it is before redeeming.
 *
 * The activation screen used to say nothing at all: a customer pasted a code and learned only
 * whether it worked. Now it can show how many days the key grants, whether it is free, already
 * theirs, or spent by another machine.
 *
 * ## Why this needs the same proof as redeeming
 *
 * An endpoint that describes any key on request is an oracle for guessing them. Requiring the same
 * signed device proof means only a registered installation can ask, and the same rate limiter that
 * protects redemption applies — so this adds no way to enumerate keys that redeeming did not
 * already offer, at a far higher cost per guess.
 *
 * ## What it deliberately does not say
 *
 * Never which device owns a key. "In use" is what the customer needs to know; the id of the machine
 * holding it is somebody else's business, and returning it would turn a mistyped code into a
 * disclosure about another customer.
 */
async function handleKeyInfo(request, env) {
  const body = await readJson(request);
  const keyCode = String(body.key ?? '').trim().toUpperCase();
  if (!keyCode) return json({ error: 'bad_request' }, 400);

  const now = new Date();
  const authorization = await authorizeExistingDevice(body, 'validate', env, now);
  if (!authorization.ok) return json({ error: authorization.error }, authorization.status);

  const key = await env.DB.prepare(
    'SELECT grant_days, redeemed_by, valid_until FROM redemption_keys WHERE key_code = ?',
  )
    .bind(keyCode)
    .first();

  if (!key) return json({ state: 'unknown' }, 404);

  const expired = key.valid_until && new Date(key.valid_until) < now;
  const state = (() => {
    if (expired) return 'expired';
    if (!key.redeemed_by) return 'available';
    // The distinction that matters to the person typing: their own key still works.
    return key.redeemed_by === authorization.deviceId ? 'yours' : 'in_use';
  })();

  return json({
    state,
    grantDays: Number(key.grant_days) || null,
    validUntil: key.valid_until ?? null,
  });
}

/**
 * Verifies a Google Play purchase and binds it to a proved Android installation.
 *
 * The token is never trusted as a receipt. Google is queried server-to-server, the configured
 * product and purchase option are checked, and the token is encrypted before D1 persistence.
 */
async function handleGooglePlayPurchase(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);
  const body = await readJson(request);
  const now = new Date();
  const authorization = await authorizeGooglePlayPurchase(body, env, now);
  if (!authorization.ok) return json({ error: authorization.error }, authorization.status);
  if (!env.GOOGLE_TOKEN_ENCRYPTION_KEY) {
    return json({ error: 'google_play_unconfigured' }, 503);
  }

  const inspected = await inspectGooglePlayPurchase(
    authorization.purchaseToken,
    authorization.accountId,
    env,
  );
  if (!inspected.ok) {
    const status = inspected.retryable
      ? 503
      : inspected.reason === 'purchase_not_found'
        ? 404
        : inspected.reason === 'purchase_account_mismatch'
          ? 403
          : 409;
    return json({ error: inspected.reason }, status);
  }

  let tokenCiphertext;
  try {
    tokenCiphertext = await sealGooglePurchaseToken(
      authorization.purchaseToken,
      env.GOOGLE_TOKEN_ENCRYPTION_KEY,
    );
  } catch {
    return json({ error: 'google_play_unconfigured' }, 503);
  }

  const existing = await env.DB.prepare(
    'SELECT * FROM google_play_purchases WHERE purchase_token_hash = ?',
  )
    .bind(inspected.purchaseTokenHash)
    .first();
  if (existing && existing.obfuscated_account_id !== authorization.accountId) {
    return json({ error: 'purchase_already_bound' }, 409);
  }
  if (
    inspected.state === 'PURCHASED' &&
    inspected.consumptionState === 'CONSUMPTION_STATE_CONSUMED' &&
    !existing
  ) {
    return json({ error: 'purchase_already_consumed' }, 409);
  }

  if (inspected.state === 'PENDING') {
    await upsertGooglePlayLedger(
      env,
      authorization,
      inspected,
      tokenCiphertext,
      existing?.device_id ?? authorization.deviceId,
      now,
    );
    return json({ state: 'PENDING' }, 202);
  }

  if (inspected.state === 'CANCELLED' || inspected.state === 'REFUNDED') {
    await cancelGooglePlayEntitlement(
      env,
      authorization,
      inspected,
      tokenCiphertext,
      existing?.device_id ?? authorization.deviceId,
      now,
    );
    return json(
      { error: inspected.state === 'REFUNDED' ? 'purchase_refunded' : 'purchase_cancelled' },
      409,
    );
  }

  const completionTime = inspected.completionTime;
  const expiresAt = addDays(completionTime, PAID_DAYS);
  const timestamp = iso(now);
  const completedAt = iso(completionTime);
  const expiresAtText = iso(expiresAt);
  const previousDeviceId = existing?.device_id;
  const results = await env.DB.batch([
    googlePlayLedgerUpsertStatement(
      env,
      authorization,
      inspected,
      tokenCiphertext,
      authorization.deviceId,
      'PURCHASED',
      completedAt,
      expiresAtText,
      timestamp,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'REVOKED', google_purchase_token_hash = NULL, updated_at = ?
       WHERE device_id = ? AND device_id <> ? AND google_purchase_token_hash = ?`,
    ).bind(
      timestamp,
      previousDeviceId ?? authorization.deviceId,
      authorization.deviceId,
      inspected.purchaseTokenHash,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'ACTIVE', purchased_at = ?, expires_at = ?,
           stripe_session_id = NULL, google_purchase_token_hash = ?, updated_at = ?
       WHERE device_id = ? AND public_key IS NOT NULL
         AND (purchased_at IS NULL OR ? >= purchased_at OR google_purchase_token_hash = ?)
         AND NOT EXISTS (
           SELECT 1 FROM events
           WHERE device_id = ? AND kind = 'revoked' AND created_at >= ?
         )`,
    ).bind(
      completedAt,
      expiresAtText,
      inspected.purchaseTokenHash,
      timestamp,
      authorization.deviceId,
      completedAt,
      inspected.purchaseTokenHash,
      authorization.deviceId,
      completedAt,
    ),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, 'google_play_purchased', ?, ?
       WHERE EXISTS (
         SELECT 1 FROM devices WHERE device_id = ? AND google_purchase_token_hash = ?
       ) AND NOT EXISTS (
         SELECT 1 FROM events
         WHERE device_id = ? AND kind = 'google_play_purchased' AND detail = ?
       )`,
    ).bind(
      authorization.deviceId,
      inspected.purchaseTokenHash,
      timestamp,
      authorization.deviceId,
      inspected.purchaseTokenHash,
      authorization.deviceId,
      inspected.purchaseTokenHash,
    ),
  ]);
  if (statementChanges(results?.[0]) === 0) {
    return json({ error: 'purchase_ledger_conflict' }, 409);
  }

  const needsConsumption =
    inspected.consumptionState !== 'CONSUMPTION_STATE_CONSUMED';
  if (needsConsumption) {
    const consumed = await consumeGooglePlayPurchase(
      authorization.purchaseToken,
      inspected.accessToken,
      env,
    );
    if (!consumed) return json({ error: 'google_consumption_pending' }, 503);
  }
  await env.DB.prepare(
    `UPDATE google_play_purchases
     SET acknowledgement_state = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED', updated_at = ?
     WHERE purchase_token_hash = ?`,
  )
    .bind(iso(new Date()), inspected.purchaseTokenHash)
    .run();

  return await respondWithLicense(env, authorization.deviceId, authorization.nonce, now);
}

async function authorizeGooglePlayPurchase(body, env, now) {
  const deviceId = validDeviceId(body.deviceId);
  const nonce = validProofNonce(body.nonce);
  const proof = decodeProof(body.proof);
  const accountId =
    typeof body.accountId === 'string' && /^[a-f0-9]{64}$/.test(body.accountId)
      ? body.accountId
      : null;
  const purchaseToken =
    typeof body.purchaseToken === 'string' &&
    body.purchaseToken.length >= 16 &&
    body.purchaseToken.length <= 4_096 &&
    /^[\x21-\x7E]+$/.test(body.purchaseToken)
      ? body.purchaseToken
      : null;
  if (!deviceId || !nonce || !proof || !accountId || !purchaseToken) {
    return { ok: false, error: 'bad_purchase_proof', status: 400 };
  }

  const device = await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  const publicKey = canonicalPublicKey(device?.public_key);
  if (!device) return { ok: false, error: 'not_registered', status: 404 };
  if (!publicKey) return { ok: false, error: 'identity_upgrade_required', status: 409 };

  const tokenHash = await purchaseTokenHash(purchaseToken);
  const canonical =
    `iptvburo-google-play-purchase-v1\n${deviceId}\n${nonce}\n${tokenHash}\n${accountId}`;
  const verified = await verifyRawDeviceProof(decodeStandardBase64(publicKey), proof, canonical);
  if (!verified) return { ok: false, error: 'invalid_proof', status: 401 };
  if (!(await claimDeviceProof(env, deviceId, nonce, 'google_play_purchase', now))) {
    return { ok: false, error: 'proof_replayed', status: 409 };
  }
  return { ok: true, deviceId, nonce, accountId, purchaseToken, tokenHash, device };
}

async function upsertGooglePlayLedger(
  env,
  authorization,
  inspected,
  tokenCiphertext,
  deviceId,
  now,
) {
  const timestamp = iso(now);
  await googlePlayLedgerUpsertStatement(
    env,
    authorization,
    inspected,
    tokenCiphertext,
    deviceId,
    inspected.state,
    null,
    null,
    timestamp,
  ).run();
}

async function cancelGooglePlayEntitlement(
  env,
  authorization,
  inspected,
  tokenCiphertext,
  deviceId,
  now,
) {
  const timestamp = iso(now);
  const status = inspected.state === 'REFUNDED' ? 'REFUNDED' : 'CANCELLED';
  const eventKind = status === 'REFUNDED' ? 'google_play_refunded' : 'google_play_cancelled';
  await env.DB.batch([
    googlePlayLedgerUpsertStatement(
      env,
      authorization,
      inspected,
      tokenCiphertext,
      deviceId,
      status,
      null,
      null,
      timestamp,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'REVOKED', google_purchase_token_hash = NULL, updated_at = ?
       WHERE device_id = ? AND google_purchase_token_hash = ?`,
    ).bind(timestamp, deviceId, inspected.purchaseTokenHash),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, ?, ?, ?
       WHERE NOT EXISTS (
         SELECT 1 FROM events
         WHERE device_id = ? AND kind = ? AND detail = ?
       )`,
    ).bind(
      deviceId,
      eventKind,
      inspected.purchaseTokenHash,
      timestamp,
      deviceId,
      eventKind,
      inspected.purchaseTokenHash,
    ),
  ]);
}

/**
 * Re-checks active and pending purchases with Google even if the app never opens again.
 *
 * This is the durable fallback for missed Play notifications and full refunds: only the encrypted
 * token is opened inside the Worker, one OAuth token is reused for the bounded batch, and failures
 * leave the existing entitlement untouched for a later retry.
 */
export async function reconcileGooglePlayPurchases(
  env,
  limit = GOOGLE_PLAY_RECONCILIATION_LIMIT,
  now = new Date(),
) {
  if (!env?.DB || !env.GOOGLE_TOKEN_ENCRYPTION_KEY) return { checked: 0, changed: 0 };
  const boundedLimit = Math.max(1, Math.min(Number(limit) || GOOGLE_PLAY_RECONCILIATION_LIMIT, 100));
  const timestamp = iso(now);
  const result = await env.DB.prepare(
    `SELECT * FROM google_play_purchases
     WHERE status = 'PENDING'
        OR (status = 'PURCHASED' AND (expires_at IS NULL OR expires_at > ?))
     ORDER BY COALESCE(last_checked_at, created_at) ASC
     LIMIT ?`,
  ).bind(timestamp, boundedLimit).all();
  const purchases = Array.isArray(result?.results) ? result.results : [];
  let checked = 0;
  let changed = 0;
  let accessToken = null;

  for (const purchase of purchases) {
    try {
      const token = await openGooglePurchaseToken(
        purchase.token_ciphertext,
        env.GOOGLE_TOKEN_ENCRYPTION_KEY,
      );
      const inspected = await inspectGooglePlayPurchase(
        token,
        purchase.obfuscated_account_id,
        env,
        accessToken,
      );
      checked += 1;
      if (inspected.accessToken) accessToken = inspected.accessToken;
      if (!inspected.ok) {
        await markGooglePlayChecked(env, purchase.purchase_token_hash, timestamp);
        continue;
      }

      if (inspected.state === 'CANCELLED' || inspected.state === 'REFUNDED') {
        await revokePersistedGooglePurchase(env, purchase, inspected.state, timestamp);
        changed += 1;
        continue;
      }

      if (inspected.state === 'PENDING') {
        await env.DB.prepare(
          `UPDATE google_play_purchases
           SET status = 'PENDING', acknowledgement_state = ?, last_checked_at = ?, updated_at = ?
           WHERE purchase_token_hash = ?`,
        ).bind(
          inspected.acknowledgementState || 'ACKNOWLEDGEMENT_STATE_PENDING',
          timestamp,
          timestamp,
          purchase.purchase_token_hash,
        ).run();
        continue;
      }

      const completedAt = iso(inspected.completionTime);
      const expiresAt = iso(addDays(inspected.completionTime, PAID_DAYS));
      await grantPersistedGooglePurchase(
        env,
        purchase,
        inspected,
        completedAt,
        expiresAt,
        timestamp,
      );
      changed += purchase.status === 'PENDING' ? 1 : 0;

      const alreadyConsumed =
        inspected.consumptionState === 'CONSUMPTION_STATE_CONSUMED';
      const delivered = alreadyConsumed ||
        await consumeGooglePlayPurchase(token, inspected.accessToken, env);
      if (delivered) {
        await env.DB.prepare(
          `UPDATE google_play_purchases
           SET acknowledgement_state = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
               last_checked_at = ?, updated_at = ?
           WHERE purchase_token_hash = ?`,
        ).bind(timestamp, timestamp, purchase.purchase_token_hash).run();
      }
    } catch (error) {
      // Never log the row or token. A corrupt envelope or temporary API problem is retried later.
      console.error('google play reconciliation failed', error?.name);
      await markGooglePlayChecked(env, purchase.purchase_token_hash, timestamp);
    }
  }

  return { checked, changed };
}

async function markGooglePlayChecked(env, tokenHash, timestamp) {
  await env.DB.prepare(
    'UPDATE google_play_purchases SET last_checked_at = ? WHERE purchase_token_hash = ?',
  ).bind(timestamp, tokenHash).run();
}

async function revokePersistedGooglePurchase(env, purchase, state, timestamp) {
  const status = state === 'REFUNDED' ? 'REFUNDED' : 'CANCELLED';
  const eventKind = status === 'REFUNDED' ? 'google_play_refunded' : 'google_play_cancelled';
  await env.DB.batch([
    env.DB.prepare(
      `UPDATE google_play_purchases
       SET status = ?, last_checked_at = ?, updated_at = ?
       WHERE purchase_token_hash = ?`,
    ).bind(status, timestamp, timestamp, purchase.purchase_token_hash),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'REVOKED', google_purchase_token_hash = NULL, updated_at = ?
       WHERE device_id = ? AND google_purchase_token_hash = ?`,
    ).bind(timestamp, purchase.device_id, purchase.purchase_token_hash),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, ?, ?, ?
       WHERE NOT EXISTS (
         SELECT 1 FROM events WHERE device_id = ? AND kind = ? AND detail = ?
       )`,
    ).bind(
      purchase.device_id,
      eventKind,
      purchase.purchase_token_hash,
      timestamp,
      purchase.device_id,
      eventKind,
      purchase.purchase_token_hash,
    ),
  ]);
}

async function grantPersistedGooglePurchase(
  env,
  purchase,
  inspected,
  completedAt,
  expiresAt,
  timestamp,
) {
  await env.DB.batch([
    env.DB.prepare(
      `UPDATE google_play_purchases
       SET status = 'PURCHASED', acknowledgement_state = ?, purchase_completed_at = ?,
           expires_at = ?, last_checked_at = ?, updated_at = ?
       WHERE purchase_token_hash = ?`,
    ).bind(
      inspected.acknowledgementState || 'ACKNOWLEDGEMENT_STATE_PENDING',
      completedAt,
      expiresAt,
      timestamp,
      timestamp,
      purchase.purchase_token_hash,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'ACTIVE', purchased_at = ?, expires_at = ?, stripe_session_id = NULL,
           google_purchase_token_hash = ?, updated_at = ?
       WHERE device_id = ? AND public_key IS NOT NULL
         AND (purchased_at IS NULL OR ? >= purchased_at OR google_purchase_token_hash = ?)`,
    ).bind(
      completedAt,
      expiresAt,
      purchase.purchase_token_hash,
      timestamp,
      purchase.device_id,
      completedAt,
      purchase.purchase_token_hash,
    ),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, 'google_play_purchased', ?, ?
       WHERE EXISTS (
         SELECT 1 FROM devices WHERE device_id = ? AND google_purchase_token_hash = ?
       ) AND NOT EXISTS (
         SELECT 1 FROM events
         WHERE device_id = ? AND kind = 'google_play_purchased' AND detail = ?
       )`,
    ).bind(
      purchase.device_id,
      purchase.purchase_token_hash,
      timestamp,
      purchase.device_id,
      purchase.purchase_token_hash,
      purchase.device_id,
      purchase.purchase_token_hash,
    ),
  ]);
}

function googlePlayLedgerUpsertStatement(
  env,
  authorization,
  inspected,
  tokenCiphertext,
  deviceId,
  status,
  completedAt,
  expiresAt,
  timestamp,
) {
  return env.DB.prepare(
    `INSERT INTO google_play_purchases (
       purchase_token_hash, token_ciphertext, device_id, product_id, purchase_option_id,
       obfuscated_account_id, status, acknowledgement_state, test_purchase,
       purchase_completed_at, expires_at, last_checked_at, created_at, updated_at
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(purchase_token_hash) DO UPDATE SET
       token_ciphertext = excluded.token_ciphertext,
       device_id = excluded.device_id,
       status = excluded.status,
       acknowledgement_state = excluded.acknowledgement_state,
       test_purchase = excluded.test_purchase,
       purchase_completed_at = COALESCE(excluded.purchase_completed_at, google_play_purchases.purchase_completed_at),
       expires_at = COALESCE(excluded.expires_at, google_play_purchases.expires_at),
       last_checked_at = excluded.last_checked_at,
       updated_at = excluded.updated_at
     WHERE google_play_purchases.product_id = excluded.product_id
       AND google_play_purchases.purchase_option_id = excluded.purchase_option_id
       AND google_play_purchases.obfuscated_account_id = excluded.obfuscated_account_id`,
  ).bind(
    inspected.purchaseTokenHash,
    tokenCiphertext,
    deviceId,
    inspected.productId,
    inspected.purchaseOptionId,
    authorization.accountId,
    status,
    inspected.acknowledgementState || 'ACKNOWLEDGEMENT_STATE_PENDING',
    inspected.testPurchase ? 1 : 0,
    completedAt,
    expiresAt,
    timestamp,
    timestamp,
    timestamp,
  );
}

/**
 * The redemption itself, shared by the app and the website form.
 *
 * One implementation rather than two, because single use is the property that makes a key worth
 * anything: a key posted publicly must unlock exactly one device. Two copies of this logic would
 * eventually disagree, and the copy that disagreed would be the one giving the product away.
 *
 * @returns {{ok: true}} or {{ok: false, error: string, status: number}}
 */
async function redeemKey(deviceId, keyCode, env, now) {
  if (!keyCode) return { ok: false, error: 'bad_request', status: 400 };

  // The phone portal may redeem for a public code, but it may only mutate an identity the Windows
  // app already registered and pinned. It never creates a row or supplies a key of its own.
  const device = await env.DB.prepare('SELECT public_key FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  if (!device) return { ok: false, error: 'not_registered', status: 404 };
  if (!device.public_key) {
    return { ok: false, error: 'identity_upgrade_required', status: 409 };
  }

  const key = await env.DB.prepare('SELECT * FROM redemption_keys WHERE key_code = ?')
    .bind(keyCode)
    .first();

  if (!key) return { ok: false, error: 'unknown_key', status: 404 };
  // Used by *somebody else*, rather than used at all.
  //
  // This refused the buyer their own key. A customer who reinstalled — and whose device id
  // therefore changed, see the identity fix that ships with this — was told their key was already
  // spent, on a licence they had paid for hours earlier. The only way out was to buy it again.
  //
  // Single use is still exactly single use: the key binds to the first device that redeems it and
  // no other device can ever take it. What changes is that the owner is no longer locked out of
  // the thing they own.
  if (key.redeemed_by && key.redeemed_by !== deviceId) {
    return { ok: false, error: 'already_used', status: 409 };
  }
  if (key.valid_until && new Date(key.valid_until) < now) {
    return { ok: false, error: 'key_expired', status: 410 };
  }

  const grantDays = Number(key.grant_days);
  if (!Number.isInteger(grantDays) || grantDays < 1 || grantDays > 36_500) {
    return { ok: false, error: 'invalid_key', status: 409 };
  }

  const redeemedAt = iso(now);
  const expiresAt = addDays(now, grantDays);
  const results = await env.DB.batch([
    env.DB.prepare(
      // Free, or already this device's.
      //
      // This is the statement that makes single use real: two machines racing for the same key
      // both reach here, and only one changes a row. Admitting `redeemed_by = ?` alongside NULL
      // lets the owner re-redeem without weakening that — a second device still finds the row
      // claimed by an id that is not its own, and still changes nothing.
      `UPDATE redemption_keys
       SET redeemed_by = ?, redeemed_at = ?
       WHERE key_code = ?
         AND (redeemed_by IS NULL OR redeemed_by = ?)
         AND (valid_until IS NULL OR valid_until >= ?)`,
    ).bind(deviceId, redeemedAt, keyCode, deviceId, redeemedAt),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'ACTIVE', purchased_at = ?, expires_at = ?,
           stripe_session_id = NULL, google_purchase_token_hash = NULL,
           note = ?, updated_at = ?
       WHERE device_id = ? AND public_key = ?
         AND EXISTS (
           SELECT 1 FROM redemption_keys
           WHERE key_code = ? AND redeemed_by = ? AND redeemed_at = ?
         )`,
    ).bind(
      iso(now),
      iso(expiresAt),
      `key:${keyCode}`,
      iso(now),
      deviceId,
      device.public_key,
      keyCode,
      deviceId,
      redeemedAt,
    ),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, 'redeemed', key_code, ?
       FROM redemption_keys
       WHERE key_code = ? AND redeemed_by = ? AND redeemed_at = ?`,
    ).bind(deviceId, redeemedAt, keyCode, deviceId, redeemedAt),
  ]);

  if (statementChanges(results?.[0]) === 0) {
    const current = await env.DB.prepare('SELECT * FROM redemption_keys WHERE key_code = ?')
      .bind(keyCode)
      .first();
    if (!current) return { ok: false, error: 'unknown_key', status: 404 };
    // Same rule as above: only another device's claim is a refusal.
    if (current.redeemed_by && current.redeemed_by !== deviceId) {
      return { ok: false, error: 'already_used', status: 409 };
    }
    if (current.valid_until && new Date(current.valid_until) < now) {
      return { ok: false, error: 'key_expired', status: 410 };
    }
    return { ok: false, error: 'redemption_conflict', status: 409 };
  }
  if (statementChanges(results?.[1]) === 0) {
    return { ok: false, error: 'redemption_conflict', status: 409 };
  }

  return { ok: true };
}

/** Validates derivation and possession before the first key is pinned. */
async function verifyRegistrationProof(body) {
  const deviceId = validDeviceId(body.deviceId);
  const nonce = validProofNonce(body.nonce);
  const installationId = validInstallationId(body.installationId);
  const publicKey = canonicalPublicKey(body.publicKey);
  const proof = decodeProof(body.proof);
  if (!deviceId || !nonce || !installationId || !publicKey || !proof) {
    return { ok: false, error: 'bad_identity', status: 400 };
  }

  const publicKeyBytes = decodeStandardBase64(publicKey);
  const derived = await deriveDeviceId(publicKeyBytes, installationId);
  if (derived !== deviceId) return { ok: false, error: 'bad_identity', status: 400 };

  const verified = await verifyDeviceProof(publicKeyBytes, proof, 'register', deviceId, nonce);
  if (!verified) return { ok: false, error: 'invalid_proof', status: 401 };
  // The installation id travels on, because it is now anchored to the machine rather than drawn at
  // random: it is what lets a returning machine be recognised after its stored files were deleted.
  return { ok: true, deviceId, nonce, publicKey, installationId };
}

/**
 * Records coarse, self-reported support information after the device proof has succeeded.
 *
 * This data never decides whether a licence is valid. A modified client could call itself a
 * television or change its model, so the admin panel labels it as reported information and the
 * entitlement continues to depend only on the pinned key and signed server state. Hostnames,
 * serials, MAC addresses, Android IDs and OS account names are neither accepted nor stored.
 */
async function observeDevice(env, deviceId, reported, reportedCountry, recordActivationCountry, now) {
  const profile = normaliseDeviceProfile(reported);
  const country = normaliseCountry(reportedCountry);
  await env.DB.prepare(
    `UPDATE devices SET
       device_type = COALESCE(?, device_type),
       platform = COALESCE(?, platform),
       manufacturer = COALESCE(?, manufacturer),
       model = COALESCE(?, model),
       os_version = COALESCE(?, os_version),
       app_version = COALESCE(?, app_version),
       activation_country = CASE
         WHEN ? = 1 THEN COALESCE(activation_country, ?)
         ELSE activation_country
       END,
       last_country = COALESCE(?, last_country),
       country_updated_at = CASE WHEN ? IS NULL THEN country_updated_at ELSE ? END,
       last_seen_at = ?
     WHERE device_id = ?`,
  )
    .bind(
      profile?.deviceType ?? null,
      profile?.platform ?? null,
      profile?.manufacturer ?? null,
      profile?.model ?? null,
      profile?.osVersion ?? null,
      profile?.appVersion ?? null,
      recordActivationCountry ? 1 : 0,
      country,
      country,
      country,
      country ? iso(now) : null,
      iso(now),
      deviceId,
    )
    .run();
}

/** Cloudflare country codes are coarse routing metadata, not client-reported location. */
function normaliseCountry(value) {
  const country = String(value ?? '').trim().toUpperCase();
  return /^[A-Z]{2}$/.test(country) && country !== 'XX' ? country : null;
}

function normaliseDeviceProfile(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const deviceType = boundedLabel(value.deviceType, 32)?.toUpperCase();
  const platform = boundedLabel(value.platform, 24)?.toUpperCase();
  const allowedTypes = new Set(['WINDOWS_PC', 'ANDROID_PHONE', 'ANDROID_TABLET', 'ANDROID_TV', 'TV']);
  const allowedPlatforms = new Set(['WINDOWS', 'ANDROID', 'TIZEN', 'WEBOS']);

  return {
    deviceType: allowedTypes.has(deviceType) ? deviceType : null,
    platform: allowedPlatforms.has(platform) ? platform : null,
    manufacturer: boundedLabel(value.manufacturer, 80),
    model: boundedLabel(value.model, 120),
    osVersion: boundedLabel(value.osVersion, 120),
    appVersion: boundedLabel(value.appVersion, 80),
  };
}

/** Printable single-line text only. Control characters cannot become admin markup or log noise. */
function boundedLabel(value, maximum) {
  if (typeof value !== 'string') return null;
  const clean = value.replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim();
  return clean ? clean.slice(0, maximum) : null;
}

/** Authenticates validate/redeem exclusively with the key already pinned to the device row. */
async function authorizeExistingDevice(body, action, env, now) {
  const deviceId = validDeviceId(body.deviceId);
  const nonce = validProofNonce(body.nonce);
  const proof = decodeProof(body.proof);
  if (!deviceId || !nonce || !proof) {
    return { ok: false, error: 'bad_proof', status: 400 };
  }

  const device = await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();
  if (!device) return { ok: false, error: 'not_registered', status: 404 };
  const publicKey = canonicalPublicKey(device.public_key);
  if (!publicKey) {
    return { ok: false, error: 'identity_upgrade_required', status: 409 };
  }

  const verified = await verifyDeviceProof(
    decodeStandardBase64(publicKey),
    proof,
    action,
    deviceId,
    nonce,
  );
  if (!verified) return { ok: false, error: 'invalid_proof', status: 401 };

  // All parsing, identity and cryptographic errors happen before this insert, so malformed requests
  // never burn a nonce. A valid captured request, however, is accepted exactly once.
  if (!(await claimDeviceProof(env, deviceId, nonce, action, now))) {
    return { ok: false, error: 'proof_replayed', status: 409 };
  }
  return { ok: true, deviceId, nonce, device };
}

async function verifyDeviceProof(publicKeyBytes, proof, action, deviceId, nonce) {
  return await verifyRawDeviceProof(
    publicKeyBytes,
    proof,
    canonicalDeviceProof(action, deviceId, nonce),
  );
}

async function verifyRawDeviceProof(publicKeyBytes, proof, canonical) {
  try {
    const key = await crypto.subtle.importKey(
      'spki',
      publicKeyBytes,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify'],
    );
    return await crypto.subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      key,
      proof,
      new TextEncoder().encode(canonical),
    );
  } catch {
    return false;
  }
}

/** Atomically remembers a valid nonce and performs a bounded age cleanup. */
async function claimDeviceProof(env, deviceId, nonce, action, now) {
  const staleBefore = iso(addDays(now, -DEVICE_PROOF_RETENTION_DAYS));
  const results = await env.DB.batch([
    env.DB.prepare(
      `DELETE FROM device_proof_nonces
       WHERE rowid IN (
         SELECT rowid FROM device_proof_nonces
         WHERE used_at < ? ORDER BY used_at LIMIT ?
       )`,
    ).bind(staleBefore, DEVICE_PROOF_CLEANUP_LIMIT),
    env.DB.prepare(
      `INSERT INTO device_proof_nonces (device_id, nonce, action, used_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(device_id, nonce) DO NOTHING`,
    ).bind(deviceId, nonce, action, iso(now)),
  ]);
  return statementChanges(results?.[1]) > 0;
}

function canonicalDeviceProof(action, deviceId, nonce) {
  return `iptvburo-device-proof-v1\n${action}\n${deviceId}\n${nonce}`;
}

/** The UUID is lowercase canonical RFC-4122 text and specifically version 4 (random). */
function validInstallationId(value) {
  const candidate = typeof value === 'string' ? value : '';
  return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(candidate)
    ? candidate
    : null;
}

function validProofNonce(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{22}$/.test(value) ? value : null;
}

/** P-256 SPKI DER in canonical standard Base64 (including required padding). */
function canonicalPublicKey(value) {
  if (typeof value !== 'string' || value.length < 100 || value.length > 256) return null;
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value)) return null;
  const decoded = decodeStandardBase64(value);
  if (!decoded || decoded.byteLength < 80 || decoded.byteLength > 160) return null;
  return encodeStandardBase64(decoded) === value ? value : null;
}

function decodeProof(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{86}$/.test(value)) return null;
  try {
    const bytes = decodeBase64Url(value);
    return bytes.byteLength === 64 ? bytes : null;
  } catch {
    return null;
  }
}

function decodeStandardBase64(value) {
  try {
    const binary = atob(value);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function encodeStandardBase64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function decodeBase64Url(value) {
  const standard = value.replaceAll('-', '+').replaceAll('_', '/');
  return decodeStandardBase64(standard + '='.repeat((4 - (standard.length % 4)) % 4));
}

async function deriveDeviceId(publicKeyBytes, installationId) {
  const uuidBytes = new TextEncoder().encode(installationId);
  const material = new Uint8Array(publicKeyBytes.byteLength + uuidBytes.byteLength);
  material.set(publicKeyBytes, 0);
  material.set(uuidBytes, publicKeyBytes.byteLength);
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', material));

  let bitBuffer = 0;
  let bitCount = 0;
  let code = '';
  for (const byte of digest) {
    bitBuffer = (bitBuffer << 8) | byte;
    bitCount += 8;
    while (bitCount >= 5 && code.length < 12) {
      bitCount -= 5;
      code += DEVICE_ID_ALPHABET[(bitBuffer >>> bitCount) & 31];
    }
    bitBuffer = bitCount === 0 ? 0 : bitBuffer & ((1 << bitCount) - 1);
    if (code.length === 12) break;
  }
  return `${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8, 12)}`;
}

const DEVICE_ID_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/**
 * Stripe telling us a payment cleared, or a refund happened.
 *
 * The signature is verified before anything is read. Without that check this endpoint is a public
 * "activate any device" button — anyone who knows the URL could post a fabricated payment event.
 */
async function handleStripeWebhook(request, env) {
  if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405);

  const payload = await readTextWithinLimit(request, MAX_STRIPE_WEBHOOK_BODY_BYTES);
  if (payload === null) return json({ error: 'payload_too_large' }, 413);
  const signature = request.headers.get('stripe-signature');

  if (!(await isValidStripeSignature(payload, signature, env.STRIPE_WEBHOOK_SECRET))) {
    return json({ error: 'bad_signature' }, 400);
  }

  let event;
  try {
    event = JSON.parse(payload);
  } catch {
    return json({ error: 'bad_payload' }, 400);
  }

  if (!validStripeEvent(event)) return json({ error: 'bad_event' }, 400);
  const expectedLivemode = stripeLivemodeForEnvironment(env);
  if (expectedLivemode === null) return json({ error: 'stripe_mode_unconfigured' }, 503);
  if (event.livemode !== expectedLivemode) return json({ error: 'wrong_stripe_mode' }, 400);
  if (!SUPPORTED_STRIPE_EVENTS.has(event.type)) return json({ received: true, ignored: true });

  const now = new Date();
  const claim = await claimStripeEvent(env, event, now);
  if (claim === 'duplicate') return json({ received: true, duplicate: true });
  if (claim === 'in_progress') return json({ error: 'event_in_progress' }, 503);

  try {
    switch (event.type) {
      case 'checkout.session.completed':
      case 'checkout.session.async_payment_succeeded':
        return await processCheckoutPayment(event, env, now);
      case 'charge.refunded':
        return await processChargeRefund(event, env, now);
      case 'charge.dispute.created':
        return await processChargeDisputeCreated(event, env, now);
      case 'charge.dispute.closed':
        return await processChargeDisputeClosed(event, env, now);
      default:
        await finishStripeEvent(env, event.id, 'IGNORED', 'unsupported_type', now);
        return json({ received: true, ignored: true });
    }
  } catch (error) {
    await failStripeEvent(env, event.id, error?.name, new Date()).catch(() => {});
    throw error;
  }
}

const SUPPORTED_STRIPE_EVENTS = new Set([
  'checkout.session.completed',
  'checkout.session.async_payment_succeeded',
  'charge.refunded',
  'charge.dispute.created',
  'charge.dispute.closed',
]);

const STRIPE_DISPUTE_STATUSES = new Set([
  'warning_needs_response',
  'warning_under_review',
  'warning_closed',
  'needs_response',
  'under_review',
  'won',
  'lost',
  'prevented',
]);

const CLOSED_STRIPE_DISPUTE_STATUSES = new Set(['won', 'lost', 'warning_closed']);

/** Only a well-formed Stripe Event gets as far as the idempotency ledger. */
function validStripeEvent(event) {
  return Boolean(
    event &&
      stripeObjectId(event.id) &&
      typeof event.livemode === 'boolean' &&
      typeof event.type === 'string' &&
      event.type.length <= 100 &&
      event.data &&
      typeof event.data.object === 'object' &&
      event.data.object !== null,
  );
}

/** Fails closed when a deployment forgot to declare whether it accepts test or live events. */
function stripeLivemodeForEnvironment(env) {
  const mode = String(env.STRIPE_MODE ?? '').trim().toLowerCase();
  if (mode === 'live') return true;
  if (mode === 'test') return false;
  return null;
}

/** Claims one delivery. Processed/ignored ids are acknowledged; a live claimant asks for a retry. */
async function claimStripeEvent(env, event, now) {
  const eventId = stripeObjectId(event.id);
  const objectId = stripeObjectId(event.data.object?.id);
  const timestamp = iso(now);
  const inserted = await env.DB.prepare(
    `INSERT INTO stripe_events (
       event_id, event_type, object_id, status, received_at, updated_at
     ) VALUES (?, ?, ?, 'PROCESSING', ?, ?)
     ON CONFLICT(event_id) DO NOTHING`,
  )
    .bind(eventId, event.type, objectId, timestamp, timestamp)
    .run();
  if (statementChanges(inserted) > 0) return 'claimed';

  const existing = await env.DB.prepare('SELECT * FROM stripe_events WHERE event_id = ?')
    .bind(eventId)
    .first();
  if (existing?.status === 'PROCESSED' || existing?.status === 'IGNORED') return 'duplicate';

  const staleBefore = iso(new Date(now.getTime() - STRIPE_EVENT_CLAIM_TIMEOUT_MS));
  const reclaimed = await env.DB.prepare(
    `UPDATE stripe_events
     SET status = 'PROCESSING', detail = NULL, attempt_count = attempt_count + 1, updated_at = ?
     WHERE event_id = ?
       AND (status = 'FAILED' OR (status = 'PROCESSING' AND updated_at < ?))`,
  )
    .bind(timestamp, eventId, staleBefore)
    .run();
  return statementChanges(reclaimed) > 0 ? 'claimed' : 'in_progress';
}

async function processCheckoutPayment(event, env, now) {
  const session = event.data.object;
  const checked = validatePaidCheckoutSession(session);
  if (!checked.ok) {
    await finishStripeEvent(env, event.id, 'IGNORED', checked.reason, now);
    return json({ received: true, ignored: true });
  }

  const { deviceId, sessionId, paymentIntentId, currency, amountMinor } = checked;
  const existing = await env.DB.prepare('SELECT * FROM payments WHERE checkout_session_id = ?')
    .bind(sessionId)
    .first();

  if (existing && !paymentMatches(existing, checked)) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'payment_ledger_mismatch', now);
    return json({ received: true, ignored: true });
  }
  if (existing && existing.status !== 'PENDING') {
    await finishStripeEvent(env, event.id, 'PROCESSED', 'payment_already_transitioned', now);
    return json({ received: true, duplicate: true });
  }

  const paidAt = stripeEventTime(event, now);
  const paidAtText = iso(paidAt);
  const nowText = iso(now);
  const expiresAt = iso(addDays(paidAt, PAID_DAYS));

  const results = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO payments (
         checkout_session_id, payment_intent_id, device_id, product_id, amount_minor, currency,
         status, created_at, updated_at
       ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
       ON CONFLICT(checkout_session_id) DO NOTHING`,
    ).bind(
      sessionId,
      paymentIntentId,
      deviceId,
      LICENSE_PRODUCT.id,
      amountMinor,
      currency,
      paidAtText,
      nowText,
    ),
    env.DB.prepare(
      `UPDATE payments
       SET payment_intent_id = ?, status = 'PAID', paid_event_id = ?, paid_at = ?, updated_at = ?
       WHERE checkout_session_id = ?
         AND status = 'PENDING'
         AND device_id = ?
         AND product_id = ?
         AND amount_minor = ?
         AND currency = ?
         AND (payment_intent_id IS NULL OR payment_intent_id = ?)`,
    ).bind(
      paymentIntentId,
      event.id,
      paidAtText,
      nowText,
      sessionId,
      deviceId,
      LICENSE_PRODUCT.id,
      amountMinor,
      currency,
      paymentIntentId,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'ACTIVE', purchased_at = ?, expires_at = ?,
           stripe_session_id = ?, google_purchase_token_hash = NULL, updated_at = ?
       WHERE device_id = ? AND public_key IS NOT NULL
         AND EXISTS (
         SELECT 1 FROM payments WHERE checkout_session_id = ? AND paid_event_id = ?
       )
         AND (purchased_at IS NULL OR ? > purchased_at)`,
    ).bind(
      paidAtText,
      expiresAt,
      sessionId,
      nowText,
      deviceId,
      sessionId,
      event.id,
      paidAtText,
    ),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT device_id, 'purchased', checkout_session_id, ?
       FROM payments
       WHERE checkout_session_id = ? AND paid_event_id = ?`,
    ).bind(nowText, sessionId, event.id),
    finishStripeEventStatement(env, event.id, 'PROCESSED', 'payment_activated', now),
  ]);

  const paymentRecorded = statementChanges(results?.[1]) > 0;
  const entitlementChanged = statementChanges(results?.[2]) > 0;
  return json({
    received: true,
    activated: entitlementChanged,
    ...(paymentRecorded && !entitlementChanged ? { superseded: true } : {}),
  });
}

function validatePaidCheckoutSession(session) {
  const sessionId = stripeObjectId(session?.id);
  if (!sessionId) return { ok: false, reason: 'bad_session_id' };
  if (session.mode !== 'payment') return { ok: false, reason: 'bad_mode' };

  const deviceId = validDeviceId(session.metadata?.device_id);
  if (!deviceId) return { ok: false, reason: 'bad_device' };
  if (session.metadata?.product_id !== LICENSE_PRODUCT.id) {
    return { ok: false, reason: 'bad_product' };
  }
  if (session.metadata?.grant_days !== String(LICENSE_PRODUCT.grantDays)) {
    return { ok: false, reason: 'bad_grant' };
  }

  const currency = String(session.currency ?? '').toLowerCase();
  const price = exactPriceFor(currency);
  if (!price || session.metadata?.currency !== currency) {
    return { ok: false, reason: 'bad_currency' };
  }
  const amountMinor = Number(session.amount_total);
  if (
    !Number.isInteger(amountMinor) ||
    amountMinor !== price.amount ||
    session.metadata?.amount_minor !== String(price.amount)
  ) {
    return { ok: false, reason: 'bad_amount' };
  }

  const paymentIntentId = stripeObjectId(session.payment_intent);
  if (!paymentIntentId) return { ok: false, reason: 'missing_payment_intent' };
  if (session.payment_status !== 'paid') return { ok: false, reason: 'payment_not_paid' };

  return { ok: true, deviceId, sessionId, paymentIntentId, currency, amountMinor };
}

function paymentMatches(payment, expected) {
  return (
    payment.device_id === expected.deviceId &&
    payment.product_id === LICENSE_PRODUCT.id &&
    Number(payment.amount_minor) === expected.amountMinor &&
    payment.currency === expected.currency &&
    (!payment.payment_intent_id || payment.payment_intent_id === expected.paymentIntentId)
  );
}

async function processChargeRefund(event, env, now) {
  const charge = event.data.object;
  const chargeId = stripeObjectId(charge.id);
  const paymentIntentId = stripeObjectId(charge.payment_intent);
  if (!chargeId || !paymentIntentId) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'refund_missing_financial_id', now);
    return json({ received: true, ignored: true });
  }

  const payment = await env.DB.prepare('SELECT * FROM payments WHERE payment_intent_id = ?')
    .bind(paymentIntentId)
    .first();
  if (!payment) {
    // Stripe does not promise event ordering. A reversal can reach us before the Checkout event
    // that creates its payment row. Retry an unclassified/ours reversal instead of acknowledging it
    // for ever; only an explicitly different product is safe to discard as foreign.
    if (charge.metadata?.product_id && charge.metadata.product_id !== LICENSE_PRODUCT.id) {
      await finishStripeEvent(env, event.id, 'IGNORED', 'foreign_payment', now);
      return json({ received: true, ignored: true });
    }
    await failStripeEvent(env, event.id, 'payment_not_recorded', now);
    return json({ error: 'payment_not_recorded' }, 503);
  }
  if (payment.charge_id && payment.charge_id !== chargeId) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'charge_id_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const chargeAmount = Number(charge.amount);
  const refundedAmount = Number(charge.amount_refunded);
  const currency = String(charge.currency ?? '').toLowerCase();
  if (
    !Number.isInteger(chargeAmount) ||
    !Number.isInteger(refundedAmount) ||
    refundedAmount <= 0 ||
    refundedAmount > chargeAmount ||
    chargeAmount !== Number(payment.amount_minor) ||
    currency !== payment.currency
  ) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'refund_amount_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const fullRefund = charge.refunded === true && refundedAmount === chargeAmount;
  const nextStatus = fullRefund ? 'REFUNDED' : 'PARTIALLY_REFUNDED';
  const nowText = iso(now);
  const statements = [
    env.DB.prepare(
      `UPDATE payments
       SET charge_id = ?,
           amount_refunded_minor = MAX(amount_refunded_minor, ?),
            status = CASE
              WHEN status = 'REFUNDED' THEN status
              WHEN ? = 'REFUNDED' THEN 'REFUNDED'
              WHEN status = 'DISPUTED' THEN status
              ELSE 'PARTIALLY_REFUNDED'
            END,
           updated_at = ?
       WHERE checkout_session_id = ?`,
    ).bind(chargeId, refundedAmount, nextStatus, nowText, payment.checkout_session_id),
  ];

  if (fullRefund) {
    statements.push(
      env.DB.prepare(
        `UPDATE devices
         SET status = 'REFUNDED', updated_at = ?
         WHERE device_id = ? AND stripe_session_id = ?`,
      ).bind(nowText, payment.device_id, payment.checkout_session_id),
    );
  }
  statements.push(
    env.DB.prepare(
      'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
    ).bind(
      payment.device_id,
      fullRefund ? 'refunded' : 'refund_partial',
      `${refundedAmount}/${payment.amount_minor} ${payment.currency}`,
      nowText,
    ),
    finishStripeEventStatement(
      env,
      event.id,
      'PROCESSED',
      fullRefund ? 'full_refund' : 'partial_refund_no_revoke',
      now,
    ),
  );
  const results = await env.DB.batch(statements);
  const revoked = fullRefund && statementChanges(results?.[1]) > 0;
  return json({ received: true, revoked });
}

async function processChargeDisputeCreated(event, env, now) {
  const dispute = event.data.object;
  const disputeId = stripeObjectId(dispute.id);
  const paymentIntentId = stripeObjectId(dispute.payment_intent);
  const chargeId = stripeObjectId(dispute.charge);
  const disputeStatus = validStripeDisputeStatus(dispute.status);
  if (dispute.object !== 'dispute' || !disputeId || (!paymentIntentId && !chargeId)) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'dispute_missing_financial_id', now);
    return json({ received: true, ignored: true });
  }
  if (!disputeStatus) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'bad_dispute_status', now);
    return json({ received: true, ignored: true });
  }

  const resolved = await resolveDisputedPayment(env, paymentIntentId, chargeId);
  const payment = resolved.payment;
  if (!payment) {
    const productId = resolved.charge?.metadata?.product_id ?? dispute.metadata?.product_id;
    if (productId && productId !== LICENSE_PRODUCT.id) {
      await finishStripeEvent(env, event.id, 'IGNORED', 'foreign_payment', now);
      return json({ received: true, ignored: true });
    }
    await failStripeEvent(env, event.id, 'payment_not_recorded', now);
    return json({ error: 'payment_not_recorded' }, 503);
  }
  if (payment.charge_id && chargeId && payment.charge_id !== chargeId) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'charge_id_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const checked = validateStripeDispute(dispute, payment);
  if (!checked.ok) {
    await finishStripeEvent(env, event.id, 'IGNORED', checked.reason, now);
    return json({ received: true, ignored: true });
  }
  const existingDispute = await env.DB.prepare(
    'SELECT checkout_session_id FROM payment_disputes WHERE dispute_id = ?',
  )
    .bind(disputeId)
    .first();
  if (existingDispute && existingDispute.checkout_session_id !== payment.checkout_session_id) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'dispute_payment_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const nowText = iso(now);
  const openedAtText = iso(
    stripeEventTime({ created: dispute.created }, stripeEventTime(event, now)),
  );
  const results = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO payment_disputes (
         dispute_id, checkout_session_id, charge_id, amount_minor, currency, status,
         suspended_entitlement, opened_at, closed_at, updated_at
       ) VALUES (
         ?, ?, ?, ?, ?, ?,
         CASE WHEN EXISTS (
           SELECT 1 FROM devices
           WHERE device_id = ? AND stripe_session_id = ? AND status = 'ACTIVE'
         ) THEN 1 ELSE 0 END,
         ?, NULL, ?
       )
       ON CONFLICT(dispute_id) DO UPDATE SET
         charge_id = COALESCE(payment_disputes.charge_id, excluded.charge_id),
         amount_minor = excluded.amount_minor,
         currency = excluded.currency,
         status = CASE
           WHEN payment_disputes.status IN ('won', 'lost', 'warning_closed', 'prevented')
             THEN payment_disputes.status
           ELSE excluded.status
         END,
         suspended_entitlement = MAX(
           payment_disputes.suspended_entitlement,
           excluded.suspended_entitlement
         ),
         updated_at = excluded.updated_at
       WHERE payment_disputes.checkout_session_id = excluded.checkout_session_id`,
    ).bind(
      disputeId,
      payment.checkout_session_id,
      chargeId,
      checked.amountMinor,
      checked.currency,
      disputeStatus,
      payment.device_id,
      payment.checkout_session_id,
      openedAtText,
      nowText,
    ),
    env.DB.prepare(
      `UPDATE payments
       SET charge_id = COALESCE(charge_id, ?), dispute_id = ?,
           status = CASE
             WHEN status = 'REFUNDED' THEN status
             WHEN EXISTS (
               SELECT 1 FROM payment_disputes
               WHERE checkout_session_id = ?
                 AND status NOT IN ('won', 'warning_closed', 'prevented')
             ) THEN 'DISPUTED'
             WHEN amount_refunded_minor > 0 THEN 'PARTIALLY_REFUNDED'
             ELSE 'PAID'
           END,
           updated_at = ?
       WHERE checkout_session_id = ?`,
    ).bind(
      chargeId,
      disputeId,
      payment.checkout_session_id,
      nowText,
      payment.checkout_session_id,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = 'REVOKED', updated_at = ?
       WHERE device_id = ? AND stripe_session_id = ?
         AND EXISTS (
           SELECT 1 FROM payments
           WHERE checkout_session_id = ? AND status = 'DISPUTED'
         )`,
    ).bind(
      nowText,
      payment.device_id,
      payment.checkout_session_id,
      payment.checkout_session_id,
    ),
    env.DB.prepare(
      `INSERT INTO events (device_id, kind, detail, created_at)
       SELECT ?, 'disputed', ?, ?
       WHERE EXISTS (
         SELECT 1 FROM payment_disputes
         WHERE dispute_id = ? AND status NOT IN ('won', 'warning_closed', 'prevented')
       )`,
    ).bind(payment.device_id, disputeId, nowText, disputeId),
    finishStripeEventStatement(env, event.id, 'PROCESSED', 'payment_disputed', now),
  ]);
  return json({ received: true, revoked: statementChanges(results?.[2]) > 0 });
}

async function processChargeDisputeClosed(event, env, now) {
  const dispute = event.data.object;
  const disputeId = stripeObjectId(dispute.id);
  const paymentIntentId = stripeObjectId(dispute.payment_intent);
  const chargeId = stripeObjectId(dispute.charge);
  const disputeStatus = validStripeDisputeStatus(dispute.status);
  if (dispute.object !== 'dispute' || !disputeId || (!paymentIntentId && !chargeId)) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'dispute_missing_financial_id', now);
    return json({ received: true, ignored: true });
  }
  if (!disputeStatus || !CLOSED_STRIPE_DISPUTE_STATUSES.has(disputeStatus)) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'bad_closed_dispute_status', now);
    return json({ received: true, ignored: true });
  }

  const resolved = await resolveDisputedPayment(env, paymentIntentId, chargeId);
  const payment = resolved.payment;
  if (!payment) {
    const productId = resolved.charge?.metadata?.product_id ?? dispute.metadata?.product_id;
    if (productId && productId !== LICENSE_PRODUCT.id) {
      await finishStripeEvent(env, event.id, 'IGNORED', 'foreign_payment', now);
      return json({ received: true, ignored: true });
    }
    await failStripeEvent(env, event.id, 'payment_not_recorded', now);
    return json({ error: 'payment_not_recorded' }, 503);
  }
  if (payment.charge_id && chargeId && payment.charge_id !== chargeId) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'charge_id_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const checked = validateStripeDispute(dispute, payment);
  if (!checked.ok) {
    await finishStripeEvent(env, event.id, 'IGNORED', checked.reason, now);
    return json({ received: true, ignored: true });
  }
  const existingDispute = await env.DB.prepare(
    'SELECT checkout_session_id FROM payment_disputes WHERE dispute_id = ?',
  )
    .bind(disputeId)
    .first();
  if (existingDispute && existingDispute.checkout_session_id !== payment.checkout_session_id) {
    await finishStripeEvent(env, event.id, 'IGNORED', 'dispute_payment_mismatch', now);
    return json({ received: true, ignored: true });
  }

  const nowText = iso(now);
  const openedAtText = iso(
    stripeEventTime({ created: dispute.created }, stripeEventTime(event, now)),
  );
  const closedAtText = iso(stripeEventTime(event, now));
  const eventKind =
    disputeStatus === 'won'
      ? 'dispute_won'
      : disputeStatus === 'warning_closed'
        ? 'dispute_warning_closed'
        : 'dispute_lost';
  const results = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO payment_disputes (
         dispute_id, checkout_session_id, charge_id, amount_minor, currency, status,
         suspended_entitlement, opened_at, closed_at, updated_at
       ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
       ON CONFLICT(dispute_id) DO UPDATE SET
         charge_id = COALESCE(payment_disputes.charge_id, excluded.charge_id),
         amount_minor = excluded.amount_minor,
         currency = excluded.currency,
         status = CASE
           WHEN payment_disputes.status IN ('won', 'lost', 'warning_closed', 'prevented')
             THEN payment_disputes.status
           ELSE excluded.status
         END,
         closed_at = COALESCE(payment_disputes.closed_at, excluded.closed_at),
         updated_at = excluded.updated_at
       WHERE payment_disputes.checkout_session_id = excluded.checkout_session_id`,
    ).bind(
      disputeId,
      payment.checkout_session_id,
      chargeId,
      checked.amountMinor,
      checked.currency,
      disputeStatus,
      openedAtText,
      closedAtText,
      nowText,
    ),
    env.DB.prepare(
      `UPDATE payments
       SET charge_id = COALESCE(charge_id, ?), dispute_id = ?,
           status = CASE
             WHEN status = 'REFUNDED' THEN status
             WHEN EXISTS (
               SELECT 1 FROM payment_disputes
               WHERE checkout_session_id = ?
                 AND status NOT IN ('won', 'warning_closed', 'prevented')
             ) THEN 'DISPUTED'
             WHEN amount_refunded_minor > 0 THEN 'PARTIALLY_REFUNDED'
             ELSE 'PAID'
           END,
           updated_at = ?
       WHERE checkout_session_id = ?`,
    ).bind(
      chargeId,
      disputeId,
      payment.checkout_session_id,
      nowText,
      payment.checkout_session_id,
    ),
    env.DB.prepare(
      `UPDATE devices
       SET status = CASE
             WHEN expires_at IS NOT NULL AND expires_at > ? THEN 'ACTIVE'
             ELSE 'EXPIRED'
           END,
           updated_at = ?
       WHERE device_id = ? AND stripe_session_id = ? AND status = 'REVOKED'
         AND EXISTS (
           SELECT 1 FROM payments
           WHERE checkout_session_id = ? AND status IN ('PAID', 'PARTIALLY_REFUNDED')
         )
         AND EXISTS (
           SELECT 1 FROM payment_disputes
           WHERE checkout_session_id = ? AND suspended_entitlement = 1
         )
         AND NOT EXISTS (
           SELECT 1 FROM events
           WHERE device_id = ? AND kind = 'revoked'
             AND created_at >= (
               SELECT MIN(opened_at) FROM payment_disputes WHERE checkout_session_id = ?
             )
         )`,
    ).bind(
      nowText,
      nowText,
      payment.device_id,
      payment.checkout_session_id,
      payment.checkout_session_id,
      payment.checkout_session_id,
      payment.device_id,
      payment.checkout_session_id,
    ),
    env.DB.prepare(
      'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
    ).bind(payment.device_id, eventKind, disputeId, nowText),
    finishStripeEventStatement(env, event.id, 'PROCESSED', `dispute_closed_${disputeStatus}`, now),
  ]);

  const entitlementChanged = statementChanges(results?.[2]) > 0;
  const device = entitlementChanged
    ? await env.DB.prepare('SELECT status FROM devices WHERE device_id = ?')
        .bind(payment.device_id)
        .first()
    : null;
  const storedDispute = await env.DB.prepare(
    'SELECT status FROM payment_disputes WHERE dispute_id = ?',
  )
    .bind(disputeId)
    .first();
  return json({
    received: true,
    status: storedDispute?.status ?? disputeStatus,
    restored: entitlementChanged && device?.status === 'ACTIVE',
    expired: entitlementChanged && device?.status === 'EXPIRED',
  });
}

function validStripeDisputeStatus(value) {
  return typeof value === 'string' && STRIPE_DISPUTE_STATUSES.has(value) ? value : null;
}

function validateStripeDispute(dispute, payment) {
  const amountMinor = Number(dispute.amount);
  const currency = String(dispute.currency ?? '').toLowerCase();
  if (
    !Number.isInteger(amountMinor) ||
    amountMinor <= 0 ||
    amountMinor > Number(payment.amount_minor) ||
    currency !== payment.currency
  ) {
    return { ok: false, reason: 'dispute_amount_mismatch' };
  }
  return { ok: true, amountMinor, currency };
}

/** Resolves a Dispute without trusting metadata that Stripe does not guarantee it inherits. */
async function resolveDisputedPayment(env, paymentIntentId, chargeId) {
  if (paymentIntentId) {
    const payment = await env.DB.prepare('SELECT * FROM payments WHERE payment_intent_id = ?')
      .bind(paymentIntentId)
      .first();
    return { payment, charge: null };
  }

  const byCharge = await env.DB.prepare('SELECT * FROM payments WHERE charge_id = ?')
    .bind(chargeId)
    .first();
  if (byCharge) return { payment: byCharge, charge: null };

  const charge = await retrieveStripeCharge(env, chargeId);
  const resolvedPaymentIntentId = stripeObjectId(charge.payment_intent);
  if (!resolvedPaymentIntentId) return { payment: null, charge };

  const payment = await env.DB.prepare('SELECT * FROM payments WHERE payment_intent_id = ?')
    .bind(resolvedPaymentIntentId)
    .first();
  return { payment, charge };
}

/** Retrieves the authoritative Charge only when the Dispute omitted its nullable PaymentIntent. */
async function retrieveStripeCharge(env, chargeId) {
  if (!env.STRIPE_SECRET_KEY) throw namedError('StripeSecretMissing');

  const response = await fetch(
    `https://api.stripe.com/v1/charges/${encodeURIComponent(chargeId)}`,
    {
      method: 'GET',
      headers: { authorization: `Bearer ${env.STRIPE_SECRET_KEY}` },
    },
  );
  if (!response.ok) throw namedError('StripeChargeLookupFailed');

  const charge = await response.json();
  if (charge?.object !== 'charge' || stripeObjectId(charge.id) !== chargeId) {
    throw namedError('StripeChargeLookupMismatch');
  }
  return charge;
}

function namedError(name) {
  const error = new Error(name);
  error.name = name;
  return error;
}

function finishStripeEventStatement(env, eventId, status, detail, now) {
  return env.DB.prepare(
    `UPDATE stripe_events SET status = ?, detail = ?, updated_at = ?
     WHERE event_id = ? AND status = 'PROCESSING'`,
  ).bind(status, safeLedgerDetail(detail), iso(now), eventId);
}

async function finishStripeEvent(env, eventId, status, detail, now) {
  await finishStripeEventStatement(env, eventId, status, detail, now).run();
}

async function failStripeEvent(env, eventId, errorName, now) {
  await env.DB.prepare(
    `UPDATE stripe_events SET status = 'FAILED', detail = ?, updated_at = ?
     WHERE event_id = ? AND status = 'PROCESSING'`,
  )
    .bind(safeLedgerDetail(errorName || 'processing_error'), iso(now), eventId)
    .run();
}

function stripeObjectId(value) {
  const candidate = typeof value === 'string' ? value : value?.id;
  return typeof candidate === 'string' && /^[A-Za-z0-9_]{3,255}$/.test(candidate)
    ? candidate
    : null;
}

function stripeEventTime(event, fallback) {
  const seconds = Number(event.created);
  if (!Number.isFinite(seconds) || seconds <= 0) return fallback;
  const parsed = new Date(seconds * 1000);
  return Number.isNaN(parsed.getTime()) ? fallback : parsed;
}

function safeLedgerDetail(value) {
  return String(value ?? 'unknown').replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 80);
}

/**
 * Builds and signs the answer.
 *
 * The nonce the client sent is echoed back inside the signed document. That is what makes a
 * replayed licence detectable: an old copy carries an old challenge, and the client refuses it.
 */
async function respondWithLicense(env, deviceId, nonce, now) {
  const device = await env.DB.prepare('SELECT * FROM devices WHERE device_id = ?')
    .bind(deviceId)
    .first();

  if (!device) return json({ error: 'not_found' }, 404);

  const document = {
    deviceId,
    state: device.status,
    serverTime: iso(now),
  };
  if (device.trial_ends_at) document.trialEndsAt = device.trial_ends_at;
  if (device.expires_at) document.expiresAt = device.expires_at;
  if (nonce) document.nonce = String(nonce).slice(0, 128);

  const signed = await signLicense(document, env.SIGNING_KEY);
  return json({ payload: signed.payload, signature: signed.signature });
}

/** Moves a device out of TRIAL or ACTIVE once its date has passed, so the table matches reality. */
async function expireIfDue(env, device, now) {
  const isTrialOver = device.status === 'TRIAL' && new Date(device.trial_ends_at) < now;
  const isPaidOver =
    device.status === 'ACTIVE' && device.expires_at && new Date(device.expires_at) < now;

  if (isTrialOver || isPaidOver) {
    await env.DB.prepare("UPDATE devices SET status = 'EXPIRED', updated_at = ? WHERE device_id = ?")
      .bind(iso(now), device.device_id)
      .run();
    device.status = 'EXPIRED';
  }
}

/**
 * Verifies Stripe's webhook signature.
 *
 * Implemented here rather than with Stripe's SDK, which expects Node's crypto and does not run in a
 * Worker. The scheme is HMAC-SHA256 over `timestamp.payload`.
 *
 * The timestamp check is not optional: without it, a captured webhook can be replayed for ever, and
 * the one that says "this device paid" is the one worth capturing.
 */
async function isValidStripeSignature(payload, header, secret) {
  if (!header || !secret) return false;

  let timestamp = null;
  const provided = [];
  for (const piece of header.split(',')) {
    const separator = piece.indexOf('=');
    if (separator < 1) continue;
    const key = piece.slice(0, separator).trim();
    const value = piece.slice(separator + 1).trim();
    if (key === 't' && timestamp === null) timestamp = value;
    if (key === 'v1' && value) provided.push(value);
  }
  if (!timestamp || provided.length === 0) return false;

  // Five minutes, as Stripe recommends. Older than that is a replay rather than a slow network.
  const age = Math.abs(Date.now() / 1000 - Number(timestamp));
  if (!Number.isFinite(age) || age > 300) return false;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const expected = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(`${timestamp}.${payload}`),
  );

  const expectedHex = toHex(new Uint8Array(expected));
  return provided.some((candidate) => timingSafeEqual(expectedHex, candidate));
}

/** Web Crypto returns bytes; Stripe writes the HMAC as lowercase hexadecimal. */
function toHex(bytes) {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * Compares without returning early on the first difference.
 *
 * A comparison that stops at the first mismatched character reveals, through timing, how much of a
 * guess was right — enough to forge a signature one character at a time.
 */
function timingSafeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

/**
 * Accepts only the XXXX-XXXX-XXXX shape the client produces.
 *
 * Everything else is refused before it reaches a query. The queries are parameterised, so this is
 * not the injection defence — it is what stops a table filling with junk device ids from anyone
 * who finds the endpoint.
 */
function validDeviceId(value) {
  const candidate = String(value ?? '').trim().toUpperCase();
  return /^[A-Z2-9]{4}(-[A-Z2-9]{4}){2}$/.test(candidate) ? candidate : null;
}

async function recordEvent(env, deviceId, kind, detail, now) {
  await env.DB.prepare(
    'INSERT INTO events (device_id, kind, detail, created_at) VALUES (?, ?, ?, ?)',
  )
    .bind(deviceId, kind, detail, iso(now))
    .run();
}

async function readJson(request) {
  try {
    const contentType = String(request.headers.get('content-type') ?? '').toLowerCase();
    if (!contentType.startsWith('application/json')) return {};
    const text = await readTextWithinLimit(request, MAX_DEVICE_API_BODY_BYTES);
    if (text === null) return {};
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

/** The product's HTML forms are URL-encoded; parsing a bounded copy closes chunked-body bypasses. */
async function readUrlEncodedForm(request, maximumBytes) {
  const contentType = String(request.headers.get('content-type') ?? '').toLowerCase();
  if (!contentType.startsWith('application/x-www-form-urlencoded')) return null;
  const text = await readTextWithinLimit(request, maximumBytes);
  return text === null ? null : new URLSearchParams(text);
}

/** Reads a request stream without allowing a chunked body to bypass the declared-length check. */
async function readTextWithinLimit(request, maximumBytes) {
  if (declaredBodyExceeds(request, maximumBytes)) return null;
  if (!request.body) return '';

  const reader = request.body.getReader();
  const decoder = new TextDecoder();
  let bytesRead = 0;
  let text = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    bytesRead += value.byteLength;
    if (bytesRead > maximumBytes) {
      await reader.cancel().catch(() => {});
      return null;
    }
    text += decoder.decode(value, { stream: true });
  }
  return text + decoder.decode();
}

function declaredBodyExceeds(request, maximumBytes) {
  const raw = request.headers.get('content-length');
  if (raw === null) return false;
  const declared = Number(raw);
  return Number.isFinite(declared) && declared > maximumBytes;
}

/**
 * Cloudflare's local rate-limit binding is a coarse abuse brake; cryptographic possession and D1
 * idempotency remain the authority. Tests and offline tooling omit the binding deliberately.
 */
async function enforceRouteRateLimit(request, env, url) {
  if (request.method === 'OPTIONS') return null;
  const bindingName = rateLimitBindingFor(url.pathname);
  const limiter = bindingName ? env[bindingName] : null;
  if (!limiter || typeof limiter.limit !== 'function') return null;

  // Cloudflare supplies this header at the edge. If it is unavailable in local tooling, skip rather
  // than putting every local request into one shared "unknown" bucket.
  const actor = request.headers.get('cf-connecting-ip');
  if (!actor) return null;

  try {
    const result = await limiter.limit({ key: `${url.pathname}:${actor}` });
    if (result?.success !== false) return null;
    return json({ error: 'rate_limited' }, 429, { 'retry-after': '60' });
  } catch (error) {
    // Licence validation is an availability boundary. A limiter outage must not lock paid users out;
    // the request still has to pass its normal proof/signature checks below.
    console.error('rate limiter failed', error?.name);
    return null;
  }
}

function rateLimitBindingFor(pathname) {
  if (pathname === '/v1/register') return 'REGISTRATION_RATE_LIMITER';
  if (pathname === '/v1/validate' || pathname === '/v1/redeem' || pathname === '/v1/key-info') {
    // Same limiter as redeeming, deliberately: describing a key is a cheaper guess than spending
    // one, so it must not be a cheaper way to enumerate them.
    return 'LICENSE_API_RATE_LIMITER';
  }
  if (pathname === '/v1/google-play/purchase') return 'CHECKOUT_RATE_LIMITER';
  if (pathname === '/v1/signing-key-check') return 'REGISTRATION_RATE_LIMITER';
  if (pathname === '/checkout') return 'CHECKOUT_RATE_LIMITER';
  if (pathname.startsWith('/admin/')) return 'ADMIN_RATE_LIMITER';
  return null;
}

function addDays(from, days) {
  return new Date(from.getTime() + days * 24 * 60 * 60 * 1000);
}

function iso(date) {
  return date.toISOString();
}

/** Normalises the affected-row count returned by D1 and by the local SQLite test adapter. */
function statementChanges(result) {
  const value = result?.meta?.changes ?? result?.changes ?? 0;
  return Number.isFinite(Number(value)) ? Number(value) : 0;
}

/**
 * The customer-facing policy.
 *
 * No scripts at all. These pages are a headline, a price and a form, so nothing is given up by
 * forbidding script entirely — and forbidding it is what makes an injected `<script>` inert even if
 * an escape somewhere were ever wrong. The payment page is the one page where that matters most.
 */
const PUBLIC_CSP =
  "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; form-action 'self' https://checkout.stripe.com; base-uri 'none'";

/**
 * The admin panel's policy.
 *
 * Looser, because the panel is an application rather than a document and needs its own script. Kept
 * as a separate constant so loosening it here can never quietly loosen the customer pages: the two
 * are different values used in different places, not one value with an exception.
 */
const ADMIN_CSP =
  "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'";

function html(body, status = 200, policy = PUBLIC_CSP) {
  return new Response(body, {
    status,
    headers: {
      'content-type': 'text/html; charset=utf-8',
      // No framing: these pages have a payment button and an admin panel, and letting either be
      // embedded invisibly in another site is the setup for a clickjacked purchase or grant.
      'x-frame-options': 'DENY',
      'referrer-policy': 'no-referrer',
      'x-content-type-options': 'nosniff',
      'content-security-policy': policy,
      'cache-control': 'no-store',
      // Keep these pages out of search results.
      //
      // The admin panel is a login box at a fixed, guessable path. Nothing behind it is reachable
      // without the token, so being indexed is not a breach — but a panel that turns up in a search
      // for the product is an invitation to try the box, and the activation pages carry a device
      // code in their query string that has no business in anyone's index either.
      'x-robots-tag': 'noindex, nofollow, noarchive',
      // The panel grants licences and the payment pages take a device code, so the browser should
      // refuse to talk to either over plain http after the first visit.
      'strict-transport-security': 'max-age=31536000; includeSubDomains',
    },
  });
}

function json(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json',
      'cache-control': 'no-store',
      ...extraHeaders,
    },
  });
}

const PUBLIC_CORS_PATHS = new Set([
  '/v1/register',
  '/v1/validate',
  '/v1/redeem',
  '/v1/key-info',
  '/v1/price',
]);

/** Adds browser access only after routing has established that this is a public app endpoint. */
function withCors(response) {
  const headers = new Headers(response.headers);
  for (const [name, value] of Object.entries(corsHeaders())) headers.set(name, value);
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'POST, OPTIONS',
    // Native clients are unaffected by CORS; this narrow allowance is only for browser app clients.
    'access-control-allow-headers': 'content-type',
  };
}
