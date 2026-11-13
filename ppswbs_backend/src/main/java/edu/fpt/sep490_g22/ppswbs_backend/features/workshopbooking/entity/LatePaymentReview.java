package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One review of a verified group payment that arrived after its hold (003.1 FR-040/FR-040a). */
@Entity
@Table(name = "late_payment_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatePaymentReview {

    public static final String AUTO_RECONFIRMED = "AUTO_RECONFIRMED";
    public static final String PENDING_MANAGER = "PENDING_MANAGER";
    public static final String MANAGER_CONFIRMED = "MANAGER_CONFIRMED";
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_reference", nullable = false, length = 36, updatable = false)
    private String reviewReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    /** The group payment attempt's merchant reference: shared by the IPN and the QueryDR of the same payment. */
    @Column(name = "verified_evidence_reference", nullable = false, length = 128, updatable = false)
    private String verifiedEvidenceReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "arrived_at", nullable = false, updatable = false)
    private Instant arrivedAt;

    /** Comma-separated failed conditions: CAPACITY, START_TIME, AMOUNT, ARRIVAL_WINDOW, DUPLICATE_BOOKING. */
    @Column(name = "failed_conditions", length = 255)
    private String failedConditions;

    /** Stored as the V113 MySQL ENUM; values are the constants of this class. */
    @Column(name = "outcome", nullable = false, columnDefinition =
            "enum('AUTO_RECONFIRMED','PENDING_MANAGER','MANAGER_CONFIRMED','REFUND_REQUESTED')")
    private String outcome;

    @Column(name = "decision", length = 16)
    private String decision;

    @Column(name = "decided_by_account_id")
    private Long decidedByAccountId;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "earliest_session_at")
    private Instant earliestSessionAt;

    @Column(name = "result_due_at")
    private Instant resultDueAt;

    @Column(name = "refund_request_reference", length = 64)
    private String refundRequestReference;

    @Column(name = "correlation_reference", length = 64)
    private String correlationReference;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
