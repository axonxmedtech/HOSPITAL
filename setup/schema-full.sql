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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5poa40gpt44a152gibdlfe6sb` (`ipd_admission_id`),
  KEY `idx_discharge_summary_hospital` (`hospital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `doctors`
--

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

CREATE TABLE IF NOT EXISTS vital_signs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipd_admission_id BIGINT NOT NULL,
    hospital_id BIGINT NOT NULL,
    blood_pressure VARCHAR(20),
    pulse INT,
    temperature DECIMAL(4,1),
    spo2 INT,
    respiratory_rate int DEFAULT NULL,
    recorded_by BIGINT,
    recorded_by_name VARCHAR(100),
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_vs_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id)
);

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

CREATE TABLE IF NOT EXISTS nurse_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_order_id BIGINT NOT NULL,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_lr_lab_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders(id)
) ENGINE=InnoDB;

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id),
    CONSTRAINT fk_rr_radiology_order FOREIGN KEY (radiology_order_id) REFERENCES radiology_orders(id)
) ENGINE=InnoDB;

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dr_ipd FOREIGN KEY (ipd_admission_id) REFERENCES ipd_admission(id),
    CONSTRAINT fk_dr_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

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
