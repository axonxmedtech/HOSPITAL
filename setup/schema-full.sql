CREATE DATABASE  IF NOT EXISTS `railway` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `railway`;
-- MySQL dump 10.13  Distrib 8.0.36, for Win64 (x86_64)
--
-- Host: viaduct.proxy.rlwy.net    Database: railway
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `appointments`
--

DROP TABLE IF EXISTS `appointments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `beds`
--

DROP TABLE IF EXISTS `beds`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beds` (
  `bed_id` bigint NOT NULL AUTO_INCREMENT,
  `bed_code` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `current_ipd_admission_id` bigint DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `status` varchar(255) NOT NULL,
  `ward_id` bigint NOT NULL,
  PRIMARY KEY (`bed_id`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `billing`
--

DROP TABLE IF EXISTS `billing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `marked_paid_by` varchar(100) DEFAULT NULL,
  `payment_status` varchar(20) NOT NULL,
  `public_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_873yunoi4d9gm43g5bu8ltm9k` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `billing_items`
--

DROP TABLE IF EXISTS `billing_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10,2) NOT NULL,
  `billing_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(200) NOT NULL,
  `hospital_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `billing_payments`
--

DROP TABLE IF EXISTS `billing_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

-- Form 30 BR-7 patient advance deposits. Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS patient_advance (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `patient_id` bigint NOT NULL,
  `ipd_admission_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `balance` decimal(10,2) NOT NULL,
  `payment_mode` varchar(30) DEFAULT NULL,
  `received_by_name` varchar(100) DEFAULT NULL,
  `received_at` datetime DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_patient_advance_hospital_admission` (`hospital_id`, `ipd_admission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Form 30 BR-5 refund sign-off register. Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS billing_refund (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `billing_id` bigint NOT NULL,
  `patient_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` text NOT NULL,
  `requested_by_name` varchar(100) NOT NULL,
  `requested_at` datetime NOT NULL,
  `status` varchar(20) DEFAULT NULL,
  `approved_by_name` varchar(100) DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `rejection_reason` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_refund_hospital` (`hospital_id`, `billing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `discharge_summary`
--

-- Phase 0.1 additive migration (safe on live data):
-- ALTER TABLE `discharge_summary`
--   ADD COLUMN `hospital_id` bigint DEFAULT NULL,
--   ADD COLUMN `patient_id` bigint DEFAULT NULL,
--   ADD COLUMN `doctor_id` bigint DEFAULT NULL,
--   ADD KEY `idx_discharge_summary_hospital` (`hospital_id`);
-- UPDATE `discharge_summary` ds JOIN `ipd_admission` ia ON ia.id = ds.ipd_admission_id
--   SET ds.hospital_id = ia.hospital_id, ds.patient_id = ia.patient_id, ds.doctor_id = ia.doctor_id
--   WHERE ds.hospital_id IS NULL;
DROP TABLE IF EXISTS `discharge_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `discharge_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `discharge_notes` text,
  `final_diagnosis` text,
  `follow_up_date` date DEFAULT NULL,
  `ipd_admission_id` bigint NOT NULL,
  `treatment_given` text,
  `hospital_id` bigint DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  `discharge_type` varchar(50) DEFAULT NULL,
  `discharge_condition` varchar(50) DEFAULT NULL,
  `icd_code` varchar(20) DEFAULT NULL,
  `follow_up_advice` text,
  `home_medications` text,
  `diet_advice` text,
  `activity_restrictions` text,
  `referred_to` varchar(255) DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `finalized_by` bigint DEFAULT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5poa40gpt44a152gibdlfe6sb` (`ipd_admission_id`),
  KEY `idx_discharge_summary_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Phase 3 additive migration (safe on live data — nullable columns, Hibernate ddl-auto creates them):
-- ALTER TABLE `discharge_summary`
--   ADD COLUMN `discharge_type` varchar(50) DEFAULT NULL,
--   ADD COLUMN `discharge_condition` varchar(50) DEFAULT NULL,
--   ADD COLUMN `icd_code` varchar(20) DEFAULT NULL,
--   ADD COLUMN `follow_up_advice` text,
--   ADD COLUMN `home_medications` text,
--   ADD COLUMN `diet_advice` text,
--   ADD COLUMN `activity_restrictions` text,
--   ADD COLUMN `referred_to` varchar(255) DEFAULT NULL,
--   ADD COLUMN `status` varchar(30) DEFAULT NULL,
--   ADD COLUMN `finalized_by` bigint DEFAULT NULL,
--   ADD COLUMN `finalized_at` datetime(6) DEFAULT NULL;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `doctors`
--

-- Phase 0.5 additive migration:
-- ALTER TABLE doctors ADD COLUMN user_id bigint DEFAULT NULL;
-- ALTER TABLE doctors ADD COLUMN is_anaesthetist bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE doctors ADD COLUMN is_surgeon bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE doctors ADD COLUMN is_pathologist bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE doctors ADD COLUMN is_radiologist bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE doctors ADD COLUMN is_intensivist bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE doctors ADD COLUMN is_cmo bit(1) NOT NULL DEFAULT b'0';

DROP TABLE IF EXISTS `doctors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `user_id` bigint DEFAULT NULL,
  `is_anaesthetist` bit(1) NOT NULL DEFAULT b'0',
  `is_surgeon` bit(1) NOT NULL DEFAULT b'0',
  `is_pathologist` bit(1) NOT NULL DEFAULT b'0',
  `is_radiologist` bit(1) NOT NULL DEFAULT b'0',
  `is_intensivist` bit(1) NOT NULL DEFAULT b'0',
  `is_cmo` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_lqd2m2kx16cc3locfwtq02bek` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hospital_modules`
--

DROP TABLE IF EXISTS `hospital_modules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hospital_modules` (
  `hospital_id` bigint NOT NULL,
  `module_name` varchar(255) DEFAULT NULL,
  KEY `FKcn73q131q80psfovorxk09c13` (`hospital_id`),
  CONSTRAINT `FKcn73q131q80psfovorxk09c13` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hospitals`
--

DROP TABLE IF EXISTS `hospitals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hospitals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(500) DEFAULT NULL,
  `case_paper_fee` decimal(10,2) DEFAULT NULL,
  `consultation_fee` decimal(10,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `custom_id` varchar(255) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `logo_url` varchar(500) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `opd_timings` varchar(100) DEFAULT NULL,
  `parent_organization` varchar(200) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `plan` varchar(20) DEFAULT NULL,
  `public_id` varchar(255) NOT NULL,
  `subscription_status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `type` varchar(20) NOT NULL DEFAULT 'HOSPITAL',
  `is_single_doctor` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_dre74r9dx5tme2mvsmu33ur77` (`public_id`),
  UNIQUE KEY `UK_snisvhrc8x0rcmph9cwiuqxvt` (`custom_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_transactions`
--

DROP TABLE IF EXISTS `inventory_transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `medicine_batch_id` bigint NOT NULL,
  `transaction_type` varchar(50) DEFAULT NULL,
  `quantity` decimal(38,2) NOT NULL,
  `quantity_before` decimal(38,2) DEFAULT NULL,
  `quantity_after` decimal(38,2) DEFAULT NULL,
  `reference_type` varchar(50) DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `remarks` text,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `FKcmvc7jx2d37wg6116l0ghu3h7` (`medicine_batch_id`),
  CONSTRAINT `FKcmvc7jx2d37wg6116l0ghu3h7` FOREIGN KEY (`medicine_batch_id`) REFERENCES `medicine_batches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ipd_admission`
--

DROP TABLE IF EXISTS `ipd_admission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lab_orders`
--

DROP TABLE IF EXISTS `lab_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lab_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `hospital_id` bigint NOT NULL,
  `medical_record_id` bigint NOT NULL,
  `public_id` varchar(255) NOT NULL,
  `status` varchar(50) NOT NULL,
  `test_name` varchar(255) NOT NULL,
  `lab_test_master_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_9d9924cas2rwvtgkan0mtkmq` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `manufacturers`
--

DROP TABLE IF EXISTS `manufacturers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `manufacturers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` text,
  `contact_person` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `license_number` varchar(255) DEFAULT NULL,
  `manufacturer_name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medical_records`
--

DROP TABLE IF EXISTS `medical_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `administered_items_json` varchar(3000) DEFAULT NULL,
  `symptoms` varchar(1000) DEFAULT NULL,
  `treatment_notes` varchar(2000) DEFAULT NULL,
  `visit_type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_4elkgkxl6vd0j1p95r174acix` (`public_id`),
  UNIQUE KEY `UK_2nyonrbplqq716buy7u4ghmt8` (`appointment_id`),
  UNIQUE KEY `UK_8keap6v2c4lgf774c99jg4n7` (`opd_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medicine_batches`
--

DROP TABLE IF EXISTS `medicine_batches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medicine_batches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `medicine_id` bigint NOT NULL,
  `batch_number` varchar(100) NOT NULL,
  `expiry_date` date NOT NULL,
  `manufacturing_date` date DEFAULT NULL,
  `mrp` decimal(38,2) NOT NULL,
  `purchase_rate` decimal(38,2) NOT NULL,
  `selling_price` decimal(38,2) NOT NULL,
  `current_quantity` decimal(38,2) DEFAULT NULL,
  `reserved_quantity` decimal(38,2) DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `purchase_invoice_item_id` bigint DEFAULT NULL,
  `gst_percentage` decimal(38,2) DEFAULT NULL,
  `remarks` text,
  `status` varchar(30) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `FKs60kqtwcpr2qtqsd9xle3eqot` (`location_id`),
  KEY `FKchyofsp38cdkq2erdq4a9r1uc` (`medicine_id`),
  KEY `FKlds0umb8e6lk2afnbqnmjyy41` (`supplier_id`),
  CONSTRAINT `FKchyofsp38cdkq2erdq4a9r1uc` FOREIGN KEY (`medicine_id`) REFERENCES `medicine_master` (`id`),
  CONSTRAINT `FKlds0umb8e6lk2afnbqnmjyy41` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKs60kqtwcpr2qtqsd9xle3eqot` FOREIGN KEY (`location_id`) REFERENCES `storage_locations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- Form 29 BR-4 narcotic register. Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS narcotic_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    pharmacy_sale_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    quantity_issued DECIMAL(10,2) NOT NULL,
    quantity_wasted DECIMAL(10,2) NULL,
    witness_user_id BIGINT NOT NULL,
    witness_name VARCHAR(100) NOT NULL,
    dispensed_by_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_narcotic_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_narcotic_log_hospital ON narcotic_log(hospital_id, created_at);

-- Phase 6.01 Blood Bank / BBTMS (Form 38 core). Additive; Hibernate ddl-auto creates them.
CREATE TABLE IF NOT EXISTS blood_donor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    donor_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NULL,
    blood_group VARCHAR(5) NOT NULL,
    rh_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deferral_expiry DATE NULL,
    last_donation_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_donor_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS blood_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    unit_number VARCHAR(30) NOT NULL UNIQUE,
    donor_id BIGINT NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    blood_group VARCHAR(5) NOT NULL,
    rh_type VARCHAR(10) NOT NULL,
    hiv_result VARCHAR(20) NULL,
    hbsag_result VARCHAR(20) NULL,
    malaria_result VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL,
    expiry_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_unit_donor FOREIGN KEY (donor_id) REFERENCES blood_donor(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_blood_unit_group_status ON blood_unit(hospital_id, blood_group, status);

CREATE TABLE IF NOT EXISTS blood_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    admission_id BIGINT NULL,
    department VARCHAR(50) NOT NULL,
    component VARCHAR(20) NOT NULL,
    units_requested INT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by_name VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bloodreq_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cross_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    blood_unit_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    verified_by_name VARCHAR(100) NULL,
    verified_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xmatch_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_xmatch_request FOREIGN KEY (request_id) REFERENCES blood_request(id),
    CONSTRAINT fk_xmatch_unit FOREIGN KEY (blood_unit_id) REFERENCES blood_unit(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transfusion_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    blood_unit_id BIGINT NOT NULL UNIQUE,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    reaction VARCHAR(30) NOT NULL,
    reaction_notes TEXT NULL,
    nurse_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfusion_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_transfusion_unit FOREIGN KEY (blood_unit_id) REFERENCES blood_unit(id)
) ENGINE=InnoDB;

--
-- Table structure for table `medicine_categories`
--

DROP TABLE IF EXISTS `medicine_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medicine_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medicine_master`
--

DROP TABLE IF EXISTS `medicine_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `medicine_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `medicine_code` varchar(50) DEFAULT NULL,
  `medicine_name` varchar(255) NOT NULL,
  `generic_name` varchar(255) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  `manufacturer_id` bigint DEFAULT NULL,
  `medicine_type` varchar(50) DEFAULT NULL,
  `schedule_type` varchar(20) DEFAULT NULL,
  `dosage_form` varchar(100) DEFAULT NULL,
  `strength` varchar(100) DEFAULT NULL,
  `unit_of_measure` varchar(50) DEFAULT NULL,
  `reorder_level` int DEFAULT '0',
  `min_stock_level` int DEFAULT '0',
  `gst_percentage` decimal(38,2) DEFAULT NULL,
  `requires_prescription` tinyint(1) DEFAULT '1',
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `medicine_code` (`medicine_code`),
  KEY `FKqbtdqift3k79579mmsb1hp42d` (`category_id`),
  KEY `FK7t8h6ykhxexnd6q6w2etvg9u7` (`manufacturer_id`),
  CONSTRAINT `FK7t8h6ykhxexnd6q6w2etvg9u7` FOREIGN KEY (`manufacturer_id`) REFERENCES `manufacturers` (`id`),
  CONSTRAINT `FKqbtdqift3k79579mmsb1hp42d` FOREIGN KEY (`category_id`) REFERENCES `medicine_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `medicines`
--

DROP TABLE IF EXISTS `medicines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `opd`
--

DROP TABLE IF EXISTS `opd`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `patients`
--

-- Phase 0.4 additive migration:
-- ALTER TABLE patients ADD COLUMN date_of_birth date DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN guardian_name varchar(100) DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN guardian_relationship varchar(50) DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN preferred_language varchar(50) DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN blood_group varchar(10) DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN uhid varchar(50) DEFAULT NULL;
-- ALTER TABLE patients ADD COLUMN is_temporary bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE patients ADD COLUMN is_unknown bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE patients ADD COLUMN is_merged bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE patients ADD COLUMN merged_to_id bigint DEFAULT NULL;
-- UPDATE patients SET uhid = CONCAT('UHID-', hospital_id, '-', id) WHERE uhid IS NULL;
-- UPDATE patients SET age = TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) WHERE age IS NULL AND date_of_birth IS NOT NULL;
-- UPDATE patients SET date_of_birth = DATE_SUB(CONCAT(YEAR(CURDATE()) - age, '-01-01'), INTERVAL 0 DAY) WHERE date_of_birth IS NULL AND age IS NOT NULL;

DROP TABLE IF EXISTS `patients`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `date_of_birth` date DEFAULT NULL,
  `guardian_name` varchar(100) DEFAULT NULL,
  `guardian_relationship` varchar(50) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `blood_group` varchar(10) DEFAULT NULL,
  `uhid` varchar(50) DEFAULT NULL,
  `is_temporary` bit(1) NOT NULL DEFAULT b'0',
  `is_unknown` bit(1) NOT NULL DEFAULT b'0',
  `is_merged` bit(1) NOT NULL DEFAULT b'0',
  `merged_to_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8isyrjl9ji56k5uv4cgp9p2q6` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pharmacy_audit_logs`
--

DROP TABLE IF EXISTS `pharmacy_audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pharmacy_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `username` varchar(255) NOT NULL,
  `user_role` varchar(50) NOT NULL,
  `action_type` varchar(100) NOT NULL,
  `module` varchar(100) NOT NULL,
  `entity_name` varchar(255) DEFAULT NULL,
  `entity_id` bigint DEFAULT NULL,
  `old_value` text DEFAULT NULL,
  `new_value` text DEFAULT NULL,
  `remarks` text DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_pal_hospital_id` (`hospital_id`),
  KEY `idx_pal_action_type` (`action_type`),
  KEY `idx_pal_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pharmacy_sale_items`
--

DROP TABLE IF EXISTS `pharmacy_sale_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pharmacy_sale_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sale_id` bigint NOT NULL,
  `medicine_batch_id` bigint NOT NULL,
  `prescribed_quantity` decimal(10,2) DEFAULT NULL,
  `sold_quantity` decimal(10,2) DEFAULT NULL,
  `unit_price` decimal(10,2) DEFAULT NULL,
  `discount_percentage` decimal(5,2) DEFAULT NULL,
  `gst_percentage` decimal(5,2) DEFAULT NULL,
  `line_total` decimal(12,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pharmacy_sales`
--

DROP TABLE IF EXISTS `pharmacy_sales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pharmacy_sales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `invoice_number` varchar(100) DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL,
  `prescription_id` bigint DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  `pharmacist_id` bigint DEFAULT NULL,
  `sale_type` varchar(30) DEFAULT NULL,
  `subtotal` decimal(12,2) DEFAULT NULL,
  `discount_amount` decimal(12,2) DEFAULT NULL,
  `gst_amount` decimal(12,2) DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `payment_status` varchar(50) DEFAULT NULL,
  `posting_status` varchar(50) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_number` (`invoice_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `prescriptions`
--

DROP TABLE IF EXISTS `prescriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `medicine_master_id` bigint DEFAULT NULL,
  `route` varchar(255) NOT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `purchase_invoice_items`
--

DROP TABLE IF EXISTS `purchase_invoice_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_invoice_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_invoice_id` bigint NOT NULL,
  `medicine_id` bigint NOT NULL,
  `batch_number` varchar(100) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `quantity` decimal(38,2) DEFAULT NULL,
  `free_quantity` decimal(38,2) DEFAULT NULL,
  `purchase_rate` decimal(38,2) DEFAULT NULL,
  `mrp` decimal(38,2) DEFAULT NULL,
  `selling_price` decimal(38,2) DEFAULT NULL,
  `gst_percentage` decimal(38,2) DEFAULT NULL,
  `line_total` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg67d6qh5bq0jm6cnjogocjfy0` (`medicine_id`),
  CONSTRAINT `FKg67d6qh5bq0jm6cnjogocjfy0` FOREIGN KEY (`medicine_id`) REFERENCES `medicine_master` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `purchase_invoices`
--

DROP TABLE IF EXISTS `purchase_invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `supplier_id` bigint NOT NULL,
  `invoice_number` varchar(100) DEFAULT NULL,
  `invoice_date` date DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `discount_amount` decimal(38,2) DEFAULT NULL,
  `gst_amount` decimal(38,2) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `payment_status` varchar(50) DEFAULT NULL,
  `posting_status` varchar(50) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `FKa0fe6yai855h0xkk72fhmpk2a` (`supplier_id`),
  CONSTRAINT `FKa0fe6yai855h0xkk72fhmpk2a` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `queue_entry`
--

DROP TABLE IF EXISTS `queue_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sale_returns`
--

DROP TABLE IF EXISTS `sale_returns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sale_returns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `sale_id` bigint NOT NULL,
  `return_reason` text,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_locations`
--

DROP TABLE IF EXISTS `storage_locations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_locations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `location_name` varchar(255) NOT NULL,
  `location_type` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `suppliers`
--

DROP TABLE IF EXISTS `suppliers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suppliers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` text,
  `contact_person` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credit_days` int DEFAULT NULL,
  `drug_license_number` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `gst_number` varchar(255) DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `supplier_name` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

-- Phase 0.5 additive migration:
-- ALTER TABLE users ADD COLUMN department varchar(100) DEFAULT NULL;
-- ALTER TABLE users ADD COLUMN designation varchar(100) DEFAULT NULL;
-- ALTER TABLE users ADD COLUMN is_trainer bit(1) NOT NULL DEFAULT b'0';

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `department` varchar(100) DEFAULT NULL,
  `designation` varchar(100) DEFAULT NULL,
  `is_trainer` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_s24bux761rbgowsl7a4b386ba` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `wards`
--

DROP TABLE IF EXISTS `wards`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wards` (
  `ward_id` bigint NOT NULL AUTO_INCREMENT,
  `bed_price` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `floor_number` int DEFAULT NULL,
  `hospital_id` bigint NOT NULL,
  `total_beds` int NOT NULL,
  `ward_name` varchar(255) NOT NULL,
  PRIMARY KEY (`ward_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-13 16:27:45

-- Performance indexes: hospital_id scoping + frequently filtered columns
CREATE INDEX IF NOT EXISTS idx_patients_hospital ON patients(hospital_id);
CREATE INDEX IF NOT EXISTS idx_appointments_hospital_date ON appointments(hospital_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_opd_hospital_created ON opd(hospital_id, created_at);
CREATE INDEX IF NOT EXISTS idx_billing_hospital_status ON billing(hospital_id, payment_status);
CREATE INDEX IF NOT EXISTS idx_medicine_batches_expiry ON medicine_batches(hospital_id, expiry_date);
CREATE INDEX IF NOT EXISTS idx_audit_logs_hospital_ts ON audit_logs(hospital_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_doctors_hospital ON doctors(hospital_id);
CREATE INDEX IF NOT EXISTS idx_ipd_admissions_hospital ON ipd_admissions(hospital_id, status);
CREATE INDEX IF NOT EXISTS idx_nurses_hospital_id ON nurses(hospital_id);

--
-- Table structure for table `plans`
-- (Platform-level subscription plans for hospitals)
--

DROP TABLE IF EXISTS `plans`;
CREATE TABLE `plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` varchar(255) NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(20) NOT NULL,
  `monthly_price` decimal(10,2) NOT NULL DEFAULT 0.00,
  `yearly_price` decimal(10,2) NOT NULL DEFAULT 0.00,
  `in_clinic` tinyint(1) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_plans_public_id` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `plan_modules`
-- (Element collection for Plan.modules)
--

DROP TABLE IF EXISTS `plan_modules`;
CREATE TABLE `plan_modules` (
  `plan_id` bigint NOT NULL,
  `module_name` varchar(255) DEFAULT NULL,
  KEY `FK_plan_modules_plan_id` (`plan_id`),
  CONSTRAINT `FK_plan_modules_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `plan_features`
-- (Element collection for Plan.features)
--

DROP TABLE IF EXISTS `plan_features`;
CREATE TABLE `plan_features` (
  `plan_id` bigint NOT NULL,
  `feature_name` varchar(255) DEFAULT NULL,
  KEY `FK_plan_features_plan_id` (`plan_id`),
  CONSTRAINT `FK_plan_features_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `hospital_plan_subscriptions`
-- (Tracks which plan each hospital is subscribed to)
--

DROP TABLE IF EXISTS `hospital_plan_subscriptions`;
CREATE TABLE `hospital_plan_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `billing_period` varchar(20) NOT NULL,
  `assigned_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `assigned_by` bigint DEFAULT NULL,
  `is_current` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `FK_hps_hospital_id` (`hospital_id`),
  KEY `FK_hps_plan_id` (`plan_id`),
  CONSTRAINT `FK_hps_hospital_id` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`),
  CONSTRAINT `FK_hps_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Table structure for table `hospital_settings`
-- (Per-hospital operational toggles: reception mode, billing handler, etc.)
--

DROP TABLE IF EXISTS `hospital_settings`;
CREATE TABLE `hospital_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `reception_mode` varchar(20) NOT NULL DEFAULT 'HAS_RECEPTIONIST',
  `billing_handler` varchar(20) NOT NULL DEFAULT 'RECEPTIONIST',
  `in_clinic` tinyint(1) NOT NULL DEFAULT 1,
  `shift_mode` varchar(20) NOT NULL DEFAULT 'FIXED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_hospital_settings_hospital_id` (`hospital_id`),
  CONSTRAINT `FK_hospital_settings_hospital_id` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- WhatsApp integration tables (added with V4 migration)
--

CREATE TABLE IF NOT EXISTS `whatsapp_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `access_token` varchar(500) NOT NULL,
  `phone_number_id` varchar(100) NOT NULL,
  `waba_id` varchar(100) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `send_appointments` tinyint(1) NOT NULL DEFAULT 1,
  `send_billing` tinyint(1) NOT NULL DEFAULT 1,
  `send_case_papers` tinyint(1) NOT NULL DEFAULT 1,
  `send_prescription` tinyint(1) NOT NULL DEFAULT 1,
  `send_medicine_list` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_wc_hospital` UNIQUE (`hospital_id`),
  CONSTRAINT `FK_whatsapp_config_hospital` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `whatsapp_message_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hospital_id` bigint NOT NULL,
  `patient_id` bigint DEFAULT NULL,
  `patient_phone` varchar(20) NOT NULL,
  `message_type` varchar(50) NOT NULL,
  `status` varchar(25) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `template_name` varchar(100) DEFAULT NULL,
  `template_params_json` varchar(1000) DEFAULT NULL,
  `media_url` varchar(500) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wml_hospital_status` (`hospital_id`, `status`),
  KEY `idx_wml_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================
-- NURSE IPD WORKFLOW (Phase 1)
-- =============================================

-- Phase 0.5 additive migration:
-- ALTER TABLE nurses ADD COLUMN user_id bigint DEFAULT NULL;
-- ALTER TABLE nurses ADD COLUMN is_scrub bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE nurses ADD COLUMN is_ot bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE nurses ADD COLUMN is_pacu bit(1) NOT NULL DEFAULT b'0';
-- ALTER TABLE nurses ADD COLUMN is_icu bit(1) NOT NULL DEFAULT b'0';

CREATE TABLE IF NOT EXISTS nurses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    custom_id VARCHAR(20),
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT DEFAULT NULL,
    is_scrub bit(1) NOT NULL DEFAULT b'0',
    is_ot bit(1) NOT NULL DEFAULT b'0',
    is_pacu bit(1) NOT NULL DEFAULT b'0',
    is_icu bit(1) NOT NULL DEFAULT b'0',
    CONSTRAINT fk_nurse_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

CREATE TABLE IF NOT EXISTS nurse_ward_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nurse_id BIGINT NOT NULL,
    ward_id BIGINT NOT NULL,
    UNIQUE KEY uq_nurse_ward (nurse_id, ward_id),
    CONSTRAINT fk_nwa_nurse FOREIGN KEY (nurse_id) REFERENCES nurses(id),
    CONSTRAINT fk_nwa_ward FOREIGN KEY (ward_id) REFERENCES wards(id)
);

CREATE TABLE IF NOT EXISTS nurse_assessments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL UNIQUE,
    hospital_id BIGINT NOT NULL,
    blood_pressure VARCHAR(20),
    pulse INT,
    temperature DECIMAL(4,1),
    spo2 INT,
    height DECIMAL(5,1),
    weight DECIMAL(5,1),
    pain_score INT,
    allergies TEXT,
    fall_risk VARCHAR(10),
    general_condition TEXT,
    chief_complaint_on_admission TEXT,
    assessed_by BIGINT,
    assessed_by_name VARCHAR(100),
    assessed_at DATETIME,
    CONSTRAINT fk_na_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id)
);

-- Phase 0.2 additive migration (safe on live data):
-- ALTER TABLE `vital_signs`
--   ADD COLUMN `bp_systolic` int DEFAULT NULL,
--   ADD COLUMN `bp_diastolic` int DEFAULT NULL,
--   ADD COLUMN `pain_score` int DEFAULT NULL,
--   ADD COLUMN `weight` decimal(5,2) DEFAULT NULL,
--   ADD COLUMN `oxygen_support` varchar(50) DEFAULT NULL,
--   ADD COLUMN `remarks` text;
-- UPDATE `vital_signs`
--   SET bp_systolic  = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure,' ','/'),'/',1) AS UNSIGNED),
--       bp_diastolic = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure,' ','/'),'/',-1) AS UNSIGNED)
--   WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$';
CREATE TABLE IF NOT EXISTS vital_signs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    blood_pressure VARCHAR(20),
    bp_systolic int DEFAULT NULL,
    bp_diastolic int DEFAULT NULL,
    pain_score int DEFAULT NULL,
    weight decimal(5,2) DEFAULT NULL,
    oxygen_support varchar(50) DEFAULT NULL,
    remarks text,
    pulse INT,
    temperature DECIMAL(4,1),
    spo2 INT,
    respiratory_rate int DEFAULT NULL,
    temp_method varchar(50) DEFAULT NULL,
    pulse_rhythm varchar(50) DEFAULT NULL,
    resp_pattern varchar(50) DEFAULT NULL,
    bp_position varchar(50) DEFAULT NULL,
    recorded_by BIGINT,
    recorded_by_name VARCHAR(100),
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_vs_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id)
);

-- Phase 1 additive migration:
-- ALTER TABLE `vital_signs`
--   ADD COLUMN `temp_method` varchar(50) DEFAULT NULL,
--   ADD COLUMN `pulse_rhythm` varchar(50) DEFAULT NULL,
--   ADD COLUMN `resp_pattern` varchar(50) DEFAULT NULL,
--   ADD COLUMN `bp_position` varchar(50) DEFAULT NULL;

-- Phase 0.7 additive migration:
-- CREATE TABLE IF NOT EXISTS monitoring_vitals (
--   id bigint NOT NULL AUTO_INCREMENT,
--   ipd_admission_id bigint NOT NULL,
--   hospital_id bigint NOT NULL,
--   context varchar(20) NOT NULL,
--   pulse int DEFAULT NULL,
--   bp_systolic int DEFAULT NULL,
--   bp_diastolic int DEFAULT NULL,
--   spo2 int DEFAULT NULL,
--   respiratory_rate int DEFAULT NULL,
--   temperature decimal(4,1) DEFAULT NULL,
--   recorded_by bigint DEFAULT NULL,
--   recorded_by_name varchar(100) DEFAULT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_mv_admission_context (ipd_admission_id, context),
--   KEY idx_mv_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS monitoring_vitals (
  id bigint NOT NULL AUTO_INCREMENT,
  ipd_admission_id bigint NOT NULL,
  hospital_id bigint NOT NULL,
  context varchar(20) NOT NULL,
  pulse int DEFAULT NULL,
  bp_systolic int DEFAULT NULL,
  bp_diastolic int DEFAULT NULL,
  spo2 int DEFAULT NULL,
  respiratory_rate int DEFAULT NULL,
  temperature decimal(4,1) DEFAULT NULL,
  recorded_by bigint DEFAULT NULL,
  recorded_by_name varchar(100) DEFAULT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_mv_admission_context (ipd_admission_id, context),
  KEY idx_mv_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 0.8 additive migration:
-- CREATE TABLE IF NOT EXISTS signature_slots (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   signer_role varchar(30) NOT NULL,
--   signer_name varchar(100) NOT NULL,
--   signer_relationship varchar(50) DEFAULT NULL,
--   signed_at datetime NOT NULL,
--   document_type varchar(50) NOT NULL,
--   document_id varchar(50) NOT NULL,
--   signature_image_base64 longtext DEFAULT NULL,
--   signature_hash varchar(64) DEFAULT NULL,
--   PRIMARY KEY (id),
--   KEY idx_sig_doc (document_type, document_id),
--   KEY idx_sig_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS document_versions (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   document_type varchar(50) NOT NULL,
--   document_id varchar(50) NOT NULL,
--   version int NOT NULL,
--   content_url varchar(255) DEFAULT NULL,
--   updated_by bigint DEFAULT NULL,
--   updated_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_dv_doc (document_type, document_id),
--   KEY idx_dv_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS signature_slots (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  signer_role varchar(30) NOT NULL,
  signer_name varchar(100) NOT NULL,
  signer_relationship varchar(50) DEFAULT NULL,
  signed_at datetime NOT NULL,
  document_type varchar(50) NOT NULL,
  document_id varchar(50) NOT NULL,
  signature_image_base64 longtext DEFAULT NULL,
  signature_hash varchar(64) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_sig_doc (document_type, document_id),
  KEY idx_sig_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS document_versions (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  document_type varchar(50) NOT NULL,
  document_id varchar(50) NOT NULL,
  version int NOT NULL,
  content_url varchar(255) DEFAULT NULL,
  updated_by bigint DEFAULT NULL,
  updated_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_dv_doc (document_type, document_id),
  KEY idx_dv_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 1 additive tables:
-- CREATE TABLE IF NOT EXISTS patient_consent (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint DEFAULT NULL,
--   encounter_type varchar(20) NOT NULL,
--   consent_type varchar(30) NOT NULL,
--   version int NOT NULL,
--   parent_id bigint DEFAULT NULL,
--   status varchar(20) NOT NULL,
--   patient_signed boolean NOT NULL DEFAULT false,
--   guardian_signed boolean NOT NULL DEFAULT false,
--   relationship varchar(40) DEFAULT NULL,
--   signature_type varchar(30) DEFAULT NULL,
--   witness_id bigint DEFAULT NULL,
--   language varchar(20) NOT NULL,
--   interpreter_id bigint DEFAULT NULL,
--   signed_at datetime DEFAULT NULL,
--   remarks text DEFAULT NULL,
--   created_by bigint DEFAULT NULL,
--   created_at datetime NOT NULL,
--   updated_at datetime NOT NULL,
--   is_deleted boolean NOT NULL DEFAULT false,
--   PRIMARY KEY (id),
--   KEY idx_consent_patient (patient_id),
--   KEY idx_consent_admission (admission_id),
--   KEY idx_consent_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS blood_consent_detail (
--   id bigint NOT NULL AUTO_INCREMENT,
--   consent_id bigint NOT NULL UNIQUE,
--   explanation_given boolean NOT NULL DEFAULT false,
--   witness_patient_name varchar(100) DEFAULT NULL,
--   witness_patient_signed boolean NOT NULL DEFAULT false,
--   witness_hospital_name varchar(100) DEFAULT NULL,
--   witness_hospital_signed boolean NOT NULL DEFAULT false,
--   interpreter_required boolean NOT NULL DEFAULT false,
--   interpreter_language varchar(40) DEFAULT NULL,
--   interpreter_name varchar(100) DEFAULT NULL,
--   interpreter_signed boolean NOT NULL DEFAULT false,
--   blood_request_id bigint DEFAULT NULL,
--   PRIMARY KEY (id),
--   CONSTRAINT fk_blood_consent_parent FOREIGN KEY (consent_id) REFERENCES patient_consent (id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS clinical_assessment (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   doctor_id bigint NOT NULL,
--   chief_complaint text NOT NULL,
--   history_present_illness text NOT NULL,
--   provisional_diagnosis text NOT NULL,
--   treatment_plan text NOT NULL,
--   status varchar(20) NOT NULL,
--   version int NOT NULL,
--   parent_id bigint DEFAULT NULL,
--   finalized_by bigint DEFAULT NULL,
--   finalized_at datetime DEFAULT NULL,
--   created_at datetime NOT NULL,
--   updated_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_ca_admission (admission_id),
--   KEY idx_ca_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_medical_history (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   condition_name varchar(100) NOT NULL,
--   diagnosed_year int DEFAULT NULL,
--   is_active boolean NOT NULL DEFAULT true,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_pmh_patient (patient_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_surgical_history (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   procedure_name varchar(150) NOT NULL,
--   surgery_year int DEFAULT NULL,
--   hospital_name varchar(100) DEFAULT NULL,
--   complications text DEFAULT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_psh_patient (patient_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_medication_history (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   medication_name varchar(150) NOT NULL,
--   dosage varchar(50) DEFAULT NULL,
--   frequency varchar(50) DEFAULT NULL,
--   compliance_status varchar(30) DEFAULT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_pmdh_patient (patient_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_family_history (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   relationship varchar(50) NOT NULL,
--   condition_name varchar(100) NOT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_pfh_patient (patient_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_social_history (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   smoking_status varchar(30) DEFAULT NULL,
--   alcohol_consumption varchar(30) DEFAULT NULL,
--   tobacco_use varchar(30) DEFAULT NULL,
--   dietary_habits varchar(100) DEFAULT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_psch_patient (patient_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_diagnosis (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   diagnosis_code varchar(30) DEFAULT NULL,
--   diagnosis_description text NOT NULL,
--   diagnosis_type varchar(20) NOT NULL,
--   recorded_by bigint NOT NULL,
--   recorded_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_pd_admission (admission_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS patient_risk_assessment (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   scale_type varchar(30) NOT NULL,
--   fall_risk varchar(10) NOT NULL,
--   pressure_ulcer_risk varchar(10) NOT NULL,
--   nutrition_risk varchar(10) NOT NULL,
--   mental_status varchar(50) DEFAULT NULL,
--   mobility_status varchar(50) DEFAULT NULL,
--   infection_risk boolean NOT NULL DEFAULT false,
--   allergy_risk boolean NOT NULL DEFAULT false,
--   isolation_required boolean NOT NULL DEFAULT false,
--   overall_risk varchar(10) NOT NULL,
--   inputs_json text DEFAULT NULL,
--   status varchar(20) NOT NULL,
--   version int NOT NULL,
--   parent_id bigint DEFAULT NULL,
--   assessed_by bigint NOT NULL,
--   reviewed_by bigint DEFAULT NULL,
--   created_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_pra_admission (admission_id),
--   KEY idx_pra_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS fluid_master (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   category varchar(30) NOT NULL,
--   name varchar(100) NOT NULL,
--   default_unit varchar(10) NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_fm_hospital (hospital_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS fluid_intake (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   type varchar(30) NOT NULL,
--   source_ref bigint DEFAULT NULL,
--   description varchar(255) NOT NULL,
--   volume_ml int NOT NULL,
--   recorded_time datetime NOT NULL,
--   recorded_by bigint NOT NULL,
--   created_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_fi_admission (admission_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS fluid_output (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   type varchar(30) NOT NULL,
--   description varchar(255) NOT NULL,
--   volume_ml int NOT NULL,
--   color varchar(50) DEFAULT NULL,
--   recorded_time datetime NOT NULL,
--   recorded_by bigint NOT NULL,
--   created_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_fo_admission (admission_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS daily_fluid_balance (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   balance_date date NOT NULL,
--   total_intake int NOT NULL,
--   total_output int NOT NULL,
--   net_balance int NOT NULL,
--   updated_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   UNIQUE KEY uq_dfb_encounter (admission_id, balance_date)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS nursing_progress_note (
--   id bigint NOT NULL AUTO_INCREMENT,
--   public_id varchar(36) NOT NULL UNIQUE,
--   hospital_id bigint NOT NULL,
--   patient_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   shift varchar(20) NOT NULL,
--   nurse_id bigint NOT NULL,
--   general_condition varchar(50) NOT NULL,
--   pain_score int NOT NULL,
--   remarks text DEFAULT NULL,
--   doctor_notified boolean NOT NULL DEFAULT false,
--   doctor_name varchar(100) DEFAULT NULL,
--   doctor_advice text DEFAULT NULL,
--   patient_response varchar(50) NOT NULL,
--   status varchar(20) NOT NULL,
--   created_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_npn_admission (admission_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS nursing_procedure (
--   id bigint NOT NULL AUTO_INCREMENT,
--   progress_note_id bigint NOT NULL,
--   hospital_id bigint NOT NULL,
--   procedure_name varchar(150) NOT NULL,
--   performed_by bigint NOT NULL,
--   performed_time datetime NOT NULL,
--   remarks text DEFAULT NULL,
--   PRIMARY KEY (id),
--   CONSTRAINT fk_nproc_parent FOREIGN KEY (progress_note_id) REFERENCES nursing_progress_note (id) ON DELETE CASCADE
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- CREATE TABLE IF NOT EXISTS shift_handover (
--   id bigint NOT NULL AUTO_INCREMENT,
--   hospital_id bigint NOT NULL,
--   admission_id bigint NOT NULL,
--   shift varchar(20) NOT NULL,
--   outgoing_nurse_id bigint NOT NULL,
--   incoming_nurse_id bigint NOT NULL,
--   pending_tasks text DEFAULT NULL,
--   critical_alerts text DEFAULT NULL,
--   meds_due text DEFAULT NULL,
--   investigations_pending text DEFAULT NULL,
--   doctor_review_pending boolean NOT NULL DEFAULT false,
--   created_at datetime NOT NULL,
--   PRIMARY KEY (id),
--   KEY idx_sh_admission (admission_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_consent (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint DEFAULT NULL,
  encounter_type varchar(20) NOT NULL,
  consent_type varchar(30) NOT NULL,
  version int NOT NULL,
  parent_id bigint DEFAULT NULL,
  status varchar(20) NOT NULL,
  patient_signed boolean NOT NULL DEFAULT false,
  guardian_signed boolean NOT NULL DEFAULT false,
  relationship varchar(40) DEFAULT NULL,
  signature_type varchar(30) DEFAULT NULL,
  witness_id bigint DEFAULT NULL,
  language varchar(20) NOT NULL,
  interpreter_id bigint DEFAULT NULL,
  signed_at datetime DEFAULT NULL,
  remarks text DEFAULT NULL,
  created_by bigint DEFAULT NULL,
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  is_deleted boolean NOT NULL DEFAULT false,
  PRIMARY KEY (id),
  KEY idx_consent_patient (patient_id),
  KEY idx_consent_admission (admission_id),
  KEY idx_consent_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS blood_consent_detail (
  id bigint NOT NULL AUTO_INCREMENT,
  consent_id bigint NOT NULL UNIQUE,
  explanation_given boolean NOT NULL DEFAULT false,
  witness_patient_name varchar(100) DEFAULT NULL,
  witness_patient_signed boolean NOT NULL DEFAULT false,
  witness_hospital_name varchar(100) DEFAULT NULL,
  witness_hospital_signed boolean NOT NULL DEFAULT false,
  interpreter_required boolean NOT NULL DEFAULT false,
  interpreter_language varchar(40) DEFAULT NULL,
  interpreter_name varchar(100) DEFAULT NULL,
  interpreter_signed boolean NOT NULL DEFAULT false,
  blood_request_id bigint DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_blood_consent_parent FOREIGN KEY (consent_id) REFERENCES patient_consent (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Form 16 Surgical Consent detail. Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS surgical_consent_detail (
  id bigint NOT NULL AUTO_INCREMENT,
  consent_id bigint NOT NULL UNIQUE,
  procedure_name varchar(200) DEFAULT NULL,
  surgeon_name varchar(100) DEFAULT NULL,
  planned_anaesthesia varchar(100) DEFAULT NULL,
  risks_explained boolean NOT NULL DEFAULT false,
  alternatives_explained boolean NOT NULL DEFAULT false,
  high_risk boolean NOT NULL DEFAULT false,
  ot_booking_id bigint DEFAULT NULL,
  remarks text,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_surgical_consent_parent FOREIGN KEY (consent_id) REFERENCES patient_consent (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS clinical_assessment (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  doctor_id bigint NOT NULL,
  chief_complaint text NOT NULL,
  history_present_illness text NOT NULL,
  provisional_diagnosis text NOT NULL,
  treatment_plan text NOT NULL,
  status varchar(20) NOT NULL,
  version int NOT NULL,
  parent_id bigint DEFAULT NULL,
  finalized_by bigint DEFAULT NULL,
  finalized_at datetime DEFAULT NULL,
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ca_admission (admission_id),
  KEY idx_ca_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_medical_history (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  condition_name varchar(100) NOT NULL,
  diagnosed_year int DEFAULT NULL,
  is_active boolean NOT NULL DEFAULT true,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pmh_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_surgical_history (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  procedure_name varchar(150) NOT NULL,
  surgery_year int DEFAULT NULL,
  hospital_name varchar(100) DEFAULT NULL,
  complications text DEFAULT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_psh_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_medication_history (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  medication_name varchar(150) NOT NULL,
  dosage varchar(50) DEFAULT NULL,
  frequency varchar(50) DEFAULT NULL,
  compliance_status varchar(30) DEFAULT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pmdh_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_family_history (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  relationship varchar(50) NOT NULL,
  condition_name varchar(100) NOT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pfh_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_social_history (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  smoking_status varchar(30) DEFAULT NULL,
  alcohol_consumption varchar(30) DEFAULT NULL,
  tobacco_use varchar(30) DEFAULT NULL,
  dietary_habits varchar(100) DEFAULT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_psch_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_diagnosis (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  diagnosis_code varchar(30) DEFAULT NULL,
  diagnosis_description text NOT NULL,
  diagnosis_type varchar(20) NOT NULL,
  recorded_by bigint NOT NULL,
  recorded_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pd_admission (admission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS patient_risk_assessment (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  scale_type varchar(30) NOT NULL,
  fall_risk varchar(10) NOT NULL,
  pressure_ulcer_risk varchar(10) NOT NULL,
  nutrition_risk varchar(10) NOT NULL,
  mental_status varchar(50) DEFAULT NULL,
  mobility_status varchar(50) DEFAULT NULL,
  infection_risk boolean NOT NULL DEFAULT false,
  allergy_risk boolean NOT NULL DEFAULT false,
  isolation_required boolean NOT NULL DEFAULT false,
  overall_risk varchar(10) NOT NULL,
  inputs_json text DEFAULT NULL,
  status varchar(20) NOT NULL,
  version int NOT NULL,
  parent_id bigint DEFAULT NULL,
  assessed_by bigint NOT NULL,
  reviewed_by bigint DEFAULT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pra_admission (admission_id),
  KEY idx_pra_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fluid_master (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  category varchar(30) NOT NULL,
  name varchar(100) NOT NULL,
  default_unit varchar(10) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fm_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fluid_intake (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  type varchar(30) NOT NULL,
  source_ref bigint DEFAULT NULL,
  description varchar(255) NOT NULL,
  volume_ml int NOT NULL,
  recorded_time datetime NOT NULL,
  recorded_by bigint NOT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fi_admission (admission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS fluid_output (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  type varchar(30) NOT NULL,
  description varchar(255) NOT NULL,
  volume_ml int NOT NULL,
  color varchar(50) DEFAULT NULL,
  recorded_time datetime NOT NULL,
  recorded_by bigint NOT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fo_admission (admission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS daily_fluid_balance (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  balance_date date NOT NULL,
  total_intake int NOT NULL,
  total_output int NOT NULL,
  net_balance int NOT NULL,
  updated_at datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_dfb_encounter (admission_id, balance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS nursing_progress_note (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  shift varchar(20) NOT NULL,
  nurse_id bigint NOT NULL,
  general_condition varchar(50) NOT NULL,
  pain_score int NOT NULL,
  remarks text DEFAULT NULL,
  doctor_notified boolean NOT NULL DEFAULT false,
  doctor_name varchar(100) DEFAULT NULL,
  doctor_advice text DEFAULT NULL,
  patient_response varchar(50) NOT NULL,
  status varchar(20) NOT NULL,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_npn_admission (admission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS nursing_procedure (
  id bigint NOT NULL AUTO_INCREMENT,
  progress_note_id bigint NOT NULL,
  hospital_id bigint NOT NULL,
  procedure_name varchar(150) NOT NULL,
  performed_by bigint NOT NULL,
  performed_time datetime NOT NULL,
  remarks text DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_nproc_parent FOREIGN KEY (progress_note_id) REFERENCES nursing_progress_note (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS shift_handover (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  admission_id bigint NOT NULL,
  shift varchar(20) NOT NULL,
  outgoing_nurse_id bigint NOT NULL,
  incoming_nurse_id bigint NOT NULL,
  pending_tasks text DEFAULT NULL,
  critical_alerts text DEFAULT NULL,
  meds_due text DEFAULT NULL,
  investigations_pending text DEFAULT NULL,
  doctor_review_pending boolean NOT NULL DEFAULT false,
  created_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sh_admission (admission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS doctor_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    frequency VARCHAR(20),
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    CONSTRAINT fk_do_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id)
);

-- Phase 0.3 additive migration:
-- ALTER TABLE nurse_tasks MODIFY COLUMN doctor_order_id BIGINT DEFAULT NULL;
-- ALTER TABLE nurse_tasks ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'DOCTOR_ORDER';
-- ALTER TABLE nurse_tasks ADD COLUMN task_type VARCHAR(50) DEFAULT NULL;
-- ALTER TABLE nurse_tasks ADD COLUMN missed_reason TEXT DEFAULT NULL;
-- UPDATE nurse_tasks SET source = 'DOCTOR_ORDER' WHERE source IS NULL;

CREATE TABLE IF NOT EXISTS nurse_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_order_id BIGINT DEFAULT NULL,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    scheduled_at DATETIME,
    executed_at DATETIME,
    executed_by BIGINT,
    executed_by_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    administered_quantity DECIMAL(5,2) NULL,
    route VARCHAR(50) NULL,
    injection_site VARCHAR(100) NULL,
    pre_vitals VARCHAR(255) NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'DOCTOR_ORDER',
    task_type VARCHAR(50) DEFAULT NULL,
    missed_reason TEXT DEFAULT NULL,
    CONSTRAINT fk_nt_order FOREIGN KEY (doctor_order_id) REFERENCES doctor_orders(id)
);

-- =====================================================
-- LABORATORY WORKFLOW (Step 9)
-- =====================================================

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
    verified_at DATETIME NULL,
    released_by_name VARCHAR(100) NULL,
    released_at DATETIME NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    critical_alert_sent_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_lr_lab_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders(id)
) ENGINE=InnoDB;
-- Form 27 BR-4/5/6 additive migration (safe on live data):
-- ALTER TABLE lab_results
--   ADD COLUMN verified_at DATETIME NULL, ADD COLUMN released_by_name VARCHAR(100) NULL,
--   ADD COLUMN released_at DATETIME NULL, ADD COLUMN is_critical BOOLEAN NOT NULL DEFAULT FALSE,
--   ADD COLUMN critical_alert_sent_at DATETIME NULL;
-- lab_orders.status now also takes VERIFIED / RELEASED values (existing VARCHAR column, no ALTER needed).

-- Indexes for lab workflow queries
CREATE INDEX IF NOT EXISTS idx_lab_orders_hospital_id ON lab_orders(hospital_id);
CREATE INDEX IF NOT EXISTS idx_lab_orders_ipd_admission ON lab_orders(ipd_admission_id);
CREATE INDEX IF NOT EXISTS idx_lab_orders_patient ON lab_orders(patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_results_order ON lab_results(lab_order_id);

-- =====================================================
-- RADIOLOGY WORKFLOW (Step 10)
-- =====================================================

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
    radiology_test_master_id BIGINT DEFAULT NULL,
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
    verified_at DATETIME NULL,
    released_by_name VARCHAR(100) NULL,
    released_at DATETIME NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    critical_alert_sent_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_rr_radiology_order FOREIGN KEY (radiology_order_id) REFERENCES radiology_orders(id)
) ENGINE=InnoDB;
-- Form 28 BR-4/5/6 additive migration (safe on live data):
-- ALTER TABLE radiology_results
--   ADD COLUMN verified_at DATETIME NULL, ADD COLUMN released_by_name VARCHAR(100) NULL,
--   ADD COLUMN released_at DATETIME NULL, ADD COLUMN is_critical BOOLEAN NOT NULL DEFAULT FALSE,
--   ADD COLUMN critical_alert_sent_at DATETIME NULL;
-- radiology_orders.status now also takes VERIFIED / RELEASED values (existing VARCHAR column, no ALTER needed).

-- Indexes for radiology queries
CREATE INDEX IF NOT EXISTS idx_radiology_orders_hospital_id ON radiology_orders(hospital_id);
CREATE INDEX IF NOT EXISTS idx_radiology_orders_ipd_admission ON radiology_orders(ipd_admission_id);
CREATE INDEX IF NOT EXISTS idx_radiology_orders_patient ON radiology_orders(patient_id);
CREATE INDEX IF NOT EXISTS idx_radiology_results_order ON radiology_results(radiology_order_id);

-- =====================================================
-- DOCTOR ROUNDS WORKFLOW (Step 12)
-- =====================================================

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
    assessment_type VARCHAR(30) NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    amended_from_id BIGINT NULL,
    amendment_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dr_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id),
    CONSTRAINT fk_dr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
-- Forms 11/13 additive migration (safe on live data; null status on legacy rows = implicitly SIGNED):
-- ALTER TABLE doctor_rounds
--   ADD COLUMN assessment_type VARCHAR(30) NULL, ADD COLUMN status VARCHAR(20) NULL,
--   ADD COLUMN signed_by VARCHAR(100) NULL, ADD COLUMN signed_at DATETIME NULL,
--   ADD COLUMN amended_from_id BIGINT NULL, ADD COLUMN amendment_reason VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_dr_ipd_hosp ON doctor_rounds(ipd_admission_id, hospital_id);

-- =====================================================
-- OPERATION THEATRE WORKFLOW (Step 13)
-- =====================================================

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

-- Phase 4.01 Operation Record (Form 18 core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS operation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    surgeon_id BIGINT NULL,
    procedure_name VARCHAR(200) NULL,
    actual_procedure TEXT NULL,
    operative_findings TEXT NULL,
    estimated_blood_loss VARCHAR(100) NULL,
    complications_summary TEXT NULL,
    post_op_plan TEXT NULL,
    operation_start DATETIME NULL,
    operation_end DATETIME NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oprec_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_oprec_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_operation_record_hospital_booking ON operation_record(hospital_id, ot_booking_id);

-- Phase 4.02 Anaesthesia Record (Form 19 core / AIMS). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS anaesthesia_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    anaesthesiologist_id BIGINT NULL,
    anaesthesia_type VARCHAR(100) NULL,
    asa_grade VARCHAR(10) NULL,
    airway_type VARCHAR(100) NULL,
    ventilation_mode VARCHAR(100) NULL,
    induction_time DATETIME NULL,
    completion_time DATETIME NULL,
    notes TEXT NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_anaes_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_anaes_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_anaesthesia_record_hospital_booking ON anaesthesia_record(hospital_id, ot_booking_id);

-- Phase 4.03 PACU / Recovery Record (Form 20 core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS pacu_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    recovery_start DATETIME NULL,
    recovery_end DATETIME NULL,
    recovery_bed VARCHAR(20) NULL,
    consciousness VARCHAR(20) NULL,
    orientation VARCHAR(20) NULL,
    airway_status VARCHAR(20) NULL,
    breathing_status VARCHAR(20) NULL,
    circulation_status VARCHAR(20) NULL,
    nausea_severity VARCHAR(20) NULL,
    vomiting_present BIT(1) NULL,
    pain_score INT NULL,
    aldrete_activity INT NULL,
    aldrete_respiration INT NULL,
    aldrete_circulation INT NULL,
    aldrete_consciousness INT NULL,
    aldrete_oxygen INT NULL,
    aldrete_score INT NULL,
    transfer_destination VARCHAR(30) NULL,
    handover_notes TEXT NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pacu_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_pacu_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_pacu_record_hospital_booking ON pacu_record(hospital_id, ot_booking_id);

-- Phase 4.04 Clinical Handover (Form 22 core; OT/PACU -> ward). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS clinical_handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    from_department VARCHAR(50) NULL,
    to_department VARCHAR(50) NULL,
    transport_mode VARCHAR(30) NULL,
    transport_staff VARCHAR(100) NULL,
    transfer_time DATETIME NULL,
    accepted_time DATETIME NULL,
    devices TEXT NULL,
    monitoring_plan TEXT NULL,
    pending_tasks TEXT NULL,
    remarks TEXT NULL,
    handover_by VARCHAR(100) NULL,
    accepted_by VARCHAR(100) NULL,
    status VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_handover_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_handover_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_clinical_handover_hospital_booking ON clinical_handover(hospital_id, ot_booking_id);

-- Phase 4.05 Post-operative Orders (Form 21 core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS postoperative_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    surgeon_id BIGINT NULL,
    postop_diagnosis VARCHAR(250) NULL,
    post_condition VARCHAR(30) NULL,
    diet_order VARCHAR(30) NULL,
    activity_order VARCHAR(30) NULL,
    medications TEXT NULL,
    monitoring_plan TEXT NULL,
    investigations TEXT NULL,
    escalation_instructions TEXT NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_postop_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_postop_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_postop_orders_hospital_booking ON postoperative_orders(hospital_id, ot_booking_id);

-- Phase 4.06 Instrument/Swab/Needle Count (Form 23 core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS ot_instrument_count (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NULL,
    ot_booking_id BIGINT NOT NULL UNIQUE,
    scrub_nurse VARCHAR(100) NULL,
    circulating_nurse VARCHAR(100) NULL,
    count_summary TEXT NULL,
    initial_count_status VARCHAR(20) NULL,
    final_count_status VARCHAR(20) NULL,
    discrepancy_found BIT(1) NULL,
    resolved BIT(1) NULL,
    search_conducted BIT(1) NULL,
    xray_performed BIT(1) NULL,
    resolution_remarks TEXT NULL,
    completed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_instcount_booking FOREIGN KEY (ot_booking_id) REFERENCES ot_bookings(id),
    CONSTRAINT fk_instcount_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_instrument_count_hospital_booking ON ot_instrument_count(hospital_id, ot_booking_id);

-- Table structure for table `patient_implant` (Form 24 — Implant Record)
CREATE TABLE IF NOT EXISTS patient_implant (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id         BIGINT NOT NULL,
    patient_id          BIGINT,
    ipd_admission_id    BIGINT NOT NULL,
    ot_booking_id       BIGINT NOT NULL,
    inventory_item_id   BIGINT,
    implant_name        VARCHAR(150),
    manufacturer        VARCHAR(100),
    model_number        VARCHAR(50),
    catalog_number      VARCHAR(50),
    batch_number        VARCHAR(30),
    lot_number          VARCHAR(30),
    serial_number       VARCHAR(40),
    udi                 VARCHAR(100),
    expiry_date         DATE,
    quantity_opened     INT,
    quantity_implanted  INT,
    quantity_returned   INT,
    quantity_wasted     INT,
    implant_location    VARCHAR(100),
    warranty_card_number VARCHAR(50),
    patient_card_issued  BOOLEAN,
    nurse_sig           TEXT,
    surgeon_sig         TEXT,
    signed_by           BIGINT,
    signed_at           DATETIME(6),
    status              VARCHAR(20),
    recorded_by         BIGINT,
    recorded_by_name    VARCHAR(100),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    CONSTRAINT fk_implant_booking  FOREIGN KEY (ot_booking_id)    REFERENCES ot_bookings(id),
    CONSTRAINT fk_implant_hospital FOREIGN KEY (hospital_id)      REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_implant_hospital_booking  ON patient_implant(hospital_id, ot_booking_id);
CREATE INDEX IF NOT EXISTS idx_implant_hospital_patient  ON patient_implant(hospital_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_implant_hospital_batch    ON patient_implant(hospital_id, batch_number);

-- Table structure for table `ot_readiness` (Form 26 — OT Readiness Checklist)
CREATE TABLE IF NOT EXISTS ot_readiness (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id    BIGINT NOT NULL,
    ot_room        VARCHAR(50) NOT NULL,
    readiness_date DATE NOT NULL,
    cleaning_done  BOOLEAN,
    sterility_done BOOLEAN,
    equipment_ok   BOOLEAN,
    status         VARCHAR(20) NOT NULL,
    verified_by    VARCHAR(100),
    verified_at    DATETIME(6),
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_readiness_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_readiness_hospital_room_date ON ot_readiness(hospital_id, ot_room, readiness_date);

-- Table structure for table `patient_consent` (Form 05 — General & Central Consent Engine)
CREATE TABLE IF NOT EXISTS patient_consent (
  id bigint NOT NULL AUTO_INCREMENT,
  public_id varchar(36) NOT NULL UNIQUE,
  hospital_id bigint NOT NULL,
  patient_id bigint NOT NULL,
  admission_id bigint DEFAULT NULL,
  encounter_type varchar(20) NOT NULL,
  consent_type varchar(30) NOT NULL,
  version int NOT NULL,
  parent_id bigint DEFAULT NULL,
  status varchar(20) NOT NULL,
  patient_signed boolean NOT NULL DEFAULT false,
  guardian_signed boolean NOT NULL DEFAULT false,
  relationship varchar(40) DEFAULT NULL,
  signature_type varchar(30) DEFAULT NULL,
  witness_id bigint DEFAULT NULL,
  language varchar(20) NOT NULL,
  interpreter_id bigint DEFAULT NULL,
  signed_at datetime DEFAULT NULL,
  remarks text DEFAULT NULL,
  created_by bigint DEFAULT NULL,
  created_at datetime NOT NULL,
  updated_at datetime NOT NULL,
  is_deleted boolean NOT NULL DEFAULT false,
  PRIMARY KEY (id),
  KEY idx_consent_patient (patient_id),
  KEY idx_consent_admission (admission_id),
  KEY idx_consent_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table structure for table `blood_consent_detail` (Form 01 — Blood Transfusion Consent)
CREATE TABLE IF NOT EXISTS blood_consent_detail (
  id bigint NOT NULL AUTO_INCREMENT,
  consent_id bigint NOT NULL UNIQUE,
  explanation_given boolean NOT NULL DEFAULT false,
  witness_patient_name varchar(100) DEFAULT NULL,
  witness_patient_signed boolean NOT NULL DEFAULT false,
  witness_hospital_name varchar(100) DEFAULT NULL,
  witness_hospital_signed boolean NOT NULL DEFAULT false,
  interpreter_required boolean NOT NULL DEFAULT false,
  interpreter_language varchar(40) DEFAULT NULL,
  interpreter_name varchar(100) DEFAULT NULL,
  interpreter_signed boolean NOT NULL DEFAULT false,
  blood_request_id bigint DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_blood_consent_parent FOREIGN KEY (consent_id) REFERENCES patient_consent (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Table structure for table `signature_slots` (Centralized Signature Slots)
CREATE TABLE IF NOT EXISTS signature_slots (
  id bigint NOT NULL AUTO_INCREMENT,
  hospital_id bigint NOT NULL,
  signer_role varchar(30) NOT NULL,
  signer_name varchar(100) NOT NULL,
  signer_relationship varchar(50) DEFAULT NULL,
  signed_at datetime NOT NULL,
  document_type varchar(50) NOT NULL,
  document_id varchar(50) NOT NULL,
  signature_image_base64 longtext DEFAULT NULL,
  signature_hash varchar(64) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_sig_doc (document_type, document_id),
  KEY idx_sig_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 5.01 Pre-Anaesthesia Assessment (Form 15 core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS pre_anaesthesia_assessment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    admission_id BIGINT NOT NULL UNIQUE,
    ot_booking_id BIGINT NULL,
    asa_class VARCHAR(10) NULL,
    airway_assessment TEXT NULL,
    systemic_assessment TEXT NULL,
    fitness_status VARCHAR(30) NULL,
    planned_anaesthesia VARCHAR(100) NULL,
    remarks TEXT NULL,
    status VARCHAR(20) NULL,
    signed_by VARCHAR(100) NULL,
    signed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pac_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_pac_hospital_admission ON pre_anaesthesia_assessment(hospital_id, admission_id);
CREATE INDEX IF NOT EXISTS idx_pac_hospital_fitness ON pre_anaesthesia_assessment(hospital_id, fitness_status);

-- Form 12 Emergency Visit (EIS core). Additive; Hibernate ddl-auto creates it.
CREATE TABLE IF NOT EXISTS emergency_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    emergency_number VARCHAR(30) NULL,
    arrival_time DATETIME NULL,
    arrival_mode VARCHAR(30) NULL,
    triage_level VARCHAR(10) NULL,
    triage_criteria VARCHAR(255) NULL,
    triaged_by VARCHAR(100) NULL,
    triaged_at DATETIME NULL,
    is_mlc BIT(1) NULL,
    mlc_number VARCHAR(50) NULL,
    chief_complaint TEXT NULL,
    airway_status VARCHAR(30) NULL,
    breathing_status VARCHAR(30) NULL,
    circulation_status VARCHAR(30) NULL,
    gcs_score INT NULL,
    initial_diagnosis TEXT NULL,
    assessed_by VARCHAR(100) NULL,
    assessed_at DATETIME NULL,
    disposition VARCHAR(30) NULL,
    ipd_admission_id BIGINT NULL,
    status VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_er_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_er_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
) ENGINE=InnoDB;
CREATE INDEX IF NOT EXISTS idx_er_hospital_status_triage ON emergency_visit(hospital_id, status, triage_level);

-- Table structure for table `mrd_records`



DROP TABLE IF EXISTS `mrd_records`;
CREATE TABLE `mrd_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `hospital_id` BIGINT NOT NULL,
    `ipd_admission_id` BIGINT NOT NULL UNIQUE,
    `mrd_number` VARCHAR(100) NOT NULL UNIQUE,
    `rack_location` VARCHAR(200) NOT NULL,
    `status` VARCHAR(50) NOT NULL DEFAULT 'ARCHIVED',
    `archived_at` DATETIME NOT NULL,
    `archived_by_id` BIGINT NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_mrd_ipd` FOREIGN KEY (`ipd_admission_id`) REFERENCES `ipd_admission` (`id`),
    CONSTRAINT `fk_mrd_hospital` FOREIGN KEY (`hospital_id`) REFERENCES `hospitals` (`id`),
    CONSTRAINT `fk_mrd_user` FOREIGN KEY (`archived_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE INDEX `idx_mrd_hospital` ON `mrd_records` (`hospital_id`);

-- =====================================================
-- MASTER DATA ARCHITECTURE (Step 16)
-- =====================================================

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

-- CDSS Tables
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
