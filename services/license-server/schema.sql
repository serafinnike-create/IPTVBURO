-- IPTV BURO licence server — Cloudflare D1 schema.
--
-- Apply with:  wrangler d1 execute iptvburo-licences --file=schema.sql --remote
--
-- Design notes that matter for money and support:
--
--   * A device is the unit of sale. One purchase, one device, as the product promises. Nothing here
--     stores a customer identity — Stripe holds that, and it is where a refund conversation happens
--     anyway. The less that lives here, the less there is to lose.
--
--   * Every date is stored as an ISO-8601 string in UTC. SQLite has no date type, and integers
--     would make a support query ("when does this expire?") unreadable at the moment it is most
--     needed. The strings sort correctly, which is the property comparisons rely on.
--
--   * Nothing is ever deleted. A refund flips a status; it does not remove a row. Working out what
--     happened to a customer six months later is impossible against a table that forgets, and that
--     conversation is exactly when the answer matters.

CREATE TABLE IF NOT EXISTS devices (
    -- Public activation code derived from the installation UUID and P-256 public key.
    device_id     TEXT PRIMARY KEY,

    -- Pinned P-256 SPKI (DER, standard Base64). Nullable only so the 0002 migration can preserve
    -- historical rows. A null value fails closed and must be migrated manually; no request may pin
    -- a key onto a pre-existing public code.
    public_key    TEXT,

    -- Legacy-only column retained for an in-place D1 migration and old support tooling. New clients
    -- never send a MAC and the Worker never writes one.
    mac_address   TEXT,

    -- TRIAL, ACTIVE, REVOKED, REFUNDED, EXPIRED. Mirrors EntitlementState in the client.
    status        TEXT NOT NULL,

    -- When the server first saw this device. The trial is measured from here, by the server's
    -- clock, which is what makes moving the local clock pointless.
    first_seen_at TEXT NOT NULL,
    trial_ends_at TEXT NOT NULL,

    -- Set when payment clears. Null for a device that has never paid.
    purchased_at  TEXT,
    expires_at    TEXT,

    -- The Stripe session that paid for it, so a refund can find its way back here. Also the
    -- idempotency key: a webhook Stripe delivers twice must not extend a licence twice.
    stripe_session_id TEXT,

    -- SHA-256 of the Google Play purchase token that currently owns the entitlement. The opaque
    -- token itself is encrypted in google_play_purchases and never appears in support output.
    google_purchase_token_hash TEXT,

    -- Free-text, for the manual grants. "friend of Lucas", "paid cash", "reseller batch 3" — the
    -- answer to "why is this one active?" a year from now.
    note          TEXT,

    -- The machine this device belongs to, as the stable installation UUID the client derives from
    -- the Windows MachineGuid.
    --
    -- This is what closes the trial reset. The installation id used to be random, so deleting three
    -- files on disk produced a device the server had never met, and it correctly granted a fresh
    -- seven days — repeatable for ever. Anchored to the machine, the same computer comes back with
    -- the same value and its earlier trial can be found.
    --
    -- Nullable: rows written before this existed have none, and a null anchor simply means the row
    -- cannot be matched by machine, which is exactly the old behaviour.
    machine_anchor TEXT,

    -- Coarse support information reported by an authenticated client. Never hostname, serial,
    -- MAC, Android ID or account data. Old clients leave these null and continue to work.
    device_type  TEXT,
    platform     TEXT,
    manufacturer TEXT,
    model        TEXT,
    os_version   TEXT,
    app_version  TEXT,
    last_seen_at TEXT,

    -- Admin "delete" is reversible archival. The row and its entitlement history must survive,
    -- both for audit and so deleting it cannot manufacture a new seven-day trial.
    archived_at   TEXT,
    archived_note TEXT,

    updated_at    TEXT NOT NULL
);

-- Given a machine, find the earliest trial it ever started. Not unique: one machine legitimately
-- holds several device rows over its life, after a key rotation or a support-issued replacement.
CREATE INDEX IF NOT EXISTS devices_machine_anchor ON devices (machine_anchor, first_seen_at);

-- Legacy support tooling can still search historical MAC values. New rows leave this column NULL.
CREATE INDEX IF NOT EXISTS devices_by_mac ON devices (mac_address);

-- A refund arrives naming a Stripe session, and has to find the device it paid for.
CREATE INDEX IF NOT EXISTS devices_by_session ON devices (stripe_session_id);
CREATE INDEX IF NOT EXISTS devices_by_google_purchase ON devices (google_purchase_token_hash);
CREATE INDEX IF NOT EXISTS devices_by_last_seen ON devices (last_seen_at DESC);
CREATE INDEX IF NOT EXISTS devices_by_archive ON devices (archived_at, updated_at DESC);

-- A client-generated nonce is accepted once. Without this ledger a captured, otherwise valid proof
-- could be replayed without possessing the private key. Payloads and proofs are intentionally not
-- retained; the nonce, action and timestamp are enough to reject a replay.
CREATE TABLE IF NOT EXISTS device_proof_nonces (
    device_id TEXT NOT NULL,
    nonce     TEXT NOT NULL,
    action    TEXT NOT NULL CHECK (action IN ('register', 'validate', 'redeem', 'google_play_purchase')),
    used_at   TEXT NOT NULL,
    PRIMARY KEY (device_id, nonce)
);

CREATE INDEX IF NOT EXISTS device_proof_nonces_by_age ON device_proof_nonces (used_at);

-- One row per Stripe Checkout Session. The device row remains the current entitlement; this table
-- is the financial history that lets a later refund or dispute identify the exact purchase it
-- belongs to without trusting mutable metadata from the reversal event.
CREATE TABLE IF NOT EXISTS payments (
    checkout_session_id   TEXT PRIMARY KEY,
    payment_intent_id     TEXT UNIQUE,
    charge_id             TEXT UNIQUE,
    dispute_id            TEXT,
    device_id             TEXT NOT NULL,
    product_id            TEXT NOT NULL,
    amount_minor          INTEGER NOT NULL CHECK (amount_minor > 0),
    currency              TEXT NOT NULL CHECK (currency IN ('eur', 'usd', 'brl')),
    status                TEXT NOT NULL CHECK (
        status IN ('PENDING', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'DISPUTED')
    ),
    amount_refunded_minor INTEGER NOT NULL DEFAULT 0 CHECK (amount_refunded_minor >= 0),
    paid_event_id         TEXT UNIQUE,
    paid_at               TEXT,
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS payments_by_device ON payments (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS payments_by_status ON payments (status, updated_at);

-- One payment can receive more than one partial dispute. Keeping each Dispute separately is what
-- prevents one won case from restoring a licence while another case is still open or was lost.
CREATE TABLE IF NOT EXISTS payment_disputes (
    dispute_id              TEXT PRIMARY KEY,
    checkout_session_id     TEXT NOT NULL,
    charge_id               TEXT,
    amount_minor            INTEGER NOT NULL CHECK (amount_minor > 0),
    currency                TEXT NOT NULL CHECK (currency IN ('eur', 'usd', 'brl')),
    status                  TEXT NOT NULL CHECK (
        status IN (
            'warning_needs_response', 'warning_under_review', 'warning_closed',
            'needs_response', 'under_review', 'won', 'lost', 'prevented'
        )
    ),
    suspended_entitlement   INTEGER NOT NULL DEFAULT 0 CHECK (suspended_entitlement IN (0, 1)),
    opened_at               TEXT NOT NULL,
    closed_at               TEXT,
    updated_at              TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS payment_disputes_by_payment
    ON payment_disputes (checkout_session_id, status, updated_at);

-- Stripe retries and can deliver events out of order. Claiming an event here before applying it
-- makes every financial transition idempotent. Payloads are deliberately not stored: they can carry
-- customer and payment data that this service does not need to retain.
CREATE TABLE IF NOT EXISTS stripe_events (
    event_id       TEXT PRIMARY KEY,
    event_type     TEXT NOT NULL,
    object_id      TEXT,
    status         TEXT NOT NULL CHECK (status IN ('PROCESSING', 'PROCESSED', 'IGNORED', 'FAILED')),
    detail         TEXT,
    attempt_count  INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    received_at    TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS stripe_events_by_status ON stripe_events (status, updated_at);

-- Google Play consumable one-time purchases. The token hash is the stable idempotency key; the opaque
-- token is AES-GCM encrypted so the Worker can re-query Google without leaving a bearer credential
-- in readable D1 rows or admin output.
CREATE TABLE IF NOT EXISTS google_play_purchases (
    purchase_token_hash    TEXT PRIMARY KEY,
    token_ciphertext       TEXT NOT NULL,
    device_id              TEXT NOT NULL,
    product_id             TEXT NOT NULL,
    purchase_option_id     TEXT NOT NULL,
    obfuscated_account_id  TEXT NOT NULL,
    status                 TEXT NOT NULL CHECK (status IN ('PENDING', 'PURCHASED', 'CANCELLED', 'REFUNDED')),
    acknowledgement_state  TEXT NOT NULL,
    test_purchase          INTEGER NOT NULL DEFAULT 0 CHECK (test_purchase IN (0, 1)),
    purchase_completed_at  TEXT,
    expires_at             TEXT,
    last_checked_at        TEXT,
    created_at             TEXT NOT NULL,
    updated_at             TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS google_play_purchases_by_device
    ON google_play_purchases (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS google_play_purchases_by_status
    ON google_play_purchases (status, updated_at);

-- Keys handed out by hand: "someone asked to try it for a month".
--
-- Separate from devices because a key exists before it belongs to anyone. Redeeming binds it.
CREATE TABLE IF NOT EXISTS redemption_keys (
    key_code    TEXT PRIMARY KEY,

    -- How long redeeming grants. Days rather than an end date: a key printed today and redeemed in
    -- March should still give a full month.
    grant_days  INTEGER NOT NULL,

    created_at  TEXT NOT NULL,

    -- A key that is no use after a date, so a batch handed to a reseller cannot be used for ever.
    -- Null means it does not expire on its own.
    valid_until TEXT,

    -- Filled in on redemption. A key is single-use: one key, one device, or one code posted online
    -- unlocks every install that finds it.
    redeemed_by TEXT,
    redeemed_at TEXT,

    note        TEXT
);

-- An append-only record of what happened, for the questions that arrive months later:
-- "I paid and it stopped working", "was this refunded?", "how many devices did this key unlock?"
--
-- Deliberately not derived from the devices table. That one holds the current state; this holds how
-- it got there, and the two answer different questions.
CREATE TABLE IF NOT EXISTS events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id   TEXT,
    kind        TEXT NOT NULL,   -- registered, validated, purchased, refunded, revoked, redeemed, granted
    detail      TEXT,
    created_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS events_by_device ON events (device_id, created_at);
