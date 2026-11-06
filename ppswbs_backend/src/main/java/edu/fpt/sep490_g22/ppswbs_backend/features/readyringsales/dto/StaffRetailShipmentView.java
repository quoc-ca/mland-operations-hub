package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Staff/Manager view of a fictional GHTK shipment (FE-08 mock-shipment slice).
 *
 * <p>Carries only display-safe facts. It never exposes the JPA entity, the grouped-checkout
 * internal FK, or any recipient PII beyond what Staff already need to hand a parcel to GHTK.
 * Recipient fields are included because Staff physically pack and hand over the parcel and must
 * be able to confirm the address on the box matches the order; they are not returned to Members.
 */
public record StaffRetailShipmentView(
    String id,
    String branchOrderId,
    String branchOrderIdentifier,
    Long locationId,
    String fulfilmentMethod,
    String mockTrackingNumber,
    RetailShipmentStatus status,
    int version,
    String recipientName,
    String recipientPhone,
    String recipientAddress,
    String provinceName,
    String districtName,
    String wardName,
    Instant createdAt,
    Instant updatedAt,
    List<HistoryEntry> history
) {
    public record HistoryEntry(
        RetailShipmentStatus status,
        Long actorId,
        String actorLabel,
        String note,
        Instant createdAt
    ) { }
}
