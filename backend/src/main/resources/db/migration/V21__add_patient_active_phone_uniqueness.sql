-- Race-safe enforcement of the patient duplicate-phone invariant (S-PID-D).
--
-- Phase A detects a duplicate phone in the application and asks a human whether the second
-- registration is a different person sharing the number. Two requests can pass that check
-- concurrently and both insert. This migration makes the invariant the database's job.
--
-- active_phone_key is the phone number ONLY while the row actually competes for it:
--   * an inactive (soft-deleted) patient competes for nothing            -> NULL
--   * a patient acknowledged FOR ITS CURRENT PHONE is a known sharer      -> NULL
--   * everyone else                                                      -> the phone
-- MySQL allows repeated NULLs in a unique index, which is what lets acknowledged sharers and
-- released numbers coexist while unacknowledged actives cannot collide.
--
-- The acknowledgement test is `ack_for <> phone`, not `ack_for IS NOT NULL`: an acknowledgement
-- recorded for a DIFFERENT number must not exempt the number the row holds now. The application
-- clears a stale acknowledgement before re-checking, and this expression means the invariant
-- holds even if some future write path forgets to.
--
-- This migration NEVER edits patient data. It does not merge, deactivate, delete or acknowledge
-- anything. If conflicting rows exist it FAILS, and a human resolves them (see
-- docs/operations/patient-duplicate-phone-preflight.sql).

-- The acknowledgement columns arrived through DatabaseMigrationRunner, not Flyway, so a database
-- whose Flyway history reaches this point without the runner having run must still get them.
-- MySQL has no ADD COLUMN IF NOT EXISTS; this is the standard conditional-DDL workaround.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE patients ADD COLUMN duplicate_phone_ack_for VARCHAR(15) DEFAULT NULL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'duplicate_phone_ack_for');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE patients ADD COLUMN duplicate_phone_ack_at DATETIME(6) DEFAULT NULL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'duplicate_phone_ack_at');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE patients ADD COLUMN duplicate_phone_ack_by VARCHAR(100) DEFAULT NULL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'duplicate_phone_ack_by');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- VIRTUAL, not STORED: indexable in MySQL 8 and needs no table rewrite. hospital_id is the
-- leading index column and is deliberately NOT folded into the key.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE patients ADD COLUMN active_phone_key VARCHAR(15) GENERATED ALWAYS AS (CASE WHEN is_active = 1 AND phone IS NOT NULL AND phone <> '''' AND (duplicate_phone_ack_for IS NULL OR duplicate_phone_ack_for <> phone) THEN phone END) VIRTUAL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'active_phone_key');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Deliberately NOT wrapped in a conditional that could swallow a failure: if conflicting rows
-- exist, MySQL raises ER_DUP_ENTRY here naming the offending (hospital_id, phone) pair, the
-- migration fails, and the deployment stops. That is the intended behaviour.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE patients ADD UNIQUE KEY uq_patient_active_phone (hospital_id, active_phone_key)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND INDEX_NAME = 'uq_patient_active_phone');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
