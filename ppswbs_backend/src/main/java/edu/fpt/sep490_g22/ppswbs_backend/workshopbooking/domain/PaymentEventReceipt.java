package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable deduplication record for a provider callback event.
 * Provider event identity is unique; prevents double-confirmation.
 * No signed payloads or OTP values are stored.
 */
@Entity
@Table(name = "payment_event_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_per_event_id", columnNames = "provider_event_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEventReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** Unique provider event identifier for deduplication. */
    @Column(name = "provider_event_id", nullable = false, length = 128, unique = true)
    private String providerEventId;

    /** Safe provider reference (no secret content). */
    @Column(name = "provider_reference", nullable = false, length = 128)
    private String providerReference;

    /** Outcome as reported by provider: SUCCESS, FAILED, CANCELLED. */
    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    /** Amount from provider callback (must match invoice deposit). */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Currency from provider callback (must match invoice currency). */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }
}
