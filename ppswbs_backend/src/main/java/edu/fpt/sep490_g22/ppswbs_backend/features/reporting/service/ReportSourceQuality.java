package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import java.util.Collection;
import java.util.Objects;

/** Source quality rules used before real reporting facades are connected. */
public final class ReportSourceQuality {
    private ReportSourceQuality() { }

    public enum SourceStatus { COMPLETE, EMPTY, UNAVAILABLE }
    public enum OverallStatus { COMPLETE, EMPTY, PARTIAL, UNAVAILABLE }

    public static OverallStatus overall(Collection<SourceStatus> sources) {
        if (sources == null || sources.isEmpty() || sources.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Source statuses are required");
        }
        if (sources.stream().allMatch(status -> status == SourceStatus.UNAVAILABLE)) return OverallStatus.UNAVAILABLE;
        if (sources.stream().anyMatch(status -> status == SourceStatus.UNAVAILABLE)) return OverallStatus.PARTIAL;
        if (sources.stream().allMatch(status -> status == SourceStatus.EMPTY)) return OverallStatus.EMPTY;
        return OverallStatus.COMPLETE;
    }
}
