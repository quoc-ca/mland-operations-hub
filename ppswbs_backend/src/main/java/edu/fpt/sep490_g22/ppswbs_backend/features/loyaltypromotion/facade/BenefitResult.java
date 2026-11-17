package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;
import java.util.List;

/**
 * Current component and earn result of one accepted transaction, for source reconciliation only. {@code parts}
 * (Session 2026-11-12, D-011) are the current shares and states of each settlement part; empty without parts.
 */
public record BenefitResult(SourceKind sourceKind, String sourceRef, ComponentState pointsState,
                            ComponentState voucherState, EarnState earnState, Instant settledAt,
                            List<BenefitPartRef> parts) {

    public BenefitResult {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    /** Shape before settlement parts, kept so callers and their test doubles keep compiling. */
    public BenefitResult(SourceKind sourceKind, String sourceRef, ComponentState pointsState,
                         ComponentState voucherState, EarnState earnState, Instant settledAt) {
        this(sourceKind, sourceRef, pointsState, voucherState, earnState, settledAt, List.of());
    }
}
