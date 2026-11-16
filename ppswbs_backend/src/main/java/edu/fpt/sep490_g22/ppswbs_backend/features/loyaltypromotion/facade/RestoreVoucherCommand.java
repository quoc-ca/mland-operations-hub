package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * 003.3 asks the voucher and points module to give back what one cancelled workshop ticket held or used
 * (workshop-interfeature-facades.md, "Voucher and points restoration on ticket cancellation"). Opaque references only.
 */
public record RestoreVoucherCommand(String groupCode, String ticketCode, TicketCancellationKind cancellationKind) {

    public RestoreVoucherCommand {
        if (groupCode == null || groupCode.isBlank()) {
            throw LoyaltyException.invalidField("groupCode", "The group code is required.");
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            throw LoyaltyException.invalidField("ticketCode", "The ticket code is required.");
        }
        if (cancellationKind == null) {
            throw LoyaltyException.invalidField("cancellationKind", "The cancellation kind is required.");
        }
        groupCode = groupCode.trim();
        ticketCode = ticketCode.trim();
    }
}
