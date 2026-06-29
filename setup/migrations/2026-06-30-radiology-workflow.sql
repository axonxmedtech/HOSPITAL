-- ============================================================
-- Migration: Radiology Workflow (Step 10)
-- Date: 2026-06-30
-- Description: Adds radiology_technicians, radiology_orders,
--              and radiology_results tables/indexes.
-- ============================================================

-- Radiology Technicians profile table (mirrors nurses/lab techs pattern)
CREATE TABLE IF NOT EXISTS radiology_technicians (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    custom_id VARCHAR(10),
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

-- Radiology orders table
CREATE TABLE IF NOT EXISTS radiology_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    medical_record_id BIGINT NULL,
    patient_id BIGINT NOT NULL,
    ipd_admission_id BIGINT NULL,
    opd_id BIGINT NULL,
    test_name VARCHAR(255) NOT NULL,
    priority VARCHAR(10) NOT NULL DEFAULT 'ROUTINE',
    status VARCHAR(50) NOT NULL DEFAULT 'ORDERED',
    ordered_by BIGINT NULL,
    ordered_by_name VARCHAR(100) NULL,
    notes TEXT NULL,
    study_conducted_at DATETIME NULL,
    study_conducted_by_name VARCHAR(100) NULL,
    updated_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ro_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

-- Radiology results table (one-to-one with order via unique key)
CREATE TABLE IF NOT EXISTS radiology_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    radiology_order_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    findings TEXT NULL,
    impression TEXT NULL,
    is_abnormal BOOLEAN NOT NULL DEFAULT FALSE,
    result_file_url VARCHAR(500) NULL,
    resulted_by_name VARCHAR(100) NOT NULL,
    resulted_at DATETIME NOT NULL,
    verified_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_rr_radiology_order FOREIGN KEY (radiology_order_id) REFERENCES radiology_orders(id)
) ENGINE=InnoDB;

-- Indexes for radiology queries
CREATE INDEX IF NOT EXISTS idx_radiology_orders_hospital_id ON radiology_orders(hospital_id);
CREATE INDEX IF NOT EXISTS idx_radiology_orders_ipd_admission ON radiology_orders(ipd_admission_id);
CREATE INDEX IF NOT EXISTS idx_radiology_orders_patient ON radiology_orders(patient_id);
CREATE INDEX IF NOT EXISTS idx_radiology_results_order ON radiology_results(radiology_order_id);
