package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable catalogue facts of one size, read under the catalogue row locks of a
 * {@link CatalogueSalesFacade} context. Money and quantities are whole VND / units ({@code DECIMAL(19,0)}).
 * {@code appliedPrice} is null only in a historical context for a size that never had an applied price.
 * {@code imageMediaIds} are the product's current READY images in display order, for
 * {@link CatalogueSalesFacade#retainMediaReferences}.
 */
public record SaleVariantFact(long productId, String productName, long variantId, String sku, int size,
                              BigInteger appliedPrice, String currency, BigInteger remainingUnsold,
                              boolean saleEnabled, boolean productDeleted, boolean publiclyEligible,
                              long productVersion, long productContentVersion, long variantVersion,
                              long appliedSequence, List<UUID> imageMediaIds) {

    public static final String CURRENCY = "VND";

    public SaleVariantFact {
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(remainingUnsold, "remainingUnsold");
        imageMediaIds = imageMediaIds == null ? List.of() : List.copyOf(imageMediaIds);
    }
}
