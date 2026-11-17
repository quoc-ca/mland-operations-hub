package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

/** Safe receipt returned for both a first event and an idempotent replay. */
public record OtaInboundEventReceipt(
        Long eventId,
        boolean duplicate,
        String processingStatus,
        String outcomeReference) {
}
