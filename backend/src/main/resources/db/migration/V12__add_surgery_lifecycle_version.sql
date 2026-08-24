-- Flyway applies this version exactly once; MySQL 8 does not support ADD COLUMN IF NOT EXISTS.
ALTER TABLE surgeries
    ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0;
