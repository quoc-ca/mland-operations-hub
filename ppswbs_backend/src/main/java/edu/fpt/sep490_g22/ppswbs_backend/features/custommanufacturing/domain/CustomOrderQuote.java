package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_order_quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomOrderQuote {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "custom_order_id", nullable = false, length = 36)
    private String customOrderId;

    @Column(name = "quote_reference", nullable = false, unique = true, length = 64)
    private String quoteReference;

    @Column(name = "quote_generation", nullable = false)
    @Builder.Default
    private Integer quoteGeneration = 1;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "QUOTE_PENDING";

    @Column(name = "approved_by_manager", nullable = false)
    @Builder.Default
    private Boolean approvedByManager = false;

    @Column(name = "manager_account_id")
    private Long managerAccountId;

    @Column(name = "manager_reason", length = 2000)
    private String managerReason;

    @Column(name = "payment_status", nullable = false, length = 32)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Column(name = "payment_mode", nullable = false, length = 32)
    @Builder.Default
    private String paymentMode = "FULL_CUSTOM_ORDER_PAYMENT";

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
        if (this.quoteReference == null) {
            this.quoteReference = "QUO-" + (this.id != null ? this.id.substring(0, Math.min(8, this.id.length())).toUpperCase() : "Q");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
