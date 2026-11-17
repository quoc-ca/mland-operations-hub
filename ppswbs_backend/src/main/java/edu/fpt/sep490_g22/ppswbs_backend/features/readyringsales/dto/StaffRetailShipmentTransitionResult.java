package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;

import java.time.Instant;

/** Result of a Staff-recorded shipment transition (FE-08 mock-shipment slice). */
public record StaffRetailShipmentTransitionResult(
    String id,
    RetailShipmentStatus status,
    int version,
    Instant updatedAt
) { }
