package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Verified payment evidence whose group confirmation failed on a dependency (003.2 hold commit or 006 benefit
 * settlement) inside the hold. The group stays PENDING_PAYMENT; the row is the wait (003.1 T145, data-model).
 */
@Entity
@Table(name = "group_confirmation_retries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupConfirmationRetry {

    public enum Status { WAITING, CONFIRMED, HANDED_TO_LATE_REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private GroupPaymentAttempt attempt;

    @Column(name = "verified_evidence_reference", nullable = false, length = 128, updatable = false)
    private String verifiedEvidenceReference;

    @Column(name = "merchant_reference", nullable = false, length = 64, updatable = false)
    private String merchantReference;

    @Column(name = "provider_transaction_reference", length = 64, updatable = false)
    private String providerTransactionReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    /** When Mland received the verified evidence; the FR-040 arrival time at hand-over. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_status", nullable = false, length = 24)
    private Status retryStatus;

    @Column(name = "retries_made", nullable = false)
    private int retriesMade;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_failure_code", length = 64)
    private String lastFailureCode;

    @Column(name = "late_payment_review_reference", length = 64)
    private String latePaymentReviewReference;

    @Column(name = "correlation_reference", length = 128, updatable = false)
    private String correlationReference;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (retryStatus == null) retryStatus = Status.WAITING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
