-- Coarse network country supplied by Cloudflare after an authenticated app request.
--
-- Store only the ISO country code. The raw IP, city, coordinates and ISP are deliberately not
-- retained. activation_country is the first country observed for the device; last_country follows
-- later use so support can spot an apparent move without turning this into location tracking.
ALTER TABLE devices ADD COLUMN activation_country TEXT;
ALTER TABLE devices ADD COLUMN last_country TEXT;
ALTER TABLE devices ADD COLUMN country_updated_at TEXT;

CREATE INDEX IF NOT EXISTS devices_by_last_country
    ON devices (last_country, last_seen_at DESC);
