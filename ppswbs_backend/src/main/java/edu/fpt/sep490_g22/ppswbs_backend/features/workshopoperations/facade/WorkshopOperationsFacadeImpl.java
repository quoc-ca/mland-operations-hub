package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceTicketService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
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
    private final CapacityHoldService capacityHoldService;
    private final AttendanceTicketService attendanceTicketService;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;

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
    @Transactional(readOnly = true)
    public PublishedPackageSelectionSnapshot getPublishedPackageSelection(PublishedPackageSelectionQuery query) {
        Long packageId = packageReservationPolicy.parsePackageReference(query.packageReference());
        if (packageId == null) {
            return null;
        }
        return packageReservationPolicy.readPublishedSnapshot(packageId);
    }

    @Override
    @Transactional
    public CapacityHoldOutcome reserveBookingCapacity(ReserveBookingCapacityCommand command) {
        return capacityHoldService.reserveBookingCapacity(command,
                () -> packageReservationPolicy.validate(command));
    }

    @Override
    @Transactional
    public CapacityCommitOutcome commitBookingHolds(CommitBookingHoldsCommand command) {
        CapacityCommitOutcome outcome = capacityHoldService.commitBookingHolds(command);
        if (outcome.status() == OutcomeStatus.ACCEPTED) {
            attendanceTicketService.issueTicketsForBooking(command.bookingReference(), command.expectedHoldReferences());
        }
        return outcome;
    }

    @Override
    @Transactional
    public CapacityReleaseOutcome releaseBookingHolds(ReleaseBookingHoldsCommand command) {
        return capacityHoldService.releaseBookingHolds(command);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverySafeTicketSet getBookingTicketProjection(BookingTicketProjectionQuery query) {
        return attendanceTicketService.getBookingTicketProjection(query.bookingReference());
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
            if (entitlements.isEmpty()) {
                return new SettlementReadiness(false);
            }
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
        String fingerprint = command.bookingReference() + "|" + command.completeTargetSessionReferences().stream()
                .map(UUID::toString).sorted().toList();

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

        List<String> targetRefs = command.completeTargetSessionReferences().stream().map(UUID::toString).toList();
        Set<String> targetRefSet = new HashSet<>(targetRefs);
        if (targetRefs.isEmpty() || targetRefSet.size() != targetRefs.size()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        List<AttendanceTicket> observedTickets = ticketRepository.findByBookingReference(command.bookingReference());
        if (observedTickets.isEmpty()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Set<String> observedSourceRefs = new TreeSet<>();
        for (AttendanceTicket ticket : observedTickets) {
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketId(ticket.getId());
            entitlements.stream().map(e -> e.getSession().getReference()).forEach(observedSourceRefs::add);
        }
        if (observedSourceRefs.isEmpty()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Set<String> refsToLock = new TreeSet<>(observedSourceRefs);
        refsToLock.addAll(targetRefSet);
        List<WorkshopSession> lockedSessions = sessionRepository.findByReferenceInWithLock(refsToLock);
        if (lockedSessions.size() != refsToLock.size()) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }
        Map<String, WorkshopSession> sessionsByReference = new HashMap<>();
        lockedSessions.forEach(s -> sessionsByReference.put(s.getReference(), s));

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReferenceWithLock(command.bookingReference());
        if (tickets.size() != observedTickets.size()
                || !tickets.stream().map(AttendanceTicket::getId).collect(java.util.stream.Collectors.toSet())
                .equals(observedTickets.stream().map(AttendanceTicket::getId).collect(java.util.stream.Collectors.toSet()))) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Map<Long, List<TicketSessionEntitlement>> entitlementsByTicket = new HashMap<>();
        Set<String> currentSourceRefs = new TreeSet<>();
        for (AttendanceTicket ticket : tickets) {
            if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
            List<TicketSessionEntitlement> entitlements = entitlementRepository.findByTicketIdWithLock(ticket.getId());
            entitlementsByTicket.put(ticket.getId(), entitlements);
            entitlements.stream().map(e -> e.getSession().getReference()).forEach(currentSourceRefs::add);
        }
        if (!currentSourceRefs.equals(observedSourceRefs)) {
            RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Map<String, Integer> currentBookingSeats = new HashMap<>();
        for (List<TicketSessionEntitlement> entitlements : entitlementsByTicket.values()) {
            for (TicketSessionEntitlement entitlement : entitlements) {
                currentBookingSeats.merge(entitlement.getSession().getReference(), 1, Integer::sum);
            }
        }

        Instant now = Instant.now();
        int participantCount = (int) tickets.stream()
                .filter(t -> t.getValidityState() == TicketValidityState.ACTIVE).count();

        Set<String> removedSourceRefs = new HashSet<>(currentSourceRefs);
        removedSourceRefs.removeAll(targetRefSet);
        for (String sourceRef : removedSourceRefs) {
            WorkshopSession source = sessionsByReference.get(sourceRef);
            if (source.getScheduledStartAt() == null || !source.getScheduledStartAt().isAfter(now)
                    || source.getState() == SessionState.IN_PROGRESS
                    || source.getState() == SessionState.ATTENDANCE_FINALIZED
                    || source.getState() == SessionState.CANCELLATION_REQUESTED
                    || source.getState() == SessionState.CANCELLED
                    || entitlementsByTicket.values().stream().flatMap(Collection::stream)
                    .filter(e -> e.getSession().getReference().equals(sourceRef))
                    .anyMatch(e -> e.getAttendanceState() != AttendanceState.NOT_CHECKED_IN)) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        List<WorkshopSession> targetSessions = targetRefs.stream().map(sessionsByReference::get).toList();

        for (WorkshopSession target : targetSessions) {
            if (target.getState() != SessionState.PUBLISHED || target.getScheduledStartAt() == null
                    || !target.getScheduledStartAt().isAfter(now)) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
            int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(target.getId(), now);
            int confirmed = entitlementRepository.countActiveConfirmedSeatsForSession(target.getId());
            int remainingCapacity = target.getCapacity() - activeHolds - confirmed;
            int existingBookingSeats = currentBookingSeats.getOrDefault(target.getReference(), 0);
            if (remainingCapacity <= 0 || remainingCapacity + existingBookingSeats < participantCount) {
                RequiredSessionReplacementOutcome outcome = RequiredSessionReplacementOutcome.rejected(RejectionReason.INSUFFICIENT_CAPACITY);
                idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
                return outcome;
            }
        }

        Set<String> changedSessionRefs = new HashSet<>(removedSourceRefs);
        for (AttendanceTicket ticket : tickets) {
            List<TicketSessionEntitlement> oldEntitlements = entitlementsByTicket.get(ticket.getId());
            List<TicketSessionEntitlement> removedEntitlements = oldEntitlements.stream()
                    .filter(e -> !targetRefSet.contains(e.getSession().getReference())).toList();
            entitlementRepository.deleteAll(removedEntitlements);
            Set<String> existingRefs = oldEntitlements.stream()
                    .map(e -> e.getSession().getReference()).collect(java.util.stream.Collectors.toSet());

            for (WorkshopSession newSession : targetSessions) {
                if (existingRefs.contains(newSession.getReference())) {
                    continue;
                }
                TicketSessionEntitlement newEntitlement = TicketSessionEntitlement.builder()
                    .ticket(ticket)
                    .session(newSession)
                    .attendanceState(AttendanceState.NOT_CHECKED_IN)
                    .build();
                entitlementRepository.save(newEntitlement);
                changedSessionRefs.add(newSession.getReference());
            }
        }

        changedSessionRefs.stream().map(sessionsByReference::get).filter(Objects::nonNull)
                .forEach(session -> otaSnapshotService.publishForObservableChange(
                        session, command.operationReference(), now));

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

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReferenceWithLock(command.bookingReference());
        if (tickets.isEmpty()) {
            RefundConsequenceOutcome outcome = RefundConsequenceOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        Instant now = Instant.now();
        Map<String, WorkshopSession> affectedSessions = new HashMap<>();
        for (AttendanceTicket ticket : tickets) {
            for (TicketSessionEntitlement entitlement : entitlementRepository.findByTicketIdWithLock(ticket.getId())) {
                affectedSessions.put(entitlement.getSession().getReference(), entitlement.getSession());
            }
            ticket.setValidityState(TicketValidityState.REFUNDED);
            ticketRepository.save(ticket);
        }

        affectedSessions.values().forEach(session -> otaSnapshotService.publishForObservableChange(
                session, command.operationReference(), now));

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
