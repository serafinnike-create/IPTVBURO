-- Sync between a household's devices: favourites and playback positions. See src/sync.js.
--
-- Sync calls are signed with the device's pinned key and spend a one-time nonce, so the nonce
-- ledger's CHECK has to admit the 'sync' action. SQLite cannot extend a CHECK in place, so the
-- ledger is rebuilt once more, rows preserved, as 0004 and 0013 did. Apply before deploying the
-- Worker that uses it.
CREATE TABLE device_proof_nonces_v4 (
    device_id TEXT NOT NULL,
    nonce     TEXT NOT NULL,
    action    TEXT NOT NULL CHECK (action IN ('register', 'validate', 'redeem', 'google_play_purchase', 'provisioning', 'sync')),
    used_at   TEXT NOT NULL,
    PRIMARY KEY (device_id, nonce)
);

INSERT INTO device_proof_nonces_v4 (device_id, nonce, action, used_at)
SELECT device_id, nonce, action, used_at FROM device_proof_nonces;

DROP TABLE device_proof_nonces;
ALTER TABLE device_proof_nonces_v4 RENAME TO device_proof_nonces;
CREATE INDEX IF NOT EXISTS device_proof_nonces_by_age ON device_proof_nonces (used_at);

-- One row per household. The revision counts pushes, so a device asks only for what changed.
CREATE TABLE IF NOT EXISTS sync_groups (
    group_id   TEXT PRIMARY KEY,
    revision   INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

-- A device belongs to at most one group.
CREATE TABLE IF NOT EXISTS sync_members (
    device_id TEXT PRIMARY KEY,
    group_id  TEXT NOT NULL,
    joined_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS sync_members_by_group ON sync_members (group_id);

-- Invite codes, hashed. One live code per group; ten minutes.
CREATE TABLE IF NOT EXISTS sync_invites (
    code_hash  TEXT PRIMARY KEY,
    group_id   TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

-- Opaque, encrypted records. The key is a client-side hash, so it names no title.
CREATE TABLE IF NOT EXISTS sync_records (
    group_id      TEXT NOT NULL,
    record_key    TEXT NOT NULL,
    ciphertext    TEXT NOT NULL,
    nonce         TEXT NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    deleted       INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
    revision      INTEGER NOT NULL,
    PRIMARY KEY (group_id, record_key)
);

CREATE INDEX IF NOT EXISTS sync_records_by_revision ON sync_records (group_id, revision);
