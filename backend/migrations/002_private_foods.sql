PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS private_foods (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    brand TEXT,
    default_amount TEXT NOT NULL,
    unit TEXT NOT NULL,
    basis TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_private_foods_user_name ON private_foods(user_id, name);

CREATE TABLE IF NOT EXISTS private_food_nutrients (
    id TEXT PRIMARY KEY,
    food_id TEXT NOT NULL REFERENCES private_foods(id) ON DELETE CASCADE,
    nutrient_key TEXT NOT NULL,
    value TEXT NOT NULL,
    unit TEXT NOT NULL,
    basis TEXT NOT NULL,
    UNIQUE(food_id, nutrient_key)
);
