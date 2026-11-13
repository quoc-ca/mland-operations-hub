package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupNotificationIntent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Booking-group emails (decision 2026-11-13). Stores a protected snapshot per email and publishes a
 * reference-only {@link BookingNotificationReady} after commit; Feature 007 pulls the snapshot. Snapshots that
 * carry an OTP expire with the OTP and are scrubbed. Never logs recipients, content, QR or OTP.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupNotificationService {

    public static final String KIND_TICKET_RECIPIENT = "GROUP_TICKET_RECIPIENT";
    public static final String KIND_TICKET_RESEND = "GROUP_TICKET_RESEND";
    public static final String KIND_TICKET_RECIPIENT_REVOKED = "GROUP_TICKET_RECIPIENT_REVOKED";
    public static final String KIND_BUYER_VERIFICATION_OTP = "GROUP_BUYER_VERIFICATION_OTP";
    public static final String KIND_LATE_PAYMENT_PENDING = "GROUP_LATE_PAYMENT_PENDING";
    public static final String KIND_LATE_PAYMENT_MANAGER_ALERT = "GROUP_LATE_PAYMENT_MANAGER_ALERT";
    public static final String KIND_GROUP_CONFIRMATION = "GROUP_CONFIRMATION";

    private final BookingGroupNotificationIntentRepository intents;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    /** Queues one email in the caller's transaction; the handoff is published only after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String queue(BookingGroup group, TicketBooking ticket, String kind, String recipientEmail, String subject,
                        String body, List<String> artifacts, Instant snapshotExpiresAt, String correlationReference) {
        String reference = UUID.randomUUID().toString();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("notificationKind", kind);
        snapshot.put("storedRecipient", recipientEmail);
        snapshot.put("renderedSubject", subject);
        snapshot.put("renderedBookingContent", body);
        snapshot.put("renderedTicketArtifacts", artifacts == null ? List.of() : artifacts);
        snapshot.put("correlationReference", correlationReference);
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Group notification snapshot could not be written");
        }
        intents.save(BookingGroupNotificationIntent.builder()
                .notificationReference(reference)
                .group(group)
                .ticket(ticket)
                .notificationKind(kind)
                .correlationReference(correlationReference)
                .deliverySnapshot(json)
                .snapshotExpiresAt(snapshotExpiresAt)
                .build());
        BookingNotificationReady event = new BookingNotificationReady(reference, group.getGroupCode(), kind,
                correlationReference);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    events.publishEvent(event);
                } catch (RuntimeException ex) {
                    log.warn("Group notification handoff {} failed: {}", reference, ex.getClass().getSimpleName());
                }
            }
        });
        return reference;
    }

    /** The payload for Feature 007, or null when unknown, scrubbed or expired. */
    @Transactional(readOnly = true)
    public BookingNotificationDeliveryPayload readPayload(String notificationReference) {
        BookingGroupNotificationIntent intent = intents.findByNotificationReference(notificationReference).orElse(null);
        if (intent == null || intent.getDeliverySnapshot() == null || intent.getDeliverySnapshot().isBlank()) {
            return null;
        }
        if (intent.getSnapshotExpiresAt() != null && !intent.getSnapshotExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(intent.getDeliverySnapshot());
            if (node.isTextual()) node = objectMapper.readTree(node.asText());
            List<String> artifacts = new java.util.ArrayList<>();
            node.path("renderedTicketArtifacts").forEach(item -> artifacts.add(item.asText()));
            return new BookingNotificationDeliveryPayload(
                    node.path("notificationKind").asText(intent.getNotificationKind()),
                    node.path("storedRecipient").asText(""),
                    node.path("renderedBookingContent").asText(""),
                    artifacts,
                    "",
                    node.path("correlationReference").asText(intent.getCorrelationReference()),
                    node.path("renderedSubject").asText(null));
        } catch (Exception e) {
            log.warn("Failed to read group notification snapshot: notificationReference={}", notificationReference);
            return null;
        }
    }

    /** Removes expired snapshots (OTP emails) so no OTP stays at rest after its validity. */
    @Scheduled(fixedDelayString = "${workshop.booking.group-notification-scrub-ms:60000}")
    @Transactional
    public int scrubExpiredSnapshots() {
        return intents.scrubExpiredSnapshots(Instant.now());
    }
}
