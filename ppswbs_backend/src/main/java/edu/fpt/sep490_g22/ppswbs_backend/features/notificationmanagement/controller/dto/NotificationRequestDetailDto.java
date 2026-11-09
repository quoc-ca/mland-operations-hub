package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record NotificationRequestDetailDto(
        Long id,
        String businessEventReference,
        String templateCode,
        Long templateVersionId,
        String recipientRole,
        String recipientAddressMasked,
        String deliveryState,
        int attemptCount,
        Instant nextAttemptAt,
        Instant processingStartedAt,
        Instant smtpInvocationStartedAt,
        Instant terminalAt,
        Long originalRequestId,
        Instant createdAt,
        Instant updatedAt,
        List<AttemptDto> attempts,
        List<AuditDto> audits
) {
    @Builder
    public record AttemptDto(
            int attemptNumber,
            Instant startedAt,
            Instant completedAt,
            String outcomeCategory,
            String providerMessageId,
            String safeErrorSummary
    ) {}

    @Builder
    public record AuditDto(
            Long id,
            String actorAccountId,
            String actorRole,
            String actionType,
            String reason,
            String previousState,
            String newState,
            boolean duplicateRiskAccepted,
            Instant createdAt
    ) {}
}

