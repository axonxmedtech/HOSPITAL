-- Follow-up lifecycle on medical_records.
--
-- Additive only. Existing consultations keep their follow_up_date untouched and receive a NULL
-- status, which the application reads as OPEN: nobody recorded that those patients came back, so
-- nothing here may claim they did. No encounter, queue entry or bill is created by this
-- migration or by anything reading its results.
--
-- medical_records carries a NOT NULL hospital_id of its own, so the due query is scoped directly
-- and the index below is genuinely (tenant, date) rather than a scan.

ALTER TABLE medical_records
    ADD COLUMN follow_up_instructions VARCHAR(1000) NULL;

ALTER TABLE medical_records
    ADD COLUMN follow_up_status VARCHAR(20) NULL;

-- The due/overdue/upcoming list reads one facility across a date window, repeatedly, all day.
CREATE INDEX idx_medical_records_followup
    ON medical_records (hospital_id, follow_up_date);
