-- ADR-004 installation identity and proof-of-possession migration.
--
-- This migration is intentionally fail-closed. Existing device rows receive a NULL public_key and
-- cannot validate, redeem or begin a new Checkout until support performs an audited entitlement
-- transfer to a freshly registered UUID + P-256 identity. The public device code alone is not
-- enough to claim an old paid row, so this migration never auto-pins a key supplied by a request.
--
-- Cloudflare applies numbered D1 migrations once. `ALTER TABLE ... ADD COLUMN` is consequently not
-- repeatable; the CREATE statements are idempotent for recovery and fresh-schema tests.

ALTER TABLE devices ADD COLUMN public_key TEXT;

CREATE TABLE IF NOT EXISTS device_proof_nonces (
    device_id TEXT NOT NULL,
    nonce     TEXT NOT NULL,
    action    TEXT NOT NULL CHECK (action IN ('register', 'validate', 'redeem')),
    used_at   TEXT NOT NULL,
    PRIMARY KEY (device_id, nonce)
);

CREATE INDEX IF NOT EXISTS device_proof_nonces_by_age ON device_proof_nonces (used_at);
