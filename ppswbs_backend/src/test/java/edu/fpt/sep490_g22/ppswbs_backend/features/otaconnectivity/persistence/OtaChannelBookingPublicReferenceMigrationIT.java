package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtaChannelBookingPublicReferenceMigrationIT {

    @Test
    void upgradeBackfillsDistinctStablePublicReferencesWithoutChangingNumericIds() throws Exception {
        var configured = OtaMigrationTestSupport.fromEnvironment();
        Assumptions.assumeTrue(configured.isPresent(),
                "Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL migration evidence");

        try (OtaMigrationTestSupport support = configured.orElseThrow()) {
            String database = support.createDatabase();
            support.flyway(database, "47").migrate();

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
                        + "retail_amount_minor, target_net_amount_minor, currency_code) VALUES "
                        + "(301, 201, 'external-1', 'RECEIVED', 1, 'TEST', 'policy', '1', 50000, 40000, 'VND'), "
                        + "(302, 201, 'external-2', 'RECEIVED', 2, 'TEST', 'policy', '1', 100000, 80000, 'VND')");
            }

            assertThat(support.flyway(database, null).migrate().success).isTrue();
            List<String> references = readReferences(support, database);
            assertThat(references).hasSize(2).doesNotHaveDuplicates();
            references.forEach(reference -> assertThat(UUID.fromString(reference)).isNotNull());
            assertBookingCorrelationsAndConstraints(support, database, references);

            assertThat(support.flyway(database, null).migrate().migrations).isEmpty();
            assertThat(readReferences(support, database)).containsExactlyElementsOf(references);
        }
    }

    private static void assertBookingCorrelationsAndConstraints(OtaMigrationTestSupport support,
                                                                String database, List<String> references)
            throws Exception {
        try (Connection connection = support.databaseConnection(database);
             PreparedStatement duplicateReference = connection.prepareStatement(
                     "UPDATE ota_channel_bookings SET public_reference = ? WHERE id = 302");
             Statement statement = connection.createStatement()) {
            duplicateReference.setString(1, references.get(0));
            assertThatThrownBy(duplicateReference::executeUpdate).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE ota_channel_bookings SET public_reference = NULL WHERE id = 302"))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate("INSERT INTO ota_booking_ticket_links "
                    + "(channel_booking_id, participant_ordinal, operations_ticket_reference) VALUES "
                    + "(302, 1, 'ticket-1'), (302, 2, 'ticket-2')");
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO ota_booking_ticket_links "
                    + "(channel_booking_id, participant_ordinal, operations_ticket_reference) "
                    + "VALUES (302, 1, 'ticket-3')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO ota_booking_ticket_links "
                    + "(channel_booking_id, participant_ordinal, operations_ticket_reference) "
                    + "VALUES (301, 1, 'ticket-1')"))
                    .isInstanceOf(SQLException.class);

            try (PreparedStatement event = connection.prepareStatement("INSERT INTO ota_inbound_events "
                    + "(connection_id, payload_fingerprint, dedupe_key, event_kind, authentication_result, "
                    + "processing_status, outcome_reference, received_at, processed_at) "
                    + "VALUES (101, REPEAT('a', 64), REPEAT('b', 64), 'BOOKING', 'VERIFIED', "
                    + "'APPLIED', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")) {
                event.setString(1, references.get(0));
                assertThat(event.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet event = statement.executeQuery("SELECT outcome_reference FROM ota_inbound_events "
                    + "WHERE connection_id = 101")) {
                assertThat(event.next()).isTrue();
                assertThat(event.getString(1)).isEqualTo(references.get(0));
            }
        }
    }

    private static List<String> readReferences(OtaMigrationTestSupport support, String database) throws Exception {
        try (Connection connection = support.databaseConnection(database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, public_reference FROM ota_channel_bookings ORDER BY id")) {
            List<String> references = new ArrayList<>();
            long expectedId = 301;
            while (rows.next()) {
                assertThat(rows.getLong("id")).isEqualTo(expectedId++);
                references.add(rows.getString("public_reference"));
            }
            return references;
        }
    }
}
