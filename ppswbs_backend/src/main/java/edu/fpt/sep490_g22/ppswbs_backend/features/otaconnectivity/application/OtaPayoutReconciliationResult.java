package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutStatus;

public record OtaPayoutReconciliationResult(
        String entryPublicReference,
        OtaPayoutStatus payoutStatus,
        long adjustedExpectedMinor,
        String disputeReference
) {}
