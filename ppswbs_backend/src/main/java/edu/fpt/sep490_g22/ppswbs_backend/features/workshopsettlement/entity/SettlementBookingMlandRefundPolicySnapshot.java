package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Immutable policy terms selected using the booking's committed confirmation time. */
@Entity
@Table(name = "settlement_booking_mland_refund_policy_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBookingMlandRefundPolicySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_reference", nullable = false, unique = true, length = 128)
    private String snapshotReference;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 128)
    private String bookingReference;

    @Column(name = "policy_reference", nullable = false, length = 128)
    private String policyReference;

    @Column(name = "refund_percent", nullable = false)
    private Short refundPercent;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) capturedAt = Instant.now();
    }
}
