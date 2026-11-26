package com.hms.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V46 backfills BILLING onto every plan and hospital that holds IPD. Runs the real migration file
 * (twice, to prove it is idempotent) against an in-memory database in MySQL mode.
 */
class V46IpdIncludesBillingMigrationTest {

    private static final Path V46 = Path.of("src/main/resources/db/migration/V46__ipd_includes_billing.sql");

    @Test
    void ipdRowsGainBilling_opdOnlyRowsAreUntouched_andASecondRunChangesNothing() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:v46;MODE=MySQL;DB_CLOSE_DELAY=-1");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE plan_modules (plan_id BIGINT NOT NULL, module_name VARCHAR(255))");
            s.execute("CREATE TABLE hospital_modules (hospital_id BIGINT NOT NULL, module_name VARCHAR(255))");
            s.execute("INSERT INTO plan_modules VALUES (1,'OPD'),(1,'IPD'),(2,'OPD'),(3,'IPD'),(3,'BILLING')");
            s.execute("INSERT INTO hospital_modules VALUES (10,'OPD'),(10,'IPD'),(10,'ICU'),(11,'OPD'),(12,'IPD'),(12,'BILLING')");

            String sql = Files.readString(V46);
            for (int run = 0; run < 2; run++) {
                for (String stmt : sql.replaceAll("(?m)^--.*$", "").split(";")) {
                    if (!stmt.isBlank()) s.execute(stmt);
                }
            }

            assertThat(modules(s, "plan_modules", "plan_id")).isEqualTo(Map.of(
                    1L, new TreeSet<>(java.util.List.of("BILLING", "IPD", "OPD")),
                    2L, new TreeSet<>(java.util.List.of("OPD")),
                    3L, new TreeSet<>(java.util.List.of("BILLING", "IPD"))));
            assertThat(modules(s, "hospital_modules", "hospital_id")).isEqualTo(Map.of(
                    10L, new TreeSet<>(java.util.List.of("BILLING", "ICU", "IPD", "OPD")),
                    11L, new TreeSet<>(java.util.List.of("OPD")),
                    12L, new TreeSet<>(java.util.List.of("BILLING", "IPD"))));

            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM hospital_modules WHERE module_name = 'BILLING'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("no duplicate BILLING rows after two runs").isEqualTo(2);
            }
        }
    }

    private static Map<Long, TreeSet<String>> modules(Statement s, String table, String key) throws Exception {
        Map<Long, TreeSet<String>> out = new TreeMap<>();
        try (ResultSet rs = s.executeQuery("SELECT " + key + ", module_name FROM " + table)) {
            while (rs.next()) out.computeIfAbsent(rs.getLong(1), k -> new TreeSet<>()).add(rs.getString(2));
        }
        return out;
    }
}
