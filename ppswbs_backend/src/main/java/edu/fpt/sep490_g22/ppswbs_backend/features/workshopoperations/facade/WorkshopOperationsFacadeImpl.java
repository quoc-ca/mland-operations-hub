package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopOperationsFacadeImpl implements WorkshopOperationsFacade {

    private final WorkshopSessionRepository sessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final CancellationHandoffRepository cancellationHandoffRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query) {
        String sessionRef = query.sessionReference().toString();
        Optional<WorkshopSession> sessionOpt = sessionRepository.findByReference(sessionRef);
        if (sessionOpt.isEmpty()) {
            return new SaleEligibilitySnapshot(false, "NOT_FOUND", null, null, null, 0);
        }

        WorkshopSession session = sessionOpt.get();
        Instant now = Instant.now();
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session.getId(), now);
        int confirmedSeats = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
        int remainingCapacity = Math.max(0, session.getCapacity() - activeHolds - confirmedSeats);

        boolean selectable = session.getState() == SessionState.PUBLISHED && remainingCapacity > 0;
        Long locationRef = session.getLocation() != null ? session.getLocation().getId() : null;

        return new SaleEligibilitySnapshot(
            selectable,
            session.getState().name(),
            locationRef,
            session.getScheduledStartAt(),
            session.getScheduledEndAt(),
            remainingCapacity
        );
    }

    @Override
    @Transactional
    public CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command) {
        String action = "FACADE_RESERVE_CAPACITY";
        String fingerprint = command.bookingReference() + "|" + command.orderedRequiredSessionReferences() + "|" + command.participantCount();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
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

        List<String> sessionRefs = command.orderedRequiredSessionReferences().stream()
            .map(UUID::toString)
            .toList();

        List<WorkshopSession> sessions = sessionRepository.findByReferenceInWithLock(sessionRefs);
        if (sessions.size() != sessionRefs.size()) {
            CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.SALE_NOT_ELIGIBLE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Instant now = Instant.now();
        for (WorkshopSession session : sessions) {
            if (session.getState() != SessionState.PUBLISHED) {
                CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.SALE_NOT_ELIGIBLE);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
            int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session.getId(), now);
            int confirmedSeats = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
            int remaining = session.getCapacity() - activeHolds - confirmedSeats;
            if (remaining < command.participantCount()) {
                CapacityHoldOutcome outcome = CapacityHoldOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
        List<UUID> holdRefs = new ArrayList<>();

        for (WorkshopSession session : sessions) {
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

        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", sessions.getFirst().getId());

        CapacityHoldOutcome outcome = CapacityHoldOutcome.accepted(holdRefs, expiresAt);
        String payload = buildHoldPayload(holdRefs, expiresAt);
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, payload);
        return outcome;
    }

    @Override
    @Transactional
    public CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command) {
        String action = "FACADE_COMMIT_HOLDS";
        String fingerprint = command.bookingReference() + "|" + command.expectedHoldReferences();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
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

        List<String> holdRefs = command.expectedHoldReferences().stream()
            .map(UUID::toString)
            .toList();

        List<CapacityHold> holds = capacityHoldRepository.findByReferenceInWithLock(holdRefs);
        if (holds.size() != holdRefs.size()) {
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
        }

        for (CapacityHold hold : holds) {
            hold.setState(CapacityHoldState.CONFIRMED);
            capacityHoldRepository.save(hold);
        }

        List<AttendanceTicket> existingTickets = ticketRepository.findByBookingReference(command.bookingReference());
        int participantCount = holds.getFirst().getRequestedTicketCount();

        if (existingTickets.isEmpty()) {
            List<AttendanceTicket> createdTickets = new ArrayList<>();
            for (int i = 1; i <= participantCount; i++) {
                String ticketCode = "TK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String qrRef = "QR-" + UUID.randomUUID();
                AttendanceTicket ticket = AttendanceTicket.builder()
                    .bookingReference(command.bookingReference())
                    .ticketSequence(i)
                    .ticketCode(ticketCode)
                    .qrReference(qrRef)
                    .validityState(TicketValidityState.ACTIVE)
                    .build();
                ticket = ticketRepository.save(ticket);
                createdTickets.add(ticket);
            }

            for (CapacityHold hold : holds) {
                for (AttendanceTicket ticket : createdTickets) {
                    TicketSessionEntitlement entitlement = TicketSessionEntitlement.builder()
                        .ticket(ticket)
                        .session(hold.getSession())
                        .attendanceState(AttendanceState.NOT_CHECKED_IN)
                        .build();
                    entitlementRepository.save(entitlement);
                }
            }
        }

        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", holds.getFirst().getSession().getId());

        CapacityCommitOutcome outcome = CapacityCommitOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    @Override
    @Transactional
    public CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command) {
        String action = "FACADE_RELEASE_HOLDS";
        String fingerprint = command.bookingReference() + "|" + command.expectedHoldReferences();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
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

        List<String> holdRefs = command.expectedHoldReferences().stream()
            .map(UUID::toString)
            .toList();

        List<CapacityHold> holds = capacityHoldRepository.findByReferenceInWithLock(holdRefs);
        for (CapacityHold hold : holds) {
            if (hold.getState() == CapacityHoldState.ACTIVE) {
                hold.setState(CapacityHoldState.RELEASED);
                capacityHoldRepository.save(hold);
            }
        }

        Long sessionId = holds.isEmpty() ? null : holds.getFirst().getSession().getId();
        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", sessionId);

        CapacityReleaseOutcome outcome = CapacityReleaseOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query) {
        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference(query.bookingReference());
        List<DeliverySafeTicket> projections = new ArrayList<>();

        for (AttendanceTicket ticket : tickets) {
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
            List<SessionEntitlementProjection> sessionProjections = entitlements.stream()
                .map(e -> new SessionEntitlementProjection(
                    e.getSession().getLocation() != null ? e.getSession().getLocation().getId() : null,
                    e.getSession().getScheduledStartAt(),
                    e.getSession().getScheduledEndAt()
                ))
                .toList();

            projections.add(new DeliverySafeTicket(
                UUID.fromString(ticket.getReference()),
                ticket.getTicketCode(),
                sessionProjections
            ));
        }

        return new DeliverySafeTicketSet(projections);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementReadiness getSettlementReadiness(BookingSettlementReadinessQuery query) {
        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference(query.bookingReference());
        if (tickets.isEmpty()) {
            return new SettlementReadiness(false);
        }

        for (AttendanceTicket ticket : tickets) {
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
            for (TicketSessionEntitlement e : entitlements) {
                if (e.getSession().getState() != SessionState.ATTENDANCE_FINALIZED) {
                    return new SettlementReadiness(false);
                }
            }
        }

        return new SettlementReadiness(true);
    }

    @Override
    @Transactional
    public RequiredSessionReplacementOutcome replaceRequiredSessions(ReplaceRequiredSessionsCommand command) {
        String action = "FACADE_REPLACE_SESSIONS";
        String fingerprint = command.bookingReference() + "|" + command.completeTargetSessionReferences();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return RequiredSessionReplacementOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructReplaceOutcome(claim.getCompletedRecord());
        }

        List<String> targetRefs = command.completeTargetSessionReferences().stream()
            .map(UUID::toString)
            .toList();

        List<WorkshopSession> targetSessions = sessionRepository.findByReferenceInWithLock(targetRefs);
        if (targetSessions.size() != targetRefs.size()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference(command.bookingReference());
        if (tickets.isEmpty()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Instant now = Instant.now();
        int participantCount = tickets.size();

        for (WorkshopSession target : targetSessions) {
            if (target.getState() != SessionState.PUBLISHED || target.getScheduledStartAt().isBefore(now)) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
            int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(target.getId(), now);
            int confirmed = entitlementRepository.countActiveConfirmedSeatsForSession(target.getId());
            if (target.getCapacity() - activeHolds - confirmed < participantCount) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        for (AttendanceTicket ticket : tickets) {
            List<TicketSessionEntitlement> oldEntitlements = entitlementRepository.findByTicketId(ticket.getId());
            entitlementRepository.deleteAll(oldEntitlements);

            for (WorkshopSession newSession : targetSessions) {
                TicketSessionEntitlement newEntitlement = TicketSessionEntitlement.builder()
                    .ticket(ticket)
                    .session(newSession)
                    .attendanceState(AttendanceState.NOT_CHECKED_IN)
                    .build();
                entitlementRepository.save(newEntitlement);
            }
        }

        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", targetSessions.getFirst().getId());

        RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    @Override
    @Transactional
    public RefundConsequenceOutcome applyVerifiedRefundConsequence(ApplyVerifiedRefundConsequenceCommand command) {
        String action = "FACADE_APPLY_REFUND";
        String fingerprint = command.bookingReference() + "|" + command.verifiedRefundReference();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return RefundConsequenceOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return RefundConsequenceOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructRefundOutcome(claim.getCompletedRecord());
        }

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReference(command.bookingReference());
        if (tickets.isEmpty()) {
            RefundConsequenceOutcome outcome = RefundConsequenceOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        for (AttendanceTicket ticket : tickets) {
            ticket.setValidityState(TicketValidityState.REFUNDED);
            ticketRepository.save(ticket);
        }

        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", null);

        RefundConsequenceOutcome outcome = RefundConsequenceOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    @Override
    @Transactional
    public CancellationCompletionOutcome completeCancellationHandoff(CompleteCancellationHandoffCommand command) {
        String action = "FACADE_COMPLETE_CANCELLATION";
        String fingerprint = command.sessionReference() + "|" + command.cancellationCaseReference();

        ClaimResult claim = idempotencyManager.acquireClaim(
            action, command.operationReference(), command.correlationReference(), fingerprint
        );

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return CancellationCompletionOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return CancellationCompletionOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructCancellationOutcome(claim.getCompletedRecord());
        }

        String sessionRef = command.sessionReference().toString();
        Optional<WorkshopSession> sessionOpt = sessionRepository.findByReferenceWithLock(sessionRef);
        if (sessionOpt.isEmpty()) {
            CancellationCompletionOutcome outcome = CancellationCompletionOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        WorkshopSession session = sessionOpt.get();
        session.setState(SessionState.CANCELLED);
        sessionRepository.save(session);

        Optional<CancellationHandoff> handoffOpt = cancellationHandoffRepository
            .findByCancellationCaseReference(command.cancellationCaseReference());
        if (handoffOpt.isPresent()) {
            CancellationHandoff handoff = handoffOpt.get();
            handoff.setStatus(CancellationHandoffStatus.RESOLVED);
            handoff.setResolvedAt(Instant.now());
            cancellationHandoffRepository.save(handoff);
        }

        recordBusinessAudit(action, command.correlationReference(), "SUCCESS", session.getId());

        CancellationCompletionOutcome outcome = CancellationCompletionOutcome.accepted();
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), null, null);
        return outcome;
    }

    private void recordBusinessAudit(String action, String correlationReference, String outcome, Long sessionId) {
        OperationsAuditEvent event = OperationsAuditEvent.builder()
            .sessionId(sessionId)
            .action(action)
            .outcome(outcome)
            .correlationId(correlationReference)
            .metadata(null)
            .build();
        auditEventRepository.save(event);
    }

    private String buildHoldPayload(List<UUID> holdReferences, Instant expiresAt) {
        Map<String, Object> map = new HashMap<>();
        map.put("holdReferences", holdReferences.stream().map(UUID::toString).toList());
        map.put("expiresAt", expiresAt.toString());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private JsonNode parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
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

    private RequiredSessionReplacementOutcome reconstructReplaceOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return RequiredSessionReplacementOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {}
        }
        return RequiredSessionReplacementOutcome.rejected(reason);
    }

    private RefundConsequenceOutcome reconstructRefundOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return RefundConsequenceOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {}
        }
        return RefundConsequenceOutcome.rejected(reason);
    }

    private CancellationCompletionOutcome reconstructCancellationOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            return CancellationCompletionOutcome.accepted();
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {}
        }
        return CancellationCompletionOutcome.rejected(reason);
    }
}
