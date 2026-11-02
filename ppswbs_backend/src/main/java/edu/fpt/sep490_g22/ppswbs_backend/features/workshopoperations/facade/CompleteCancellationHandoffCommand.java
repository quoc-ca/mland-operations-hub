package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.util.Objects;
import java.util.UUID;

public record CompleteCancellationHandoffCommand(
    UUID sessionReference,
    String cancellationCaseReference,
    String operationReference,
    String correlationReference
) {
    public CompleteCancellationHandoffCommand {
        Objects.requireNonNull(sessionReference, "sessionReference must not be null");
        Objects.requireNonNull(cancellationCaseReference, "cancellationCaseReference must not be null");
        Objects.requireNonNull(operationReference, "operationReference must not be null");
        Objects.requireNonNull(correlationReference, "correlationReference must not be null");
    }
}
