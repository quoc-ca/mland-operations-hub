package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

/** Durable state of an accepted outbound inventory change. */
public enum OtaOutboundStatus {
    QUEUED,
    DELIVERED,
    RETRYABLE,
    EXHAUSTED
}
