-- IPD hardening, phase C: advance deposits, the bed-charge rule, visit and nursing charges, packages.
--
-- 1. ipd_billing_settings: one row per hospital (none = defaults): the minimum advance for a
--    planned admission, the bed-charge rule (CALENDAR_DAY or HOURS_24 with grace hours) and the
--    doctor-visit fee.
-- 2. wards.nursing_charge: a per-day nursing charge per ward (ICU can cost more than general).
-- 3. ipd_packages: fixed-price packages that cover bed, nursing and doctor visits for N days.
-- 4. ipd_admission: the bed-charge rule and grace hours in force when the patient was admitted
--    (so changing the setting never re-prices a patient already in a bed), and the package.
-- Advance deposits need no table: they are billing_payments rows of kind ADVANCE.
-- Additive only.

CREATE TABLE IF NOT EXISTS ipd_billing_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    min_advance DECIMAL(10,2) NULL,
    bed_charge_rule VARCHAR(20) NOT NULL DEFAULT 'CALENDAR_DAY',
    grace_hours INT NOT NULL DEFAULT 0,
    doctor_visit_fee DECIMAL(10,2) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ipd_billing_settings_hospital (hospital_id)
);

CREATE TABLE IF NOT EXISTS ipd_packages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    included_days INT NOT NULL,
    description VARCHAR(500) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ipd_packages_hospital_name (hospital_id, name),
    KEY idx_ipd_packages_hospital (hospital_id)
);

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'wards' AND COLUMN_NAME = 'nursing_charge') > 0,
  'SELECT 1', 'ALTER TABLE wards ADD COLUMN nursing_charge DECIMAL(10,2) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'bed_charge_rule') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN bed_charge_rule VARCHAR(20) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'grace_hours') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN grace_hours INT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'package_id') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN package_id BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'package_applied_at') > 0,
  'SELECT 1', 'ALTER TABLE ipd_admission ADD COLUMN package_applied_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
