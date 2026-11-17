package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One gateway attempt for a whole booking group's booking-time collection (003.1 FR-038). */
@Entity
@Table(name = "group_payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupPaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private BookingGroup group;

    @Column(name = "attempt_reference", nullable = false, length = 64, updatable = false)
    private String attemptReference;

    @Column(name = "merchant_reference", nullable = false, length = 64, updatable = false)
    private String merchantReference;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    /** VNPay's own creation timestamp, required later by QueryDR and refunds. */
    @Column(name = "provider_create_date", length = 14, updatable = false)
    private String providerCreateDate;

    @Column(name = "payment_url_snapshot", length = 2048, updatable = false)
    private String paymentUrlSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 16)
    private PaymentAttemptStatus attemptStatus;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (attemptStatus == null) attemptStatus = PaymentAttemptStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
