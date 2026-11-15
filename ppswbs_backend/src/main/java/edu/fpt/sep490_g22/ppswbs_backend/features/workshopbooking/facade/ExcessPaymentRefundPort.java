package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.LatePaymentRefundPort.LatePaymentRefundOutcome;

/**
 * 003.1 → 003.3 port for the 100% bank refund of excess or duplicate money on a {@code CONFIRMED} group
 * (FR-013a, decided 2026-11-15). Declared by Booking and implemented by Workshop Settlement, so Booking never
 * depends on Settlement. Reason {@code EXCESS_OR_DUPLICATE_MONEY}, source reference = {@code evidenceReference},
 * idempotent on {@code (reason, source_reference)}; runs with propagation MANDATORY inside the evidence
 * transaction, so the money is never recorded without a durable bank-refund request. Never calls VNPay Refund.
 */
public interface ExcessPaymentRefundPort {

    LatePaymentRefundOutcome openExcessPaymentRefund(OpenExcessPaymentRefundCommand command);

    record OpenExcessPaymentRefundCommand(
            String groupCode,
            String evidenceReference,
            long amountVnd,
            String operationReference,
            String correlationReference
    ) {
    }
}
