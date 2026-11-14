package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.Valid;
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

    /**
     * {@code changeRequestReference} is set (and the route answers 202) when only a Manager rule was missing and the
     * request waits in the Staff/Manager queue instead of being applied (T117, FR-045a).
     */
    public record ChangeResultDto(
            String ticketCode,
            boolean applied,
            List<UUID> targetSessionReferences,
            String priceIncreaseAmount,
            boolean confirmIncreaseRequired,
            String balanceDue,
            Boolean countedChange,
            String changeRequestReference
    ) {
        public ChangeResultDto(String ticketCode, boolean applied, List<UUID> targetSessionReferences,
                               String priceIncreaseAmount, boolean confirmIncreaseRequired, String balanceDue,
                               Boolean countedChange) {
            this(ticketCode, applied, targetSessionReferences, priceIncreaseAmount, confirmIncreaseRequired,
                    balanceDue, countedChange, null);
        }
    }

    public record GrantChangeAllowanceRequestDto(
            @NotBlank @Size(min = 1, max = 1000) String reason
    ) {}

    public record ChangeAllowanceResponseDto(
            String allowanceReference,
            String ticketCode,
            String reason,
            Instant grantedAt
    ) {}

    // ---- T117: Staff/Manager queue of changes only a Manager may allow --------------------------------------

    public enum ChangeRequestDecision {
        APPROVE,
        REJECT
    }

    public record ChangeRequestDecisionDto(
            @NotNull ChangeRequestDecision decision,
            @NotBlank @Size(min = 1, max = 1000) String reason
    ) {}

    public record ChangeRequestDto(
            String requestReference,
            String ticketCode,
            String groupCode,
            TicketChangeType type,
            List<UUID> targetSessionReferences,
            List<String> blockedRules,
            String status,
            String priceIncreaseAmount,
            boolean buyerConfirmedIncrease,
            String requesterRole,
            String channel,
            String reason,
            Instant expiresAt,
            Instant createdAt,
            String decisionReason,
            Instant decidedAt,
            String appliedChangeReference
    ) {
        public ChangeRequestDto {
            targetSessionReferences = targetSessionReferences == null ? List.of() : List.copyOf(targetSessionReferences);
            blockedRules = blockedRules == null ? List.of() : List.copyOf(blockedRules);
        }
    }

    // ---- T118: several tickets of one group changed together, all or none (FR-044) --------------------------

    public record GroupTicketChangeItemDto(
            @NotBlank String ticketCode,
            @NotEmpty @Size(min = 1, max = 2) List<UUID> targetSessionReferences
    ) {}

    public record GroupTicketChangeRequestDto(
            @NotNull TicketChangeType type,
            @NotEmpty @Size(min = 1, max = 200) List<@Valid GroupTicketChangeItemDto> changes,
            Boolean confirmIncrease,
            String buyerVerificationReference,
            @Size(max = 1000) String reason
    ) {}

    public record GroupTicketChangeResultDto(
            String groupCode,
            boolean applied,
            String totalPriceIncrease,
            boolean confirmIncreaseRequired,
            List<ChangeResultDto> tickets
    ) {
        public GroupTicketChangeResultDto {
            tickets = tickets == null ? List.of() : List.copyOf(tickets);
        }
    }
}
