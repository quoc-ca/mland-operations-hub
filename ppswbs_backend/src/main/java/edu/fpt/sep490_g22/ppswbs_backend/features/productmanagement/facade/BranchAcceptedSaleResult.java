package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Result of {@link CatalogueSalesFacade#recordAcceptedSale(UUID, long, long, BigInteger)} (FR-053).
 * {@code branchRemainingAfter} is the branch quantity after the deduction (the size total for a product not yet
 * assigned to any branch); {@code sizeRemainingAfter} the size total. {@code replayed} is true when the same
 * operation reference was already recorded and its stored result is returned.
 */
public record BranchAcceptedSaleResult(UUID operationRef, long variantId, long locationId, BigInteger quantity,
                                       BigInteger branchRemainingAfter, BigInteger sizeRemainingAfter,
                                       long variantVersion, boolean replayed) {
}
