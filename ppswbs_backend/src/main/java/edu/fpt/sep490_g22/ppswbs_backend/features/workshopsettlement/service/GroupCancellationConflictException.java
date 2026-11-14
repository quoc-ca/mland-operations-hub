package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when one or more tickets in a group cancellation cannot be cancelled (FR-041).
 * Carries the list of failing ticket codes for 409 GroupCancellationProblem.
 */
@Getter
public class GroupCancellationConflictException extends RuntimeException {

    private final String code;
    private final List<String> failingTicketCodes;

    public GroupCancellationConflictException(String code, String message, List<String> failingTicketCodes) {
        super(message);
        this.code = code;
        this.failingTicketCodes = failingTicketCodes == null ? List.of() : List.copyOf(failingTicketCodes);
    }

    public GroupCancellationConflictException(List<String> failingTicketCodes) {
        this("GROUP_CANCELLATION_CONFLICT", "One or more tickets cannot be cancelled", failingTicketCodes);
    }
}

