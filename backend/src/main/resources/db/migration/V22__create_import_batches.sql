-- Legacy patient import, foundation (phase 1 of the v2 port): the batch ledger.
--
-- An import_batches row is the unit an administrator sees, retries, undoes and audits. One row
-- per commit attempt, hospital-owned, identified outward by public_id. import_row_results holds
-- the per-row outcome of a batch: every row that was not simply CREATED/UPDATED gets a stable
-- machine-readable reason_code, so the administrator learns exactly what happened to row 57
-- instead of "the import failed". raw_row_json is kept ONLY for rows that need a human
-- (NEEDS_REVIEW / FAILED) because it is the input the correction loop re-uploads; it is patient
-- data at rest and is subject to the 90-day retention decision, enforced in a later phase.
--
-- Tenancy: hospital_id is stored on the batch and FK-bound to hospitals with ON DELETE CASCADE,
-- the same shape as recovery_bays (V13). That is what makes the existing tenant purge
-- (PlatformHospitalService.deleteHospital ends with DELETE FROM hospitals) remove a tenant's
-- import history without a code change: batches cascade from hospitals, results cascade from
-- batches.
--
-- No FK from matched_patient_id to patients, deliberately: a result row is history ("this row
-- matched PAT17 at the time") and must survive that patient later being purged or merged.
--
-- Idempotent by construction (CREATE TABLE IF NOT EXISTS), for the reason V12/V13 explain: a
-- database that booted this build once with Flyway disabled already has these tables from
-- Hibernate ddl-auto, and a plain CREATE would record a failed migration that blocks every later
-- one. Existing-data safe: nothing here touches an existing table.

CREATE TABLE IF NOT EXISTS import_batches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  hospital_id BIGINT NOT NULL,
  entity_type VARCHAR(20) NOT NULL,
  source_filename VARCHAR(255) DEFAULT NULL,
  sheet_name VARCHAR(255) DEFAULT NULL,
  mapping_json TEXT DEFAULT NULL,
  file_sha256 CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(255) DEFAULT NULL,
  total_rows INT NOT NULL DEFAULT 0,
  created_count INT NOT NULL DEFAULT 0,
  updated_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  needs_review_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  created_by VARCHAR(100) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  heartbeat_at DATETIME(6) DEFAULT NULL,
  committed_at DATETIME(6) DEFAULT NULL,
  undone_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_import_batch_public_id (public_id),
  KEY idx_import_batch_hospital_status (hospital_id, status),
  KEY idx_import_batch_hospital_sha (hospital_id, file_sha256, created_at),
  CONSTRAINT FK_import_batch_hospital FOREIGN KEY (hospital_id)
    REFERENCES hospitals (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS import_row_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  row_num INT NOT NULL,
  state VARCHAR(16) NOT NULL,
  reason_code VARCHAR(40) DEFAULT NULL,
  column_name VARCHAR(120) DEFAULT NULL,
  message VARCHAR(500) DEFAULT NULL,
  phone_masked VARCHAR(15) DEFAULT NULL,
  matched_patient_id BIGINT DEFAULT NULL,
  raw_row_json TEXT DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_import_row_result_batch_row (batch_id, row_num),
  CONSTRAINT FK_import_row_result_batch FOREIGN KEY (batch_id)
    REFERENCES import_batches (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
