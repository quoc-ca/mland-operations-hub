package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCorrection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class FolioCorrectionDtos {

    private FolioCorrectionDtos() {}

    public record CreateRequest(
            @NotNull(message = "amountVnd is required")
            @Positive(message = "amountVnd must be positive")
            Long amountVnd,

            @NotBlank(message = "reason is required")
            @Size(max = 255, message = "reason must not exceed 255 characters")
            String reason
    ) {}

    public record Response(
            String correctionReference,
            String bookingReference,
            String billReference,
            Long amountVnd,
            String currency,
            String reason,
            String lifecycleStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(SettlementFolioCorrection correction, SettlementCase settlementCase) {
            return new Response(
                    correction.getCorrectionReference(),
                    correction.getBookingReference(),
                    correction.getBillReference(),
                    correction.getAmountVnd(),
                    correction.getCurrencyCode(),
                    correction.getReason(),
                    settlementCase.getLifecycleStatus(),
                    correction.getCreatedAt(),
                    correction.getUpdatedAt()
            );
        }
    }
}

