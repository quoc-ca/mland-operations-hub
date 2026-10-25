package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_design_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDesignRequest {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "booking_code", nullable = false, length = 64)
    private String bookingCode;

    @Column(name = "design_version", nullable = false)
    @Builder.Default
    private Integer designVersion = 1;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

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
        if (this.designVersion == null) {
            this.designVersion = 1;
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
