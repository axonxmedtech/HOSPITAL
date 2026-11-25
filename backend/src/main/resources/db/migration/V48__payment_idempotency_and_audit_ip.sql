-- Launch readiness.
-- 1. billing_payments.idempotency_key: a retried payment (double-click, resend after a dropped
--    response) carries the same key and is answered from the payment already recorded, so the
--    money is never taken twice. Unique per bill; MySQL allows any number of NULLs, so payments
--    recorded without a key are unaffected.
-- 2. audit_logs.client_ip: the admin audit trail records where a change came from, as the
--    patient access log already does. performed_by_role has been written by the app for a while
--    but only ever created by Hibernate; it is added here too when missing.
--
-- Additive only. Idempotent: DatabaseMigrationRunner.ensureLaunchReadinessColumns does the same on
-- non-Flyway (dev) profiles. Must run after V47 (Flyway out-of-order is off in prod).

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_payments' AND COLUMN_NAME = 'idempotency_key') > 0,
  'SELECT 1', 'ALTER TABLE billing_payments ADD COLUMN idempotency_key VARCHAR(64) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_payments' AND INDEX_NAME = 'uk_billing_payments_idempotency') > 0,
  'SELECT 1', 'ALTER TABLE billing_payments ADD UNIQUE INDEX uk_billing_payments_idempotency (billing_id, idempotency_key)'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND COLUMN_NAME = 'client_ip') > 0,
  'SELECT 1', 'ALTER TABLE audit_logs ADD COLUMN client_ip VARCHAR(45) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND COLUMN_NAME = 'performed_by_role') > 0,
  'SELECT 1', 'ALTER TABLE audit_logs ADD COLUMN performed_by_role VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
