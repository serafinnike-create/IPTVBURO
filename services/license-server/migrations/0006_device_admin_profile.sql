-- Device information reported by authenticated IPTV BURO clients for support and administration.
--
-- These fields deliberately contain no hostname, serial number, MAC address, Android ID, account
-- name or other cross-app identifier. Manufacturer/model/OS/app version are enough to distinguish
-- "the Samsung TV" from "the Windows notebook" without turning the licence database into a
-- hardware fingerprint database.
ALTER TABLE devices ADD COLUMN device_type TEXT;
ALTER TABLE devices ADD COLUMN platform TEXT;
ALTER TABLE devices ADD COLUMN manufacturer TEXT;
ALTER TABLE devices ADD COLUMN model TEXT;
ALTER TABLE devices ADD COLUMN os_version TEXT;
ALTER TABLE devices ADD COLUMN app_version TEXT;
ALTER TABLE devices ADD COLUMN last_seen_at TEXT;

-- "Delete" in the admin panel is an archive, never a physical deletion. Removing the row would
-- erase payment/support history and let the same installation register for a new trial.
ALTER TABLE devices ADD COLUMN archived_at TEXT;
ALTER TABLE devices ADD COLUMN archived_note TEXT;

CREATE INDEX IF NOT EXISTS devices_by_last_seen
    ON devices (last_seen_at DESC);
CREATE INDEX IF NOT EXISTS devices_by_archive
    ON devices (archived_at);
