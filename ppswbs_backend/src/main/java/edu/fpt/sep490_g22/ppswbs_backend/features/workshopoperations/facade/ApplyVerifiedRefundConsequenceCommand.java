package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;

public record ApplyVerifiedRefundConsequenceCommand(
    String bookingReference,
    String verifiedRefundReference,
    String operationReference,
    String correlationReference
) {
    public ApplyVerifiedRefundConsequenceCommand {
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(verifiedRefundReference, "verifiedRefundReference must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
    }
}
