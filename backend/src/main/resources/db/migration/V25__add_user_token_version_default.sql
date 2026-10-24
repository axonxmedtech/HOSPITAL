-- users.token_version: INT NOT NULL DEFAULT 0, whatever state the column arrives in.
--
-- The session-revocation release added User.tokenVersion (NOT NULL) with no migration, so only
-- Hibernate ddl-auto created it, as INT NOT NULL with NO default. An insert that omits the column
-- then fails under MySQL's default strict mode. That is exactly what the previous application build
-- does when users are created (staff onboarding, new hospitals), so a rollback after this release
-- would break user creation. A column DEFAULT of 0 makes such inserts work, and 0 is the value every
-- existing session already carries.
--
-- Three starting states, all converging on the same column:
--   production  column ABSENT. Flyway runs before Hibernate, and the production build that wrote
--               this database predates the column. ADD COLUMN gives existing rows 0.
--   staging     column PRESENT as INT NOT NULL without a default (created by ddl-auto).
--               Only the default is added. Existing values are kept, so live sessions stay valid.
--   defensive   column PRESENT but NULLABLE (never created that way by this app). NULLs become 0,
--               then the column is made NOT NULL.
-- Idempotent: running the statements again changes nothing. MySQL 8 has no ADD COLUMN IF NOT
-- EXISTS, so the guards are information_schema checks driving prepared statements (as in V12).
--
-- flyway:safety-ack: the MODIFY runs only when token_version exists AND is nullable, after NULLs are set to 0. It keeps the type INT and narrows nothing (audit H1, PR for B2/H1).

SET @tv_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'token_version');

SET @tv_nullable := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'token_version'
      AND IS_NULLABLE = 'YES');

-- 1. Absent: add it. Existing rows get the default 0.
SET @tv_add := IF(@tv_exists = 0,
    'ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE tv_add_stmt FROM @tv_add;
EXECUTE tv_add_stmt;
DEALLOCATE PREPARE tv_add_stmt;

-- 2. Present but nullable: fill the NULLs, then forbid them.
SET @tv_fill := IF(@tv_nullable > 0,
    'UPDATE users SET token_version = 0 WHERE token_version IS NULL',
    'DO 0');
PREPARE tv_fill_stmt FROM @tv_fill;
EXECUTE tv_fill_stmt;
DEALLOCATE PREPARE tv_fill_stmt;

SET @tv_not_null := IF(@tv_nullable > 0,
    'ALTER TABLE users MODIFY COLUMN token_version INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE tv_not_null_stmt FROM @tv_not_null;
EXECUTE tv_not_null_stmt;
DEALLOCATE PREPARE tv_not_null_stmt;

-- 3. Converge the default in every case. Metadata-only on MySQL 8: no table rewrite, no row
-- touched, nullability unchanged. Already 0 after step 1 or 2, so repeating it is harmless.
ALTER TABLE users ALTER COLUMN token_version SET DEFAULT 0;
