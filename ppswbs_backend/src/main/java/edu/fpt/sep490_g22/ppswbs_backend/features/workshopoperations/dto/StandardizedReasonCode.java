package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

/**
 * T034 / T041: Standardized reason catalogue for operational exceptions, check-in overrides,
 * post-finalization attendance corrections, and session cancellations (FR-004, FR-007a, FR-008, FR-009).
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
     * Session cancelled due to emergency instructor/craftsperson absence or sudden unavailability.
     */
    INSTRUCTOR_UNAVAILABLE(true, false),

    /**
     * Session cancelled due to facility, power, tool, or equipment failure onsite.
     */
    FACILITY_ISSUE(true, false),

    /**
     * Session cancelled due to severe weather emergency, flood, typhoon, or natural disaster.
     */
    WEATHER_EMERGENCY(true, false),

    /**
     * Attendance correction due to staff scanning error, mistaken participant identification, or barcode confusion.
     */
    RECORDING_ERROR(true, false),

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
