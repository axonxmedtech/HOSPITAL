-- Appointment double-booking preflight. READ-ONLY.
--
-- Run this on production (and staging) BEFORE deploying V49__appointment_active_slot_uniqueness.sql.
-- Every statement is a SELECT; nothing here changes a row. V49 deliberately FAILS if query 1
-- returns anything, because deciding which of two patients keeps a slot is a front-desk decision
-- (call the patients, move one booking), never a side effect of a deployment.
--
-- For each group from query 1, reception decides which booking keeps the slot and either
-- reschedules or cancels the other one THROUGH THE APPLICATION (so the change is audited).
-- Then run query 1 again; deploy once it returns no rows.
--
-- No patient names or phone numbers are selected.

-- 1. Slots held by more than one active booking (would violate uq_appointment_active_slot).
SELECT hospital_id,
       doctor_id,
       appointment_date,
       appointment_time,
       COUNT(*)                            AS bookings,
       GROUP_CONCAT(id ORDER BY id)        AS appointment_ids,
       GROUP_CONCAT(public_id ORDER BY id) AS appointment_public_ids,
       GROUP_CONCAT(status ORDER BY id)    AS statuses
FROM appointments
WHERE is_active = 1
  AND status <> 'CANCELLED'
GROUP BY hospital_id, doctor_id, appointment_date, appointment_time
HAVING COUNT(*) > 1
ORDER BY appointment_date DESC, hospital_id, doctor_id, appointment_time;

-- 2. How many groups, per facility (for the ticket / sign-off).
SELECT hospital_id, COUNT(*) AS double_booked_slots
FROM (
    SELECT hospital_id
    FROM appointments
    WHERE is_active = 1 AND status <> 'CANCELLED'
    GROUP BY hospital_id, doctor_id, appointment_date, appointment_time
    HAVING COUNT(*) > 1
) g
GROUP BY hospital_id
ORDER BY double_booked_slots DESC;
