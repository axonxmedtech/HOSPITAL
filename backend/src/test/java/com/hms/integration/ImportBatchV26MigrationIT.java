package com.hms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V26 migration test against real MySQL 8, through Flyway.
 *
 * Verifies Decision 1 (ImportBatch V26):
 * 1. Fresh/already-correct schema with generated active_marker is preserved.
 * 2. Legacy ordinary-column schema without duplicates is safely migrated to VIRTUAL GENERATED and UNIQUE KEY.
 * 3. Schema with duplicate active batches fails closed loudly without altering or deleting any rows.
 * 4. Migration is idempotent across multiple runs.
 */
@Testcontainers(disabledWithoutDocker = true)
class ImportBatchV26MigrationIT {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hms_v26")
            .withUsername("hms")
            .withPassword("hms");

    private static final String STRICT =
            "SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'";

    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        try (Statement s = c.createStatement()) {
            s.execute(STRICT);
        }
        return c;
    }

    private void exec(String... sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String q : sql) {
                s.execute(q);
            }
        }
    }

    @BeforeEach
    void cleanSchema() throws SQLException {
        exec("DROP TABLE IF EXISTS import_batches", "DROP TABLE IF EXISTS users", "DROP TABLE IF EXISTS flyway_schema_history");
    }

    private void createBaseImportBatchesTable() throws SQLException {
        exec("CREATE TABLE import_batches (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                "  public_id VARCHAR(36) NOT NULL," +
                "  hospital_id BIGINT NOT NULL," +
                "  entity_type VARCHAR(20) NOT NULL," +
                "  source_filename VARCHAR(255) NULL," +
                "  sheet_name VARCHAR(255) NULL," +
                "  mapping_json TEXT NULL," +
                "  file_sha256 VARCHAR(64) NOT NULL," +
                "  status VARCHAR(20) NOT NULL," +
                "  failure_reason VARCHAR(255) NULL," +
                "  total_rows INT NOT NULL DEFAULT 0," +
                "  created_count INT NOT NULL DEFAULT 0," +
                "  updated_count INT NOT NULL DEFAULT 0," +
                "  skipped_count INT NOT NULL DEFAULT 0," +
                "  needs_review_count INT NOT NULL DEFAULT 0," +
                "  failed_count INT NOT NULL DEFAULT 0," +
                "  created_by VARCHAR(100) NULL," +
                "  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)," +
                "  heartbeat_at DATETIME(6) NULL," +
                "  committed_at DATETIME(6) NULL," +
                "  undone_at DATETIME(6) NULL" +
                ") ENGINE=InnoDB");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("25") // baselined at V25 so only V26 runs
                .load()
                .migrate();
    }

    @Test
    void legacyOrdinaryColumn_noDuplicates_migratesToVirtualGeneratedAndUniqueIndex() throws SQLException {
        createBaseImportBatchesTable();
        // Add active_marker as legacy ordinary column
        exec("ALTER TABLE import_batches ADD COLUMN active_marker TINYINT NULL");
        exec("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status, active_marker) " +
                "VALUES ('b1', 10, 'PATIENT', 'sha_abc', 'COMPLETED', 1)");
        exec("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status, active_marker) " +
                "VALUES ('b2', 10, 'PATIENT', 'sha_failed', 'FAILED', NULL)");

        migrate();

        // Verify column is now VIRTUAL GENERATED
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT EXTRA, DATA_TYPE FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND COLUMN_NAME = 'active_marker'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("EXTRA")).contains("VIRTUAL GENERATED");
            assertThat(rs.getString("DATA_TYPE")).isEqualTo("tinyint");
        }

        // Verify unique index exists with correct order
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.STATISTICS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'import_batches' AND INDEX_NAME = 'uk_import_batch_active' " +
                     "ORDER BY SEQ_IN_INDEX")) {
            List<String> cols = new ArrayList<>();
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME"));
            }
            assertThat(cols).containsExactly("hospital_id", "file_sha256", "active_marker");
        }

        // Verify row count preserved
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM import_batches")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void duplicateActiveBatches_failsClosed_withoutModifyingOrDeletingRows() throws SQLException {
        createBaseImportBatchesTable();
        exec("ALTER TABLE import_batches ADD COLUMN active_marker TINYINT NULL");
        // Insert duplicate active rows for (hospital_id 10, file_sha256 'dup_sha')
        exec("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status, active_marker) " +
                "VALUES ('b1', 10, 'PATIENT', 'dup_sha', 'COMPLETED', 1)");
        exec("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status, active_marker) " +
                "VALUES ('b2', 10, 'PATIENT', 'dup_sha', 'RUNNING', 1)");

        // Migration must fail closed
        assertThatThrownBy(this::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("ERROR_V26_DUPLICATE_ACTIVE_BATCHES_FAIL_CLOSED");

        // Verify zero rows were deleted, overwritten, or modified
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM import_batches")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }

        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT public_id, status FROM import_batches ORDER BY public_id")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("public_id")).isEqualTo("b1");
            assertThat(rs.getString("status")).isEqualTo("COMPLETED");

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("public_id")).isEqualTo("b2");
            assertThat(rs.getString("status")).isEqualTo("RUNNING");
        }
    }

    @Test
    void alreadyCorrectSchema_idempotentMigrationPreservesEverything() throws SQLException {
        createBaseImportBatchesTable();
        exec("ALTER TABLE import_batches ADD COLUMN active_marker TINYINT GENERATED ALWAYS AS " +
                "(CASE WHEN status IN ('RUNNING','COMPLETED','PARTIAL') THEN 1 END) VIRTUAL");
        exec("ALTER TABLE import_batches ADD UNIQUE KEY uk_import_batch_active (hospital_id, file_sha256, active_marker)");
        exec("INSERT INTO import_batches (public_id, hospital_id, entity_type, file_sha256, status) " +
                "VALUES ('b1', 10, 'PATIENT', 'sha_1', 'COMPLETED')");

        // Run V26
        migrate();

        // Run V26 again (rehearsal / idempotency)
        migrate();

        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT active_marker FROM import_batches WHERE public_id = 'b1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("active_marker")).isEqualTo(1);
        }
    }

    @Test
    void missingImportBatchesTable_failsClosedLoudly() throws SQLException {
        // Simulating a corrupt production schema at V25 that has other tables (e.g. users) but is missing import_batches
        exec("CREATE TABLE users (id BIGINT NOT NULL PRIMARY KEY)");
        assertThatThrownBy(this::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("ERROR_V26_TABLE_IMPORT_BATCHES_DOES_NOT_EXIST_CORRUPT_SCHEMA");
        exec("DROP TABLE IF EXISTS users");
    }
}
