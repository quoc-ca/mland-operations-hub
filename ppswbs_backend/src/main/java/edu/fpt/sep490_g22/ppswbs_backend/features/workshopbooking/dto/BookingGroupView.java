package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The buyer's read-only view of a booking group (003.1 FR-042, FR-049, decision 2026-11-13): every ticket with its
 * own status, price breakdown, schedule, masked recipient and attendance. {@code qrReference} is given to the buyer
 * only while the ticket is confirmed; {@code ticketReference} only to the owning Member (Feature 011 review matching).
 */
public record BookingGroupView(
        String groupCode,
        String groupStatus,
        String paymentMode,
        long groupCollectionVnd,
        Instant holdExpiresAt,
        Instant paymentDeadlineAt,
        Instant createdAt,
        Instant confirmedAt,
        List<BookingGroupResponse.GroupLine> lines,
        List<TicketView> tickets) {

    public record TicketView(
            String ticketCode,
            int lineIndex,
            int participantOrdinal,
            Long packageId,
            String packageName,
            String ticketStatus,
            String financialStatus,
            long finalPriceVnd,
            long collectedAtBookingVnd,
            String collectionKind,
            long remainingVnd,
            String recipientEmailMasked,
            Integer onlineRecipientChangesRemaining,
            String attendanceStatus,
            String qrReference,
            String ticketReference,
            List<SessionView> sessions) {
    }

    public record SessionView(
            UUID sessionReference,
            Long locationId,
            int ordinal,
            Instant scheduledStart,
            Instant scheduledEnd) {
    }
}
