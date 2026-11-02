package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

public class WorkshopRateLimitException extends RuntimeException {
    public WorkshopRateLimitException(String message) { super(message); }
}
