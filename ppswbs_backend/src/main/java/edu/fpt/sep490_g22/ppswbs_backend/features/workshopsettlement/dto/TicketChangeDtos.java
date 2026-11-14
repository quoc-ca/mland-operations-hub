package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketChangeDtos {

    private TicketChangeDtos() {}

    public enum TicketChangeType {
        ADVANCE,
        LATE_ARRIVAL
    }

    public record TicketChangeRequestDto(
            @NotNull TicketChangeType type,
            @NotEmpty @Size(min = 1, max = 2) List<UUID> targetSessionReferences,
            Boolean confirmIncrease,
            String buyerVerificationReference,
            @Size(max = 1000) String reason
    ) {}

    public record ChangeResultDto(
            String ticketCode,
            boolean applied,
            List<UUID> targetSessionReferences,
            String priceIncreaseAmount,
            boolean confirmIncreaseRequired,
            String balanceDue,
            Boolean countedChange
    ) {}

    public record GrantChangeAllowanceRequestDto(
            @NotBlank @Size(min = 1, max = 1000) String reason
    ) {}

    public record ChangeAllowanceResponseDto(
            String allowanceReference,
            String ticketCode,
            String reason,
            Instant grantedAt
    ) {}
}

