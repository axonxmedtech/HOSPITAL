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
}
