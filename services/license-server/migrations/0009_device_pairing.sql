-- Pairing: handing a television something that is painful to type on a remote.
--
-- The problem this solves is narrow and real. A TMDb key is 32 characters of
-- hex; entering it on a Samsung remote means driving a cursor around an on-screen
-- keyboard once per character. People give up, and the app that needs the key to
-- show artwork, cast and synopsis stays bare — which reads as the app being poor
-- rather than the key being missing.
--
-- Why a server at all, when both devices are usually on the same sofa: the Tizen
-- Web Runtime gives JavaScript no listening socket, so the television cannot be
-- spoken to directly. It can only make outbound HTTPS requests. So the TV posts a
-- code, the phone posts a payload against that code, and the TV collects it. The
-- server is a letterbox, not a party to the content.
--
-- What that forces on the design:
--
--   * The payload is a secret. A TMDb key is the user's own credential, and the
--     "open a title" case can carry what they are watching. So a row lives for
--     five minutes, is delivered exactly once, and is deleted on collection.
--
--   * The code is short because it is read off a television across a room, which
--     makes it guessable. Six digits is a million values; the brake is the
--     attempt counter below, not the length. A code that has been guessed at too
--     often is dead even if the right value arrives afterwards.
--
--   * Nothing here identifies a person. No account, no device id, no IP. A row is
--     a code, a blob and two timestamps, and the whole table is empty five
--     minutes after anybody stops using it.

CREATE TABLE IF NOT EXISTS pairing_requests (
    -- The six digits shown on the television. Primary key, so a code in use
    -- cannot be handed out twice; the Worker retries generation on collision.
    code           TEXT PRIMARY KEY,

    -- What the phone is sending: 'tmdb_key', 'critics_key' or 'open_title'. Kept
    -- as a column rather than inferred from the payload so the television can
    -- refuse a kind it did not ask for — a TV waiting for a key must not be handed
    -- a title to play.
    kind           TEXT NOT NULL,

    -- AES-GCM ciphertext, base64. The key is derived from the pairing code itself,
    -- so the Worker stores a value it cannot read: someone with database access
    -- but not the code on the screen holds nothing. Null until the phone posts.
    payload        TEXT,

    -- Random per-row, base64. Stored beside the ciphertext because it is not a
    -- secret; reusing a nonce across rows with a related key is what must not
    -- happen, and generating one per row is how that is avoided.
    payload_nonce  TEXT,

    -- Wrong-code attempts spent against this row. The brake on guessing: past the
    -- ceiling the Worker refuses the code permanently rather than for a window,
    -- because a five-minute row cannot outlast a lockout worth waiting through.
    attempts       INTEGER NOT NULL DEFAULT 0,

    created_at     TEXT NOT NULL,

    -- ISO-8601 UTC, like every other date here. Sorts correctly, and a support
    -- question about a stuck pairing is readable without conversion.
    expires_at     TEXT NOT NULL,

    -- Set when the television collects. The row is deleted immediately after, so
    -- this exists for the ordering inside that single transaction rather than as
    -- history: a payload must never be delivered to a second reader.
    claimed_at     TEXT
);

-- The sweep. Expired rows are removed on every pairing request rather than by a
-- cron, so the table stays empty without another scheduled job to keep alive.
CREATE INDEX IF NOT EXISTS pairing_requests_expiry ON pairing_requests (expires_at);
