package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import java.time.Instant;

/** Normal check-in attempted before the session's checkInOpensAt (409 CHECK_IN_NOT_OPEN, T126). */
public class WorkshopCheckInNotOpenException extends RuntimeException {

    private final Instant checkInOpensAt;

    public WorkshopCheckInNotOpenException(String message, Instant checkInOpensAt) {
        super(message);
        this.checkInOpensAt = checkInOpensAt;
    }

    public Instant getCheckInOpensAt() {
        return checkInOpensAt;
    }
}
