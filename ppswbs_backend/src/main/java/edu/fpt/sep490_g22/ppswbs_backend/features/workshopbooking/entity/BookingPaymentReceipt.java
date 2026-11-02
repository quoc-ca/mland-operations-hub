package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_payment_receipts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bpr_provider_event", columnNames = {"provider_event_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingPaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id")
    private BookingPaymentAttempt paymentAttempt;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "provider_name", nullable = false, length = 32)
    private String providerName;

    @Column(name = "amount_received", nullable = false)
    private Long amountReceived;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "safe_payload_hash", nullable = false, length = 64)
    private String safePayloadHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (providerName == null) {
            providerName = "VNPAY";
        }
    }
}
