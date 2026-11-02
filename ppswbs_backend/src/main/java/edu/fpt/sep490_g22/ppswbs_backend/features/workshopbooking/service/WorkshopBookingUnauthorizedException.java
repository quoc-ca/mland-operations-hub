package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class WorkshopBookingUnauthorizedException extends RuntimeException {
    public WorkshopBookingUnauthorizedException(String message) {
        super(message);
    }

    public WorkshopBookingUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
