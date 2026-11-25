package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Read-only best-seller rankings for FE-10 (Session 2026-11-23). Each query aggregates in MySQL and returns at most
 * {@code limit} rows, so no order, ticket or customer row leaves the source. One call per source, so one failing
 * source never hides the other. Every period is the half-open interval {@code [from, to)}.
 */
public interface ReportingTopSellerPort {

    /** FE-08 products by units sold in VND branch orders that became PAID in the period, most units first. */
    List<ProductSales> topProducts(Collection<Long> locationIds, Instant from, Instant to, int limit);

    /** FE-03 workshop packages by confirmed tickets of booking groups placed in the period, most tickets first. */
    List<PackageSales> topPackages(Collection<Long> locationIds, Instant from, Instant to, int limit);

    /** {@code revenue}: whole VND of the sold lines at their unit price, before order discounts. */
    record ProductSales(long productId, String name, long quantity, BigInteger revenue) { }

    /** {@code revenue}: whole VND of the confirmed tickets at the package price per participant, before promotions. */
    record PackageSales(long packageId, String name, long tickets, BigInteger revenue) { }
}
