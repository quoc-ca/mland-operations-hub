package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The only service of productmanagement that FE-08 (readyringsales) may call (contracts/interfaces.md,
 * "CatalogueSalesFacade", T048/T062). It is internal Java only, not a REST API; the FE-08 consumer verifies
 * the Member, the hold and the accepted transaction before calling it.
 *
 * <p>Every method except {@link #readSaleFacts} joins the caller's transaction (MANDATORY) and fails outside one. Catalogue row locks are
 * taken in the agreed order category → product → size (ascending IDs) and stay held until the caller's
 * transaction ends; the FE-08 source guard ({@link AvailabilityGuardPort#withGuard}) is taken inside the
 * callback, after them. The caller's transaction should use READ COMMITTED (like catalogue commands) so
 * reads after a lock wait see the latest committed state. Any {@link CatalogueSalesException} marks the
 * caller's transaction rollback-only.
 */
public interface CatalogueSalesFacade {

    /**
     * Read-only facts for the FE-08 cart: one {@link CartSaleFact} per distinct requested size, in ascending
     * variant ID order. Takes no catalogue lock and no source guard; held quantities come from
     * {@link AvailabilityGuardPort#readSnapshots}. Unlike the other methods it may be called with or without a
     * transaction (it opens a read-only one if needed). A size that is not for sale is returned with
     * {@code saleEligible = false}, not rejected. Empty or null IDs are {@code INVALID_REQUEST}; an unknown size
     * is {@code NOT_FOUND} (listing the IDs); a missing source is {@code DEPENDENCY_UNAVAILABLE}.
     */
    List<CartSaleFact> readSaleFacts(Collection<Long> variantIds);

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

    // ---- FR-053 (US10, 2026-11-09): sale per size × branch ----
    // Additive: the size-only methods above keep working until FE-08 has switched to these, then they are removed
    // together with the interim "deduct without branch" rule. Same transaction, lock and error rules as above; the
    // branch ({@code locationId}) is the branch of the cart line / order.

    /**
     * Read-only cart facts per size × branch, one {@link BranchCartSaleFact} per distinct key in {@link BranchVariant}
     * order (see that record for eligibility and available quantity). Held quantities come from
     * {@link AvailabilityGuardPort#readBranchSnapshots}. May be called with or without a transaction. Empty or null
     * keys are {@code INVALID_REQUEST}; an unknown size is {@code NOT_FOUND}; a missing hold or branch source is
     * {@code DEPENDENCY_UNAVAILABLE}.
     */
    List<BranchCartSaleFact> readBranchSaleFacts(Collection<BranchVariant> keys);

    /**
     * {@link #withLockedSaleContext} for sizes at branches: locks the sizes of the keys (category → product → size,
     * ascending) and adds one {@link BranchStockFact} per key to the context. Besides the size checks, each key's
     * branch must be active and sell the size (a product not yet assigned to any branch may be sold at any active
     * branch); otherwise {@code NOT_FOR_SALE} lists the offending sizes. Availability at the branch (branch quantity −
     * branch holds) is checked by FE-08 under {@link AvailabilityGuardPort#withBranchGuard} inside {@code work}.
     */
    <T> T withLockedBranchSaleContext(Collection<BranchVariant> keys, Function<SaleContext, T> work);

    /**
     * {@link #withLockedHistoricalContext} for sizes at branches (settlement or release of an accepted transaction):
     * the same locks and branch facts, without any sale check.
     */
    <T> T withLockedHistoricalBranchContext(String acceptedTransactionRef, Collection<BranchVariant> keys,
                                            Function<SaleContext, T> work);

    /**
     * Deducts {@code quantity} from the size at the branch exactly once per {@code operationRef}, inside a context
     * that locked the size, after FE-08 settled its hold in the same transaction. The branch quantity and the size
     * total both go down; neither may go below zero, and the size total not below the size's held quantity after
     * settlement (per branch too once {@link AvailabilityGuardPort#tracksHoldsPerBranch} is true). A product not yet
     * assigned to any branch is deducted from its size total only. Repeating the same operation returns the earlier
     * result; reusing the reference for another size, branch or quantity is {@code OPERATION_CONFLICT}; a branch that
     * does not sell an assigned size is {@code NOT_FOR_SALE}.
     */
    BranchAcceptedSaleResult recordAcceptedSale(UUID operationRef, long variantId, long locationId, BigInteger quantity);

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
