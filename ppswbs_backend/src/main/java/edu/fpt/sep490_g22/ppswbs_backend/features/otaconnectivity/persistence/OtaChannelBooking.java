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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** OTA-owned booking identity and the commercial facts captured at acceptance. */
@Entity
@Table(name = "ota_channel_bookings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ota_channel_booking_external",
                columnNames = {"mapping_id", "external_booking_reference"}),
        @UniqueConstraint(name = "uq_ota_channel_booking_public_reference",
                columnNames = {"public_reference"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtaChannelBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false, updatable = false)
    private OtaChannelMapping mapping;

    @Column(name = "external_booking_reference", nullable = false, length = 255, updatable = false)
    private String externalBookingReference;

    @Column(name = "provider_status", nullable = false, length = 32)
    private String providerStatus;

    @Column(name = "participant_count", nullable = false, updatable = false)
    private Integer participantCount;

    @Column(name = "mland_booking_reference", length = 128)
    private String mlandBookingReference;

    @Column(name = "sales_channel_snapshot", nullable = false, length = 32, updatable = false)
    private String salesChannelSnapshot;

    @Column(name = "displayed_policy_reference", nullable = false, length = 128, updatable = false)
    private String displayedPolicyReference;

    @Column(name = "displayed_policy_version", nullable = false, length = 64, updatable = false)
    private String displayedPolicyVersion;

    @Column(name = "booking_cutoff_at", updatable = false)
    private Instant bookingCutoffAt;

    @Column(name = "booking_cutoff_timezone", length = 64, updatable = false)
    private String bookingCutoffTimezone;

    @Column(name = "retail_amount_minor", nullable = false, updatable = false)
    private Long retailAmountMinor;

    @Column(name = "target_net_amount_minor", nullable = false, updatable = false)
    private Long targetNetAmountMinor;

    @Column(name = "commission_amount_minor", updatable = false)
    private Long commissionAmountMinor;

    @Column(name = "fee_amount_minor", updatable = false)
    private Long feeAmountMinor;

    @Column(name = "accepted_amount_minor", updatable = false)
    private Long acceptedAmountMinor;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OtaChannelBooking capture(OtaChannelMapping mapping,
                                            String externalBookingReference, String providerStatus,
                                            int participantCount, String mlandBookingReference,
                                            String salesChannelSnapshot, String displayedPolicyReference,
                                            String displayedPolicyVersion, Instant bookingCutoffAt,
                                            String bookingCutoffTimezone, long retailAmountMinor,
                                            long targetNetAmountMinor, Long commissionAmountMinor,
                                            Long feeAmountMinor, Long acceptedAmountMinor,
                                            String currencyCode) {
        if (mapping == null || participantCount <= 0 || retailAmountMinor < 0 || targetNetAmountMinor < 0
                || negative(commissionAmountMinor) || negative(feeAmountMinor)
                || negative(acceptedAmountMinor)) {
            throw new IllegalArgumentException("Channel booking snapshot has invalid references or amounts");
        }
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Channel booking currency must be a three-letter uppercase code");
        }
        OtaChannelBooking booking = new OtaChannelBooking();
        booking.publicReference = UUID.randomUUID().toString();
        booking.mapping = mapping;
        booking.externalBookingReference = required(externalBookingReference, 255);
        booking.providerStatus = required(providerStatus, 32);
        booking.participantCount = participantCount;
        booking.mlandBookingReference = optional(mlandBookingReference, 128);
        booking.salesChannelSnapshot = required(salesChannelSnapshot, 32);
        booking.displayedPolicyReference = required(displayedPolicyReference, 128);
        booking.displayedPolicyVersion = required(displayedPolicyVersion, 64);
        booking.bookingCutoffAt = bookingCutoffAt;
        booking.bookingCutoffTimezone = optional(bookingCutoffTimezone, 64);
        booking.retailAmountMinor = retailAmountMinor;
        booking.targetNetAmountMinor = targetNetAmountMinor;
        booking.commissionAmountMinor = commissionAmountMinor;
        booking.feeAmountMinor = feeAmountMinor;
        booking.acceptedAmountMinor = acceptedAmountMinor;
        booking.currencyCode = currencyCode.toUpperCase(Locale.ROOT);
        return booking;
    }

    public void markCancellationRequested() {
        this.providerStatus = "CANCELLATION_REQUESTED";
    }

    public void markCancelled() {
        this.providerStatus = "CANCELLED";
    }

    public void markRefunded() {
        this.providerStatus = "REFUNDED";
    }

    private static boolean negative(Long amount) {
        return amount != null && amount < 0;
    }

    private static String required(String value, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException("Required channel booking snapshot value is missing");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Channel booking snapshot value is invalid");
        }
        return normalized;
    }

    @PrePersist
    void onCreate() {
        if (publicReference == null) publicReference = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
