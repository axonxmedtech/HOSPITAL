-- Flyway V26: Reconcile import_batches active_marker and uniqueness invariant.
-- flyway:safety-ack: Release owner approved scoped replacement of an invalid derived marker/index on 2026-10-22.
-- See docs/release/PR51_V26_SAFETY.md for preconditions and recovery; no business rows are deleted.
-- This candidate changes the V26 checksum: STOP if an environment already recorded an earlier V26; never repair history.
--
-- Safety and Invariant Rules:
-- 1. Fail closed if duplicate active import batches exist (status IN ('RUNNING','COMPLETED','PARTIAL')).
--    Zero business records are deleted, merged, or overwritten.
-- 2. Detect and reconcile legacy ordinary-column, incorrect-expression, stored-generated,
--    and non-unique or malformed index schemas.
-- 3. Invariant: active_marker must be a MySQL VIRTUAL generated column:
--      TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('RUNNING','COMPLETED','PARTIAL') THEN 1 END) VIRTUAL
-- 4. Invariant: unique index uk_import_batch_active must be UNIQUE and on exact columns:
--      (hospital_id, file_sha256, active_marker)
-- 5. Final invariant assertion verifies exact column expression and unique index structure.
-- 6. Safe and idempotent across repeated runs.

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

-- Step 2: Validate existing active_marker column definition
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'
);

SET @col_is_valid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'
      AND DATA_TYPE = 'tinyint'
      AND EXTRA LIKE '%VIRTUAL%'
      AND EXTRA LIKE '%GENERATED%'
      AND EXTRA NOT LIKE '%STORED%'
      AND COLUMN_TYPE NOT LIKE '%unsigned%'
      -- Compare complete MySQL-8 canonical expressions, including literal boundaries.
      -- Do not strip whitespace/quotes inside literals: 'RUN NING' is not 'RUNNING'.
      -- MySQL reports charset introducers and, depending on version, escaped quotes.
      AND EXISTS (
          SELECT 1 FROM (
              SELECT '_utf8mb4' AS introducer UNION ALL SELECT '_utf8mb3'
              UNION ALL SELECT '_utf8' UNION ALL SELECT '_latin1'
              UNION ALL SELECT '_ascii' UNION ALL SELECT ''
          ) cs CROSS JOIN (
              SELECT CONCAT(CHAR(92), CHAR(39)) AS literal_quote
              UNION ALL SELECT CHAR(39)
          ) q
          WHERE BINARY GENERATION_EXPRESSION = BINARY CONCAT(
              '(case when (`status` in (',
              cs.introducer, q.literal_quote, 'RUNNING', q.literal_quote, ',',
              cs.introducer, q.literal_quote, 'COMPLETED', q.literal_quote, ',',
              cs.introducer, q.literal_quote, 'PARTIAL', q.literal_quote,
              ')) then 1 end)')
      )
);

-- Inspect if any index named uk_import_batch_active exists
SET @any_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active'
);

-- If column exists but is invalid (e.g. ordinary column, stored, wrong expression, wrong type):
-- We must drop any dependent index first, then drop the invalid column, and re-add the valid virtual column.
SET @drop_idx_for_invalid_col := IF(@any_idx_exists > 0 AND @col_is_valid = 0,
    'ALTER TABLE import_batches DROP INDEX uk_import_batch_active',
    'DO 0');
PREPARE stmt_drop_idx_col FROM @drop_idx_for_invalid_col;
EXECUTE stmt_drop_idx_col;
DEALLOCATE PREPARE stmt_drop_idx_col;

SET @drop_invalid_col := IF(@col_exists > 0 AND @col_is_valid = 0,
    'ALTER TABLE import_batches DROP COLUMN active_marker',
    'DO 0');
PREPARE stmt_drop_col FROM @drop_invalid_col;
EXECUTE stmt_drop_col;
DEALLOCATE PREPARE stmt_drop_col;

-- Add virtual generated column if missing or if dropped due to invalid definition
SET @add_col := IF(@col_is_valid = 0,
    'ALTER TABLE import_batches ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN (''RUNNING'',''COMPLETED'',''PARTIAL'') THEN 1 END) VIRTUAL',
    'DO 0');
PREPARE stmt_add_col FROM @add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- Step 3: Inspect existing unique index uk_import_batch_active
SET @any_idx_exists_now := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active'
);

SET @idx_is_valid_now := (
    SELECT COUNT(*) FROM (
        SELECT
            COUNT(*) AS col_count,
            MAX(NON_UNIQUE) AS max_non_unique,
            GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS col_list
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'import_batches'
          AND INDEX_NAME = 'uk_import_batch_active'
        GROUP BY INDEX_NAME
        HAVING col_count = 3
           AND max_non_unique = 0
           AND SUM(SUB_PART IS NOT NULL) = 0
           AND SUM(EXPRESSION IS NOT NULL) = 0
           AND SUM(COLLATION <> 'A') = 0
           AND col_list = 'hospital_id,file_sha256,active_marker'
    ) _valid_idx
);

-- If an index named uk_import_batch_active exists but does not match required uniqueness or columns: drop it
SET @drop_invalid_idx := IF(@any_idx_exists_now > 0 AND @idx_is_valid_now = 0,
    'ALTER TABLE import_batches DROP INDEX uk_import_batch_active',
    'DO 0');
PREPARE stmt_drop_invalid_idx FROM @drop_invalid_idx;
EXECUTE stmt_drop_invalid_idx;
DEALLOCATE PREPARE stmt_drop_invalid_idx;

-- Ensure correct unique index exists
SET @add_idx := IF(@idx_is_valid_now = 0,
    'ALTER TABLE import_batches ADD UNIQUE KEY uk_import_batch_active (hospital_id, file_sha256, active_marker)',
    'DO 0');
PREPARE stmt_add_idx FROM @add_idx;
EXECUTE stmt_add_idx;
DEALLOCATE PREPARE stmt_add_idx;

-- Step 4: Final verification and assertion of uniqueness invariant
SET @final_col_valid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'
      AND DATA_TYPE = 'tinyint'
      AND EXTRA LIKE '%VIRTUAL%'
      AND EXTRA LIKE '%GENERATED%'
      AND EXTRA NOT LIKE '%STORED%'
      AND COLUMN_TYPE NOT LIKE '%unsigned%'
      -- Compare complete MySQL-8 canonical expressions, including literal boundaries.
      -- Do not strip whitespace/quotes inside literals: 'RUN NING' is not 'RUNNING'.
      -- MySQL reports charset introducers and, depending on version, escaped quotes.
      AND EXISTS (
          SELECT 1 FROM (
              SELECT '_utf8mb4' AS introducer UNION ALL SELECT '_utf8mb3'
              UNION ALL SELECT '_utf8' UNION ALL SELECT '_latin1'
              UNION ALL SELECT '_ascii' UNION ALL SELECT ''
          ) cs CROSS JOIN (
              SELECT CONCAT(CHAR(92), CHAR(39)) AS literal_quote
              UNION ALL SELECT CHAR(39)
          ) q
          WHERE BINARY GENERATION_EXPRESSION = BINARY CONCAT(
              '(case when (`status` in (',
              cs.introducer, q.literal_quote, 'RUNNING', q.literal_quote, ',',
              cs.introducer, q.literal_quote, 'COMPLETED', q.literal_quote, ',',
              cs.introducer, q.literal_quote, 'PARTIAL', q.literal_quote,
              ')) then 1 end)')
      )
);

SET @final_idx_valid := (
    SELECT COUNT(*) FROM (
        SELECT
            COUNT(*) AS col_count,
            MAX(NON_UNIQUE) AS max_non_unique,
            GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS col_list
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'import_batches'
          AND INDEX_NAME = 'uk_import_batch_active'
        GROUP BY INDEX_NAME
        HAVING col_count = 3
           AND max_non_unique = 0
           AND SUM(SUB_PART IS NOT NULL) = 0
           AND SUM(EXPRESSION IS NOT NULL) = 0
           AND SUM(COLLATION <> 'A') = 0
           AND col_list = 'hospital_id,file_sha256,active_marker'
    ) _final_valid_idx
);

-- Fail closed if invariant verification fails
SET @abort_on_invariant_failure := IF(@final_col_valid != 1 OR @final_idx_valid != 1,
    'SELECT * FROM `ERROR_V26_FINAL_UNIQUENESS_INVARIANT_FAILED`',
    'DO 0');
PREPARE stmt_abort_invariant FROM @abort_on_invariant_failure;
EXECUTE stmt_abort_invariant;
DEALLOCATE PREPARE stmt_abort_invariant;
