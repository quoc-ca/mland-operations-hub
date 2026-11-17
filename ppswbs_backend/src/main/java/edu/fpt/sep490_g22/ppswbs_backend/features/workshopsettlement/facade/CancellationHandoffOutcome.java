package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade;

/** Typed, persistence-safe outcome exposed to Workshop Operations. */
public record CancellationHandoffOutcome(
        Status status,
        String cancellationCaseReference,
        String rejectionReason,
        String correlationReference
) {
    public enum Status { ACCEPTED, REJECTED }

    public static CancellationHandoffOutcome accepted(String caseReference, String correlationReference) {
        return new CancellationHandoffOutcome(Status.ACCEPTED, caseReference, null, correlationReference);
    }

    public static CancellationHandoffOutcome rejected(String reason, String correlationReference) {
        return new CancellationHandoffOutcome(Status.REJECTED, null, reason, correlationReference);
    }
}
