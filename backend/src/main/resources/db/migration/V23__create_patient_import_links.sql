-- Legacy patient import, foundation (phase 1 of the v2 port): import provenance for a patient.
--
-- The patients table is NOT touched. Everything an import needs to remember about a patient
-- lives here, one row per imported patient (patient_id is UNIQUE):
--
--   legacy_id                 the source system's MRN; the deterministic match key for re-imports.
--                             UNIQUE per hospital. NULL is allowed and repeats freely (MySQL
--                             unique indexes ignore NULLs), because files without an MRN column
--                             are permitted and those links simply have no key.
--   created_by_batch_id       the batch that CREATED the patient. Undo reverses exactly these
--                             rows; a later batch that merely updated the patient never re-stamps
--                             it, so undoing that later batch cannot deactivate a patient it did
--                             not create.
--   last_batch_id / last_imported_at
--                             the most recent batch that wrote to the patient.
--   last_imported_values_json the demographic values the importer wrote last time. This is the
--                             edit-protection mechanism: on re-import, a field whose current value
--                             differs from what the importer wrote was changed by a human in HMS
--                             since, and the row goes to review instead of being overwritten.
--                             Being value-based, it holds for every write path -- no flag has to
--                             be set by anyone.
--   custom_fields_json        source columns the schema does not model, preserved verbatim.
--
-- FK decisions:
--   patient_id -> patients(id) ON DELETE CASCADE. A link is meaningless without its patient, and
--   PlatformHospitalService.deleteHospital hard-deletes patients by hospital; without the cascade
--   that purge would fail on the first imported tenant.
--   created_by_batch_id / last_batch_id carry NO FK. They are history pointers; batches cascade
--   away with their hospital, and a link must never block or be blocked by batch lifecycle.
--   hospital_id is stored (denormalised) so every application query is tenant-scoped without a
--   join, but is not FK-bound: the only cascade path is the patient, deliberately single.
--
-- Idempotent (CREATE TABLE IF NOT EXISTS) and existing-data safe for the reasons stated in V22.

CREATE TABLE IF NOT EXISTS patient_import_links (
  id BIGINT NOT NULL AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  hospital_id BIGINT NOT NULL,
  legacy_id VARCHAR(100) DEFAULT NULL,
  created_by_batch_id BIGINT DEFAULT NULL,
  last_batch_id BIGINT NOT NULL,
  last_imported_at DATETIME(6) NOT NULL,
  last_imported_values_json TEXT DEFAULT NULL,
  custom_fields_json TEXT DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_patient_import_link_patient (patient_id),
  UNIQUE KEY uk_patient_import_link_legacy (hospital_id, legacy_id),
  KEY idx_patient_import_link_created_by (hospital_id, created_by_batch_id),
  CONSTRAINT FK_patient_import_link_patient FOREIGN KEY (patient_id)
    REFERENCES patients (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
