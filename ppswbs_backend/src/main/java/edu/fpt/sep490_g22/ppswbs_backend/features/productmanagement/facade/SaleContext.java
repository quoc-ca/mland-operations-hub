package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Facts handed to the callback of {@link CatalogueSalesFacade}. {@code asOf} is the UTC time the context
 * was opened; use it as the {@code asOf} of the FE-08 source guard. Variants are in ascending ID order.
 * {@code acceptedTransactionRef} is set only for a historical context.
 */
public record SaleContext(Instant asOf, String acceptedTransactionRef, List<SaleVariantFact> variants) {

    public SaleContext {
        Objects.requireNonNull(asOf, "asOf");
        variants = List.copyOf(variants);
    }

    public boolean historical() {
        return acceptedTransactionRef != null;
    }

    public Optional<SaleVariantFact> variant(long variantId) {
        return variants.stream().filter(fact -> fact.variantId() == variantId).findFirst();
    }
}
