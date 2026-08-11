-- Google Play purchase ledger and proof action.
--
-- The migration is deliberately idempotent where SQLite permits it. D1 applies each numbered
-- migration once; the test suite also exercises a legacy schema through the full chain.

ALTER TABLE devices ADD COLUMN google_purchase_token_hash TEXT;
CREATE INDEX IF NOT EXISTS devices_by_google_purchase ON devices (google_purchase_token_hash);

-- SQLite cannot extend a CHECK constraint in place, so preserve the nonce ledger while replacing
-- only its action constraint. The migration runs while Worker deployment is gated, avoiding a
-- registration request in the few milliseconds between tables.
CREATE TABLE device_proof_nonces_v2 (
    device_id TEXT NOT NULL,
    nonce     TEXT NOT NULL,
    action    TEXT NOT NULL CHECK (action IN ('register', 'validate', 'redeem', 'google_play_purchase')),
    used_at   TEXT NOT NULL,
    PRIMARY KEY (device_id, nonce)
);

INSERT INTO device_proof_nonces_v2 (device_id, nonce, action, used_at)
SELECT device_id, nonce, action, used_at FROM device_proof_nonces;

DROP TABLE device_proof_nonces;
ALTER TABLE device_proof_nonces_v2 RENAME TO device_proof_nonces;
CREATE INDEX IF NOT EXISTS device_proof_nonces_by_age ON device_proof_nonces (used_at);

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
