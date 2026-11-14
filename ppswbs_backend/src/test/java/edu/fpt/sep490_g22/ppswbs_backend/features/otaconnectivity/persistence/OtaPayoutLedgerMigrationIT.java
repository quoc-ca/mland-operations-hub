package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class OtaPayoutLedgerMigrationIT {

    @Test
    void addsExplanationColumnsWithoutInventingValuesForExistingLedgerFacts() throws Exception {
        var configured = OtaMigrationTestSupport.fromEnvironment();
        Assumptions.assumeTrue(configured.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated MySQL migration evidence");

        try (OtaMigrationTestSupport support = configured.orElseThrow()) {
            String database = support.createDatabase();
            support.flyway(database, "48").migrate();
            try (Connection connection = support.databaseConnection(database);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO ota_channel_connections "
                        + "(id, provider_code, environment, status, public_reference) "
                        + "VALUES (101, 'TEST', 'TEST', 'DISABLED', UUID())");
                statement.executeUpdate("INSERT INTO ota_channel_mappings "
                        + "(id, connection_id, offer_reference, session_reference, location_reference, "
                        + "external_product_id, external_option_id, status, public_reference, version) "
                        + "VALUES (201, 101, 'offer', 'session', 'location', 'product', 'option', "
                        + "'DRAFT', UUID(), 1)");
                statement.executeUpdate("INSERT INTO ota_channel_bookings "
                        + "(id, mapping_id, external_booking_reference, provider_status, participant_count, "
                        + "sales_channel_snapshot, displayed_policy_reference, displayed_policy_version, "
                        + "retail_amount_minor, target_net_amount_minor, currency_code, public_reference) "
                        + "VALUES (301, 201, 'external-1', 'RECEIVED', 1, 'TEST', 'policy', '1', "
                        + "50000, 40000, 'VND', UUID())");
                statement.executeUpdate("INSERT INTO ota_payout_positions "
                        + "(id, channel_booking_id, expected_amount_minor, currency_code, status) "
                        + "VALUES (401, 301, 40000, 'VND', 'ACCRUED')");
                statement.executeUpdate("INSERT INTO ota_payout_ledger_entries "
                        + "(id, payout_position_id, entry_kind, amount_minor, currency_code, "
                        + "external_evidence_reference, occurred_at, correlation_reference) "
                        + "VALUES (501, 401, 'GROSS_SALE', 50000, 'VND', 'provider-evidence-1', "
                        + "'2026-10-30 12:00:00', 'trace-1')");
            }

            assertThat(support.flyway(database, null).migrate().success).isTrue();
            try (Connection connection = support.databaseConnection(database);
                 Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("SELECT id, public_reference, evidence_type, "
                         + "allocation_reference, amount_minor, "
                         + "external_evidence_reference, correlation_reference, reason, "
                         + "related_record_reference FROM ota_payout_ledger_entries WHERE id = 501")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("id")).isEqualTo(501L);
                assertThat(java.util.UUID.fromString(row.getString("public_reference"))).isNotNull();
                assertThat(row.getString("evidence_type")).isNull();
                assertThat(row.getString("allocation_reference")).isNull();
                assertThat(row.getLong("amount_minor")).isEqualTo(50000L);
                assertThat(row.getString("external_evidence_reference")).isEqualTo("provider-evidence-1");
                assertThat(row.getString("correlation_reference")).isEqualTo("trace-1");
                assertThat(row.getString("reason")).isNull();
                assertThat(row.getString("related_record_reference")).isNull();
            }
            assertThat(support.flyway(database, null).migrate().migrations).isEmpty();
        }
    }
}
