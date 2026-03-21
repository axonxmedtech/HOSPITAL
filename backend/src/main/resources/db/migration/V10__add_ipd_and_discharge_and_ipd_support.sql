-- Migration: Add IPD admission, discharge summary and related columns
-- Creates: ipd_admission, discharge_summary
-- Alters: medical_records, prescriptions, billing, beds

CREATE TABLE IF NOT EXISTS ipd_admission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ipd_number VARCHAR(50) UNIQUE NOT NULL,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    source_opd_id BIGINT NULL,
    admission_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    admission_datetime TIMESTAMP NOT NULL,
    discharge_datetime TIMESTAMP NULL,
    ward_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    primary_diagnosis TEXT,
    notes TEXT,
    CONSTRAINT fk_ipd_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_ipd_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT fk_ipd_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_ipd_opd FOREIGN KEY (source_opd_id) REFERENCES opd(id),
    CONSTRAINT fk_ipd_ward FOREIGN KEY (ward_id) REFERENCES wards(ward_id),
    CONSTRAINT fk_ipd_bed FOREIGN KEY (bed_id) REFERENCES beds(bed_id)
);

CREATE TABLE IF NOT EXISTS discharge_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ipd_admission_id BIGINT NOT NULL UNIQUE,
    final_diagnosis TEXT,
    treatment_given TEXT,
    discharge_notes TEXT,
    follow_up_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_discharge_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id)
);

-- Alter medical_records to support IPD linkage and visit type
ALTER TABLE medical_records
  ADD COLUMN ipd_admission_id BIGINT NULL,
  ADD COLUMN visit_type VARCHAR(10) NOT NULL DEFAULT 'OPD';

ALTER TABLE medical_records
  ADD CONSTRAINT fk_medrec_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id);

-- Alter prescriptions to support types, route, scheduling and status
ALTER TABLE prescriptions
  ADD COLUMN `type` VARCHAR(20) NOT NULL DEFAULT 'TABLET',
  ADD COLUMN route VARCHAR(20) NOT NULL DEFAULT 'ORAL',
  ADD COLUMN duration_days INT NULL,
  ADD COLUMN start_date DATE NULL;

-- Update prescriptions status default to ACTIVE (modify existing column)
ALTER TABLE prescriptions
  MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Alter billing to support IPD billing
ALTER TABLE billing
  ADD COLUMN ipd_admission_id BIGINT NULL,
  ADD COLUMN billing_type VARCHAR(10) NOT NULL DEFAULT 'OPD';

ALTER TABLE billing
  ADD CONSTRAINT fk_billing_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id);

-- Alter beds to track current active IPD admission
ALTER TABLE beds
  ADD COLUMN current_ipd_admission_id BIGINT NULL;

ALTER TABLE beds
  ADD CONSTRAINT fk_bed_ipd FOREIGN KEY (current_ipd_admission_id) REFERENCES ipd_admission(id);
