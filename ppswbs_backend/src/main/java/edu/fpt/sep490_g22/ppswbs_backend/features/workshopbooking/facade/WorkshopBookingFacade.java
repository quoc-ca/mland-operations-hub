package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * Public synchronous facade for 003.1 Workshop Booking.
 * Exposes only approved cross-feature operations specified in workshop-interfeature-facades.md.
 * Does not expose JPA entities, DAO, contact PII, raw Magic Link, or VNPay payloads.
 */
public interface WorkshopBookingFacade {

    /**
     * Read confirmed booking financial projection required by 003.3.
     */
    ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query);

    /**
     * Record final-balance resolution from 003.3.
     */
    FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command);

    /**
     * Apply verified deposit evidence dispatched by paymentintegration.
     */
    DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command);

    /**
     * Request delivery of existing ticket set (called by 003.2 resend flow).
     */
    TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command);

    /**
     * Read immutable notification delivery payload (called by Feature 007).
     */
    BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(BookingNotificationDeliveryPayloadQuery query);
}
