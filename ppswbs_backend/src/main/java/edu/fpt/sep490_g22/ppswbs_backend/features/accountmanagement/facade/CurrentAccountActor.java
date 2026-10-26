package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.util.List;

public record CurrentAccountActor(
    Long accountId,
    ActorRole role,
    ActorStatus status,
    boolean emailVerified,
    List<Long> activeLocationIds
) {}
