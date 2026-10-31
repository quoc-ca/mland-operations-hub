package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_mland_remedy_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementMlandRemedySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_reference", nullable = false, unique = true, length = 128)
    private String snapshotReference;

    @Column(name = "case_reference", nullable = false, unique = true, length = 128)
    private String caseReference;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 128)
    private String bookingReference;

    @Column(name = "policy_reference", nullable = false, length = 128)
    private String policyReference;

    @Column(name = "cancellation_handoff_reference", nullable = false, length = 128)
    private String cancellationHandoffReference;

    @Column(name = "replacement_outcome", nullable = false, length = 16)
    private String replacementOutcome;

    @Column(name = "refund_percent", nullable = false)
    private Short refundPercent;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "collected_amount_vnd", nullable = false)
    private Long collectedAmountVnd;

    @Column(name = "refundable_amount_vnd", nullable = false)
    private Long refundableAmountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) capturedAt = Instant.now();
    }
}
