package com.hms.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.RepairResult;
import org.flywaydb.core.api.output.RepairOutput;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Task P0-02 (Flyway migration handling safety).
 *
 * <p>Ensures that under normal startup:
 * <ul>
 *   <li>{@code flyway.repair()} is never called.</li>
 *   <li>{@code flyway.migrate()} is executed directly.</li>
 *   <li>Checksum mismatches and failed migrations fail closed without repair.</li>
 * </ul>
 * And under explicit override ({@code hms.flyway.repair-on-startup=true}):
 * <ul>
 *   <li>{@code flyway.repair()} is executed before {@code flyway.migrate()}.</li>
 * </ul>
 */
class FlywayRepairMigrationStrategyTest {

    @Test
    void defaultStartup_doesNotInvokeRepair_andExecutesMigrate() {
        FlywayRepairMigrationStrategy strategy = new FlywayRepairMigrationStrategy();
        ReflectionTestUtils.setField(strategy, "repairOnStartup", false);

        Flyway flyway = mock(Flyway.class);
        FlywayMigrationStrategy migrationStrategy = strategy.safeFlywayMigrationStrategy();

        migrationStrategy.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway, times(1)).migrate();
    }

    @Test
    void defaultStartup_checksumMismatch_failsClosedWithoutRepair() {
        FlywayRepairMigrationStrategy strategy = new FlywayRepairMigrationStrategy();
        ReflectionTestUtils.setField(strategy, "repairOnStartup", false);

        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(new FlywayValidateException(null, "Migration checksum mismatch for V24"));

        FlywayMigrationStrategy migrationStrategy = strategy.safeFlywayMigrationStrategy();

        assertThatThrownBy(() -> migrationStrategy.migrate(flyway))
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Migration checksum mismatch");

        verify(flyway, never()).repair();
        verify(flyway, times(1)).migrate();
    }

    @Test
    void defaultStartup_failedMigrationRow_failsClosedWithoutRepair() {
        FlywayRepairMigrationStrategy strategy = new FlywayRepairMigrationStrategy();
        ReflectionTestUtils.setField(strategy, "repairOnStartup", false);

        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(new FlywayException("Schema contains a failed migration to version 24"));

        FlywayMigrationStrategy migrationStrategy = strategy.safeFlywayMigrationStrategy();

        assertThatThrownBy(() -> migrationStrategy.migrate(flyway))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("failed migration");

        verify(flyway, never()).repair();
        verify(flyway, times(1)).migrate();
    }

    @Test
    void explicitOverride_repairOnStartupTrue_invokesRepairThenMigrate() {
        FlywayRepairMigrationStrategy strategy = new FlywayRepairMigrationStrategy();
        ReflectionTestUtils.setField(strategy, "repairOnStartup", true);

        Flyway flyway = mock(Flyway.class);
        RepairResult repairResult = new RepairResult("dummy-version", "dummy-db");
        repairResult.migrationsRemoved = List.of(new RepairOutput("V24", "Failed row"));
        repairResult.migrationsAligned = List.of(new RepairOutput("V24", "Checksum aligned"));
        when(flyway.repair()).thenReturn(repairResult);

        FlywayMigrationStrategy migrationStrategy = strategy.safeFlywayMigrationStrategy();

        migrationStrategy.migrate(flyway);

        verify(flyway, times(1)).repair();
        verify(flyway, times(1)).migrate();
    }
}
