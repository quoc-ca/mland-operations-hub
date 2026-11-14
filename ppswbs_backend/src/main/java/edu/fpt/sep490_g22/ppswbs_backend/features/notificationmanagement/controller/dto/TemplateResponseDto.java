package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record TemplateResponseDto(
        String code,
        String name,
        String description,
        String owningModule,
        String lifecycleStatus,
        List<String> allowedPlaceholders,
        ActiveVersionDto activeVersion,
        Integer latestDraftVersionNumber
) {
    @Builder
    public record ActiveVersionDto(
            Integer versionNumber,
            String subjectTemplate,
            Instant activatedAt,
            String activatedBy
    ) {}
}

