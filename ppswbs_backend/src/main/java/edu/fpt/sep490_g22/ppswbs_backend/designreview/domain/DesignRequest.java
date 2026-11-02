package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "design_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignRequest {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "request_reference", nullable = false, unique = true, length = 64)
    private String requestReference;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(name = "guest_proof_hash", length = 128)
    private String guestProofHash;

    @Column(name = "ownership_type", nullable = false, length = 16)
    @Builder.Default
    private String ownershipType = "MEMBER";

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "catalogue_reference", length = 128)
    private String catalogueReference;

    @Column(name = "request_version", nullable = false)
    @Builder.Default
    private Integer requestVersion = 1;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_status", nullable = false, length = 32)
    @Builder.Default
    private String requestStatus = "ACTIVE";

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 32)
    private AnalysisStatus analysisStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus;

    @Column(name = "estimated_value")
    private Long estimatedValue;

    @Column(name = "next_action", nullable = false, length = 64)
    private String nextAction;

    @Column(name = "safe_failure_code", length = 64)
    private String safeFailureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.requestReference == null) {
            this.requestReference = "DR-" + (this.id != null ? this.id.substring(0, Math.min(8, this.id.length())).toUpperCase() : "REQ");
        }
        if (this.ownershipType == null) {
            this.ownershipType = "MEMBER";
        }
        if (this.requestVersion == null) {
            this.requestVersion = 1;
        }
        if (this.requestStatus == null) {
            this.requestStatus = "ACTIVE";
        }
        if (this.analysisStatus == null) {
            this.analysisStatus = AnalysisStatus.PENDING;
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = ReviewStatus.NEED_REVIEW;
        }
        if (this.nextAction == null) {
            this.nextAction = "AWAITING_ANALYSIS";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
