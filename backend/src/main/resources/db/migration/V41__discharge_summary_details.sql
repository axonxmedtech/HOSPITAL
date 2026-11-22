-- IPD hardening, phase B: a discharge summary that can be printed and handed to the patient.
--
-- discharge_summary gains the sections a discharge summary carries (NABH / MCI practice):
-- condition at discharge, procedures done, key investigations, advice, the medicines to take
-- home (a JSON list), the follow-up time and the appointment booked for it, and who prepared
-- the summary and when it was last changed. Additive only; existing rows keep their text.

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'condition_at_discharge') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN condition_at_discharge VARCHAR(30) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'procedures_done') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN procedures_done TEXT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'investigations') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN investigations TEXT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'advice') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN advice TEXT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'discharge_medications') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN discharge_medications TEXT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'follow_up_time') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN follow_up_time TIME NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'follow_up_appointment_id') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN follow_up_appointment_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'prepared_by_doctor_id') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN prepared_by_doctor_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'updated_at') > 0,
  'SELECT 1', 'ALTER TABLE discharge_summary ADD COLUMN updated_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
