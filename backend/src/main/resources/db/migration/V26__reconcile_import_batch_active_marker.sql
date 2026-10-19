-- Flyway V26: Reconcile import_batches active_marker and uniqueness invariant.
--
-- Safety and Invariant Rules:
-- 1. Fail closed if duplicate active import batches exist (status IN ('RUNNING','COMPLETED','PARTIAL')).
--    Zero business records are deleted, merged, or overwritten.
-- 2. Detect and handle both legacy ordinary-column and already-correct generated-column schemas.
-- 3. Invariant: active_marker must be a MySQL VIRTUAL generated column:
--      TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('RUNNING','COMPLETED','PARTIAL') THEN 1 END) VIRTUAL
-- 4. Invariant: unique index uk_import_batch_active must be on:
--      (hospital_id, file_sha256, active_marker)
-- 5. Safe and idempotent across repeated runs.

-- Step 0: Ensure import_batches table exists. If missing on an environment running V26, fail closed immediately.
SET @table_exists := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches'
);

SET @abort_on_missing_table := IF(@table_exists = 0,
    'SELECT * FROM `ERROR_V26_TABLE_IMPORT_BATCHES_DOES_NOT_EXIST_CORRUPT_SCHEMA`',
    'DO 0');
PREPARE stmt_abort_missing FROM @abort_on_missing_table;
EXECUTE stmt_abort_missing;
DEALLOCATE PREPARE stmt_abort_missing;

-- Step 1: Detect duplicate active batches and fail closed without deleting, merging, or overwriting rows
SET @check_dup_sql := IF(@table_exists > 0,
    'SELECT COUNT(*) INTO @dup_active_count FROM (SELECT hospital_id, file_sha256 FROM import_batches WHERE status IN (''RUNNING'', ''COMPLETED'', ''PARTIAL'') GROUP BY hospital_id, file_sha256 HAVING COUNT(*) > 1) _dups',
    'SET @dup_active_count := 0');
PREPARE stmt_check_dup FROM @check_dup_sql;
EXECUTE stmt_check_dup;
DEALLOCATE PREPARE stmt_check_dup;

-- If duplicate active rows exist, fail closed immediately with a descriptive diagnostic table name
SET @abort_on_duplicate := IF(@dup_active_count > 0,
    'SELECT * FROM `ERROR_V26_DUPLICATE_ACTIVE_BATCHES_FAIL_CLOSED`',
    'DO 0');
PREPARE stmt_abort FROM @abort_on_duplicate;
EXECUTE stmt_abort;
DEALLOCATE PREPARE stmt_abort;

-- Step 2: Inspect existing active_marker column definition
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'
);

SET @col_is_generated := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'
      AND EXTRA LIKE '%GENERATED%'
);

-- Step 3: Inspect existing unique index
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active'
);

-- If column is missing: add it as virtual generated column
SET @add_col := IF(@col_exists = 0,
    'ALTER TABLE import_batches ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN (''RUNNING'',''COMPLETED'',''PARTIAL'') THEN 1 END) VIRTUAL',
    'DO 0');
PREPARE stmt_add_col FROM @add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- If column exists as an ordinary column (not virtual generated):
-- In MySQL 8, 'Changing the STORED status' is not permitted via MODIFY COLUMN (ERROR 3106).
-- Dropping the ordinary column and adding the virtual generated column safely converts the column
-- while computing active_marker from the immutable status field without modifying business data.
SET @drop_legacy_idx := IF(@col_exists > 0 AND @col_is_generated = 0 AND @idx_exists > 0,
    'ALTER TABLE import_batches DROP INDEX uk_import_batch_active',
    'DO 0');
PREPARE stmt_drop_idx FROM @drop_legacy_idx;
EXECUTE stmt_drop_idx;
DEALLOCATE PREPARE stmt_drop_idx;

-- Recreate ordinary column as virtual generated column
SET @recreate_col := IF(@col_exists > 0 AND @col_is_generated = 0,
    'ALTER TABLE import_batches DROP COLUMN active_marker, ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN (''RUNNING'',''COMPLETED'',''PARTIAL'') THEN 1 END) VIRTUAL',
    'DO 0');
PREPARE stmt_recreate_col FROM @recreate_col;
EXECUTE stmt_recreate_col;
DEALLOCATE PREPARE stmt_recreate_col;

-- Step 4: Ensure unique index uk_import_batch_active (hospital_id, file_sha256, active_marker) exists
SET @idx_exists_now := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active'
);

SET @add_idx := IF(@idx_exists_now = 0,
    'ALTER TABLE import_batches ADD UNIQUE KEY uk_import_batch_active (hospital_id, file_sha256, active_marker)',
    'DO 0');
PREPARE stmt_add_idx FROM @add_idx;
EXECUTE stmt_add_idx;
DEALLOCATE PREPARE stmt_add_idx;
