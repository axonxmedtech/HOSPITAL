-- Plan capacity limits (beds, ICU beds, doctors, staff logins, OT theatres), per-hospital
-- overrides of them, and demo accounts with an end date.
--
-- Idempotent: DatabaseMigrationRunner applies the same changes on non-Flyway (dev) profiles.

CREATE TABLE IF NOT EXISTS plan_limits (
    plan_id   BIGINT      NOT NULL,
    limit_key VARCHAR(40) NOT NULL,
    max_value INT         NULL,
    PRIMARY KEY (plan_id, limit_key),
    CONSTRAINT fk_plan_limits_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS hospital_limit_overrides (
    hospital_id BIGINT      NOT NULL,
    limit_key   VARCHAR(40) NOT NULL,
    max_value   INT         NULL,
    PRIMARY KEY (hospital_id, limit_key),
    CONSTRAINT fk_hospital_limit_overrides_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id) ON DELETE CASCADE
);

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'is_demo');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE hospitals ADD COLUMN is_demo BIT(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'demo_started_at');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE hospitals ADD COLUMN demo_started_at DATETIME(6) NULL', 'DO 0');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'demo_ends_at');
SET @s3 := IF(@c3 = 0, 'ALTER TABLE hospitals ADD COLUMN demo_ends_at DATETIME(6) NULL', 'DO 0');
PREPARE p3 FROM @s3;
EXECUTE p3;
DEALLOCATE PREPARE p3;
