package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * A cart to quote (003.1 FR-030): one or more lines, each a package with its complete required-session set
 * and a participant count. {@code benefitSelection} is optional: without one, or with {@code NONE}, the cart gets
 * the automatic workshop discount (006 FR-044); {@code VOUCHER} replaces it (006 FR-015). A voucher or points need a
 * signed-in Member.
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

        /** Whether this selection asks for a Member-only benefit: a voucher or points. */
        public boolean requestsBenefit() {
            boolean points = requestedPoints != null && !requestedPoints.isBlank() && !"0".equals(requestedPoints);
            return choice == Choice.VOUCHER || points;
        }
    }
}
