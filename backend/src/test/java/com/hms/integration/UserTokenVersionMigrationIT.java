package com.hms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V25 against real MySQL 8, through Flyway exactly as staging/production run it (audit H1).
 *
 * <p>Each test builds the {@code users} table in one of the shapes a deployed database can be in,
 * baselines Flyway at V24 (so only V25 applies) and checks the result, including the insert the
 * PREVIOUS application build performs (it does not know the column). Strict SQL mode is set
 * explicitly, as MySQL 8 does by default.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserTokenVersionMigrationIT {

    @Container
    @SuppressWarnings("resource") // lifecycle managed by the @Testcontainers extension
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hms_v25")
            .withUsername("hms")
            .withPassword("hms");

    private static final String STRICT =
            "SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'";

    /** The previous build's user insert: it has no token_version field. */
    private static final String OLD_BUILD_INSERT = "INSERT INTO users (email) VALUES ('created-by-old-build@test.invalid')";

    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        try (Statement s = c.createStatement()) {
            s.execute(STRICT);
        }
        return c;
    }

    private void exec(String... sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String q : sql) {
                s.execute(q);
            }
        }
    }

    @BeforeEach
    void cleanSchema() throws SQLException {
        exec("DROP TABLE IF EXISTS users", "DROP TABLE IF EXISTS flyway_schema_history");
    }

    /** users with the given token_version column definition, or none when null; two existing rows. */
    private void createUsers(String tokenVersionColumn, String... existingRows) throws SQLException {
        exec("CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, email VARCHAR(100) NOT NULL"
                + (tokenVersionColumn == null ? "" : ", token_version " + tokenVersionColumn) + ")");
        exec(existingRows);
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("24")   // everything up to V24 is already applied; only V25 runs
                .load()
                .migrate();
    }

    /** {DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT} of users.token_version. */
    private List<String> column() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'token_version'")) {
            assertThat(r.next()).as("token_version exists").isTrue();
            return List.of(r.getString(1), r.getString(2), String.valueOf(r.getString(3)));
        }
    }

    private List<Integer> tokenVersions() throws SQLException {
        List<Integer> out = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT token_version FROM users ORDER BY id")) {
            while (r.next()) {
                out.add(r.getInt(1));
            }
        }
        return out;
    }

    private String v25State() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '25'")) {
            return r.next() ? r.getString(1) : "absent";
        }
    }

    @Test
    void production_columnAbsent_isAddedWithDefault_andExistingUsersGetZero() throws SQLException {
        createUsers(null, "INSERT INTO users (email) VALUES ('a@test.invalid'), ('b@test.invalid')");

        migrate();

        assertThat(v25State()).isEqualTo("1");
        assertThat(column()).containsExactly("int", "NO", "0");
        assertThat(tokenVersions()).containsExactly(0, 0);
        exec(OLD_BUILD_INSERT);                                   // rollback compatibility
        assertThat(tokenVersions()).containsExactly(0, 0, 0);
    }

    @Test
    void staging_hibernateColumnWithoutDefault_gainsDefault_andKeepsSessionVersions() throws SQLException {
        createUsers("INT NOT NULL",
                "INSERT INTO users (email, token_version) VALUES ('a@test.invalid', 3), ('b@test.invalid', 0)");
        // Negative control: this is H1 -- the old build cannot create a user on this schema.
        assertThatThrownBy(() -> exec(OLD_BUILD_INSERT))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("token_version");

        migrate();

        assertThat(v25State()).isEqualTo("1");
        assertThat(column()).containsExactly("int", "NO", "0");
        assertThat(tokenVersions()).containsExactly(3, 0);       // live sessions are not invalidated
        exec(OLD_BUILD_INSERT);
        assertThat(tokenVersions()).containsExactly(3, 0, 0);
    }

    @Test
    void defensive_nullableColumn_isBackfilled_andMadeNotNull() throws SQLException {
        createUsers("INT NULL",
                "INSERT INTO users (email, token_version) VALUES ('a@test.invalid', NULL), ('b@test.invalid', 2)");

        migrate();

        assertThat(column()).containsExactly("int", "NO", "0");
        assertThat(tokenVersions()).containsExactly(0, 2);
        exec(OLD_BUILD_INSERT);
        assertThat(tokenVersions()).containsExactly(0, 2, 0);
    }

    @Test
    void rerunningTheMigrationStatements_changesNothing() throws SQLException {
        createUsers("INT NOT NULL", "INSERT INTO users (email, token_version) VALUES ('a@test.invalid', 5)");
        migrate();
        List<String> once = column();

        try (Connection c = connect()) {                          // the same SQL a second time, outside Flyway
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V25__add_user_token_version_default.sql"));
        }

        assertThat(column()).isEqualTo(once).containsExactly("int", "NO", "0");
        assertThat(tokenVersions()).containsExactly(5);
    }

    @Test
    void currentBuild_explicitTokenVersion_isStored() throws SQLException {
        createUsers(null);
        migrate();

        exec("INSERT INTO users (email, token_version) VALUES ('new-build@test.invalid', 7)");

        assertThat(tokenVersions()).containsExactly(7);
    }
}
