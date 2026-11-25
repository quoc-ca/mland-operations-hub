package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveReportService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveTopSellerService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveTrendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FE-10 against the migrated MySQL schema (T043, QS-17): the real JDBC source adapters run their SQL for the KPI
 * report, the trends and the best sellers for every period type, no source is reported unavailable (so the SQL is
 * valid on the real schema), and no table the reports read is created into or changed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "retail.unstarted-expiry.enabled=false", "vnpay.enabled=false", "spring.task.scheduling.enabled=false"})
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
class ExecutiveReportReadOnlyMySqlTest {
    /** Every table read by the FE-10 SQL in JdbcReportingSourceAdapter, JdbcReportingTrendAdapter, JdbcReportingTopSellerAdapter. */
    private static final List<String> SOURCE_TABLES = List.of(
        "attendance_tickets", "booking_group_line_sessions", "booking_group_lines", "booking_groups",
        "booking_session_commitments", "catalogue_products", "catalogue_variants", "retail_grouped_checkouts",
        "retail_order_items", "retail_orders", "settlement_business_audits", "settlement_charges",
        "settlement_evidence_conclusions", "settlement_final_bills", "settlement_folio_collections", "ticket_bookings",
        "ticket_session_entitlements", "workshop_bookings", "workshop_locations", "workshop_packages", "workshop_sessions");

    @Autowired ExecutiveReportService reports;
    @Autowired ExecutiveTrendService trends;
    @Autowired ExecutiveTopSellerService topSellers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyReportRunsOnTheRealSchemaAndWritesNothing() {
        List<Long> branches = jdbc.queryForList("SELECT id FROM workshop_locations ORDER BY id LIMIT 5", Long.class);
        if (branches.isEmpty()) branches = List.of(1L);
        Map<String, Object> before = checksums();

        for (String period : List.of("DAY", "WEEK", "MONTH", "YEAR")) {
            for (LocalDate anchor : List.of(LocalDate.now(), LocalDate.now().minusYears(1))) {
                var report = reports.report(period, anchor, branches);
                assertThat(report.quality()).as("%s %s KPI sources", period, anchor)
                    .allSatisfy(quality -> assertThat(quality.status()).isNotEqualTo("UNAVAILABLE"));
                assertThat(report.branches()).hasSize(branches.size());

                var trend = trends.trend(period, anchor, branches);
                assertThat(trend.unavailable()).as("%s %s trend sources", period, anchor).isEmpty();
                assertThat(trend.points()).isNotEmpty();

                var best = topSellers.topSellers(period, anchor, branches);
                assertThat(best.unavailable()).as("%s %s best-seller sources", period, anchor).isEmpty();
                assertThat(best.products()).isNotNull().hasSizeLessThanOrEqualTo(5);
                assertThat(best.packages()).isNotNull().hasSizeLessThanOrEqualTo(5);
            }
        }

        assertThat(checksums()).as("FE-10 is read-only").isEqualTo(before);
    }

    private Map<String, Object> checksums() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : SOURCE_TABLES) {
            result.put(table, jdbc.queryForMap("CHECKSUM TABLE " + table).get("Checksum"));
        }
        return result;
    }
}
