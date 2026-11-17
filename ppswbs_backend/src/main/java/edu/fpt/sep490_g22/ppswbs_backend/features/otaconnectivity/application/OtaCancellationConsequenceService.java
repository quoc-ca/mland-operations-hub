package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ApplyVerifiedRefundConsequenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RefundConsequenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * T037: Integrates confirmed cancellation outcomes with Operations via WorkshopOperationsFacade.
 * Enforces:
 * - Revokes only applicable unused tickets and releases eligible future capacity exactly once.
 * - Idempotency: duplicate confirmation replays the prior result without duplicate capacity compensation.
 * - Stopped at facade boundary: never touches Operations entities or tables directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaCancellationConsequenceService {

    private final WorkshopOperationsFacade workshopOperationsFacade;

    public record CancellationConsequenceResult(
            boolean successful,
            String rejectionReason
    ) {
        public static CancellationConsequenceResult success() {
            return new CancellationConsequenceResult(true, null);
        }

        public static CancellationConsequenceResult rejected(String reason) {
            return new CancellationConsequenceResult(false, reason);
        }
    }

    @Transactional
    public CancellationConsequenceResult applyConfirmedConsequence(
            OtaChannelBooking booking,
            String refundEvidenceReference,
            String correlationReference
    ) {
        if (booking == null || booking.getMlandBookingReference() == null) {
            throw new IllegalArgumentException("Valid booking with Mland booking reference is required");
        }

        String verifiedRefundRef = refundEvidenceReference != null && !refundEvidenceReference.isBlank()
                ? refundEvidenceReference
                : "ota-ref-" + UUID.randomUUID();

        String operationRef = "ota-canc-op-" + UUID.randomUUID();
        String correlation = correlationReference != null && !correlationReference.isBlank()
                ? correlationReference
                : UUID.randomUUID().toString();

        ApplyVerifiedRefundConsequenceCommand command = new ApplyVerifiedRefundConsequenceCommand(
                booking.getMlandBookingReference(),
                verifiedRefundRef,
                operationRef,
                correlation
        );

        RefundConsequenceOutcome outcome = workshopOperationsFacade.applyVerifiedRefundConsequence(command);

        if (outcome.status() == OutcomeStatus.ACCEPTED) {
            log.info("Successfully applied cancellation consequence for booking {}", booking.getMlandBookingReference());
            return CancellationConsequenceResult.success();
        } else {
            String reason = outcome.reason() != null ? outcome.reason().name() : "OPERATIONS_REFUND_REJECTED";
            log.warn("Operations rejected cancellation consequence for booking {}: {}", booking.getMlandBookingReference(), reason);
            return CancellationConsequenceResult.rejected(reason);
        }
    }
}

