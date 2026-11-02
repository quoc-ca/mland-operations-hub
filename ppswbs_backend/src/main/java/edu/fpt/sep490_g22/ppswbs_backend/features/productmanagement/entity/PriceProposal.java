package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;

/**
 * One revision of a price proposal for one size (data-model {@code catalogue_price_proposals}, FR-013/014).
 *
 * <ul>
 *   <li>A revised proposal is a new row that {@code supersedes} the previous one; history is never rewritten.</li>
 *   <li>Only the current effective PENDING revision may be referenced by {@code variant.pending_proposal_id};
 *       the "one pending per size" rule is enforced by the service under the variant lock.</li>
 *   <li>APPROVED only records the Manager decision; it does not mean the price is applied.</li>
 *   <li>Quantity is not a price term.</li>
 * </ul>
 * Variant, review and superseded proposal are referenced by scalar IDs (FK restrict in the migration).
 */
@Entity
@Table(name = "catalogue_price_proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceProposal {

    public static final String CURRENCY = "VND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /** Set when the proposal is part of a new product's review. */
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ReviewState state;

    @Column(name = "effective", nullable = false)
    private boolean effective;

    @Column(name = "proposed_amount", nullable = false, precision = 19, scale = 0)
    private BigInteger proposedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 0)
    private BigInteger approvedAmount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    /** Applied-price sequence of the variant when this revision was created; protects against stale apply. */
    @Column(name = "base_applied_sequence", nullable = false)
    private long baseAppliedSequence;

    /** Digest binding variant identity, size, currency and review context of the decision. */
    @Column(name = "terms_digest", nullable = false, length = 64)
    private String termsDigest;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Required (1–2000 code points) only when REJECTED. */
    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    public static PriceProposal draft(Long variantId, Long reviewId, long revision, BigInteger proposedAmount,
                                      long baseAppliedSequence, String termsDigest, Long supersedesId) {
        PriceProposal proposal = new PriceProposal();
        proposal.variantId = Objects.requireNonNull(variantId, "variantId");
        proposal.reviewId = reviewId;
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        proposal.revision = revision;
        proposal.state = ReviewState.DRAFT;
        proposal.effective = true;
        proposal.proposedAmount = requirePositive("proposedPrice", proposedAmount);
        proposal.currency = CURRENCY;
        proposal.baseAppliedSequence = baseAppliedSequence;
        proposal.termsDigest = Objects.requireNonNull(termsDigest, "termsDigest");
        proposal.supersedesId = supersedesId;
        return proposal;
    }

    public void submit(String actorId, Instant at) {
        requireEffective();
        requireState(ReviewState.DRAFT);
        this.submittedBy = Objects.requireNonNull(actorId, "actorId");
        this.submittedAt = Objects.requireNonNull(at, "at");
        this.state = ReviewState.PENDING;
    }

    /** Manager decision; the final amount may differ from the proposed one. Does not apply the price. */
    public void approve(String managerId, Instant at, BigInteger finalAmount) {
        requireEffective();
        requireState(ReviewState.PENDING);
        this.approvedAmount = requirePositive("approvedPrice", finalAmount);
        decide(managerId, at, ReviewState.APPROVED);
    }

    public void reject(String managerId, Instant at, String rawReason) {
        requireEffective();
        requireState(ReviewState.PENDING);
        this.reason = CatalogueValues.rejectionReason("reason", rawReason);
        decide(managerId, at, ReviewState.REJECTED);
    }

    /** The revision can no longer be decided or applied; its recorded decision is kept. */
    public void makeIneffective() {
        this.effective = false;
    }

    public boolean isCurrentPending() {
        return effective && state == ReviewState.PENDING;
    }

    private void decide(String managerId, Instant at, ReviewState decided) {
        this.decidedBy = Objects.requireNonNull(managerId, "managerId");
        this.decidedAt = Objects.requireNonNull(at, "at");
        this.state = decided;
    }

    private void requireState(ReviewState expected) {
        if (state != expected) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
    }

    private void requireEffective() {
        if (!effective) {
            throw new CatalogueException(CatalogueErrorCode.STALE_VERSION);
        }
    }

    private static BigInteger requirePositive(String field, BigInteger amount) {
        if (amount == null || amount.signum() <= 0 || amount.toString().length() > 19) {
            throw CatalogueException.invalidField(field, "Must be a positive whole VND amount of at most 19 digits.");
        }
        return amount;
    }
}
