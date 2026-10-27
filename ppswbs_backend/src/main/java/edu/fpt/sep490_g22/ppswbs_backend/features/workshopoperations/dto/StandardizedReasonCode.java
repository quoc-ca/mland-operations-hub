package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

/**
 * T034: Standardized reason catalogue for operational exceptions and check-in overrides (FR-004, FR-007a).
 * Fixed and system-controlled catalogue preventing ad-hoc reason codes per location.
 */
public enum StandardizedReasonCode {

    /**
     * Used when ticket redelivery has failed (delivery error or buyer confirmed non-receipt/inability to access).
     * Mandatory requirements: short reasonNote and evidenceReference (failed-redelivery correlation reference).
     */
    TICKET_REDELIVERY_UNAVAILABLE(true, true),

    /**
     * Customer device battery dead, damaged screen, or device unavailable onsite.
     */
    CUSTOMER_DEVICE_UNAVAILABLE(false, false),

    /**
     * Customer presented physical booking voucher or printed confirmation verified by Staff.
     */
    PHYSICAL_VOUCHER_VERIFIED(false, false),

    /**
     * Unlisted exception requiring a clear explanatory note or evidence reference.
     */
    OTHER_REQUIRES_NOTE(true, false);

    private final boolean requiresNote;
    private final boolean requiresEvidence;

    StandardizedReasonCode(boolean requiresNote, boolean requiresEvidence) {
        this.requiresNote = requiresNote;
        this.requiresEvidence = requiresEvidence;
    }

    public boolean isRequiresNote() {
        return requiresNote;
    }

    public boolean isRequiresEvidence() {
        return requiresEvidence;
    }

    public static boolean isRecognized(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (StandardizedReasonCode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
