package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public interface WorkshopOperationsFacade {

    SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query);

    CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command);

    CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command);

    CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command);

    DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query);

    SettlementReadiness getSettlementReadiness(BookingSettlementReadinessQuery query);

    RequiredSessionReplacementOutcome replaceRequiredSessions(ReplaceRequiredSessionsCommand command);

    RefundConsequenceOutcome applyVerifiedRefundConsequence(ApplyVerifiedRefundConsequenceCommand command);

    CancellationCompletionOutcome completeCancellationHandoff(CompleteCancellationHandoffCommand command);
}
