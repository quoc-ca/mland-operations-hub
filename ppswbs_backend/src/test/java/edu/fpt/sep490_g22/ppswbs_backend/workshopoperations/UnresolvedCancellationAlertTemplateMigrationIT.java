package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMigrationTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** T133: V159 inserts the alert template definition and its active first version, on MySQL. */
class UnresolvedCancellationAlertTemplateMigrationIT {

    private static final OtaMigrationTestSupport DB = OtaMigrationTestSupport.fromEnvironment().orElse(null);
    private static String schema;

    @BeforeAll
    static void migrate() throws Exception {
        if (DB == null) return;
        schema = DB.createDatabase();
        DB.flyway(schema, null).migrate();
    }

    @AfterAll
    static void close() throws Exception {
        if (DB != null) DB.close();
    }

    @Test
    void migrationInsertsDefinitionAndActiveVersionWithinAllowlist() throws Exception {
        assumeTrue(DB != null, "Requires isolated MySQL test configuration");
        try (Connection c = DB.databaseConnection(schema); Statement st = c.createStatement()) {
            ResultSet def = st.executeQuery("SELECT id, owning_module, lifecycle_status, allowed_placeholders "
                    + "FROM notification_template_definition WHERE code='WORKSHOP_CANCELLATION_UNRESOLVED_ALERT'");
            assertThat(def.next()).isTrue();
            long id = def.getLong("id");
            assertThat(def.getString("owning_module")).isEqualTo("workshopoperations");
            assertThat(def.getString("lifecycle_status")).isEqualTo("ACTIVE");
            Set<String> allowed = new HashSet<>(Arrays.asList(def.getString("allowed_placeholders")
                    .replaceAll("[\\[\\]\"\\s]", "").split(",")));
            assertThat(allowed).containsExactlyInAnyOrder(
                    "sessionReference", "sessionName", "locationName", "scheduledEndAt", "hoursSinceScheduledEnd");

            ResultSet ver = st.executeQuery("SELECT version_number, status, subject_template, html_template, "
                    + "plain_text_template FROM notification_template_version WHERE template_definition_id=" + id);
            assertThat(ver.next()).isTrue();
            assertThat(ver.getInt("version_number")).isEqualTo(1);
            assertThat(ver.getString("status")).isEqualTo("ACTIVE");
            for (String column : new String[] {"subject_template", "html_template", "plain_text_template"}) {
                Matcher m = Pattern.compile("\\{\\{(\\w+)}}").matcher(ver.getString(column));
                while (m.find()) {
                    assertThat(allowed).as(column + " placeholder " + m.group(1)).contains(m.group(1));
                }
            }
            assertThat(ver.getString("subject_template")).doesNotContain("unresolvedTicketCount");
            assertThat(ver.next()).isFalse();
        }
    }

    /** Same rendering path as the 007 preview API (POST /{code}/preview): every placeholder is replaced by sample data. */
    @Test
    void previewRenderingWithTheSnapshotKeysLeavesNoPlaceholder() throws Exception {
        assumeTrue(DB != null, "Requires isolated MySQL test configuration");
        try (Connection c = DB.databaseConnection(schema); Statement st = c.createStatement()) {
            ResultSet ver = st.executeQuery("SELECT v.subject_template, v.html_template, v.plain_text_template "
                    + "FROM notification_template_version v JOIN notification_template_definition d "
                    + "ON d.id = v.template_definition_id WHERE d.code='WORKSHOP_CANCELLATION_UNRESOLVED_ALERT' AND v.status='ACTIVE'");
            assertThat(ver.next()).isTrue();
            Map<String, Object> sample = Map.of("sessionReference", "S-1", "sessionName", "Sáng ngày 2026-11-10",
                    "locationName", "Chi nhánh Q1", "scheduledEndAt", "10/11/2026 17:00", "hoursSinceScheduledEnd", 24L);
            RenditionService.RenderedOutput out = new RenditionService(null, null).renderSample(
                    ver.getString(1), ver.getString(2), ver.getString(3), sample);
            assertThat(out.subject()).contains("Sáng ngày 2026-11-10").doesNotContain("{{");
            assertThat(out.html()).contains("Chi nhánh Q1").contains("24").doesNotContain("{{");
            assertThat(out.plainText()).contains("S-1").doesNotContain("{{");
        }
    }
}
