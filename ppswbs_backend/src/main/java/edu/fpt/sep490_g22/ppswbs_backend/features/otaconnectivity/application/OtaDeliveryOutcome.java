package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

/** Provider-neutral result after one outbound delivery attempt. */
public enum OtaDeliveryOutcome {
    DELIVERED,
    RETRYABLE,
    EXHAUSTED
}
