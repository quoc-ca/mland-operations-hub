package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.util.List;

public class BookingValidationException extends RuntimeException {

    private final String code;
    private final List<Integer> failingLines;

    public BookingValidationException(String message) {
        this(null, message, List.of());
    }

    /** A refusal that names the zero-based cart positions at fault (003.1 FR-031, T121). */
    public BookingValidationException(String code, String message, List<Integer> failingLines) {
        super(message);
        this.code = code;
        this.failingLines = failingLines == null ? List.of() : List.copyOf(failingLines);
    }

    public String getCode() {
        return code;
    }

    public List<Integer> getFailingLines() {
        return failingLines;
    }
}
