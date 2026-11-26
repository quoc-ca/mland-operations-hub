package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public interface WorkshopOperationsFacade {

    /** Returns null when the UUID session reference is unknown; numeric Operations IDs never cross this boundary. */
    SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query);

    PublishedPackageSelectionSnapshot getPublishedPackageSelection(PublishedPackageSelectionQuery query);

    /** Returns null unless this UUID add-on is active for the published package and active location. */
    WorkshopAddOnPriceSnapshot getWorkshopAddOnPrice(WorkshopAddOnPriceQuery query);

    /** The location a catalogue add-on belongs to (any state); null when unknown. Read only (003.3 T187, FR-060). */
    Long getWorkshopAddOnLocation(String addOnReference);

    /** The fixed-price add-ons currently sellable for a package at a location (empty when none or not sellable). */
    java.util.List<WorkshopAddOnPriceSnapshot> listWorkshopAddOns(WorkshopAddOnListQuery query);

    /** The locations where the Package is sellable now (003.2 FR-039); read only, used by 003.3 change options. */
    ActivePackageLocations activeLocationsForPackage(String packageReference);

    CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command);

    /** Reserves every line of a booking group all or none, one seat per ticket per session (003.2 FR-030). */
    GroupCapacityOutcome reserveGroupCapacity(ReserveGroupCapacityCommand command);

    /** Revokes a ticket's active QR and issues a new opaque QR for the same entitlements (003.2 FR-033). */
    TicketQrReissueOutcome reissueTicketQr(ReissueTicketQrCommand command);

    /** Releases one ticket's future seats and revokes its future entitlements (003.2 FR-031). */
    TicketCancellationOutcome cancelTicket(CancelTicketCommand command);

    /** Replaces the sessions of the selected tickets, all or none (003.2 FR-034, quota FR-035). */
    TicketSessionReplacementOutcome replaceTicketSessions(ReplaceTicketSessionsCommand command);

    /** The sessions a ticket may move to, by 003.2 own rules; read only and advisory (003.3 FR-044a, T176). */
    TicketChangeOptions listTicketChangeOptions(ListTicketChangeOptionsQuery query);

    /** Every valid equivalent replacement for one cancelled session of a ticket; read only, no seat held (003.3 T112, FR-043). */
    EquivalentReplacementSessions findEquivalentReplacementSessions(FindEquivalentReplacementSessionsQuery query);

    CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command);

    CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command);

    DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query);

    /**
     * The sessions a cancelled ticket held when it was cancelled: those it gave back plus those it still keeps
     * (already started or past). Empty when the ticket is unknown or not cancelled. Read only, no QR.
     */
    java.util.List<SessionEntitlementProjection> getCancelledTicketSessions(String ticketReference);

    /** True once at least one of the booking's active sessions is running or its attendance is finalized (the customer is at the workshop). */
    boolean hasBookingServiceStarted(BookingSettlementReadinessQuery query);

    /** True when the Staff member is actively assigned to at least one session the booking holds a valid ticket for. */
    boolean isStaffAssignedToBooking(StaffBookingAssignmentQuery query);

    /** The terminal attendance outcome of one ticket of a booking group (003.2 T092, 003.3 FR-032). */
    TicketAttendanceOutcome getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery query);

    CancellationCompletionOutcome completeCancellationHandoff(CompleteCancellationHandoffCommand command);

    java.util.Optional<WorkshopLocationSnapshot> findLocationByCode(String code);

    java.util.Optional<WorkshopLocationSnapshot> findLocationById(Long id);

    java.util.List<WorkshopLocationSnapshot> listActiveLocations();

}
