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
public class LatePaymentFollowUpRequest {

    @NotBlank(message = "action is required")
    @Pattern(regexp = "RECORD|ESCALATE", message = "action must be RECORD or ESCALATE")
    private String action;

    @NotBlank(message = "reason is required")
    @Size(max = 1000, message = "reason must not exceed 1000 characters")
    private String reason;
}
