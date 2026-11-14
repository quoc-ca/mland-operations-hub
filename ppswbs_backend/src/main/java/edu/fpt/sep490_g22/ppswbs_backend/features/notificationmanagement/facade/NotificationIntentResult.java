package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationIntentResult {
    public enum Status {
        ENQUEUED,
        DUPLICATE_IGNORED,
        OPERATIONAL_FAILURE
    }

    private final Status status;
    private final Long notificationRequestId;   // Non-null if ENQUEUED or DUPLICATE_IGNORED
    private final Long operationalFailureId;     // Non-null if OPERATIONAL_FAILURE
    private final String failureCode;            // Non-null if OPERATIONAL_FAILURE
    private final String message;
}

