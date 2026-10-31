package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;

import java.util.Set;

public record TargetAccountFacts(
    AccountId accountId,
    AccountRole role,
    AccountStatus status,
    Set<Long> activeWorkshopLocationIds
) {}

