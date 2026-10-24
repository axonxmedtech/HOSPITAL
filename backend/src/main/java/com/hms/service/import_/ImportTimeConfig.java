package com.hms.service.import_;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the import engine, so createdAt/heartbeatAt/committedAt and the stale-batch
 * cutoff are all read from the same injectable source and tests can pin it. Uses the system
 * default zone, which is what {@code PatientService} already uses for its own timestamps and
 * what the DATETIME(6) columns store.
 */
@Configuration
public class ImportTimeConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock importClock() {
        return Clock.systemDefaultZone();
    }
}
