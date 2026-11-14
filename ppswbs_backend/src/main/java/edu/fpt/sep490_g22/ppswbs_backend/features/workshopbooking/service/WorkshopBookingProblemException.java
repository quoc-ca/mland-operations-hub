package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import org.springframework.http.HttpStatus;

/** A typed, safe business refusal with an explicit HTTP status and contract error code. */
public class WorkshopBookingProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public WorkshopBookingProblemException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
