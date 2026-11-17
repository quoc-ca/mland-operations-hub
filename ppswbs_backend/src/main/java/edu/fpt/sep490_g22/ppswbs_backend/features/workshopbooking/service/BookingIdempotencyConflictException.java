package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class BookingIdempotencyConflictException extends RuntimeException {

    public BookingIdempotencyConflictException(String message) {
        super(message);
    }
}
