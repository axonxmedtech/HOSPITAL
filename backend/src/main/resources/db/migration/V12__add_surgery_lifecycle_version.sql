-- Optimistic revision supplied by schedule/reschedule clients to reject stale commands.
-- MySQL 8 accepts IF NOT EXISTS, which also keeps a fresh schema-full bootstrap compatible.
ALTER TABLE surgeries
    ADD COLUMN IF NOT EXISTS lifecycle_version BIGINT NOT NULL DEFAULT 0;
