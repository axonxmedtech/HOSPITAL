-- ============================================================
-- Migration: Operation Theatre (OT) Workflow (Step 13)
-- Date: 2026-06-30
-- Description: Creates ot_bookings and ot_checklists tables
-- ============================================================

CREATE TABLE IF NOT EXISTS ot_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    procedure_name VARCHAR(200) NOT NULL,
    scheduled_date_time DATETIME NOT NULL,
    surgeon_id BIGINT NOT NULL,
    anesthetist_name VARCHAR(100) NULL,
    ot_room_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otb_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id),
    CONSTRAINT fk_otb_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ot_checklists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    sign_in_completed BOOLEAN NOT NULL DEFAULT FALSE,
    sign_in_by VARCHAR(100) NULL,
    sign_in_at DATETIME NULL,
    sign_in_notes TEXT NULL,
    time_out_completed BOOLEAN NOT NULL DEFAULT FALSE,
    time_out_by VARCHAR(100) NULL,
    time_out_at DATETIME NULL,
    time_out_notes TEXT NULL,
    sign_out_completed BOOLEAN NOT NULL DEFAULT FALSE,
    sign_out_by VARCHAR(100) NULL,
    sign_out_at DATETIME NULL,
    sign_out_notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otc_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_otc_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

CREATE INDEX IF NOT EXISTS idx_ot_bookings_admission ON ot_bookings(ipd_admission_id);
