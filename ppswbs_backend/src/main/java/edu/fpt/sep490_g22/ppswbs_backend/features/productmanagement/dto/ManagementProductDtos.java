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

    /**
     * FR-064 counts per list tab for the same filters as the list. {@code pricePending}/{@code priceDraft}/{@code priceRejected} (FR-065) count standalone
     * proposals, not products.
     */
    public record TabCounts(String all, String draft, String pending, String rejected, String approved,
                            String published, String hidden, String pricePending, String priceDraft,
                            String priceRejected) {
    }

    public record ManagementProduct(String id, String name, String description, Category category, boolean published,
                                    Instant firstPublishedAt, Instant deletedAt, long contentVersion, long version,
                                    List<ImageReference> images, List<ManagementVariant> variants,
                                    List<Review> reviews, List<Branch> branches) {
    }

    /**
     * A branch where the product is sold (US10). {@code name} is null when the branch is no longer active in the
     * canonical list; {@code saleEnabled} is false when selling is stopped there for every size.
     */
    public record Branch(String locationId, String name, boolean active, boolean saleEnabled) {
    }

    /**
     * Quantity of a size at one branch (US10); {@code version} is the row version for the branch quantity command.
     * {@code validHeldQuantity}/{@code availableQuantity} (FR-051): holds and availability at this branch, null while the
     * FE-08 source keeps holds per size only (the size's {@code validHeldQuantity} then applies at every branch).
     */
    public record BranchStock(String locationId, String remainingUnsold, boolean saleEnabled, long version,
                              String validHeldQuantity, String availableQuantity) {
    }

    public record Category(String id, String name, String description, boolean active, long version) {
    }

    public record ImageReference(UUID mediaId, String altText, int position) {
    }

    public record ManagementVariant(String id, int size, String sku, String remainingUnsold, String validHeldQuantity,
                                    String availableQuantity, boolean saleEnabled,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String appliedPrice,
                                    long appliedSequence, long version, List<Proposal> proposals,
                                    List<BranchStock> branchStocks) {
    }

    /**
     * FR-060 fields ({@code submittedBy} … {@code decidedAt}) are omitted while the revision was not submitted /
     * decided.
     */
    public record Proposal(String id, String variantId, long revision, long version, String state, boolean effective,
                           String proposedPrice,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String approvedPrice,
                           long baseAppliedSequence, boolean applied,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
                           @JsonInclude(JsonInclude.Include.NON_NULL) ActorRef submittedBy,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Instant submittedAt,
                           @JsonInclude(JsonInclude.Include.NON_NULL) SubmitterBranches submitterBranches,
                           @JsonInclude(JsonInclude.Include.NON_NULL) ActorRef decidedBy,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Instant decidedAt) {
    }

    /** FR-060 fields as on {@link Proposal}. */
    public record Review(String id, long revision, long contentVersion, String state, boolean effective,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
                         @JsonInclude(JsonInclude.Include.NON_NULL) ActorRef submittedBy,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Instant submittedAt,
                         @JsonInclude(JsonInclude.Include.NON_NULL) SubmitterBranches submitterBranches,
                         @JsonInclude(JsonInclude.Include.NON_NULL) ActorRef decidedBy,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Instant decidedAt) {
    }

    /**
     * FR-060: who recorded a submit or decision. {@code kind} ACCOUNT or SYSTEM; {@code displayName} is the final label
     * (full name, else "Tài khoản #id"; "Hệ thống" for SYSTEM). No raw id, email or Firebase UID.
     */
    public record ActorRef(String kind, String displayName) {
    }

    /** FR-061: branch scope of the submitter at submit time; {@code kind} ALL, LISTED, SYSTEM or NOT_RECORDED. */
    public record SubmitterBranches(String kind, List<SubmitterBranch> branches) {
    }

    /** {@code name} as at submit time; null when it was not known then. */
    public record SubmitterBranch(String locationId, @JsonInclude(JsonInclude.Include.NON_NULL) String name) {
    }
}
