package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test helper that creates and drops only uniquely named local OTA migration schemas. */
public final class OtaMigrationTestSupport implements AutoCloseable {

    private static final Pattern LOCAL_TEST_URL = Pattern.compile(
            "^jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/(ppswbs_workshop_test_[A-Za-z0-9_]+)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final String TEMP_DATABASE_PREFIX = "ppswbs_ota_migration_";

    private final String serverUrl;
    private final String parameters;
    private final String user;
    private final String password;
    private final List<String> ownedDatabases = new ArrayList<>();

    private OtaMigrationTestSupport(String serverUrl, String parameters, String user, String password) {
        this.serverUrl = serverUrl;
        this.parameters = parameters;
        this.user = user;
        this.password = password;
    }

    public static Optional<OtaMigrationTestSupport> fromEnvironment() {
        String url = System.getenv("WORKSHOP_OTA_TEST_JDBC_URL");
        String user = System.getenv("WORKSHOP_OTA_TEST_JDBC_USER");
        String password = System.getenv("WORKSHOP_OTA_TEST_JDBC_PASSWORD");
        if (blank(url) && blank(user) && blank(password)) {
            return Optional.empty();
        }
        if (blank(url) || blank(user) || password == null) {
            throw new IllegalStateException("Set WORKSHOP_OTA_TEST_JDBC_URL, WORKSHOP_OTA_TEST_JDBC_USER and "
                    + "WORKSHOP_OTA_TEST_JDBC_PASSWORD together");
        }

        Matcher matcher = LOCAL_TEST_URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("OTA migration tests require a localhost URL whose selected schema "
                    + "starts with ppswbs_workshop_test_");
        }
        String server = "jdbc:mysql://" + matcher.group(1) + (matcher.group(2) == null ? "" : matcher.group(2));
        String parameters = matcher.group(4) == null ? "" : matcher.group(4);
        return Optional.of(new OtaMigrationTestSupport(server, parameters, user, password));
    }

    public String createDatabase() throws SQLException {
        String database = TEMP_DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        if (!database.matches("ppswbs_ota_migration_[a-f0-9]{32}")) {
            throw new IllegalStateException("Generated OTA migration schema name failed its safety check");
        }
        try (Connection connection = serverConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        ownedDatabases.add(database);
        return database;
    }

    public Flyway flyway(String database, String target) {
        requireOwnedDatabase(database);
        var configuration = Flyway.configure()
                .dataSource(databaseUrl(database), user, password)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    public Connection databaseConnection(String database) throws SQLException {
        requireOwnedDatabase(database);
        return DriverManager.getConnection(databaseUrl(database), user, password);
    }

    public String jdbcUrl(String database) {
        requireOwnedDatabase(database);
        return databaseUrl(database);
    }

    private Connection serverConnection() throws SQLException {
        return DriverManager.getConnection(serverUrl + "/" + parameters, user, password);
    }

    private String databaseUrl(String database) {
        return serverUrl + "/" + database + parameters;
    }

    private void requireOwnedDatabase(String database) {
        if (database == null || !ownedDatabases.contains(database)
                || !database.matches("ppswbs_ota_migration_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Refusing to access a schema not created by this test helper");
        }
    }

    @Override
    public void close() throws SQLException {
        try (Connection connection = serverConnection(); Statement statement = connection.createStatement()) {
            for (String database : List.copyOf(ownedDatabases)) {
                requireOwnedDatabase(database);
                statement.execute("DROP DATABASE `" + database + "`");
                ownedDatabases.remove(database);
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
