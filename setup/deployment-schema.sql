-- MySQL Database Schema for Hospital Management System
-- Deployment ready for Railway / MySQL

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for hospitals
-- ----------------------------
DROP TABLE IF EXISTS `hospitals`;
CREATE TABLE `hospitals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(500) DEFAULT NULL,
  `case_paper_fee` decimal(10,2) DEFAULT NULL,
  `consultation_fee` decimal(10,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(200) NOT NULL,
  `opd_timings` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `plan` varchar(20) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_dre74r9dx5tme2mvsmu33ur77` (`public_id`),
  UNIQUE KEY `UK_snisvhrc8x0rcmph9cwiuqxvt` (`custom_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for hospital_modules
-- ----------------------------
DROP TABLE IF EXISTS `hospital_modules`;
CREATE TABLE `hospital_modules` (
  `hospital_id` bigint NOT NULL,
  `module_name` varchar(255) DEFAULT NULL,
  KEY `FKcn73q131q80psfovorxk09c13` (`hospital_id`),
  CONSTRAINT `FKcn73q131q80psfovorxk09c13` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  `hospital_id` bigint DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_s24bux761rbgowsl7a4b386ba` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for doctors
-- ----------------------------
DROP TABLE IF EXISTS `doctors`;
CREATE TABLE `doctors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `email` varchar(100) NOT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `phone` varchar(15) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `specialization` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_lqd2m2kx16cc3locfwtq02bek` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for patients
-- ----------------------------
DROP TABLE IF EXISTS `patients`;
CREATE TABLE `patients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `age` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `gender` varchar(10) NOT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `medical_history` varchar(1000) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `phone` varchar(15) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `status` enum('REGISTERED','CONSULTING','COMPLETED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8isyrjl9ji56k5uv4cgp9p2q6` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for opd
-- ----------------------------
DROP TABLE IF EXISTS `opd`;
CREATE TABLE `opd` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bp` varchar(255) DEFAULT NULL,
  `case_id` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `problem` text,
  `pulse` int DEFAULT NULL,
  `spo2` int DEFAULT NULL,
  `status` enum('QUEUED','CONSULTED','COMPLETED','IN_IPD') NOT NULL,
  `temperature` double DEFAULT NULL,
  `token_number` int DEFAULT NULL,
  `visit_type` enum('NEW','FOLLOWUP') DEFAULT NULL,
  `weight` double DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL,
  `receptionist_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_1hooemtjkcvad3j015rxyddlm` (`case_id`),
  KEY `FKkrh8ux3vbkckqx3tslhqa4wok` (`doctor_id`),
  KEY `FKjmn16oyjgbruv844ufcyolbjd` (`patient_id`),
  KEY `FKjlnk1jk30lio8g8wckvbnjrf9` (`receptionist_id`),
  CONSTRAINT `FKjlnk1jk30lio8g8wckvbnjrf9` FOREIGN KEY (`receptionist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKjmn16oyjgbruv844ufcyolbjd` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`),
  CONSTRAINT `FKkrh8ux3vbkckqx3tslhqa4wok` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for queue_entry
-- ----------------------------
DROP TABLE IF EXISTS `queue_entry`;
CREATE TABLE `queue_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `token_number` int DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  `opd_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhym5nwrldoiu0p5bkexif1pbd` (`doctor_id`),
  KEY `FK46n948328s187drytewghqf70` (`opd_id`),
  CONSTRAINT `FK46n948328s187drytewghqf70` FOREIGN KEY (`opd_id`) REFERENCES `opd` (`id`),
  CONSTRAINT `FKhym5nwrldoiu0p5bkexif1pbd` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for appointments
-- ----------------------------
DROP TABLE IF EXISTS `appointments`;
CREATE TABLE `appointments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `appointment_date` date NOT NULL,
  `appointment_time` time(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `doctor_id` bigint NOT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `notes` varchar(500) DEFAULT NULL,
  `patient_id` bigint NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_by8ybe23gqrq9m7r9knbgsilm` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for wards
-- ----------------------------
DROP TABLE IF EXISTS `wards`;
CREATE TABLE `wards` (
  `ward_id` bigint NOT NULL AUTO_INCREMENT,
  `bed_price` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `floor_number` int DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `total_beds` int NOT NULL,
  `ward_name` varchar(255) NOT NULL,
  PRIMARY KEY (`ward_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for beds
-- ----------------------------
DROP TABLE IF EXISTS `beds`;
CREATE TABLE `beds` (
  `bed_id` bigint NOT NULL AUTO_INCREMENT,
  `bed_code` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `current_ipd_admission_id` bigint DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `status` varchar(255) NOT NULL,
  `ward_id` bigint NOT NULL,
  PRIMARY KEY (`bed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for ipd_admission
-- ----------------------------
DROP TABLE IF EXISTS `ipd_admission`;
CREATE TABLE `ipd_admission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admission_datetime` datetime(6) NOT NULL,
  `admission_type` varchar(255) NOT NULL,
  `bed_id` bigint NOT NULL,
  `discharge_datetime` datetime(6) DEFAULT NULL,
  `doctor_id` bigint NOT NULL,
  `hospital_id` bigint NOT NULL,
  `ipd_number` varchar(255) NOT NULL,
  `notes` text,
  `patient_id` bigint NOT NULL,
  `primary_diagnosis` text,
  `source_opd_id` bigint DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  `ward_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_3p2j5aiaxya8xnh9epblbmlhp` (`ipd_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for medical_records
-- ----------------------------
DROP TABLE IF EXISTS `medical_records`;
CREATE TABLE `medical_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `appointment_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `diagnosis` varchar(1000) DEFAULT NULL,
  `doctor_id` bigint NOT NULL,
  `follow_up_date` date DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `ipd_admission_id` bigint DEFAULT NULL,
  `opd_id` bigint DEFAULT NULL,
  `patient_id` bigint NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `symptoms` varchar(1000) DEFAULT NULL,
  `treatment_notes` varchar(2000) DEFAULT NULL,
  `visit_type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_4elkgkxl6vd0j1p95r174acix` (`public_id`),
  UNIQUE KEY `UK_2nyonrbplqq716buy7u4ghmt8` (`appointment_id`),
  UNIQUE KEY `UK_8keap6v2c4lgf774c99jg4n7` (`opd_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for prescriptions
-- ----------------------------
DROP TABLE IF EXISTS `prescriptions`;
CREATE TABLE `prescriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `dosage` varchar(50) DEFAULT NULL,
  `duration` varchar(50) DEFAULT NULL,
  `duration_days` int DEFAULT NULL,
  `frequency` varchar(50) DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `instructions` varchar(200) DEFAULT NULL,
  `medical_record_id` bigint NOT NULL,
  `medicine_name` varchar(255) NOT NULL,
  `route` varchar(255) NOT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for medicines
-- ----------------------------
DROP TABLE IF EXISTS `medicines`;
CREATE TABLE `medicines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `default_dosage` varchar(255) DEFAULT NULL,
  `default_duration` varchar(255) DEFAULT NULL,
  `default_frequency` varchar(255) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `hospital_id` bigint DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `manufacturer` varchar(255) DEFAULT NULL,
  `min_stock_level` int DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `stock_quantity` int NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `unit_price` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for billing
-- ----------------------------
DROP TABLE IF EXISTS `billing`;
CREATE TABLE `billing` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10,2) NOT NULL,
  `appointment_id` bigint DEFAULT NULL,
  `billing_type` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `doctor_id` bigint NOT NULL,
  `hospital_id` bigint NOT NULL,
  `ipd_admission_id` bigint DEFAULT NULL,
  `opd_id` bigint DEFAULT NULL,
  `patient_id` bigint NOT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `payment_reference` varchar(100) DEFAULT NULL,
  `payment_status` varchar(20) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_873yunoi4d9gm43g5bu8ltm9k` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for billing_items
-- ----------------------------
DROP TABLE IF EXISTS `billing_items`;
CREATE TABLE `billing_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10,2) NOT NULL,
  `billing_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(200) NOT NULL,
  `hospital_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for billing_payments
-- ----------------------------
DROP TABLE IF EXISTS `billing_payments`;
CREATE TABLE `billing_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10,2) NOT NULL,
  `billing_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `hospital_id` bigint NOT NULL,
  `mode` varchar(50) DEFAULT NULL,
  `reference` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for discharge_summary
-- ----------------------------
DROP TABLE IF EXISTS `discharge_summary`;
CREATE TABLE `discharge_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `discharge_notes` text,
  `final_diagnosis` text,
  `follow_up_date` date DEFAULT NULL,
  `ipd_admission_id` bigint NOT NULL,
  `treatment_given` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5poa40gpt44a152gibdlfe6sb` (`ipd_admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for lab_orders
-- ----------------------------
DROP TABLE IF EXISTS `lab_orders`;
CREATE TABLE `lab_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `hospital_id` bigint NOT NULL,
  `medical_record_id` bigint NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `status` varchar(50) NOT NULL,
  `test_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_9d9924cas2rwvtgkan0mtkmq` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(255) NOT NULL,
  `details` varchar(1000) NOT NULL,
  `entity_id` varchar(255) DEFAULT NULL,
  `entity_type` varchar(255) DEFAULT NULL,
  `hospital_id` bigint DEFAULT NULL,
  `performed_by` varchar(255) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `reason` varchar(2000) DEFAULT NULL,
  `timestamp` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_cw6h8py7jx6jf1d4j3mjkrg47` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Initial Setup: Super Admin
-- ----------------------------
-- Email: admin@hms.com
-- Password: admin123
INSERT INTO `users` (`email`, `password`, `name`, `role`, `hospital_id`, `is_active`, `public_id`, `created_at`)
VALUES ('admin@hms.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhCy', 'Super Admin', 'SUPER_ADMIN', NULL, TRUE, 'SUPER_ADMIN_001', NOW());


CREATE TABLE medicine_master (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    medicine_code VARCHAR(50) UNIQUE,
    medicine_name VARCHAR(255) NOT NULL,
    generic_name VARCHAR(255),

    category_id BIGINT,
    manufacturer_id BIGINT,

    medicine_type VARCHAR(50),
    -- TABLET, SYRUP, INJECTION

    schedule_type VARCHAR(20),
    -- H, H1, X, OTC

    dosage_form VARCHAR(100),
    strength VARCHAR(100),

    unit_of_measure VARCHAR(50),
    -- TABLET, ML, BOTTLE

    reorder_level INT DEFAULT 0,

    gst_percentage DECIMAL(5,2),

    requires_prescription BOOLEAN DEFAULT TRUE,

    is_active BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medicine_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    medicine_id BIGINT NOT NULL,

    batch_number VARCHAR(100) NOT NULL,

    expiry_date DATE NOT NULL,
    manufacturing_date DATE,

    mrp DECIMAL(10,2) NOT NULL,

    purchase_rate DECIMAL(10,2) NOT NULL,

    selling_price DECIMAL(10,2) NOT NULL,

    current_quantity DECIMAL(10,2) DEFAULT 0,

    reserved_quantity DECIMAL(10,2) DEFAULT 0,

    location_id BIGINT,

    supplier_id BIGINT,

    purchase_invoice_item_id BIGINT,

    status VARCHAR(30),
    -- ACTIVE, EXPIRED, QUARANTINED, RECALLED

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    medicine_batch_id BIGINT NOT NULL,

    transaction_type VARCHAR(50),
    -- PURCHASE
    -- SALE
    -- SALE_RETURN
    -- PURCHASE_RETURN
    -- ADJUSTMENT
    -- EXPIRED
    -- DAMAGED
    -- TRANSFER

    quantity DECIMAL(10,2) NOT NULL,

    quantity_before DECIMAL(10,2),
    quantity_after DECIMAL(10,2),

    reference_type VARCHAR(50),
    -- PURCHASE_INVOICE
    -- SALE_INVOICE
    -- MANUAL

    reference_id BIGINT,

    remarks TEXT,

    created_by BIGINT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_invoices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    supplier_id BIGINT NOT NULL,

    invoice_number VARCHAR(100),

    invoice_date DATE,

    subtotal DECIMAL(12,2),
    discount_amount DECIMAL(12,2),
    gst_amount DECIMAL(12,2),
    total_amount DECIMAL(12,2),

    payment_status VARCHAR(50),

    posting_status VARCHAR(50),
    -- DRAFT / POSTED

    created_by BIGINT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_invoice_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    purchase_invoice_id BIGINT NOT NULL,

    medicine_id BIGINT NOT NULL,

    batch_number VARCHAR(100),

    expiry_date DATE,

    quantity DECIMAL(10,2),

    free_quantity DECIMAL(10,2),

    purchase_rate DECIMAL(10,2),

    mrp DECIMAL(10,2),

    selling_price DECIMAL(10,2),

    gst_percentage DECIMAL(5,2),

    line_total DECIMAL(12,2)
);

CREATE TABLE pharmacy_sales (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    invoice_number VARCHAR(100) UNIQUE,

    patient_id BIGINT,

    prescription_id BIGINT,

    doctor_id BIGINT,

    pharmacist_id BIGINT,

    sale_type VARCHAR(30),
    -- OPD / IPD / WALKIN

    subtotal DECIMAL(12,2),
    discount_amount DECIMAL(12,2),
    gst_amount DECIMAL(12,2),
    total_amount DECIMAL(12,2),

    payment_method VARCHAR(50),

    payment_status VARCHAR(50),

    posting_status VARCHAR(50),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pharmacy_sale_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    sale_id BIGINT NOT NULL,

    medicine_batch_id BIGINT NOT NULL,

    prescribed_quantity DECIMAL(10,2),

    sold_quantity DECIMAL(10,2),

    unit_price DECIMAL(10,2),

    discount_percentage DECIMAL(5,2),

    gst_percentage DECIMAL(5,2),

    line_total DECIMAL(12,2)
);

CREATE TABLE sale_returns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    hospital_id BIGINT NOT NULL,

    sale_id BIGINT NOT NULL,

    return_reason TEXT,

    refund_amount DECIMAL(12,2),

    approved_by BIGINT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);