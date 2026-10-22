package com.hms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real MySQL regression: metadata defaults cannot become SQL; legacy values/defaults survive. */
@Testcontainers
class LegacyColumnDdlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hms_legacy_ddl").withUsername("hms").withPassword("hms");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesHistoricalValuesLiteralDefaultsAndCollationWithoutExecutingDefaultAsSql(boolean serverPrepared) {
        String url = MYSQL.getJdbcUrl();
        url += (url.contains("?") ? "&" : "?") + "useServerPrepStmts=" + serverPrepared;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                url, MYSQL.getUsername(), MYSQL.getPassword()));
        String value = "café'\\; DROP TABLE billing; --";
        jdbc.execute("DROP TABLE IF EXISTS hospital_settings,users,doctors,billing");
        jdbc.execute("CREATE TABLE billing(id BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO billing VALUES(17)");
        jdbc.execute("CREATE TABLE hospital_settings(id BIGINT PRIMARY KEY,shift_mode VARCHAR(100) " +
                "CHARACTER SET latin1 COLLATE latin1_bin NOT NULL DEFAULT _utf8mb4'café''\\\\; DROP TABLE billing; --')");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,is_trainer TINYINT(1) NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE doctors(id BIGINT PRIMARY KEY,is_anaesthetist TINYINT(1) NOT NULL DEFAULT 0)");
        jdbc.update("INSERT INTO hospital_settings VALUES(?,?)", 1L, "HISTORICAL");
        jdbc.execute("INSERT INTO users VALUES(1,0)");
        jdbc.execute("INSERT INTO doctors VALUES(1,1)");
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner();
        ReflectionTestUtils.setField(runner, "jdbcTemplate", jdbc);
        ReflectionTestUtils.invokeMethod(runner, "preserveAndRelaxLegacyNotNullColumns");
        jdbc.execute("INSERT INTO hospital_settings(id) VALUES(2)");
        jdbc.execute("INSERT INTO users(id) VALUES(2)");
        jdbc.execute("INSERT INTO doctors(id) VALUES(2)");
        assertThat(jdbc.queryForObject("SELECT shift_mode FROM hospital_settings WHERE id=1", String.class)).isEqualTo("HISTORICAL");
        assertThat(jdbc.queryForObject("SELECT shift_mode FROM hospital_settings WHERE id=2", String.class)).isEqualTo(value);
        assertThat(jdbc.queryForObject("SELECT is_trainer FROM users WHERE id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_anaesthetist FROM doctors WHERE id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_trainer FROM users WHERE id=2", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_anaesthetist FROM doctors WHERE id=2", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing WHERE id=17", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.columns " +
                "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hospital_settings' AND COLUMN_NAME='shift_mode'", String.class)).isEqualTo("latin1_bin");
        ReflectionTestUtils.invokeMethod(runner, "preserveAndRelaxLegacyNotNullColumns");
        assertThat(jdbc.queryForObject("SELECT shift_mode FROM hospital_settings WHERE id=2", String.class)).isEqualTo(value);

        // Distinguish SQL NULL from a literal 'null' default through both JDBC preparation modes.
        jdbc.execute("DROP TABLE hospital_settings,users,doctors");
        jdbc.execute("CREATE TABLE hospital_settings(id BIGINT PRIMARY KEY,shift_mode VARCHAR(20) NOT NULL DEFAULT 'null')");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,is_trainer TINYINT(1) NOT NULL)");
        jdbc.execute("CREATE TABLE doctors(id BIGINT PRIMARY KEY,is_anaesthetist TINYINT(1) NOT NULL)");
        ReflectionTestUtils.invokeMethod(runner, "preserveAndRelaxLegacyNotNullColumns");
        jdbc.execute("INSERT INTO hospital_settings(id) VALUES(3)");
        jdbc.execute("INSERT INTO users(id) VALUES(3)");
        jdbc.execute("INSERT INTO doctors(id) VALUES(3)");
        assertThat(jdbc.queryForObject("SELECT shift_mode FROM hospital_settings WHERE id=3", String.class)).isEqualTo("null");
        assertThat(jdbc.queryForObject("SELECT is_trainer FROM users WHERE id=3", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT is_anaesthetist FROM doctors WHERE id=3", Integer.class)).isNull();
    }
}
