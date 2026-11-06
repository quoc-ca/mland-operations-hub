package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RefundPolicyRevisionCommandDto(@NotNull Instant effectiveAt,
                                             @NotNull @Min(0) @Max(100) Integer wholeBookingRefundPercent) { }
