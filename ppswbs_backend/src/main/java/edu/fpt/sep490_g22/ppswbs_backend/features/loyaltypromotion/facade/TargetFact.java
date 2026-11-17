package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/**
 * Immutable fact about one target, supplied by the owning module. Loyalty rejects a configuration only
 * when the target does not exist, a variant does not belong to its product, or it is soft-deleted;
 * unpublished, hidden or unsold targets stay valid (spec clarification 2026-10-26).
 */
public record TargetFact(TargetDimension dimension, String targetKey, boolean exists,
                         boolean belongsToParent, boolean softDeleted, boolean publicVisible,
                         boolean purchasable) {
}
