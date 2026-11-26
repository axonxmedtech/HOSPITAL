-- IPD includes BILLING (EntitlementRegistry.IMPLIED_BY). An admission always produces a bill and
-- is settled at discharge, so a plan or hospital holding IPD without BILLING could not discharge
-- once billing changes are module-gated. New plans store BILLING with IPD automatically; this
-- backfills plans and hospitals saved before that rule. OPD-only plans are untouched.
--
-- Data only, no DDL. Idempotent (NOT EXISTS): DatabaseMigrationRunner.backfillBillingForIpd runs
-- the same statements on every boot for non-Flyway (dev) profiles.

INSERT INTO plan_modules (plan_id, module_name)
SELECT ipd.plan_id, 'BILLING'
FROM (SELECT DISTINCT plan_id FROM plan_modules WHERE module_name = 'IPD') ipd
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT plan_id FROM plan_modules WHERE module_name = 'BILLING') b
    WHERE b.plan_id = ipd.plan_id);

INSERT INTO hospital_modules (hospital_id, module_name)
SELECT ipd.hospital_id, 'BILLING'
FROM (SELECT DISTINCT hospital_id FROM hospital_modules WHERE module_name = 'IPD') ipd
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT hospital_id FROM hospital_modules WHERE module_name = 'BILLING') b
    WHERE b.hospital_id = ipd.hospital_id);
