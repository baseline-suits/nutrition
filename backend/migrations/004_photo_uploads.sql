PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS photo_uploads (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key TEXT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (
        status IN (
            'pending_upload',
            'uploaded',
            'ready',
            'analysis_attached',
            'deleted',
            'failed'
        )
    ),
    declared_media_type TEXT NOT NULL,
    stored_media_type TEXT,
    declared_size INTEGER NOT NULL,
    size_bytes INTEGER,
    width INTEGER,
    height INTEGER,
    expires_at TEXT NOT NULL,
    retained_at TEXT,
    deleted_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_photo_uploads_user_status
    ON photo_uploads(user_id, status, expires_at);

ALTER TABLE attachments ADD COLUMN upload_id TEXT REFERENCES photo_uploads(id);
CREATE INDEX IF NOT EXISTS idx_attachments_upload ON attachments(upload_id);

ALTER TABLE analysis_requests ADD COLUMN attachment_id TEXT REFERENCES photo_uploads(id);
