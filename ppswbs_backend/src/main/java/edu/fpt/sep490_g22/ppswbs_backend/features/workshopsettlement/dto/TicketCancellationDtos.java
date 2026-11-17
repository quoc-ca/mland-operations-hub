package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TicketCancellationDtos {

    private TicketCancellationDtos() {}

    public record CancellationRequestDto(
            @NotBlank(message = "reason is required")
            @Size(min = 1, max = 1000, message = "reason must be between 1 and 1000 characters")
            String reason,

            @Size(max = 256, message = "buyerVerificationReference must not exceed 256 characters")
            String buyerVerificationReference
    ) {}

    public record TicketCancellationDto(
            String ticketCode,
            String benefitOutcome,
            long pointsReturned,
            boolean voucherRestored
    ) {}

    public record GroupCancellationRequestDto(
            @NotBlank(message = "reason is required")
            @Size(min = 1, max = 1000, message = "reason must be between 1 and 1000 characters")
            String reason,

            @Size(max = 256, message = "buyerVerificationReference must not exceed 256 characters")
            String buyerVerificationReference,

            @NotEmpty(message = "ticketCodes must not be empty")
            @Size(min = 1, max = 200, message = "ticketCodes must contain between 1 and 200 items")
            List<String> ticketCodes
    ) {}

    public record GroupCancellationResponseDto(
            String groupCode,
            List<TicketCancellationDto> tickets
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupCancellationProblemDto(
            String code,
            String correlationId,
            String correlationReference,
            String message,
            List<String> failingTicketCodes
    ) {
        public GroupCancellationProblemDto(String code, String correlation, String message, List<String> failingTicketCodes) {
            this(code, correlation, correlation, message, failingTicketCodes == null ? List.of() : List.copyOf(failingTicketCodes));
        }
    }
}

