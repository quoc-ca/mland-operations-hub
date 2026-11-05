package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ModerationAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisibilityCommandRequest {

    @NotNull(message = "action is required")
    private ModerationAction action;

    @NotBlank(message = "reason is required")
    @Size(min = 3, max = 1000, message = "reason must be between 3 and 1000 characters")
    private String reason;

    private String idempotencyKey;
}

