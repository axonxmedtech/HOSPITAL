package com.hms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs idempotent schema patches on every startup.
 *
 * ddl-auto=update can add columns but never removes them or changes nullability.
 * This runner bridges that gap for the few historical mismatches that need fixing.
 * Each patch is wrapped individually so one failure does not block the others.
 */
@Component
public class DatabaseMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        fixHospitalsPlanColumn();
        ensureHospitalSettingsInClinic();
        ensureHospitalsIsSingleDoctor();
        ensureWhatsAppConfigTable();      // NEW
        ensureWhatsAppMessageLogTable();  // NEW
        ensureWhatsAppMessageLogRetryColumns();
        ensureMissingIndexes();
        simplifyMedicineListTable();
        migratePatientAgeToDateOfBirth(); // NEW
        ensureConsultationNotePresetsTable(); // NEW
        ensurePrescriptionPresetTables(); // NEW
        ensurePresetDoctorIdColumns(); // NEW — per-doctor preset isolation
        ensureInventoryItemHasOwnStockColumn(); // NEW
        ensureInventoryServicesTables(); // NEW
        ensureHospitalSettingsBarcodeEnabled(); // NEW — pharmacy barcode toggle
        ensurePlanMultiOutletColumns(); // NEW — pharmacy multi-outlet plan support
        dropLegacyNotNullNoDefaultColumns(); // NEW — remove NOT-NULL-no-default landmines
        ensureMedicineMasterManufacturerName(); // NEW — free-text manufacturer from purchase
        ensurePharmacyBranchSupport(); // NEW — Multi Pharmacy branches
        ensurePharmacyDataBranchColumns(); // NEW — branch_id on pharmacy data tables
        ensureAuditLogsBranchColumn(); // NEW — branch_id on audit_logs for pharmacy audit trails
        ensureNurseProfilesTable(); // NEW — Nurse module (Phase 1)
        ensureNurseProfileWardColumn(); // NEW — Nurse ward assignment
        ensureNurseProfileShiftColumn(); // NEW — Nurse shift availability
        ensureAdmissionConfirmedColumns(); // NEW — nurse-confirmed admission
        ensureAdmittedByColumn(); // NEW — admitting receptionist
        ensureAdmissionFormsTable(); // NEW — nurse admission form
        ensureInitialAssessmentsTable(); // NEW — nurse initial assessment
        ensureInitialAssessmentPainColumn(); // NEW — pain score
        ensureVulnerabilityAssessmentsTable(); // NEW — nurse vulnerability assessment
        ensureSugarChartEntriesTable(); // NEW — nurse sugar chart
        ensurePatientNurseAssignmentsTable(); // NEW — Nurse module (Phase 1)
        ensureVitalsRecordsTable(); // NEW — Nurse module (Phase 1)
        ensureNursingNotesTable(); // NEW — Nurse module (Phase 1)
        ensureMedicationAdministrationsTable(); // NEW — Nurse module (Phase 1)
        ensureManualTasksTable(); // NEW — Nurse module (Phase 1, M7)
        ensureNotificationsTable(); // NEW — Nurse module (Phase 1, M8)
        ensureOpdAdmitRecommendedColumn(); // NEW
        ensureSurgeriesTable(); // NEW — OT module (Phase 2)
        ensureSurgerySurgeonNameColumn(); // NEW — OT "Other" operator name
        ensureSurgeryAnaesthetistNameColumn(); // NEW — OT optional anaesthetist
        ensureSurgeryFormsTable(); // NEW — OT/NABH surgery forms store
        ensureNursingNoteSurgeryIdColumn(); // NEW — OT notes link
        ensureNurseProfilePhaseAColumns(); // NEW — Nursing Mgmt Phase A
        ensureWardInchargeColumn();
        ensureSeparateNurseLoginColumn();
        ensurePerformedByNurseIdColumns(); // NEW — Nursing Mgmt Phase A3, "Performed By"
        ensureShiftTemplatesTable(); // NEW — Nursing Mgmt Phase B1
        ensureAppointmentSlotsTable(); // NEW — Nursing Mgmt Phase B1
        ensureNurseShiftSchedulesTable(); // NEW — Nursing Mgmt Phase B2
        ensureNurseAttendanceTable(); // NEW — Nursing Mgmt Phase D
        ensureNurseWardAssignmentsTable(); // NEW — Nursing Mgmt Phase F
        ensureNurseSubstitutionsTable(); // NEW — Nursing Mgmt Phase F
        ensureCalendarEventsTable();
        ensureHospitalFormAccessTable();
        // Orders (drugs / IV fluids) captured alongside a nursing note; printed beside it.
        addColumnIfMissing("nursing_notes", "orders", "TEXT NULL");
        // Height (cm) captured with the OPD vitals, alongside weight.
        addColumnIfMissing("opd", "height", "DOUBLE NULL");
        ensureBedStatusAuditsTable(); // NEW — Nursing Mgmt Phase C1
    }

    private void ensureHospitalFormAccessTable() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'hospital_form_access'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE hospital_form_access (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  hospital_id BIGINT NOT NULL," +
                        "  form_key VARCHAR(60) NOT NULL," +
                        "  enabled TINYINT(1) NOT NULL DEFAULT 1," +
                        "  access_role VARCHAR(10) NOT NULL DEFAULT 'BOTH'," +
                        "  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  UNIQUE KEY uq_form_access_hosp_key (hospital_id, form_key)," +
                        "  CONSTRAINT fk_form_access_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                        ")");
                log.info("Created hospital_form_access table");
            }
        } catch (Exception e) {
            log.warn("ensureHospitalFormAccessTable failed: {}", e.getMessage());
        }
    }

    private void ensureCalendarEventsTable() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'calendar_events'",
                    Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE calendar_events (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  public_id VARCHAR(64) NOT NULL UNIQUE," +
                        "  hospital_id BIGINT NOT NULL," +
                        "  title VARCHAR(160) NOT NULL," +
                        "  event_type VARCHAR(20) NOT NULL," +
                        "  from_date DATE NOT NULL," +
                        "  to_date DATE NOT NULL," +
                        "  description VARCHAR(500)," +
                        "  created_by_user_id BIGINT," +
                        "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  INDEX idx_calevent_hosp_dates (hospital_id, from_date, to_date)," +
                        "  CONSTRAINT fk_calevent_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                        ")");
                log.info("Created calendar_events table");
            }
        } catch (Exception e) {
            log.warn("ensureCalendarEventsTable failed: {}", e.getMessage());
        }
    }

    /**
     * Creates the bed_status_audits table if absent (Nursing Mgmt Phase C1).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureBedStatusAuditsTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bed_status_audits'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE bed_status_audits (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "bed_id BIGINT NOT NULL, ward_id BIGINT, previous_status VARCHAR(20), new_status VARCHAR(20) NOT NULL," +
                    "changed_by_user_id BIGINT, remarks VARCHAR(255), changed_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_bsa_public (public_id)," +
                    "KEY idx_bsa_bed_time (bed_id, changed_at), KEY idx_bsa_hospital (hospital_id)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: bed_status_audits table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (bed_status_audits): {}", e.getMessage()); }
    }

    /**
     * Creates the nurse_shift_schedules table if absent (Nursing Mgmt Phase B2).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNurseShiftSchedulesTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_shift_schedules'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE nurse_shift_schedules (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "nurse_profile_id BIGINT NOT NULL, ward_id BIGINT, shift_date DATE NOT NULL, shift_template_id BIGINT NOT NULL," +
                    "start_time TIME NOT NULL, end_time TIME NOT NULL, created_by_user_id BIGINT, created_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_nss_public (public_id), UNIQUE KEY UK_nss_nurse_date (nurse_profile_id, shift_date)," +
                    "KEY idx_nss_hospital_date (hospital_id, shift_date), KEY idx_nss_ward_date (ward_id, shift_date)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: nurse_shift_schedules table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (nurse_shift_schedules): {}", e.getMessage()); }
    }

    private void ensureNurseWardAssignmentsTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_ward_assignments'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE nurse_ward_assignments (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "nurse_profile_id BIGINT NOT NULL, temp_ward_id BIGINT NOT NULL, from_date DATE NOT NULL, to_date DATE NOT NULL," +
                    "reason VARCHAR(255), created_by_user_id BIGINT, created_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_nwa_public (public_id)," +
                    "KEY idx_nwa_nurse (nurse_profile_id, from_date, to_date), KEY idx_nwa_ward (temp_ward_id, from_date, to_date)," +
                    "KEY idx_nwa_hospital (hospital_id, to_date)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: nurse_ward_assignments table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (nurse_ward_assignments): {}", e.getMessage()); }
    }

    private void ensureNurseSubstitutionsTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_substitutions'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE nurse_substitutions (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "primary_nurse_profile_id BIGINT NOT NULL, replacement_nurse_profile_id BIGINT NOT NULL," +
                    "from_date DATE NOT NULL, to_date DATE NOT NULL, reason VARCHAR(255), created_by_user_id BIGINT, created_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_nsub_public (public_id)," +
                    "KEY idx_nsub_repl (replacement_nurse_profile_id, from_date, to_date), KEY idx_nsub_hospital (hospital_id, to_date)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: nurse_substitutions table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (nurse_substitutions): {}", e.getMessage()); }
    }

    private void ensureNurseAttendanceTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_attendance'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE nurse_attendance (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "nurse_profile_id BIGINT NOT NULL, ward_id BIGINT, attendance_date DATE NOT NULL, status VARCHAR(20) NOT NULL," +
                    "shift_template_id BIGINT, shift_start_time TIME, shift_end_time TIME," +
                    "check_in_time TIME, check_out_time TIME, remarks VARCHAR(255), marked_by_user_id BIGINT," +
                    "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_na_public (public_id)," +
                    "UNIQUE KEY UK_na_nurse_date (nurse_profile_id, attendance_date)," +
                    "KEY idx_na_hospital_date (hospital_id, attendance_date), KEY idx_na_ward_date (ward_id, attendance_date)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: nurse_attendance table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (nurse_attendance): {}", e.getMessage()); }
    }

    /**
     * Creates the shift_templates table if absent (Nursing Mgmt Phase B1).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureShiftTemplatesTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shift_templates'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE shift_templates (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "name VARCHAR(60) NOT NULL, start_time TIME NOT NULL, end_time TIME NOT NULL," +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_shift_template_public (public_id), KEY idx_shift_template_hospital (hospital_id)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: shift_templates table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (shift_templates): {}", e.getMessage()); }
    }

    /**
     * Creates the appointment_slots table if absent (Nursing Mgmt Phase B1).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureAppointmentSlotsTable() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appointment_slots'", Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("CREATE TABLE appointment_slots (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, public_id VARCHAR(255) NOT NULL, hospital_id BIGINT NOT NULL," +
                    "start_time TIME NOT NULL, end_time TIME NOT NULL, is_active TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME(6) NOT NULL," +
                    "PRIMARY KEY (id), UNIQUE KEY UK_appt_slot_public (public_id), KEY idx_appt_slot_hospital (hospital_id)," +
                    "FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE)");
                log.info("DB migration applied: appointment_slots table created");
            }
        } catch (Exception e) { log.warn("DB migration skipped (appointment_slots): {}", e.getMessage()); }
    }

    /**
     * Creates the manual_tasks table if absent (Phase 1 Nurse module, M7).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureManualTasksTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'manual_tasks'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE manual_tasks (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  title VARCHAR(150) NOT NULL," +
                    "  description TEXT," +
                    "  assigned_to_nurse_user_id BIGINT NOT NULL," +
                    "  assigned_by_user_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT," +
                    "  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'," +
                    "  status VARCHAR(15) NOT NULL DEFAULT 'PENDING'," +
                    "  due_date DATETIME(6)," +
                    "  completed_at DATETIME(6)," +
                    "  completion_remarks VARCHAR(500)," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_manual_tasks_public_id (public_id)," +
                    "  KEY idx_tasks_nurse_status (assigned_to_nurse_user_id, status)," +
                    "  KEY idx_tasks_hospital_status (hospital_id, status)," +
                    "  KEY idx_tasks_admission (ipd_admission_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: manual_tasks table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (manual_tasks): {}", e.getMessage());
        }
    }

    /**
     * Creates the notifications table if absent (Phase 1 Nurse module, M8).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNotificationsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE notifications (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  recipient_user_id BIGINT NOT NULL," +
                    "  type VARCHAR(30) NOT NULL," +
                    "  title VARCHAR(150) NOT NULL," +
                    "  message TEXT NOT NULL," +
                    "  reference_type VARCHAR(50) DEFAULT NULL," +
                    "  reference_id BIGINT DEFAULT NULL," +
                    "  is_read TINYINT(1) NOT NULL DEFAULT 0," +
                    "  read_at DATETIME(6) DEFAULT NULL," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_notifications_public_id (public_id)," +
                    "  KEY idx_ntf_recipient (recipient_user_id)," +
                    "  KEY idx_ntf_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: notifications table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (notifications): {}", e.getMessage());
        }
    }

    /**
     * Creates the medication_administrations table if absent (Phase 1 Nurse
     * module). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureMedicationAdministrationsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medication_administrations'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE medication_administrations (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  prescription_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  nurse_user_id BIGINT NOT NULL," +
                    "  scheduled_time DATETIME(6)," +
                    "  administered_time DATETIME(6)," +
                    "  status VARCHAR(20) NOT NULL," +
                    "  remarks VARCHAR(500)," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_mar_public_id (public_id)," +
                    "  KEY idx_mar_admission_time (ipd_admission_id, administered_time)," +
                    "  KEY idx_mar_prescription (prescription_id)," +
                    "  KEY idx_mar_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: medication_administrations table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (medication_administrations): {}", e.getMessage());
        }
    }

    /**
     * Creates the nursing_notes table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNursingNotesTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nursing_notes'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE nursing_notes (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  nurse_user_id BIGINT NOT NULL," +
                    "  note_text TEXT NOT NULL," +
                    "  category VARCHAR(40)," +
                    "  recorded_at DATETIME(6) NOT NULL," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_nursing_notes_public_id (public_id)," +
                    "  KEY idx_nursing_notes_admission_time (ipd_admission_id, recorded_at)," +
                    "  KEY idx_nursing_notes_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: nursing_notes table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (nursing_notes): {}", e.getMessage());
        }
    }

    /**
     * Creates the vitals_records table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureVitalsRecordsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vitals_records'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE vitals_records (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  recorded_by_user_id BIGINT NOT NULL," +
                    "  recorded_at DATETIME(6) NOT NULL," +
                    "  temperature DECIMAL(4,1)," +
                    "  pulse INT," +
                    "  bp_systolic INT," +
                    "  bp_diastolic INT," +
                    "  respiratory_rate INT," +
                    "  spo2 INT," +
                    "  weight DECIMAL(5,2)," +
                    "  pain_score INT," +
                    "  remarks VARCHAR(500)," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_vitals_public_id (public_id)," +
                    "  KEY idx_vitals_admission_time (ipd_admission_id, recorded_at)," +
                    "  KEY idx_vitals_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: vitals_records table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (vitals_records): {}", e.getMessage());
        }
    }

    /**
     * Creates the patient_nurse_assignments table if absent (Phase 1 Nurse
     * module). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensurePatientNurseAssignmentsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patient_nurse_assignments'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE patient_nurse_assignments (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  nurse_user_id BIGINT NOT NULL," +
                    "  assigned_by_user_id BIGINT NOT NULL," +
                    "  assigned_at DATETIME(6) NOT NULL," +
                    "  unassigned_at DATETIME(6)," +
                    "  notes VARCHAR(255)," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_pna_public_id (public_id)," +
                    "  KEY idx_pna_admission_active (ipd_admission_id, is_active)," +
                    "  KEY idx_pna_nurse_active (nurse_user_id, is_active)," +
                    "  KEY idx_pna_hospital_active (hospital_id, is_active)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: patient_nurse_assignments table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (patient_nurse_assignments): {}", e.getMessage());
        }
    }

    /**
     * Creates the nurse_profiles table if absent (Phase 1 Nurse module).
     * ddl-auto=update also creates it from the entity; this keeps the schema
     * reproducible on databases where ddl-auto is disabled and mirrors
     * setup/schema-full.sql. Idempotent.
     */
    private void ensureNurseProfilesTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_profiles'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE nurse_profiles (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  custom_id VARCHAR(255)," +
                    "  user_id BIGINT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  name VARCHAR(100) NOT NULL," +
                    "  phone VARCHAR(20)," +
                    "  email VARCHAR(100) NOT NULL," +
                    "  license_number VARCHAR(50)," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_nurse_profiles_public_id (public_id)," +
                    "  KEY idx_nurse_profiles_hospital (hospital_id)," +
                    "  KEY idx_nurse_profiles_user (user_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: nurse_profiles table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (nurse_profiles): {}", e.getMessage());
        }
    }

    /**
     * Adds the nullable ward_id column to nurse_profiles (nurse-to-ward
     * assignment). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNurseProfileWardColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_profiles' AND COLUMN_NAME = 'ward_id'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE nurse_profiles ADD COLUMN ward_id BIGINT NULL");
                log.info("DB migration applied: added ward_id column to nurse_profiles");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (nurse_profiles.ward_id): {}", e.getMessage());
        }
    }

    /**
     * Adds the on_shift column to nurse_profiles (nurse shift availability).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNurseProfileShiftColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_profiles' AND COLUMN_NAME = 'on_shift'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE nurse_profiles ADD COLUMN on_shift TINYINT(1) NOT NULL DEFAULT 0");
                log.info("DB migration applied: added on_shift column to nurse_profiles");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (nurse_profiles.on_shift): {}", e.getMessage());
        }
    }

    /**
     * Adds admission_confirmed + admission_confirmed_at to ipd_admission
     * (nurse-confirmed admission after the signed form is collected). Idempotent.
     */
    private void ensureAdmissionConfirmedColumns() {
        try {
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'admission_confirmed'",
                Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("ALTER TABLE ipd_admission ADD COLUMN admission_confirmed TINYINT(1) NOT NULL DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE ipd_admission ADD COLUMN admission_confirmed_at DATETIME(6) NULL");
                log.info("DB migration applied: added admission_confirmed columns to ipd_admission");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (ipd_admission.admission_confirmed): {}", e.getMessage());
        }
    }

    /**
     * Adds admitted_by_user_id to ipd_admission (the receptionist/admin who
     * admitted the patient). Idempotent.
     */
    private void ensureAdmittedByColumn() {
        try {
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ipd_admission' AND COLUMN_NAME = 'admitted_by_user_id'",
                Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("ALTER TABLE ipd_admission ADD COLUMN admitted_by_user_id BIGINT NULL");
                log.info("DB migration applied: added admitted_by_user_id column to ipd_admission");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (ipd_admission.admitted_by_user_id): {}", e.getMessage());
        }
    }

    /**
     * Creates the admission_forms table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureAdmissionFormsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admission_forms'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE admission_forms (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  prn_no VARCHAR(60), bed_no VARCHAR(60), category VARCHAR(40)," +
                    "  patient_surname VARCHAR(100), patient_first_name VARCHAR(100), husband_father_name VARCHAR(150)," +
                    "  patient_address TEXT, age VARCHAR(20), sex VARCHAR(20), occupation VARCHAR(100)," +
                    "  patient_category VARCHAR(40), mediclaim VARCHAR(20), tpa_name VARCHAR(200)," +
                    "  relative_name VARCHAR(150), email VARCHAR(120), telephone VARCHAR(40)," +
                    "  receptionist_name VARCHAR(120), ref_dr VARCHAR(150)," +
                    "  ipd_registration_no VARCHAR(60), department VARCHAR(120), under_care_of_dr VARCHAR(150)," +
                    "  admitted_date VARCHAR(40), admitted_time VARCHAR(40)," +
                    "  prov_diagnosis1 TEXT, prov_diagnosis2 TEXT, hypersensitivity_history TEXT," +
                    "  relative_address TEXT, relative_phone VARCHAR(40)," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_admission_forms_public_id (public_id)," +
                    "  UNIQUE KEY UK_admission_forms_ipd (ipd_admission_id)," +
                    "  KEY idx_admission_forms_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: admission_forms table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (admission_forms): {}", e.getMessage());
        }
    }

    /**
     * Creates the initial_assessments table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureInitialAssessmentsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initial_assessments'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE initial_assessments (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  chief_complaints TEXT, associated_illness TEXT, relevant_investigations TEXT," +
                    "  allergies TEXT, vaccination_history TEXT, others TEXT," +
                    "  past_history TEXT, family_history TEXT, personal_history TEXT," +
                    "  provisional_diagnosis TEXT, care_plan TEXT," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_initial_assessments_public_id (public_id)," +
                    "  UNIQUE KEY UK_initial_assessments_ipd (ipd_admission_id)," +
                    "  KEY idx_initial_assessments_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: initial_assessments table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (initial_assessments): {}", e.getMessage());
        }
    }

    /** Adds the pain_score column to initial_assessments. Idempotent. */
    private void ensureInitialAssessmentPainColumn() {
        try {
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initial_assessments' AND COLUMN_NAME = 'pain_score'",
                Integer.class);
            if (c != null && c == 0) {
                jdbcTemplate.execute("ALTER TABLE initial_assessments ADD COLUMN pain_score INT NULL");
                log.info("DB migration applied: added pain_score column to initial_assessments");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (initial_assessments.pain_score): {}", e.getMessage());
        }
    }

    /**
     * Creates the vulnerability_assessments table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureVulnerabilityAssessmentsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vulnerability_assessments'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE vulnerability_assessments (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  category VARCHAR(120), fall_risk_assessment TEXT, sensory_deficit TEXT," +
                    "  disorientation VARCHAR(120), self_care_deficit VARCHAR(10), mobility_problem VARCHAR(10)," +
                    "  history_of_fall VARCHAR(10), impaired_judgement VARCHAR(10)," +
                    "  psychological_status VARCHAR(200), remarks TEXT, nursing_intervention TEXT," +
                    "  reason_for_transfer TEXT, investigation_lab TEXT, investigation_radiology TEXT," +
                    "  transfer_provisional_diagnosis TEXT, transfer_doctor_name VARCHAR(150)," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_vuln_public_id (public_id)," +
                    "  UNIQUE KEY UK_vuln_ipd (ipd_admission_id)," +
                    "  KEY idx_vuln_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: vulnerability_assessments table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (vulnerability_assessments): {}", e.getMessage());
        }
    }

    /**
     * Creates the sugar_chart_entries table if absent (Phase 1 Nurse module).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureSugarChartEntriesTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sugar_chart_entries'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE sugar_chart_entries (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  nurse_user_id BIGINT NOT NULL," +
                    "  blood_sugar VARCHAR(60)," +
                    "  treatment TEXT," +
                    "  recorded_at DATETIME(6) NOT NULL," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_sugar_public_id (public_id)," +
                    "  KEY idx_sugar_admission_time (ipd_admission_id, recorded_at)," +
                    "  KEY idx_sugar_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: sugar_chart_entries table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (sugar_chart_entries): {}", e.getMessage());
        }
    }

    /**
     * Creates the surgeries table if absent (OT module, Phase 2).
     * Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureSurgeriesTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'surgeries'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE surgeries (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  patient_id BIGINT NOT NULL," +
                    "  procedure_name VARCHAR(255)," +
                    "  clinical_notes TEXT," +
                    "  priority VARCHAR(20)," +
                    "  preferred_date DATE," +
                    "  requested_by_doctor_id BIGINT," +
                    "  requested_by_user_id BIGINT," +
                    "  requested_at DATETIME(6) NOT NULL," +
                    "  status VARCHAR(20) NOT NULL," +
                    "  surgeon_doctor_id BIGINT," +
                    "  surgeon_name VARCHAR(255)," +
                    "  anaesthetist_name VARCHAR(255)," +
                    "  scheduled_at DATETIME(6)," +
                    "  ot_ward_id BIGINT," +
                    "  ot_bed_id BIGINT," +
                    "  scheduled_by_user_id BIGINT," +
                    "  started_at DATETIME(6)," +
                    "  completed_at DATETIME(6)," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_surgery_public_id (public_id)," +
                    "  KEY idx_surgery_hospital_status (hospital_id, status)," +
                    "  KEY idx_surgery_admission (ipd_admission_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: surgeries table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (surgeries): {}", e.getMessage());
        }
    }

    /**
     * Adds the nullable surgeon_name column to surgeries (free-text "Other"
     * operator name). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureSurgerySurgeonNameColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'surgeries' AND COLUMN_NAME = 'surgeon_name'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE surgeries ADD COLUMN surgeon_name VARCHAR(255) NULL");
                log.info("DB migration applied: added surgeon_name column to surgeries");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (surgeries.surgeon_name): {}", e.getMessage());
        }
    }

    /**
     * Adds the nullable anaesthetist_name column to surgeries (optional
     * anaesthetist for the surgery). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureSurgeryAnaesthetistNameColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'surgeries' AND COLUMN_NAME = 'anaesthetist_name'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE surgeries ADD COLUMN anaesthetist_name VARCHAR(255) NULL");
                log.info("DB migration applied: added anaesthetist_name column to surgeries");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (surgeries.anaesthetist_name): {}", e.getMessage());
        }
    }

    private void ensureSurgeryFormsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'surgery_forms'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE surgery_forms (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  public_id VARCHAR(255) NOT NULL," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  ipd_admission_id BIGINT NOT NULL," +
                    "  surgery_id BIGINT," +
                    "  form_type VARCHAR(60) NOT NULL," +
                    "  data_json LONGTEXT," +
                    "  saved_by_user_id BIGINT," +
                    "  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY UK_surgery_form_public_id (public_id)," +
                    "  UNIQUE KEY UK_surgery_form_admission_type (ipd_admission_id, form_type)," +
                    "  KEY idx_surgery_form_hospital (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: surgery_forms table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (surgery_forms): {}", e.getMessage());
        }
    }

    /**
     * Adds the nullable surgery_id column to nursing_notes (links an OT note
     * to its surgery). Idempotent; mirrors setup/schema-full.sql.
     */
    private void ensureNursingNoteSurgeryIdColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nursing_notes' AND COLUMN_NAME = 'surgery_id'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE nursing_notes ADD COLUMN surgery_id BIGINT NULL");
                log.info("DB migration applied: added surgery_id column to nursing_notes");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (nursing_notes.surgery_id): {}", e.getMessage());
        }
    }

    /**
     * Add nullable branch_id to the branch-scoped pharmacy data tables (Multi Pharmacy
     * isolation). Null = not branch-scoped (single-shop / hospital / clinic pharmacy).
     */
    private void ensurePharmacyDataBranchColumns() {
        String[] tables = {
            "medicine_master", "medicine_batches", "suppliers",
            "purchase_invoices", "inventory_transactions", "pharmacy_sales"
        };
        for (String table : tables) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'branch_id'",
                    Integer.class, table
                );
                if (count != null && count == 0) {
                    jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN branch_id BIGINT DEFAULT NULL");
                    log.info("DB migration applied: {}.branch_id column added", table);
                }
            } catch (Exception e) {
                log.warn("DB migration skipped ({}.branch_id): {}", table, e.getMessage());
            }
        }
    }

    /**
     * Ensure the pharmacy_branch table and users.branch_id column exist (Multi Pharmacy).
     */
    private void ensurePharmacyBranchSupport() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pharmacy_branch'",
                Integer.class
            );
            if (tableExists != null && tableExists == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE pharmacy_branch (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  name VARCHAR(255) NOT NULL," +
                    "  address VARCHAR(255) DEFAULT NULL," +
                    "  phone VARCHAR(30) DEFAULT NULL," +
                    "  login_user_id BIGINT DEFAULT NULL," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) DEFAULT NULL," +
                    "  updated_at DATETIME(6) DEFAULT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  KEY idx_pharmacy_branch_hospital (hospital_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                );
                log.info("DB migration applied: pharmacy_branch table created");
            }

            Integer colExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'branch_id'",
                Integer.class
            );
            if (colExists != null && colExists == 0) {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN branch_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: users.branch_id column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (pharmacy_branch support): {}", e.getMessage());
        }
    }

    /**
     * Ensure medicine_master.manufacturer_name exists. Nullable free-text manufacturer
     * captured on the pharmacy purchase form (standalone pharmacy ERP).
     */
    private void ensureMedicineMasterManufacturerName() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medicine_master' AND COLUMN_NAME = 'manufacturer_name'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE medicine_master ADD COLUMN manufacturer_name VARCHAR(255) DEFAULT NULL");
                log.info("DB migration applied: medicine_master.manufacturer_name column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (medicine_master.manufacturer_name): {}", e.getMessage());
        }
    }

    /**
     * Drop orphaned columns that are NOT NULL with no default and no longer mapped by
     * any entity (hospital_settings.shift_mode, users.is_trainer). Hibernate omits them
     * on INSERT, so MySQL rejects every insert on those tables (error 1364), breaking
     * hospital/clinic/pharmacy onboarding and user creation. The canonical schema has
     * no such columns — this realigns older databases with it.
     */
    private void dropLegacyNotNullNoDefaultColumns() {
        String[][] orphanColumns = {
            {"hospital_settings", "shift_mode"},
            {"users", "is_trainer"},
            {"doctors", "is_anaesthetist"},
        };
        for (String[] tc : orphanColumns) {
            String table = tc[0], column = tc[1];
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column
                );
                if (count != null && count > 0) {
                    jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`");
                    log.info("DB migration applied: dropped legacy {}.{} column", table, column);
                }
            } catch (Exception e) {
                log.warn("DB migration skipped (drop {}.{}): {}", table, column, e.getMessage());
            }
        }
    }

    /**
     * Ensure plans.multi_outlet and plans.max_outlets exist for pharmacy chain plans.
     */
    private void ensurePlanMultiOutletColumns() {
        try {
            Integer hasMulti = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plans' AND COLUMN_NAME = 'multi_outlet'",
                Integer.class
            );
            if (hasMulti != null && hasMulti == 0) {
                jdbcTemplate.execute("ALTER TABLE plans ADD COLUMN multi_outlet TINYINT(1) NOT NULL DEFAULT 0");
                log.info("DB migration applied: plans.multi_outlet column added");
            }
            Integer hasMax = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plans' AND COLUMN_NAME = 'max_outlets'",
                Integer.class
            );
            if (hasMax != null && hasMax == 0) {
                jdbcTemplate.execute("ALTER TABLE plans ADD COLUMN max_outlets INT DEFAULT NULL");
                log.info("DB migration applied: plans.max_outlets column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (plans.multi_outlet/max_outlets): {}", e.getMessage());
        }
    }

    /**
     * Ensure hospital_settings.barcode_enabled exists. Defaults to enabled so
     * existing pharmacies keep their barcode workflow until an admin turns it off.
     */
    private void ensureHospitalSettingsBarcodeEnabled() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospital_settings' AND COLUMN_NAME = 'barcode_enabled'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE hospital_settings ADD COLUMN barcode_enabled TINYINT(1) NOT NULL DEFAULT 1"
                );
                log.info("DB migration applied: hospital_settings.barcode_enabled column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (hospital_settings.barcode_enabled): {}", e.getMessage());
        }
    }

    /**
     * The legacy `hospitals.plan VARCHAR(20) NOT NULL` column was replaced by the
     * hospital_plan_subscriptions table. Hibernate no longer writes this column, so
     * any INSERT fails with "Column 'plan' cannot be null". Make it nullable.
     */
    private void fixHospitalsPlanColumn() {
        try {
            Integer isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'plan'",
                Integer.class
            );
            if (isNullable != null && isNullable == 0) {
                jdbcTemplate.execute("ALTER TABLE hospitals MODIFY COLUMN plan VARCHAR(20) DEFAULT NULL");
                log.info("DB migration applied: hospitals.plan is now nullable");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (hospitals.plan): {}", e.getMessage());
        }
    }

    /**
     * Ensure hospital_settings.in_clinic exists and is NOT NULL.
     * ddl-auto=update may fail to add this column if rows existed at the time.
     */
    private void ensureHospitalSettingsInClinic() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospital_settings' AND COLUMN_NAME = 'in_clinic'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE hospital_settings ADD COLUMN in_clinic TINYINT(1) NOT NULL DEFAULT 0"
                );
                log.info("DB migration applied: hospital_settings.in_clinic column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (hospital_settings.in_clinic): {}", e.getMessage());
        }
    }

    /**
     * Ensure hospitals.is_single_doctor exists (added when single-doctor mode feature landed).
     */
    private void ensureHospitalsIsSingleDoctor() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals' AND COLUMN_NAME = 'is_single_doctor'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE hospitals ADD COLUMN is_single_doctor TINYINT(1) NOT NULL DEFAULT 0"
                );
                log.info("DB migration applied: hospitals.is_single_doctor column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (hospitals.is_single_doctor): {}", e.getMessage());
        }
    }

    /**
     * Creates the whatsapp_config table if it does not exist.
     * Stores hospital-specific Meta WhatsApp credentials for WHATSAPP_CUSTOM mode.
     * ddl-auto=update cannot create tables from scratch — this runner bridges that gap.
     */
    private void ensureWhatsAppConfigTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'whatsapp_config'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE whatsapp_config (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  access_token VARCHAR(500) NOT NULL," +
                    "  phone_number_id VARCHAR(100) NOT NULL," +
                    "  waba_id VARCHAR(100) DEFAULT NULL," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  send_appointments TINYINT(1) NOT NULL DEFAULT 1," +
                    "  send_billing TINYINT(1) NOT NULL DEFAULT 1," +
                    "  send_case_papers TINYINT(1) NOT NULL DEFAULT 1," +
                    "  send_prescription TINYINT(1) NOT NULL DEFAULT 1," +
                    "  send_medicine_list TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  updated_at DATETIME(6) DEFAULT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  CONSTRAINT uq_wc_hospital UNIQUE (hospital_id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: whatsapp_config table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (whatsapp_config): {}", e.getMessage());
        }
    }

    /**
     * Creates the whatsapp_message_log table if it does not exist.
     * Logs every WhatsApp send attempt (automated and broadcast) for retry tracking
     * and hospital admin visibility. No FK on hospital_id/patient_id intentionally —
     * log rows must survive hospital/patient hard deletes for audit purposes.
     */
    private void ensureWhatsAppMessageLogTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'whatsapp_message_log'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE whatsapp_message_log (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  patient_id BIGINT DEFAULT NULL," +
                    "  patient_phone VARCHAR(20) NOT NULL," +
                    "  message_type VARCHAR(50) NOT NULL," +
                    "  status VARCHAR(25) NOT NULL," +
                    "  error_message VARCHAR(500) DEFAULT NULL," +
                    "  retry_count INT NOT NULL DEFAULT 0," +
                    "  next_retry_at DATETIME(6) DEFAULT NULL," +
                    "  sent_at DATETIME(6) DEFAULT NULL," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  KEY idx_wml_hospital_status (hospital_id, status)," +
                    "  KEY idx_wml_retry (status, next_retry_at)" +
                    ")"
                );
                log.info("DB migration applied: whatsapp_message_log table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (whatsapp_message_log): {}", e.getMessage());
        }
    }

    private void ensureWhatsAppMessageLogRetryColumns() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'whatsapp_message_log' AND COLUMN_NAME = 'template_name'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE whatsapp_message_log " +
                    "ADD COLUMN template_name VARCHAR(100) DEFAULT NULL, " +
                    "ADD COLUMN template_params_json VARCHAR(1000) DEFAULT NULL, " +
                    "ADD COLUMN media_url VARCHAR(500) DEFAULT NULL");
                log.info("DB migration applied: whatsapp_message_log retry columns added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (whatsapp_message_log retry columns): {}", e.getMessage());
        }
    }

    private void ensureMissingIndexes() {
        addIndexIfMissing("appointments", "idx_appt_date", "appointment_date");
        addIndexIfMissing("patients",     "idx_patient_hospital", "hospital_id");
        addIndexIfMissing("doctors",      "idx_doctor_hospital",  "hospital_id");
    }

    private void addIndexIfMissing(String table, String indexName, String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, indexName
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE `" + table + "` ADD INDEX `" + indexName + "` (`" + column + "`)"
                );
                log.info("DB migration applied: index {} added on {}.{}", indexName, table, column);
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (index {} on {}): {}", indexName, table, e.getMessage());
        }
    }

    private void simplifyMedicineListTable() {
        try {
            // 1. Deduplicate by name and type, keeping only the first id
            jdbcTemplate.execute(
                "DELETE m1 FROM medicine_list m1 " +
                "INNER JOIN medicine_list m2 " +
                "ON LOWER(m1.name) = LOWER(m2.name) AND LOWER(m1.type) = LOWER(m2.type) " +
                "WHERE m1.id > m2.id"
            );
            log.info("DB migration applied: deduplicated medicine_list table");

            // 2. Drop columns if they exist
            String[] colsToDrop = {"default_dosage", "default_frequency", "default_duration", "manufacturer", "hospital_id", "is_active", "created_at"};
            for (String col : colsToDrop) {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medicine_list' AND COLUMN_NAME = ?",
                    Integer.class, col
                );
                if (count != null && count > 0) {
                    jdbcTemplate.execute("ALTER TABLE medicine_list DROP COLUMN `" + col + "`");
                    log.info("DB migration applied: dropped column medicine_list." + col);
                }
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (simplifyMedicineListTable): {}", e.getMessage());
        }
    }

    /**
     * Replaces patients.age (stored, goes stale every year) with
     * patients.date_of_birth (computed live by Patient.getAge()).
     *
     * date_of_birth is added and left nullable — NOT promoted to NOT NULL —
     * deliberately. PatientService already enforces "always required" at
     * the application layer, and this project has twice hit real incidents
     * (hospital_settings.shift_mode, users.is_trainer) where a NOT NULL
     * column with no default broke every insert on a populated table. This
     * migration avoids adding a third one.
     */
    private void migratePatientAgeToDateOfBirth() {
        try {
            Integer dobExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'date_of_birth'",
                Integer.class
            );
            if (dobExists != null && dobExists == 0) {
                jdbcTemplate.execute("ALTER TABLE patients ADD COLUMN date_of_birth DATE DEFAULT NULL");
                log.info("DB migration applied: patients.date_of_birth column added");
            }

            Integer ageExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients' AND COLUMN_NAME = 'age'",
                Integer.class
            );
            if (ageExists != null && ageExists > 0) {
                int updated = jdbcTemplate.update(
                    "UPDATE patients SET date_of_birth = DATE_SUB(CURDATE(), INTERVAL age YEAR) " +
                    "WHERE date_of_birth IS NULL"
                );
                log.info("DB migration applied: backfilled date_of_birth for {} patient(s) from age", updated);

                jdbcTemplate.execute("ALTER TABLE patients DROP COLUMN age");
                log.info("DB migration applied: dropped patients.age column");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (patients.age -> date_of_birth): {}", e.getMessage());
        }
    }

    /**
     * Creates the consultation_note_presets table if it does not exist.
     * Stores per-hospital quick-note phrases doctors can insert with one
     * click into Treatment Notes (and, in future, other consultation
     * fields — see field_type).
     * ddl-auto=update cannot create tables from scratch — this runner
     * bridges that gap.
     */
    private void ensureConsultationNotePresetsTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consultation_note_presets'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE consultation_note_presets (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  field_type VARCHAR(30) NOT NULL," +
                    "  text VARCHAR(255) NOT NULL," +
                    "  display_order INT NOT NULL DEFAULT 0," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: consultation_note_presets table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (consultation_note_presets): {}", e.getMessage());
        }
    }

    /**
     * Creates the prescription_presets and prescription_preset_items tables
     * if they do not exist. Stores per-hospital named bundles of medicines
     * a doctor can apply to a prescription in one action.
     * ddl-auto=update cannot create tables from scratch — this runner
     * bridges that gap.
     */
    private void ensurePrescriptionPresetTables() {
        try {
            Integer presetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescription_presets'",
                Integer.class
            );
            if (presetCount != null && presetCount == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE prescription_presets (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  name VARCHAR(150) NOT NULL," +
                    "  display_order INT NOT NULL DEFAULT 0," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: prescription_presets table created");
            }

            Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prescription_preset_items'",
                Integer.class
            );
            if (itemCount != null && itemCount == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE prescription_preset_items (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  preset_id BIGINT NOT NULL," +
                    "  medicine_name VARCHAR(255) NOT NULL," +
                    "  dosage VARCHAR(50) DEFAULT NULL," +
                    "  frequency VARCHAR(50) DEFAULT NULL," +
                    "  duration VARCHAR(50) DEFAULT NULL," +
                    "  instructions VARCHAR(200) DEFAULT NULL," +
                    "  sort_order INT NOT NULL DEFAULT 0," +
                    "  PRIMARY KEY (id)," +
                    "  FOREIGN KEY (preset_id) REFERENCES prescription_presets(id) ON DELETE CASCADE" +
                    ")"
                );
                log.info("DB migration applied: prescription_preset_items table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (prescription presets): {}", e.getMessage());
        }
    }

    /**
     * Adds a nullable doctor_id column to prescription_presets and
     * consultation_note_presets for per-doctor preset isolation. A NULL doctor_id
     * means the preset is shared (visible to every doctor in the hospital); a set
     * value scopes it privately to that doctor. Existing rows stay NULL, so they
     * remain shared — no behaviour change for data created before this migration.
     */
    private void ensurePresetDoctorIdColumns() {
        addNullableDoctorIdColumn("prescription_presets");
        addNullableDoctorIdColumn("consultation_note_presets");
    }

    private void addNullableDoctorIdColumn(String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = 'doctor_id'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN doctor_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: {}.doctor_id column added", table);
            }
        } catch (Exception e) {
            log.warn("DB migration skipped ({}.doctor_id): {}", table, e.getMessage());
        }
    }

    /**
     * Adds inventory_items.has_own_stock if it does not exist, defaulting
     * every existing row to true (1) so current catalog items keep their
     * exact current behavior (own-stock check + cascade to related items)
     * until an admin explicitly marks one as a service item.
     * ddl-auto=update can add columns but not backfill a specific default
     * for pre-existing rows in every MySQL configuration -- this runner
     * makes that explicit and idempotent.
     */
    private void ensureInventoryItemHasOwnStockColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_items' AND COLUMN_NAME = 'has_own_stock'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE inventory_items ADD COLUMN has_own_stock TINYINT(1) NOT NULL DEFAULT 1");
                log.info("DB migration applied: inventory_items.has_own_stock column added (defaulted to true for existing rows)");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (inventory_items.has_own_stock): {}", e.getMessage());
        }
    }

    /**
     * Creates the global inventory catalog + per-hospital services tables if
     * absent: inventory_master_items (platform-global item names),
     * hospital_services (per-hospital billable procedures), and
     * hospital_service_items (join to master items). Idempotent, each checked
     * independently.
     */
    private void ensureInventoryServicesTables() {
        try {
            Integer masterCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_master_items'",
                Integer.class);
            if (masterCount != null && masterCount == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE inventory_master_items (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  name VARCHAR(255) NOT NULL," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)" +
                    ")");
                log.info("DB migration applied: inventory_master_items table created");
            }

            Integer svcCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospital_services'",
                Integer.class);
            if (svcCount != null && svcCount == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE hospital_services (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  hospital_id BIGINT NOT NULL," +
                    "  name VARCHAR(150) NOT NULL," +
                    "  charge DECIMAL(10,2) NOT NULL," +
                    "  is_active TINYINT(1) NOT NULL DEFAULT 1," +
                    "  created_at DATETIME(6) NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: hospital_services table created");
            }

            Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospital_service_items'",
                Integer.class);
            if (itemCount != null && itemCount == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE hospital_service_items (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT," +
                    "  service_id BIGINT NOT NULL," +
                    "  master_item_id BIGINT NOT NULL," +
                    "  PRIMARY KEY (id)," +
                    "  FOREIGN KEY (service_id) REFERENCES hospital_services(id) ON DELETE CASCADE" +
                    ")");
                log.info("DB migration applied: hospital_service_items table created");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (inventory services tables): {}", e.getMessage());
        }
    }

    /**
     * Add nullable branch_id to audit_logs for pharmacy audit trail isolation.
     * Null = not branch-scoped (single-shop / hospital / clinic pharmacy, platform actions).
     */
    private void ensureAuditLogsBranchColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND COLUMN_NAME = 'branch_id'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN branch_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: audit_logs.branch_id column added");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (audit_logs.branch_id): {}", e.getMessage());
        }
    }

    private void ensureOpdAdmitRecommendedColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opd' AND COLUMN_NAME = 'ipd_admit_recommended'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE opd ADD COLUMN ipd_admit_recommended TINYINT(1) NOT NULL DEFAULT 0");
                log.info("DB migration applied: added ipd_admit_recommended column to opd table");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (ipd_admit_recommended): {}", e.getMessage());
        }
    }

    private void ensureNurseProfilePhaseAColumns() {
        addColumnIfMissing("nurse_profiles", "is_incharge", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("nurse_profiles", "gender", "VARCHAR(10) NULL");
        addColumnIfMissing("nurse_profiles", "qualification", "VARCHAR(120) NULL");
        addColumnIfMissing("nurse_profiles", "registration_number", "VARCHAR(60) NULL");
        addColumnIfMissing("nurse_profiles", "joining_date", "DATE NULL");
    }

    private void ensureWardInchargeColumn() {
        addColumnIfMissing("wards", "incharge_nurse_id", "BIGINT NULL");
    }

    private void ensureSeparateNurseLoginColumn() {
        addColumnIfMissing("hospital_settings", "separate_nurse_login", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    private void ensurePerformedByNurseIdColumns() {
        for (String t : new String[]{"vitals_records","nursing_notes","medication_administrations","sugar_chart_entries","surgery_forms"}) {
            addColumnIfMissing(t, "performed_by_nurse_id", "BIGINT NULL");
        }
    }

    /** Adds a column only if it does not already exist. Idempotent. */
    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
            if (count != null && count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("DB migration applied: added {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("DB migration skipped ({}.{}): {}", table, column, e.getMessage());
        }
    }
}
