-- IPD discharge outcome: how the patient left (NORMAL, LAMA, REFERRED, EXPIRED, ABSCONDED)
-- and a free-text note (referral hospital, cause / time of death, etc.).
-- Every discharge used to be recorded as a normal discharge home.
--
-- Idempotent: DatabaseMigrationRunner adds the same columns on non-Flyway (dev) profiles.

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'discharge_type');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE ipd_admission ADD COLUMN discharge_type VARCHAR(20) NULL', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'discharge_note');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE ipd_admission ADD COLUMN discharge_note VARCHAR(500) NULL', 'DO 0');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

UPDATE ipd_admission SET discharge_type = 'NORMAL' WHERE status = 'DISCHARGED' AND discharge_type IS NULL;
