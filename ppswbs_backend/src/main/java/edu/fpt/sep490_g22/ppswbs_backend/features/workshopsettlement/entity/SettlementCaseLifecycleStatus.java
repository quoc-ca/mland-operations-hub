package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity;

import java.util.Arrays;

/**
 * FR-022b1 lifecycle vocabulary for {@link SettlementCase} (customer cancellation,
 * required-session change and Mland-caused remedy). Only a case that awaits
 * verified refund evidence may be {@link #PENDING_PROVIDER}; there is no
 * deferred-payment, {@code EXPIRED} or {@code CANCELED} case state.
 */
public enum SettlementCaseLifecycleStatus {
    OPEN,
    /** Awaits verified refund evidence (decided 2026-11-14, replaces "awaiting gateway"). */
    PENDING_PROVIDER,
    RESOLVED,
    REJECTED,
    FAILED;

    public static boolean isKnown(String value) {
        return value != null && Arrays.stream(values()).anyMatch(s -> s.name().equals(value));
    }
}
