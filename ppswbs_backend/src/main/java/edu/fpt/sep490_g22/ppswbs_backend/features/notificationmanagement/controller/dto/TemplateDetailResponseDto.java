package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record TemplateDetailResponseDto(
        String code,
        String name,
        String description,
        String owningModule,
        String lifecycleStatus,
        List<String> allowedPlaceholders,
        List<TemplateVersionDto> versions
) {
    @Builder
    public record TemplateVersionDto(
            Integer versionNumber,
            String status,
            String subjectTemplate,
            String htmlTemplate,
            String plainTextTemplate,
            Instant createdAt,
            String createdBy,
            Instant activatedAt,
            String activatedBy
    ) {}
}

