package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import java.time.Instant;

public record VerifiedIdentity(
    String uid,
    String email,
    boolean emailVerified,
    Instant authTime
) {}
