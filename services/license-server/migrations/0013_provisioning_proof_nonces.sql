-- Provisioning requests now spend a one-time nonce against the device's pinned key, like
-- validate and redeem. The nonce ledger's action constraint has to admit them first, or every
-- claim would fail on insert. SQLite cannot extend a CHECK constraint in place, so the ledger is
-- rebuilt exactly as 0004 did, preserving the rows. Apply this before deploying the Worker that
-- uses it.
CREATE TABLE device_proof_nonces_v3 (
    device_id TEXT NOT NULL,
    nonce     TEXT NOT NULL,
    action    TEXT NOT NULL CHECK (action IN ('register', 'validate', 'redeem', 'google_play_purchase', 'provisioning')),
    used_at   TEXT NOT NULL,
    PRIMARY KEY (device_id, nonce)
);

INSERT INTO device_proof_nonces_v3 (device_id, nonce, action, used_at)
SELECT device_id, nonce, action, used_at FROM device_proof_nonces;

DROP TABLE device_proof_nonces;
ALTER TABLE device_proof_nonces_v3 RENAME TO device_proof_nonces;
CREATE INDEX IF NOT EXISTS device_proof_nonces_by_age ON device_proof_nonces (used_at);
