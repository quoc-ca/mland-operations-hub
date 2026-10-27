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
    private Integer participantCount;
    private Instant holdExpiresAt;
    private List<BookedSessionDto> sessions;
    private List<BookingTicketDto> tickets;
}
