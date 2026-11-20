package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a fictional GHTK shipment (FE-08 "mock shipment" slice). Only the GHTK fee quote and the
 * mock tracking number come from (or resemble) the real GHTK API; there is no real shipment creation,
 * webhook, or tracking behind this. Staff drives every transition by hand, including the terminal
 * {@link #DELIVERED} state, because no real carrier callback exists in this slice.
 */
public enum RetailShipmentStatus {
    /** Paid; the branch is packing the order (Staff "Đang chuẩn bị hàng", GHTK "Đã tiếp nhận"). */
    PREPARING,
    /** Packed and waiting for the courier (Session 2026-11-18: Staff "Chờ ĐVVC lấy hàng", GHTK "Đang lấy hàng"). */
    READY_FOR_CARRIER,
    /** The courier took it (Staff "Đã bàn giao cho ĐVVC", GHTK and Member "Đang giao hàng"). */
    HANDED_TO_CARRIER,
    /** Kept for shipments moved before Session 2026-11-18; new shipments go from HANDED_TO_CARRIER to DELIVERED. */
    IN_TRANSIT,
    DELIVERED;

    private static final Map<RetailShipmentStatus, Set<RetailShipmentStatus>> ALLOWED_NEXT = Map.of(
        PREPARING, Set.of(READY_FOR_CARRIER),
        READY_FOR_CARRIER, Set.of(HANDED_TO_CARRIER),
        HANDED_TO_CARRIER, Set.of(DELIVERED, IN_TRANSIT),
        IN_TRANSIT, Set.of(DELIVERED),
        DELIVERED, Set.of()
    );

    public boolean canAdvanceTo(RetailShipmentStatus next) {
        return ALLOWED_NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == DELIVERED;
    }
}
