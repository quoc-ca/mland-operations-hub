package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MlandRemedyCommandDto(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Pattern(regexp = "UNAVAILABLE|DECLINED") String replacementOutcome,
        @NotBlank @Size(max = 128) String cancellationHandoffReference
) { }
