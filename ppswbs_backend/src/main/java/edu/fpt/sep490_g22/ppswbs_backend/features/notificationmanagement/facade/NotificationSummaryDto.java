package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class NotificationSummaryDto {
    private final Long requestId;
    private final String templateCode;
    private final String deliveryState; // PENDING, PROCESSING, RETRY_SCHEDULED, SENT, FAILED, UNKNOWN_OUTCOME, CANCELLED
    private final int attemptCount;
    private final Instant createdAt;
    private final Instant terminalAt;
}

