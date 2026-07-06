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
}
