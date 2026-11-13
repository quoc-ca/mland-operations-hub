package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupMagicLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
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
    public static final String KIND_GROUP_CONFIRMATION_RESEND = "GROUP_CONFIRMATION_RESEND";
    public static final String KIND_GROUP_MAGIC_LINK_ROTATED = "GROUP_MAGIC_LINK_ROTATED";
    private static final String ACCESS_LINK_FIELD = "bookingAccessLink";

    private final BookingGroupNotificationIntentRepository intents;
    private final BookingGroupMagicLinkRepository magicLinks;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    /** Queues one email in the caller's transaction; the handoff is published only after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String queue(BookingGroup group, TicketBooking ticket, String kind, String recipientEmail, String subject,
                        String body, List<String> artifacts, Instant snapshotExpiresAt, String correlationReference) {
        return queue(group, ticket, kind, recipientEmail, subject, body, artifacts, snapshotExpiresAt, null,
                correlationReference);
    }

    /**
     * Same, carrying the group Magic Link. The raw URL is kept only in this protected snapshot; it is handed to
     * Feature 007 only while the link is active and is scrubbed once the link is revoked or expires (FR-017b9a).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String queue(BookingGroup group, TicketBooking ticket, String kind, String recipientEmail, String subject,
                        String body, List<String> artifacts, Instant snapshotExpiresAt, String bookingAccessLink,
                        String correlationReference) {
        String reference = UUID.randomUUID().toString();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("notificationKind", kind);
        snapshot.put("storedRecipient", recipientEmail);
        snapshot.put("renderedSubject", subject);
        snapshot.put("renderedBookingContent", body);
        snapshot.put("renderedTicketArtifacts", artifacts == null ? List.of() : artifacts);
        if (bookingAccessLink != null && !bookingAccessLink.isBlank()) {
            snapshot.put(ACCESS_LINK_FIELD, bookingAccessLink);
        }
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
                    activeLinkOrEmpty(node.path(ACCESS_LINK_FIELD).asText("")),
                    node.path("correlationReference").asText(intent.getCorrelationReference()),
                    node.path("renderedSubject").asText(null));
        } catch (Exception e) {
            log.warn("Failed to read group notification snapshot: notificationReference={}", notificationReference);
            return null;
        }
    }

    /** The stored link while its Magic Link is still active; otherwise nothing, so a dead link is never sent. */
    private String activeLinkOrEmpty(String accessLink) {
        String token = GroupMagicLinkTokens.tokenOf(accessLink);
        if (token == null) return "";
        return magicLinks.findByTokenHash(GroupMagicLinkTokens.hash(token))
                .filter(link -> link.getLifecycleStatus() == MagicLinkLifecycleStatus.ACTIVE
                        && link.getExpiresAt() != null && link.getExpiresAt().isAfter(Instant.now()))
                .map(link -> accessLink)
                .orElse("");
    }

    /** The group's still-active Magic Link URL from its newest email snapshot that carries one, or null. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String findActiveAccessLink(BookingGroup group) {
        List<BookingGroupNotificationIntent> all = intents.findByGroupIdOrderByCreatedAtAsc(group.getId());
        for (int i = all.size() - 1; i >= 0; i--) {
            String json = all.get(i).getDeliverySnapshot();
            if (json == null || !json.contains(ACCESS_LINK_FIELD)) continue;
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node.isTextual()) node = objectMapper.readTree(node.asText());
                String link = activeLinkOrEmpty(node.path(ACCESS_LINK_FIELD).asText(""));
                if (!link.isEmpty()) return link;
            } catch (Exception e) {
                log.warn("Skipping an unreadable group notification snapshot of group {}", group.getGroupCode());
            }
        }
        return null;
    }

    /** Removes the Magic Link URL from every snapshot of the group, in the caller's transaction (revoke/rotate/expiry). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int scrubAccessLinks(BookingGroup group) {
        int scrubbed = 0;
        for (BookingGroupNotificationIntent intent : intents.findByGroupIdOrderByCreatedAtAsc(group.getId())) {
            String json = intent.getDeliverySnapshot();
            if (json == null || !json.contains(ACCESS_LINK_FIELD)) continue;
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node.isTextual()) node = objectMapper.readTree(node.asText());
                if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object && object.has(ACCESS_LINK_FIELD)) {
                    object.remove(ACCESS_LINK_FIELD);
                    intent.setDeliverySnapshot(objectMapper.writeValueAsString(object));
                    intents.save(intent);
                    scrubbed++;
                }
            } catch (Exception e) {
                // An unreadable snapshot cannot be served either; drop it rather than keep a raw link at rest.
                intent.setDeliverySnapshot(null);
                intents.save(intent);
                scrubbed++;
            }
        }
        return scrubbed;
    }

    /** Removes expired snapshots (OTP emails) so no OTP stays at rest after its validity. */
    @Scheduled(fixedDelayString = "${workshop.booking.group-notification-scrub-ms:60000}")
    @Transactional
    public int scrubExpiredSnapshots() {
        return intents.scrubExpiredSnapshots(Instant.now());
    }
}
