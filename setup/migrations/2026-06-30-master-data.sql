-- =============================================================
-- Master Data Architecture Migration
-- Date: 2026-06-30
-- =============================================================

-- 1. Lab Test Master
CREATE TABLE IF NOT EXISTS `lab_test_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `test_code` varchar(50) DEFAULT NULL,
  `test_name` varchar(200) NOT NULL,
  `department` varchar(50) NOT NULL DEFAULT 'OTHER',
  `sample_type` varchar(50) NOT NULL DEFAULT 'BLOOD',
  `normal_range_text` varchar(500) DEFAULT NULL,
  `unit` varchar(50) DEFAULT NULL,
  `turnaround_hours` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_ltm_hospital` (`hospital_id`),
  KEY `idx_ltm_name` (`test_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Radiology Test Master
CREATE TABLE IF NOT EXISTS `radiology_test_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `test_code` varchar(50) DEFAULT NULL,
  `test_name` varchar(200) NOT NULL,
  `modality` varchar(50) NOT NULL DEFAULT 'OTHER',
  `preparation_instructions` text DEFAULT NULL,
  `estimated_duration_minutes` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_rtm_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Allergy Master
CREATE TABLE IF NOT EXISTS `allergy_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `allergy_name` varchar(200) NOT NULL,
  `category` varchar(50) NOT NULL DEFAULT 'OTHER',
  `is_custom` bit(1) NOT NULL DEFAULT 0,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_am_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Patient Allergies
CREATE TABLE IF NOT EXISTS `patient_allergies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `patient_id` bigint NOT NULL,
  `allergy_master_id` bigint NOT NULL,
  `severity` varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  `notes` text DEFAULT NULL,
  `recorded_by_user_id` bigint DEFAULT NULL,
  `recorded_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_pa_patient` (`patient_id`),
  KEY `idx_pa_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. Diagnosis Master
CREATE TABLE IF NOT EXISTS `diagnosis_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `icd_code` varchar(20) NOT NULL,
  `icd_description` varchar(500) NOT NULL,
  `category` varchar(50) NOT NULL DEFAULT 'OTHER',
  `is_custom` bit(1) NOT NULL DEFAULT 0,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_dm_hospital` (`hospital_id`),
  KEY `idx_dm_code` (`icd_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. Procedure Master
CREATE TABLE IF NOT EXISTS `procedure_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `procedure_code` varchar(50) DEFAULT NULL,
  `procedure_name` varchar(200) NOT NULL,
  `department` varchar(100) DEFAULT NULL,
  `estimated_duration_minutes` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` bit(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_pm_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7. Add FK columns to existing tables (nullable for backward compat)
-- Using stored procedure to guard against duplicate-column errors on re-run

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
  IN tbl VARCHAR(64),
  IN col VARCHAR(64),
  IN col_def VARCHAR(255)
)
BEGIN
  -- Only add if the table exists AND the column is absent
  IF EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = tbl
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = tbl
      AND COLUMN_NAME  = col
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', col_def);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('lab_orders',       'lab_test_master_id',        'bigint DEFAULT NULL');
CALL add_column_if_missing('radiology_orders',  'radiology_test_master_id',  'bigint DEFAULT NULL');
CALL add_column_if_missing('prescriptions',     'medicine_master_id',        'bigint DEFAULT NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing;
