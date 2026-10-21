-- Reconcile missing tables and enforce collision-safe custom ID uniqueness for patients and billing.
--
-- 1. Enforce uniqueness on (hospital_id, custom_id) for patients and billing.
-- 2. Idempotently create the 14 JPA tables previously deferred to DatabaseMigrationRunner.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Custom ID Uniqueness Constraints
-- ─────────────────────────────────────────────────────────────────────────────

SET @exist_pat_uk := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'patients'
      AND INDEX_NAME = 'uk_patient_hospital_custom_id');

SET @sql_pat_uk := IF(@exist_pat_uk = 0,
    'ALTER TABLE patients ADD UNIQUE KEY uk_patient_hospital_custom_id (hospital_id, custom_id)',
    'DO 0');
PREPARE stmt_pat_uk FROM @sql_pat_uk;
EXECUTE stmt_pat_uk;
DEALLOCATE PREPARE stmt_pat_uk;

SET @exist_bill_uk := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'billing'
      AND INDEX_NAME = 'uk_billing_hospital_custom_id');

SET @sql_bill_uk := IF(@exist_bill_uk = 0,
    'ALTER TABLE billing ADD UNIQUE KEY uk_billing_hospital_custom_id (hospital_id, custom_id)',
    'DO 0');
PREPARE stmt_bill_uk FROM @sql_bill_uk;
EXECUTE stmt_bill_uk;
DEALLOCATE PREPARE stmt_bill_uk;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Consultation Statements
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS consultation_statements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  hospital_type VARCHAR(20) NOT NULL DEFAULT 'ALL',
  category VARCHAR(30) NOT NULL,
  english_text VARCHAR(500) NOT NULL,
  marathi_text VARCHAR(500) NOT NULL,
  hindi_text VARCHAR(500) NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_stmt_type_cat (hospital_type, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Surgery Pre-Op Safety Tables
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS surgery_anaesthesia_clearances (
  id BIGINT NOT NULL AUTO_INCREMENT,
  surgery_id BIGINT NOT NULL,
  hospital_id BIGINT NOT NULL,
  outcome VARCHAR(40) NOT NULL,
  conditions_comments TEXT NULL,
  recorded_by_user_id BIGINT NOT NULL,
  recorded_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sac_hospital_surgery_time (hospital_id, surgery_id, recorded_at),
  KEY idx_sac_surgery (surgery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS surgery_emergency_overrides (
  id BIGINT NOT NULL AUTO_INCREMENT,
  surgery_id BIGINT NOT NULL,
  hospital_id BIGINT NOT NULL,
  reason TEXT NOT NULL,
  bypassed_gates VARCHAR(100) NOT NULL,
  recorded_by_user_id BIGINT NOT NULL,
  recorded_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_seo_hospital_surgery_time (hospital_id, surgery_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. ICU Module Tables
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS icu_wards (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ward_id BIGINT NOT NULL,
  ward_name VARCHAR(100) NOT NULL,
  unit_type VARCHAR(20) NOT NULL DEFAULT 'ICU',
  bed_price DECIMAL(10,2) NOT NULL,
  total_beds INT NOT NULL,
  floor_number INT NULL,
  incharge_nurse_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_ward_public_id (public_id),
  UNIQUE KEY uk_icu_ward_ward_id (ward_id),
  UNIQUE KEY uk_icu_ward_hospital_name (hospital_id, ward_name),
  KEY idx_icu_wards_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_stay (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ipd_admission_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  ward_id BIGINT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  source VARCHAR(20) NOT NULL,
  source_ref_id BIGINT DEFAULT NULL,
  admitted_at DATETIME(6) NOT NULL,
  admission_reason VARCHAR(255) DEFAULT NULL,
  intensivist_doctor_id BIGINT DEFAULT NULL,
  admitted_by_user_id BIGINT DEFAULT NULL,
  disposition VARCHAR(20) DEFAULT NULL,
  discharged_at DATETIME(6) DEFAULT NULL,
  discharged_by_user_id BIGINT DEFAULT NULL,
  active_marker BIGINT DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_stay_public_id (public_id),
  UNIQUE KEY uk_icu_stay_active (hospital_id, active_marker),
  KEY idx_icu_stay_admission (ipd_admission_id),
  KEY idx_icu_stay_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_cleaning_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  ward_id BIGINT NULL,
  bed_id BIGINT NULL,
  ward_name VARCHAR(255) NULL,
  bed_number VARCHAR(100) NULL,
  task_description VARCHAR(500) NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  assigned_to VARCHAR(255) NULL,
  created_by VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_icu_cleaning_hosp_status (hospital_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS icu_alert_threshold (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  source VARCHAR(20) NOT NULL,
  metric_key VARCHAR(60) NOT NULL,
  min_value DECIMAL(12,3) NULL,
  max_value DECIMAL(12,3) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_by_user_id BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_alert_public_id (public_id),
  UNIQUE KEY uk_icu_alert_metric (hospital_id, source, metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_score_type_setting (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  score_type VARCHAR(20) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_score_type_public_id (public_id),
  UNIQUE KEY uk_icu_score_type (hospital_id, score_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_severity_score (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ipd_admission_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  icu_stay_id BIGINT NULL,
  score_type VARCHAR(20) NOT NULL,
  components_json TEXT NULL,
  total_score INT NULL,
  scored_at DATETIME(6) NOT NULL,
  recorded_by_user_id BIGINT NULL,
  performed_by_nurse_id BIGINT NULL,
  supersedes_score_id BIGINT NULL,
  note VARCHAR(255) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_score_public_id (public_id),
  KEY idx_icu_score_admission (ipd_admission_id, score_type, scored_at),
  KEY idx_icu_score_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_ventilator_parameter (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  param_key VARCHAR(60) NOT NULL,
  display_name VARCHAR(60) NOT NULL,
  unit VARCHAR(20) NULL,
  category VARCHAR(20) NOT NULL DEFAULT 'SETTING',
  value_type VARCHAR(20) NOT NULL DEFAULT 'NUMBER',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  is_custom TINYINT(1) NOT NULL DEFAULT 0,
  sort_order INT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_vent_param_public_id (public_id),
  UNIQUE KEY uk_icu_vent_param_key (hospital_id, param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_ventilator_setting (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ipd_admission_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  icu_stay_id BIGINT NULL,
  ventilation_status VARCHAR(20) NOT NULL,
  values_json TEXT NULL,
  observed_at DATETIME(6) NOT NULL,
  recorded_by_user_id BIGINT NULL,
  performed_by_nurse_id BIGINT NULL,
  supersedes_setting_id BIGINT NULL,
  note VARCHAR(255) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_vent_public_id (public_id),
  KEY idx_icu_vent_admission (ipd_admission_id, observed_at),
  KEY idx_icu_vent_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_infusion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ipd_admission_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  prescription_id BIGINT NULL,
  medicine_name VARCHAR(255) NOT NULL,
  started_at DATETIME(6) NOT NULL,
  stopped_at DATETIME(6) NULL,
  stop_reason VARCHAR(255) NULL,
  started_by_user_id BIGINT NULL,
  performed_by_nurse_id BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_inf_public_id (public_id),
  KEY idx_icu_inf_admission (ipd_admission_id),
  KEY idx_icu_inf_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_infusion_rate (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  icu_infusion_id BIGINT NOT NULL,
  rate_ml_hr DECIMAL(8,2) NOT NULL,
  effective_from DATETIME(6) NOT NULL,
  reason VARCHAR(255) NULL,
  recorded_by_user_id BIGINT NULL,
  performed_by_nurse_id BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_inf_rate_public_id (public_id),
  KEY idx_icu_inf_rate_span (icu_infusion_id, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS icu_io_entry (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(255) NOT NULL,
  hospital_id BIGINT NOT NULL,
  ipd_admission_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  direction VARCHAR(6) NOT NULL,
  route VARCHAR(30) NOT NULL,
  volume_ml INT NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  notes VARCHAR(255) NULL,
  recorded_by_user_id BIGINT NULL,
  performed_by_nurse_id BIGINT NULL,
  supersedes_io_entry_id BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icu_io_public_id (public_id),
  KEY idx_icu_io_admission (ipd_admission_id),
  KEY idx_icu_io_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
