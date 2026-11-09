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

    /**
     * A disabled size keeps its label ({@code saleEnabled=false}) but cannot be chosen. US10 (FR-046/FR-052):
     * {@code branches} lists the active branches that sell the size, each with whether it can be bought there now —
     * no quantity, hold or stopped branch is exposed. {@code assignedToBranches=false} (a product not yet assigned to
     * any branch) means every active selling location may be chosen and {@code branches} is empty.
     * {@code canBuy} is true when the size can be bought at at least one branch.
     */
    public record PublicVariant(String id, int size, String sku, String appliedPrice, String currency,
                                boolean saleEnabled, boolean canBuy, boolean assignedToBranches,
                                List<PublicVariantBranch> branches) {
    }

    /** A branch selling a size; {@code locationId} is the cart line's {@code locationId}. */
    public record PublicVariantBranch(String locationId, boolean canBuy) {
    }

    public record PriceRange(String minimum, String maximum, String currency) {
    }

    /** Active branch a Member picks before adding to FE-08's cart; {@code id} is the cart line's {@code locationId}. */
    public record PublicSellingLocation(String id, String name, String address) {
    }

    public record SellingLocationList(List<PublicSellingLocation> items) {
    }
}
