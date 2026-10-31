package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record FinalizationCommandDto(Boolean managerException, @Size(max = 500) String reason) {
    @AssertTrue(message = "managerException is required and an exception requires a reason")
    public boolean isValidCommand() {
        if (managerException == null) return false;
        return managerException ? reason != null && !reason.isBlank() : reason == null || reason.isBlank();
    }
}
