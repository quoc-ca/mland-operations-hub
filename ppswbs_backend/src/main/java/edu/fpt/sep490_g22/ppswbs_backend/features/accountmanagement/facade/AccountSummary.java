package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;

import java.time.Instant;

public record AccountSummary(
    Long accountId,
    AccountRole role,
    AccountStatus status,
    String loginEmail,
    Instant createdAt,
    Instant updatedAt
) {}
