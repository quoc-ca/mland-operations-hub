package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.BranchKpi;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.ExecutiveReportResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.KpiValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.ReportScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.SourceQuality;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportSourceQuality.OverallStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportSourceQuality.SourceStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.RetailBranchRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.SessionCapacity;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.WorkshopRevenue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Composes the five FE-10 KPIs per branch and in total (FR-002, FR-010). Each source is read on its own: a failing
 * source makes only its own values null and the view PARTIAL (FR-012, FR-013); unknown is never shown as zero.
 * Viewing is read-only (FR-015).
 */
@Slf4j
@Service
public class ExecutiveReportService {

    private final ReportingSourcePort sources;
    private final ReportingPeriodResolver periods;
    private final Clock clock;

    @Autowired
    public ExecutiveReportService(ReportingSourcePort sources, ReportingPeriodResolver periods) {
        this(sources, periods, Clock.systemUTC());
    }

    ExecutiveReportService(ReportingSourcePort sources, ReportingPeriodResolver periods, Clock clock) {
        this.sources = sources;
        this.periods = periods;
        this.clock = clock;
    }

    public ExecutiveReportResponse report(String periodType, LocalDate anchorDate, List<Long> locationIds) {
        var period = periods.resolve(periodType, anchorDate);
        Instant from = period.startAt();
        Instant to = period.endAt();
        String asOf = Instant.now(clock).toString();

        Read<List<RetailBranchRevenue>> retail = read("RETAIL", () -> sources.retailRevenue(locationIds, from, to));
        Read<WorkshopRevenue> workshop = read("WORKSHOP_REVENUE", () -> sources.workshopRevenue(locationIds, from, to));
        Read<List<SessionCapacity>> sessions = read("WORKSHOP_CAPACITY", () -> sources.completedSessions(locationIds, from, to));
        Map<Long, String> names = namesOf(locationIds);

        Map<Long, BigInteger> retailRevenue = new HashMap<>();
        Map<Long, Long> paidOrders = new HashMap<>();
        long nonVndRetail = 0;
        if (retail.ok()) {
            for (var row : retail.value()) {
                if (!"VND".equals(row.currency())) { nonVndRetail += row.paidOrderCount(); continue; }
                retailRevenue.merge(row.locationId(), row.revenue(), BigInteger::add);
                paidOrders.merge(row.locationId(), row.paidOrderCount(), Long::sum);
            }
        }
        Map<Long, BigInteger> workshopRevenue = new HashMap<>();
        if (workshop.ok()) {
            workshop.value().branches().forEach(row -> workshopRevenue.merge(row.locationId(), row.revenue(), BigInteger::add));
        }
        Map<Long, long[]> capacity = new HashMap<>();
        if (sessions.ok()) {
            sessions.value().forEach(row -> capacity.merge(row.locationId(), new long[]{row.checkedIn(), row.capacity()},
                (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]}));
        }

        List<BranchKpi> branches = new ArrayList<>();
        for (Long id : locationIds) {
            branches.add(new BranchKpi(id, names.getOrDefault(id, "Chi nhánh #" + id), "VND", values(
                retail.ok() ? retailRevenue.getOrDefault(id, BigInteger.ZERO) : null,
                workshop.ok() ? workshopRevenue.getOrDefault(id, BigInteger.ZERO) : null,
                retail.ok() ? paidOrders.getOrDefault(id, 0L) : null,
                sessions.ok() ? capacity.getOrDefault(id, new long[]{0, 0}) : null)));
        }
        KpiValues totals = values(
            retail.ok() ? retailRevenue.values().stream().reduce(BigInteger.ZERO, BigInteger::add) : null,
            workshop.ok() ? workshopRevenue.values().stream().reduce(BigInteger.ZERO, BigInteger::add) : null,
            retail.ok() ? paidOrders.values().stream().mapToLong(Long::longValue).sum() : null,
            sessions.ok() ? capacity.values().stream().reduce(new long[]{0, 0}, (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]}) : null);

        String retailReason = !retail.ok() ? "SOURCE_UNAVAILABLE" : nonVndRetail > 0 ? "NON_VND_EXCLUDED" : null;
        String workshopReason = !workshop.ok() ? "SOURCE_UNAVAILABLE"
            : workshop.value().nonVndFolios() > 0 ? "NON_VND_EXCLUDED"
            : workshop.value().unresolvedFolios() > 0 ? "WORKSHOP_LOCATION_UNRESOLVED" : null;
        String sessionReason = sessions.ok() ? null : "SOURCE_UNAVAILABLE";

        SourceStatus retailStatus = status(retail, retail.ok() && !retail.value().isEmpty());
        SourceStatus workshopStatus = status(workshop, workshop.ok() && !workshop.value().branches().isEmpty());
        SourceStatus sessionStatus = status(sessions, sessions.ok() && !sessions.value().isEmpty());
        boolean attendanceKnown = sessions.ok() && capacity.values().stream().anyMatch(v -> v[0] > 0);
        SourceStatus attendanceStatus = !sessions.ok() ? SourceStatus.UNAVAILABLE : attendanceKnown ? SourceStatus.COMPLETE : SourceStatus.EMPTY;

        List<SourceQuality> quality = List.of(
            new SourceQuality("RETAIL", retailStatus.name(), asOf, retailReason),
            new SourceQuality("WORKSHOP_REVENUE", workshopStatus.name(), asOf, workshopReason),
            new SourceQuality("WORKSHOP_CAPACITY", sessionStatus.name(), asOf, sessionReason),
            new SourceQuality("ATTENDANCE", attendanceStatus.name(), asOf, sessionReason));

        OverallStatus overall = ReportSourceQuality.overall(List.of(retailStatus, workshopStatus, sessionStatus));
        if (overall == OverallStatus.UNAVAILABLE) throw ReportingException.sourcesUnavailable();
        boolean partialFacts = retailReason != null || workshopReason != null;
        String status = overall == OverallStatus.PARTIAL || partialFacts ? "PARTIAL" : overall.name();

        var scope = new ReportScope(period.periodType(), from.toString(), to.toString(),
            ReportingPeriodResolver.BUSINESS_ZONE.getId(), locationIds);
        return new ExecutiveReportResponse(scope, status, "VND", totals, branches, quality, asOf, false);
    }

    private static KpiValues values(BigInteger retail, BigInteger workshop, Long paidOrders, long[] attendance) {
        Long checkedIn = attendance == null ? null : attendance[0];
        Long capacity = attendance == null ? null : attendance[1];
        Double utilization = attendance == null || attendance[1] == 0 ? null : attendance[0] * 100.0 / attendance[1];
        return new KpiValues(ReportingMoney.vnd(retail), ReportingMoney.vnd(workshop), ReportingMoney.sumVnd(retail, workshop),
            paidOrders, checkedIn, capacity, utilization);
    }

    private static SourceStatus status(Read<?> read, boolean hasFacts) {
        if (!read.ok()) return SourceStatus.UNAVAILABLE;
        return hasFacts ? SourceStatus.COMPLETE : SourceStatus.EMPTY;
    }

    private Map<Long, String> namesOf(List<Long> locationIds) {
        try {
            return sources.branchNames(locationIds);
        } catch (RuntimeException ex) {
            log.warn("FE-10 branch names unavailable: {}", ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static <T> Read<T> read(String source, Supplier<T> query) {
        try {
            return new Read<>(query.get(), true);
        } catch (RuntimeException ex) {
            log.warn("FE-10 source {} unavailable: {}", source, ex.getClass().getSimpleName());
            return new Read<>(null, false);
        }
    }

    private record Read<T>(T value, boolean ok) { }
}
