package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.facade;

import java.util.Optional;

public interface NotificationFacade {

    /**
     * Enqueues a notification intent inside the caller's active database transaction.
     *
     * <p>Invariants:
     * 1. Executed synchronously within the caller's JPA/Spring transaction.
     * 2. Idempotent per (businessEventReference, templateCode, recipientRole, recipientAddressFingerprint).
     * 3. If validation fails (e.g. inactive template, invalid recipient, missing placeholder),
     *    the method DOES NOT throw an exception and DOES NOT roll back the business transaction;
     *    it creates a durable OperationalFailure record and returns a failed NotificationIntentResult.
     * 4. SMTP delivery is NOT executed in this call; it occurs asynchronously post-commit via worker polling.
     *
     * @param command The notification intent command containing business event reference, template code,
     *                recipient details, and frozen business snapshot.
     * @return NotificationIntentResult indicating whether a request was enqueued or an operational failure recorded.
     */
    NotificationIntentResult submitNotificationIntent(SubmitNotificationIntentCommand command);

    /**
     * Checks if a notification request exists for a given business event.
     *
     * @param businessEventReference Unique business event ID (e.g. bookingCode or orderCode).
     * @param templateCode           Registered template code (e.g. "BOOKING_CONFIRMED").
     * @return Minimal diagnostic summary if present.
     */
    Optional<NotificationSummaryDto> findNotificationSummary(String businessEventReference, String templateCode);
}

