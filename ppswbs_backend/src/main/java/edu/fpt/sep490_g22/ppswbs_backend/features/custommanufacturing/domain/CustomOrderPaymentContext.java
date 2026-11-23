package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_order_payment_context")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomOrderPaymentContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "custom_order_id", nullable = false, length = 36)
    private String customOrderId;

    @Column(name = "quote_id", nullable = false, length = 36)
    private String quoteId;

    @Column(name = "merchant_reference", nullable = false, unique = true, length = 64)
    private String merchantReference;

    @Column(name = "payment_attempt_reference", nullable = false, unique = true, length = 64)
    private String paymentAttemptReference;

    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "INITIATED";

    @Column(name = "vnpay_transaction_no", length = 64)
    private String vnpayTransactionNo;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
