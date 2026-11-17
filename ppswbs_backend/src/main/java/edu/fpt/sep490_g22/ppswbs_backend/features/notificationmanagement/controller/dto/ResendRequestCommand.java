package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record ResendRequestCommand(
        @NotBlank(message = "reason must not be blank")
        String reason,

        Boolean duplicateRiskAccepted
) {}

