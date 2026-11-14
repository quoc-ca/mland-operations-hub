package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Settlement's only Operations dependency; it exposes typed outcomes, never Operations persistence. */
@Component
@RequiredArgsConstructor
public class WorkshopOperationsSettlementAdapter {
    private final WorkshopOperationsFacade operations;

    /** Whether the Staff member is actively assigned to a session this booking holds a valid ticket for. */
    public boolean staffAssignedToBooking(Long staffAccountId, String bookingReference) {
        return operations.isStaffAssignedToBooking(new StaffBookingAssignmentQuery(staffAccountId, bookingReference));
    }

    public boolean serviceStarted(String bookingReference) {
        return operations.hasBookingServiceStarted(new BookingSettlementReadinessQuery(bookingReference));
    }

    /** The terminal attendance outcome of one ticket of a booking group (003.3 FR-030, 003.2 T092). */
    public TicketAttendanceOutcome ticketAttendanceOutcome(String ticketReference) {
        return operations.getTicketAttendanceOutcome(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketAttendanceOutcomeQuery(
                        ticketReference));
    }
    public java.util.List<WorkshopAddOnPriceSnapshot> listAddOns(String packageReference, Long locationReference) {
        return operations.listWorkshopAddOns(new WorkshopAddOnListQuery(packageReference, locationReference));
    }

    public WorkshopAddOnPriceSnapshot addOnPrice(String packageReference, Long locationReference, String addOnReference) {
        return operations.getWorkshopAddOnPrice(new WorkshopAddOnPriceQuery(packageReference, locationReference, addOnReference));
    }

    public RefundConsequenceOutcome applyRefund(String bookingReference, String verifiedRefundReference,
            String operationReference, String correlationReference) {
        return operations.applyVerifiedRefundConsequence(new ApplyVerifiedRefundConsequenceCommand(
                bookingReference, verifiedRefundReference, operationReference, correlationReference));
    }

    public CancellationCompletionOutcome completeCancellation(String sessionReference,
            String cancellationCaseReference, String operationReference, String correlationReference) {
        return operations.completeCancellationHandoff(new CompleteCancellationHandoffCommand(
                java.util.UUID.fromString(sessionReference), cancellationCaseReference, operationReference, correlationReference));
    }

    public TicketCancellationOutcome cancelTicket(java.util.UUID ticketReference,
            String cancellationCaseReference, String operationReference, String correlationReference) {
        return operations.cancelTicket(new CancelTicketCommand(
                ticketReference, cancellationCaseReference, operationReference, correlationReference));
    }

    public TicketSessionReplacementOutcome replaceTicketSessions(ReplaceTicketSessionsCommand command) {
        return operations.replaceTicketSessions(command);
    }

    /** The active tickets of one 003.2 booking line with their sessions (T112 maps a cancelled session to tickets). */
    public DeliverySafeTicketSet ticketProjection(String bookingReference) {
        return operations.getBookingTicketProjection(new BookingTicketProjectionQuery(bookingReference));
    }

    /** Every valid equivalent replacement for one cancelled session of a ticket, read only (T112, FR-043). */
    public EquivalentReplacementSessions equivalentReplacementSessions(java.util.UUID ticketReference,
            java.util.UUID cancelledSessionReference) {
        return operations.findEquivalentReplacementSessions(
                new FindEquivalentReplacementSessionsQuery(ticketReference, cancelledSessionReference));
    }

    public SaleEligibilitySnapshot saleEligibility(java.util.UUID sessionReference) {
        return operations.getSaleEligibility(new SaleEligibilityQuery(sessionReference));
    }
}
