package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class SubmitNotificationIntentCommand {
    /** Unique source event ID (e.g. booking code, order reference). Mandatory. */
    private final String businessEventReference;

    /** Registered template code (e.g. "BOOKING_CONFIRMED", "RETAIL_ORDER_CONFIRMED"). Mandatory. */
    private final String templateCode;

    /** Authoritative recipient email approved by source feature. Mandatory. */
    private final String recipientEmail;

    /** Recipient display name approved by source feature. Optional. */
    private final String recipientName;

    /** Frozen map of template variables/placeholders matching template definition allowlist. Mandatory. */
    private final Map<String, Object> businessDataSnapshot;

    /** Optional safe non-credential artifact reference (e.g. ticket ID, order link slug). */
    private final String artifactReference;

    /** Optional short-lived credential reference (scrubbed on expiry). */
    private final String credentialReference;

    /** Optional expiration time of the credential reference. */
    private final Instant credentialExpiresAt;
}

