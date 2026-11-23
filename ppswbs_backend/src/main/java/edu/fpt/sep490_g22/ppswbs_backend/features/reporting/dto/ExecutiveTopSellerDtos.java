package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto;

import java.util.List;

/**
 * FE-10 best sellers (Session 2026-11-23): the five products with the most units sold and the five workshop packages
 * with the most confirmed tickets in the chosen period and branches. A null list means the source could not be read,
 * never "nothing sold"; an empty list means nothing was sold. Money is whole VND as a string.
 */
public final class ExecutiveTopSellerDtos {
    private ExecutiveTopSellerDtos() { }

    /** {@code quantity}: units for a product, confirmed tickets for a package. */
    public record TopSeller(Long id, String name, long quantity, String revenue) { }

    public record ExecutiveTopSellerResponse(
        ExecutiveReportDtos.ReportScope scope, List<TopSeller> products, List<TopSeller> packages,
        List<String> unavailable, String lastUpdatedAt) { }
}
