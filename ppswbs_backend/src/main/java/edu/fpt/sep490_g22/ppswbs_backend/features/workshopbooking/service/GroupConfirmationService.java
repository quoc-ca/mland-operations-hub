package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.common.booking.MlandTicketConfirmedEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Confirms or releases a whole booking group (003.1 T093, FR-038, FR-039, FR-032).
 *
 * <p>Confirmation is one transaction for the group: every line's capacity holds are committed, each ticket
 * takes the QR identity Workshop Operations issued for it, the group gets one Magic Link, and one
 * {@link MlandTicketConfirmedEvent} per ticket lets Settlement open one folio per ticket. A free ticket of a
 * paying group is confirmed with the group, never before it, because the group pays once.
 *
 * <p>Replay is safe: a group that is already {@code CONFIRMED} is returned unchanged, and the Operations
 * commit and ticket issue are themselves idempotent on the line reference.
 *
 * <p>Benefits are not settled here: a cart that asks for a voucher or points is refused at quote time while
 * the 006 cart contract is pending, so every group confirmed here holds no Loyalty component. The
 * {@code settleAcceptedBenefits} call belongs with that contract.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupConfirmationService {

    private final BookingGroupRepository groups;
    private final BookingGroupLineRepository lines;
    private final TicketBookingRepository tickets;
    private final TicketPriceSnapshotRepository snapshots;
    private final BookingGroupMagicLinkRepository magicLinks;
    private final WorkshopOperationsAdapter operations;
    private final ApplicationEventPublisher events;

    /** How long after the last session a group's Magic Link stays usable when no session end is known. */
    private static final Duration FALLBACK_LINK_LIFETIME = Duration.ofDays(30);

    /**
     * @param rawMagicToken the only time the Magic Link value exists in clear; only its hash is stored
     */
    public record GroupConfirmation(String groupCode, String groupStatus, String paymentMode,
                                    long groupCollectionVnd, Instant confirmedAt, String rawMagicToken,
                                    List<ConfirmedTicket> tickets, boolean alreadyConfirmed) {
    }

    public record ConfirmedTicket(String ticketCode, int lineIndex, int participantOrdinal, String qrReference,
                                  String financialStatus, long finalPriceVnd, long collectedAtBookingVnd,
                                  long remainingVnd) {
    }

    /**
     * Confirms a group that needs no gateway payment: every ticket free ({@code INTERNAL_FREE}) or the whole
     * group collecting under the 5,000 VND VNPay floor and deferred to checkout ({@code INTERNAL_DEFERRED}).
     */
    @Transactional
    public GroupConfirmation confirmInternalGroup(String groupCode, String operationReference,
                                                  String correlationReference) {
        BookingGroup group = lockGroup(groupCode);
        if (group.getPaymentMode() == GroupPaymentMode.VNPAY) {
            throw new BookingValidationException("This group collects " + group.getGroupCollectionVnd()
                    + " VND and must be paid through the gateway");
        }
        if (group.getGroupCollectionVnd() != 0) {
            throw new BookingValidationException("An internally confirmed group collects nothing at booking");
        }
        return confirm(group, "INTERNAL_CONFIRMED", operationReference, correlationReference);
    }

    /**
     * Confirms a group against verified payment evidence for the whole group amount. The caller owns the
     * evidence check; this method only requires the group to be the VNPay one still inside its hold.
     */
    @Transactional
    public GroupConfirmation confirmPaidGroup(String groupCode, long verifiedAmountVnd, String operationReference,
                                              String correlationReference) {
        BookingGroup group = lockGroup(groupCode);
        if (group.getPaymentMode() != GroupPaymentMode.VNPAY) {
            throw new BookingValidationException("This group was not awaiting a gateway payment");
        }
        if (verifiedAmountVnd != group.getGroupCollectionVnd()) {
            throw new BookingConflictException("The verified amount does not match the group collection");
        }
        return confirm(group, "GROUP_PAYMENT_CONFIRMED", operationReference, correlationReference);
    }

    /** FR-032: when the 12-minute hold runs out the whole group is released, never a part of it. */
    @Transactional
    public boolean releaseExpiredGroup(String groupCode, String operationReference, String correlationReference) {
        BookingGroup group = groups.findByGroupCodeForUpdate(groupCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking group " + groupCode + " was not found"));
        if (group.getGroupStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        if (group.getHoldExpiresAt() == null || group.getHoldExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        for (BookingGroupLine line : lines.findByGroupIdOrderByLineIndexAsc(group.getId())) {
            List<UUID> holdReferences = holdReferencesOf(line);
            if (!holdReferences.isEmpty()) {
                CapacityReleaseOutcome outcome = operations.releaseBookingHolds(line.getLineReference(),
                        holdReferences, operationReference + ":" + line.getLineReference(), correlationReference);
                if (outcome == null || outcome.status() != OutcomeStatus.ACCEPTED) {
                    throw new BookingConflictException("Workshop Operations refused to release line "
                            + line.getLineIndex() + " of the group");
                }
            }
            line.getSessions().forEach(session -> session.setHoldStatus(LineHoldStatus.RELEASED));
            lines.save(line);
        }
        group.setGroupStatus(BookingStatus.EXPIRED);
        groups.save(group);
        for (TicketBooking ticket : tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())) {
            ticket.setTicketStatus(BookingStatus.EXPIRED);
            tickets.save(ticket);
        }
        log.info("Released the whole booking group {} after its hold expired", groupCode);
        return true;
    }

    private GroupConfirmation confirm(BookingGroup group, String auditAction, String operationReference,
                                      String correlationReference) {
        if (group.getGroupStatus() == BookingStatus.CONFIRMED) {
            return replay(group);
        }
        if (group.getGroupStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException("Booking group is " + group.getGroupStatus() + " and cannot confirm");
        }
        Instant now = Instant.now();
        if (group.getHoldExpiresAt() == null || !group.getHoldExpiresAt().isAfter(now)) {
            // A payment that lands after the hold is the late-payment review of FR-040, not a confirmation.
            throw new BookingCreationRejectedException("GROUP_HOLD_EXPIRED",
                    "The group hold has expired; a late payment is reviewed separately");
        }
        List<BookingGroupLine> groupLines = lines.findByGroupIdOrderByLineIndexAsc(group.getId());
        if (groupLines.isEmpty()) {
            throw new BookingConflictException("The booking group has no line to confirm");
        }

        // 1. Commit every line, then take the ticket identities Operations issued for it.
        Instant lastSessionEnd = null;
        List<ConfirmedTicket> confirmed = new ArrayList<>();
        List<TicketBooking> confirmedEntities = new ArrayList<>();
        for (BookingGroupLine line : groupLines) {
            List<UUID> holdReferences = holdReferencesOf(line);
            if (holdReferences.size() != line.getSessions().size() || holdReferences.isEmpty()) {
                throw new BookingConflictException("Line " + line.getLineIndex() + " has no capacity hold to commit");
            }
            CapacityCommitOutcome outcome = operations.commitBookingHolds(line.getLineReference(), holdReferences,
                    operationReference + ":" + line.getLineReference(), correlationReference);
            if (outcome == null || outcome.status() != OutcomeStatus.ACCEPTED) {
                String reason = outcome == null ? "UNKNOWN" : String.valueOf(outcome.reason());
                throw new BookingConflictException("Workshop Operations refused to commit line "
                        + line.getLineIndex() + ": " + reason);
            }
            line.getSessions().forEach(session -> session.setHoldStatus(LineHoldStatus.COMMITTED));
            lines.save(line);

            DeliverySafeTicketSet projection = operations.getBookingTicketProjection(line.getLineReference());
            if (projection == null || projection.tickets() == null
                    || projection.tickets().size() != line.getTicketCount()) {
                throw new BookingConflictException("Workshop Operations issued no complete ticket set for line "
                        + line.getLineIndex());
            }
            List<TicketBooking> lineTickets = tickets
                    .findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId()).stream()
                    .filter(ticket -> ticket.getLineIndex() == line.getLineIndex())
                    .toList();
            if (lineTickets.size() != line.getTicketCount()) {
                throw new BookingConflictException("Line " + line.getLineIndex() + " has no ticket per person");
            }
            for (int i = 0; i < lineTickets.size(); i++) {
                DeliverySafeTicket issued = projection.tickets().get(i);
                if (issued.qrReference() == null || issued.qrReference().isBlank() || issued.ticketReference() == null) {
                    throw new BookingConflictException("Workshop Operations returned a ticket without a QR identity");
                }
                TicketBooking ticket = lineTickets.get(i);
                TicketPriceSnapshot snapshot = snapshots.findByTicketId(ticket.getId())
                        .orElseThrow(() -> new BookingConflictException(
                                "Ticket " + ticket.getTicketCode() + " has no price snapshot"));
                ticket.setOperationsTicketReference(issued.ticketReference().toString());
                ticket.setQrReference(issued.qrReference());
                ticket.setTicketStatus(BookingStatus.CONFIRMED);
                ticket.setFinancialStatus(financialStatusOf(snapshot));
                ticket.setConfirmedAt(now.truncatedTo(ChronoUnit.MICROS));
                tickets.save(ticket);
                confirmedEntities.add(ticket);
                confirmed.add(new ConfirmedTicket(ticket.getTicketCode(), line.getLineIndex(),
                        ticket.getParticipantOrdinal(), issued.qrReference(), ticket.getFinancialStatus().name(),
                        snapshot.getFinalPriceVnd(), snapshot.getCollectedAtBookingVnd(),
                        snapshot.getRemainingVnd()));
                for (var entitlement : issued.sessionEntitlements()) {
                    if (entitlement.scheduledEnd() != null
                            && (lastSessionEnd == null || entitlement.scheduledEnd().isAfter(lastSessionEnd))) {
                        lastSessionEnd = entitlement.scheduledEnd();
                    }
                }
            }
        }

        // 2. The group itself, then one Magic Link covering every ticket of the group (FR-042).
        group.setGroupStatus(BookingStatus.CONFIRMED);
        group.setConfirmedAt(now.truncatedTo(ChronoUnit.MICROS));
        groups.save(group);
        String rawMagicToken = UUID.randomUUID().toString();
        magicLinks.save(BookingGroupMagicLink.builder()
                .group(group)
                .tokenHash(hashToken(rawMagicToken))
                .lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(lastSessionEnd != null ? lastSessionEnd : now.plus(FALLBACK_LINK_LIFETIME))
                .build());

        // 3. One event per ticket so Settlement opens one folio per ticket (003.3 FR-030).
        for (int i = 0; i < confirmedEntities.size(); i++) {
            ConfirmedTicket ticket = confirmed.get(i);
            events.publishEvent(new MlandTicketConfirmedEvent(group.getGroupCode(), ticket.ticketCode(),
                    ticket.finalPriceVnd(), ticket.collectedAtBookingVnd(), ticket.remainingVnd(),
                    group.getConfirmedAt()));
        }
        log.info("Confirmed booking group {} ({}): {} tickets, {} VND collected [{}]", group.getGroupCode(),
                auditAction, confirmed.size(), group.getGroupCollectionVnd(), correlationReference);
        return new GroupConfirmation(group.getGroupCode(), group.getGroupStatus().name(),
                group.getPaymentMode().name(), group.getGroupCollectionVnd(), group.getConfirmedAt(), rawMagicToken,
                List.copyOf(confirmed), false);
    }

    /** A replayed confirmation reports the stored outcome and issues no second Magic Link. */
    private GroupConfirmation replay(BookingGroup group) {
        List<ConfirmedTicket> stored = new ArrayList<>();
        for (TicketBooking ticket : tickets.findByGroupIdOrderByLineIndexAscParticipantOrdinalAsc(group.getId())) {
            TicketPriceSnapshot snapshot = snapshots.findByTicketId(ticket.getId()).orElseThrow(
                    () -> new BookingConflictException("Ticket " + ticket.getTicketCode() + " has no price snapshot"));
            stored.add(new ConfirmedTicket(ticket.getTicketCode(), ticket.getLineIndex(),
                    ticket.getParticipantOrdinal(), ticket.getQrReference(), ticket.getFinancialStatus().name(),
                    snapshot.getFinalPriceVnd(), snapshot.getCollectedAtBookingVnd(), snapshot.getRemainingVnd()));
        }
        return new GroupConfirmation(group.getGroupCode(), group.getGroupStatus().name(),
                group.getPaymentMode().name(), group.getGroupCollectionVnd(), group.getConfirmedAt(), null,
                List.copyOf(stored), true);
    }

    private BookingGroup lockGroup(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new BookingValidationException("A group code is required");
        }
        return groups.findByGroupCodeForUpdate(groupCode)
                .orElseThrow(() -> new BookingNotFoundException("Booking group " + groupCode + " was not found"));
    }

    private static PaymentStatus financialStatusOf(TicketPriceSnapshot snapshot) {
        if (snapshot.getRemainingVnd() == 0) {
            return PaymentStatus.FULLY_PAID;
        }
        return snapshot.getCollectedAtBookingVnd() > 0 ? PaymentStatus.DEPOSIT_PAID : PaymentStatus.UNPAID;
    }

    private static List<UUID> holdReferencesOf(BookingGroupLine line) {
        List<UUID> references = new ArrayList<>();
        for (BookingGroupLineSession session : line.getSessions()) {
            if (session.getHoldReference() == null) {
                continue;
            }
            try {
                references.add(UUID.fromString(session.getHoldReference()));
            } catch (IllegalArgumentException ex) {
                throw new BookingConflictException("A stored capacity hold reference is invalid");
            }
        }
        return references;
    }

    private static String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
