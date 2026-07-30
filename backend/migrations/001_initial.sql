PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL COLLATE NOCASE UNIQUE,
    password_hash TEXT NOT NULL,
    locale TEXT NOT NULL DEFAULT 'de' CHECK (locale IN ('de', 'ru')),
    timezone TEXT NOT NULL DEFAULT 'UTC',
    onboarding_complete INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS access_codes (
    id TEXT PRIMARY KEY,
    code_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    consumed_at TEXT,
    revoked_at TEXT,
    consumed_by TEXT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

CREATE TABLE IF NOT EXISTS profiles (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    birth_date TEXT,
    biological_input TEXT,
    height_cm TEXT,
    weight_kg TEXT,
    weight_measured_at TEXT,
    activity_level TEXT,
    goal_direction TEXT,
    formula_version TEXT,
    calculation_json TEXT,
    target_kcal TEXT,
    target_protein_g TEXT,
    target_carbs_g TEXT,
    target_fat_g TEXT,
    targets_manual INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meals (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    local_day TEXT NOT NULL,
    eaten_at TEXT NOT NULL,
    timezone TEXT NOT NULL,
    meal_type TEXT NOT NULL,
    name TEXT NOT NULL,
    note TEXT,
    capture_method TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    deleted_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_meals_day ON meals(user_id, local_day, eaten_at);

CREATE TABLE IF NOT EXISTS ingredients (
    id TEXT PRIMARY KEY,
    meal_id TEXT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    original_name TEXT NOT NULL,
    normalized_name TEXT,
    preparation TEXT,
    amount TEXT NOT NULL,
    unit TEXT NOT NULL,
    UNIQUE(meal_id, position)
);

CREATE TABLE IF NOT EXISTS nutrient_values (
    id TEXT PRIMARY KEY,
    meal_id TEXT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    ingredient_id TEXT REFERENCES ingredients(id) ON DELETE CASCADE,
    nutrient_key TEXT NOT NULL,
    value TEXT NOT NULL,
    unit TEXT NOT NULL,
    basis TEXT NOT NULL,
    source TEXT NOT NULL,
    locked INTEGER NOT NULL DEFAULT 0,
    accuracy TEXT,
    UNIQUE(ingredient_id, nutrient_key, basis)
);
CREATE INDEX IF NOT EXISTS idx_nutrients_meal ON nutrient_values(meal_id);

CREATE TABLE IF NOT EXISTS provenance (
    id TEXT PRIMARY KEY,
    meal_id TEXT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    external_reference TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS attachments (
    id TEXT PRIMARY KEY,
    meal_id TEXT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL,
    media_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meal_revisions (
    id TEXT PRIMARY KEY,
    meal_id TEXT NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(meal_id, version)
);

