package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationStatusResponse {
    private Long accountId;
    private int invitationVersion;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant serverNow;
    private String deliveryState; // QUEUED, PENDING, RETRY_SCHEDULED, SENT, FAILED, UNKNOWN_OUTCOME, CANCELLED
    private boolean manualContentEligible;
    private Long notificationRequestId;
}
