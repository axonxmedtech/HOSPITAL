package com.hms.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Staging must not log SQL or bound values.
 *
 * <p>Staging's journal is collected into deployment artifacts. With Hibernate SQL at DEBUG and JDBC
 * binds at TRACE it carried staff e-mail addresses and BCrypt password hashes, and would carry any
 * patient data a query touched. Hibernate 6 uses org.hibernate.SQL for statements and
 * org.hibernate.orm.jdbc.bind / .extract for values.
 */
class StagingLoggingConfigurationTest {

    private static final List<String> SQL_LOGGERS = List.of(
            "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.hibernate.orm.jdbc.extract");
    private static final List<String> VERBOSE = List.of("DEBUG", "TRACE", "ALL");

    private static Properties load(String name) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(name));
    }

    private static String level(Properties p, String logger) {
        return p.getProperty("logging.level." + logger, "").trim().toUpperCase(Locale.ROOT);
    }

    @Test
    void stagingLogsNoSqlStatementsOrBoundValues() throws IOException {
        Properties staging = load("application-staging.properties");
        for (String logger : SQL_LOGGERS) {
            assertThat(level(staging, logger)).as(logger).isNotIn(VERBOSE);
        }
        assertThat(staging.getProperty("spring.jpa.show-sql", "false")).isEqualToIgnoringCase("false");
    }

    @Test
    void stagingApplicationLoggingIsInfo() throws IOException {
        assertThat(level(load("application-staging.properties"), "com.hms")).isNotIn(VERBOSE);
    }

    @Test
    void theBaseConfigurationDoesNotTurnSqlLoggingOnForEveryProfile() throws IOException {
        Properties base = load("application.properties");
        for (String logger : SQL_LOGGERS) {
            assertThat(level(base, logger)).as(logger).isNotIn(VERBOSE);
        }
        assertThat(base.getProperty("spring.jpa.show-sql", "false")).isEqualToIgnoringCase("false");
    }
}
