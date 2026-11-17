package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventCommand;

/**
 * Result of translating an inbound provider cancellation/refund webhook payload.
 * Enforces fail-closed classification: any unmapped or unverified event routes
 * to an auditable reconciliation work item rather than mutating inventory.
 */
public sealed interface OtaCancellationMappingResult {

    record Mapped(OtaCancellationEventCommand command) implements OtaCancellationMappingResult {}

    record Unmapped(String eventType, String reason) implements OtaCancellationMappingResult {}

    record Rejected(String failureCategory, String reason) implements OtaCancellationMappingResult {}
}

