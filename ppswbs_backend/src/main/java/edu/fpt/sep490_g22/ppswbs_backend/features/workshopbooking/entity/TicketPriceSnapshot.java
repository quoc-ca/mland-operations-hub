package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Immutable price, benefit allocation and booking-time collection of one ticket (003.1 FR-033..FR-035). */
@Entity
@Table(name = "ticket_price_snapshots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private TicketBooking ticket;

    @Column(name = "package_price_vnd", nullable = false, updatable = false)
    private long packagePriceVnd;

    @Column(name = "voucher_discount_vnd", nullable = false, updatable = false)
    private long voucherDiscountVnd;

    @Column(name = "points_used", nullable = false, updatable = false)
    private long pointsUsed;

    @Column(name = "point_discount_vnd", nullable = false, updatable = false)
    private long pointDiscountVnd;

    @Column(name = "final_price_vnd", nullable = false, updatable = false)
    private long finalPriceVnd;

    @Column(name = "collected_at_booking_vnd", nullable = false, updatable = false)
    private long collectedAtBookingVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_kind", nullable = false, length = 40, updatable = false)
    private TicketCollectionKind collectionKind;

    @Column(name = "remaining_vnd", nullable = false, updatable = false)
    private long remainingVnd;

    @Column(name = "non_refundable_disclosed", nullable = false, updatable = false)
    private boolean nonRefundableDisclosed;

    @Column(name = "loyalty_policy_revision", updatable = false)
    private Long loyaltyPolicyRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
