package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationResolutionRequest(
        @NotBlank @Size(max = 32) @OtaJsonString String resolution,
        @NotBlank @Size(max = 1000) @OtaJsonString String reason) implements OtaStrictRequest {}
