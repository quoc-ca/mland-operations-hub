package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtaConnectivityMigrationIT {

    private static final List<String> OTA_TABLES = List.of(
            "ota_channel_connections", "ota_channel_mappings", "ota_channel_price_snapshots",
            "ota_readiness_assessments", "ota_readiness_check_evidence",
            "ota_outbound_changes", "ota_delivery_attempts", "ota_inbound_events",
            "ota_channel_bookings", "ota_booking_ticket_links", "ota_customer_payment_evidence",
            "ota_payout_positions", "ota_payout_ledger_entries", "ota_addon_bill_links",
            "ota_reconciliation_work_items", "ota_reconciliation_decisions", "ota_control_outbox");

    @Test
    void freshSchemaAppliesAppendOnlyOtaMigrationAndAllOwnedTables() throws Exception {
        OtaMigrationTestSupport support = configuredSupport();
        if (support == null) return;
        try (support) {
            String database = support.createDatabase();
            MigrateResult result = support.flyway(database, null).migrate();

            assertThat(result.success).isTrue();
            assertThat(result.migrations).extracting(migration -> migration.version)
                    .containsSubsequence("21", "22", "23", "24", "25", "26", "27", "28", "29");
            try (Connection connection = support.databaseConnection(database)) {
                assertOtaTables(connection, database);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '22' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '25' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '26' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '27' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '28' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '29' AND success = 1")).isEqualTo(1);
            }
        }
    }

    @Test
    void upgradeFromVersion21PreservesExistingOperationsRowsAndMigrationHistory() throws Exception {
        OtaMigrationTestSupport support = configuredSupport();
        if (support == null) return;
        try (support) {
            String database = support.createDatabase();
            support.flyway(database, "21").migrate();
            long locationId;
            try (Connection connection = support.databaseConnection(database);
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO workshop_locations (code, display_name, address_snapshot, active) "
                                 + "VALUES (?, ?, ?, 1)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "ota-migration-canary");
                insert.setString(2, "Migration canary");
                insert.setString(3, "Test-only location");
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    locationId = keys.getLong(1);
                }
            }

            MigrateResult upgrade = support.flyway(database, null).migrate();
            MigrateResult replay = support.flyway(database, null).migrate();

            assertThat(upgrade.success).isTrue();
            assertThat(upgrade.migrations).extracting(migration -> migration.version).contains("22", "25", "26");
            assertThat(replay.success).isTrue();
            assertThat(replay.migrations).isEmpty();
            try (Connection connection = support.databaseConnection(database)) {
                assertOtaTables(connection, database);
                assertThat(count(connection, "SELECT COUNT(*) FROM workshop_locations WHERE id = " + locationId))
                        .isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '21' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '22' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '25' AND success = 1")).isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '26' AND success = 1")).isEqualTo(1);
            }
        }
    }

    private static OtaMigrationTestSupport configuredSupport() {
        var support = OtaMigrationTestSupport.fromEnvironment();
        Assumptions.assumeTrue(support.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL migration evidence");
        return support.orElseThrow();
    }

    private static void assertOtaTables(Connection connection, String database) throws Exception {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? "
                        + "AND table_name LIKE 'ota\\_%'")) {
            query.setString(1, database);
            try (ResultSet result = query.executeQuery()) {
                java.util.ArrayList<String> tables = new java.util.ArrayList<>();
                while (result.next()) {
                    tables.add(result.getString(1));
                }
                assertThat(tables).containsExactlyInAnyOrderElementsOf(OTA_TABLES);
            }
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
