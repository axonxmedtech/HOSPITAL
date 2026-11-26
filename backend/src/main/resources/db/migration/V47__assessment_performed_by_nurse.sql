-- "Performed By Nurse" on the Initial Assessment and the Vulnerability Assessment, like every other
-- nursing record (vitals, notes, medication, sugar chart, OT forms). With Separate Nurse Login OFF
-- the in-charge enters the assessment and names the staff nurse who did it; with it ON the
-- logged-in nurse is recorded (PerformingNurseResolver). NABH expects every nursing assessment to
-- show who performed it. NULL for existing rows and for doctor / admin entries.
--
-- Additive only. Idempotent: DatabaseMigrationRunner.ensurePerformedByNurseIdColumns adds the same
-- columns on non-Flyway (dev) profiles. Must run after V46 (Flyway out-of-order is off in prod).

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initial_assessments') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initial_assessments' AND COLUMN_NAME = 'performed_by_nurse_id') > 0,
  'SELECT 1', 'ALTER TABLE initial_assessments ADD COLUMN performed_by_nurse_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vulnerability_assessments') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vulnerability_assessments' AND COLUMN_NAME = 'performed_by_nurse_id') > 0,
  'SELECT 1', 'ALTER TABLE vulnerability_assessments ADD COLUMN performed_by_nurse_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
