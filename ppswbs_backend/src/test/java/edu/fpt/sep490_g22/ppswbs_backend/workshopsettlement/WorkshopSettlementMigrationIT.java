package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMigrationTestSupport;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WorkshopSettlementMigrationIT {
    private static final List<String> TABLES = List.of("settlement_cancellation_cases", "settlement_handoff_idempotency", "settlement_final_bills", "settlement_charges", "settlement_cases", "settlement_evidence_conclusions", "settlement_refund_policy_snapshots", "settlement_business_audits", "settlement_idempotency_outcomes", "settlement_notification_handoffs", "settlement_customer_cancellation_snapshots", "settlement_refund_allocations", "settlement_final_payment_attempts", "settlement_folio_corrections", "settlement_addon_payment_attempts", "settlement_addon_refunds");
    @Test void freshSchemaAppliesSettlementFoundation() throws Exception {
        OtaMigrationTestSupport support = configuredSupport(); if (support == null) return;
        try (support) { String database=support.createDatabase(); MigrateResult result=support.flyway(database,null).migrate(); assertThat(result.success).isTrue(); assertThat(result.migrations).extracting(m->m.version).contains("17","18","30","44","45","47","96","98","99"); try(Connection c=support.databaseConnection(database)){assertTables(c,database); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='44' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='45' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='47' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='96' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='98' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='99' AND success=1")).isEqualTo(1); assertColumns(c,database,"payment_owner_registrations",List.of("provider_create_date")); assertColumns(c,database,"settlement_evidence_conclusions",List.of("provider_transaction_number","original_provider_create_date")); assertColumns(c,database,"settlement_customer_cancellation_snapshots",List.of("case_reference","booking_reference","refund_percent","refund_amount_vnd","eligibility_outcome")); assertColumns(c,database,"settlement_refund_allocations",List.of("original_payment_reference","original_provider_transaction_number","original_provider_create_date","allocated_amount_vnd","provider_refund_transaction_number","provider_transaction_type","provider_transaction_status","last_query_at")); assertColumns(c,database,"settlement_final_payment_attempts",List.of("attempt_reference","bill_reference","booking_reference","operation_reference","merchant_reference","amount_vnd","currency_code","provider_create_date","payment_url_snapshot","lifecycle_status","expires_at")); assertColumns(c,database,"settlement_folio_corrections",List.of("correction_reference","case_reference","booking_reference","bill_reference","amount_vnd","currency_code","reason","manager_actor_reference","created_at","updated_at")); assertColumns(c,database,"settlement_addon_payment_attempts",List.of("attempt_reference","bill_reference","channel_booking_reference","operation_reference","merchant_reference","amount_vnd","currency_code","provider_create_date","payment_url_snapshot","lifecycle_status","expires_at")); assertColumns(c,database,"settlement_addon_refunds",List.of("refund_reference","case_reference","bill_reference","channel_booking_reference","amount_vnd","currency_code","reason","manager_actor_reference","created_at","updated_at"));} }
    }
    @Test void upgradeFromVersion29PreservesLegacyTablesAndIsIdempotent() throws Exception {
        OtaMigrationTestSupport support=configuredSupport(); if(support==null)return;
        try(support){String database=support.createDatabase(); support.flyway(database,"29").migrate(); try(Connection c=support.databaseConnection(database); Statement s=c.createStatement()){s.executeUpdate("INSERT INTO settlement_cancellation_cases (case_reference,session_reference,affected_booking_references,cancellation_reason_reference,operation_reference,correlation_reference,status,created_at,updated_at) VALUES ('migration-case','session-1','[\\\"booking-1\\\"]','reason','operation-1','correlation-1','OPEN',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");} MigrateResult upgrade=support.flyway(database,null).migrate(); MigrateResult replay=support.flyway(database,null).migrate(); assertThat(upgrade.success).isTrue(); assertThat(replay.migrations).isEmpty(); try(Connection c=support.databaseConnection(database)){assertTables(c,database); assertThat(count(c,"SELECT COUNT(*) FROM settlement_cancellation_cases WHERE case_reference='migration-case'")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='45' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='47' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='96' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='98' AND success=1")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='99' AND success=1")).isEqualTo(1);}} }
    /** 003.3 T087 / V96: existing confirmed direct bookings get exactly one OPEN folio; nothing else changes. */
    @Test void v96BackfillsOneOpenFolioPerConfirmedDirectBookingOnly() throws Exception {
        OtaMigrationTestSupport support = configuredSupport(); if (support == null) return;
        try (support) {
            String database = support.createDatabase();
            support.flyway(database, "95").migrate();
            try (Connection c = support.databaseConnection(database); Statement s = c.createStatement()) {
                for (String[] row : new String[][] {
                        {"BK-V96-DIRECT", "CONFIRMED"}, {"BK-V96-CANCELLED", "CANCELLED"},
                        {"BK-V96-EXPIRED", "EXPIRED"}, {"BK-V96-HAS-FOLIO", "CONFIRMED"}}) {
                    s.executeUpdate("INSERT INTO workshop_bookings (booking_code,package_id,package_name_snapshot,contact_email,"
                            + "contact_phone,participant_count,booking_status,payment_status,confirmed_at) VALUES ('" + row[0]
                            + "',1,'Package','migration@example.invalid','0900000000',1,'" + row[1] + "','DEPOSIT_PAID',CURRENT_TIMESTAMP(6))");
                    s.executeUpdate("INSERT INTO booking_invoices (booking_id,invoice_number,price_per_participant,package_total,"
                            + "post_promotion_amount,deposit_amount,remaining_amount,currency) SELECT id,'INV-" + row[0]
                            + "',100001,100001,100001,50001,50000,'VND' FROM workshop_bookings WHERE booking_code='" + row[0] + "'");
                }
                s.executeUpdate("INSERT INTO settlement_final_bills (bill_reference,booking_reference,post_promotion_amount_vnd,"
                        + "accepted_deposit_amount_vnd,remaining_amount_vnd,currency_code,lifecycle_status,frozen_at,created_at) "
                        + "VALUES ('BILL-EXISTING','BK-V96-HAS-FOLIO',1,1,0,'VND','FINALIZED',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
            }
            MigrateResult upgrade = support.flyway(database, null).migrate();
            MigrateResult replay = support.flyway(database, null).migrate();
            assertThat(upgrade.success).isTrue();
            assertThat(replay.migrations).isEmpty();
            try (Connection c = support.databaseConnection(database)) {
                assertThat(count(c, "SELECT COUNT(*) FROM settlement_final_bills WHERE booking_reference='BK-V96-DIRECT' "
                        + "AND lifecycle_status='OPEN' AND post_promotion_amount_vnd=100001 AND accepted_deposit_amount_vnd=50001 "
                        + "AND remaining_amount_vnd=50000 AND currency_code='VND'")).isEqualTo(1);
                assertThat(count(c, "SELECT COUNT(*) FROM settlement_final_bills WHERE booking_reference IN "
                        + "('BK-V96-CANCELLED','BK-V96-EXPIRED')")).isZero();
                assertThat(count(c, "SELECT COUNT(*) FROM settlement_final_bills WHERE booking_reference='BK-V96-HAS-FOLIO'"))
                        .isEqualTo(1);
                assertThat(count(c, "SELECT COUNT(*) FROM settlement_final_bills WHERE bill_reference='BILL-EXISTING' "
                        + "AND lifecycle_status='FINALIZED'")).isEqualTo(1);
            }
        }
    }

    private static OtaMigrationTestSupport configuredSupport(){var s=OtaMigrationTestSupport.fromEnvironment(); Assumptions.assumeTrue(s.isPresent(),"Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL migration evidence"); return s.orElseThrow();}
    private static void assertTables(Connection c,String db)throws Exception{try(PreparedStatement q=c.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_name LIKE 'settlement\\_%'")){q.setString(1,db);try(ResultSet r=q.executeQuery()){java.util.ArrayList<String>a=new java.util.ArrayList<>();while(r.next())a.add(r.getString(1));assertThat(a).containsAll(TABLES);}}}
    private static void assertColumns(Connection c,String db,String table,List<String> expected)throws Exception{try(PreparedStatement q=c.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=?")){q.setString(1,db);q.setString(2,table);try(ResultSet r=q.executeQuery()){java.util.ArrayList<String>a=new java.util.ArrayList<>();while(r.next())a.add(r.getString(1));assertThat(a).containsAll(expected);}}}
    private static long count(Connection c,String sql)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getLong(1);}}
}
