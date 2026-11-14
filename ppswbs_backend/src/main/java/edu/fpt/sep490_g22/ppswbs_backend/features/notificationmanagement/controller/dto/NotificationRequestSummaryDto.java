package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.controller.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record NotificationRequestSummaryDto(
        Long id,
        String businessEventReference,
        String templateCode,
        String recipientAddressMasked,
        String deliveryState,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant terminalAt
) {}

