package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

@Builder
public record TemplatePreviewResponse(
        String renderedSubject,
        String renderedHtml,
        String renderedPlainText
) {}

