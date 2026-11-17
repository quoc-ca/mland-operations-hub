package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Display facts of one size for the FE-08 cart (add-to-cart, cart preview), returned by
 * {@link CatalogueSalesFacade#readSaleFacts}. Read without locks, so they may change before checkout; the
 * accept must re-check under {@link CatalogueSalesFacade#withLockedSaleContext}.
 *
 * <ul>
 *   <li>{@code saleEligible}: the product is publicly eligible (published, not deleted, active category, usable
 *       image, applied price for every size) and the size is sale-enabled.</li>
 *   <li>{@code appliedPrice}: whole VND; null only when the size never had an applied price (then not eligible).</li>
 *   <li>{@code availableQuantity}: remaining unsold − valid held, never negative; zero when not eligible.</li>
 * </ul>
 */
public record CartSaleFact(long variantId, long productId, String productName, String sku, int size,
                           BigInteger appliedPrice, String currency, boolean saleEligible,
                           BigInteger availableQuantity) {

    public CartSaleFact {
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(availableQuantity, "availableQuantity");
    }
}
