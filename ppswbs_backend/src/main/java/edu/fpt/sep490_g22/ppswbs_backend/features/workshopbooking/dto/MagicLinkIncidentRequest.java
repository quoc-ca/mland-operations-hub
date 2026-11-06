package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MagicLinkIncidentRequest {

    @NotBlank(message = "action is required")
    @Pattern(regexp = "REVOKE|ROTATE", message = "action must be REVOKE or ROTATE")
    private String action;

    @NotBlank(message = "reason is required")
    @Size(max = 1000, message = "reason must not exceed 1000 characters")
    private String reason;
}
