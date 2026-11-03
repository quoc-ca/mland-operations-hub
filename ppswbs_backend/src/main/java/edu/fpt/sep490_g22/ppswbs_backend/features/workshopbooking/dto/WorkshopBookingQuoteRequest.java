package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record WorkshopBookingQuoteRequest(
        @NotNull @Positive Long packageId,
        @NotEmpty @Size(max = 2) List<@NotNull String> sessionReferences,
        @NotNull @Positive Integer participantCount,
        @NotNull @Valid BenefitSelectionRequest benefitSelection) {

    public record BenefitSelectionRequest(
            @NotNull Choice choice,
            @Size(max = 128) String voucherCode,
            @NotNull @Pattern(regexp = "^(0|[1-9][0-9]*)$") @Size(max = 19) String requestedPoints) {
        public enum Choice { NONE, VOUCHER }
    }
}
