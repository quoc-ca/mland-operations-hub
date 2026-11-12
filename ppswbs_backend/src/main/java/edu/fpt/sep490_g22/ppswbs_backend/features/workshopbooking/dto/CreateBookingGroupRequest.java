package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.*;

/**
 * Confirms a quoted cart into one booking group (003.1 FR-030, FR-031). The contact is shared by the whole
 * group; companion details are not required (FR-031). Participants' own emails are set later by FR-041.
 */
public record CreateBookingGroupRequest(
        @NotBlank @Size(max = 36) String quoteRef,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Size(max = 32) String contactPhone) {
}
