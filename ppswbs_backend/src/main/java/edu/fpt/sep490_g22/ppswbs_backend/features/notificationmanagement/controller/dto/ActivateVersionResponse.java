package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ActivateVersionResponse(
        Integer activatedVersionNumber,
        Integer deactivatedVersionNumber,
        Instant activatedAt,
        String activatedBy
) {}

