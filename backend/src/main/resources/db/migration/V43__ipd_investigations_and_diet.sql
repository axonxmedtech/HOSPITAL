-- IPD hardening, phase D: investigations for inpatients, and diet orders.
--
-- 1. lab_orders gains what an inpatient investigation needs: the admission it belongs to, the
--    doctor's note, who ordered it, sample collection, the result (text, when, by whom) and
--    cancellation. The columns the OPD orders already use (patient_id, opd_id, priority) were
--    only ever created by Hibernate, so they are added here too when missing.
-- 2. diet_orders: the diet the doctor sets for an inpatient. One row per order; changing the diet
--    ends the current row (ended_at) and starts a new one, so the history stays.
-- Additive only.

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'patient_id') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN patient_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'opd_id') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN opd_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'priority') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT ''ROUTINE'''));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'ipd_admission_id') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN ipd_admission_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'notes') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN notes VARCHAR(500) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'ordered_by') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN ordered_by VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'sample_collected_at') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN sample_collected_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'sample_collected_by') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN sample_collected_by VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'result_text') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN result_text TEXT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'result_at') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN result_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'result_by') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN result_by VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'cancel_reason') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN cancel_reason VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'cancelled_at') > 0,
  'SELECT 1', 'ALTER TABLE lab_orders ADD COLUMN cancelled_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders') = 0
  OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND INDEX_NAME = 'idx_lab_orders_ipd') > 0,
  'SELECT 1', 'CREATE INDEX idx_lab_orders_ipd ON lab_orders (hospital_id, ipd_admission_id)'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS diet_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    hospital_id BIGINT NOT NULL,
    ipd_admission_id BIGINT NOT NULL,
    diet_type VARCHAR(30) NOT NULL,
    instructions VARCHAR(500) NULL,
    ordered_by_doctor_id BIGINT NULL,
    ordered_by VARCHAR(255) NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diet_orders_public_id (public_id),
    KEY idx_diet_orders_admission (hospital_id, ipd_admission_id)
);
