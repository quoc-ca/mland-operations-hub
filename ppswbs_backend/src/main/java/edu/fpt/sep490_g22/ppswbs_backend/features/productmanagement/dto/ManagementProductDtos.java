package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response shapes of {@code listManagementProducts} / {@code getManagementProduct}
 * (OpenAPI {@code ManagementProductPage}, {@code ManagementProduct}). IDs, amounts and quantities
 * are decimal strings. Required-but-nullable fields are serialized as {@code null}; optional fields
 * are omitted when absent.
 */
public final class ManagementProductDtos {

    public static final int PAGE_SIZE = 20;

    private ManagementProductDtos() {
    }

    public record ManagementProductPage(List<ManagementProduct> items, int page, int pageSize, String totalElements) {
    }

    public record ManagementProduct(String id, String name, String description, Category category, boolean published,
                                    Instant firstPublishedAt, Instant deletedAt, long contentVersion, long version,
                                    List<ImageReference> images, List<ManagementVariant> variants,
                                    List<Review> reviews) {
    }

    public record Category(String id, String name, String description, boolean active, long version) {
    }

    public record ImageReference(UUID mediaId, String altText, int position) {
    }

    public record ManagementVariant(String id, int size, String sku, String remainingUnsold, String validHeldQuantity,
                                    String availableQuantity, boolean saleEnabled,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String appliedPrice,
                                    long appliedSequence, long version, List<Proposal> proposals) {
    }

    public record Proposal(String id, String variantId, long revision, long version, String state, boolean effective,
                           String proposedPrice,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String approvedPrice,
                           long baseAppliedSequence, boolean applied,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {
    }

    public record Review(String id, long revision, long contentVersion, String state, boolean effective,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {
    }
}
