package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingTicket;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingLookupResult {
    private WorkshopBooking booking;
    private BookingTicket ticket;
    private Instant holdExpiresAt;
    private boolean paymentResumeAllowed;
}
