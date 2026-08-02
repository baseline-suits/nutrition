PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS favorites (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_meal_id TEXT REFERENCES meals(id) ON DELETE SET NULL,
    display_name TEXT NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    last_used_at TEXT,
    UNIQUE(user_id, original_meal_id)
);
CREATE INDEX IF NOT EXISTS idx_favorites_user_recent
    ON favorites(user_id, last_used_at DESC, updated_at DESC);
