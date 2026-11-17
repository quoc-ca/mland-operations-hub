package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShipmentStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Staff/Manager list projection for the FE-08 mock-shipment slice. */
public record StaffRetailShipmentSummary(
    String id,
    String branchOrderId,
    String branchOrderIdentifier,
    Long locationId,
    String mockTrackingNumber,
    RetailShipmentStatus status,
    int version,
    String recipientName,
    String provinceName,
    String districtName,
    String wardName,
    Instant createdAt,
    Instant updatedAt,
    String orderCode,
    String payableAmount,
    int itemQuantity
) {
    /** Summary without the order display facts (code, amount, item count). */
    public StaffRetailShipmentSummary(String id, String branchOrderId, String branchOrderIdentifier, Long locationId,
                                      String mockTrackingNumber, RetailShipmentStatus status, int version,
                                      String recipientName, String provinceName, String districtName, String wardName,
                                      Instant createdAt, Instant updatedAt) {
        this(id, branchOrderId, branchOrderIdentifier, locationId, mockTrackingNumber, status, version, recipientName,
            provinceName, districtName, wardName, createdAt, updatedAt,
            edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderCodes.of(branchOrderId, null),
            null, 0);
    }
    /** Bound query parameters for the staff list endpoint. */
    public record ListQuery(
        @Min(0) int page,
        @Min(1) @Max(50) int size,
        RetailShipmentStatus status,
        Long locationId
    ) {
        public ListQuery(int page, int size, RetailShipmentStatus status, Long locationId) {
            this.page = Math.max(page, 0);
            this.size = size < 1 ? 20 : Math.min(size, 50);
            this.status = status;
            this.locationId = locationId;
        }
    }

    /** Page envelope for the staff list endpoint. */
    public record Page(
        List<StaffRetailShipmentSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) { }

    /** Optional note for a Staff-recorded transition. */
    public record TransitionRequest(
        @NotNull RetailShipmentStatus nextStatus,
        @NotNull Integer expectedVersion,
        @Size(max = 512) String note
    ) { }
}
