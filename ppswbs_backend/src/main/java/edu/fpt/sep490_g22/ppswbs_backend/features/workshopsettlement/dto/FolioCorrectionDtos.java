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
            String ticketCode,
            String bookingReference,
            String billReference,
            Long amountVnd,
            String overchargeAmount,
            String currency,
            String reason,
            String status,
            String lifecycleStatus,
            String refundId,
            String refundStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(SettlementFolioCorrection correction, SettlementCase settlementCase) {
            return from(correction, settlementCase, null, null);
        }

        public static Response from(SettlementFolioCorrection correction, SettlementCase settlementCase,
                                   String refundId, String refundStatus) {
            String ticketCode = correction.getBookingReference();
            String overcharge = correction.getAmountVnd() != null ? String.valueOf(correction.getAmountVnd()) : null;
            String st = settlementCase != null ? settlementCase.getLifecycleStatus() : null;
            return new Response(
                    correction.getCorrectionReference(),
                    ticketCode,
                    correction.getBookingReference(),
                    correction.getBillReference(),
                    correction.getAmountVnd(),
                    overcharge,
                    correction.getCurrencyCode(),
                    correction.getReason(),
                    st,
                    st,
                    refundId,
                    refundStatus,
                    correction.getCreatedAt(),
                    correction.getUpdatedAt()
            );
        }
    }
}

