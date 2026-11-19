-- OPD visits can be cancelled before the doctor sees the patient (left without being seen,
-- registered twice, wrong doctor). Until now a visit could only leave the queue through a
-- consultation, so a patient who walked out stayed "In Queue" for ever.
--
-- Idempotent: DatabaseMigrationRunner applies the same changes on non-Flyway (dev) profiles.

SET @t := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opd' AND COLUMN_NAME = 'status');
SET @s := IF(@t LIKE 'enum(%' AND @t NOT LIKE '%CANCELLED%',
    'ALTER TABLE opd MODIFY COLUMN status ENUM(''QUEUED'',''CONSULTED'',''COMPLETED'',''IN_IPD'',''CANCELLED'') NOT NULL',
    'DO 0');
PREPARE p FROM @s;
EXECUTE p;
DEALLOCATE PREPARE p;

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opd' AND COLUMN_NAME = 'cancel_reason');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE opd ADD COLUMN cancel_reason VARCHAR(255) NULL', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opd' AND COLUMN_NAME = 'cancelled_by');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE opd ADD COLUMN cancelled_by VARCHAR(150) NULL', 'DO 0');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opd' AND COLUMN_NAME = 'cancelled_at');
SET @s3 := IF(@c3 = 0, 'ALTER TABLE opd ADD COLUMN cancelled_at DATETIME NULL', 'DO 0');
PREPARE p3 FROM @s3;
EXECUTE p3;
DEALLOCATE PREPARE p3;
