package com.hms.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The production service is launched with {@code -Dspring.profiles.active=production}, but its
 * settings live in {@code application-prod.properties}. Before the {@code production -> prod} group,
 * that profile loaded no file at all: Flyway stayed off and V12+ never ran (audit B2).
 *
 * <p>Boots an EMPTY context with the real packaged configuration, so this checks what Spring Boot
 * actually resolves from application*.properties, not what a test overrides.
 */
class ProductionProfileConfigTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    private static ConfigurableApplicationContext boot(String... profiles) {
        return new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .logStartupInfo(false)
                .run();
    }

    private static void assertProductionSchemaSettings(Environment env) {
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.baseline-version")).isEqualTo("11");
        assertThat(env.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.out-of-order")).isEqualTo("false");
        assertThat(env.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        // Additive reconciliation after Flyway, as documented in application-prod.properties
        // (HIBERNATE_DDL_AUTO can override it; it is not set in tests).
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(env.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("OFF");
    }

    @Test
    void productionProfile_loadsTheProdConfiguration() {
        try (ConfigurableApplicationContext ctx = boot("production")) {
            Environment env = ctx.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("production", "prod");
            assertProductionSchemaSettings(env);
        }
    }

    @Test
    void prodProfile_aloneIsUnchanged() {
        try (ConfigurableApplicationContext ctx = boot("prod")) {
            assertThat(ctx.getEnvironment().getActiveProfiles()).containsExactly("prod");
            assertProductionSchemaSettings(ctx.getEnvironment());
        }
    }

    @Test
    void stagingProfile_doesNotPullInProd() {
        try (ConfigurableApplicationContext ctx = boot("staging")) {
            Environment env = ctx.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("staging");
            assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");   // from application-staging
            assertThat(env.getProperty("logging.level.root")).isNotEqualTo("WARN");   // prod-only setting absent
        }
    }

    @Test
    void noProfile_keepsFlywayOff() {
        try (ConfigurableApplicationContext ctx = boot()) {
            assertThat(ctx.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(ctx.getEnvironment().getProperty("spring.flyway.enabled")).isEqualTo("false");
        }
    }
}
