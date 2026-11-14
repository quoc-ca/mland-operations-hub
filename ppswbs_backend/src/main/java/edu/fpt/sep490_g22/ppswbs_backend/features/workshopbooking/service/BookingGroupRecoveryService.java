package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupMagicLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingGroupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.GroupActionOutcomeResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLineSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupMagicLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer and support recovery of a booking group (003.1 FR-048, decision 2026-11-13): the owning Member's read and
 * list, Guest lookup, the group Magic Link exchange, pending-hold release, confirmation resend and Magic Link
 * incidents. A failed proof always answers "not found", so a caller learns nothing about the group or which part
 * failed. Audit never stores contact proof, links or tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingGroupRecoveryService {

    static final int MEMBER_LIST_LIMIT = 30;
    private static final Duration EXPIRED_VISIBLE_FOR = Duration.ofHours(24);
    private static final int MEMBER_LIST_SCAN = 100;

    private final BookingGroupRepository groups;
    private final BookingGroupLineRepository lines;
    private final BookingGroupMagicLinkRepository magicLinks;
    private final GroupPaymentAttemptRepository paymentAttempts;
    private final BookingAuditEvidenceRepository auditEvidence;
    private final BookingGroupProjectionService projection;
    private final GroupConfirmationService confirmation;
    private final GroupNotificationService notifications;
    private final WorkshopBookingAuthorizationService authorization;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Value("${workshop.booking.resend.self-service.max-per-booking:3}")
    private int selfServiceResendLimit;
    @Value("${workshop.booking.resend.self-service.window-seconds:3600}")
    private long selfServiceResendWindowSeconds;
    @Value("${workshop.booking.resend.internal.max-per-booking:5}")
    private int internalResendLimit;
    @Value("${workshop.booking.resend.internal.window-seconds:900}")
    private long internalResendWindowSeconds;

    /** Who is acting, resolved from the bearer before any cached replay is returned. */
    public record Actor(AccountSummary account) {
        public boolean anonymous() {
            return account == null;
        }

        public String key() {
            return account == null ? "anonymous" : String.valueOf(account.accountId());
        }
    }

    public Actor resolveActor(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) return new Actor(null);
        return new Actor(authorization.authenticate(authorizationHeader));
    }

    // ---- Reads -------------------------------------------------------------------------------------------------

    /** The owning Member's group; any other caller gets 404, never 403. */
    @Transactional(readOnly = true)
    public BookingGroupView readOwned(String authorizationHeader, String groupCode) {
        AccountSummary member = authorization.requireMemberOwnership(authorizationHeader, null);
        BookingGroup group = findGroup(groupCode);
        if (!Objects.equals(group.getBuyerMemberAccountId(), member.accountId())) throw notFound();
        return projection.project(group, true);
    }

    /** At most 30 of the Member's groups, newest first; an expired hold is listed for 24 hours only. */
    @Transactional(readOnly = true)
    public List<BookingGroupView> listOwned(String authorizationHeader) {
        AccountSummary member = authorization.requireMemberOwnership(authorizationHeader, null);
        Instant now = Instant.now();
        List<BookingGroupView> result = new ArrayList<>();
        for (BookingGroup group : groups.findByBuyerMemberAccountIdOrderByCreatedAtDesc(member.accountId(),
                PageRequest.of(0, MEMBER_LIST_SCAN))) {
            if (result.size() >= MEMBER_LIST_LIMIT) break;
            if (group.getGroupStatus() == BookingStatus.EXPIRED
                    && (group.getHoldExpiresAt() == null || !group.getHoldExpiresAt().isAfter(now.minus(EXPIRED_VISIBLE_FOR)))) {
                continue;
            }
            try {
                result.add(projection.project(group, true));
            } catch (RuntimeException failure) {
                log.warn("Skipping group {} in a Member's list: {}", group.getGroupCode(), failure.getClass().getSimpleName());
            }
        }
        return result;
    }

    /** Guest lookup with the group code and exactly one stored contact value. */
    @Transactional(readOnly = true)
    public BookingGroupView lookupGuest(String groupCode, String contactEmail, String contactPhone) {
        requireOneProof(contactEmail, contactPhone);
        BookingGroup group = findGroup(groupCode);
        if (!contactMatches(group, contactEmail, contactPhone)) throw notFound();
        return projection.project(group, false);
    }

    /** Exchanges the group Magic Link for the read-only view; expired, revoked or unknown links all answer 404. */
    @Transactional(readOnly = true)
    public BookingGroupView exchangeMagicLink(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw notFound();
        BookingGroupMagicLink link = magicLinks.findByTokenHash(GroupMagicLinkTokens.hash(rawToken.trim()))
                .orElseThrow(BookingGroupRecoveryService::notFound);
        if (link.getLifecycleStatus() != MagicLinkLifecycleStatus.ACTIVE || link.getExpiresAt() == null
                || !link.getExpiresAt().isAfter(Instant.now())) {
            throw notFound();
        }
        return projection.project(link.getGroup(), false);
    }

    // ---- Commands ----------------------------------------------------------------------------------------------

    /** Guest (one contact proof) or the owning Member releases a PENDING_PAYMENT group before paying. */
    @Transactional
    public void releasePendingHold(String groupCode, Actor actor, String contactEmail, String contactPhone,
                                   String reason, String correlation, BookingIdempotencyClaim claim) {
        BookingGroup group = groups.findByGroupCodeForUpdate(normalizedCode(groupCode)).orElseThrow(BookingGroupRecoveryService::notFound);
        String actorType = requireBuyer(group, actor, contactEmail, contactPhone);
        boolean attemptExists = !paymentAttempts.findByGroupIdOrderByCreatedAtDesc(group.getId()).isEmpty();
        confirmation.releasePendingGroupByBuyer(group, attemptExists, "REL-CUSTOMER-" + claim.recordId(), correlation);
        audit(group, locationOf(group), actorType, actor, "PENDING_HOLD_RELEASE", correlation, reason.trim());
        idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 204, "");
    }

    /** Sends the buyer confirmation again to the stored contact email only, within the rolling limits. */
    @Transactional
    public GroupActionOutcomeResponse resendConfirmation(String groupCode, Actor actor, String contactEmail,
                                                         String contactPhone, String correlation,
                                                         BookingIdempotencyClaim claim) {
        BookingGroup group = groups.findByGroupCodeForUpdate(normalizedCode(groupCode)).orElseThrow(BookingGroupRecoveryService::notFound);
        Long locationId = locationOf(group);
        String actorType;
        if (!actor.anonymous() && (authorization.isStaff(actor.account()) || authorization.isManager(actor.account()))) {
            if (!authorization.canResendConfirmation(actor.account(), locationId)) {
                throw new WorkshopBookingProblemException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Caller is not authorized to resend this group's confirmation");
            }
            actorType = authorization.isManager(actor.account()) ? "MANAGER" : "STAFF";
        } else {
            actorType = requireBuyer(group, actor, contactEmail, contactPhone);
        }
        if (group.getGroupStatus() != BookingStatus.CONFIRMED) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only a confirmed group can resend its confirmation");
        }
        boolean selfService = "GUEST".equals(actorType) || "MEMBER".equals(actorType);
        int limit = selfService ? selfServiceResendLimit : internalResendLimit;
        long window = selfService ? selfServiceResendWindowSeconds : internalResendWindowSeconds;
        long recent = auditEvidence.countByBookingCodeAndActionAndOutcomeAndActorTypeInAndOccurredAtAfter(
                group.getGroupCode(), "CONFIRMATION_RESEND", "ACCEPTED",
                selfService ? List.of("GUEST", "MEMBER") : List.of("STAFF", "MANAGER"),
                Instant.now().minusSeconds(window));
        if (recent >= limit) {
            throw new WorkshopBookingProblemException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_RATE_LIMITED",
                    "Confirmation resend limit reached");
        }
        // Same rule as the single-booking resend: the existing active link is sent again, never a new one.
        String accessLink = notifications.findActiveAccessLink(group);
        if (accessLink == null) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "ACCESS_LINK_UNAVAILABLE",
                    "The active booking access link is unavailable");
        }
        confirmation.resendConfirmationEmail(group, accessLink, correlation);
        audit(group, locationId, actorType, actor, "CONFIRMATION_RESEND", correlation, null);
        return complete(claim, 202, new GroupActionOutcomeResponse(group.getGroupCode(), "CONFIRMATION_RESEND",
                "QUEUED", correlation));
    }

    /** Any active Manager revokes the group Magic Link, or rotates it and emails the new one to the stored contact. */
    @Transactional
    public GroupActionOutcomeResponse handleMagicLinkIncident(String groupCode, Actor actor, String action,
                                                              String reason, String correlation,
                                                              BookingIdempotencyClaim claim) {
        if (actor.anonymous()) {
            throw new WorkshopBookingUnauthorizedException("Authentication is required");
        }
        if (!authorization.canHandleMagicLinkIncident(actor.account(), null)) {
            throw new WorkshopBookingProblemException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Manager authorization is required");
        }
        BookingGroup group = groups.findByGroupCodeForUpdate(normalizedCode(groupCode)).orElseThrow(BookingGroupRecoveryService::notFound);
        if (group.getGroupStatus() != BookingStatus.CONFIRMED) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only a confirmed group has a Magic Link");
        }
        String outcome;
        if ("ROTATE".equals(action)) {
            String accessLink = rotateLink(group);
            notifications.queue(group, null, GroupNotificationService.KIND_GROUP_MAGIC_LINK_ROTATED,
                    group.getContactEmail(), "MLAND – Liên kết mới để xem đặt chỗ " + group.getGroupCode(),
                    "Xin chào,\n\nLiên kết xem đặt chỗ " + group.getGroupCode() + " đã được cấp lại. Liên kết cũ không "
                            + "còn dùng được. Vui lòng dùng liên kết riêng trong email này và không chia sẻ cho người khác.",
                    List.of(), null, accessLink, correlation);
            outcome = "ROTATED";
        } else {
            revokeActiveLinks(group);
            outcome = "REVOKED";
        }
        audit(group, locationOf(group), "MANAGER", actor, "MAGIC_LINK_" + action, correlation, reason.trim());
        return complete(claim, 200, new GroupActionOutcomeResponse(group.getGroupCode(), "MAGIC_LINK_" + action,
                outcome, correlation));
    }

    /** Expires active group links whose lifetime ran out and scrubs their URL from email snapshots (FR-017b9a). */
    @Scheduled(fixedDelayString = "${workshop.booking.group-magic-link-expiry-sweep-ms:60000}")
    @Transactional
    public int expireMagicLinks() {
        int expired = 0;
        for (BookingGroupMagicLink link : magicLinks.findActiveExpired(Instant.now(), PageRequest.of(0, 100))) {
            link.setLifecycleStatus(MagicLinkLifecycleStatus.EXPIRED);
            magicLinks.save(link);
            notifications.scrubAccessLinks(link.getGroup());
            expired++;
        }
        return expired;
    }

    // ---- Helpers -----------------------------------------------------------------------------------------------

    private String rotateLink(BookingGroup group) {
        Instant expiresAt = revokeActiveLinks(group);
        String rawToken = UUID.randomUUID().toString();
        magicLinks.save(BookingGroupMagicLink.builder()
                .group(group)
                .tokenHash(GroupMagicLinkTokens.hash(rawToken))
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(expiresAt != null && expiresAt.isAfter(Instant.now()) ? expiresAt
                        : Instant.now().plus(Duration.ofDays(30)))
                .build());
        return GroupMagicLinkTokens.accessLink(rawToken);
    }

    /** Revokes every active link of the group and scrubs their URL; returns the latest expiry seen. */
    private Instant revokeActiveLinks(BookingGroup group) {
        Instant latest = null;
        Instant now = Instant.now();
        for (BookingGroupMagicLink link : magicLinks.findByGroupIdAndLifecycleStatus(group.getId(),
                MagicLinkLifecycleStatus.ACTIVE)) {
            if (link.getExpiresAt() != null && (latest == null || link.getExpiresAt().isAfter(latest))) {
                latest = link.getExpiresAt();
            }
            link.setLifecycleStatus(MagicLinkLifecycleStatus.REVOKED);
            link.setRevokedAt(now);
            magicLinks.save(link);
        }
        notifications.scrubAccessLinks(group);
        return latest;
    }

    /** GUEST with exactly one matching contact value and no bearer, or the owning MEMBER with no proof. */
    private String requireBuyer(BookingGroup group, Actor actor, String contactEmail, String contactPhone) {
        if (actor.anonymous()) {
            requireOneProof(contactEmail, contactPhone);
            if (!contactMatches(group, contactEmail, contactPhone)) throw notFound();
            return "GUEST";
        }
        if (hasText(contactEmail) || hasText(contactPhone)) {
            throw new BookingValidationException("A signed-in Member sends no contact proof");
        }
        if (!authorization.isOwner(actor.account(), group.getBuyerMemberAccountId())) throw notFound();
        return "MEMBER";
    }

    private static void requireOneProof(String contactEmail, String contactPhone) {
        if (hasText(contactEmail) == hasText(contactPhone)) {
            throw new BookingValidationException("Exactly one of contactEmail or contactPhone is required");
        }
    }

    private static boolean contactMatches(BookingGroup group, String contactEmail, String contactPhone) {
        if (hasText(contactEmail)) {
            return group.getContactEmail() != null
                    && EmailMasking.normalize(contactEmail).equals(EmailMasking.normalize(group.getContactEmail()));
        }
        return ContactPhones.sameContact(contactPhone, group.getContactPhone());
    }

    private BookingGroup findGroup(String groupCode) {
        return groups.findByGroupCode(normalizedCode(groupCode)).orElseThrow(BookingGroupRecoveryService::notFound);
    }

    private static String normalizedCode(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) throw notFound();
        return groupCode.trim();
    }

    private Long locationOf(BookingGroup group) {
        return lines.findByGroupIdOrderByLineIndexAsc(group.getId()).stream()
                .flatMap(line -> line.getSessions().stream())
                .map(BookingGroupLineSession::getLocationReference)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private void audit(BookingGroup group, Long locationId, String actorType, Actor actor, String action,
                       String correlation, String reason) {
        auditEvidence.save(BookingAuditEvidence.builder()
                .bookingCode(group.getGroupCode())
                .locationId(locationId != null ? locationId : 0L)
                .actorType(actorType)
                .actorId(actor.anonymous() ? null : actor.account().accountId())
                .action(action)
                .outcome("ACCEPTED")
                .correlationReference(correlation)
                .actionReason(reason)
                .occurredAt(Instant.now())
                .build());
    }

    private GroupActionOutcomeResponse complete(BookingIdempotencyClaim claim, int status,
                                                GroupActionOutcomeResponse response) {
        try {
            idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), status,
                    mapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Group action outcome could not be stored", e);
        }
        return response;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static WorkshopBookingProblemException notFound() {
        return new WorkshopBookingProblemException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found");
    }
}
