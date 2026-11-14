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

    /** The one Loyalty context of a booking group, with its ticket identities (003.3 FR-030). */
    BookingGroupLoyaltyOutcomeSource getGroupLoyaltyOutcomeSource(String groupReference);

    /**
     * Resolves a 003.2 ticket reference ({@code attendance_tickets.reference}) to the ticket booking that holds it,
     * so 003.3 can find the ticket folio keyed by ticket code (decision 2026-11-13). Empty when unknown.
     */
    java.util.Optional<TicketBookingRef> findTicketBookingByOperationsReference(String ticketReference);

    /** Returns only an authorization decision; contact proof is neither returned nor logged. */
    CustomerBookingAccessDecision verifyCustomerBookingAccess(CustomerBookingAccessQuery query);

    /**
     * Record final-balance resolution from 003.3.
     */
    FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command);

    /**
     * 003.3 concludes that one ticket's folio is settled (FINALIZED with a zero balance): the ticket becomes
     * {@code FULLY_PAID}. MANDATORY, inside the caller's finalization transaction. Idempotent: a ticket already
     * {@code FULLY_PAID} is accepted without a second audit. {@code BOOKING_NOT_FOUND} for an unknown ticket;
     * {@code INVALID_STATE} for a ticket that is not confirmed or completed, or is in a refund state.
     */
    FinalBalanceResolutionOutcome recordTicketFinalBalanceResolved(RecordTicketFinalBalanceResolvedCommand command);

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

    /** The PII-free facts of one late-payment review, so 003.3 can recheck a refund amount (FR-040). */
    java.util.Optional<LatePaymentReviewSnapshot> findLatePaymentReview(String reviewReference);

    /**
     * Read-only folio context of one ticket inside a booking group (003.1 T107 for 003.3 T106).
     * Empty when unknown.
     */
    java.util.Optional<TicketFolioContext> getTicketFolioContext(String ticketCode);

    /**
     * Verifies and consumes buyer proof inside the caller's transaction (003.1 T107 for 003.3 T099, T100, T101).
     * Must be called within an active transaction (MANDATORY).
     */
    ConsumeBuyerProofResult consumeBuyerProof(ConsumeBuyerProofCommand command);
}
