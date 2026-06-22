-- Additive Operation Theatre (OT) module schema.
-- Safe to run on existing HMS databases. No existing table or column is removed.

USE hospital_management;

CREATE TABLE IF NOT EXISTS ot_room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  room_code VARCHAR(50),
  table_count INT DEFAULT 1,
  status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
  notes VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_ot_room_hospital (hospital_id),
  CONSTRAINT fk_ot_room_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS surgery (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  specialty VARCHAR(100),
  procedure_code VARCHAR(50),
  default_duration_minutes INT DEFAULT 60,
  default_charge DECIMAL(10,2) DEFAULT 0.00,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_surgery_hospital (hospital_id),
  CONSTRAINT fk_surgery_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS ot_booking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  patient_uhid VARCHAR(80),
  ipd_admission_id BIGINT,
  ipd_number VARCHAR(80),
  surgeon_id BIGINT,
  assistant_surgeon_id BIGINT,
  ot_room_id BIGINT,
  ot_table VARCHAR(50),
  specialty VARCHAR(100),
  procedure_name VARCHAR(160) NOT NULL,
  diagnosis VARCHAR(1000),
  expected_duration_minutes INT DEFAULT 60,
  priority VARCHAR(30) DEFAULT 'ELECTIVE',
  surgery_type VARCHAR(30) DEFAULT 'ELECTIVE',
  status VARCHAR(30) DEFAULT 'WAITING',
  clearance_status VARCHAR(30) DEFAULT 'PENDING_CLEARANCE',
  scheduled_start DATETIME NOT NULL,
  scheduled_end DATETIME NOT NULL,
  remarks VARCHAR(1000),
  intra_op_notes VARCHAR(4000),
  post_op_orders VARCHAR(4000),
  billing_id BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_ot_booking_hospital_time (hospital_id, scheduled_start, scheduled_end),
  INDEX idx_ot_booking_room_time (hospital_id, ot_room_id, scheduled_start, scheduled_end),
  CONSTRAINT fk_ot_booking_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
  CONSTRAINT fk_ot_booking_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
  CONSTRAINT fk_ot_booking_room FOREIGN KEY (ot_room_id) REFERENCES ot_room(id),
  CONSTRAINT fk_ot_booking_billing FOREIGN KEY (billing_id) REFERENCES billing(id)
);

CREATE TABLE IF NOT EXISTS pre_op_checklist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL UNIQUE,
  consent_signed BOOLEAN DEFAULT FALSE,
  blood_available BOOLEAN DEFAULT FALSE,
  cbc BOOLEAN DEFAULT FALSE,
  lft BOOLEAN DEFAULT FALSE,
  kft BOOLEAN DEFAULT FALSE,
  pt_inr BOOLEAN DEFAULT FALSE,
  ecg BOOLEAN DEFAULT FALSE,
  chest_xray BOOLEAN DEFAULT FALSE,
  cross_matching BOOLEAN DEFAULT FALSE,
  physician_fitness BOOLEAN DEFAULT FALSE,
  pac_clearance BOOLEAN DEFAULT FALSE,
  status VARCHAR(30) DEFAULT 'PENDING_CLEARANCE',
  notes VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_preop_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS ot_staff_assignment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  staff_user_id BIGINT,
  doctor_id BIGINT,
  staff_name VARCHAR(120) NOT NULL,
  role VARCHAR(60) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_ot_staff_booking (hospital_id, ot_booking_id),
  CONSTRAINT fk_ot_staff_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS equipment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  name VARCHAR(140) NOT NULL,
  category VARCHAR(80),
  serial_number VARCHAR(80),
  status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
  notes VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_equipment_hospital (hospital_id),
  CONSTRAINT fk_equipment_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS equipment_assignment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  equipment_id BIGINT NOT NULL,
  status VARCHAR(30) DEFAULT 'ASSIGNED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_equipment_assignment_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id),
  CONSTRAINT fk_equipment_assignment_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id)
);

CREATE TABLE IF NOT EXISTS instrument_set (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  name VARCHAR(140) NOT NULL,
  set_type VARCHAR(60),
  status VARCHAR(30) NOT NULL DEFAULT 'STERILIZED',
  contents VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_instrument_set_hospital (hospital_id),
  CONSTRAINT fk_instrument_set_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS instrument_assignment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  instrument_set_id BIGINT NOT NULL,
  status VARCHAR(30) DEFAULT 'ASSIGNED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_instrument_assignment_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id),
  CONSTRAINT fk_instrument_assignment_set FOREIGN KEY (instrument_set_id) REFERENCES instrument_set(id)
);

CREATE TABLE IF NOT EXISTS who_checklist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL UNIQUE,
  patient_identity BOOLEAN DEFAULT FALSE,
  site_marked BOOLEAN DEFAULT FALSE,
  consent_signed BOOLEAN DEFAULT FALSE,
  allergies_checked BOOLEAN DEFAULT FALSE,
  blood_available BOOLEAN DEFAULT FALSE,
  team_introduction BOOLEAN DEFAULT FALSE,
  antibiotic_given BOOLEAN DEFAULT FALSE,
  imaging_displayed BOOLEAN DEFAULT FALSE,
  instrument_count BOOLEAN DEFAULT FALSE,
  sponge_count BOOLEAN DEFAULT FALSE,
  final_count BOOLEAN DEFAULT FALSE,
  specimen_labeled BOOLEAN DEFAULT FALSE,
  procedure_confirmed BOOLEAN DEFAULT FALSE,
  recovery_plan BOOLEAN DEFAULT FALSE,
  status VARCHAR(30) DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_who_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS surgery_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  status VARCHAR(60) NOT NULL,
  event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  INDEX idx_surgery_status_booking (hospital_id, ot_booking_id, event_time),
  CONSTRAINT fk_surgery_status_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS anesthesia_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  anesthesia_type VARCHAR(40) NOT NULL,
  drug_chart VARCHAR(4000),
  bp VARCHAR(40),
  pulse INT,
  spo2 INT,
  temperature DOUBLE,
  respiration INT,
  complications VARCHAR(2000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_anesthesia_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS implant_usage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  implant_name VARCHAR(140) NOT NULL,
  brand VARCHAR(100),
  lot_number VARCHAR(100),
  batch_number VARCHAR(100),
  serial_number VARCHAR(100),
  manufacturer VARCHAR(140),
  expiry_date DATE,
  charge DECIMAL(10,2) DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_implant_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS recovery_room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  bp VARCHAR(40),
  pulse INT,
  spo2 INT,
  pain_score INT,
  consciousness VARCHAR(80),
  nausea BOOLEAN DEFAULT FALSE,
  drain_output VARCHAR(80),
  urine_output VARCHAR(80),
  disposition VARCHAR(40),
  notes VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_recovery_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS ot_consumable_usage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  inventory_item_id BIGINT,
  item_name VARCHAR(140) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  unit_charge DECIMAL(10,2) DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_ot_consumable_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id)
);

CREATE TABLE IF NOT EXISTS ot_billing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hospital_id BIGINT NOT NULL,
  ot_booking_id BIGINT NOT NULL,
  billing_id BIGINT,
  charge_type VARCHAR(120) NOT NULL,
  amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  notes VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(100),
  CONSTRAINT fk_ot_billing_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_booking(id),
  CONSTRAINT fk_ot_billing_billing FOREIGN KEY (billing_id) REFERENCES billing(id)
);

-- Optional local dev login seed. Password: admin123
INSERT INTO users (email, password, name, role, hospital_id, is_active, public_id, created_at)
SELECT 'otadmin@hms.com', '$2a$10$0qQ.izyRhAq.JgTRSNsF4.XPhqD71NdGxlMJa22T/V2CMU.BXh/Gu', 'OT Admin', 'OT_ADMIN', h.id, TRUE, UUID(), NOW()
FROM hospitals h
WHERE h.id = 1
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.email = 'otadmin@hms.com');
