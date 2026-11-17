package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;

public record InternalRosterEntry(
    AccountId accountId,
    String displayName,
    AccountRole role
) {}

