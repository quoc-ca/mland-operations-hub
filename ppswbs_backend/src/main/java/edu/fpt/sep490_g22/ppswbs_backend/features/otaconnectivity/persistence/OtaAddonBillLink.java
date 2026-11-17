package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ota_addon_bill_links", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_addon_booking_quote", columnNames = {"channel_booking_id", "settlement_quote_reference"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaAddonBillLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_booking_id", nullable = false)
    private OtaChannelBooking channelBooking;

    @Column(name = "settlement_quote_reference", nullable = false, length = 128)
    private String settlementQuoteReference;

    @Column(name = "settlement_bill_reference", length = 128)
    private String settlementBillReference;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "addon_type", length = 32)
    private String addonType;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "item_description", length = 500)
    private String itemDescription;

    @Column(name = "customer_consent_reference", length = 128)
    private String customerConsentReference;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

