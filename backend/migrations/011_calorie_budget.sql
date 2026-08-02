PRAGMA foreign_keys = ON;

ALTER TABLE profiles ADD COLUMN calorie_budget_mode TEXT NOT NULL DEFAULT 'fixed'
    CHECK (calorie_budget_mode IN ('fixed', 'dynamic'));
ALTER TABLE profiles ADD COLUMN budget_mode_effective_day TEXT;

ALTER TABLE daily_budget_snapshots ADD COLUMN budget_mode TEXT NOT NULL DEFAULT 'fixed'
    CHECK (budget_mode IN ('fixed', 'dynamic'));
ALTER TABLE daily_budget_snapshots ADD COLUMN base_target_kcal TEXT NOT NULL DEFAULT '0';
ALTER TABLE daily_budget_snapshots ADD COLUMN activity_status TEXT NOT NULL DEFAULT 'not_synced'
    CHECK (activity_status IN ('not_synced', 'missing', 'partial', 'ready', 'conflict'));
ALTER TABLE daily_budget_snapshots ADD COLUMN activity_kcal TEXT;
ALTER TABLE daily_budget_snapshots ADD COLUMN activity_factor TEXT NOT NULL DEFAULT '0.5';
ALTER TABLE daily_budget_snapshots ADD COLUMN activity_cap_kcal TEXT NOT NULL DEFAULT '500';
ALTER TABLE daily_budget_snapshots ADD COLUMN activity_contribution_kcal TEXT NOT NULL DEFAULT '0';
ALTER TABLE daily_budget_snapshots ADD COLUMN budget_calculation_version TEXT NOT NULL
    DEFAULT 'fixed-budget-v1';
ALTER TABLE daily_budget_snapshots ADD COLUMN budget_updated_at TEXT;

UPDATE daily_budget_snapshots
SET base_target_kcal = target_kcal,
    budget_updated_at = created_at;

CREATE TABLE health_sync_partial_days (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    installation_id TEXT NOT NULL,
    data_type TEXT NOT NULL CHECK (
        data_type IN ('steps', 'sleep', 'active_calories', 'exercise', 'weight')
    ),
    local_day TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, installation_id, data_type, local_day)
);
CREATE INDEX idx_health_partial_days_lookup
    ON health_sync_partial_days(user_id, data_type, local_day);
