package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

public record OtaInboundEventOutcome(Status status, String outcomeReference) {

    public enum Status {
        ACCEPTED,
        REJECTED,
        RECONCILIATION_REQUIRED
    }
}
