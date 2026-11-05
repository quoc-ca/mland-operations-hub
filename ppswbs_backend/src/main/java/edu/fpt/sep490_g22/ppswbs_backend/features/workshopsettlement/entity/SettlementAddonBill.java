package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_addon_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementAddonBill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_reference", nullable = false, unique = true, length = 128)
    private String billReference;
    @Column(name = "channel_booking_reference", nullable = false, length = 128)
    private String channelBookingReference;
    @Column(name = "quote_reference", nullable = false, unique = true, length = 128)
    private String quoteReference;
    @Column(name = "item_code", nullable = false, length = 128)
    private String itemCode;
    @Column(name = "item_description", nullable = false, length = 255)
    private String itemDescription;
    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "customer_consent_reference", nullable = false, length = 128)
    private String customerConsentReference;
    @Column(name = "staff_actor_reference", nullable = false, length = 128)
    private String staffActorReference;
    @Column(name = "location_id")
    private Long locationId;
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    private String lifecycleStatus;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
