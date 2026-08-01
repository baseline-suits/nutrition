PRAGMA foreign_keys = ON;

ALTER TABLE users ADD COLUMN deletion_requested_at TEXT;
ALTER TABLE meals ADD COLUMN photo_deleted_at TEXT;

CREATE TABLE IF NOT EXISTS deletion_jobs (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('meal', 'photo', 'account')),
    target_id TEXT NOT NULL,
    object_keys_json TEXT NOT NULL,
    upload_ids_json TEXT NOT NULL,
    status TEXT NOT NULL CHECK (
        status IN ('pending', 'failed_retryable', 'completed')
    ),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT,
    error_code TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    UNIQUE (kind, target_id)
);
CREATE INDEX IF NOT EXISTS idx_deletion_jobs_retry
    ON deletion_jobs(status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS account_deletion_tombstones (
    user_id TEXT PRIMARY KEY,
    requested_at TEXT NOT NULL,
    completed_at TEXT,
    retain_until TEXT NOT NULL
);

DELETE FROM favorites
WHERE original_meal_id IN (
    SELECT id FROM meals WHERE deleted_at IS NOT NULL
)
AND NOT json_valid(snapshot_json);

UPDATE favorites
SET snapshot_json = json_set(
        snapshot_json,
        '$.attachment_id', NULL,
        '$.external_reference', NULL
    ),
    updated_at = COALESCE(
        (SELECT deleted_at FROM meals WHERE id = favorites.original_meal_id),
        updated_at
    )
WHERE original_meal_id IN (
    SELECT id FROM meals WHERE deleted_at IS NOT NULL
);

DELETE FROM analysis_requests
WHERE id IN (
    SELECT substr(p.external_reference, 10)
    FROM provenance p
    JOIN meals m ON m.id = p.meal_id
    WHERE m.deleted_at IS NOT NULL
      AND p.external_reference LIKE 'analysis:%'
);

DELETE FROM analysis_requests
WHERE attachment_id IN (
    SELECT id FROM photo_uploads WHERE status = 'deleted'
);

INSERT INTO deletion_jobs (
    id,
    user_id,
    kind,
    target_id,
    object_keys_json,
    upload_ids_json,
    status,
    attempts,
    next_attempt_at,
    error_code,
    created_at,
    updated_at,
    completed_at
)
SELECT
    lower(hex(randomblob(16))),
    p.user_id,
    'photo',
    p.id,
    json_array(p.object_key),
    json_array(p.id),
    'pending',
    0,
    NULL,
    NULL,
    COALESCE(p.deleted_at, p.updated_at, p.created_at),
    COALESCE(p.deleted_at, p.updated_at, p.created_at),
    NULL
FROM photo_uploads p
WHERE p.status = 'deleted';

UPDATE meals
SET photo_deleted_at = COALESCE(photo_deleted_at, (
        SELECT MIN(COALESCE(p.deleted_at, p.updated_at, p.created_at))
        FROM attachments a
        JOIN photo_uploads p ON p.id = a.upload_id
        WHERE a.meal_id = meals.id
          AND p.status = 'deleted'
    ))
WHERE EXISTS (
    SELECT 1
    FROM attachments a
    JOIN photo_uploads p ON p.id = a.upload_id
    WHERE a.meal_id = meals.id
      AND p.status = 'deleted'
);

DELETE FROM attachments
WHERE upload_id IN (
    SELECT id FROM photo_uploads WHERE status = 'deleted'
);

DELETE FROM meal_mutations
WHERE EXISTS (
    SELECT 1 FROM meals m
    WHERE m.deleted_at IS NOT NULL
      AND m.user_id = meal_mutations.user_id
      AND meal_mutations.meal_id IN (m.id, m.client_id)
);

INSERT INTO deletion_jobs (
    id,
    user_id,
    kind,
    target_id,
    object_keys_json,
    upload_ids_json,
    status,
    attempts,
    next_attempt_at,
    error_code,
    created_at,
    updated_at,
    completed_at
)
SELECT
    lower(hex(randomblob(16))),
    m.user_id,
    'meal',
    m.id,
    COALESCE((
        SELECT json_group_array(DISTINCT p.object_key)
        FROM attachments a
        JOIN photo_uploads p ON p.id = a.upload_id
        WHERE a.meal_id = m.id
          AND NOT EXISTS (
              SELECT 1
              FROM attachments other
              JOIN meals active ON active.id = other.meal_id
              WHERE other.upload_id = p.id
                AND active.deleted_at IS NULL
          )
    ), '[]'),
    COALESCE((
        SELECT json_group_array(DISTINCT p.id)
        FROM attachments a
        JOIN photo_uploads p ON p.id = a.upload_id
        WHERE a.meal_id = m.id
          AND NOT EXISTS (
              SELECT 1
              FROM attachments other
              JOIN meals active ON active.id = other.meal_id
              WHERE other.upload_id = p.id
                AND active.deleted_at IS NULL
          )
    ), '[]'),
    'pending',
    0,
    NULL,
    NULL,
    COALESCE(m.deleted_at, m.updated_at),
    COALESCE(m.deleted_at, m.updated_at),
    NULL
FROM meals m
WHERE m.deleted_at IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM attachments a
      JOIN photo_uploads p ON p.id = a.upload_id
      WHERE a.meal_id = m.id
        AND NOT EXISTS (
            SELECT 1
            FROM attachments other
            JOIN meals active ON active.id = other.meal_id
            WHERE other.upload_id = p.id
              AND active.deleted_at IS NULL
        )
  );

UPDATE photo_uploads
SET status = 'deleted',
    deleted_at = COALESCE(deleted_at, (
        SELECT MIN(m.deleted_at)
        FROM attachments a
        JOIN meals m ON m.id = a.meal_id
        WHERE a.upload_id = photo_uploads.id
          AND m.deleted_at IS NOT NULL
    )),
    retained_at = NULL,
    updated_at = COALESCE((
        SELECT MIN(m.deleted_at)
        FROM attachments a
        JOIN meals m ON m.id = a.meal_id
        WHERE a.upload_id = photo_uploads.id
          AND m.deleted_at IS NOT NULL
    ), updated_at)
WHERE id IN (
    SELECT DISTINCT a.upload_id
    FROM attachments a
    JOIN meals m ON m.id = a.meal_id
    WHERE m.deleted_at IS NOT NULL
      AND a.upload_id IS NOT NULL
)
AND NOT EXISTS (
    SELECT 1
    FROM attachments other
    JOIN meals active ON active.id = other.meal_id
    WHERE other.upload_id = photo_uploads.id
      AND active.deleted_at IS NULL
);

DELETE FROM meals WHERE deleted_at IS NOT NULL;
