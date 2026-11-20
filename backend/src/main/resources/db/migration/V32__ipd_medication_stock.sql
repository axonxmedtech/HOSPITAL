-- IPD medicines: an order links to the medicine or hospital inventory it is given from; a given
-- dose records what it took from stock and what it added to the bill (so it can be undone); and
-- each hospital chooses which roles may record doses.
--
-- Idempotent: DatabaseMigrationRunner applies the same changes on non-Flyway (dev) profiles.

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescriptions' AND COLUMN_NAME = 'stock_source');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE prescriptions ADD COLUMN stock_source VARCHAR(20) NULL', 'DO 0');
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescriptions' AND COLUMN_NAME = 'hospital_item_name');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE prescriptions ADD COLUMN hospital_item_name VARCHAR(150) NULL', 'DO 0');
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescriptions' AND COLUMN_NAME = 'quantity_per_dose');
SET @s3 := IF(@c3 = 0, 'ALTER TABLE prescriptions ADD COLUMN quantity_per_dose INT NULL', 'DO 0');
PREPARE p3 FROM @s3;
EXECUTE p3;
DEALLOCATE PREPARE p3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'quantity');
SET @s4 := IF(@c4 = 0, 'ALTER TABLE medication_administrations ADD COLUMN quantity INT NULL', 'DO 0');
PREPARE p4 FROM @s4;
EXECUTE p4;
DEALLOCATE PREPARE p4;

SET @c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'own_supply');
SET @s5 := IF(@c5 = 0, 'ALTER TABLE medication_administrations ADD COLUMN own_supply BIT(1) NULL', 'DO 0');
PREPARE p5 FROM @s5;
EXECUTE p5;
DEALLOCATE PREPARE p5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'stock_source');
SET @s6 := IF(@c6 = 0, 'ALTER TABLE medication_administrations ADD COLUMN stock_source VARCHAR(20) NULL', 'DO 0');
PREPARE p6 FROM @s6;
EXECUTE p6;
DEALLOCATE PREPARE p6;

SET @c7 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'charge_amount');
SET @s7 := IF(@c7 = 0, 'ALTER TABLE medication_administrations ADD COLUMN charge_amount DECIMAL(10,2) NULL', 'DO 0');
PREPARE p7 FROM @s7;
EXECUTE p7;
DEALLOCATE PREPARE p7;

SET @c8 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'billing_item_id');
SET @s8 := IF(@c8 = 0, 'ALTER TABLE medication_administrations ADD COLUMN billing_item_id BIGINT NULL', 'DO 0');
PREPARE p8 FROM @s8;
EXECUTE p8;
DEALLOCATE PREPARE p8;

SET @c9 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'billing_medicine_id');
SET @s9 := IF(@c9 = 0, 'ALTER TABLE medication_administrations ADD COLUMN billing_medicine_id BIGINT NULL', 'DO 0');
PREPARE p9 FROM @s9;
EXECUTE p9;
DEALLOCATE PREPARE p9;

SET @c10 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'stock_detail');
SET @s10 := IF(@c10 = 0, 'ALTER TABLE medication_administrations ADD COLUMN stock_detail VARCHAR(1000) NULL', 'DO 0');
PREPARE p10 FROM @s10;
EXECUTE p10;
DEALLOCATE PREPARE p10;

SET @c11 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'undone_at');
SET @s11 := IF(@c11 = 0, 'ALTER TABLE medication_administrations ADD COLUMN undone_at DATETIME(6) NULL', 'DO 0');
PREPARE p11 FROM @s11;
EXECUTE p11;
DEALLOCATE PREPARE p11;

SET @c12 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'undone_by');
SET @s12 := IF(@c12 = 0, 'ALTER TABLE medication_administrations ADD COLUMN undone_by VARCHAR(150) NULL', 'DO 0');
PREPARE p12 FROM @s12;
EXECUTE p12;
DEALLOCATE PREPARE p12;

SET @c13 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations' AND COLUMN_NAME = 'undo_reason');
SET @s13 := IF(@c13 = 0, 'ALTER TABLE medication_administrations ADD COLUMN undo_reason VARCHAR(255) NULL', 'DO 0');
PREPARE p13 FROM @s13;
EXECUTE p13;
DEALLOCATE PREPARE p13;

SET @c14 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospital_settings' AND COLUMN_NAME = 'med_record_roles');
SET @s14 := IF(@c14 = 0, 'ALTER TABLE hospital_settings ADD COLUMN med_record_roles VARCHAR(200) NULL', 'DO 0');
PREPARE p14 FROM @s14;
EXECUTE p14;
DEALLOCATE PREPARE p14;
