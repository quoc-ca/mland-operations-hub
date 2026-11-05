package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheck;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;

public record OtaReadinessCheckSubmission(
        OtaReadinessCheck check,
        OtaReadinessCheckOutcome outcome,
        String evidenceReference) {
}
