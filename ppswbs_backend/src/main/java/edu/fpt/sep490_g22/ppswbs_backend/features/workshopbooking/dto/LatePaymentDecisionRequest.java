package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Manager decision on a late-payment review (003.1 FR-040). */
public record LatePaymentDecisionRequest(
        @NotBlank @Pattern(regexp = "CONFIRM|REFUND") String decision,
        @NotBlank @Size(min = 1, max = 1000) String reason
) {
}
