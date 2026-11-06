package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;

import java.time.Instant;

public record OtaPayoutEvidenceCommand(
        OtaPayoutEvidenceType evidenceType,
        long amountMinor,
        String currencyCode,
        String evidenceReference,
        String reason,
        String relatedEntryPublicReference,
        String allocationReference,
        String payoutReference,
        Instant occurredAt
) {}
