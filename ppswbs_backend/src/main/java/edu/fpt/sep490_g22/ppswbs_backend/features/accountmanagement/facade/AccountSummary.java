package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;

public record AccountSummary(
    Long accountId,
    ActorRole role,
    ActorStatus status,
    String loginEmail,
    Instant createdAt,
    Instant updatedAt
) {}
