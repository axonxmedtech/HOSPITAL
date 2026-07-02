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
        fixLabOrdersMedicalRecordIdColumn();
        fixRadiologyOrdersMedicalRecordIdColumn();
        backfillDischargeSummaryTenantColumns();
        backfillVitalSignsStructuredBp();
        decoupleNurseTasksSchema();
        migratePatientModelSchema();
        migrateStaffIdentitySchema();
        migrateAdmissionMonitoringSchema();
        migrateSignatureNotificationSchema();
        migrateAdmissionCoreSchema();
        migrateBillingHardeningSchema();
        migrateInventoryProcurementSchema();
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

    private void fixLabOrdersMedicalRecordIdColumn() {
        try {
            Integer isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_orders' AND COLUMN_NAME = 'medical_record_id'",
                Integer.class
            );
            if (isNullable != null && isNullable == 0) {
                jdbcTemplate.execute("ALTER TABLE lab_orders MODIFY COLUMN medical_record_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: lab_orders.medical_record_id is now nullable");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (lab_orders.medical_record_id): {}", e.getMessage());
        }
    }

    private void fixRadiologyOrdersMedicalRecordIdColumn() {
        try {
            Integer isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'radiology_orders' AND COLUMN_NAME = 'medical_record_id'",
                Integer.class
            );
            if (isNullable != null && isNullable == 0) {
                jdbcTemplate.execute("ALTER TABLE radiology_orders MODIFY COLUMN medical_record_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: radiology_orders.medical_record_id is now nullable");
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (radiology_orders.medical_record_id): {}", e.getMessage());
        }
    }

    /**
     * Backfills discharge_summary.hospital_id / patient_id / doctor_id from the parent
     * ipd_admission for rows created before those columns existed. Additive + idempotent:
     * only touches rows where hospital_id IS NULL, and no-ops once every row is stamped.
     */
    private void backfillDischargeSummaryTenantColumns() {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discharge_summary' AND COLUMN_NAME = 'hospital_id'",
                Integer.class
            );
            if (columnExists == null || columnExists == 0) {
                return; // ddl-auto has not created the column yet; nothing to backfill
            }

            Integer nullRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM discharge_summary WHERE hospital_id IS NULL",
                Integer.class
            );
            if (nullRows == null || nullRows == 0) {
                return; // already fully backfilled
            }

            int updated = jdbcTemplate.update(
                "UPDATE discharge_summary ds " +
                "JOIN ipd_admission ia ON ia.id = ds.ipd_admission_id " +
                "SET ds.hospital_id = ia.hospital_id, " +
                "    ds.patient_id  = ia.patient_id, " +
                "    ds.doctor_id   = ia.doctor_id " +
                "WHERE ds.hospital_id IS NULL"
            );
            log.info("DB migration applied: backfilled {} discharge_summary tenant column rows", updated);
        } catch (Exception e) {
            log.warn("DB migration skipped (discharge_summary tenant backfill): {}", e.getMessage());
        }
    }

    /**
     * Backfills vital_signs.bp_systolic / bp_diastolic from a well-formed legacy
     * blood_pressure string (e.g. "120/80"). Additive + idempotent: only rows where
     * bp_systolic IS NULL and blood_pressure matches a numeric "n/n" pattern.
     */
    private void backfillVitalSignsStructuredBp() {
        try {
            Integer columnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vital_signs' AND COLUMN_NAME = 'bp_systolic'",
                Integer.class
            );
            if (columnExists == null || columnExists == 0) {
                return; // ddl-auto has not created the column yet
            }

            Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vital_signs " +
                "WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$'",
                Integer.class
            );
            if (pending == null || pending == 0) {
                return; // nothing to backfill
            }

            int updated = jdbcTemplate.update(
                "UPDATE vital_signs " +
                "SET bp_systolic  = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure, ' ', '/'), '/', 1) AS UNSIGNED), " +
                "    bp_diastolic = CAST(SUBSTRING_INDEX(REPLACE(blood_pressure, ' ', '/'), '/', -1) AS UNSIGNED) " +
                "WHERE bp_systolic IS NULL AND blood_pressure REGEXP '^[0-9]+[/ ][0-9]+$'"
            );
            log.info("DB migration applied: backfilled {} vital_signs structured BP rows", updated);
        } catch (Exception e) {
            log.warn("DB migration skipped (vital_signs structured BP backfill): {}", e.getMessage());
        }
    }

    /**
     * Modifies nurse_tasks to make doctor_order_id nullable and backfills source.
     * Additive + idempotent: modifying column nullability and setting source is safe to rerun.
     */
    private void decoupleNurseTasksSchema() {
        try {
            // 1. Make doctor_order_id nullable
            Integer isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_tasks' AND COLUMN_NAME = 'doctor_order_id'",
                Integer.class
            );
            if (isNullable != null && isNullable == 0) {
                jdbcTemplate.execute("ALTER TABLE nurse_tasks MODIFY COLUMN doctor_order_id BIGINT DEFAULT NULL");
                log.info("DB migration applied: nurse_tasks.doctor_order_id is now nullable");
            }

            // 2. Backfill source column for existing rows
            Integer sourceColumnExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'nurse_tasks' AND COLUMN_NAME = 'source'",
                Integer.class
            );
            if (sourceColumnExists != null && sourceColumnExists > 0) {
                Integer pendingSource = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM nurse_tasks WHERE source IS NULL",
                    Integer.class
                );
                if (pendingSource != null && pendingSource > 0) {
                    int updated = jdbcTemplate.update(
                        "UPDATE nurse_tasks SET source = 'DOCTOR_ORDER' WHERE source IS NULL"
                    );
                    log.info("DB migration applied: backfilled {} nurse_tasks rows with source='DOCTOR_ORDER'", updated);
                }
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (decoupleNurseTasksSchema): {}", e.getMessage());
        }
    }

    /**
     * Modifies patient schema to backfill age, date_of_birth, and generate sequential UHID values.
     * Additive + idempotent: updates existing records and returns early if nothing to do.
     */
    private void migratePatientModelSchema() {
        try {
            // 1. Generate UHID for existing patients if NULL
            Integer countNullUhid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE uhid IS NULL",
                Integer.class
            );
            if (countNullUhid != null && countNullUhid > 0) {
                int updated = jdbcTemplate.update(
                    "UPDATE patients SET uhid = CONCAT('UHID-', hospital_id, '-', id) WHERE uhid IS NULL"
                );
                log.info("DB migration applied: generated {} patient UHIDs", updated);
            }

            // 2. Synchronize DOB -> Age
            int dobToAge = jdbcTemplate.update(
                "UPDATE patients SET age = TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE()) " +
                "WHERE age IS NULL AND date_of_birth IS NOT NULL"
            );
            if (dobToAge > 0) {
                log.info("DB migration applied: synced date_of_birth -> age for {} patients", dobToAge);
            }

            // 3. Synchronize Age -> DOB (estimate birth date using Jan 1 of estimated birth year)
            int ageToDob = jdbcTemplate.update(
                "UPDATE patients SET date_of_birth = DATE_SUB(CONCAT(YEAR(CURDATE()) - age, '-01-01'), INTERVAL 0 DAY) " +
                "WHERE date_of_birth IS NULL AND age IS NOT NULL"
            );
            if (ageToDob > 0) {
                log.info("DB migration applied: synced age -> date_of_birth for {} patients", ageToDob);
            }
        } catch (Exception e) {
            log.warn("DB migration skipped (migratePatientModelSchema): {}", e.getMessage());
        }
    }

    /**
     * Modifies staff/users schema to add user_id foreign keys, capacity flags,
     * and backfills existing doctors/nurses using matched emails.
     */
    private void migrateStaffIdentitySchema() {
        try {
            // Add columns to users if they don't exist
            addColumnIfNotExists("users", "department", "VARCHAR(100) DEFAULT NULL");
            addColumnIfNotExists("users", "designation", "VARCHAR(100) DEFAULT NULL");
            addColumnIfNotExists("users", "is_trainer", "BIT(1) NOT NULL DEFAULT b'0'");

            // Add columns to doctors if they don't exist
            addColumnIfNotExists("doctors", "user_id", "BIGINT DEFAULT NULL");
            addColumnIfNotExists("doctors", "is_anaesthetist", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("doctors", "is_surgeon", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("doctors", "is_pathologist", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("doctors", "is_radiologist", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("doctors", "is_intensivist", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("doctors", "is_cmo", "BIT(1) NOT NULL DEFAULT b'0'");

            // Add columns to nurses if they don't exist
            addColumnIfNotExists("nurses", "user_id", "BIGINT DEFAULT NULL");
            addColumnIfNotExists("nurses", "is_scrub", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("nurses", "is_ot", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("nurses", "is_pacu", "BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfNotExists("nurses", "is_icu", "BIT(1) NOT NULL DEFAULT b'0'");

            // Backfill user_id on doctors by matching email
            int updatedDocs = jdbcTemplate.update(
                "UPDATE doctors d JOIN users u ON d.email = u.email SET d.user_id = u.id WHERE d.user_id IS NULL"
            );
            if (updatedDocs > 0) {
                log.info("DB migration applied: backfilled user_id for {} doctors", updatedDocs);
            }

            // Backfill user_id on nurses by matching email
            int updatedNurses = jdbcTemplate.update(
                "UPDATE nurses n JOIN users u ON n.email = u.email SET n.user_id = u.id WHERE n.user_id IS NULL"
            );
            if (updatedNurses > 0) {
                log.info("DB migration applied: backfilled user_id for {} nurses", updatedNurses);
            }

        } catch (Exception e) {
            log.warn("DB migration skipped (migrateStaffIdentitySchema): {}", e.getMessage());
        }
    }

    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = ? " +
                "AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
                log.info("DB migration applied: added column {}.{}", tableName, columnName);
            }
        } catch (Exception e) {
            log.warn("Failed to check or add column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    /**
     * Creates the monitoring_vitals table dynamically if it does not exist.
     */
    private void migrateAdmissionMonitoringSchema() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS monitoring_vitals (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  ipd_admission_id bigint NOT NULL," +
                "  hospital_id bigint NOT NULL," +
                "  context varchar(20) NOT NULL," +
                "  pulse int DEFAULT NULL," +
                "  bp_systolic int DEFAULT NULL," +
                "  bp_diastolic int DEFAULT NULL," +
                "  spo2 int DEFAULT NULL," +
                "  respiratory_rate int DEFAULT NULL," +
                "  temperature decimal(4,1) DEFAULT NULL," +
                "  recorded_by bigint DEFAULT NULL," +
                "  recorded_by_name varchar(100) DEFAULT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_mv_admission_context (ipd_admission_id, context)," +
                "  KEY idx_mv_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration applied: created/verified monitoring_vitals table");
        } catch (Exception e) {
            log.warn("DB migration skipped (migrateAdmissionMonitoringSchema): {}", e.getMessage());
        }
    }

    /**
     * Creates signature_slots and document_versions tables dynamically if they don't exist.
     */
    private void migrateSignatureNotificationSchema() {
        try {
            // Create signature_slots table if it doesn't exist
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS signature_slots (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  signer_role varchar(30) NOT NULL," +
                "  signer_name varchar(100) NOT NULL," +
                "  signer_relationship varchar(50) DEFAULT NULL," +
                "  signed_at datetime NOT NULL," +
                "  document_type varchar(50) NOT NULL," +
                "  document_id varchar(50) NOT NULL," +
                "  signature_image_base64 longtext DEFAULT NULL," +
                "  signature_hash varchar(64) DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_sig_doc (document_type, document_id)," +
                "  KEY idx_sig_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration applied: created/verified signature_slots table");

            // Create document_versions table if it doesn't exist
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS document_versions (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  document_type varchar(50) NOT NULL," +
                "  document_id varchar(50) NOT NULL," +
                "  version int NOT NULL," +
                "  content_url varchar(255) DEFAULT NULL," +
                "  updated_by bigint DEFAULT NULL," +
                "  updated_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_dv_doc (document_type, document_id)," +
                "  KEY idx_dv_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration applied: created/verified document_versions table");
        } catch (Exception e) {
            log.warn("DB migration skipped (migrateSignatureNotificationSchema): {}", e.getMessage());
        }
    }

    /**
     * Creates all Phase 1 schema tables and extends existing tables.
     */
    private void migrateAdmissionCoreSchema() {
        try {
            // 1. patient_consent
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_consent (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint DEFAULT NULL," +
                "  encounter_type varchar(20) NOT NULL," +
                "  consent_type varchar(30) NOT NULL," +
                "  version int NOT NULL," +
                "  parent_id bigint DEFAULT NULL," +
                "  status varchar(20) NOT NULL," +
                "  patient_signed boolean NOT NULL DEFAULT false," +
                "  guardian_signed boolean NOT NULL DEFAULT false," +
                "  relationship varchar(40) DEFAULT NULL," +
                "  signature_type varchar(30) DEFAULT NULL," +
                "  witness_id bigint DEFAULT NULL," +
                "  language varchar(20) NOT NULL," +
                "  interpreter_id bigint DEFAULT NULL," +
                "  signed_at datetime DEFAULT NULL," +
                "  remarks text DEFAULT NULL," +
                "  created_by bigint DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  updated_at datetime NOT NULL," +
                "  is_deleted boolean NOT NULL DEFAULT false," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_consent_patient (patient_id)," +
                "  KEY idx_consent_admission (admission_id)," +
                "  KEY idx_consent_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified patient_consent table");

            // 2. blood_consent_detail
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS blood_consent_detail (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  consent_id bigint NOT NULL UNIQUE," +
                "  explanation_given boolean NOT NULL DEFAULT false," +
                "  witness_patient_name varchar(100) DEFAULT NULL," +
                "  witness_patient_signed boolean NOT NULL DEFAULT false," +
                "  witness_hospital_name varchar(100) DEFAULT NULL," +
                "  witness_hospital_signed boolean NOT NULL DEFAULT false," +
                "  interpreter_required boolean NOT NULL DEFAULT false," +
                "  interpreter_language varchar(40) DEFAULT NULL," +
                "  interpreter_name varchar(100) DEFAULT NULL," +
                "  interpreter_signed boolean NOT NULL DEFAULT false," +
                "  blood_request_id bigint DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  CONSTRAINT fk_blood_consent_parent FOREIGN KEY (consent_id) REFERENCES patient_consent (id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified blood_consent_detail table");

            // 3. clinical_assessment
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS clinical_assessment (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  doctor_id bigint NOT NULL," +
                "  chief_complaint text NOT NULL," +
                "  history_present_illness text NOT NULL," +
                "  provisional_diagnosis text NOT NULL," +
                "  treatment_plan text NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  version int NOT NULL," +
                "  parent_id bigint DEFAULT NULL," +
                "  finalized_by bigint DEFAULT NULL," +
                "  finalized_at datetime DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  updated_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_ca_admission (admission_id)," +
                "  KEY idx_ca_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified clinical_assessment table");

            // 4. EMR history tables
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_medical_history (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  condition_name varchar(100) NOT NULL," +
                "  diagnosed_year int DEFAULT NULL," +
                "  is_active boolean NOT NULL DEFAULT true," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pmh_patient (patient_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_surgical_history (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  procedure_name varchar(150) NOT NULL," +
                "  surgery_year int DEFAULT NULL," +
                "  hospital_name varchar(100) DEFAULT NULL," +
                "  complications text DEFAULT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_psh_patient (patient_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_medication_history (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  medication_name varchar(150) NOT NULL," +
                "  dosage varchar(50) DEFAULT NULL," +
                "  frequency varchar(50) DEFAULT NULL," +
                "  compliance_status varchar(30) DEFAULT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pmdh_patient (patient_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_family_history (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  relationship varchar(50) NOT NULL," +
                "  condition_name varchar(100) NOT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pfh_patient (patient_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_social_history (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  smoking_status varchar(30) DEFAULT NULL," +
                "  alcohol_consumption varchar(30) DEFAULT NULL," +
                "  tobacco_use varchar(30) DEFAULT NULL," +
                "  dietary_habits varchar(100) DEFAULT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_psch_patient (patient_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_diagnosis (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  diagnosis_code varchar(30) DEFAULT NULL," +
                "  diagnosis_description text NOT NULL," +
                "  diagnosis_type varchar(20) NOT NULL," +
                "  recorded_by bigint NOT NULL," +
                "  recorded_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pd_admission (admission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified EMR spine tables");

            // 5. patient_risk_assessment
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS patient_risk_assessment (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  scale_type varchar(30) NOT NULL," +
                "  fall_risk varchar(10) NOT NULL," +
                "  pressure_ulcer_risk varchar(10) NOT NULL," +
                "  nutrition_risk varchar(10) NOT NULL," +
                "  mental_status varchar(50) DEFAULT NULL," +
                "  mobility_status varchar(50) DEFAULT NULL," +
                "  infection_risk boolean NOT NULL DEFAULT false," +
                "  allergy_risk boolean NOT NULL DEFAULT false," +
                "  isolation_required boolean NOT NULL DEFAULT false," +
                "  overall_risk varchar(10) NOT NULL," +
                "  inputs_json text DEFAULT NULL," +
                "  status varchar(20) NOT NULL," +
                "  version int NOT NULL," +
                "  parent_id bigint DEFAULT NULL," +
                "  assessed_by bigint NOT NULL," +
                "  reviewed_by bigint DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pra_admission (admission_id)," +
                "  KEY idx_pra_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified patient_risk_assessment table");

            // 6. vital_signs alters
            addColumnIfNotExists("vital_signs", "temp_method", "VARCHAR(50) DEFAULT NULL");
            addColumnIfNotExists("vital_signs", "pulse_rhythm", "VARCHAR(50) DEFAULT NULL");
            addColumnIfNotExists("vital_signs", "resp_pattern", "VARCHAR(50) DEFAULT NULL");
            addColumnIfNotExists("vital_signs", "bp_position", "VARCHAR(50) DEFAULT NULL");
            log.info("DB migration: altered vital_signs table");

            // 7. Fluid Management Engine
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS fluid_master (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  category varchar(30) NOT NULL," +
                "  name varchar(100) NOT NULL," +
                "  default_unit varchar(10) NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_fm_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS fluid_intake (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  type varchar(30) NOT NULL," +
                "  source_ref bigint DEFAULT NULL," +
                "  description varchar(255) NOT NULL," +
                "  volume_ml int NOT NULL," +
                "  recorded_time datetime NOT NULL," +
                "  recorded_by bigint NOT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_fi_admission (admission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS fluid_output (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  type varchar(30) NOT NULL," +
                "  description varchar(255) NOT NULL," +
                "  volume_ml int NOT NULL," +
                "  color varchar(50) DEFAULT NULL," +
                "  recorded_time datetime NOT NULL," +
                "  recorded_by bigint NOT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_fo_admission (admission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS daily_fluid_balance (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  balance_date date NOT NULL," +
                "  total_intake int NOT NULL," +
                "  total_output int NOT NULL," +
                "  net_balance int NOT NULL," +
                "  updated_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_dfb_encounter (admission_id, balance_date)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified Fluid Engine tables");

            // 8. Nursing progress & handover
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nursing_progress_note (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(36) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  patient_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  shift varchar(20) NOT NULL," +
                "  nurse_id bigint NOT NULL," +
                "  general_condition varchar(50) NOT NULL," +
                "  pain_score int NOT NULL," +
                "  remarks text DEFAULT NULL," +
                "  doctor_notified boolean NOT NULL DEFAULT false," +
                "  doctor_name varchar(100) DEFAULT NULL," +
                "  doctor_advice text DEFAULT NULL," +
                "  patient_response varchar(50) NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_npn_admission (admission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS nursing_procedure (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  progress_note_id bigint NOT NULL," +
                "  hospital_id bigint NOT NULL," +
                "  procedure_name varchar(150) NOT NULL," +
                "  performed_by bigint NOT NULL," +
                "  performed_time datetime NOT NULL," +
                "  remarks text DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  CONSTRAINT fk_nproc_parent FOREIGN KEY (progress_note_id) REFERENCES nursing_progress_note (id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS shift_handover (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  admission_id bigint NOT NULL," +
                "  shift varchar(20) NOT NULL," +
                "  outgoing_nurse_id bigint NOT NULL," +
                "  incoming_nurse_id bigint NOT NULL," +
                "  pending_tasks text DEFAULT NULL," +
                "  critical_alerts text DEFAULT NULL," +
                "  meds_due text DEFAULT NULL," +
                "  investigations_pending text DEFAULT NULL," +
                "  doctor_review_pending boolean NOT NULL DEFAULT false," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_sh_admission (admission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );
            log.info("DB migration: created/verified Nursing Engine tables");
        } catch (Exception e) {
            log.warn("DB migration skipped (migrateAdmissionCoreSchema): {}", e.getMessage());
        }
    }

    private void migrateBillingHardeningSchema() {
        try {
            // Add columns to billing if they don't exist
            addColumnIfNotExists("billing", "payer_type", "VARCHAR(20) NOT NULL DEFAULT 'PATIENT'");
            addColumnIfNotExists("billing", "advance_applied", "DECIMAL(10,2) DEFAULT 0.00");

            // Add composite unique key to billing(hospital_id, custom_id)
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing' AND INDEX_NAME = 'uq_billing_custom_id'",
                    Integer.class
                );
                if (count != null && count == 0) {
                    jdbcTemplate.execute(
                        "ALTER TABLE billing ADD CONSTRAINT uq_billing_custom_id UNIQUE (hospital_id, custom_id)"
                    );
                    log.info("DB migration applied: unique constraint uq_billing_custom_id added to billing");
                }
            } catch (Exception e) {
                log.warn("DB migration skipped (billing.uq_billing_custom_id): {}", e.getMessage());
            }

            // Create charge_master table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS charge_master (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  service_code varchar(50) NOT NULL," +
                "  name varchar(200) NOT NULL," +
                "  category varchar(50) NOT NULL," +
                "  active_price decimal(10,2) NOT NULL," +
                "  effective_from date NOT NULL," +
                "  is_active boolean NOT NULL DEFAULT true," +
                "  created_at datetime NOT NULL," +
                "  updated_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_cm_code (hospital_id, service_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // Create insurance_claim table
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS insurance_claim (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  billing_id bigint NOT NULL," +
                "  payer varchar(100) NOT NULL," +
                "  claim_amount decimal(10,2) NOT NULL," +
                "  approved_amount decimal(10,2) DEFAULT NULL," +
                "  status varchar(30) NOT NULL," +
                "  submitted_at datetime DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  updated_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_ic_billing (billing_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            log.info("DB migration: created/verified Billing Hardening / Charge Master tables");
        } catch (Exception e) {
            log.warn("DB migration skipped (migrateBillingHardeningSchema): {}", e.getMessage());
        }
    }

    private void migrateInventoryProcurementSchema() {
        try {
            // 1. department_indent
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS department_indent (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  from_department varchar(50) NOT NULL," +
                "  inventory_item_id bigint NOT NULL," +
                "  requested_qty decimal(10,2) NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  requested_by bigint DEFAULT NULL," +
                "  approved_by bigint DEFAULT NULL," +
                "  approved_by_sig text DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_di_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // 2. stock_transaction
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS stock_transaction (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  inventory_item_id bigint NOT NULL," +
                "  batch_id bigint DEFAULT NULL," +
                "  transaction_type varchar(30) NOT NULL," +
                "  quantity decimal(10,2) NOT NULL," +
                "  from_store varchar(50) DEFAULT NULL," +
                "  to_store varchar(50) DEFAULT NULL," +
                "  performed_by varchar(100) NOT NULL," +
                "  transaction_time datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_st_hospital_time (hospital_id, transaction_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // 3. purchase_requisition
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS purchase_requisition (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  public_id varchar(50) NOT NULL UNIQUE," +
                "  hospital_id bigint NOT NULL," +
                "  department varchar(50) NOT NULL," +
                "  requested_by bigint NOT NULL," +
                "  priority varchar(20) NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  required_date date NOT NULL," +
                "  items_json text DEFAULT NULL," +
                "  created_at datetime NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  KEY idx_pr_hospital (hospital_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // 4. vendor
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS vendor (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  vendor_code varchar(20) NOT NULL," +
                "  vendor_name varchar(150) NOT NULL," +
                "  gst_number varchar(15) NOT NULL," +
                "  license_number varchar(50) DEFAULT NULL," +
                "  rating decimal(3,2) DEFAULT NULL," +
                "  status varchar(20) NOT NULL," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_vendor_code (hospital_id, vendor_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // 5. purchase_order
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS purchase_order (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  vendor_id bigint NOT NULL," +
                "  po_number varchar(25) NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  approved_by bigint DEFAULT NULL," +
                "  approved_by_sig text DEFAULT NULL," +
                "  order_date datetime NOT NULL," +
                "  expected_delivery date DEFAULT NULL," +
                "  items_json text DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_po_number (hospital_id, po_number)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            // 6. vendor_invoice
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS vendor_invoice (" +
                "  id bigint NOT NULL AUTO_INCREMENT," +
                "  hospital_id bigint NOT NULL," +
                "  vendor_id bigint NOT NULL," +
                "  invoice_number varchar(50) NOT NULL," +
                "  invoice_date date NOT NULL," +
                "  amount decimal(12,2) NOT NULL," +
                "  status varchar(20) NOT NULL," +
                "  matched_po_id bigint DEFAULT NULL," +
                "  matched_grn_id bigint DEFAULT NULL," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_vendor_invoice (hospital_id, vendor_id, invoice_number)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci"
            );

            log.info("DB migration: created/verified Inventory & Procurement tables");
        } catch (Exception e) {
            log.warn("DB migration skipped (migrateInventoryProcurementSchema): {}", e.getMessage());
        }
    }
}
