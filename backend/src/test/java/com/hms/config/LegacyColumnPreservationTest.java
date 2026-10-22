package com.hms.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

/**
 * Verifies Decision 2 (Legacy Column Preservation).
 * Proves that:
 * 1. Historical non-null sentinel values survive migration and startup without data loss.
 * 2. New inserts omitting unmapped legacy columns succeed without MySQL error 1364.
 * 3. The migration is idempotent across repeated application startups.
 */
class LegacyColumnPreservationTest {

    private JdbcTemplate jdbc;
    private DatabaseMigrationRunner runner;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:legacy-col-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);

        // Create legacy schema variants matching known legacy definitions
        jdbc.execute("CREATE TABLE hospital_settings (id BIGINT PRIMARY KEY AUTO_INCREMENT, hospital_id BIGINT, shift_mode VARCHAR(50) NOT NULL)");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100), is_trainer TINYINT(1) NOT NULL)");
        jdbc.execute("CREATE TABLE doctors (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100), is_anaesthetist TINYINT(1) NOT NULL)");

        // Insert sentinel rows with legacy data
        jdbc.execute("INSERT INTO hospital_settings (hospital_id, shift_mode) VALUES (101, 'ROTATIONAL')");
        jdbc.execute("INSERT INTO users (name, is_trainer) VALUES ('Dr. Trainer', 1)");
        jdbc.execute("INSERT INTO doctors (name, is_anaesthetist) VALUES ('Dr. Anaesthesia', 1)");

        runner = spy(new DatabaseMigrationRunner());
        ReflectionTestUtils.setField(runner, "jdbcTemplate", jdbc);
    }

    @Test
    void historicalValuesSurviveMigration_andNewInsertsSucceedWithoutError1364() throws Exception {
        // Run migration (first startup)
        ReflectionTestUtils.invokeMethod(runner, "preserveAndRelaxLegacyNotNullColumns");

        // 1. Verify historical values are intact
        Map<String, Object> setting = jdbc.queryForMap("SELECT * FROM hospital_settings WHERE hospital_id = 101");
        assertThat(setting.get("shift_mode")).isEqualTo("ROTATIONAL");

        Map<String, Object> user = jdbc.queryForMap("SELECT * FROM users WHERE name = 'Dr. Trainer'");
        assertThat(user.get("is_trainer")).isEqualTo(1);

        Map<String, Object> doctor = jdbc.queryForMap("SELECT * FROM doctors WHERE name = 'Dr. Anaesthesia'");
        assertThat(doctor.get("is_anaesthetist")).isEqualTo(1);

        // 2. Verify new inserts omitting unmapped columns succeed (no MySQL 1364 error)
        jdbc.execute("INSERT INTO hospital_settings (hospital_id) VALUES (202)");
        jdbc.execute("INSERT INTO users (name) VALUES ('New User')");
        jdbc.execute("INSERT INTO doctors (name) VALUES ('New Doctor')");

        Map<String, Object> newSetting = jdbc.queryForMap("SELECT * FROM hospital_settings WHERE hospital_id = 202");
        assertThat(newSetting.get("shift_mode")).isNull();

        Map<String, Object> newUser = jdbc.queryForMap("SELECT * FROM users WHERE name = 'New User'");
        assertThat(newUser.get("is_trainer")).isNull();

        // 3. Second startup rehearsal (idempotency check)
        ReflectionTestUtils.invokeMethod(runner, "preserveAndRelaxLegacyNotNullColumns");

        // Sentinel data must still be intact
        assertThat(jdbc.queryForObject("SELECT shift_mode FROM hospital_settings WHERE hospital_id = 101", String.class))
                .isEqualTo("ROTATIONAL");
        assertThat(jdbc.queryForObject("SELECT is_trainer FROM users WHERE name = 'Dr. Trainer'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_anaesthetist FROM doctors WHERE name = 'Dr. Anaesthesia'", Integer.class))
                .isEqualTo(1);
    }
}
