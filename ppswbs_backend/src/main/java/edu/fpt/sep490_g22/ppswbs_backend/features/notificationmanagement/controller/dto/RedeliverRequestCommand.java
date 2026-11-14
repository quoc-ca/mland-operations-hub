package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;

@Builder
public record RedeliverRequestCommand(
        @NotBlank(message = "reason must not be blank")
        String reason
) {}

