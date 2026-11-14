package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import java.util.List;

public record OtaBookingEnableCommand(
        Long mappingId,
        String readinessRecordReference,
        String reason,
        String adminAccountReference,
        List<OtaReadinessCheckSubmission> checks) {
}
