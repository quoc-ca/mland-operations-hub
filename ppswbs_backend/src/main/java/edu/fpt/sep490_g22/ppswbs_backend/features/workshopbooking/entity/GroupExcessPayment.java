package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Verified money beyond the one payment that confirmed a group (003.1 FR-013a, T124). Append-only. */
@Entity
@Table(name = "group_excess_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupExcessPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @Column(name = "verified_evidence_reference", nullable = false, length = 128, updatable = false)
    private String verifiedEvidenceReference;

    @Column(name = "merchant_reference", nullable = false, length = 64, updatable = false)
    private String merchantReference;

    @Column(name = "provider_transaction_reference", length = 64, updatable = false)
    private String providerTransactionReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    /** When Mland received the verified evidence. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "refund_request_reference", length = 64, updatable = false)
    private String refundRequestReference;

    /** ACCEPTED, PRIOR_EQUIVALENT or REJECTED; stored as the MySQL ENUM of V156. */
    @Column(name = "refund_outcome", nullable = false, updatable = false, columnDefinition =
            "enum('ACCEPTED','PRIOR_EQUIVALENT','REJECTED')")
    private String refundOutcome;

    @Column(name = "correlation_reference", length = 128, updatable = false)
    private String correlationReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
