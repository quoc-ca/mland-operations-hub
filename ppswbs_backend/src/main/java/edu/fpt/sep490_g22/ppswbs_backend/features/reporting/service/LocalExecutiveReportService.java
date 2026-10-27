package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Profile("local")
public class LocalExecutiveReportService {
    private static final List<Long> AUTHORIZED_LOCATIONS = List.of(10L, 20L);
    private final ReportingPeriodResolver periodResolver;
    public LocalExecutiveReportService(ReportingPeriodResolver periodResolver) { this.periodResolver = periodResolver; }
    public ExecutiveReportDtos.ExecutiveReportResponse report(String periodType, LocalDate anchorDate, List<Long> locationIds) {
        List<Long> effective = locationIds == null || locationIds.isEmpty() ? AUTHORIZED_LOCATIONS : locationIds;
        if (!AUTHORIZED_LOCATIONS.containsAll(effective) || effective.stream().distinct().count() != effective.size()) throw new ReportingConflictException("Requested location is outside the local Manager demo scope");
        var period = periodResolver.resolve(periodType, anchorDate);
        List<ExecutiveReportDtos.BranchKpi> branches = effective.stream().map(this::branch).toList();
        long retail = branches.stream().mapToLong(b -> Long.parseLong(b.values().retailRevenue())).sum();
        long workshop = branches.stream().mapToLong(b -> Long.parseLong(b.values().workshopRevenue())).sum();
        long orders = branches.stream().mapToLong(b -> b.values().paidRetailOrderCount()).sum();
        long checkedIn = branches.stream().mapToLong(b -> b.values().checkedInParticipants()).sum();
        long capacity = branches.stream().mapToLong(b -> b.values().completedSessionCapacity()).sum();
        var totals = new ExecutiveReportDtos.KpiValues(String.valueOf(retail), String.valueOf(workshop), String.valueOf(retail + workshop), orders, checkedIn, capacity, capacity == 0 ? null : checkedIn * 100.0 / capacity);
        String now = Instant.now().toString();
        var quality = List.of(new ExecutiveReportDtos.SourceQuality("RETAIL", "COMPLETE", now, "LOCAL_DEMO"), new ExecutiveReportDtos.SourceQuality("WORKSHOP_REVENUE", "COMPLETE", now, "LOCAL_DEMO"), new ExecutiveReportDtos.SourceQuality("WORKSHOP_CAPACITY", "COMPLETE", now, "LOCAL_DEMO"), new ExecutiveReportDtos.SourceQuality("ATTENDANCE", "COMPLETE", now, "LOCAL_DEMO"));
        return new ExecutiveReportDtos.ExecutiveReportResponse(new ExecutiveReportDtos.ReportScope(period.periodType(), period.startAt().toString(), period.endAt().toString(), ReportingPeriodResolver.BUSINESS_ZONE.getId(), effective), "COMPLETE", "VND", totals, branches, quality, now, true);
    }
    private ExecutiveReportDtos.BranchKpi branch(Long id) {
        boolean hanoi = id.equals(10L); long retail = hanoi ? 8_000_000L : 5_200_000L; long workshop = hanoi ? 12_000_000L : 7_500_000L; long checkedIn = hanoi ? 28 : 16; long capacity = hanoi ? 50 : 30;
        return new ExecutiveReportDtos.BranchKpi(id, hanoi ? "Hanoi Atelier" : "Saigon Craft Lab", "VND", new ExecutiveReportDtos.KpiValues(String.valueOf(retail), String.valueOf(workshop), String.valueOf(retail + workshop), hanoi ? 6L : 4L, checkedIn, capacity, checkedIn * 100.0 / capacity));
    }
}
