package com.hms.config;

import org.flywaydb.core.api.output.RepairResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Safe Flyway migration strategy for production and staging environments.
 *
 * <p><b>Security and Reliability Guard (Task P0-02):</b>
 * Previously, this strategy called {@code flyway.repair()} unconditionally on every boot.
 * Unconditional repair on startup silently removes failed migration rows ({@code success = 0})
 * and realigns recorded checksums without operator review, hiding accidental script changes
 * or partially applied migrations.
 *
 * <p>Under the hardened production policy:
 * <ul>
 *   <li>Standard startup defaults to {@code hms.flyway.repair-on-startup=false}.
 *       Automatic repair is strictly DISABLED. Checksum mismatches and failed migrations
 *       fail closed via {@code flyway.migrate()} validation, halting application startup.</li>
 *   <li>Emergency manual repair requires explicit operator authorization by starting the
 *       application with {@code --hms.flyway.repair-on-startup=true}. In that mode, loud warnings
 *       are emitted to audit logs, and repair runs under operator supervision.</li>
 * </ul>
 *
 * See {@code docs/operations/FLYWAY_RECOVERY.md} for operator troubleshooting procedures.
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class FlywayRepairMigrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairMigrationStrategy.class);

    @Value("${hms.flyway.repair-on-startup:false}")
    private boolean repairOnStartup;

    public boolean isRepairOnStartup() {
        return repairOnStartup;
    }

    public void setRepairOnStartup(boolean repairOnStartup) {
        this.repairOnStartup = repairOnStartup;
    }

    @Bean
    public FlywayMigrationStrategy safeFlywayMigrationStrategy() {
        return flyway -> {
            if (repairOnStartup) {
                log.warn("================================================================================");
                log.warn("EMERGENCY OVERRIDE: 'hms.flyway.repair-on-startup=true' is active.");
                log.warn("Running flyway.repair() before migrate(). This should only run under operator review.");
                log.warn("================================================================================");
                RepairResult repair = flyway.repair();
                if (repair != null) {
                    int removed = repair.migrationsRemoved == null ? 0 : repair.migrationsRemoved.size();
                    int aligned = repair.migrationsAligned == null ? 0 : repair.migrationsAligned.size();
                    log.warn("Flyway repair executed: removed {} failed migration row(s), realigned {} checksum(s).",
                            removed, aligned);
                }
            } else {
                log.info("Flyway safe migration strategy active (automatic repair: disabled).");
            }
            flyway.migrate();
        };
    }
}
