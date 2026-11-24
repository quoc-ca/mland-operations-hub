package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos.ReportScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTopSellerDtos.ExecutiveTopSellerResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTopSellerDtos.TopSeller;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTopSellerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * FE-10 best sellers (Session 2026-11-23): for the chosen period and branches, the top five products by units sold
 * (paid retail orders) and the top five workshop packages by confirmed tickets. Read-only; each source is read on its
 * own, so a failing source is null (listed in {@code unavailable}) and never hides the other.
 */
@Slf4j
@Service
public class ExecutiveTopSellerService {

    static final int LIMIT = 5;

    private final ReportingTopSellerPort sources;
    private final ReportingPeriodResolver periods;
    private final Clock clock;

    @Autowired
    public ExecutiveTopSellerService(ReportingTopSellerPort sources, ReportingPeriodResolver periods) {
        this(sources, periods, Clock.systemUTC());
    }

    ExecutiveTopSellerService(ReportingTopSellerPort sources, ReportingPeriodResolver periods, Clock clock) {
        this.sources = sources;
        this.periods = periods;
        this.clock = clock;
    }

    public ExecutiveTopSellerResponse topSellers(String periodType, LocalDate anchorDate, List<Long> locationIds) {
        var period = periods.resolve(periodType, anchorDate);
        Instant from = period.startAt();
        Instant to = period.endAt();
        List<String> unavailable = new ArrayList<>();

        List<TopSeller> products = read("TOP_PRODUCTS", () -> sources.topProducts(locationIds, from, to, LIMIT).stream()
            .map(p -> new TopSeller(p.productId(), p.name(), p.quantity(), p.revenue().toString())).toList(), unavailable);
        List<TopSeller> packages = read("TOP_PACKAGES", () -> sources.topPackages(locationIds, from, to, LIMIT).stream()
            .map(p -> new TopSeller(p.packageId(), p.name(), p.tickets(), p.revenue().toString())).toList(), unavailable);

        var scope = new ReportScope(period.periodType(), from.toString(), to.toString(),
            ReportingPeriodResolver.BUSINESS_ZONE.getId(), locationIds);
        return new ExecutiveTopSellerResponse(scope, products, packages, List.copyOf(unavailable), Instant.now(clock).toString());
    }

    private static List<TopSeller> read(String source, Supplier<List<TopSeller>> query, List<String> unavailable) {
        try {
            return query.get();
        } catch (RuntimeException ex) {
            log.warn("FE-10 best-seller source {} unavailable: {}", source, ex.getClass().getSimpleName());
            unavailable.add(source);
            return null;
        }
    }
}
