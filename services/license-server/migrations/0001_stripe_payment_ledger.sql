-- P0 payment ledger for an existing IPTV BURO D1 database.
--
-- Safe to apply more than once. It adds no secret or customer payload and leaves the current
-- devices/events tables intact.

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
