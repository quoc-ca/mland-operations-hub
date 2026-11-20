package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * What the GHTK partner (role CARRIER, simulated courier portal, Session 2026-11-20) sees of a shipment: where to
 * collect, whom to deliver to, the fee, and when each step happened. No product prices, vouchers, points or Member
 * account data.
 */
public final class CarrierShipmentDtos {
    private CarrierShipmentDtos() { }

    /**
     * @param status      PREPARING (GHTK "Đã tiếp nhận"), READY_FOR_CARRIER ("Đang lấy hàng"), HANDED_TO_CARRIER or
     *                    IN_TRANSIT ("Đang giao hàng"), DELIVERED ("Đã giao hàng")
     * @param readyAt     when the branch finished packing
     * @param pickedUpAt  when the courier collected it
     * @param deliveredAt when it was delivered
     */
    public record Shipment(String id, String trackingNumber, String status, int version, String orderCode,
                           Long pickupLocationId, String recipientName, String recipientPhone, String recipientAddress,
                           String wardName, String districtName, String provinceName, int itemQuantity,
                           String shippingCharge, Instant createdAt, Instant readyAt, Instant pickedUpAt,
                           Instant deliveredAt, Instant updatedAt) { }

    public record Page(List<Shipment> items, int page, int size, long totalElements, int totalPages) { }

    /** {@code action}: PICKED_UP ("Đã lấy hàng") or DELIVERED ("Giao thành công"). */
    public record TransitionRequest(@NotBlank String action, @NotNull Integer expectedVersion) { }

    public record TransitionResult(String id, String status, int version, Instant updatedAt) { }
}
