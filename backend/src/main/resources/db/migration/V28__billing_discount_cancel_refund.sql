-- Billing: discount / concession, bill cancellation, and a refund-aware payment ledger.
--
-- billing.discount_*      : net payable = gross charges - discount (gross is never rewritten).
-- billing.cancel*         : a CANCELLED bill records why, by whom and when.
-- billing_payments.kind   : PAYMENT (money in) or REFUND (money out, stored as a negative amount),
--                          so SUM(amount) stays 'money actually collected'.
-- billing_payments.recorded_by / note : who took the money, for the daily cash-closing report.
--
-- Idempotent: DatabaseMigrationRunner adds the same columns on non-Flyway (dev) profiles.

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'discount_amount');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE billing ADD COLUMN discount_amount DECIMAL(10,2) NULL', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'discount_reason');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE billing ADD COLUMN discount_reason VARCHAR(255) NULL', 'DO 0');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'discounted_by');
SET @s3 := IF(@c3 = 0, 'ALTER TABLE billing ADD COLUMN discounted_by VARCHAR(150) NULL', 'DO 0');
PREPARE p3 FROM @s3;
EXECUTE p3;
DEALLOCATE PREPARE p3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'cancel_reason');
SET @s4 := IF(@c4 = 0, 'ALTER TABLE billing ADD COLUMN cancel_reason VARCHAR(255) NULL', 'DO 0');
PREPARE p4 FROM @s4;
EXECUTE p4;
DEALLOCATE PREPARE p4;

SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'cancelled_by');
SET @s5 := IF(@c5 = 0, 'ALTER TABLE billing ADD COLUMN cancelled_by VARCHAR(150) NULL', 'DO 0');
PREPARE p5 FROM @s5;
EXECUTE p5;
DEALLOCATE PREPARE p5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND COLUMN_NAME = 'cancelled_at');
SET @s6 := IF(@c6 = 0, 'ALTER TABLE billing ADD COLUMN cancelled_at DATETIME(6) NULL', 'DO 0');
PREPARE p6 FROM @s6;
EXECUTE p6;
DEALLOCATE PREPARE p6;

SET @c7 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_payments' AND COLUMN_NAME = 'kind');
SET @s7 := IF(@c7 = 0, 'ALTER TABLE billing_payments ADD COLUMN kind VARCHAR(10) NULL DEFAULT ''PAYMENT''', 'DO 0');
PREPARE p7 FROM @s7;
EXECUTE p7;
DEALLOCATE PREPARE p7;

SET @c8 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_payments' AND COLUMN_NAME = 'recorded_by');
SET @s8 := IF(@c8 = 0, 'ALTER TABLE billing_payments ADD COLUMN recorded_by VARCHAR(150) NULL', 'DO 0');
PREPARE p8 FROM @s8;
EXECUTE p8;
DEALLOCATE PREPARE p8;

SET @c9 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_payments' AND COLUMN_NAME = 'note');
SET @s9 := IF(@c9 = 0, 'ALTER TABLE billing_payments ADD COLUMN note VARCHAR(255) NULL', 'DO 0');
PREPARE p9 FROM @s9;
EXECUTE p9;
DEALLOCATE PREPARE p9;

-- Existing ledger rows are all receipts.
UPDATE billing_payments SET kind = 'PAYMENT' WHERE kind IS NULL;
