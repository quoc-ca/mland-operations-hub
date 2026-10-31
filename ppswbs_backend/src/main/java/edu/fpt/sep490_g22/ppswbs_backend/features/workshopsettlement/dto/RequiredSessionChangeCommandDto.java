package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RequiredSessionChangeCommandDto(
        @NotEmpty List<@NotBlank String> targetRequiredSessionReferences,
        @NotBlank @Size(max = 200) String reason,
        @Size(max = 128) String cancellationHandoffReference) {
    public RequiredSessionChangeCommandDto {
        targetRequiredSessionReferences = targetRequiredSessionReferences == null
                ? List.of() : List.copyOf(targetRequiredSessionReferences);
    }
}
