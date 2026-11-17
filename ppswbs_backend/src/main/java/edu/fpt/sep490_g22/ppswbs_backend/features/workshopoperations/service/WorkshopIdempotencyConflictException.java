package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;
public class WorkshopIdempotencyConflictException extends RuntimeException {
    public WorkshopIdempotencyConflictException(String message) { super(message); }
}
