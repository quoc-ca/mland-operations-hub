package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopBookingPackagePolicySnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopBookingPackagePolicySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.GroupCapacityOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveGroupCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Group capacity reservation (003.2 FR-030, decision 2026-11-12). All lines of a booking group are validated and
 * held in one transaction, all or none. Demand is summed per session across lines before the capacity check, so
 * two lines on the same session cannot jointly exceed it. One ticket takes one seat per session; the hold lifetime
 * is the group hold of 003.1 FR-032 (12 minutes by default).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupCapacityService {

    private static final String ACTION_RESERVE_GROUP = "FACADE_RESERVE_GROUP_CAPACITY";

    private final WorkshopSessionRepository sessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final WorkshopBookingPackagePolicySnapshotRepository bookingPolicySnapshotRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final ObjectMapper objectMapper;

    @Value("${workshop.booking.group-hold-minutes:12}")
    private long holdMinutes = 12;

    @Transactional
    public GroupCapacityOutcome reserveGroupCapacity(ReserveGroupCapacityCommand command) {
        String fingerprint = command.groupReference() + "|" + command.lines().stream()
                .map(l -> l.bookingReference() + ":" + l.packageReference() + ":"
                        + l.orderedRequiredSessionReferences() + ":" + l.ticketCount())
                .toList();
        ClaimResult claim = idempotencyManager.acquireClaim(
                ACTION_RESERVE_GROUP, command.operationReference(), command.correlationReference(), fingerprint);
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return GroupCapacityOutcome.rejected(RejectionReason.INVALID_STATE, List.of());
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return GroupCapacityOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstruct(claim.getCompletedRecord());
        }

        GroupCapacityOutcome rejection = validate(command);
        if (rejection != null) {
            return finish(claim, rejection);
        }

        // Lock every involved session once, in a deterministic order, to avoid application deadlocks.
        TreeSet<String> sortedRefs = new TreeSet<>();
        command.lines().forEach(l -> l.orderedRequiredSessionReferences().forEach(r -> sortedRefs.add(r.toString())));
        List<WorkshopSession> locked = sessionRepository.findByReferenceInWithLock(sortedRefs);
        Map<String, WorkshopSession> sessions = new HashMap<>();
        locked.forEach(s -> sessions.put(s.getReference(), s));

        List<Integer> failing = new ArrayList<>();
        RejectionReason firstReason = null;
        Map<String, Integer> demand = new LinkedHashMap<>();
        for (int i = 0; i < command.lines().size(); i++) {
            var line = command.lines().get(i);
            boolean ok = true;
            for (UUID ref : line.orderedRequiredSessionReferences()) {
                WorkshopSession session = sessions.get(ref.toString());
                if (session == null || session.getState() != SessionState.PUBLISHED) {
                    ok = false;
                    firstReason = firstReason == null ? RejectionReason.SALE_NOT_ELIGIBLE : firstReason;
                } else {
                    demand.merge(session.getReference(), line.ticketCount(), Integer::sum);
                }
            }
            if (!ok) failing.add(i);
        }

        Instant now = Instant.now();
        // Capacity: total demand of the group per session must fit. A line is failing when any of its sessions is short.
        Map<String, Boolean> sessionShort = new HashMap<>();
        for (Map.Entry<String, Integer> e : demand.entrySet()) {
            WorkshopSession session = sessions.get(e.getKey());
            // Locking reads: the caller's transaction may already hold an older REPEATABLE READ snapshot, so a plain
            // count could miss seats another group committed while this one waited for the session lock.
            List<CapacityHold> countable = capacityHoldRepository.findCountableHoldsForSessionForShare(session.getId());
            int activeHolds = countable.stream()
                    .filter(h -> h.getState() == CapacityHoldState.ACTIVE && h.getExpiresAt().isAfter(now))
                    .mapToInt(CapacityHold::getRequestedTicketCount).sum();
            int confirmedHolds = countable.stream().filter(h -> h.getState() == CapacityHoldState.CONFIRMED)
                    .mapToInt(CapacityHold::getRequestedTicketCount).sum();
            int confirmedTickets = entitlementRepository.findActiveSeatsForSessionForShare(session.getId()).size();
            int remaining = session.getCapacity() - activeHolds - Math.max(confirmedHolds, confirmedTickets);
            sessionShort.put(e.getKey(), remaining < e.getValue());
        }
        for (int i = 0; i < command.lines().size(); i++) {
            if (failing.contains(i)) continue;
            for (UUID ref : command.lines().get(i).orderedRequiredSessionReferences()) {
                if (Boolean.TRUE.equals(sessionShort.get(ref.toString()))) {
                    failing.add(i);
                    firstReason = firstReason == null ? RejectionReason.INSUFFICIENT_CAPACITY : firstReason;
                    break;
                }
            }
        }
        if (!failing.isEmpty()) {
            failing.sort(Integer::compare);
            return finish(claim, GroupCapacityOutcome.rejected(firstReason, failing));
        }

        // Everything fits: persist the per-line policy snapshots and one hold per line per session.
        Instant expiresAt = now.plus(holdMinutes, ChronoUnit.MINUTES);
        List<List<UUID>> lineHolds = new ArrayList<>();
        for (var line : command.lines()) {
            ReserveBookingCapacityCommand single = singleLine(command, line);
            WorkshopBookingPackagePolicySnapshot snapshot = packageReservationPolicy.snapshotForBooking(single);
            if (snapshot != null && bookingPolicySnapshotRepository
                    .findByBookingReference(line.bookingReference()).isEmpty()) {
                bookingPolicySnapshotRepository.save(snapshot);
            }
            List<UUID> holdRefs = new ArrayList<>();
            for (UUID ref : line.orderedRequiredSessionReferences()) {
                String holdRef = UUID.randomUUID().toString();
                capacityHoldRepository.save(CapacityHold.builder()
                        .reference(holdRef)
                        .session(sessions.get(ref.toString()))
                        .bookingReference(line.bookingReference())
                        .requestedTicketCount(line.ticketCount())
                        .state(CapacityHoldState.ACTIVE)
                        .expiresAt(expiresAt)
                        .build());
                holdRefs.add(UUID.fromString(holdRef));
            }
            lineHolds.add(holdRefs);
        }
        for (WorkshopSession session : sessions.values()) {
            otaSnapshotService.publishForObservableChange(session, UUID.randomUUID().toString(), now);
        }
        audit(command.correlationReference(), "SUCCESS", locked.isEmpty() ? null : locked.getFirst().getId());
        return finish(claim, GroupCapacityOutcome.accepted(lineHolds, expiresAt));
    }

    /** Structural and package-rule checks per line; returns the rejection (with failing lines) or null. */
    private GroupCapacityOutcome validate(ReserveGroupCapacityCommand command) {
        if (command.lines().isEmpty()) {
            return GroupCapacityOutcome.rejected(RejectionReason.INVALID_STATE, List.of());
        }
        List<Integer> failing = new ArrayList<>();
        RejectionReason first = null;
        java.util.Set<String> references = new java.util.HashSet<>();
        for (int i = 0; i < command.lines().size(); i++) {
            var line = command.lines().get(i);
            RejectionReason reason = null;
            if (line.ticketCount() < 1 || !references.add(line.bookingReference())
                    || line.orderedRequiredSessionReferences().isEmpty()
                    || new java.util.HashSet<>(line.orderedRequiredSessionReferences()).size()
                    != line.orderedRequiredSessionReferences().size()) {
                reason = RejectionReason.INVALID_STATE;
            } else {
                reason = packageReservationPolicy.validate(singleLine(command, line));
            }
            if (reason != null) {
                failing.add(i);
                first = first == null ? reason : first;
            }
        }
        return failing.isEmpty() ? null : GroupCapacityOutcome.rejected(first, failing);
    }

    private static ReserveBookingCapacityCommand singleLine(ReserveGroupCapacityCommand command,
                                                            ReserveGroupCapacityCommand.Line line) {
        return new ReserveBookingCapacityCommand(line.bookingReference(), line.packageReference(),
                line.orderedRequiredSessionReferences(), line.ticketCount(),
                command.operationReference() + ":" + line.bookingReference(), command.correlationReference());
    }

    private GroupCapacityOutcome finish(ClaimResult claim, GroupCapacityOutcome outcome) {
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(),
                outcome.reason(), payload(outcome));
        return outcome;
    }

    private String payload(GroupCapacityOutcome outcome) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("lineHoldReferences", outcome.lineHoldReferences());
            map.put("failingLines", outcome.failingLines());
            if (outcome.expiresAt() != null) map.put("expiresAt", outcome.expiresAt().toString());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private GroupCapacityOutcome reconstruct(
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord record) {
        List<List<UUID>> lines = new ArrayList<>();
        List<Integer> failing = new ArrayList<>();
        Instant expiresAt = null;
        try {
            if (record.getResponsePayload() != null && !record.getResponsePayload().isBlank()) {
                JsonNode meta = objectMapper.readTree(record.getResponsePayload());
                for (JsonNode line : meta.path("lineHoldReferences")) {
                    List<UUID> refs = new ArrayList<>();
                    line.forEach(r -> refs.add(UUID.fromString(r.asText())));
                    lines.add(refs);
                }
                meta.path("failingLines").forEach(n -> failing.add(n.asInt()));
                if (meta.has("expiresAt")) expiresAt = Instant.parse(meta.path("expiresAt").asText());
            }
        } catch (JsonProcessingException ignored) {
            // fall through with what was read
        }
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return GroupCapacityOutcome.accepted(lines, expiresAt);
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        try {
            if (record.getRejectionReason() != null) reason = RejectionReason.valueOf(record.getRejectionReason());
        } catch (IllegalArgumentException ignored) {
            // keep INVALID_STATE
        }
        return GroupCapacityOutcome.rejected(reason, failing);
    }

    private void audit(String correlationReference, String outcome, Long sessionId) {
        try {
            auditEventRepository.save(OperationsAuditEvent.builder().sessionId(sessionId)
                    .action(ACTION_RESERVE_GROUP).outcome(outcome).correlationId(correlationReference).build());
        } catch (Exception e) {
            log.warn("Failed to record group capacity audit: {}", e.getMessage());
        }
    }
}
