package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.util.List;

/**
 * Cart confirmation held nothing (003.1 FR-031). {@code failingLines} carries the zero-based cart positions
 * that could not be served, so the cart stays editable and can show exactly which lines to fix.
 */
public class CartHoldRejectedException extends RuntimeException {

    private final String code;
    private final List<Integer> failingLines;

    public CartHoldRejectedException(String code, String message, List<Integer> failingLines) {
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
