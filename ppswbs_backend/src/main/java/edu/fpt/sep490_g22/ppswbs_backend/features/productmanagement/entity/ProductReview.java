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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * One revision of a new product's review file (data-model {@code catalogue_product_reviews}, FR-009–011).
 *
 * <p>The immutable snapshot is captured at submission: content, category, media references,
 * size IDs and proposal revisions, never identity tokens or hold records. A DRAFT revision has no
 * snapshot yet. Decisions are never rewritten; {@code effective = false} withdraws the revision from
 * publication without deleting its decision. A Manager may approve their own product.
 */
@Entity
@Table(name = "catalogue_product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalogue_product_reviews_revision",
                columnNames = {"product_id", "revision"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ReviewState state;

    @Column(name = "effective", nullable = false)
    private boolean effective;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot")
    private String snapshot;

    public static ProductReview draft(Long productId, long revision, long contentVersion) {
        ProductReview review = new ProductReview();
        review.productId = Objects.requireNonNull(productId, "productId");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        review.revision = revision;
        review.contentVersion = contentVersion;
        review.state = ReviewState.DRAFT;
        review.effective = true;
        return review;
    }

    /** Freezes the snapshot JSON prepared by the submission service and moves to PENDING. */
    public void submit(String actorId, Instant at, String snapshotJson) {
        requireCurrent(ReviewState.DRAFT);
        this.snapshot = Objects.requireNonNull(snapshotJson, "snapshotJson");
        this.submittedBy = Objects.requireNonNull(actorId, "actorId");
        this.submittedAt = Objects.requireNonNull(at, "at");
        this.state = ReviewState.PENDING;
    }

    public void approve(String managerId, Instant at) {
        requireCurrent(ReviewState.PENDING);
        decide(managerId, at, ReviewState.APPROVED);
    }

    public void reject(String managerId, Instant at, String rawReason) {
        requireCurrent(ReviewState.PENDING);
        this.rejectionReason = CatalogueValues.rejectionReason("reason", rawReason);
        decide(managerId, at, ReviewState.REJECTED);
    }

    /** A DRAFT is not frozen yet: it follows the product's current content version until submission. */
    public void followContentVersion(long newContentVersion) {
        if (state != ReviewState.DRAFT || !effective) {
            throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
        }
        this.contentVersion = newContentVersion;
    }

    /** Content, image or proposed price changed before first publication: this revision no longer counts. */
    public void makeIneffective() {
        this.effective = false;
    }

    public boolean isEffectiveApproval() {
        return effective && state == ReviewState.APPROVED;
    }

    private void decide(String managerId, Instant at, ReviewState decided) {
        this.decidedBy = Objects.requireNonNull(managerId, "managerId");
        this.decidedAt = Objects.requireNonNull(at, "at");
        this.state = decided;
    }

    private void requireCurrent(ReviewState expected) {
        if (!effective || state != expected) {
            throw new CatalogueException(CatalogueErrorCode.REVIEW_NOT_CURRENT);
        }
    }
}
