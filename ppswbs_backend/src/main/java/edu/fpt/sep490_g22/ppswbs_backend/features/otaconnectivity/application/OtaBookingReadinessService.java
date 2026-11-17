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
 * - Authenticated provider outcome must carry payment evidence (a positive collected amount); the amount and currency are
 *   the OTA's own and are not compared with Mland's retail price.
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

        // T112: terms whose window ended at or before the transaction are not active terms.
        if (priceOpt.isEmpty()
                || (priceOpt.get().getEffectiveTo() != null && !priceOpt.get().getEffectiveTo().isAfter(effectiveTime))) {
            return ReadinessValidationResult.rejected("NO_ACTIVE_COMMERCIAL_TERMS");
        }

        // T113: the OTA collects its own price (platform fee, other currency); Mland does not compare it with its
        // retail price or currency. Only the absence of payment evidence blocks the booking.
        if (acceptedAmountMinor == null || acceptedAmountMinor <= 0) {
            return ReadinessValidationResult.rejected("MISSING_PAYMENT_EVIDENCE");
        }

        return ReadinessValidationResult.approved(priceOpt.get());
    }
}
