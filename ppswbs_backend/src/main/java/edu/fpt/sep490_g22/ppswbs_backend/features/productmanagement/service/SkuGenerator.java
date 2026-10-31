package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import java.util.Locale;

/**
 * SKU format {@code SP{product number}-S{size}} (FR-006): the product number is left-padded with zeros
 * to at least 6 digits and kept whole when longer; the size always has 2 digits (05–29). The SKU is
 * derived from the stable product ID only, so it is never truncated, edited or reused.
 */
public final class SkuGenerator {

    /** "SP" + up to 19 digits + "-S" + 2 digits. */
    public static final int MAX_LENGTH = 25;

    private SkuGenerator() {
    }

    public static String sku(long productId, int size) {
        if (productId <= 0) {
            throw new IllegalArgumentException("Product number must be positive");
        }
        CatalogueValues.requireSize("size", size);
        // Locale.ROOT keeps ASCII digits whatever the JVM default locale is.
        return "SP" + String.format(Locale.ROOT, "%06d", productId) + "-S" + String.format(Locale.ROOT, "%02d", size);
    }
}
