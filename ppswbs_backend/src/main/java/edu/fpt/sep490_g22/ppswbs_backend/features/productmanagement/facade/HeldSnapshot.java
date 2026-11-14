package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact quantity held by valid FE-08 holds for one size, as verified by the real source at {@code asOf}.
 * {@code sourceRevision} changes whenever a hold of the size is created, settled or released. Zero is a
 * verified zero, never a stand-in for unknown.
 */
public record HeldSnapshot(long variantId, BigInteger heldQuantity, long sourceRevision) {

    public HeldSnapshot {
        Objects.requireNonNull(heldQuantity, "heldQuantity");
        if (heldQuantity.signum() < 0) {
            throw new IllegalArgumentException("heldQuantity must not be negative");
        }
    }
}
