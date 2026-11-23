-- Patient access log: who opened, printed or downloaded which patient's record, and when.
-- Append-only (never updated or deleted) and kept when a facility is purged, like audit_logs.
-- Kept apart from audit_logs: reads far outnumber changes and would bury the change history.
--
-- Idempotent: DatabaseMigrationRunner creates the same table on non-Flyway (dev) profiles.
--
-- Numbered V45 (written as V37): staging and production run Flyway with out-of-order=false, and
-- V38 (pharmacy) and V40-V44 (IPD) reached them first, so any lower number would be refused.

CREATE TABLE IF NOT EXISTS patient_access_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    record_type VARCHAR(30) NOT NULL,
    record_ref VARCHAR(64) NULL,
    action VARCHAR(20) NOT NULL,
    user_id BIGINT NULL,
    user_email VARCHAR(150) NOT NULL,
    user_role VARCHAR(30) NULL,
    client_ip VARCHAR(45) NULL,
    accessed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pal_patient (hospital_id, patient_id, accessed_at),
    KEY idx_pal_user (hospital_id, user_email, accessed_at)
);
