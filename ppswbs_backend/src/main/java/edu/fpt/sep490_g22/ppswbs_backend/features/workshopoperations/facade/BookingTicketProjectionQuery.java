package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;

public record BookingTicketProjectionQuery(String bookingReference) {
    public BookingTicketProjectionQuery {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
    }
}
