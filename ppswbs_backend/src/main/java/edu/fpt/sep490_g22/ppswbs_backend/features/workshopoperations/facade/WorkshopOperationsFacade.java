package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public interface WorkshopOperationsFacade {

    /** Returns null when the UUID session reference is unknown; numeric Operations IDs never cross this boundary. */
    SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query);

    PublishedPackageSelectionSnapshot getPublishedPackageSelection(PublishedPackageSelectionQuery query);

    /** Returns null unless this UUID add-on is active for the published package and active location. */
    WorkshopAddOnPriceSnapshot getWorkshopAddOnPrice(WorkshopAddOnPriceQuery query);

    CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command);

    CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command);

    OtaBookingCapacityOutcome acceptOtaBooking(AcceptOtaBookingCommand command);

    CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command);

    DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query);

    SettlementReadiness getSettlementReadiness(BookingSettlementReadinessQuery query);

    /** Authoritative, PII-free session and attendance facts used by cancellation policy. */
    BookingCancellationEligibilitySnapshot getBookingCancellationEligibility(BookingCancellationEligibilityQuery query);

    RequiredSessionReplacementOutcome replaceRequiredSessions(ReplaceRequiredSessionsCommand command);

    RefundConsequenceOutcome applyVerifiedRefundConsequence(ApplyVerifiedRefundConsequenceCommand command);

    CancellationCompletionOutcome completeCancellationHandoff(CompleteCancellationHandoffCommand command);
}
