package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMigrationTestSupport;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class WorkshopSettlementMigrationIT {
    private static final List<String> TABLES = List.of("settlement_cancellation_cases", "settlement_handoff_idempotency", "settlement_final_bills", "settlement_charges", "settlement_cases", "settlement_evidence_conclusions", "settlement_refund_policy_snapshots", "settlement_business_audits", "settlement_idempotency_outcomes");
    @Test void freshSchemaAppliesSettlementFoundation() throws Exception {
        OtaMigrationTestSupport support = configuredSupport(); if (support == null) return;
        try (support) { String database=support.createDatabase(); MigrateResult result=support.flyway(database,null).migrate(); assertThat(result.success).isTrue(); assertThat(result.migrations).extracting(m->m.version).contains("17","18","30"); try(Connection c=support.databaseConnection(database)){assertTables(c,database); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")).isEqualTo(1);} }
    }
    @Test void upgradeFromVersion29PreservesLegacyTablesAndIsIdempotent() throws Exception {
        OtaMigrationTestSupport support=configuredSupport(); if(support==null)return;
        try(support){String database=support.createDatabase(); support.flyway(database,"29").migrate(); try(Connection c=support.databaseConnection(database); Statement s=c.createStatement()){s.executeUpdate("INSERT INTO settlement_cancellation_cases (case_reference,session_reference,affected_booking_references,cancellation_reason_reference,operation_reference,correlation_reference,status,created_at,updated_at) VALUES ('migration-case','session-1','[\\\"booking-1\\\"]','reason','operation-1','correlation-1','OPEN',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");} MigrateResult upgrade=support.flyway(database,null).migrate(); MigrateResult replay=support.flyway(database,null).migrate(); assertThat(upgrade.success).isTrue(); assertThat(replay.migrations).isEmpty(); try(Connection c=support.databaseConnection(database)){assertTables(c,database); assertThat(count(c,"SELECT COUNT(*) FROM settlement_cancellation_cases WHERE case_reference='migration-case'")).isEqualTo(1); assertThat(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='30' AND success=1")).isEqualTo(1);}} }
    private static OtaMigrationTestSupport configuredSupport(){var s=OtaMigrationTestSupport.fromEnvironment(); Assumptions.assumeTrue(s.isPresent(),"Requires WORKSHOP_OTA_TEST_JDBC_URL/USER/PASSWORD for isolated local MySQL migration evidence"); return s.orElseThrow();}
    private static void assertTables(Connection c,String db)throws Exception{try(PreparedStatement q=c.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_name LIKE 'settlement\\_%'")){q.setString(1,db);try(ResultSet r=q.executeQuery()){java.util.ArrayList<String>a=new java.util.ArrayList<>();while(r.next())a.add(r.getString(1));assertThat(a).containsAll(TABLES);}}}
    private static long count(Connection c,String sql)throws Exception{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getLong(1);}}
}
