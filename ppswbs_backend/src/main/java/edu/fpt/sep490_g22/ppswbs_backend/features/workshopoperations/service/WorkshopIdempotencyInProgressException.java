package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;
public class WorkshopIdempotencyInProgressException extends RuntimeException {
    public WorkshopIdempotencyInProgressException(String message) { super(message); }
}
