package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class TicketRemedyDtos {

    private TicketRemedyDtos() {}

    public enum TicketRemedyAnswer {
        ACCEPT_REPLACEMENT,
        REFUND
    }

    public record TicketRemedyAnswerRequestDto(
            @NotNull(message = "ANSWER_REQUIRED")
            TicketRemedyAnswer answer,

            @Pattern(regexp = "^bvr-[0-9a-f]{16,64}$", message = "BUYER_VERIFICATION_REFERENCE_INVALID")
            String buyerVerificationReference
    ) {}

    public record TicketRemedyAnswerResponseDto(
            String remedyReference,
            String ticketCode,
            String groupCode,
            TicketRemedyAnswer answer,
            String outcome,
            Instant answeredAt,
            String refundRequestReference,
            long pointsReturned,
            boolean voucherRestored
    ) {}
}

