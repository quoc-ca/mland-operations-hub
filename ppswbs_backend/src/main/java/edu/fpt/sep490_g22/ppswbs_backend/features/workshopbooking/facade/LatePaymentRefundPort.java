package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * 003.1 → 003.3 port for the 100% bank refund of a late group payment (FR-040, decision 2026-11-13).
 * Declared by Booking and implemented by Workshop Settlement, so Booking never depends on Settlement.
 * Runs with propagation MANDATORY inside the Manager decision transaction: a failure rolls the decision back, and
 * the review becomes REFUND_REQUESTED only once a durable bank-refund request exists. Never calls VNPay Refund.
 */
public interface LatePaymentRefundPort {

    LatePaymentRefundOutcome openLatePaymentRefund(OpenLatePaymentRefundCommand command);

    record OpenLatePaymentRefundCommand(
            String groupCode,
            String reviewReference,
            String evidenceReference,
            long amountVnd,
            String operationReference,
            String correlationReference
    ) {
    }

    /** status ACCEPTED, PRIOR_EQUIVALENT (same review and amount already opened) or REJECTED with a safe reason. */
    record LatePaymentRefundOutcome(String status, String refundRequestReference, String refundStatus, String reason) {
        public static final String ACCEPTED = "ACCEPTED";
        public static final String PRIOR_EQUIVALENT = "PRIOR_EQUIVALENT";
        public static final String REJECTED = "REJECTED";

        public boolean opened() {
            return ACCEPTED.equals(status) || PRIOR_EQUIVALENT.equals(status);
        }
    }
}
