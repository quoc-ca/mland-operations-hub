package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

/** Supplier receivable position, separate from customer payment evidence. */
@Entity
@Table(name = "ota_payout_positions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaPayoutPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_booking_id", nullable = false, updatable = false)
    private Long channelBookingId;

    @Column(name = "price_snapshot_id", updatable = false)
    private Long priceSnapshotId;

    @Column(name = "expected_amount_minor", nullable = false, updatable = false)
    private Long expectedAmountMinor;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OtaPayoutStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private OtaPayoutPosition(Long channelBookingId, Long priceSnapshotId, Long expectedAmountMinor,
                              String currencyCode, OtaPayoutStatus status) {
        this.channelBookingId = channelBookingId;
        this.priceSnapshotId = priceSnapshotId;
        this.expectedAmountMinor = expectedAmountMinor;
        this.currencyCode = currencyCode;
        this.status = status;
    }

    public static OtaPayoutPosition create(Long channelBookingId, Long priceSnapshotId,
                                           Long expectedAmountMinor, String currencyCode,
                                           OtaPayoutStatus status) {
        if (channelBookingId == null || channelBookingId <= 0) {
            throw new IllegalArgumentException("OTA channel booking ID must be positive");
        }
        if (priceSnapshotId != null && priceSnapshotId <= 0) {
            throw new IllegalArgumentException("OTA price snapshot ID must be positive when supplied");
        }
        if (expectedAmountMinor == null || expectedAmountMinor < 0) {
            throw new IllegalArgumentException("Expected payout amount cannot be negative");
        }
        String currency = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency code must use three ISO 4217 letters");
        }
        if (status == null) {
            throw new IllegalArgumentException("Payout status is required");
        }
        return new OtaPayoutPosition(channelBookingId, priceSnapshotId, expectedAmountMinor, currency, status);
    }

    /** Called by the payout workflow only after the corresponding evidence is persisted. */
    public void transitionTo(OtaPayoutStatus target) {
        if (target == null || target.ordinal() != status.ordinal() + 1) {
            throw new IllegalStateException("Payout status must advance by one approved lifecycle step");
        }
        status = target;
    }

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
