package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import java.time.Instant;

public record SettlementReadiness(boolean ready, boolean attendanceCorrectionWindowClosed,
                                  boolean hasNoShow, Instant lastRequiredSessionFinalizedAt) {
    public SettlementReadiness(boolean ready) {
        this(ready, false, false, null);
    }
}
