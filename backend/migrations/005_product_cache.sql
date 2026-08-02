PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS product_cache (
    barcode TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL,
    fetched_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_product_cache_expiry ON product_cache(expires_at);
