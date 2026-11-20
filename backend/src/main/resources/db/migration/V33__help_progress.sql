-- In-app help: each user's progress (guide steps ticked, tours seen), so it follows them to any
-- computer. Preference data only; purged with the facility (TenantTables PURGE_SAFE).
--
-- Idempotent: DatabaseMigrationRunner creates the same table on non-Flyway (dev) profiles.

CREATE TABLE IF NOT EXISTS help_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NULL,
    scope_key VARCHAR(160) NOT NULL,
    data TEXT NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY UK_help_progress_scope (scope_key),
    KEY idx_help_progress_hospital (hospital_id)
);
