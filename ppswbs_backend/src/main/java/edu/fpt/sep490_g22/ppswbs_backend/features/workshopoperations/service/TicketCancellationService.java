package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 003.2 FR-031 / T091: per-ticket cancellation. The ticket's seats in its future sessions are released and
 * those entitlements revoked; sessions that already started or were finalized keep their attendance record.
 * No other ticket of the booking group is touched. Idempotent per {@code operationReference}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCancellationService {

    static final String ACTION_CANCEL_TICKET = "CANCEL_TICKET";

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final TicketEntitlementReleaseRepository releaseRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final WorkshopOtaSnapshotService otaSnapshotService;

    @Transactional
    public TicketCancellationOutcome cancelTicket(CancelTicketCommand command) {
        if (command == null || command.ticketReference() == null
                || isBlank(command.cancellationCaseReference())
                || isBlank(command.operationReference()) || isBlank(command.correlationReference())) {
            return TicketCancellationOutcome.rejected(command == null ? null : command.ticketReference(),
                    RejectionReason.INVALID_STATE);
        }
        UUID ticketRef = command.ticketReference();
        ClaimResult claim = idempotencyManager.acquireClaim(ACTION_CANCEL_TICKET, command.operationReference(),
                command.correlationReference(), ticketRef + "|" + command.cancellationCaseReference());
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return TicketCancellationOutcome.rejected(ticketRef, RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return TicketCancellationOutcome.inProgress(ticketRef);
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstruct(ticketRef, claim.getCompletedRecord());
        }

        AttendanceTicket ticket = ticketRepository.findByReferenceWithLock(ticketRef.toString()).orElse(null);
        if (ticket == null) {
            return finish(claim, TicketCancellationOutcome.rejected(ticketRef, RejectionReason.TICKET_NOT_FOUND));
        }
        if (ticket.getSalesChannel() == TicketSalesChannel.OTA) {
            return finish(claim, TicketCancellationOutcome.rejected(ticketRef,
                    RejectionReason.OTA_TICKET_NOT_SUPPORTED));
        }
        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            return finish(claim, TicketCancellationOutcome.rejected(ticketRef, RejectionReason.INVALID_STATE));
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketIdWithLock(ticket.getId());
        List<TicketSessionEntitlement> releasable = new ArrayList<>();
        Map<Long, WorkshopSession> changedSessions = new LinkedHashMap<>();
        for (TicketSessionEntitlement entitlement : entitlements) {
            WorkshopSession session = entitlement.getSession();
            if (session == null || session.getScheduledStartAt() == null) {
                return finish(claim, TicketCancellationOutcome.rejected(ticketRef, RejectionReason.INVALID_STATE));
            }
            if (isFutureAndUntouched(session, entitlement, now)) {
                releasable.add(entitlement);
                changedSessions.put(session.getId(), session);
            }
        }

        List<UUID> released = new ArrayList<>();
        for (TicketSessionEntitlement entitlement : releasable) {
            WorkshopSession session = entitlement.getSession();
            releaseRepository.save(TicketEntitlementRelease.builder()
                    .ticket(ticket)
                    .session(session)
                    .cancellationCaseReference(command.cancellationCaseReference())
                    .operationReference(command.operationReference())
                    .correlationReference(command.correlationReference())
                    .releasedAt(now)
                    .build());
            entitlementRepository.delete(entitlement);
            releaseCommittedSeat(ticket.getBookingReference(), session.getId());
            released.add(UUID.fromString(session.getReference()));
        }
        entitlementRepository.flush();

        ticket.setValidityState(TicketValidityState.CANCELLED);
        ticketRepository.save(ticket);

        for (WorkshopSession changed : changedSessions.values()) {
            otaSnapshotService.publishForObservableChange(changed, command.operationReference(), now);
        }
        audit(command.correlationReference(), ticket, released.size(), "SUCCESS");
        log.info("Cancelled ticket {} and released {} future seats", ticket.getTicketCode(), released.size());
        return finish(claim, TicketCancellationOutcome.accepted(ticketRef, released));
    }

    /**
     * A session the ticket may still give back: it has not started, is not being or already cancelled, and the
     * ticket was neither checked in nor marked no-show there.
     */
    private boolean isFutureAndUntouched(WorkshopSession session, TicketSessionEntitlement entitlement, Instant now) {
        return session.getScheduledStartAt().isAfter(now)
                && session.getState() != SessionState.IN_PROGRESS
                && session.getState() != SessionState.ATTENDANCE_FINALIZED
                && session.getState() != SessionState.CANCELLED
                && entitlement.getCheckedInAt() == null
                && entitlement.getAttendanceState() == AttendanceState.NOT_CHECKED_IN;
    }

    /**
     * The booking line keeps one confirmed hold per session with its committed ticket count, and the capacity
     * reads take the greater of that count and the active entitlements. Decrementing it is what actually frees
     * the seat; a line whose last ticket goes releases the hold.
     */
    private void releaseCommittedSeat(String bookingReference, Long sessionId) {
        List<CapacityHold> holds = capacityHoldRepository
                .findConfirmedForBookingAndSessionWithLock(bookingReference, sessionId);
        for (CapacityHold hold : holds) {
            int remaining = (hold.getRequestedTicketCount() == null ? 0 : hold.getRequestedTicketCount()) - 1;
            if (remaining <= 0) {
                hold.setRequestedTicketCount(0);
                hold.setState(CapacityHoldState.RELEASED);
            } else {
                hold.setRequestedTicketCount(remaining);
            }
            capacityHoldRepository.save(hold);
            return;
        }
    }

    private TicketCancellationOutcome finish(ClaimResult claim, TicketCancellationOutcome outcome) {
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(),
                outcome.reason(), null);
        return outcome;
    }

    /**
     * Replays the original outcome. The released sessions are re-read from the release records, so a replay
     * reports the same seats without touching capacity again.
     */
    private TicketCancellationOutcome reconstruct(UUID ticketRef, FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() != OutcomeStatus.ACCEPTED) {
            RejectionReason reason = RejectionReason.INVALID_STATE;
            try {
                if (record.getRejectionReason() != null) {
                    reason = RejectionReason.valueOf(record.getRejectionReason());
                }
            } catch (IllegalArgumentException ignored) {
                // keep INVALID_STATE
            }
            return TicketCancellationOutcome.rejected(ticketRef, reason);
        }
        AttendanceTicket ticket = ticketRepository.findByReference(ticketRef.toString()).orElse(null);
        if (ticket == null) {
            return TicketCancellationOutcome.rejected(ticketRef, RejectionReason.TICKET_NOT_FOUND);
        }
        List<UUID> released = releaseRepository.findByTicketIdOrderByIdAsc(ticket.getId()).stream()
                .map(release -> sessionRepository.findById(release.getSession().getId())
                        .map(WorkshopSession::getReference).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(UUID::fromString)
                .toList();
        return TicketCancellationOutcome.accepted(ticketRef, released);
    }

    private void audit(String correlationReference, AttendanceTicket ticket, int releasedSeats, String outcome) {
        auditEventRepository.save(OperationsAuditEvent.builder()
                .action(ACTION_CANCEL_TICKET)
                .outcome(outcome)
                .correlationId(correlationReference)
                .metadata("{\"ticketCode\":\"" + ticket.getTicketCode() + "\",\"releasedSeats\":" + releasedSeats + "}")
                .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
