package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.CapacityHoldRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing lock-protected Capacity Holds, commit, release, expiry sweeping,
 * and capacity reduction protection according to 003.2 Workshop Operations specifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityHoldService {

    private static final String ACTION_RESERVE = "FACADE_RESERVE_CAPACITY";
    private static final String ACTION_COMMIT = "FACADE_COMMIT_HOLDS";
    private static final String ACTION_RELEASE = "FACADE_RELEASE_HOLDS";

    private final WorkshopSessionRepository sessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final ObjectMapper objectMapper;

    /**
     * Reserves capacity for one or multiple required sessions (all-or-nothing).
     * Locks session rows, enforces PUBLISHED state, rejects on SALES_PAUSED or insufficient capacity,
     * and uses the durable idempotency ledger.
     */
    @Transactional
    public CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command) {
        String fingerprint = command.bookingReference() + "|" + command.orderedRequiredSessionReferences() + "|" + command.participantCount();

        ClaimResult claim = idempotencyManager.acquireClaim(
            ACTION_RESERVE, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return CapacityHoldOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return CapacityHoldOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructHoldOutcome(claim.getCompletedRecord());
        }

        if (command.participantCount() < 1) {
            CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        // Lock sessions deterministically sorted by reference to prevent application deadlocks
        List<String> sortedRefs = command.orderedRequiredSessionReferences().stream()
            .map(UUID::toString)
            .sorted()
            .toList();

        List<WorkshopSession> lockedSessions = sessionRepository.findByReferenceInWithLock(sortedRefs);
        if (lockedSessions.size() != command.orderedRequiredSessionReferences().size()) {
            CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.SALE_NOT_ELIGIBLE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Map<String, WorkshopSession> sessionMap = lockedSessions.stream()
            .collect(Collectors.toMap(WorkshopSession::getReference, s -> s));

        List<WorkshopSession> orderedSessions = new ArrayList<>();
        for (UUID ref : command.orderedRequiredSessionReferences()) {
            WorkshopSession s = sessionMap.get(ref.toString());
            if (s == null) {
                CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.SALE_NOT_ELIGIBLE);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
            orderedSessions.add(s);
        }

        Instant now = Instant.now();

        // All-or-nothing validation across all sessions
        for (WorkshopSession session : orderedSessions) {
            // Only PUBLISHED allows new reservations; SALES_PAUSED rejects new holds
            if (session.getState() != SessionState.PUBLISHED) {
                CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.SALE_NOT_ELIGIBLE);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }

            int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session.getId(), now);
            int confirmedHolds = capacityHoldRepository.sumConfirmedHoldsForSession(session.getId());
            int confirmedTickets = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
            int confirmedSeats = Math.max(confirmedHolds, confirmedTickets);
            int remaining = session.getCapacity() - activeHolds - confirmedSeats;

            if (remaining < command.participantCount()) {
                CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        // All sessions qualified: create active holds
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        List<UUID> holdRefs = new ArrayList<>();

        for (WorkshopSession session : orderedSessions) {
            String holdRef = UUID.randomUUID().toString();
            CapacityHold hold = CapacityHold.builder()
                .reference(holdRef)
                .session(session)
                .bookingReference(command.bookingReference())
                .requestedTicketCount(command.participantCount())
                .state(CapacityHoldState.ACTIVE)
                .expiresAt(expiresAt)
                .build();
            capacityHoldRepository.save(hold);
            holdRefs.add(UUID.fromString(holdRef));
        }

        recordBusinessAudit(ACTION_RESERVE, command.correlationReference(), "SUCCESS", orderedSessions.getFirst().getId());

        CapacityHoldOutcome outcome = CapacityHoldOutcome.accepted(holdRefs, expiresAt);
        String payload = buildHoldPayload(holdRefs, expiresAt);
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, payload);
        return outcome;
    }

    /**
     * Commits active, unexpired holds for a confirmed booking.
     * All-or-nothing: if any expected hold is missing, expired, or non-active, none are committed.
     * Permits commitment for sessions that entered SALES_PAUSED after the hold was created.
     */
    @Transactional
    public CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command) {
        String fingerprint = command.bookingReference() + "|" + command.expectedHoldReferences();

        ClaimResult claim = idempotencyManager.acquireClaim(
            ACTION_COMMIT, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return CapacityCommitOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return CapacityCommitOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructCommitOutcome(claim.getCompletedRecord());
        }

        List<String> sortedHoldRefs = command.expectedHoldReferences().stream()
            .map(UUID::toString)
            .sorted()
            .toList();

        List<CapacityHold> holds = capacityHoldRepository.findByReferenceInWithLock(sortedHoldRefs);
        if (holds.size() != command.expectedHoldReferences().size()) {
            CapacityCommitOutcome outcome = CapacityCommitOutcome.rejected(RejectionReason.HOLD_MISSING_OR_EXPIRED);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Instant now = Instant.now();
        for (CapacityHold hold : holds) {
            if (!command.bookingReference().equals(hold.getBookingReference())
                || hold.getState() != CapacityHoldState.ACTIVE
                || hold.getExpiresAt().isBefore(now)) {
                CapacityCommitOutcome outcome = CapacityCommitOutcome.rejected(RejectionReason.HOLD_MISSING_OR_EXPIRED);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }

            // Session must not be cancelled or attendance finalized
            SessionState sessionState = hold.getSession().getState();
            if (sessionState == SessionState.CANCELLED || sessionState == SessionState.ATTENDANCE_FINALIZED) {
                CapacityCommitOutcome outcome = CapacityCommitOutcome.rejected(RejectionReason.INVALID_STATE);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        // All holds valid and active: commit all
        for (CapacityHold hold : holds) {
            hold.setState(CapacityHoldState.CONFIRMED);
            capacityHoldRepository.save(hold);
        }

        recordBusinessAudit(ACTION_COMMIT, command.correlationReference(), "SUCCESS", holds.getFirst().getSession().getId());

        CapacityCommitOutcome outcome = CapacityCommitOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    /**
     * Releases active holds idempotently.
     * Retries or late commands do not alter already released/expired holds or change capacity a second time.
     */
    @Transactional
    public CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command) {
        String fingerprint = command.bookingReference() + "|" + command.expectedHoldReferences();

        ClaimResult claim = idempotencyManager.acquireClaim(
            ACTION_RELEASE, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return CapacityReleaseOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return CapacityReleaseOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructReleaseOutcome(claim.getCompletedRecord());
        }

        List<String> sortedHoldRefs = command.expectedHoldReferences().stream()
            .map(UUID::toString)
            .sorted()
            .toList();

        List<CapacityHold> holds = capacityHoldRepository.findByReferenceInWithLock(sortedHoldRefs);
        for (CapacityHold hold : holds) {
            if (hold.getState() == CapacityHoldState.ACTIVE) {
                hold.setState(CapacityHoldState.RELEASED);
                capacityHoldRepository.save(hold);
            }
        }

        Long sessionId = holds.isEmpty() ? null : holds.getFirst().getSession().getId();
        recordBusinessAudit(ACTION_RELEASE, command.correlationReference(), "SUCCESS", sessionId);

        CapacityReleaseOutcome outcome = CapacityReleaseOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    /**
     * Sweeps active holds past their expiresAt timestamp to EXPIRED status.
     * Idempotent: repeated sweeps do not alter already expired records.
     *
     * @return count of holds expired in this sweep
     */
    @Transactional
    public int expirePendingHolds() {
        Instant now = Instant.now();
        List<CapacityHold> expiredList = capacityHoldRepository.findExpiredActiveHolds(now);
        int count = 0;
        for (CapacityHold hold : expiredList) {
            if (hold.getState() == CapacityHoldState.ACTIVE && !hold.getExpiresAt().isAfter(now)) {
                hold.setState(CapacityHoldState.EXPIRED);
                capacityHoldRepository.save(hold);
                count++;
            }
        }
        if (count > 0) {
            log.info("Expired {} pending capacity holds past expiresAt {}", count, now);
        }
        return count;
    }

    /**
     * Validates capacity reduction: rejects reducing capacity below active unexpired holds + confirmed seats.
     * Throws WorkshopCapacityConflictException if newCapacity < committed seats.
     */
    @Transactional(readOnly = true)
    public void validateCapacityReduction(Long sessionId, int newCapacity) {
        Instant now = Instant.now();
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(sessionId, now);
        int confirmedHolds = capacityHoldRepository.sumConfirmedHoldsForSession(sessionId);
        int confirmedTickets = entitlementRepository.countActiveConfirmedSeatsForSession(sessionId);
        int confirmedSeats = Math.max(confirmedHolds, confirmedTickets);
        int committed = activeHolds + confirmedSeats;

        if (newCapacity < committed) {
            throw new WorkshopCapacityConflictException(
                "Cannot reduce capacity to " + newCapacity + ": session has " + committed +
                " committed seats (" + activeHolds + " active holds, " + confirmedSeats + " confirmed seats)"
            );
        }
    }

    /**
     * Calculates remaining capacity for a session considering active unexpired holds and confirmed seats.
     */
    @Transactional(readOnly = true)
    public int getRemainingCapacity(Long sessionId) {
        WorkshopSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new WorkshopNotFoundException("Session not found: " + sessionId));

        Instant now = Instant.now();
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(sessionId, now);
        int confirmedHolds = capacityHoldRepository.sumConfirmedHoldsForSession(sessionId);
        int confirmedTickets = entitlementRepository.countActiveConfirmedSeatsForSession(sessionId);
        int confirmedSeats = Math.max(confirmedHolds, confirmedTickets);

        return Math.max(0, session.getCapacity() - activeHolds - confirmedSeats);
    }

    private void recordBusinessAudit(String action, String correlationReference, String outcome, Long sessionId) {
        try {
            OperationsAuditEvent event = OperationsAuditEvent.builder()
                .sessionId(sessionId)
                .action(action)
                .outcome(outcome)
                .correlationId(correlationReference)
                .metadata(null)
                .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record facade business audit for action={}: {}", action, e.getMessage());
        }
    }

    private String buildHoldPayload(List<UUID> holdRefs, Instant expiresAt) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("holdReferences", holdRefs);
            map.put("expiresAt", expiresAt.toString());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private CapacityHoldOutcome reconstructHoldOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            List<UUID> holdRefs = new ArrayList<>();
            Instant expiresAt = null;
            if (record.getResponsePayload() != null && !record.getResponsePayload().isBlank()) {
                JsonNode meta = parseMetadata(record.getResponsePayload());
                if (meta != null) {
                    JsonNode refsNode = meta.path("holdReferences");
                    if (refsNode.isArray()) {
                        for (JsonNode r : refsNode) {
                            holdRefs.add(UUID.fromString(r.asText()));
                        }
                    }
                    if (meta.has("expiresAt")) {
                        expiresAt = Instant.parse(meta.path("expiresAt").asText());
                    }
                }
            }
            return CapacityHoldOutcome.accepted(holdRefs, expiresAt);
        } else {
            RejectionReason reason = RejectionReason.INVALID_STATE;
            if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
                try {
                    reason = RejectionReason.valueOf(record.getRejectionReason());
                } catch (IllegalArgumentException ignored) {}
            }
            return CapacityHoldOutcome.rejected(reason);
        }
    }

    private CapacityCommitOutcome reconstructCommitOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return CapacityCommitOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {}
        }
        return CapacityCommitOutcome.rejected(reason);
    }

    private CapacityReleaseOutcome reconstructReleaseOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return CapacityReleaseOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {}
        }
        return CapacityReleaseOutcome.rejected(reason);
    }

    private JsonNode parseMetadata(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                try {
                    return objectMapper.readTree(node.asText());
                } catch (Exception ignored) {
                    return node;
                }
            }
            return node;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
