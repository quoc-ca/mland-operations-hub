package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A held booking group awaiting its single booking-time payment (003.1 FR-030..FR-032, FR-036).
 * {@code holdExpiresAt} is 12 minutes after the cart was held; {@code paymentDeadlineAt} is 2 minutes
 * earlier and is the only deadline a customer is shown (FR-032).
 */
public record BookingGroupResponse(
        String groupCode,
        String groupStatus,
        String paymentMode,
        long groupCollectionVnd,
        Instant holdExpiresAt,
        Instant paymentDeadlineAt,
        List<GroupLine> lines,
        List<GroupTicket> tickets) {

    public record GroupLine(
            int lineIndex,
            Long packageId,
            String packageName,
            long pricePerParticipantVnd,
            int ticketCount,
            List<UUID> sessionReferences) {
    }

    public record GroupTicket(
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
            long remainingVnd) {
    }
}
