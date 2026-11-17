package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * Result of buyer proof verification and consumption (003.1 T107).
 * Reuses 003.1 buyer verification error codes.
 */
public record ConsumeBuyerProofResult(
        String outcome,
        String errorCode
) {
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REFUSED = "REFUSED";

    public static final String CODE_REQUIRED = "BUYER_VERIFICATION_REQUIRED";
    public static final String CODE_FAILED = "BUYER_VERIFICATION_FAILED";
    public static final String CODE_EXPIRED = "BUYER_VERIFICATION_EXPIRED";

    public static ConsumeBuyerProofResult accepted() {
        return new ConsumeBuyerProofResult(ACCEPTED, null);
    }

    public static ConsumeBuyerProofResult refused(String errorCode) {
        return new ConsumeBuyerProofResult(REFUSED, errorCode);
    }

    public boolean isAccepted() {
        return ACCEPTED.equals(outcome);
    }
}

