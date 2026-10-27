package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

/**
 * The only service of productmanagement that FE-08 (readyringsales) may call (contracts/interfaces.md,
 * "CatalogueSalesFacade", T048/T062). It is internal Java only, not a REST API; the FE-08 consumer verifies
 * the Member, the hold and the accepted transaction before calling it.
 *
 * <p>Every method joins the caller's transaction (MANDATORY) and fails outside one. Catalogue row locks are
 * taken in the agreed order category → product → size (ascending IDs) and stay held until the caller's
 * transaction ends; the FE-08 source guard ({@link AvailabilityGuardPort#withGuard}) is taken inside the
 * callback, after them. The caller's transaction should use READ COMMITTED (like catalogue commands) so
 * reads after a lock wait see the latest committed state. Any {@link CatalogueSalesException} marks the
 * caller's transaction rollback-only.
 */
public interface CatalogueSalesFacade {

    /**
     * Locks the given sizes for a new sale and runs {@code work} with their facts. Every size must belong to
     * a publicly eligible, not deleted product (published, active category, usable image, applied price for
     * every size) and be sale-enabled; otherwise {@code NOT_FOR_SALE} lists the offending sizes. Availability
     * (remaining − held) is checked by FE-08 under its source guard inside {@code work}.
     */
    <T> T withLockedSaleContext(Collection<Long> variantIds, Function<SaleContext, T> work);

    /**
     * Same locks for the settlement or release of a transaction FE-08 already accepted and verified. The
     * product may meanwhile be hidden, deleted or disabled. It grants nothing for a new hold.
     */
    <T> T withLockedHistoricalContext(String acceptedTransactionRef, Collection<Long> variantIds,
                                      Function<SaleContext, T> work);

    /**
     * Deducts {@code quantity} from the size's remaining unsold quantity exactly once per
     * {@code operationRef}. Must be called inside one of the contexts above that locked the size, after FE-08
     * settled its hold in the same transaction: the remaining quantity after deduction must be at least the
     * post-settlement held quantity read through {@link AvailabilityGuardPort}. Repeating the same
     * operation returns the earlier result; reusing the reference for another size or quantity is
     * {@code OPERATION_CONFLICT}.
     */
    AcceptedSaleResult recordAcceptedSale(UUID operationRef, long variantId, BigInteger quantity);

    /**
     * Registers that an FE-08 record (for example an order item snapshot) uses these images, so cleanup keeps
     * them. Call in the transaction that stores the snapshot. {@code ownerRecordRef} is FE-08's stable
     * reference of that record, at most 128 characters.
     */
    void retainMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds);

    /**
     * Releases the references of an FE-08 record whose retention ended. Soft-deleting a product never does
     * this; the image is deleted later only if nothing else references it.
     */
    void releaseMediaReferences(String ownerRecordRef, Collection<UUID> mediaIds);
}
