package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_payment_attempts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bpa_attempt_reference", columnNames = {"attempt_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingPaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private WorkshopBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private BookingInvoice invoice;

    @Column(name = "attempt_reference", nullable = false, length = 64)
    private String attemptReference;

    @Column(name = "merchant_reference", nullable = false, length = 128)
    private String merchantReference;

    @Column(name = "provider_create_date", length = 14)
    private String providerCreateDate;

    @Column(name = "attempt_amount", nullable = false)
    private Long attemptAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentAttemptStatus status;

    @Column(name = "payment_url_snapshot", length = 2048)
    private String paymentUrlSnapshot;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = PaymentAttemptStatus.INITIATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
