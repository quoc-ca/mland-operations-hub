package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/** Immutable reconciliation fact linked to one OTA payout position. */
@Entity
@Immutable
@Table(name = "ota_payout_ledger_entries", uniqueConstraints =
    @UniqueConstraint(name = "uq_ota_payout_ledger_evidence",
        columnNames = {"payout_position_id", "entry_kind", "external_evidence_reference"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtaPayoutLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, length = 36, updatable = false)
    private String publicReference;

    @Column(name = "evidence_type", length = 24, updatable = false)
    private String evidenceType;

    @Column(name = "allocation_reference", length = 128, updatable = false)
    private String allocationReference;

    @Column(name = "payout_reference", length = 200, updatable = false)
    private String payoutReference;

    @Column(name = "payout_position_id", nullable = false, updatable = false)
    private Long payoutPositionId;

    @Column(name = "entry_kind", nullable = false, length = 24, updatable = false)
    private String entryKind;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private Long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "external_evidence_reference", nullable = false, length = 255, updatable = false)
    private String externalEvidenceReference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "correlation_reference", length = 128, updatable = false)
    private String correlationReference;

    @Column(name = "reason", length = 1000, updatable = false)
    private String reason;

    @Column(name = "related_record_reference", length = 128, updatable = false)
    private String relatedRecordReference;

    private OtaPayoutLedgerEntry(Long payoutPositionId, String entryKind, Long amountMinor,
                                 String currencyCode, String externalEvidenceReference,
                                 Instant occurredAt, String reason, String relatedRecordReference,
                                 String correlationReference, OtaPayoutEvidenceType evidenceType,
                                 String allocationReference, String payoutReference) {
        this.publicReference = UUID.randomUUID().toString();
        this.payoutPositionId = payoutPositionId;
        this.entryKind = entryKind;
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.externalEvidenceReference = externalEvidenceReference;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
        this.reason = reason;
        this.relatedRecordReference = relatedRecordReference;
        this.correlationReference = correlationReference;
        this.evidenceType = evidenceType == null ? null : evidenceType.name();
        this.allocationReference = allocationReference;
        this.payoutReference = payoutReference;
    }

    public static OtaPayoutLedgerEntry record(Long payoutPositionId, OtaPayoutLedgerKind entryKind, Long amountMinor,
                                              String currencyCode, String externalEvidenceReference,
                                              Instant occurredAt, String reason, String relatedRecordReference,
                                              String correlationReference) {
        return record(payoutPositionId, entryKind, amountMinor, currencyCode, externalEvidenceReference,
                occurredAt, reason, relatedRecordReference, correlationReference, null, null, null);
    }

    public static OtaPayoutLedgerEntry record(Long payoutPositionId, OtaPayoutLedgerKind entryKind, Long amountMinor,
                                              String currencyCode, String externalEvidenceReference,
                                              Instant occurredAt, String reason, String relatedRecordReference,
                                              String correlationReference, OtaPayoutEvidenceType evidenceType,
                                              String allocationReference) {
        return record(payoutPositionId, entryKind, amountMinor, currencyCode, externalEvidenceReference,
                occurredAt, reason, relatedRecordReference, correlationReference, evidenceType,
                allocationReference, null);
    }

    public static OtaPayoutLedgerEntry record(Long payoutPositionId, OtaPayoutLedgerKind entryKind, Long amountMinor,
                                              String currencyCode, String externalEvidenceReference,
                                              Instant occurredAt, String reason, String relatedRecordReference,
                                              String correlationReference, OtaPayoutEvidenceType evidenceType,
                                              String allocationReference, String payoutReference) {
        if (payoutPositionId == null || payoutPositionId <= 0) {
            throw new IllegalArgumentException("Payout position ID must be positive");
        }
        if (entryKind == null) {
            throw new IllegalArgumentException("Payout ledger entry kind is required");
        }
        if (amountMinor == null) {
            throw new IllegalArgumentException("Payout ledger amount is required");
        }
        String currency = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency code must use three ISO 4217 letters");
        }
        String evidenceReference = required(externalEvidenceReference, 255,
                "External financial evidence reference is required");
        if (occurredAt == null) {
            throw new IllegalArgumentException("Financial evidence time is required");
        }
        String explanation = required(reason, 1000, "Payout ledger reason is required");
        String relatedRecord = required(relatedRecordReference, 128,
                "Related financial record reference is required");
        String correlation = optional(correlationReference, 128);
        String allocation = optionalAllocation(allocationReference);
        String payout = payoutReference == null ? null
                : required(payoutReference, 200, "Payout reference is invalid");
        return new OtaPayoutLedgerEntry(payoutPositionId, entryKind.name(), amountMinor, currency,
                evidenceReference, occurredAt, explanation, relatedRecord, correlation,
                evidenceType, allocation, payout);
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

    private static String optionalAllocation(String value) {
        if (value == null) return null;
        return required(value, 128, "Allocation reference is invalid");
    }
}
