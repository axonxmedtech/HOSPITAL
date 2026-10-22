package com.hms.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Readiness follows the runner: it stays out of service until the runner returns, turns UP only
 * when every required schema object is present, and is DOWN if a required object is missing or a
 * fail-closed step throws. The required-schema probe runs against a real H2 database; the
 * MySQL-specific steps themselves are skipped (they are covered by the MySQL integration tests).
 */
class DatabaseMigrationRunnerReadinessTest {

    private JdbcTemplate jdbc;
    private StartupInitializationState state;
    private DatabaseMigrationRunner runner;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:readiness-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        state = new StartupInitializationState(true);
        runner = spy(new DatabaseMigrationRunner());
        ReflectionTestUtils.setField(runner, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(runner, "startupState", state);
        doNothing().when(runner).runAllSteps();
    }

    private void createRequiredSchema() {
        jdbc.execute("CREATE TABLE icu_wards (id BIGINT, hospital_id BIGINT, ward_id BIGINT)");
        jdbc.execute("CREATE TABLE icu_stay (id BIGINT, hospital_id BIGINT, ipd_admission_id BIGINT,"
                + " status VARCHAR(10), active_marker BIGINT)");
        jdbc.execute("CREATE TABLE vitals_records (id BIGINT, gcs_eye INT, gcs_verbal INT, gcs_motor INT,"
                + " gcs_total INT, map_mmhg INT, cvp_cmh2o INT, urine_output_ml INT, supersedes_vitals_id BIGINT)");
    }

    @Test
    void allRequiredObjectsPresent_andTheRunnerFinished_isReady() {
        createRequiredSchema();

        runner.runMigrations();

        assertThat(state.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void emptyTablesAreFine_requiredObjectsNotData() {
        // No ICU ward or stay rows at all: a hospital with ICU disabled must still be ready.
        createRequiredSchema();

        runner.runMigrations();

        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.COMPLETE);
    }

    @Test
    void aMissingRequiredTable_failsClosedAndAbortsStartup() {
        createRequiredSchema();
        jdbc.execute("DROP TABLE icu_stay");

        assertThatThrownBy(runner::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("icu_stay");

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(runner.missingRequiredSchema()).singleElement().asString().contains("icu_stay");
    }

    @Test
    void aMissingRequiredColumn_failsClosedAndAbortsStartup() {
        createRequiredSchema();
        jdbc.execute("ALTER TABLE vitals_records DROP COLUMN gcs_total");

        assertThatThrownBy(runner::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vitals_records");

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(runner.missingRequiredSchema()).singleElement().asString().contains("vitals_records");
    }

    @Test
    void missingCvpCmh2o_failsClosedAndAbortsStartup() {
        createRequiredSchema();
        jdbc.execute("ALTER TABLE vitals_records DROP COLUMN cvp_cmh2o");

        assertThatThrownBy(runner::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cvp_cmh2o");

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(runner.missingRequiredSchema()).singleElement().asString().contains("cvp_cmh2o");
    }

    @Test
    void aFailClosedStepThrowing_isNotReady_andStillAbortsStartup() {
        createRequiredSchema();
        doThrow(new IllegalStateException("ICU ward backfill failed")).when(runner).runAllSteps();

        assertThatThrownBy(runner::runMigrations).isInstanceOf(IllegalStateException.class);

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void untilTheRunnerReports_readinessIsOutOfService() {
        assertThat(state.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void theRequiredListIsObjectsOnly_andNamesNoModuleGatedTable() {
        assertThat(DatabaseMigrationRunner.REQUIRED_SCHEMA.keySet())
                .containsExactly("icu_wards", "icu_stay", "vitals_records")
                .doesNotContain("icu_alert_threshold", "icu_infusion", "icu_ventilator_setting",
                        "surgery_anaesthesia_clearances", "surgery_emergency_overrides");
    }

    @Test
    void databaseTemporarilyUnavailable_abortsStartupAndReadinessStaysDown() {
        doThrow(new org.springframework.jdbc.CannotGetJdbcConnectionException("Connection refused"))
                .when(runner).runAllSteps();

        assertThatThrownBy(runner::runMigrations)
                .isInstanceOf(org.springframework.jdbc.CannotGetJdbcConnectionException.class);

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.FAILED);
    }

    @Test
    void partialMigrationState_failsClosedAndKeepsReadinessDown() {
        // Table exists but only partially created (missing urine_output_ml and cvp_cmh2o)
        jdbc.execute("CREATE TABLE icu_wards (id BIGINT, hospital_id BIGINT, ward_id BIGINT)");
        jdbc.execute("CREATE TABLE icu_stay (id BIGINT, hospital_id BIGINT, ipd_admission_id BIGINT, status VARCHAR(10), active_marker BIGINT)");
        jdbc.execute("CREATE TABLE vitals_records (id BIGINT, gcs_eye INT, gcs_verbal INT, gcs_motor INT, gcs_total INT, map_mmhg INT)");

        assertThatThrownBy(runner::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vitals_records");

        assertThat(state.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(runner.missingRequiredSchema()).anyMatch(s -> s.contains("cvp_cmh2o") || s.contains("urine_output_ml"));
    }

    @Test
    void repeatedApplicationStartup_isIdempotentAndRemainsReady() {
        createRequiredSchema();

        // First startup
        runner.runMigrations();
        assertThat(state.health().getStatus()).isEqualTo(Status.UP);
        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.COMPLETE);

        // Second startup rehearsal against same schema
        runner.runMigrations();
        assertThat(state.health().getStatus()).isEqualTo(Status.UP);
        assertThat(state.phase()).isEqualTo(StartupInitializationState.Phase.COMPLETE);
    }
}
