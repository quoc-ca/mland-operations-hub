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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 003.2 FR-034 / T094: replaces the sessions of the selected tickets, all or none, one seat per ticket per
 * target session, keeping the QR and the attendance history. Customer changes also consume the target's
 * reschedule quota (FR-035 / T095); a Mland-caused replacement is checked against capacity only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSessionReplacementService {

    static final String ACTION_REPLACE_TICKET_SESSIONS = "REPLACE_TICKET_SESSIONS";

    /** FR-034: the replacement of a missed session must start within this window after its end. */
    private static final Duration LATE_ARRIVAL_WINDOW = Duration.ofDays(7);

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final TicketSessionReplacementRepository replacementRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;

    @Transactional
    public TicketSessionReplacementOutcome replace(ReplaceTicketSessionsCommand command) {
        if (command == null || command.lines() == null || command.lines().isEmpty() || command.type() == null
                || isBlank(command.operationReference()) || isBlank(command.correlationReference())) {
            return TicketSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE, List.of());
        }
        String fingerprint = command.type() + "|" + command.lines().stream()
                .map(line -> line.ticketReference() + ">" + line.targetSessionReferences().stream()
                        .map(UUID::toString).sorted().toList())
                .sorted().toList();
        ClaimResult claim = idempotencyManager.acquireClaim(ACTION_REPLACE_TICKET_SESSIONS,
                command.operationReference(), command.correlationReference(), fingerprint);
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return TicketSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE, List.of());
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return TicketSessionReplacementOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstruct(claim.getCompletedRecord());
        }

        // One ticket may appear once: a second line for the same ticket would make the new set ambiguous.
        Set<UUID> seenTickets = new HashSet<>();
        for (int i = 0; i < command.lines().size(); i++) {
            ReplaceTicketSessionsCommand.Line line = command.lines().get(i);
            if (line.targetSessionReferences().isEmpty()
                    || new HashSet<>(line.targetSessionReferences()).size() != line.targetSessionReferences().size()
                    || !seenTickets.add(line.ticketReference())) {
                return finish(claim, TicketSessionReplacementOutcome
                        .rejected(RejectionReason.INVALID_REPLACEMENT, List.of(i)));
            }
        }

        // Lock every session involved, in reference order, so two replacements cannot interleave on one session.
        Map<UUID, AttendanceTicket> tickets = new LinkedHashMap<>();
        Map<Long, List<TicketSessionEntitlement>> entitlementsByTicket = new LinkedHashMap<>();
        SortedSet<String> referencesToLock = new TreeSet<>();
        for (ReplaceTicketSessionsCommand.Line line : command.lines()) {
            line.targetSessionReferences().forEach(reference -> referencesToLock.add(reference.toString()));
        }
        for (int i = 0; i < command.lines().size(); i++) {
            ReplaceTicketSessionsCommand.Line line = command.lines().get(i);
            AttendanceTicket ticket = ticketRepository.findByReference(line.ticketReference().toString()).orElse(null);
            if (ticket == null) {
                return finish(claim, TicketSessionReplacementOutcome
                        .rejected(RejectionReason.TICKET_NOT_FOUND, List.of(i)));
            }
            tickets.put(line.ticketReference(), ticket);
            for (TicketSessionEntitlement entitlement : entitlementRepository.findByTicketId(ticket.getId())) {
                referencesToLock.add(entitlement.getSession().getReference());
            }
        }

        List<WorkshopSession> lockedSessions = sessionRepository.findByReferenceInWithLock(referencesToLock);
        if (lockedSessions.size() != referencesToLock.size()) {
            return finish(claim, TicketSessionReplacementOutcome
                    .rejected(RejectionReason.INVALID_REPLACEMENT, List.of()));
        }
        Map<String, WorkshopSession> sessionsByReference = new HashMap<>();
        lockedSessions.forEach(session -> sessionsByReference.put(session.getReference(), session));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<Integer> failing = new ArrayList<>();
        RejectionReason reason = null;

        // Per line: the ticket is active, its own moves are legal, and the new set satisfies the package rules.
        Map<Integer, List<Move>> movesByLine = new LinkedHashMap<>();
        for (int i = 0; i < command.lines().size(); i++) {
            ReplaceTicketSessionsCommand.Line line = command.lines().get(i);
            AttendanceTicket ticket = tickets.get(line.ticketReference());
            AttendanceTicket locked = ticketRepository.findByReferenceWithLock(ticket.getReference()).orElse(null);
            if (locked == null || locked.getValidityState() != TicketValidityState.ACTIVE) {
                failing.add(i);
                reason = reason == null ? RejectionReason.INVALID_STATE : reason;
                continue;
            }
            tickets.put(line.ticketReference(), locked);
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketIdWithLock(locked.getId());
            entitlementsByTicket.put(locked.getId(), entitlements);

            List<WorkshopSession> sources = entitlements.stream()
                    .map(TicketSessionEntitlement::getSession)
                    .sorted(Comparator.comparing(WorkshopSession::getScheduledStartAt)
                            .thenComparing(WorkshopSession::getReference))
                    .toList();
            List<WorkshopSession> targets = line.targetSessionReferences().stream()
                    .map(reference -> sessionsByReference.get(reference.toString()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(WorkshopSession::getScheduledStartAt)
                            .thenComparing(WorkshopSession::getReference))
                    .toList();
            if (targets.size() != line.targetSessionReferences().size() || sources.size() != targets.size()) {
                failing.add(i);
                reason = reason == null ? RejectionReason.INVALID_REPLACEMENT : reason;
                continue;
            }
            if (!packageReservationPolicy.isValidTicketReplacement(locked.getBookingReference(), targets)) {
                failing.add(i);
                reason = reason == null ? RejectionReason.INVALID_REPLACEMENT : reason;
                continue;
            }

            RejectionReason lineReason = null;
            List<Move> moves = new ArrayList<>();
            for (int index = 0; index < sources.size() && lineReason == null; index++) {
                WorkshopSession source = sources.get(index);
                WorkshopSession target = targets.get(index);
                if (source.getId().equals(target.getId())) {
                    continue; // this session of the ticket does not move
                }
                TicketSessionEntitlement entitlement = entitlements.stream()
                        .filter(item -> item.getSession().getId().equals(source.getId()))
                        .findFirst().orElse(null);
                if (entitlement == null) {
                    lineReason = RejectionReason.INVALID_STATE;
                    break;
                }
                lineReason = checkSource(command.type(), source, entitlement, now);
                if (lineReason == null) {
                    lineReason = checkTarget(target, now);
                }
                if (lineReason == null && isLateArrival(command.type(), source, now)) {
                    // FR-034: only the missed session's own replacement must fall inside the 7-day window.
                    if (target.getScheduledStartAt().isAfter(source.getScheduledEndAt().plus(LATE_ARRIVAL_WINDOW))) {
                        lineReason = RejectionReason.LATE_ARRIVAL_WINDOW_EXPIRED;
                    }
                }
                if (lineReason == null) {
                    moves.add(new Move(locked, entitlement, source, target));
                }
            }
            if (lineReason != null) {
                failing.add(i);
                reason = reason == null ? lineReason : reason;
                continue;
            }
            movesByLine.put(i, moves);
        }

        if (!failing.isEmpty()) {
            failing.sort(Integer::compare);
            return finish(claim, TicketSessionReplacementOutcome.rejected(reason, failing));
        }

        // Capacity and, for a customer change, reschedule quota must hold for the whole selection at once.
        Map<Long, Integer> incomingPerSession = new LinkedHashMap<>();
        movesByLine.values().stream().flatMap(List::stream)
                .forEach(move -> incomingPerSession.merge(move.target().getId(), 1, Integer::sum));
        for (Map.Entry<Long, Integer> entry : incomingPerSession.entrySet()) {
            WorkshopSession target = sessionsByReference.values().stream()
                    .filter(session -> session.getId().equals(entry.getKey())).findFirst().orElseThrow();
            if (remainingCapacity(target, now) < entry.getValue()) {
                return finish(claim, TicketSessionReplacementOutcome
                        .rejected(RejectionReason.INSUFFICIENT_CAPACITY, failingLinesFor(movesByLine, target.getId())));
            }
            if (command.type() != TicketReplacementType.MLAND
                    && target.remainingRescheduleQuota() < entry.getValue()) {
                return finish(claim, TicketSessionReplacementOutcome.rejected(
                        RejectionReason.RESCHEDULE_QUOTA_EXHAUSTED, failingLinesFor(movesByLine, target.getId())));
            }
        }

        // Everything fits: move the entitlements, keeping the ticket, its QR and its attendance history.
        boolean quotaCounted = command.type() != TicketReplacementType.MLAND;
        Map<Long, WorkshopSession> changedSessions = new LinkedHashMap<>();
        for (List<Move> moves : movesByLine.values()) {
            for (Move move : moves) {
                move.entitlement().setSession(move.target());
                move.entitlement().setAttendanceState(AttendanceState.NOT_CHECKED_IN);
                move.entitlement().setCheckedInAt(null);
                entitlementRepository.save(move.entitlement());
                moveCommittedSeat(move.ticket().getBookingReference(), move.source(), move.target());
                replacementRepository.save(TicketSessionReplacement.builder()
                        .ticket(move.ticket())
                        .sourceSession(move.source())
                        .targetSession(move.target())
                        .replacementType(TicketSessionReplacementType.valueOf(command.type().name()))
                        .quotaConsumed(quotaCounted)
                        .operationReference(command.operationReference())
                        .correlationReference(command.correlationReference())
                        .replacedAt(now)
                        .build());
                if (quotaCounted) {
                    WorkshopSession target = move.target();
                    target.setRescheduleQuotaUsed((target.getRescheduleQuotaUsed() == null ? 0
                            : target.getRescheduleQuotaUsed()) + 1);
                    sessionRepository.save(target);
                }
                changedSessions.put(move.source().getId(), move.source());
                changedSessions.put(move.target().getId(), move.target());
            }
        }
        entitlementRepository.flush();

        for (WorkshopSession changed : changedSessions.values()) {
            otaSnapshotService.publishForObservableChange(changed, command.operationReference(), now);
        }
        int movedTickets = (int) movesByLine.values().stream().filter(moves -> !moves.isEmpty()).count();
        audit(command, movedTickets, "SUCCESS");
        log.info("Replaced sessions of {} ticket(s) as {}", movedTickets, command.type());
        return finish(claim, TicketSessionReplacementOutcome.accepted());
    }

    /** FR-034: a source may be left behind only when the ticket has no attendance there. */
    private RejectionReason checkSource(TicketReplacementType type, WorkshopSession source,
                                        TicketSessionEntitlement entitlement, Instant now) {
        if (source.getScheduledStartAt() == null || source.getScheduledEndAt() == null) {
            return RejectionReason.INVALID_STATE;
        }
        if (entitlement.getCheckedInAt() != null
                || entitlement.getAttendanceState() != AttendanceState.NOT_CHECKED_IN) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        if (source.getState() == SessionState.ATTENDANCE_FINALIZED) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        if (type == TicketReplacementType.MLAND) {
            // The Mland case is exactly the disrupted session, so a cancelled or running source is expected.
            return null;
        }
        if (type == TicketReplacementType.CUSTOMER_LATE_ARRIVAL) {
            boolean runningAndNotEnded = source.getState() == SessionState.IN_PROGRESS
                    && source.getScheduledEndAt().isAfter(now);
            boolean future = source.getScheduledStartAt().isAfter(now);
            return runningAndNotEnded || future ? null : RejectionReason.INVALID_REPLACEMENT;
        }
        boolean future = source.getScheduledStartAt().isAfter(now)
                && source.getState() != SessionState.IN_PROGRESS
                && source.getState() != SessionState.CANCELLATION_REQUESTED
                && source.getState() != SessionState.CANCELLED;
        return future ? null : RejectionReason.INVALID_REPLACEMENT;
    }

    /** FR-034: every target must be future, published and customer-selectable. */
    private RejectionReason checkTarget(WorkshopSession target, Instant now) {
        if (target.getScheduledStartAt() == null || !target.getScheduledStartAt().isAfter(now)
                || target.getState() != SessionState.PUBLISHED) {
            return RejectionReason.INVALID_REPLACEMENT;
        }
        return null;
    }

    private boolean isLateArrival(TicketReplacementType type, WorkshopSession source, Instant now) {
        return type == TicketReplacementType.CUSTOMER_LATE_ARRIVAL
                && !source.getScheduledStartAt().isAfter(now);
    }

    /** Locking reads, as in {@code GroupCapacityService}: an older snapshot must not take the last seat twice. */
    private int remainingCapacity(WorkshopSession session, Instant now) {
        List<CapacityHold> countable = capacityHoldRepository.findCountableHoldsForSessionForShare(session.getId());
        int activeHolds = countable.stream()
                .filter(hold -> hold.getState() == CapacityHoldState.ACTIVE && hold.getExpiresAt().isAfter(now))
                .mapToInt(hold -> hold.getRequestedTicketCount() == null ? 0 : hold.getRequestedTicketCount()).sum();
        int confirmedHolds = countable.stream().filter(hold -> hold.getState() == CapacityHoldState.CONFIRMED)
                .mapToInt(hold -> hold.getRequestedTicketCount() == null ? 0 : hold.getRequestedTicketCount()).sum();
        int confirmedTickets = entitlementRepository.findActiveSeatsForSessionForShare(session.getId()).size();
        return session.getCapacity() - activeHolds - Math.max(confirmedHolds, confirmedTickets);
    }

    /**
     * The booking line's committed seat follows the ticket: one seat leaves the source hold and one is
     * committed on the target, so the capacity reads stay consistent with the entitlements.
     */
    private void moveCommittedSeat(String bookingReference, WorkshopSession source, WorkshopSession target) {
        List<CapacityHold> sourceHolds = capacityHoldRepository
                .findConfirmedForBookingAndSessionWithLock(bookingReference, source.getId());
        for (CapacityHold hold : sourceHolds) {
            int remaining = (hold.getRequestedTicketCount() == null ? 0 : hold.getRequestedTicketCount()) - 1;
            if (remaining <= 0) {
                hold.setRequestedTicketCount(0);
                hold.setState(CapacityHoldState.RELEASED);
            } else {
                hold.setRequestedTicketCount(remaining);
            }
            capacityHoldRepository.save(hold);
            break;
        }
        List<CapacityHold> targetHolds = capacityHoldRepository
                .findConfirmedForBookingAndSessionWithLock(bookingReference, target.getId());
        if (!targetHolds.isEmpty()) {
            CapacityHold hold = targetHolds.getFirst();
            hold.setRequestedTicketCount((hold.getRequestedTicketCount() == null ? 0
                    : hold.getRequestedTicketCount()) + 1);
            capacityHoldRepository.save(hold);
            return;
        }
        capacityHoldRepository.save(CapacityHold.builder()
                .reference(UUID.randomUUID().toString())
                .session(target)
                .bookingReference(bookingReference)
                .requestedTicketCount(1)
                .state(CapacityHoldState.CONFIRMED)
                .expiresAt(target.getScheduledEndAt())
                .build());
    }

    private List<Integer> failingLinesFor(Map<Integer, List<Move>> movesByLine, Long sessionId) {
        return movesByLine.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(move -> move.target().getId().equals(sessionId)))
                .map(Map.Entry::getKey).sorted().toList();
    }

    private TicketSessionReplacementOutcome finish(ClaimResult claim, TicketSessionReplacementOutcome outcome) {
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(),
                outcome.reason(), null);
        return outcome;
    }

    private TicketSessionReplacementOutcome reconstruct(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return TicketSessionReplacementOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        try {
            if (record.getRejectionReason() != null) {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            }
        } catch (IllegalArgumentException ignored) {
            // keep INVALID_STATE
        }
        return TicketSessionReplacementOutcome.rejected(reason, List.of());
    }

    private void audit(ReplaceTicketSessionsCommand command, int movedTickets, String outcome) {
        auditEventRepository.save(OperationsAuditEvent.builder()
                .action(ACTION_REPLACE_TICKET_SESSIONS)
                .outcome(outcome)
                .correlationId(command.correlationReference())
                .metadata("{\"type\":\"" + command.type() + "\",\"movedTickets\":" + movedTickets + "}")
                .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** One (ticket, source -> target) move of the selection. */
    private record Move(AttendanceTicket ticket, TicketSessionEntitlement entitlement,
                        WorkshopSession source, WorkshopSession target) {
    }
}
