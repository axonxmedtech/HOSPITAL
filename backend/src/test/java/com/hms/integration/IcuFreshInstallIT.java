package com.hms.integration;

import com.hms.config.DatabaseMigrationRunner;
import com.hms.entity.Ward;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The FRESH-INSTALL half of the {@code wards.unit_type} lifecycle, against a real MySQL.
 *
 * <p>{@link IcuBackfillIT} covers the UPGRADE path: a pre-ICU database that still carries the
 * legacy {@code wards.unit_type} classification, which the migration copies into {@code icu_wards}
 * and then deliberately <em>retains</em> rather than dropping.
 *
 * <p>This class covers the other path. {@code unit_type} is a <b>legacy compatibility column</b>:
 * it exists only on databases that predate the dedicated {@code icu_wards} table. It is NOT part
 * of the canonical schema — the {@link Ward} entity no longer maps it, so Hibernate never creates
 * it, and {@code setup/schema-full.sql} does not declare it on {@code wards}. A brand new hospital
 * therefore runs forever without the column.
 *
 * <p>That asymmetry is intentional, but it is only safe while two things hold, and both are
 * asserted here rather than left to a comment:
 *
 * <ol>
 *   <li>the migration must not fail closed on a database where the column never existed — the
 *       backfill now throws on failure, so an unguarded reference to a missing column would abort
 *       startup for every new customer;</li>
 *   <li>no ICU ward or ICU stay behaviour may depend on the legacy column — classification is
 *       owned by {@code icu_wards.unit_type}.</li>
 * </ol>
 *
 * <p>Run with the same explicit datasource the other MySQL ITs use:
 * <pre>-Dhms.it.mysql.url=… -Dhms.it.mysql.username=… -Dhms.it.mysql.password=…</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "hms.it.mysql.url", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class IcuFreshInstallIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("hms.it.mysql.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("hms.it.mysql.username", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("hms.it.mysql.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("hms.migrations.enabled", () -> "false");
        registry.add("spring.cache.type", () -> "simple");
    }

    @Autowired JdbcTemplate jdbc;

    private String uniq() { return Long.toString(System.nanoTime()); }

    private int legacyColumnCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wards' AND COLUMN_NAME='unit_type'",
                Integer.class);
    }

    /**
     * A fresh install never had the column. Spring may hand this class the same cached context —
     * and therefore the same database — that {@link IcuBackfillIT} added the legacy column to, so
     * drop it here instead of depending on class execution order.
     */
    @BeforeAll
    void freshSchemaWithoutTheLegacyColumn() {
        if (legacyColumnCount() > 0) {
            jdbc.execute("ALTER TABLE wards DROP COLUMN unit_type");
        }
    }

    /** Invoke the real production orchestration, not a copy of its SQL. */
    private void runMigration() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner();
        ReflectionTestUtils.setField(runner, "jdbcTemplate", jdbc);
        ReflectionTestUtils.invokeMethod(runner, "migrateIcuWardsAndStays");
    }

    private int activeStays() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM icu_stay WHERE status='ACTIVE'", Integer.class);
    }

    /**
     * The canonical-schema claim itself: nothing in the persistent model maps the legacy column,
     * which is why {@code ddl-auto} never recreates it and fresh installs never gain it.
     */
    @Test
    void theWardEntityDoesNotMapTheLegacyClassification() {
        boolean mapped = Arrays.stream(Ward.class.getDeclaredFields()).anyMatch(f -> {
            Column c = f.getAnnotation(Column.class);
            return "unitType".equals(f.getName())
                    || (c != null && "unit_type".equals(c.name()));
        });
        assertThat(mapped)
                .as("wards.unit_type is legacy-only; mapping it again would resurrect it on every "
                        + "fresh install and re-open the drop-vs-retain question")
                .isFalse();

        assertThat(fieldNames()).as("classification is owned by icu_wards").doesNotContain("unitType");
    }

    private java.util.List<String> fieldNames() {
        return Arrays.stream(Ward.class.getDeclaredFields()).map(Field::getName).toList();
    }

    /**
     * The regression this class exists for: the backfill now throws instead of warning, so a
     * fresh database — where {@code wards.unit_type} has never existed — must still migrate
     * cleanly. If this fails, every new hospital fails to start.
     */
    @Test
    void migrationSucceedsOnADatabaseThatNeverHadTheLegacyColumn_andIsIdempotent() {
        assertThat(legacyColumnCount()).as("precondition: a genuinely fresh schema").isZero();

        assertThatCode(this::runMigration)
                .as("fail-closed backfill must not fail a fresh install")
                .doesNotThrowAnyException();

        // Second execution: the runner runs on every ApplicationReadyEvent, so it must stay a no-op.
        assertThatCode(this::runMigration).doesNotThrowAnyException();

        assertThat(legacyColumnCount()).as("nothing recreated the legacy column").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='icu_wards'", Integer.class))
                .as("the dedicated ICU ward table is what a fresh install gets instead")
                .isEqualTo(1);
    }

    /**
     * ICU ward and ICU stay behaviour must be reachable with no legacy column anywhere: a ward
     * registered directly in {@code icu_wards} still produces an ACTIVE stay for its occupant.
     */
    @Test
    void icuWardAndStayFunctionalityDoesNotRequireTheLegacyColumn() {
        assertThat(legacyColumnCount()).isZero();
        runMigration();   // creates icu_wards on the fresh schema

        long hospitalId = 940000L + (System.nanoTime() % 1000);

        jdbc.update("INSERT INTO wards (hospital_id, ward_name, bed_price, total_beds, created_at)"
                + " VALUES (?,?,?,?,NOW(6))", hospitalId, "ICU-" + uniq(), 2500, 2);
        Long wardId = jdbc.queryForObject("SELECT MAX(ward_id) FROM wards", Long.class);

        // Classification lives here now -- not on wards.
        jdbc.update("INSERT INTO icu_wards (public_id, hospital_id, ward_id, ward_name, unit_type,"
                + " bed_price, total_beds, created_at) VALUES (UUID(),?,?,?,?,?,?,NOW(6))",
                hospitalId, wardId, "ICU-" + uniq(), "MICU", 2500, 2);

        jdbc.update("INSERT INTO ipd_admission (ipd_number, patient_id, doctor_id, hospital_id,"
                + " admission_type, status, admission_datetime, ward_id, bed_id, admission_confirmed)"
                + " VALUES (?,?,?,?,?,?,NOW(6),?,?,1)",
                "IPDF-" + uniq(), 1L, 1L, hospitalId, "ELECTIVE", "ADMITTED", wardId, 1L);
        Long admissionId = jdbc.queryForObject("SELECT MAX(id) FROM ipd_admission", Long.class);

        int before = activeStays();
        runMigration();

        assertThat(activeStays())
                .as("the occupant of an icu_wards ward gets a stay with no legacy classification present")
                .isEqualTo(before + 1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM icu_stay WHERE ipd_admission_id=? AND status='ACTIVE'",
                Integer.class, admissionId)).isEqualTo(1);
        assertThat(legacyColumnCount()).isZero();
    }
}
