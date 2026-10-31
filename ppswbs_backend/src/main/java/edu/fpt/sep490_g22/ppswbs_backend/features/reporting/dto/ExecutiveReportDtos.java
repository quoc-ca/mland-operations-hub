package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto;
import java.util.List;
public final class ExecutiveReportDtos {
    private ExecutiveReportDtos() { }
    public record KpiValues(String retailRevenue, String workshopRevenue, String totalRevenue, Long paidRetailOrderCount, Long checkedInParticipants, Long completedSessionCapacity, Double workshopUtilizationPercent) { }
    public record BranchKpi(Long locationId, String branchName, String currency, KpiValues values) { }
    public record SourceQuality(String source, String status, String asOf, String safeReasonCode) { }
    public record ReportScope(String periodType, String startAt, String endAt, String timezone, List<Long> locationIds) { }
    public record ExecutiveReportResponse(ReportScope scope, String status, String currency, KpiValues totals, List<BranchKpi> branches, List<SourceQuality> quality, String lastUpdatedAt, boolean developmentDemo) { }
}
