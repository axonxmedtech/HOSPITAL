-- ============================================================
-- Migration: Lab Workflow (Step 9)
-- Date: 2026-06-30
-- Description: Adds lab_technicians table, extends lab_orders
--              with workflow fields, and creates lab_results table.
-- ============================================================

-- Lab Technicians profile table (mirrors nurses pattern)
CREATE TABLE IF NOT EXISTS lab_technicians (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    custom_id VARCHAR(10),
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lt_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

-- Extend lab_orders with full workflow fields
ALTER TABLE lab_orders
    ADD COLUMN IF NOT EXISTS patient_id BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ipd_admission_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS opd_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS ordered_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS ordered_by_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS notes TEXT NULL,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(10) NOT NULL DEFAULT 'ROUTINE',
    ADD COLUMN IF NOT EXISTS sample_collected_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS sample_collected_by_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL;

-- Make medical_record_id nullable so IPD-only orders work too
ALTER TABLE lab_orders MODIFY COLUMN medical_record_id BIGINT NULL;

-- Lab results: one result record per completed order
CREATE TABLE IF NOT EXISTS lab_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    lab_order_id BIGINT NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    parameters TEXT,
    result_summary TEXT,
    is_abnormal BOOLEAN NOT NULL DEFAULT FALSE,
    result_file_url VARCHAR(500),
    resulted_by_name VARCHAR(100) NOT NULL,
    resulted_at DATETIME NOT NULL,
    verified_by_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_lr_lab_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders(id)
) ENGINE=InnoDB;

-- Indexes for lab workflow queries
CREATE INDEX IF NOT EXISTS idx_lab_orders_hospital_id ON lab_orders(hospital_id);
CREATE INDEX IF NOT EXISTS idx_lab_orders_ipd_admission ON lab_orders(ipd_admission_id);
CREATE INDEX IF NOT EXISTS idx_lab_orders_patient ON lab_orders(patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_results_order ON lab_results(lab_order_id);
