package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

public record DepositPaymentEvidenceOutcome(
    BookingOutcomeStatus status,
    BookingRejectionReason reason
) {
    public static DepositPaymentEvidenceOutcome accepted() {
        return new DepositPaymentEvidenceOutcome(BookingOutcomeStatus.ACCEPTED, null);
    }

    public static DepositPaymentEvidenceOutcome rejected(BookingRejectionReason reason) {
        return new DepositPaymentEvidenceOutcome(BookingOutcomeStatus.REJECTED, reason);
    }
}
