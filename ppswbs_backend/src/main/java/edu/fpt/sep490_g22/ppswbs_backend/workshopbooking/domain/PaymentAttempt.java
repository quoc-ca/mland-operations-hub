package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records a single payment attempt against a booking's invoice.
 * FAILED state is stored here only; a failed attempt does not change booking status while hold is active.
 * No raw OTP, signed payloads or provider secrets are stored here.
 */
@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** Opaque provider reference (safe, no PII or secret). */
    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    /** Amount sent to gateway (must match invoice deposit amount). */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Currency sent to gateway (must match invoice currency). */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentAttemptStatus status;

    /** Gateway payment link expiry (at most 10 minutes). */
    @Column(name = "gateway_expires_at")
    private Instant gatewayExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
