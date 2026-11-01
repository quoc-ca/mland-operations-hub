package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Settlement's only Operations dependency; it exposes typed outcomes, never Operations persistence. */
@Component
@RequiredArgsConstructor
public class WorkshopOperationsSettlementAdapter {
    private final WorkshopOperationsFacade operations;

    public SettlementReadiness readiness(String bookingReference) {
        return operations.getSettlementReadiness(new BookingSettlementReadinessQuery(bookingReference));
    }

    public BookingCancellationEligibilitySnapshot cancellationEligibility(String bookingReference) {
        return operations.getBookingCancellationEligibility(new BookingCancellationEligibilityQuery(bookingReference));
    }

    public WorkshopAddOnPriceSnapshot addOnPrice(String packageReference, Long locationReference, String addOnReference) {
        return operations.getWorkshopAddOnPrice(new WorkshopAddOnPriceQuery(packageReference, locationReference, addOnReference));
    }

    public RequiredSessionReplacementOutcome replace(String bookingReference,
            java.util.List<String> targetSessionReferences, String operationReference, String correlationReference) {
        return operations.replaceRequiredSessions(new ReplaceRequiredSessionsCommand(
                bookingReference, targetSessionReferences.stream().map(java.util.UUID::fromString).toList(),
                operationReference, correlationReference));
    }

    public RefundConsequenceOutcome applyRefund(String bookingReference, String verifiedRefundReference,
            String operationReference, String correlationReference) {
        return operations.applyVerifiedRefundConsequence(new ApplyVerifiedRefundConsequenceCommand(
                bookingReference, verifiedRefundReference, operationReference, correlationReference));
    }

    public CancellationCompletionOutcome completeCancellation(String sessionReference,
            String cancellationCaseReference, String operationReference, String correlationReference) {
        return operations.completeCancellationHandoff(new CompleteCancellationHandoffCommand(
                java.util.UUID.fromString(sessionReference), cancellationCaseReference, operationReference, correlationReference));
    }

    public CancellationCompletionOutcome cancelBooking(String bookingReference,
            String cancellationCaseReference, String operationReference, String correlationReference) {
        return operations.cancelBooking(new CancelBookingCommand(
                bookingReference, cancellationCaseReference, operationReference, correlationReference));
    }
}
