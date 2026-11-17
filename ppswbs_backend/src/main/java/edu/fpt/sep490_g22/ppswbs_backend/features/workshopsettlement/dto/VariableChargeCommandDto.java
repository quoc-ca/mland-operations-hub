package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record VariableChargeCommandDto(@NotBlank String description,
                                       @Positive Long amount,
                                       @NotBlank String reason) { }
