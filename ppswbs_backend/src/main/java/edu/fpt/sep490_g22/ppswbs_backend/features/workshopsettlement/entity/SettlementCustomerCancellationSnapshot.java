package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_customer_cancellation_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementCustomerCancellationSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_reference", nullable = false, unique = true, length = 128)
    private String snapshotReference;

    @Column(name = "case_reference", nullable = false, unique = true, length = 128)
    private String caseReference;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 128)
    private String bookingReference;

    @Column(name = "policy_revision", nullable = false, length = 64)
    private String policyRevision;

    @Column(name = "refund_percent", nullable = false)
    private Short refundPercent;

    @Column(name = "earliest_session_reference", length = 128)
    private String earliestSessionReference;

    @Column(name = "earliest_session_start")
    private Instant earliestSessionStart;

    @Column(name = "paid_deposit_amount_vnd", nullable = false)
    private Long paidDepositAmountVnd;

    @Column(name = "refund_amount_vnd", nullable = false)
    private Long refundAmountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "eligibility_outcome", nullable = false, length = 16)
    private String eligibilityOutcome;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
}
