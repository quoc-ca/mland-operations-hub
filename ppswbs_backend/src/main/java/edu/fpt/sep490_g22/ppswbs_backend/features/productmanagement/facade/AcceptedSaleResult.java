package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Result of {@link CatalogueSalesFacade#recordAcceptedSale}. {@code replayed} is true when the same
 * operation was already recorded and nothing was deducted again.
 */
public record AcceptedSaleResult(UUID operationRef, long variantId, BigInteger quantity,
                                 BigInteger remainingAfter, long variantVersion, boolean replayed) {
}
