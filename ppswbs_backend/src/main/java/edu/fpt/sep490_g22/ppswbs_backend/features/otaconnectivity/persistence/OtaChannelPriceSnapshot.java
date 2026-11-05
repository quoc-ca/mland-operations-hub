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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Immutable snapshot of price terms that the provider has confirmed externally. */
@Entity
@Table(name = "ota_channel_price_snapshots")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaChannelPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private OtaChannelMapping mapping;

    @Column(name = "retail_amount_minor", nullable = false)
    private Long retailAmountMinor;

    @Column(name = "target_net_amount_minor", nullable = false)
    private Long targetNetAmountMinor;

    @Column(name = "commission_amount_minor")
    private Long commissionAmountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Safe reference to provider-confirmed terms; never a raw contract or credential. */
    @Column(name = "terms_reference", nullable = false, length = 255)
    private String termsReference;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private OtaChannelPriceSnapshot(OtaChannelMapping mapping, Long retailAmountMinor,
                                    Long targetNetAmountMinor, Long commissionAmountMinor,
                                    String currencyCode, String termsReference,
                                    Instant effectiveFrom, Instant effectiveTo) {
        this.mapping = mapping;
        this.publicReference = UUID.randomUUID().toString();
        this.retailAmountMinor = retailAmountMinor;
        this.targetNetAmountMinor = targetNetAmountMinor;
        this.commissionAmountMinor = commissionAmountMinor;
        this.currencyCode = currencyCode;
        this.termsReference = termsReference;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** Create the final approved snapshot only after an external provider confirmation. */
    public static OtaChannelPriceSnapshot providerConfirmed(OtaChannelMapping mapping,
                                                              Long retailAmountMinor,
                                                              Long targetNetAmountMinor,
                                                              Long commissionAmountMinor,
                                                              String currencyCode,
                                                              String providerConfirmationReference,
                                                              Instant effectiveFrom,
                                                              Instant effectiveTo) {
        if (mapping == null) {
            throw new IllegalArgumentException("A channel mapping is required");
        }
        if (retailAmountMinor == null || retailAmountMinor < 0
                || targetNetAmountMinor == null || targetNetAmountMinor < 0
                || (commissionAmountMinor != null && commissionAmountMinor < 0)) {
            throw new IllegalArgumentException("Commercial price amounts must be non-negative minor units");
        }
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase ISO code");
        }
        if (providerConfirmationReference == null || providerConfirmationReference.isBlank()
                || providerConfirmationReference.length() > 255) {
            throw new IllegalArgumentException("A safe provider confirmation reference is required");
        }
        if (effectiveFrom == null || (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("The commercial terms effective window is invalid");
        }
        return new OtaChannelPriceSnapshot(mapping, retailAmountMinor, targetNetAmountMinor,
                commissionAmountMinor, currencyCode.toUpperCase(Locale.ROOT),
                providerConfirmationReference.trim(), effectiveFrom, effectiveTo);
    }

    @PrePersist
    void onCreate() {
        if (publicReference == null) {
            publicReference = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
