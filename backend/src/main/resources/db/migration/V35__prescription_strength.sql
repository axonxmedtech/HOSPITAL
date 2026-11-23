-- A prescribed medicine's strength ("500 mg", "250 mg/5 ml", "1 %") in its own column, separate
-- from the dose taken each time ("1/2 tablet"). Old rows keep it empty; they still print as before.
-- Also fills the duration text of IPD orders that stored only the number of days.
--
-- Idempotent: DatabaseMigrationRunner applies the same changes on non-Flyway (dev) profiles.

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescriptions' AND COLUMN_NAME = 'strength');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE prescriptions ADD COLUMN strength VARCHAR(50) NULL', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

UPDATE prescriptions
   SET duration = CONCAT(duration_days, CASE WHEN duration_days = 1 THEN ' Day' ELSE ' Days' END)
 WHERE (duration IS NULL OR duration = '') AND duration_days IS NOT NULL AND duration_days > 0;
