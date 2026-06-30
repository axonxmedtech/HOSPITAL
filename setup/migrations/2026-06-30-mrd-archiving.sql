-- Database migration for MRD (Medical Records Department) Archiving & Locking
-- File: setup/migrations/2026-06-30-mrd-archiving.sql

CREATE TABLE IF NOT EXISTS `mrd_records` (
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
