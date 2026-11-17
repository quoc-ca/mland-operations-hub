package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NotificationErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        String correlationId,
        List<String> details
) {}

