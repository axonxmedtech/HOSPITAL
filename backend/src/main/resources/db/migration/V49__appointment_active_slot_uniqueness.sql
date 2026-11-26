-- A doctor's slot can be held by one active booking at a time — enforced by the database, as a
-- second line behind the application's lock (AppointmentService.lockedDaySchedule). The lock
-- covers every booking path that exists today; this covers any path added later that forgets it.
--
-- active_slot_key is "doctor|date|time" ONLY while the row holds the slot:
--   * a soft-deleted (is_active = 0) appointment holds nothing          -> NULL
--   * a CANCELLED appointment has released its slot                     -> NULL
--   * every other appointment (scheduled, confirmed, completed, ...)    -> the key
-- This is exactly the rule the booking check applies. MySQL allows repeated NULLs in a unique
-- index, so released slots never collide.
--
-- This migration NEVER edits appointment data. If two active bookings already share a slot it
-- FAILS (ER_DUP_ENTRY) and the deployment stops; a human resolves them first — see
-- docs/operations/appointment-slot-preflight.sql. Run that pre-flight BEFORE deploying.
--
-- Idempotent. DatabaseMigrationRunner.ensureAppointmentActiveSlotUniqueness does the same on
-- non-Flyway (dev) profiles. Must run after V48 (Flyway out-of-order is off in prod).

-- VIRTUAL: indexable in MySQL 8 and needs no table rewrite. hospital_id leads the index.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE appointments ADD COLUMN active_slot_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN is_active = 1 AND status <> ''CANCELLED'' THEN CONCAT(doctor_id, ''|'', appointment_date, ''|'', appointment_time) END) VIRTUAL',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appointments' AND COLUMN_NAME = 'active_slot_key');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Deliberately not wrapped in anything that could swallow a failure: conflicting rows must stop
-- the deployment, naming the offending (hospital_id, active_slot_key) pair.
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE appointments ADD UNIQUE KEY uq_appointment_active_slot (hospital_id, active_slot_key)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appointments' AND INDEX_NAME = 'uq_appointment_active_slot');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
