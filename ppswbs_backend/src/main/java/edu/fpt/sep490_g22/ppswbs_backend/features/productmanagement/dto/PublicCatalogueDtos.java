package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response shapes of the public catalogue (OpenAPI {@code PublicProductPage}, {@code PublicProduct}).
 * Only public facts: no proposals, reviews, quantities or holds. IDs and amounts are decimal strings.
 */
public final class PublicCatalogueDtos {

    public static final String CURRENCY = "VND";

    private PublicCatalogueDtos() {
    }

    public record PublicProductPage(List<PublicProduct> items, int page, int pageSize, String totalElements) {
    }

    public record PublicProduct(String id, String name, String description, PublicCategory category,
                                List<PublicImage> images, List<PublicVariant> variants, PriceRange priceRange,
                                boolean canBuy, Instant createdAt) {
    }

    public record PublicCategory(String id, String name) {
    }

    /** {@code url} is the same-origin media endpoint; the provider key is never exposed. */
    public record PublicImage(String id, String url, String altText, int position) {
    }

    /** A disabled size keeps its label ({@code saleEnabled=false}) but cannot be chosen. */
    public record PublicVariant(String id, int size, String sku, String appliedPrice, String currency,
                                boolean saleEnabled, boolean canBuy) {
    }

    public record PriceRange(String minimum, String maximum, String currency) {
    }

    /** Active branch a Member picks before adding to FE-08's cart; {@code id} is the cart line's {@code locationId}. */
    public record PublicSellingLocation(String id, String name, String address) {
    }

    public record SellingLocationList(List<PublicSellingLocation> items) {
    }
}
