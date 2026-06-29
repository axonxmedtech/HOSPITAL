-- ============================================================
-- Migration: Nurse Medication Administration Record (MAR) (Step 11)
-- Date: 2026-06-30
-- Description: Extends nurse_tasks with MAR tracking columns
-- ============================================================

ALTER TABLE nurse_tasks
    ADD COLUMN IF NOT EXISTS administered_quantity DECIMAL(5,2) NULL,
    ADD COLUMN IF NOT EXISTS route VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS injection_site VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS pre_vitals VARCHAR(255) NULL;
