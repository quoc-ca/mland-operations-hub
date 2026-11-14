package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

/**
 * Command to verify and consume buyer proof for group/ticket actions (003.1 T107 for 003.3 T099, T100, T101).
 * Carries no contact PII.
 */
public record ConsumeBuyerProofCommand(
        String groupCode,
        Long memberAccountId,
        String buyerVerificationReference
) {}

