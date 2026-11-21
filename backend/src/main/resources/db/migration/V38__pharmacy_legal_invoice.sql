-- Pharmacy invoices that meet GST and drug-licence rules.
--
-- 1. hospitals.gstin / drug_license_number: printed on every pharmacy bill.
-- 2. medicine_master.hsn_code: printed per line on the tax invoice.
-- 3. pharmacy_invoice_sequences: one consecutive series per facility (or branch) per financial
--    year, so bills read PH/26-27/00001, PH/26-27/00002 ... instead of a timestamp.
-- 4. pharmacy_sales.invoice_number becomes unique per facility rather than across all
--    facilities (two pharmacies may both have PH/26-27/00001). Existing PHB-<timestamp> numbers
--    are untouched and stay unique.
-- 5. pharmacy_sale_returns: one row per customer refund (money and the GST in it), so reports
--    show refunds; before this a refund left no amount anywhere and reports showed Rs 0.
-- 6. Purchases: paid amount and cancellation (reason / when / who) on purchase_invoices, a
--    purchase_payments ledger, and numbered supplier return notes (SR/26-27/00001) with lines.
--
-- Guarded like V17: a database bootstrapped from setup/schema-full.sql may already have these.

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'gstin') > 0,
  'SELECT 1', 'ALTER TABLE hospitals ADD COLUMN gstin VARCHAR(15) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'drug_license_number') > 0,
  'SELECT 1', 'ALTER TABLE hospitals ADD COLUMN drug_license_number VARCHAR(120) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medicine_master') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medicine_master' AND COLUMN_NAME = 'hsn_code') > 0,
  'SELECT 1', 'ALTER TABLE medicine_master ADD COLUMN hsn_code VARCHAR(10) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS pharmacy_invoice_sequences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    series VARCHAR(20) NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pharmacy_invoice_series (hospital_id, series)
);

-- Drop any unique index that covers invoice_number alone (its name differs between databases
-- created by Hibernate and by schema-full.sql) ...
SET @idx := (SELECT INDEX_NAME FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pharmacy_sales' AND NON_UNIQUE = 0
    AND INDEX_NAME <> 'PRIMARY'
  GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME) = 'invoice_number' LIMIT 1);
SET @sql := IF(@idx IS NULL, 'SELECT 1', CONCAT('ALTER TABLE pharmacy_sales DROP INDEX `', @idx, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ... and make it unique per facility instead.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pharmacy_sales') = 0
  OR (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pharmacy_sales'
     AND INDEX_NAME = 'uk_pharmacy_sales_hospital_invoice') > 0,
  'SELECT 1',
  'ALTER TABLE pharmacy_sales ADD UNIQUE KEY uk_pharmacy_sales_hospital_invoice (hospital_id, invoice_number)'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Customer refunds (append-only).
CREATE TABLE IF NOT EXISTS pharmacy_sale_returns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    sale_id BIGINT NOT NULL,
    sale_item_id BIGINT NOT NULL,
    medicine_batch_id BIGINT NOT NULL,
    quantity DECIMAL(12,2) NOT NULL,
    refund_amount DECIMAL(12,2) NOT NULL,
    gst_amount DECIMAL(12,2) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_psr_hospital_date (hospital_id, created_at),
    KEY idx_psr_sale (sale_id)
);

-- Purchases: payments and cancellation.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'paid_amount') > 0,
  'SELECT 1', 'ALTER TABLE purchase_invoices ADD COLUMN paid_amount DECIMAL(12,2) NULL DEFAULT 0'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'cancel_reason') > 0,
  'SELECT 1', 'ALTER TABLE purchase_invoices ADD COLUMN cancel_reason VARCHAR(255) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'cancelled_at') > 0,
  'SELECT 1', 'ALTER TABLE purchase_invoices ADD COLUMN cancelled_at DATETIME(6) NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices') = 0
  OR (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_invoices' AND COLUMN_NAME = 'cancelled_by') > 0,
  'SELECT 1', 'ALTER TABLE purchase_invoices ADD COLUMN cancelled_by BIGINT NULL'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS purchase_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    purchase_invoice_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    reference VARCHAR(60) NULL,
    paid_on DATE NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pp_invoice (purchase_invoice_id)
);

CREATE TABLE IF NOT EXISTS supplier_return_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    note_number VARCHAR(20) NOT NULL,
    supplier_id BIGINT NOT NULL,
    total_claimed DECIMAL(12,2) NOT NULL,
    reason VARCHAR(255) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_return_note_number (hospital_id, note_number)
);

CREATE TABLE IF NOT EXISTS supplier_return_note_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    medicine_batch_id BIGINT NOT NULL,
    batch_number VARCHAR(60) NULL,
    medicine_name VARCHAR(200) NULL,
    quantity DECIMAL(12,2) NOT NULL,
    rate DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_srni_note (note_id)
);
