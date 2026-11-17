package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;

public record ReasonCommandDto(@NotBlank String reason) { }
