package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Locale;

/** Immutable provider-scoped evidence of customer payment/refund activity on the OTA channel. */
@Entity
@Immutable
@Table(name = "ota_customer_payment_evidence", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_payment_evidence_external",
        columnNames = {"channel_booking_id", "external_evidence_reference"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaCustomerPaymentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_booking_id", nullable = false, updatable = false)
    private Long channelBookingId;

    @Column(name = "external_evidence_reference", nullable = false, length = 255, updatable = false)
    private String externalEvidenceReference;

    @Column(name = "evidence_kind", nullable = false, length = 24, updatable = false)
    private String evidenceKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_status", nullable = false, length = 24, updatable = false)
    private OtaCustomerPaymentState evidenceStatus;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private Long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "correlation_reference", length = 128, updatable = false)
    private String correlationReference;

    private OtaCustomerPaymentEvidence(Long channelBookingId, String externalEvidenceReference,
                                      String evidenceKind, OtaCustomerPaymentState evidenceStatus,
                                      Long amountMinor, String currencyCode, Instant occurredAt,
                                      Instant recordedAt, String correlationReference) {
        this.channelBookingId = channelBookingId;
        this.externalEvidenceReference = externalEvidenceReference;
        this.evidenceKind = evidenceKind;
        this.evidenceStatus = evidenceStatus;
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.correlationReference = correlationReference;
    }

    public static OtaCustomerPaymentEvidence record(Long channelBookingId,
                                                     String externalEvidenceReference,
                                                     String evidenceKind,
                                                     OtaCustomerPaymentState evidenceStatus,
                                                     Long amountMinor,
                                                     String currencyCode,
                                                     Instant occurredAt,
                                                     String correlationReference) {
        if (channelBookingId == null || channelBookingId <= 0) {
            throw new IllegalArgumentException("OTA channel booking ID must be positive");
        }
        String externalReference = required(externalEvidenceReference, 255,
                "External payment evidence reference is required");
        String kind = required(evidenceKind, 24, "Payment evidence kind is required");
        if (evidenceStatus == null) {
            throw new IllegalArgumentException("Customer payment state is required");
        }
        if (amountMinor == null || amountMinor <= 0) {
            throw new IllegalArgumentException("Payment evidence amount must be positive");
        }
        String currency = required(currencyCode, 3, "Currency code is required").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency code must use three ISO 4217 letters");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Provider evidence time is required");
        }
        String correlation = optional(correlationReference, 128);
        return new OtaCustomerPaymentEvidence(channelBookingId, externalReference, kind, evidenceStatus,
                amountMinor, currency, occurredAt, Instant.now(), correlation);
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Correlation reference is invalid");
        }
        return normalized;
    }
}
