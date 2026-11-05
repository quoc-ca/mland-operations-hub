package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_final_payment_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettlementFinalPaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_reference", nullable = false, unique = true, length = 128)
    private String attemptReference;

    @Column(name = "bill_reference", nullable = false, length = 128)
    private String billReference;

    @Column(name = "booking_reference", nullable = false, length = 128)
    private String bookingReference;

    @Column(name = "operation_reference", nullable = false, unique = true, length = 128)
    private String operationReference;

    @Column(name = "merchant_reference", nullable = false, unique = true, length = 128)
    private String merchantReference;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "provider_create_date", nullable = false, length = 14)
    private String providerCreateDate;

    @Column(name = "payment_url_snapshot", length = 2048)
    private String paymentUrlSnapshot;

    @Column(name = "lifecycle_status", nullable = false, length = 24)
    private String lifecycleStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
