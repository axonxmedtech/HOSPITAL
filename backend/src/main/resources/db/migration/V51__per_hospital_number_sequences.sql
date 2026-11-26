-- Per-hospital patient (PAT…) and bill (BIL…) numbers (TenantCustomIdAllocator).
-- Before this, a new number was built from the row's global auto-increment id, so one hospital's
-- registrations moved every other hospital's numbers (hospital A's PAT12, then hospital B's PAT13).
-- Each hospital now has its own counter per kind. Existing numbers are never changed: each
-- existing hospital continues right after its own highest plain number; a hospital with none
-- starts at 1.
--
-- Additive only. Idempotent (INSERT IGNORE): DatabaseMigrationRunner.ensureTenantNumberSequences
-- does the same on non-Flyway (dev) profiles. Must run after V50 (Flyway out-of-order is off in prod).
CREATE TABLE IF NOT EXISTS tenant_number_sequences (
  id BIGINT NOT NULL AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  next_value BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_number_sequences (hospital_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO tenant_number_sequences (hospital_id, kind, next_value, updated_at)
SELECT h.id, 'PATIENT',
       COALESCE((SELECT MAX(CAST(SUBSTRING(p.custom_id, 4) AS UNSIGNED)) FROM patients p
                 WHERE p.hospital_id = h.id AND p.custom_id REGEXP '^PAT[1-9][0-9]{0,17}$'), 0) + 1,
       CURRENT_TIMESTAMP(6)
FROM hospitals h;

INSERT IGNORE INTO tenant_number_sequences (hospital_id, kind, next_value, updated_at)
SELECT h.id, 'BILLING',
       COALESCE((SELECT MAX(CAST(SUBSTRING(b.custom_id, 4) AS UNSIGNED)) FROM billing b
                 WHERE b.hospital_id = h.id AND b.custom_id REGEXP '^BIL[1-9][0-9]{0,17}$'), 0) + 1,
       CURRENT_TIMESTAMP(6)
FROM hospitals h;
