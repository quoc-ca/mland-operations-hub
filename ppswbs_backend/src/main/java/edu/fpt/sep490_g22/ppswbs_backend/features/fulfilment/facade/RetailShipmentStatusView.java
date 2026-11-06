package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.facade;

import java.time.Instant;

/**
 * Minimal normalized shipment state that may be projected to retail order
 * queries without exposing provider credentials or raw provider callbacks.
 */
public record RetailShipmentStatusView(
    String branchOrderId,
    String normalizedStatus,
    Instant lastUpdatedAt
) {
}
