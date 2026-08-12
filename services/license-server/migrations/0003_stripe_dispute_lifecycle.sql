-- Track every Stripe dispute so a won case can restore access safely without overlooking another
-- open/lost dispute for the same payment. Safe to apply more than once.

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
