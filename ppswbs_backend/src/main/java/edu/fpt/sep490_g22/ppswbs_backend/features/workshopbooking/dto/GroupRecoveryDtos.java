package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the group recovery routes (003.1 FR-048, contract workshop-booking-cart.openapi.yaml). */
public final class GroupRecoveryDtos {

    private GroupRecoveryDtos() {
    }

    /** Guest lookup: the group code and exactly one stored contact value. */
    public record GroupLookupRequest(
            @NotBlank @Size(max = 32) String groupCode,
            @Email @Size(max = 254) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String contactEmail,
            @Size(min = 7, max = 32) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String contactPhone) {
    }

    /** The token read by the React client from the Magic Link URL fragment; never persisted or logged. */
    public record GroupMagicLinkExchangeRequest(
            @NotBlank @Size(max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String token) {
        @Override
        public String toString() {
            return "GroupMagicLinkExchangeRequest[token=<redacted>]";
        }
    }

    /** Guest sends exactly one stored contact value; a Member or Staff/Manager sends none. */
    public record ContactProof(
            @Email @Size(max = 254) String contactEmail,
            @Size(min = 7, max = 32) String contactPhone) {
    }

    public record GroupPendingHoldReleaseRequest(
            @Email @Size(max = 254) String contactEmail,
            @Size(min = 7, max = 32) String contactPhone,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record GroupMagicLinkIncidentRequest(
            @NotBlank @Pattern(regexp = "REVOKE|ROTATE") String action,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
