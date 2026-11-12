package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * A cart to quote (003.1 FR-030): one or more lines, each a package with its complete required-session set
 * and a participant count. {@code benefitSelection} is optional; a voucher or points are refused until the
 * 006 cart contract lands (see 003.1 cart-benefit-contract-request.md).
 */
public record WorkshopCartQuoteRequest(
        @NotEmpty @Size(max = 10) List<@NotNull @Valid Line> lines,
        @Valid BenefitSelectionRequest benefitSelection) {

    /** One cart line. Participant count is the number of people, which is the number of tickets. */
    public record Line(
            @NotNull @Positive Long packageId,
            @NotEmpty @Size(max = 4) List<@NotNull String> sessionReferences,
            @NotNull @Positive @Max(20) Integer participantCount) {
    }

    public record BenefitSelectionRequest(
            @NotNull Choice choice,
            @Size(max = 128) String voucherCode,
            @Pattern(regexp = "^(0|[1-9][0-9]*)$") @Size(max = 19) String requestedPoints) {

        public enum Choice { NONE, VOUCHER }

        /** Whether this selection asks for anything 003.1 cannot allocate without the 006 cart contract. */
        public boolean requestsBenefit() {
            boolean points = requestedPoints != null && !requestedPoints.isBlank() && !"0".equals(requestedPoints);
            return choice == Choice.VOUCHER || points;
        }
    }
}
