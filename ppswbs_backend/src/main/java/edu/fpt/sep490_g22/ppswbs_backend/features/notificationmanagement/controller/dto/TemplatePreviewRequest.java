package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record TemplatePreviewRequest(
        Integer versionNumber,
        Map<String, Object> sampleData
) {}

