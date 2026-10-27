package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * T081 on MySQL 8.4: all migrations (001's V5, 002's V9 and those of other features) run on an EMPTY database (fresh install) and on a database that already has
 * the V5 account data (upgrade). Each case uses its own temporary database {@code ppswbs_pm_fresh_<random>}
 * created on the same server as the test database and dropped afterwards; the test database itself and any real
 * database are never touched. Connection settings come from {@code PM_TEST_JDBC_*} or {@code JDBC_*}; without them
 * the test fails (it never falls back to H2 or a default).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueMySqlIT {

    private static final List<String> CATALOGUE_TABLES = List.of(
            "catalogue_audit_events", "catalogue_categories", "catalogue_command_receipts", "catalogue_import_batches",
            "catalogue_media_objects", "catalogue_media_references", "catalogue_price_applications",
            "catalogue_price_proposals", "catalogue_product_images", "catalogue_product_reviews", "catalogue_products",
            "catalogue_variants");
    private static final Pattern URL = Pattern.compile("^(jdbc:mysql://[^/]+)/([^?]+)(\\?.*)?$");

    private String serverUrl;
    private String parameters;
    private String user;
    private String password;
    private final List<String> created = new ArrayList<>();

    @BeforeAll
    void readConnectionSettings() {
        String url = env("PM_TEST_JDBC_URL", "JDBC_URL");
        user = env("PM_TEST_JDBC_USER", "JDBC_USER");
        password = env("PM_TEST_JDBC_PASSWORD", "JDBC_PASSWORD");
        if (url == null || user == null || password == null) {
            fail("NOT RUN: set PM_TEST_JDBC_URL/USER/PASSWORD or JDBC_URL/USER/PASSWORD for the MySQL 8.4 test server");
        }
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches()) {
            fail("NOT RUN: the JDBC URL must look like jdbc:mysql://host:port/database[?params]");
        }
        serverUrl = matcher.group(1);
        parameters = matcher.group(3) == null ? "" : matcher.group(3);
    }

    @AfterAll
    void dropTemporaryDatabases() throws SQLException {
        try (Connection connection = server(); Statement statement = connection.createStatement()) {
            for (String name : created) {
                statement.execute("DROP DATABASE IF EXISTS `" + name + "`");
            }
            List<String> left = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SHOW DATABASES LIKE 'ppswbs\\_pm\\_fresh\\_%'")) {
                while (rs.next()) {
                    left.add(rs.getString(1));
                }
            }
            assertThat(left).as("temporary databases must be dropped").doesNotContainAnyElementsOf(created);
        }
    }

    @Test
    void freshInstallCreatesEveryTableKeyAndCheckWithTheAgreedEngineAndCollation() throws SQLException {
        String database = createTemporaryDatabase();
        MigrateResult result = flyway(database, null).migrate();

        assertThat(result.success).isTrue();
        // Other features own the numbers in between and after; 002 only requires 001's V5 before its V9.
        assertThat(result.migrations).extracting(migration -> migration.version).containsSubsequence("5", "9");
        try (Connection connection = database(database)) {
            Map<String, String[]> tables = rows(connection, """
                    select table_name, engine, table_collation from information_schema.tables
                    where table_schema = ? and table_name like 'catalogue\\_%'""", database, 3);
            assertThat(tables.keySet()).containsExactlyInAnyOrderElementsOf(CATALOGUE_TABLES);
            assertThat(tables.values()).allSatisfy(table -> {
                assertThat(table[1]).isEqualTo("InnoDB");
                assertThat(table[2]).isEqualTo("utf8mb4_unicode_ci");
            });

            Map<String, String[]> foreignKeys = rows(connection, """
                    select constraint_name, delete_rule, update_rule, referenced_table_name
                    from information_schema.referential_constraints
                    where constraint_schema = ? and table_name like 'catalogue\\_%'""", database, 4);
            assertThat(foreignKeys).hasSize(13);
            assertThat(foreignKeys.values()).allSatisfy(fk -> {
                assertThat(fk[1]).as("delete rule of %s", fk[0]).isEqualTo("RESTRICT");
                assertThat(fk[2]).as("update rule of %s", fk[0]).isEqualTo("RESTRICT");
            });

            // The circular variant → proposal references (pending/applied pointers) exist besides proposal → variant.
            Map<String, String[]> variantPointers = rows(connection, """
                    select column_name, referenced_table_name from information_schema.key_column_usage
                    where table_schema = ? and table_name = 'catalogue_variants' and referenced_table_name is not null""",
                    database, 2);
            assertThat(variantPointers).containsKeys("pending_proposal_id", "applied_proposal_id", "product_id");
            assertThat(variantPointers.get("pending_proposal_id")[1]).isEqualTo("catalogue_price_proposals");
            assertThat(variantPointers.get("applied_proposal_id")[1]).isEqualTo("catalogue_price_proposals");

            Map<String, String[]> checks = rows(connection, """
                    select constraint_name, enforced from information_schema.table_constraints
                    where table_schema = ? and constraint_type = 'CHECK' and table_name like 'catalogue\\_%'""", database, 2);
            assertThat(checks).hasSize(30);
            assertThat(checks.values()).allSatisfy(check -> assertThat(check[1]).isEqualTo("YES"));

            Map<String, String[]> uniques = rows(connection, """
                    select constraint_name, table_name from information_schema.table_constraints
                    where table_schema = ? and constraint_type = 'UNIQUE' and table_name like 'catalogue\\_%'""", database, 2);
            assertThat(uniques).containsKeys("uk_catalogue_categories_name_key", "uk_catalogue_variants_sku",
                    "uk_catalogue_variants_size", "uk_catalogue_product_images_position",
                    "uk_catalogue_media_objects_storage_key", "uk_catalogue_import_batches_created_command",
                    "uk_catalogue_import_batches_confirm_command");
        }
    }

    @Test
    void upgradeFromAccountManagementKeepsExistingAccountRecords() throws SQLException {
        String database = createTemporaryDatabase();
        assertThat(flyway(database, "5").migrate().success).isTrue();
        String uid = "fresh-it-" + UUID.randomUUID();
        try (Connection connection = database(database)) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into accounts (firebase_uid, login_email, role, status) values (?, ?, 'MEMBER', 'ACTIVE')")) {
                insert.setString(1, uid);
                insert.setString(2, "member@example.test");
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into account_profiles (account_id, full_name) select id, ? from accounts where firebase_uid = ?")) {
                insert.setString(1, "Nguyễn Thị Mai");
                insert.setString(2, uid);
                insert.executeUpdate();
            }
        }

        MigrateResult upgrade = flyway(database, null).migrate();

        assertThat(upgrade.success).isTrue();
        assertThat(upgrade.migrations).extracting(migration -> migration.version).contains("9").doesNotContain("5");
        try (Connection connection = database(database);
             PreparedStatement select = connection.prepareStatement("""
                     select a.role, a.status, p.full_name from accounts a join account_profiles p on p.account_id = a.id
                     where a.firebase_uid = ?""")) {
            select.setString(1, uid);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("MEMBER");
                assertThat(rs.getString(2)).isEqualTo("ACTIVE");
                assertThat(rs.getString(3)).isEqualTo("Nguyễn Thị Mai");
            }
            Map<String, String[]> tables = rows(connection, """
                    select table_name, table_type from information_schema.tables
                    where table_schema = ? and table_name like 'catalogue\\_%'""", database, 2);
            assertThat(tables).hasSize(12);
        }
    }

    @Test
    void runningTheMigrationsAgainChangesNothingAndValidates() throws SQLException {
        String database = createTemporaryDatabase();
        Flyway flyway = flyway(database, null);
        flyway.migrate();

        MigrateResult again = flyway.migrate();

        assertThat(again.migrationsExecuted).isZero();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private String createTemporaryDatabase() throws SQLException {
        String name = "ppswbs_pm_fresh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try (Connection connection = server(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        created.add(name);
        return name;
    }

    private Flyway flyway(String database, String target) {
        var configuration = Flyway.configure()
                .dataSource(serverUrl + "/" + database + parameters, user, password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection server() throws SQLException {
        return DriverManager.getConnection(serverUrl + "/" + parameters, user, password);
    }

    private Connection database(String database) throws SQLException {
        return DriverManager.getConnection(serverUrl + "/" + database + parameters, user, password);
    }

    /** First column as key, all requested columns as value. */
    private static Map<String, String[]> rows(Connection connection, String sql, String schema, int columns)
            throws SQLException {
        Map<String, String[]> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[columns];
                    for (int i = 0; i < columns; i++) {
                        row[i] = rs.getString(i + 1);
                    }
                    result.put(row[0], row);
                }
            }
        }
        return result;
    }

    private static String env(String preferred, String fallback) {
        String value = System.getenv(preferred);
        return value == null || value.isBlank() ? System.getenv(fallback) : value;
    }
}
