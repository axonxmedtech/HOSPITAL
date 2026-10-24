-- Legacy patient import, phase 5: at most ONE live batch per hospital and file, enforced by the
-- database rather than by a check-then-insert that two simultaneous commits can both pass.
--
-- The rule the application promised: the same file (SHA-256, hospital-scoped) may not be imported
-- again while a batch for it is RUNNING, COMPLETED or PARTIAL, but a FAILED or UNDONE batch is
-- retryable. A plain UNIQUE (hospital_id, file_sha256) would forbid the retry. So, exactly as V21
-- does for patient phones, the uniqueness is put on a generated column that carries a value only
-- while the row competes for it:
--   * RUNNING / COMPLETED / PARTIAL  -> 1
--   * FAILED / UNDONE                -> NULL   (MySQL unique indexes ignore NULLs)
-- Two concurrent starts then race at the INSERT and exactly one wins; the loser's transaction is
-- refused by uk_import_batch_active and it reports the existing batch instead.
--
-- Idempotent through information_schema guards (MySQL has no ADD COLUMN IF NOT EXISTS), and
-- existing-data safe: the index is added over rows whose marker is derived, and a database that
-- somehow already holds two live batches for one file will fail here loudly rather than proceed.
-- Nothing is modified, dropped or renamed.

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE import_batches ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN (''RUNNING'',''COMPLETED'',''PARTIAL'') THEN 1 END) VIRTUAL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE import_batches ADD UNIQUE KEY uk_import_batch_active (hospital_id, file_sha256, active_marker)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
