package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessAssessmentStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaSafeReadinessStatus;

import java.time.Instant;
import java.util.UUID;

public record OtaBookingEnableDecision(
        UUID assessmentId,
        OtaReadinessAssessmentStatus decision,
        OtaMappingStatus mappingState,
        OtaSafeReadinessStatus safeReadinessStatus,
        Instant recordedAt) {
}
