-- V4_whatsapp_tables.sql
-- Creates whatsapp_config and whatsapp_message_log tables.
-- Applied automatically on startup via DatabaseMigrationRunner.java.

CREATE TABLE IF NOT EXISTS whatsapp_config (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id          BIGINT       NOT NULL,
  access_token         VARCHAR(500) NOT NULL,
  phone_number_id      VARCHAR(100) NOT NULL,
  waba_id              VARCHAR(100) DEFAULT NULL,
  is_active            TINYINT(1)   NOT NULL DEFAULT 1,
  send_appointments    TINYINT(1)   NOT NULL DEFAULT 1,
  send_billing         TINYINT(1)   NOT NULL DEFAULT 1,
  send_case_papers     TINYINT(1)   NOT NULL DEFAULT 1,
  send_prescription    TINYINT(1)   NOT NULL DEFAULT 1,
  send_medicine_list   TINYINT(1)   NOT NULL DEFAULT 1,
  created_at           DATETIME(6)  NOT NULL,
  updated_at           DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_wc_hospital UNIQUE (hospital_id),
  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS whatsapp_message_log (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  hospital_id      BIGINT       NOT NULL,
  patient_id       BIGINT       DEFAULT NULL,
  patient_phone    VARCHAR(20)  NOT NULL,
  message_type     VARCHAR(50)  NOT NULL,
  status           VARCHAR(25)  NOT NULL,
  error_message    VARCHAR(500) DEFAULT NULL,
  retry_count      INT          NOT NULL DEFAULT 0,
  next_retry_at    DATETIME(6)  DEFAULT NULL,
  sent_at          DATETIME(6)  DEFAULT NULL,
  created_at       DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_wml_hospital_status (hospital_id, status),
  KEY idx_wml_retry (status, next_retry_at)
);
