package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import java.util.Arrays;

/**
 * FR-004 / D4 direct-booking open-folio lifecycle vocabulary for {@link SettlementFinalBill}.
 * Valid states are OPEN, FINALIZED, and CLOSED.
 */
public enum SettlementFinalBillLifecycleStatus {
    OPEN,
    FINALIZED,
    CLOSED;

    public static boolean isKnown(String value) {
        return value != null && Arrays.stream(values()).anyMatch(s -> s.name().equals(value));
    }
}

