package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;

/**
 * Domain event published by FE-001 when an internal account setup invitation is created or rotated.
 * Contains no secret or live URL (FR-030).
 */
public record InternalAccountInvitationRequested(
    Long invitationId,
    int version,
    Long accountId,
    String recipientEmail,
    String recipientName,
    String eventReference,
    String correlationReference,
    Instant createdAt,
    Instant expiresAt
) {}
