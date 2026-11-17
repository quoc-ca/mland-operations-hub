package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateTemplateVersionRequest(
        @NotBlank(message = "subjectTemplate must not be blank")
        String subjectTemplate,

        @NotBlank(message = "htmlTemplate must not be blank")
        String htmlTemplate,

        @NotBlank(message = "plainTextTemplate must not be blank")
        String plainTextTemplate
) {}

