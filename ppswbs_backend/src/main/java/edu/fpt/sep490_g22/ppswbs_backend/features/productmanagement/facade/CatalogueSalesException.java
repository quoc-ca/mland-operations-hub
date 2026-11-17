package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.util.List;
import java.util.Objects;

/**
 * Typed rejection of {@link CatalogueSalesFacade}. It never carries entities, secrets or stack details of
 * productmanagement. The caller's transaction is marked rollback-only when it is thrown.
 */
public class CatalogueSalesException extends RuntimeException {

    public enum Reason {
        /** Malformed argument (empty IDs, nonpositive quantity, blank reference...). */
        INVALID_REQUEST,
        /** A requested size or product does not exist. */
        NOT_FOUND,
        /** A size is not open for a new sale: product not publicly eligible, deleted or size disabled. */
        NOT_FOR_SALE,
        /** The deduction would make remaining unsold negative or lower than the post-settlement held quantity. */
        INSUFFICIENT_QUANTITY,
        /** The operation reference was already used with a different variant or quantity. */
        OPERATION_CONFLICT,
        /** A media object cannot be retained (unknown, staged or being deleted). */
        MEDIA_NOT_AVAILABLE,
        /** {@code recordAcceptedSale} was called outside a context that locked the size. */
        NO_LOCKED_CONTEXT,
        /** The FE-08 availability source is missing or returned unverifiable data. */
        DEPENDENCY_UNAVAILABLE,
        /** Lock timeout, deadlock or concurrent change; retry the whole transaction. */
        BUSY
    }

    private final Reason reason;
    private final List<Long> variantIds;

    public CatalogueSalesException(Reason reason, String message) {
        this(reason, message, List.of());
    }

    public CatalogueSalesException(Reason reason, String message, List<Long> variantIds) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.variantIds = List.copyOf(variantIds);
    }

    public Reason reason() {
        return reason;
    }

    /** Sizes the rejection is about, when known (for example the sizes not for sale). */
    public List<Long> variantIds() {
        return variantIds;
    }
}
