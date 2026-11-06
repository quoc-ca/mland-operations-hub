package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * Public synchronous facade for 003.1 Workshop Booking.
 * Exposes only approved cross-feature operations specified in workshop-interfeature-facades.md.
 * Does not expose JPA entities, DAO, contact PII except in the dedicated notification payload,
 * raw Magic Link except in that payload, or VNPay payloads.
 */
public interface WorkshopBookingFacade {

    /**
     * Read confirmed booking financial projection required by 003.3.
     */
    ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query);

    /** Reads only verified deposit payment facts, with no PII or browser/provider payload. */
    VerifiedBookingDepositPaymentsProjection getVerifiedDepositPayments(VerifiedBookingDepositPaymentsQuery query);

    /** Returns the minimal accepted Member source context required for terminal Loyalty outcomes; null for Guest or legacy bookings. */
    WorkshopBookingLoyaltyOutcomeSource getLoyaltyOutcomeSource(WorkshopBookingLoyaltyOutcomeSourceQuery query);

    /** Returns only an authorization decision; contact proof is neither returned nor logged. */
    CustomerBookingAccessDecision verifyCustomerBookingAccess(CustomerBookingAccessQuery query);

    /**
     * Record final-balance resolution from 003.3.
     */
    FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command);

    /** Records one terminal service outcome concluded by Settlement. */
    BookingServiceOutcomeResult recordBookingServiceOutcome(RecordBookingServiceOutcomeCommand command);

    /** Records the monotonic Mland-remedy refund status concluded by Settlement. */
    BookingRefundStatusResult recordBookingRefundStatus(RecordBookingRefundStatusCommand command);

    /**
     * Apply verified deposit evidence dispatched by paymentintegration.
     */
    DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command);

    /** Persist redacted, verified deposit evidence that arrived after booking capacity was released. */
    DepositPaymentEvidenceOutcome recordLateDepositEvidence(RecordLateDepositEvidenceCommand command);

    /** Cancel a confirmed direct booking after Operations has cancelled its sessions/tickets. */
    BookingCancellationOutcome cancelConfirmedBooking(CancelConfirmedBookingCommand command);

    /**
     * Request delivery of existing ticket set (called by 003.2 resend flow).
     */
    TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command);

    /**
     * Read booking eligibility snapshot for design analysis (called by Feature 004).
     */
    java.util.Optional<BookingEligibilitySnapshot> getBookingEligibilityForDesign(String bookingCode, Long memberAccountId);

    OtaBookingOutcome createOrReturnOtaBooking(OtaBookingCommand command);

    /**
     * The contact email stored on the booking, for Settlement's customer notifications; null when the booking is
     * unknown or has no email. The value is for the caller's transaction only: never an event, audit or log field.
     */
    String getSettlementNotificationRecipient(SettlementNotificationRecipientQuery query);
}
