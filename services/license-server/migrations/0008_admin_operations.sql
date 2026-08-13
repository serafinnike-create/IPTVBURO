-- Operational support fields. They are optional and never participate in licence decisions.
ALTER TABLE devices ADD COLUMN display_name TEXT;
ALTER TABLE devices ADD COLUMN customer_name TEXT;
ALTER TABLE devices ADD COLUMN customer_email TEXT;
ALTER TABLE devices ADD COLUMN order_reference TEXT;
ALTER TABLE devices ADD COLUMN support_note TEXT;

-- Append-only administrator audit. The actor is a label supplied only after successful admin
-- authentication; Cloudflare Access can later replace it with a verified email.
CREATE TABLE IF NOT EXISTS admin_audit (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    actor      TEXT NOT NULL,
    action     TEXT NOT NULL,
    device_id  TEXT,
    detail     TEXT,
    country    TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS admin_audit_by_time ON admin_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS admin_audit_by_device ON admin_audit (device_id, created_at DESC);

-- Security observations are deliberately coarse: no source IP, coordinates or raw request body.
CREATE TABLE IF NOT EXISTS security_alerts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id       TEXT,
    kind            TEXT NOT NULL,
    severity        TEXT NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    detail          TEXT,
    observed_at     TEXT NOT NULL,
    resolved_at     TEXT,
    resolution_note TEXT
);
CREATE INDEX IF NOT EXISTS security_alerts_open
    ON security_alerts (resolved_at, severity, observed_at DESC);
CREATE INDEX IF NOT EXISTS security_alerts_device
    ON security_alerts (device_id, observed_at DESC);

-- Short-lived sessions allow a TOTP challenge once at sign-in rather than on every admin request.
-- Only SHA-256 hashes of random session tokens are retained.
CREATE TABLE IF NOT EXISTS admin_sessions (
    token_hash TEXT PRIMARY KEY,
    actor      TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    last_used_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS admin_sessions_expiry ON admin_sessions (expires_at);

-- MFA is opt-in until the administrator confirms a valid authenticator code. The secret is stored
-- AES-GCM encrypted with ADMIN_MFA_ENCRYPTION_KEY, a Worker secret outside D1 and this repository.
CREATE TABLE IF NOT EXISTS admin_mfa (
    id                 INTEGER PRIMARY KEY CHECK (id = 1),
    secret_ciphertext  TEXT NOT NULL,
    enabled_at         TEXT,
    updated_at         TEXT NOT NULL
);
