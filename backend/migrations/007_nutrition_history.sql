PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS daily_budget_snapshots (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    local_day TEXT NOT NULL,
    timezone TEXT NOT NULL,
    target_kcal TEXT NOT NULL,
    target_protein_g TEXT NOT NULL,
    target_carbs_g TEXT NOT NULL,
    target_fat_g TEXT NOT NULL,
    targets_manual INTEGER NOT NULL DEFAULT 0,
    weight_kg TEXT,
    activity_level TEXT,
    calculation_version TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (user_id, local_day)
);
CREATE INDEX IF NOT EXISTS idx_daily_budget_user_day
    ON daily_budget_snapshots(user_id, local_day);

INSERT OR IGNORE INTO daily_budget_snapshots (
    user_id,
    local_day,
    timezone,
    target_kcal,
    target_protein_g,
    target_carbs_g,
    target_fat_g,
    targets_manual,
    weight_kg,
    activity_level,
    calculation_version,
    created_at
)
SELECT
    m.user_id,
    m.local_day,
    MIN(m.timezone),
    p.target_kcal,
    p.target_protein_g,
    p.target_carbs_g,
    p.target_fat_g,
    p.targets_manual,
    p.weight_kg,
    p.activity_level,
    COALESCE(p.formula_version, 'manual-v1'),
    MIN(m.created_at)
FROM meals m
JOIN profiles p ON p.user_id = m.user_id
WHERE p.target_kcal IS NOT NULL
GROUP BY m.user_id, m.local_day;
