package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

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
            String buyerVerificationReference,

            /** Required for ACCEPT_REPLACEMENT: the one offered session the buyer picks (T112, FR-043). */
            @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "REPLACEMENT_SESSION_REFERENCE_INVALID")
            String replacementSessionReference
    ) {
        public TicketRemedyAnswerRequestDto(TicketRemedyAnswer answer, String buyerVerificationReference) {
            this(answer, buyerVerificationReference, null);
        }
    }

    /** One session of a Mland offer; {@code selectable} is true while it can still be chosen (24 h rule, free seat). */
    public record OfferedSessionDto(
            String sessionReference,
            Instant startAt,
            Instant endAt,
            boolean selectable
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
            boolean voucherRestored,
            String selectedReplacementSessionReference
    ) {
        public TicketRemedyAnswerResponseDto(String remedyReference, String ticketCode, String groupCode,
                                             TicketRemedyAnswer answer, String outcome, Instant answeredAt,
                                             String refundRequestReference, long pointsReturned,
                                             boolean voucherRestored) {
            this(remedyReference, ticketCode, groupCode, answer, outcome, answeredAt, refundRequestReference,
                    pointsReturned, voucherRestored, null);
        }
    }

    /** The current Mland offer of a ticket with every offered session (T112, FR-043). */
    public record TicketRemedyOfferDto(
            String remedyReference,
            String ticketCode,
            String groupCode,
            String cancelledSessionReference,
            String outcome,
            Instant answerDeadlineAt,
            List<OfferedSessionDto> offeredSessions,
            String selectedReplacementSessionReference
    ) {
        public TicketRemedyOfferDto {
            offeredSessions = offeredSessions == null ? List.of() : List.copyOf(offeredSessions);
        }
    }
}
