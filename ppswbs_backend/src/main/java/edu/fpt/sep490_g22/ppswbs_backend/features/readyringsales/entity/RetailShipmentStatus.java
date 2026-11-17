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
    PREPARING,
    HANDED_TO_CARRIER,
    IN_TRANSIT,
    DELIVERED;

    private static final Map<RetailShipmentStatus, Set<RetailShipmentStatus>> ALLOWED_NEXT = Map.of(
        PREPARING, Set.of(HANDED_TO_CARRIER),
        HANDED_TO_CARRIER, Set.of(IN_TRANSIT),
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
