package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;

public record InvitationDeliveryReport(
    Long invitationId,
    int version,
    Long notificationRequestId,
    String deliveryState,
    Instant observedAt
) {}
