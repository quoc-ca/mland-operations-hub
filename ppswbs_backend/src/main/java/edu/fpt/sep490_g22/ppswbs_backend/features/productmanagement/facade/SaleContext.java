package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Facts handed to the callback of {@link CatalogueSalesFacade}. {@code asOf} is the UTC time the context
 * was opened; use it as the {@code asOf} of the FE-08 source guard. Variants are in ascending ID order.
 * {@code acceptedTransactionRef} is set only for a historical context.
 *
 * <p>{@code branchStocks} (FR-053) is filled by the branch contexts only, one fact per requested size × branch in
 * {@link BranchVariant} order; the size-only contexts leave it empty.
 */
public record SaleContext(Instant asOf, String acceptedTransactionRef, List<SaleVariantFact> variants,
                          List<BranchStockFact> branchStocks) {

    public SaleContext {
        Objects.requireNonNull(asOf, "asOf");
        variants = List.copyOf(variants);
        branchStocks = branchStocks == null ? List.of() : List.copyOf(branchStocks);
    }

    /** Size-only context (the contract before FR-053). */
    public SaleContext(Instant asOf, String acceptedTransactionRef, List<SaleVariantFact> variants) {
        this(asOf, acceptedTransactionRef, variants, List.of());
    }

    public boolean historical() {
        return acceptedTransactionRef != null;
    }

    public Optional<SaleVariantFact> variant(long variantId) {
        return variants.stream().filter(fact -> fact.variantId() == variantId).findFirst();
    }

    /** Stock of the size at the branch, when this is a branch context that locked it. */
    public Optional<BranchStockFact> branch(long variantId, long locationId) {
        return branchStocks.stream()
                .filter(fact -> fact.variantId() == variantId && fact.locationId() == locationId)
                .findFirst();
    }
}
