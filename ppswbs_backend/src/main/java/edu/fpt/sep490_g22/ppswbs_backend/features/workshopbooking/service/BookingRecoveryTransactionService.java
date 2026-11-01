package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingActionOutcomeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.ProblemDetailResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationReady;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Applies booking recovery/resend outcomes, audit, handoff and idempotency atomically. */
@Service
@RequiredArgsConstructor
public class BookingRecoveryTransactionService {

    private final WorkshopBookingRepository bookings;
    private final BookingSessionCommitmentRepository commitments;
    private final BookingTicketRepository tickets;
    private final MagicLinkAccessRepository magicLinks;
    private final BookingNotificationIntentRepository notifications;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final BookingLatePaymentEvidenceRepository latePaymentEvidenceRepository;
    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopBookingFacade bookingFacade;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${workshop.booking.resend.self-service.max-per-booking:3}")
    private int selfServiceResendLimit;
    @Value("${workshop.booking.resend.self-service.window-seconds:3600}")
    private long selfServiceResendWindowSeconds;
    @Value("${workshop.booking.resend.internal.max-per-booking:5}")
    private int internalResendLimit;
    @Value("${workshop.booking.resend.internal.window-seconds:900}")
    private long internalResendWindowSeconds;

    @Transactional
    public BookingRecoveryCommandResult requestConfirmationDelivery(
            String authorizationHeader, String bookingCode, String contactEmail, String contactPhone,
            String requestedActorType, Long requestedActorId, String correlationReference,
            BookingIdempotencyClaim claim) {
        WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElse(null);
        if (booking == null) {
            return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
        }
        List<BookingSessionCommitment> rows = commitments.findByBookingIdOrderByOrdinalAsc(booking.getId());
        Long locationId = rows.isEmpty() ? null : rows.get(0).getLocationId();
        if (locationId == null || rows.stream().anyMatch(row -> !locationId.equals(row.getLocationId()))) {
            return complete(claim, 409, problem(409, "CONFLICT", "Booking branch evidence is unavailable", correlationReference));
        }
        String actorType;
        Long actorId = null;
        try {
            if (authorizationHeader == null || authorizationHeader.isBlank()) {
                if ((contactEmail == null) == (contactPhone == null)) {
                    return complete(claim, 400, problem(400, "INVALID_CONTACT_PROOF", "Exactly one stored contact proof is required", correlationReference));
                }
                var decision = bookingFacade.verifyCustomerBookingAccess(
                        new CustomerBookingAccessQuery(bookingCode, null, contactEmail, contactPhone));
                if (decision == null || !decision.authorized()) {
                    return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
                }
                actorType = "GUEST";
            } else {
                if (contactEmail != null || contactPhone != null) {
                    return complete(claim, 400, problem(400, "INVALID_CONTACT_PROOF", "Authenticated callers must not submit contact proof", correlationReference));
                }
                AccountSummary account = authorization.authenticate(authorizationHeader);
                actorType = account.role().name();
                actorId = account.accountId();
                if (account.role() == edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole.MEMBER) {
                    if (booking.getMemberAccountId() == null) {
                        return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
                    }
                    authorization.requireMemberOwnership(authorizationHeader, booking.getMemberAccountId(),
                            bookingCode, locationId, correlationReference);
                } else {
                    authorization.requireConfirmationResendAuthority(authorizationHeader, locationId,
                            bookingCode, correlationReference);
                }
            }
        } catch (WorkshopBookingUnauthorizedException ex) {
            return complete(claim, 401, problem(401, "UNAUTHORIZED", "Authentication is required", correlationReference));
        } catch (WorkshopBookingForbiddenException | WorkshopBookingLocationScopeException ex) {
            return complete(claim, 403, problem(403, "FORBIDDEN", "Caller is not authorized to resend this booking", correlationReference));
        }
        if (!actorType.equals(requestedActorType) || !java.util.Objects.equals(actorId, requestedActorId)) {
            return complete(claim, 409, problem(409, "AUTHORIZATION_CHANGED", "Caller authorization changed; retry the request", correlationReference));
        }
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return complete(claim, 409, problem(409, "CONFLICT", "Only a confirmed booking can resend its confirmation", correlationReference));
        }

        boolean selfService = "GUEST".equals(actorType) || "MEMBER".equals(actorType);
        int limit = selfService ? selfServiceResendLimit : internalResendLimit;
        long windowSeconds = selfService ? selfServiceResendWindowSeconds : internalResendWindowSeconds;
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalStateException("Confirmation resend limits must be positive");
        }
        Instant now = Instant.now();
        long recent = auditEvidenceRepository.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                bookingCode, "CONFIRMATION_RESEND", "ACCEPTED",
                selfService ? List.of("GUEST", "MEMBER") : List.of("STAFF", "MANAGER"),
                now.minusSeconds(windowSeconds));
        if (recent >= limit) {
            return complete(claim, 429, problem(429, "RESEND_RATE_LIMITED", "Confirmation resend limit reached", correlationReference));
        }

        List<BookingTicket> bookingTickets = tickets.findByBookingId(booking.getId());
        List<String> ticketCodes = bookingTickets.stream().map(BookingTicket::getTicketCode)
                .filter(code -> code != null && !code.isBlank()).toList();
        if (ticketCodes.size() != booking.getParticipantCount()
                || ticketCodes.stream().distinct().count() != booking.getParticipantCount()) {
            return complete(claim, 409, problem(409, "CONFLICT", "The confirmed ticket set is unavailable", correlationReference));
        }
        String existingAccessLink = activeAccessLink(booking.getId(), booking.getContactEmail(), now);
        if (existingAccessLink == null) {
            return complete(claim, 409, problem(409, "CONFLICT", "The active booking access link is unavailable", correlationReference));
        }

        String notificationReference = "NOTIF-REDELIV-" + UUID.randomUUID();
        String snapshot = confirmationSnapshot(booking, ticketCodes, existingAccessLink, correlationReference);
        notifications.save(BookingNotificationIntent.builder().booking(booking)
                .notificationReference(notificationReference).notificationKind("CONFIRMATION_RESEND")
                .correlationReference(correlationReference).deliverySnapshot(snapshot).build());
        authorization.recordAudit(bookingCode, locationId, actorType, actorId,
                "CONFIRMATION_RESEND", "ACCEPTED", correlationReference, null);
        publishAfterCommit(new BookingNotificationReady(notificationReference, bookingCode,
                "CONFIRMATION_RESEND", correlationReference));
        return complete(claim, 202, outcome(bookingCode, "CONFIRMATION_RESEND", "QUEUED", correlationReference));
    }

    @Transactional
    public BookingRecoveryCommandResult handleLatePaymentFollowup(
            String authorizationHeader, String bookingCode, String action, String reason,
            String correlationReference, BookingIdempotencyClaim claim) {
        WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode).orElse(null);
        if (booking == null) return complete(claim, 404,
                problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
        List<BookingSessionCommitment> rows = commitments.findByBookingIdOrderByOrdinalAsc(booking.getId());
        Long locationId = rows.isEmpty() ? null : rows.get(0).getLocationId();
        if (locationId == null || rows.stream().anyMatch(row -> !locationId.equals(row.getLocationId()))) {
            return complete(claim, 409, problem(409, "CONFLICT", "Booking location evidence is unavailable", correlationReference));
        }
        AccountSummary manager;
        try {
            manager = authorization.requireLatePaymentFollowupAuthority(authorizationHeader, locationId,
                    bookingCode, action, reason, correlationReference);
        } catch (WorkshopBookingUnauthorizedException ex) {
            return complete(claim, 401, problem(401, "UNAUTHORIZED", "Authentication is required", correlationReference));
        } catch (WorkshopBookingForbiddenException ex) {
            return complete(claim, 403, problem(403, "FORBIDDEN", "Manager authorization is required", correlationReference));
        }
        if (!latePaymentEvidenceRepository.existsByBookingCode(bookingCode)) {
            return complete(claim, 409, problem(409, "LATE_PAYMENT_EVIDENCE_NOT_FOUND",
                    "No verified late deposit evidence is available for this booking", correlationReference));
        }
        String auditAction = "LATE_PAYMENT_" + action;
        authorization.recordAudit(bookingCode, locationId, "MANAGER", manager.accountId(),
                auditAction, "ACCEPTED", correlationReference, reason);
        return complete(claim, 200, outcome(bookingCode, action, "ACCEPTED", correlationReference));
    }

    private String activeAccessLink(Long bookingId, String storedContactEmail, Instant now) {
        List<MagicLinkAccess> activeLinks = magicLinks.findByBookingIdAndLifecycleStatusOrderByIdAsc(
                bookingId, MagicLinkLifecycleStatus.ACTIVE);
        for (MagicLinkAccess active : activeLinks) {
            if (active.getExpiresAt() == null || !active.getExpiresAt().isAfter(now)) continue;
            for (BookingNotificationIntent intent : notifications.findByBookingId(bookingId)) {
                try {
                    var snapshot = mapper.readTree(intent.getDeliverySnapshot());
                    String candidate = snapshot.path("bookingAccessLink").asText(null);
                    String recipient = snapshot.path("storedRecipient").asText(null);
                    if (candidate == null || candidate.isBlank()) continue;
                    if (storedContactEmail == null || !storedContactEmail.equals(recipient)) continue;
                    int fragment = candidate.indexOf('#');
                    if (fragment < 0 || fragment == candidate.length() - 1) continue;
                    String token = candidate.substring(fragment + 1);
                    if (active.getTokenHash().equals(hashToken(token))) return candidate;
                } catch (JsonProcessingException ignored) {
                    // A malformed historical notification snapshot is not a source of bearer credentials.
                }
            }
        }
        return null;
    }

    private String confirmationSnapshot(WorkshopBooking booking, List<String> ticketCodes,
                                        String accessLink, String correlationReference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationKind", "CONFIRMATION_RESEND");
        payload.put("storedRecipient", booking.getContactEmail());
        payload.put("renderedBookingContent", "Mland Workshop Booking Confirmation - Code: " + booking.getBookingCode());
        payload.put("renderedTicketArtifacts", ticketCodes);
        payload.put("bookingAccessLink", accessLink);
        payload.put("correlationReference", correlationReference);
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Booking confirmation snapshot could not be serialized", ex);
        }
    }

    @Transactional
    public BookingRecoveryCommandResult applyMagicLinkIncident(
            String authorizationHeader,
            String bookingCode,
            String action,
            String reason,
            String correlationReference,
            BookingIdempotencyClaim claim) {
        WorkshopBooking booking = bookings.findLockedByBookingCode(bookingCode)
                .orElse(null);
        if (booking == null) {
            return complete(claim, 404, problem(404, "RESOURCE_NOT_FOUND", "Booking not found", correlationReference));
        }

        List<BookingSessionCommitment> bookingCommitments = commitments.findByBookingIdOrderByOrdinalAsc(booking.getId());
        Long locationId = bookingCommitments.isEmpty() ? null : bookingCommitments.get(0).getLocationId();
        if (locationId == null || bookingCommitments.stream().anyMatch(c -> !locationId.equals(c.getLocationId()))) {
            return complete(claim, 409, problem(409, "CONFLICT", "Booking branch evidence is unavailable", correlationReference));
        }

        AccountSummary manager;
        try {
            manager = authorization.requireMagicLinkIncidentAuthority(authorizationHeader,
                    locationId, bookingCode, action, reason, correlationReference);
        } catch (WorkshopBookingUnauthorizedException ex) {
            return complete(claim, 401, problem(401, "UNAUTHORIZED", "Authentication is required", correlationReference));
        } catch (WorkshopBookingForbiddenException ex) {
            return complete(claim, 403, problem(403, "FORBIDDEN", "Manager authorization is required", correlationReference));
        }

        Instant now = Instant.now();
        List<MagicLinkAccess> active = magicLinks.findByBookingIdAndLifecycleStatusOrderByIdAsc(
                booking.getId(), MagicLinkLifecycleStatus.ACTIVE);
        List<MagicLinkAccess> usable = new ArrayList<>();
        for (MagicLinkAccess link : active) {
            if (link.getExpiresAt() != null && link.getExpiresAt().isAfter(now)) {
                usable.add(link);
            } else {
                link.setLifecycleStatus(MagicLinkLifecycleStatus.EXPIRED);
            }
        }
        if (usable.isEmpty()) {
            audit(bookingCode, locationId, manager, action, "NO_ACTIVE_LINK", correlationReference, reason, now);
            return complete(claim, 409, problem(409, "CONFLICT", "No active booking access link is available", correlationReference));
        }

        if ("REVOKE".equals(action)) {
            usable.forEach(link -> revoke(link, manager, reason, now));
            audit(bookingCode, locationId, manager, action, "REVOKED", correlationReference, reason, now);
            return complete(claim, 200, outcome(bookingCode, action, "REVOKED", correlationReference));
        }

        Instant replacementExpiry = usable.stream().map(MagicLinkAccess::getExpiresAt)
                .max(Comparator.naturalOrder()).orElseThrow();
        usable.forEach(link -> revoke(link, manager, reason, now));
        String rawToken = UUID.randomUUID().toString();
        magicLinks.save(MagicLinkAccess.builder()
                .booking(booking)
                .tokenHash(hashToken(rawToken))
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(replacementExpiry)
                .build());

        String notificationReference = "NOTIF-ML-" + UUID.randomUUID();
        String accessLink = "/workshop-booking/access#" + rawToken;
        String snapshot = deliverySnapshot(booking, accessLink, correlationReference);
        notifications.save(BookingNotificationIntent.builder()
                .booking(booking)
                .notificationReference(notificationReference)
                .notificationKind("MAGIC_LINK_ROTATION")
                .correlationReference(correlationReference)
                .deliverySnapshot(snapshot)
                .build());
        audit(bookingCode, locationId, manager, action, "ROTATED", correlationReference, reason, now);
        publishAfterCommit(new BookingNotificationReady(notificationReference, bookingCode,
                "MAGIC_LINK_ROTATION", correlationReference));
        return complete(claim, 200, outcome(bookingCode, action, "ROTATED", correlationReference));
    }

    private void revoke(MagicLinkAccess link, AccountSummary manager, String reason, Instant now) {
        link.setLifecycleStatus(MagicLinkLifecycleStatus.REVOKED);
        link.setRevokedAt(now);
        link.setRevocationReason(reason);
        link.setRevokedByAccountId(manager.accountId());
        magicLinks.save(link);
    }

    private void audit(String bookingCode, Long locationId, AccountSummary manager, String action,
                       String outcome, String correlationReference, String reason, Instant now) {
        authorization.recordAudit(bookingCode, locationId, "MANAGER", manager.accountId(),
                "MAGIC_LINK_" + action, outcome, correlationReference, reason);
    }

    private String deliverySnapshot(WorkshopBooking booking, String accessLink, String correlationReference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationKind", "MAGIC_LINK_ROTATION");
        payload.put("storedRecipient", booking.getContactEmail());
        payload.put("renderedBookingContent", "Mland Workshop Booking Confirmation - Code: " + booking.getBookingCode());
        payload.put("renderedTicketArtifacts", tickets.findByBookingId(booking.getId()).stream()
                .map(BookingTicket::getTicketCode).filter(Objects::nonNull).toList());
        payload.put("bookingAccessLink", accessLink);
        payload.put("correlationReference", correlationReference);
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Booking notification snapshot could not be serialized", ex);
        }
    }

    private BookingRecoveryCommandResult complete(BookingIdempotencyClaim claim, int status, Object response) {
        try {
            String payload = mapper.writeValueAsString(response);
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), status, payload);
            return new BookingRecoveryCommandResult(status, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Booking recovery outcome could not be serialized", ex);
        }
    }

    private ProblemDetailResponse problem(int status, String code, String detail, String correlationReference) {
        return ProblemDetailResponse.builder().type(URI.create("urn:problem-type:workshop-booking:" + code.toLowerCase(Locale.ROOT)))
                .title(status == 404 ? "Resource Not Found" : status == 400 ? "Bad Request"
                        : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden"
                        : status == 429 ? "Too Many Requests" : "Conflict")
                .status(status).code(code).detail(detail).correlationId(correlationReference).build();
    }

    private BookingActionOutcomeResponse outcome(String bookingCode, String action, String outcome, String correlationReference) {
        return BookingActionOutcomeResponse.builder().bookingCode(bookingCode).action(action)
                .outcome(outcome).correlationId(correlationReference).build();
    }

    private void publishAfterCommit(BookingNotificationReady event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { eventPublisher.publishEvent(event); }
        });
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
