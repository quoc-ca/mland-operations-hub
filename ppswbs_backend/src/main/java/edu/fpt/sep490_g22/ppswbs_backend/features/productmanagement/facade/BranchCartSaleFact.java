package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Cart facts of one size at one branch, returned by {@link CatalogueSalesFacade#readBranchSaleFacts} (FR-053).
 * Read without locks, so they may change before checkout; the accept re-checks them under
 * {@link CatalogueSalesFacade#withLockedBranchSaleContext}.
 *
 * <ul>
 *   <li>{@code saleEligible}: the size is open for sale (as {@link CartSaleFact}) <em>and</em> the branch is active and
 *       sells it ({@code assignedToBranches} false = a product not yet assigned to any branch, data from before
 *       US10, which every active branch may sell from the size total).</li>
 *   <li>{@code availableQuantity}: what can still be held at this branch — the branch quantity minus valid holds at
 *       the branch, never more than the size total minus all valid holds of the size; zero when not eligible.</li>
 * </ul>
 */
public record BranchCartSaleFact(long variantId, long locationId, long productId, String productName, String sku,
                                 int size, BigInteger appliedPrice, String currency, boolean saleEligible,
                                 BigInteger availableQuantity, boolean assignedToBranches) {

    public BranchCartSaleFact {
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(availableQuantity, "availableQuantity");
    }

    public BranchVariant key() {
        return new BranchVariant(variantId, locationId);
    }
}
