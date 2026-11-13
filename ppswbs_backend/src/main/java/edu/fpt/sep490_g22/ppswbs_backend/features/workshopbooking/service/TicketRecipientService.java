package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupLineRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketRecipientChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketRecipientRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.TicketRecipientResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketQrReissueOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 003.1 FR-041 / T095: sends a confirmed ticket to a recipient email or changes its recipient (decision
 * 2026-11-13). Authority: the owning Member (bearer), a Guest buyer with a buyerVerificationReference, or Staff and
 * Manager with location scope plus a buyerVerificationReference and a reason. The first send keeps the QR and is
 * not counted; a change to a different email revokes the QR through 003.2 and counts as one online change (at
 * most 3); Staff changes are not counted. Any refusal rolls the whole command back. Audit keeps masked emails only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketRecipientService {

    public static final int MAX_ONLINE_CHANGES = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SCHEDULE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);

    private final TicketBookingRepository tickets;
    private final BookingGroupLineRepository lines;
    private final TicketPriceSnapshotRepository priceSnapshots;
    private final TicketRecipientRepository recipients;
    private final TicketRecipientChangeRepository changes;
    private final BookingAuditEvidenceRepository auditEvidence;
    private final WorkshopBookingAuthorizationService authorization;
    private final BuyerVerificationService buyerVerification;
    private final WorkshopOperationsAdapter operations;
    private final GroupNotificationService notifications;
    private final BookingIdempotencyService idempotency;
    private final ObjectMapper mapper;

    /** Who is acting, resolved from the bearer before any cached replay is returned. */
    public record Actor(AccountSummary account) {
        public boolean anonymous() {
            return account == null;
        }
    }

    public Actor resolveActor(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return new Actor(null);
        }
        return new Actor(authorization.authenticate(authorizationHeader));
    }

    @Transactional
    public TicketRecipientResponse change(String ticketCode, String recipientEmail, String buyerVerificationReference,
                                          String reason, Actor actor, String idempotencyKey, String correlation,
                                          BookingIdempotencyClaim claim) {
        TicketBooking ticket = tickets.findByTicketCodeForUpdate(ticketCode).orElseThrow(() ->
                new WorkshopBookingProblemException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found"));
        BookingGroup group = ticket.getGroup();
        BookingGroupLine line = lines.findByGroupIdOrderByLineIndexAsc(group.getId()).stream()
                .filter(l -> l.getLineIndex() == ticket.getLineIndex()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Ticket line is missing"));

        String actorType;
        String channel;
        String actorReference;
        String auditReason = null;
        if (actor.anonymous()) {
            buyerVerification.consume(group, buyerVerificationReference);
            actorType = "GUEST";
            channel = "ONLINE";
            actorReference = null;
        } else {
            AccountSummary account = actor.account();
            if (authorization.isMember(account)) {
                if (!authorization.isOwner(account, group.getBuyerMemberAccountId())) {
                    throw forbidden();
                }
                actorType = "MEMBER";
                channel = "ONLINE";
            } else if (authorization.isStaff(account) || authorization.isManager(account)) {
                if (!authorization.canResendConfirmation(account, locationOf(line))) {
                    throw forbidden();
                }
                if (reason == null || reason.isBlank()) {
                    throw new BookingValidationException("reason is required for a Staff recipient change");
                }
                buyerVerification.consume(group, buyerVerificationReference);
                actorType = authorization.isManager(account) ? "MANAGER" : "STAFF";
                channel = "SHOP";
                auditReason = reason.trim();
            } else {
                throw forbidden();
            }
            actorReference = String.valueOf(account.accountId());
        }

        if (ticket.getTicketStatus() != BookingStatus.CONFIRMED || ticket.getOperationsTicketReference() == null) {
            throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "TICKET_NOT_CONFIRMED",
                    "Only a confirmed ticket can be sent to a recipient");
        }

        String newEmail = recipientEmail.trim();
        TicketRecipient recipient = recipients.findByTicketId(ticket.getId()).orElse(null);
        boolean online = "ONLINE".equals(channel);
        if (recipient != null && EmailMasking.normalize(recipient.getRecipientEmail()).equals(EmailMasking.normalize(newEmail))) {
            // Same email: resend with the same QR; not a change.
            notifications.queue(group, ticket, GroupNotificationService.KIND_TICKET_RESEND, recipient.getRecipientEmail(),
                    recipientSubject(group, ticket) + " (gửi lại)", recipientBody(group, ticket, line),
                    artifacts(ticket), null, correlation);
            audit(group, locationOf(line), actorType, actorReference, "TICKET_RECIPIENT_RESENT", correlation, auditReason);
        } else if (recipient == null) {
            recipient = recipients.save(TicketRecipient.builder().ticket(ticket).recipientEmail(newEmail)
                    .changeCount(0).qrGeneration(1).build());
            record(ticket, null, newEmail, actorType, actorReference, channel, TicketRecipientChange.KIND_FIRST_SEND,
                    false, false, auditReason, correlation, idempotencyKey);
            notifications.queue(group, ticket, GroupNotificationService.KIND_TICKET_RECIPIENT, newEmail,
                    recipientSubject(group, ticket), recipientBody(group, ticket, line), artifacts(ticket), null,
                    correlation);
        } else {
            if (online && recipient.getChangeCount() >= MAX_ONLINE_CHANGES) {
                throw new WorkshopBookingProblemException(HttpStatus.CONFLICT, "RECIPIENT_CHANGE_LIMIT_REACHED",
                        "This ticket reached 3 online recipient changes; please visit the shop");
            }
            TicketQrReissueOutcome outcome = operations.reissueTicketQr(
                    UUID.fromString(ticket.getOperationsTicketReference()), "RCP-" + UUID.randomUUID(), correlation);
            if (outcome.status() != OutcomeStatus.ACCEPTED || outcome.qrReference() == null) {
                throw reissueRefusal(outcome);
            }
            String oldEmail = recipient.getRecipientEmail();
            ticket.setQrReference(outcome.qrReference());
            recipient.setRecipientEmail(newEmail);
            recipient.setQrGeneration(outcome.qrGeneration());
            if (online) recipient.setChangeCount(recipient.getChangeCount() + 1);
            record(ticket, oldEmail, newEmail, actorType, actorReference, channel, TicketRecipientChange.KIND_CHANGE,
                    true, online, auditReason, correlation, idempotencyKey);
            notifications.queue(group, ticket, GroupNotificationService.KIND_TICKET_RECIPIENT, newEmail,
                    recipientSubject(group, ticket), recipientBody(group, ticket, line), artifacts(ticket), null,
                    correlation);
            notifications.queue(group, ticket, GroupNotificationService.KIND_TICKET_RECIPIENT_REVOKED, oldEmail,
                    "MLAND – Vé " + ticket.getTicketCode() + " không còn dùng được với email này",
                    revokedBody(ticket, line), List.of(), null, correlation);
        }

        TicketRecipientResponse response = response(ticket, line, recipient);
        idempotency.completeClaimWithinTransaction(claim.recordId(), claim.claimVersion(), 200, json(response));
        log.info("Recipient command for ticket {} done by {} via {}", ticket.getTicketCode(), actorType, channel);
        return response;
    }

    private Long locationOf(BookingGroupLine line) {
        return line.getSessions().stream().map(BookingGroupLineSession::getLocationReference)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static WorkshopBookingProblemException forbidden() {
        return new WorkshopBookingProblemException(HttpStatus.FORBIDDEN, "RECIPIENT_CHANGE_FORBIDDEN",
                "Only the buyer, or Staff after verifying the buyer, may change the recipient");
    }

    private static WorkshopBookingProblemException reissueRefusal(TicketQrReissueOutcome outcome) {
        if (outcome.reason() == RejectionReason.TICKET_ALREADY_CHECKED_IN) {
            return new WorkshopBookingProblemException(HttpStatus.CONFLICT, "TICKET_ALREADY_CHECKED_IN",
                    "The recipient cannot change after check-in");
        }
        if (outcome.status() == OutcomeStatus.IN_PROGRESS) {
            return new WorkshopBookingProblemException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "A QR change for this ticket is in progress");
        }
        return new WorkshopBookingProblemException(HttpStatus.CONFLICT, "TICKET_NOT_CONFIRMED",
                "The ticket is no longer valid for a recipient change");
    }

    private void record(TicketBooking ticket, String oldEmail, String newEmail, String actorType, String actorReference,
                        String channel, String kind, boolean qrRotated, boolean countedOnline, String reason,
                        String correlation, String idempotencyKey) {
        changes.save(TicketRecipientChange.builder().ticket(ticket)
                .oldEmailMasked(EmailMasking.mask(oldEmail)).newEmailMasked(EmailMasking.mask(newEmail))
                .actorType(actorType).actorReference(actorReference).channel(channel).changeKind(kind)
                .qrRotated(qrRotated).countedOnline(countedOnline).reason(reason)
                .correlationReference(correlation).idempotencyKey(idempotencyKey).changedAt(Instant.now()).build());
    }

    private void audit(BookingGroup group, Long locationId, String actorType, String actorReference, String action, String correlation,
                       String reason) {
        auditEvidence.save(BookingAuditEvidence.builder()
                .bookingCode(group.getGroupCode())
                .locationId(locationId)
                .actorType(actorType)
                .actorId(actorReference == null ? null : Long.valueOf(actorReference))
                .action(action)
                .outcome("ACCEPTED")
                .correlationReference(correlation)
                .actionReason(reason)
                .occurredAt(Instant.now())
                .build());
    }

    private TicketRecipientResponse response(TicketBooking ticket, BookingGroupLine line, TicketRecipient recipient) {
        String collectionKind = priceSnapshots.findByTicketId(ticket.getId())
                .map(s -> s.getCollectionKind() == null ? null : s.getCollectionKind().name()).orElse(null);
        List<String> sessions = line.getSessions().stream()
                .sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal))
                .map(BookingGroupLineSession::getSessionReference).toList();
        int remaining = recipient == null ? MAX_ONLINE_CHANGES
                : Math.max(0, MAX_ONLINE_CHANGES - recipient.getChangeCount());
        return new TicketRecipientResponse(ticket.getTicketCode(), ticket.getTicketStatus().name(), collectionKind,
                recipient == null ? null : EmailMasking.mask(recipient.getRecipientEmail()), null, sessions, remaining);
    }

    private String json(TicketRecipientResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Recipient response could not be stored", e);
        }
    }

    private static String recipientSubject(BookingGroup group, TicketBooking ticket) {
        return "MLAND – " + EmailMasking.mask(group.getContactEmail()) + " gửi bạn vé workshop "
                + ticket.getPackageNameSnapshot();
    }

    private static String recipientBody(BookingGroup group, TicketBooking ticket, BookingGroupLine line) {
        return "Xin chào,\n\n"
                + EmailMasking.mask(group.getContactEmail()) + " đã gửi bạn một vé workshop tại MLAND.\n\n"
                + "Mã vé: " + ticket.getTicketCode() + " · Gói: " + ticket.getPackageNameSnapshot()
                + " · Lịch: " + schedule(line) + "\n\n"
                + "Xuất trình QR dưới đây khi check-in. Muốn đổi lịch hoặc hủy vé, vui lòng liên hệ người đã gửi vé "
                + "hoặc đến quầy MLAND.";
    }

    private static String revokedBody(TicketBooking ticket, BookingGroupLine line) {
        return "Vé " + ticket.getTicketCode() + " (" + ticket.getPackageNameSnapshot() + ", " + schedule(line)
                + ") đã được người mua chuyển cho người khác. Mã QR cũ đã hết hiệu lực. Nếu bạn nghĩ đây là nhầm lẫn, "
                + "vui lòng liên hệ người mua hoặc MLAND.";
    }

    private static List<String> artifacts(TicketBooking ticket) {
        return List.of("Mã vé: " + ticket.getTicketCode(), "QR: " + ticket.getQrReference());
    }

    private static String schedule(BookingGroupLine line) {
        return String.join("; ", line.getSessions().stream()
                .sorted(Comparator.comparingInt(BookingGroupLineSession::getOrdinal))
                .map(s -> s.getScheduledStart() == null ? "" : SCHEDULE.format(s.getScheduledStart())).toList());
    }
}
