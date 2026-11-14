package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
