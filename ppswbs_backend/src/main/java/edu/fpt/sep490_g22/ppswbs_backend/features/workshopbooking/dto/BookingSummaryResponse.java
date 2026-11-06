package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSummaryResponse {
    private String bookingCode;
    private String bookingStatus;
    private String paymentStatus;
    private Long packageId;
    private String packageName;
    private Integer participantCount;
    private Instant holdExpiresAt;
    private List<BookedSessionDto> sessions;
    private List<BookingTicketDto> tickets;
    /**
     * What the customer may be told about attendance once the sessions are finalized, or null before that:
     * ATTENDED, NO_SHOW_PENDING (recorded, still inside the 24-hour correction window) or NO_SHOW (final).
     * Display only: the booking status itself still changes only when the outcome is final.
     */
    private String attendanceStatus;
}
