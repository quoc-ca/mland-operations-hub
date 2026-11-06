package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

/** One requested target of a programme. */
public record TargetRef(TargetDimension dimension, String targetKey) {

    public TargetRef {
        if (dimension == null || targetKey == null || targetKey.isBlank() || targetKey.length() > 64) {
            throw LoyaltyException.invalidField("targets", "Target dimension and key are required.");
        }
    }
}
