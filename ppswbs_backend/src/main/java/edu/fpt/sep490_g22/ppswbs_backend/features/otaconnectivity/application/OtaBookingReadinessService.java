package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * T030: Enforces booking readiness rules for OTA inbound events.
 * Requires:
 * - Mapping must be in BOOKING_ENABLED status.
 * - Active commercial terms/price snapshot must exist for the current instant.
 * - Authenticated provider outcome must establish full accepted customer payment (amount >= retail/target net and matching currency).
 * - Provider must not be booking on disabled or suspended mappings.
 */
@Service
@RequiredArgsConstructor
public class OtaBookingReadinessService {

    private final OtaChannelPriceSnapshotRepository priceSnapshotRepository;

    public record ReadinessValidationResult(
            boolean ready,
            String rejectionReason,
            OtaChannelPriceSnapshot priceSnapshot
    ) {
        public static ReadinessValidationResult approved(OtaChannelPriceSnapshot priceSnapshot) {
            return new ReadinessValidationResult(true, null, priceSnapshot);
        }

        public static ReadinessValidationResult rejected(String reason) {
            return new ReadinessValidationResult(false, reason, null);
        }
    }

    public ReadinessValidationResult evaluateBookingReadiness(
            OtaChannelMapping mapping,
            Long acceptedAmountMinor,
            String currencyCode,
            Instant transactionInstant
    ) {
        if (mapping == null) {
            return ReadinessValidationResult.rejected("MAPPING_NOT_FOUND");
        }

        if (mapping.getStatus() != OtaMappingStatus.BOOKING_ENABLED) {
            return ReadinessValidationResult.rejected("MAPPING_DISABLED");
        }

        Instant effectiveTime = transactionInstant != null ? transactionInstant : Instant.now();
        Optional<OtaChannelPriceSnapshot> priceOpt = priceSnapshotRepository
                .findFirstByMapping_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(mapping.getId(), effectiveTime);

        if (priceOpt.isEmpty()) {
            return ReadinessValidationResult.rejected("NO_ACTIVE_COMMERCIAL_TERMS");
        }

        OtaChannelPriceSnapshot price = priceOpt.get();

        if (currencyCode == null || !currencyCode.equalsIgnoreCase(price.getCurrencyCode())) {
            return ReadinessValidationResult.rejected("CURRENCY_MISMATCH");
        }

        if (acceptedAmountMinor == null || acceptedAmountMinor <= 0) {
            return ReadinessValidationResult.rejected("MISSING_PAYMENT_EVIDENCE");
        }

        if (acceptedAmountMinor < price.getRetailAmountMinor()) {
            return ReadinessValidationResult.rejected("PARTIAL_PAYMENT_REJECTED");
        }

        return ReadinessValidationResult.approved(price);
    }
}
