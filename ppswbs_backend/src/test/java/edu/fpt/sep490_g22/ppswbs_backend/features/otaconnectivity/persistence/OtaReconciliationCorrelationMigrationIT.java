package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtaReconciliationCorrelationMigrationIT {
    @Test
    void upgradeRepairsOnlyInvalidMappedCorrelationsAndEnforcesFutureWrites() throws Exception {
        var configured = OtaMigrationTestSupport.fromEnvironment();
        Assumptions.assumeTrue(configured.isPresent(), "Requires isolated local MySQL migration configuration");
        try (var support = configured.orElseThrow()) {
            String database = support.createDatabase();
            support.flyway(database, "28").migrate();
            String validCorrelation = UUID.randomUUID().toString();
            try (var connection = support.databaseConnection(database); var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO ota_channel_connections "
                        + "(id, public_reference, provider_code, environment, status) "
                        + "VALUES (1, UUID(), 'KLOOK', 'TEST', 'ACTIVE')");
                sql.executeUpdate("INSERT INTO ota_channel_mappings (id, public_reference, connection_id, "
                        + "offer_reference, session_reference, location_reference, external_product_id, "
                        + "external_option_id, status) VALUES "
                        + "(1, UUID(), 1, 'test-offer', 'test-session', 'test-location', 'product', 'option', 'SYNC_SUSPENDED')");
                sql.executeUpdate("INSERT INTO ota_inbound_events (id, connection_id, payload_fingerprint, dedupe_key, "
                        + "event_kind, authentication_result, processing_status, received_at) "
                        + "VALUES (1, 1, REPEAT('a',64), REPEAT('b',64), 'BOOKING', 'VERIFIED', 'RECEIVED', NOW(6))");
                insertItem(connection, 1, null, true);
                insertItem(connection, 2, "", true);
                insertItem(connection, 3, "legacy-test-audit", true);
                insertItem(connection, 4, "1-1-1-1-1", true);
                insertItem(connection, 5, validCorrelation, true);
                insertItem(connection, 6, null, false);
            }

            var result = support.flyway(database, null).migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrations).extracting(migration -> migration.version).contains("29");
            Map<Long, String> correlations;
            try (var connection = support.databaseConnection(database); var sql = connection.createStatement()) {
                correlations = correlations(connection);
                assertThat(correlations).hasSize(6);
                for (long id = 1; id <= 5; id++) {
                    String value = correlations.get(id);
                    assertThat(UUID.fromString(value).toString()).isEqualTo(value);
                }
                assertThat(correlations.get(5L)).isEqualTo(validCorrelation);
                assertThat(correlations.get(6L)).isNull();
                assertThat(correlations.values().stream().filter(java.util.Objects::nonNull).distinct().count())
                        .isEqualTo(5);
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM ota_reconciliation_work_items "
                        + "WHERE status = 'OPEN' AND decision = 'LEGACY_TEST' AND created_at = '2026-01-01' "
                        + "AND updated_at = '2026-01-02'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(6);
                }
                assertThatThrownBy(() -> sql.executeUpdate(
                        "UPDATE ota_reconciliation_work_items SET audit_reference = NULL WHERE id = 1"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate(
                        "UPDATE ota_reconciliation_work_items SET audit_reference = '1-1-1-1-1' WHERE id = 1"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertItem(connection, 7, null, true)).isInstanceOf(SQLException.class);
                insertItem(connection, 8, UUID.randomUUID().toString(), true);
            }
            assertThat(support.flyway(database, null).migrate().migrations).isEmpty();
            try (var connection = support.databaseConnection(database)) {
                assertThat(correlations(connection)).containsAllEntriesOf(correlations);
            }
        }
    }

    private static void insertItem(Connection connection, long id, String audit, boolean mapped) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO ota_reconciliation_work_items "
                + "(id, public_reference, mapping_id, inbound_event_id, location_reference, category, status, "
                + "audit_reference, decision, created_at, updated_at) "
                + "VALUES (?, UUID(), ?, ?, 'test-location', 'SOURCE_VERSION_GAP', 'OPEN', ?, "
                + "'LEGACY_TEST', '2026-01-01', '2026-01-02')")) {
            insert.setLong(1, id);
            insert.setObject(2, mapped ? 1L : null);
            insert.setObject(3, mapped ? null : 1L);
            insert.setString(4, audit);
            insert.executeUpdate();
        }
    }

    private static Map<Long, String> correlations(Connection connection) throws SQLException {
        Map<Long, String> result = new LinkedHashMap<>();
        try (var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT id, audit_reference FROM ota_reconciliation_work_items ORDER BY id")) {
            while (rows.next()) result.put(rows.getLong(1), rows.getString(2));
        }
        return result;
    }
}
