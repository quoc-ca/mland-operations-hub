package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request bodies of the management API, named after the OpenAPI schemas. They are read by the strict
 * JSON converter (unknown fields, wrong types and numbers sent as strings are refused); required fields and
 * value rules are checked by the controller and the services. IDs and amounts are decimal strings.
 */
public final class CatalogueRequests {

    private CatalogueRequests() {
    }

    public record VersionRequest(Long expectedVersion) {
    }

    public record ProductUpdate(Long expectedVersion, String name, String description, String categoryId) {
    }

    public record ImageReference(UUID mediaId, String altText, Integer position) {
    }

    public record ImagesUpdate(Long expectedVersion, List<ImageReference> images) {
    }

    public record PublicationRequest(Long expectedVersion, Boolean published) {
    }

    public record ReviewPrice(String variantId, String proposalId, String approvedPrice) {
    }

    /** {@code ReviewApproval} or {@code ReviewRejection}, told apart by {@code decision}. */
    public record ReviewDecision(String reviewId, Long reviewRevision, Long contentVersion, String decision,
                                 String reason, List<ReviewPrice> prices) {
    }

    public record QuantityRequest(Long expectedVersion, String quantity) {
    }

    public record SaleStatusRequest(Long expectedVersion, Boolean saleEnabled) {
    }

    public record PriceRequest(Long expectedVersion, String proposedPrice) {
    }

    /** {@code PriceApproval} or {@code PriceRejection}, told apart by {@code decision}. */
    public record PriceDecision(Long expectedVersion, String decision, String approvedPrice, String reason) {
    }

    public record CategoryCreate(String name, String description) {
    }

    public record CategoryUpdate(Long expectedVersion, String name, String description) {
    }

    public record ActivationRequest(Long expectedVersion, Boolean active) {
    }
}
