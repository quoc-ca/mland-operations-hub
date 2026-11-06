package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.time.Instant;

/** Current component and earn result of one accepted transaction, for source reconciliation only. */
public record BenefitResult(SourceKind sourceKind, String sourceRef, ComponentState pointsState,
                            ComponentState voucherState, EarnState earnState, Instant settledAt) {
}
