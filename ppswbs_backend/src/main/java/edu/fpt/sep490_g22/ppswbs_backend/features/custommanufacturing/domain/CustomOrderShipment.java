package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "custom_order_shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomOrderShipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "custom_order_id", nullable = false, length = 36)
    private String customOrderId;

    @Column(name = "custom_order_reference", nullable = false, length = 64)
    private String customOrderReference;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 64)
    private String trackingNumber;

    @Column(name = "carrier_service", nullable = false, length = 32)
    @Builder.Default
    private String carrierService = "GHTK";

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "recipient_name", length = 128)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PREPARING";

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

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
        if (this.status == null) {
            this.status = "PREPARING";
        }
        if (this.version == null) {
            this.version = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
