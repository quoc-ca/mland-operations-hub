package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.RetailBranchRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.SessionCapacity;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.WorkshopBranchRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.WorkshopRevenue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ExecutiveReportServiceTest {
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private final ReportingSourcePort sources = mock(ReportingSourcePort.class);
    private final ExecutiveReportService service =
        new ExecutiveReportService(sources, new ReportingPeriodResolver(NOW), NOW);

    @Test void composesTheFiveKpisPerBranchAndInTotal() {
        when(sources.retailRevenue(any(), any(), any())).thenReturn(List.of(
            new RetailBranchRevenue(1, "VND", BigInteger.valueOf(120_000_000), 6),
            new RetailBranchRevenue(2, "VND", BigInteger.valueOf(761_634), 1)));
        when(sources.workshopRevenue(any(), any(), any())).thenReturn(new WorkshopRevenue(
            List.of(new WorkshopBranchRevenue(1, BigInteger.valueOf(45_000_000), 3)), 0, 0));
        when(sources.completedSessions(any(), any(), any())).thenReturn(List.of(
            new SessionCapacity(1, 2, 50, 28), new SessionCapacity(2, 1, 30, 16)));
        when(sources.branchNames(any())).thenReturn(Map.of(1L, "MLAND Hoan Kiem", 2L, "MLAND Cau Giay"));

        var report = service.report(null, null, List.of(1L, 2L));

        assertThat(report.status()).isEqualTo("COMPLETE");
        assertThat(report.scope().periodType()).isEqualTo("MONTH");
        assertThat(report.scope().startAt()).isEqualTo("2026-10-31T17:00:00Z");
        assertThat(report.totals().retailRevenue()).isEqualTo("120761634");
        assertThat(report.totals().workshopRevenue()).isEqualTo("45000000");
        assertThat(report.totals().totalRevenue()).isEqualTo("165761634");
        assertThat(report.totals().paidRetailOrderCount()).isEqualTo(7L);
        assertThat(report.totals().workshopUtilizationPercent()).isEqualTo(44 * 100.0 / 80);
        assertThat(report.branches()).extracting(b -> b.branchName()).containsExactly("MLAND Hoan Kiem", "MLAND Cau Giay");
        var caoGiay = report.branches().get(1).values();
        assertThat(caoGiay.workshopRevenue()).isEqualTo("0");
        assertThat(caoGiay.totalRevenue()).isEqualTo("761634");
        assertThat(report.developmentDemo()).isFalse();
    }

    @Test void anUnavailableSourceIsNullAndPartialNeverZero() {
        when(sources.retailRevenue(any(), any(), any())).thenReturn(List.of(
            new RetailBranchRevenue(1, "VND", BigInteger.valueOf(500_000), 1)));
        when(sources.workshopRevenue(any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        when(sources.completedSessions(any(), any(), any())).thenReturn(List.of());

        var report = service.report("DAY", null, List.of(1L));

        assertThat(report.status()).isEqualTo("PARTIAL");
        assertThat(report.totals().retailRevenue()).isEqualTo("500000");
        assertThat(report.totals().workshopRevenue()).isNull();
        assertThat(report.totals().totalRevenue()).isNull();
        assertThat(report.totals().workshopUtilizationPercent()).isNull();
        assertThat(report.quality()).anySatisfy(q -> {
            assertThat(q.source()).isEqualTo("WORKSHOP_REVENUE");
            assertThat(q.status()).isEqualTo("UNAVAILABLE");
            assertThat(q.safeReasonCode()).isEqualTo("SOURCE_UNAVAILABLE");
        });
    }

    @Test void noVerifiedRecordsIsEmptyNotComplete() {
        when(sources.retailRevenue(any(), any(), any())).thenReturn(List.of());
        when(sources.workshopRevenue(any(), any(), any())).thenReturn(new WorkshopRevenue(List.of(), 0, 0));
        when(sources.completedSessions(any(), any(), any())).thenReturn(List.of());

        var report = service.report("WEEK", null, List.of(1L));

        assertThat(report.status()).isEqualTo("EMPTY");
        assertThat(report.totals().totalRevenue()).isEqualTo("0");
        assertThat(report.totals().workshopUtilizationPercent()).isNull();
    }

    @Test void nonVndAndUnresolvedFactsAreLeftOutAndMarkThePartialView() {
        when(sources.retailRevenue(any(), any(), any())).thenReturn(List.of(
            new RetailBranchRevenue(1, "VND", BigInteger.valueOf(100), 1),
            new RetailBranchRevenue(1, "USD", BigInteger.valueOf(5), 1)));
        when(sources.workshopRevenue(any(), any(), any())).thenReturn(new WorkshopRevenue(List.of(), 2, 0));
        when(sources.completedSessions(any(), any(), any())).thenReturn(List.of());

        var report = service.report(null, null, List.of(1L));

        assertThat(report.status()).isEqualTo("PARTIAL");
        assertThat(report.totals().retailRevenue()).isEqualTo("100");
        assertThat(report.totals().paidRetailOrderCount()).isEqualTo(1L);
        assertThat(report.quality()).extracting(q -> q.safeReasonCode())
            .contains("NON_VND_EXCLUDED", "WORKSHOP_LOCATION_UNRESOLVED");
    }

    @Test void allSourcesDownIsAServiceUnavailableErrorWithoutValues() {
        when(sources.retailRevenue(any(), any(), any())).thenThrow(new IllegalStateException());
        when(sources.workshopRevenue(any(), any(), any())).thenThrow(new IllegalStateException());
        when(sources.completedSessions(any(), any(), any())).thenThrow(new IllegalStateException());

        assertThatThrownBy(() -> service.report(null, null, List.of(1L)))
            .isInstanceOfSatisfying(ReportingException.class, e -> assertThat(e.code()).isEqualTo("REPORTING_SOURCES_UNAVAILABLE"));
    }

    @Test void readsOnlyTheRequestedScopeAndChangesNothing() {
        when(sources.retailRevenue(any(), any(), any())).thenReturn(List.of());
        when(sources.workshopRevenue(any(), any(), any())).thenReturn(new WorkshopRevenue(List.of(), 0, 0));
        when(sources.completedSessions(any(), any(), any())).thenReturn(List.of());

        service.report("DAY", null, List.of(2L));

        Instant from = Instant.parse("2026-11-13T17:00:00Z");
        Instant to = Instant.parse("2026-11-14T03:00:00Z");
        verify(sources).retailRevenue(List.of(2L), from, to);
        verify(sources).workshopRevenue(List.of(2L), from, to);
        verify(sources).completedSessions(List.of(2L), from, to);
        verify(sources).branchNames(List.of(2L));
        verifyNoMoreInteractions(sources);
    }
}
