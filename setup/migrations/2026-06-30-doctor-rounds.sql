-- ============================================================
-- Migration: Doctor Rounds (Step 12)
-- Date: 2026-06-30
-- Description: Creates doctor_rounds table for progress logging
-- ============================================================

CREATE TABLE IF NOT EXISTS doctor_rounds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    round_date_time DATETIME NOT NULL,
    subjective TEXT NULL,
    objective TEXT NULL,
    assessment TEXT NULL,
    plan TEXT NULL,
    next_round_at DATETIME NULL,
    doctor_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dr_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id),
    CONSTRAINT fk_dr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

CREATE INDEX IF NOT EXISTS idx_dr_ipd_hosp ON doctor_rounds(ipd_admission_id, hospital_id);
