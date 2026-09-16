-- Patient duplicate-phone preflight (S-PID-D). READ-ONLY.
--
-- Run this BEFORE applying V21__add_patient_active_phone_uniqueness.sql. Every statement is a
-- SELECT; nothing here changes a single row. The migration deliberately fails if any group below
-- is non-empty, because resolving a duplicate patient moves their appointments, bills,
-- prescriptions and admissions with them — that is a controlled clinical workflow, never a
-- side effect of a deployment.
--
-- For each group returned by query 1, a human decides ONE of:
--   * genuinely different people sharing a number -> acknowledge the additional record(s) through
--     the application, which records who decided and when
--   * a true duplicate of one person -> resolve through the patient-merge workflow, NOT here
--
-- No patient names and no plaintext phone numbers are selected. The phone is reported as a
-- SHA-256 fingerprint: identical numbers produce an identical fingerprint, which is all that is
-- needed to group and to compare environments, without putting contact details in a ticket.

-- 1. Groups that would violate uq_patient_active_phone.
SELECT hospital_id,
       SHA2(phone, 256)                     AS phone_fingerprint,
       COUNT(*)                             AS conflicting_rows,
       GROUP_CONCAT(id ORDER BY id)         AS patient_ids,
       GROUP_CONCAT(custom_id ORDER BY id)  AS custom_ids
FROM patients
WHERE is_active = 1
  AND phone IS NOT NULL
  AND phone <> ''
  AND (duplicate_phone_ack_for IS NULL OR duplicate_phone_ack_for <> phone)
GROUP BY hospital_id, phone
HAVING COUNT(*) > 1
ORDER BY conflicting_rows DESC, hospital_id;

-- 2. How big is the problem, in one number. Zero means the migration will apply cleanly.
SELECT COUNT(*) AS conflicting_groups FROM (
  SELECT 1 FROM patients
  WHERE is_active = 1 AND phone IS NOT NULL AND phone <> ''
    AND (duplicate_phone_ack_for IS NULL OR duplicate_phone_ack_for <> phone)
  GROUP BY hospital_id, phone HAVING COUNT(*) > 1
) g;

-- 3. Legacy rows the application's ^[0-9]{10}$ validation would reject today. These do NOT block
--    the migration — the constraint uses the stored value as-is — but they are worth knowing about
--    before anyone edits one and is surprised by a validation error.
SELECT COUNT(*) AS active_non_conforming_phones
FROM patients
WHERE is_active = 1 AND phone NOT REGEXP '^[0-9]{10}$';

-- 4. Confirm afterwards that enforcement is actually in place.
SELECT COLUMN_NAME, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'active_phone_key';

SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND INDEX_NAME = 'uq_patient_active_phone'
GROUP BY INDEX_NAME, NON_UNIQUE;
