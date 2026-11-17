package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

public class WorkshopBookingForbiddenException extends RuntimeException {
    public WorkshopBookingForbiddenException(String message) {
        super(message);
    }
}
