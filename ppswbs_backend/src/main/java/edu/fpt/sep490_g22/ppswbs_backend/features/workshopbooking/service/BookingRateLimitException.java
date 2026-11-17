package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class BookingRateLimitException extends RuntimeException {

    public BookingRateLimitException(String message) {
        super(message);
    }
}
