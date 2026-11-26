package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * 003.3 asks the voucher and points module to give back what one cancelled workshop ticket held or used
 * (workshop-interfeature-facades.md, "Voucher and points restoration on ticket cancellation"). Opaque references only.
 * {@code partRef} is the ticket's reference in the accepted benefit ({@code L{line}-T{ordinal}}); it differs from
 * {@code ticketCode}, which only identifies the call.
 */
public record RestoreVoucherCommand(String groupCode, String ticketCode, String partRef,
                                    TicketCancellationKind cancellationKind) {

    public RestoreVoucherCommand {
        if (groupCode == null || groupCode.isBlank()) {
            throw LoyaltyException.invalidField("groupCode", "The group code is required.");
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            throw LoyaltyException.invalidField("ticketCode", "The ticket code is required.");
        }
        if (partRef == null || partRef.isBlank()) {
            throw LoyaltyException.invalidField("partRef", "The ticket part reference is required.");
        }
        if (cancellationKind == null) {
            throw LoyaltyException.invalidField("cancellationKind", "The cancellation kind is required.");
        }
        groupCode = groupCode.trim();
        ticketCode = ticketCode.trim();
        partRef = partRef.trim();
    }
}
