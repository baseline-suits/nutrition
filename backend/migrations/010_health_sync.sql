PRAGMA foreign_keys = ON;

CREATE TABLE health_records (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data_type TEXT NOT NULL CHECK (
        data_type IN ('steps', 'sleep', 'active_calories', 'exercise', 'weight')
    ),
    origin_package TEXT NOT NULL,
    origin_app_name TEXT,
    external_record_id TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    zone_id TEXT NOT NULL,
    start_offset_seconds INTEGER,
    end_offset_seconds INTEGER,
    local_day TEXT NOT NULL,
    value TEXT NOT NULL,
    unit TEXT NOT NULL CHECK (
        (data_type = 'steps' AND unit = 'count') OR
        (data_type = 'sleep' AND unit = 's') OR
        (data_type = 'active_calories' AND unit = 'kcal') OR
        (data_type = 'exercise' AND unit = 's') OR
        (data_type = 'weight' AND unit = 'kg')
    ),
    last_modified_time TEXT NOT NULL,
    detail_type INTEGER,
    content_hash TEXT NOT NULL,
    imported_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    tombstoned_at TEXT,
    tombstone_modified_time TEXT,
    UNIQUE (user_id, data_type, origin_package, external_record_id)
);
CREATE INDEX idx_health_records_period
    ON health_records(user_id, data_type, start_time, end_time);
CREATE INDEX idx_health_records_day
    ON health_records(user_id, data_type, local_day, origin_package);
CREATE INDEX idx_health_records_source
    ON health_records(user_id, origin_package, data_type);

CREATE TABLE health_record_segments (
    id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL REFERENCES health_records(id) ON DELETE CASCADE,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    segment_type INTEGER NOT NULL,
    UNIQUE (record_id, start_time, end_time, segment_type)
);
CREATE INDEX idx_health_segments_record
    ON health_record_segments(record_id, start_time);

CREATE TABLE health_record_sightings (
    record_id TEXT NOT NULL REFERENCES health_records(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    installation_id TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    last_seen_at TEXT NOT NULL,
    removed_at TEXT,
    PRIMARY KEY (record_id, installation_id)
);
CREATE INDEX idx_health_sightings_installation
    ON health_record_sightings(user_id, installation_id, active);

CREATE TABLE health_sync_batches (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (user_id, request_id)
);
CREATE INDEX idx_health_batches_user_created
    ON health_sync_batches(user_id, created_at);

CREATE TABLE health_sync_cursors (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    installation_id TEXT NOT NULL,
    data_type TEXT NOT NULL CHECK (
        data_type IN ('steps', 'sleep', 'active_calories', 'exercise', 'weight')
    ),
    cursor TEXT NOT NULL,
    window_end TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, installation_id, data_type)
);

CREATE TABLE health_source_preferences (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data_type TEXT NOT NULL CHECK (
        data_type IN ('steps', 'sleep', 'active_calories', 'exercise', 'weight')
    ),
    origin_package TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, data_type)
);

CREATE TABLE health_daily_aggregates (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data_type TEXT NOT NULL CHECK (
        data_type IN ('steps', 'sleep', 'active_calories', 'exercise', 'weight')
    ),
    local_day TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ready', 'conflict', 'series')),
    value TEXT,
    unit TEXT NOT NULL,
    selected_origin_package TEXT,
    updated_at TEXT NOT NULL,
    UNIQUE (user_id, data_type, local_day)
);
CREATE INDEX idx_health_aggregates_period
    ON health_daily_aggregates(user_id, local_day, data_type);

CREATE TABLE health_daily_aggregate_sources (
    aggregate_id TEXT NOT NULL REFERENCES health_daily_aggregates(id) ON DELETE CASCADE,
    origin_package TEXT NOT NULL,
    record_count INTEGER NOT NULL CHECK (record_count >= 0),
    value TEXT,
    overlap_detected INTEGER NOT NULL CHECK (overlap_detected IN (0, 1)),
    selected INTEGER NOT NULL CHECK (selected IN (0, 1)),
    PRIMARY KEY (aggregate_id, origin_package)
);
