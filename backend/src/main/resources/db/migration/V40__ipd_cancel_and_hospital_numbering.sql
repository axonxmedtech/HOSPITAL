-- IPD hardening, phase A.
--
-- 1. ipd_admission.cancel_reason / cancelled_at / cancelled_by: an admission made by mistake
--    (wrong patient, duplicate) is cancelled with a reason instead of being "discharged".
-- 2. ipd_admission.ipd_number becomes unique per hospital instead of across every hospital on
--    the platform, so each hospital numbers its own admissions IPD-1, IPD-2 ... Existing numbers
--    are kept as they are (a globally unique column is already unique per hospital).
--
-- V39 is left free on purpose: it is reserved for the patient access log migration on
-- fix/data-safety-gaps, renumbered from V37 because V38 reached staging first.
--
-- flyway:safety-ack: the only DROP is of the global UNIQUE index on ipd_admission.ipd_number; it is replaced in the same migration by UNIQUE (hospital_id, ipd_number), which every existing row already satisfies. No column or row is dropped.

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'cancel_reason') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN cancel_reason VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'cancelled_at') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN cancelled_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'cancelled_by') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN cancelled_by VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Drop any unique index that covers ipd_number alone (its name differs between databases
-- created by V10, by Hibernate and by schema-full.sql) ...
SET @idx := (SELECT INDEX_NAME FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND NON_UNIQUE = 0
    AND INDEX_NAME <> 'PRIMARY'
  GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME) = 'ipd_number' LIMIT 1);
SET @sql := IF(@idx IS NULL, 'SELECT 1', CONCAT('ALTER TABLE ipd_admission DROP INDEX `', @idx, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ... and make it unique per hospital instead.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission'
     AND INDEX_NAME = 'uk_ipd_admission_hospital_ipd_number') > 0,
  'SELECT 1',
  'ALTER TABLE ipd_admission ADD UNIQUE KEY uk_ipd_admission_hospital_ipd_number (hospital_id, ipd_number)'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
