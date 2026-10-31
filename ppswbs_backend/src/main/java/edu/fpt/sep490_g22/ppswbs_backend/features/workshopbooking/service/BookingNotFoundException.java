package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String message) {
        super(message);
    }
}
