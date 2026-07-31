PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS analysis_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    input_kind TEXT NOT NULL CHECK (input_kind IN ('text', 'photo')),
    status TEXT NOT NULL CHECK (status IN ('processing', 'completed', 'failed')),
    model_name TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    response_json TEXT,
    error_category TEXT,
    latency_ms INTEGER,
    input_chars INTEGER NOT NULL DEFAULT 0,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    estimated_cost_micros INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_analysis_user_created
    ON analysis_requests(user_id, created_at);
