-- In-app help feedback: answers rated helpful or not, and questions the assistant could not
-- answer (scrubbed of numbers and emails, no name kept). Purged with the facility.
--
-- Idempotent: DatabaseMigrationRunner creates the same table on non-Flyway (dev) profiles.

CREATE TABLE IF NOT EXISTS help_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NULL,
    scope_key VARCHAR(160) NOT NULL,
    role VARCHAR(40) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    entry_id VARCHAR(60) NULL,
    question VARCHAR(200) NULL,
    normalized VARCHAR(200) NULL,
    screen VARCHAR(60) NULL,
    resolved BIT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_help_feedback_scope (scope_key, created_at),
    KEY idx_help_feedback_kind (kind, resolved, created_at),
    KEY idx_help_feedback_hospital (hospital_id)
);
