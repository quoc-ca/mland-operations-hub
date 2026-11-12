package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public interface WorkshopOperationsFacade {

    /** Returns null when the UUID session reference is unknown; numeric Operations IDs never cross this boundary. */
    SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query);

    PublishedPackageSelectionSnapshot getPublishedPackageSelection(PublishedPackageSelectionQuery query);

    /** Returns null unless this UUID add-on is active for the published package and active location. */
    WorkshopAddOnPriceSnapshot getWorkshopAddOnPrice(WorkshopAddOnPriceQuery query);

    /** The fixed-price add-ons currently sellable for a package at a location (empty when none or not sellable). */
    java.util.List<WorkshopAddOnPriceSnapshot> listWorkshopAddOns(WorkshopAddOnListQuery query);

    CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command);

    /** Reserves every line of a booking group all or none, one seat per ticket per session (003.2 FR-030). */
    GroupCapacityOutcome reserveGroupCapacity(ReserveGroupCapacityCommand command);

    CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command);

    OtaBookingCapacityOutcome acceptOtaBooking(AcceptOtaBookingCommand command);

    CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command);

    DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query);

    SettlementReadiness getSettlementReadiness(BookingSettlementReadinessQuery query);

    /** Attendance of the booking for display to the customer; reads only, takes no lock. */
    BookingAttendanceSummary getBookingAttendanceSummary(BookingSettlementReadinessQuery query);

    /** True once at least one of the booking's active sessions is running or its attendance is finalized (the customer is at the workshop). */
    boolean hasBookingServiceStarted(BookingSettlementReadinessQuery query);

    /** True when the Staff member is actively assigned to at least one session the booking holds a valid ticket for. */
    boolean isStaffAssignedToBooking(StaffBookingAssignmentQuery query);

    BookingAttendanceOutcome getBookingAttendanceOutcome(BookingAttendanceOutcomeQuery query);

    /** The terminal attendance outcome of one ticket of a booking group (003.3 FR-030). */
    BookingAttendanceOutcome getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery query);

    /** Authoritative, PII-free session and attendance facts used by cancellation policy. */
    BookingCancellationEligibilitySnapshot getBookingCancellationEligibility(BookingCancellationEligibilityQuery query);

    RequiredSessionReplacementOutcome replaceRequiredSessions(ReplaceRequiredSessionsCommand command);

    RefundConsequenceOutcome applyVerifiedRefundConsequence(ApplyVerifiedRefundConsequenceCommand command);

    CancellationCompletionOutcome completeCancellationHandoff(CompleteCancellationHandoffCommand command);

    /** Cancels every active session and ticket belonging to one booking atomically. */
    CancellationCompletionOutcome cancelBooking(CancelBookingCommand command);
}
