-- Recognise a machine whose stored files were deleted.
--
-- The client's installation id used to be UUID.randomUUID(). Deleting three files on disk made the
-- app introduce itself with a brand new device id, which the server correctly saw as a machine it
-- had never met — and granted a fresh seven-day trial. Repeatable indefinitely by anyone.
--
-- The client now derives that UUID from the Windows MachineGuid, so the same machine produces the
-- same value however many times its files are removed. Recording it here is what lets the server
-- find the earlier trial and carry its dates forward.
--
-- Nullable, because rows written before this change have no anchor and must keep working exactly as
-- they do now. A null anchor simply means "cannot be matched by machine", which is the old
-- behaviour rather than a failure.
ALTER TABLE devices ADD COLUMN machine_anchor TEXT;

-- Added here because the index below needs it and no earlier migration provides it.
--
-- `first_seen_at` is declared in schema.sql, so a database created from that file already has it —
-- which is why this gap was invisible: a fresh database migrated cleanly while a real, older one
-- failed on the index with "no such column: first_seen_at". That is precisely the database this
-- migration exists to serve.
--
-- Nullable, unlike the schema's NOT NULL. SQLite cannot add a NOT NULL column without a default,
-- and inventing a first-seen date for rows that predate the column would be worse than admitting
-- it is unknown: the anchor lookup below reads it to find the *earliest* trial a machine started,
-- and a fabricated date would answer that question wrongly rather than not at all.
ALTER TABLE devices ADD COLUMN first_seen_at TEXT;

-- The lookup this exists for: given an anchor, find the earliest trial that machine ever started.
-- Not unique — one machine legitimately holds several device rows over its life, for instance after
-- a key rotation or a support-issued replacement.
CREATE INDEX IF NOT EXISTS devices_machine_anchor
    ON devices (machine_anchor, first_seen_at);
