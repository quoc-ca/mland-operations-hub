package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade;

/**
 * FE-08 boundary to FE-02 catalogue facts. The local implementation is only a
 * development substitute and is replaced by the approved FE-02 facade.
 */
public interface RetailCataloguePort {

    boolean isEligibleForCart(Long variantId, Long locationId);

    /**
     * Reads sale facts for one concrete size at one concrete branch.  A cart
     * line is never allowed to reuse availability or a price from another
     * branch that happens to sell the same size.
     */
    CartProductFact readCartProduct(Long variantId, Long locationId);

    /** {@code productId} lets the screens show the product's public image (Session 2026-11-16); null when unknown. */
    record CartProductFact(String productName, String sku, Integer size, String indicativePrice,
                           String currency, String availableQuantity, boolean saleEligible, Long productId) {
        public CartProductFact(String productName, String sku, Integer size, String indicativePrice,
                               String currency, String availableQuantity, boolean saleEligible) {
            this(productName, sku, size, indicativePrice, currency, availableQuantity, saleEligible, null);
        }
    }
}
