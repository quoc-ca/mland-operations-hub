package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomOrder {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "custom_order_reference", nullable = false, unique = true, length = 64)
    private String customOrderReference;

    @Column(name = "member_account_id", nullable = false)
    private Long memberAccountId;

    @Column(name = "design_request_id", nullable = false, length = 36)
    private String designRequestId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "QUOTE_PENDING";

    @Column(name = "production_status", length = 32)
    private String productionStatus;

    @Column(name = "fulfilment_status", length = 32)
    private String fulfilmentStatus;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

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
        if (this.customOrderReference == null) {
            this.customOrderReference = "CO-" + (this.id != null ? this.id.substring(0, Math.min(8, this.id.length())).toUpperCase() : "REQ");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
