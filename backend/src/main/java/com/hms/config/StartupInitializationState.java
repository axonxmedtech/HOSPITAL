package com.hms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Whether the post-start schema initialisation the application depends on has finished.
 *
 * <p>Exposed as the {@code startupInitialization} health indicator and included in the readiness
 * group, so {@code /actuator/health/readiness} cannot report UP while that work is still running
 * or after it failed. A deployment waits on readiness, so this is what stops a release being
 * declared healthy on a web server that is up while {@link DatabaseMigrationRunner} -- which runs
 * on ApplicationReadyEvent, after the server has started -- has not finished or has failed.
 *
 * <ul>
 *   <li><b>STARTING</b> until the runner reports, so readiness is OUT_OF_SERVICE meanwhile.</li>
 *   <li><b>COMPLETE</b> once it finished and every required schema object is present.</li>
 *   <li><b>FAILED</b> if a required object is missing or the runner threw; readiness is DOWN.</li>
 * </ul>
 *
 * <p>When the runner is disabled ({@code hms.migrations.enabled=false}, as in tests) there is no
 * initialisation to wait for, so the state starts COMPLETE.
 *
 * <p>The health response carries no details: the endpoint is public, and which schema object is
 * missing is server-side information. The reason is logged instead, for the deployment logs.
 */
@Component("startupInitializationHealthIndicator")
public class StartupInitializationState implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializationState.class);

    public enum Phase { STARTING, COMPLETE, FAILED }

    private volatile Phase phase;

    public StartupInitializationState(
            @Value("${hms.migrations.enabled:true}") boolean migrationsEnabled) {
        this.phase = migrationsEnabled ? Phase.STARTING : Phase.COMPLETE;
    }

    public Phase phase() {
        return phase;
    }

    /** The runner finished and every required object is present. */
    public synchronized void complete() {
        if (phase == Phase.FAILED) {
            return;
        }
        phase = Phase.COMPLETE;
        log.info("Startup initialization complete");
    }

    /**
     * The runner failed, or a required object is missing. Terminal: a later {@link #complete()}
     * does not hide an earlier failure.
     */
    public synchronized void fail(List<String> reasons) {
        phase = Phase.FAILED;
        log.error("Startup initialization FAILED; readiness will stay DOWN: {}", reasons);
    }

    @Override
    public Health health() {
        return switch (phase) {
            case COMPLETE -> Health.up().build();
            case STARTING -> Health.outOfService().build();
            case FAILED -> Health.down().build();
        };
    }
}
