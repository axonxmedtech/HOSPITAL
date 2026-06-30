-- ============================================================
-- CDSS Migration — 2026-06-30
-- ============================================================

-- 1. Add respiratory_rate to vital_signs (nullable, backward-compatible)
DROP PROCEDURE IF EXISTS add_respiratory_rate;
DELIMITER $$
CREATE PROCEDURE add_respiratory_rate()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'vital_signs'
      AND COLUMN_NAME  = 'respiratory_rate'
  ) THEN
    ALTER TABLE vital_signs ADD COLUMN `respiratory_rate` int DEFAULT NULL AFTER `spo2`;
  END IF;
END$$
DELIMITER ;
CALL add_respiratory_rate();
DROP PROCEDURE IF EXISTS add_respiratory_rate;

-- 2. Drug Interaction Master
CREATE TABLE IF NOT EXISTS `drug_interaction_master` (
  `id`                       bigint NOT NULL AUTO_INCREMENT,
  `hospital_id`              bigint NOT NULL,
  `drug_a_name`              varchar(200) NOT NULL,
  `drug_b_name`              varchar(200) NOT NULL,
  `severity`                 varchar(20)  NOT NULL DEFAULT 'MEDIUM',
  `interaction_description`  text         NOT NULL,
  `is_active`                bit(1)       NOT NULL DEFAULT 1,
  `created_at`               datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_dim_hospital` (`hospital_id`),
  KEY `idx_dim_drug_a`   (`drug_a_name`),
  KEY `idx_dim_drug_b`   (`drug_b_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. CDSS Alert Log
CREATE TABLE IF NOT EXISTS `cdss_alert_log` (
  `id`                       bigint NOT NULL AUTO_INCREMENT,
  `hospital_id`              bigint NOT NULL,
  `alert_type`               varchar(50)  NOT NULL,
  `patient_id`               bigint DEFAULT NULL,
  `ipd_admission_id`         bigint DEFAULT NULL,
  `alert_message`            text         NOT NULL,
  `severity`                 varchar(20)  NOT NULL,
  `acknowledged_by_user_id`  bigint DEFAULT NULL,
  `acknowledged_at`          datetime(6)  DEFAULT NULL,
  `override_reason`          varchar(500) DEFAULT NULL,
  `created_at`               datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_cal_hospital`     (`hospital_id`),
  KEY `idx_cal_patient`      (`patient_id`),
  KEY `idx_cal_ipd`          (`ipd_admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
