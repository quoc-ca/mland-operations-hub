package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceTicketService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.CapacityHoldService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.GroupCapacityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionWindowPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopOperationsFacadeImpl implements WorkshopOperationsFacade {

    private static final String ACTION_OTA_ACCEPT = "FACADE_ACCEPT_OTA_BOOKING";

    private final WorkshopSessionRepository sessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final CancellationHandoffRepository cancellationHandoffRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final ObjectMapper objectMapper;
    private final CapacityHoldService capacityHoldService;
    private final GroupCapacityService groupCapacityService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketQrReissueService ticketQrReissueService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketCancellationService ticketCancellationService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketSessionReplacementService ticketSessionReplacementService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.EquivalentReplacementSessionService equivalentReplacementSessionService;
    private final AttendanceTicketService attendanceTicketService;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceOutcomeService bookingAttendanceOutcomeService;
    private final WorkshopAddOnService addOnService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository staffAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public WorkshopAddOnPriceSnapshot getWorkshopAddOnPrice(WorkshopAddOnPriceQuery query) {
        if (query == null) return null;
        return addOnService.getEligibleSnapshot(query.packageReference(), query.locationReference(), query.addOnReference());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<WorkshopAddOnPriceSnapshot> listWorkshopAddOns(WorkshopAddOnListQuery query) {
        if (query == null) return java.util.List.of();
        return addOnService.listEligibleSnapshots(query.packageReference(), query.locationReference());
    }

    @Override
    @Transactional(readOnly = true)
    public SaleEligibilitySnapshot getSaleEligibility(SaleEligibilityQuery query) {
        String sessionRef = query.sessionReference().toString();
        Optional<WorkshopSession> sessionOpt = sessionRepository.findByReference(sessionRef);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        WorkshopSession session = sessionOpt.get();
        Instant now = Instant.now();
        int activeHolds = capacityHoldRepository.sumActiveHoldsForSession(session.getId(), now);
        int confirmedSeats = entitlementRepository.countActiveConfirmedSeatsForSession(session.getId());
        int remainingCapacity = Math.max(0, session.getCapacity() - activeHolds - confirmedSeats);

        boolean selectable = session.getState() == SessionState.PUBLISHED && remainingCapacity > 0;
        Long locationRef = session.getLocation() != null ? session.getLocation().getId() : null;

        return new SaleEligibilitySnapshot(
            query.sessionReference(),
            selectable,
            session.getState().name(),
            locationRef,
            session.getScheduledStartAt(),
            session.getScheduledEndAt(),
            remainingCapacity,
            session.getWorkshopDate()
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
                () -> packageReservationPolicy.validate(command),
                () -> packageReservationPolicy.snapshotForBooking(command));
    }

    @Override
    @Transactional
    public GroupCapacityOutcome reserveGroupCapacity(ReserveGroupCapacityCommand command) {
        return groupCapacityService.reserveGroupCapacity(command);
    }

    @Override
    public TicketQrReissueOutcome reissueTicketQr(ReissueTicketQrCommand command) {
        return ticketQrReissueService.reissue(command);
    }

    @Override
    public TicketCancellationOutcome cancelTicket(CancelTicketCommand command) {
        return ticketCancellationService.cancelTicket(command);
    }

    @Override
    public TicketSessionReplacementOutcome replaceTicketSessions(ReplaceTicketSessionsCommand command) {
        return ticketSessionReplacementService.replace(command);
    }

    @Override
    public EquivalentReplacementSessions findEquivalentReplacementSessions(FindEquivalentReplacementSessionsQuery query) {
        return equivalentReplacementSessionService.find(query);
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
    public OtaBookingCapacityOutcome acceptOtaBooking(AcceptOtaBookingCommand command) {
        String fingerprint = command.bookingReference() + "|" + command.packageReference() + "|"
                + command.sessionReference() + "|" + command.participantCount();
        ClaimResult claim = idempotencyManager.acquireClaim(
                ACTION_OTA_ACCEPT, command.operationReference(), command.correlationReference(), fingerprint);

        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return OtaBookingCapacityOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return OtaBookingCapacityOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstructOtaBookingOutcome(claim.getCompletedRecord());
        }

        if (!ticketRepository.findByBookingReferenceWithLock(command.bookingReference()).isEmpty()) {
            return completeOtaBookingClaim(claim,
                    OtaBookingCapacityOutcome.rejected(RejectionReason.INVALID_STATE));
        }

        RejectionReason validation = packageReservationPolicy.validateOtaBooking(
                command.packageReference(), command.sessionReference(), command.participantCount());
        if (validation != null) {
            return completeOtaBookingClaim(claim, OtaBookingCapacityOutcome.rejected(validation));
        }

        String reserveOperationReference = otaSubOperationReference("reserve", command.operationReference());
        ReserveBookingCapacityCommand reserveCommand = new ReserveBookingCapacityCommand(
                command.bookingReference(),
                command.packageReference(),
                List.of(command.sessionReference()),
                command.participantCount(),
                reserveOperationReference,
                command.correlationReference());
        CapacityHoldOutcome holdOutcome = capacityHoldService.reserveBookingCapacity(
                reserveCommand,
                () -> packageReservationPolicy.validate(reserveCommand),
                () -> packageReservationPolicy.snapshotForBooking(reserveCommand));

        if (holdOutcome.status() == OutcomeStatus.IN_PROGRESS) {
            return OtaBookingCapacityOutcome.inProgress();
        }
        if (holdOutcome.status() != OutcomeStatus.ACCEPTED) {
            return completeOtaBookingClaim(claim, OtaBookingCapacityOutcome.rejected(holdOutcome.reason()));
        }

        String commitOperationReference = otaSubOperationReference("commit", command.operationReference());
        CapacityCommitOutcome commitOutcome = capacityHoldService.commitBookingHolds(
                new CommitBookingHoldsCommand(
                        command.bookingReference(),
                        holdOutcome.holdReferences(),
                        commitOperationReference,
                        command.correlationReference()));
        if (commitOutcome.status() == OutcomeStatus.IN_PROGRESS) {
            throw new IllegalStateException("OTA booking capacity commit is still in progress");
        }
        if (commitOutcome.status() != OutcomeStatus.ACCEPTED) {
            CapacityReleaseOutcome releaseOutcome = capacityHoldService.releaseBookingHolds(
                    new ReleaseBookingHoldsCommand(
                            command.bookingReference(),
                            holdOutcome.holdReferences(),
                            otaSubOperationReference("release", command.operationReference()),
                            command.correlationReference()));
            if (releaseOutcome.status() != OutcomeStatus.ACCEPTED) {
                throw new IllegalStateException("OTA booking capacity compensation did not complete");
            }
            return completeOtaBookingClaim(claim, OtaBookingCapacityOutcome.rejected(commitOutcome.reason()));
        }

        List<AttendanceTicket> tickets = attendanceTicketService.issueTicketsForBooking(
                command.bookingReference(), holdOutcome.holdReferences());
        // FR-036: an OTA-origin ticket is never cancelled, replaced or QR-reissued by Operations.
        tickets.forEach(ticket -> ticket.setSalesChannel(TicketSalesChannel.OTA));
        ticketRepository.saveAll(tickets);
        List<UUID> ticketReferences = tickets.stream()
                .sorted(Comparator.comparing(AttendanceTicket::getTicketSequence))
                .map(ticket -> UUID.fromString(ticket.getReference()))
                .toList();
        if (ticketReferences.size() != command.participantCount()
                || new HashSet<>(ticketReferences).size() != command.participantCount()) {
            throw new IllegalStateException("OTA booking did not produce one unique ticket per participant");
        }

        return completeOtaBookingClaim(claim, OtaBookingCapacityOutcome.accepted(ticketReferences));
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
    public boolean isStaffAssignedToBooking(StaffBookingAssignmentQuery query) {
        if (query == null || query.staffAccountId() == null || query.bookingReference() == null) return false;
        return entitlementRepository.findActiveByBookingReference(query.bookingReference()).stream()
                .map(item -> item.getSession().getId()).distinct()
                .anyMatch(sessionId -> staffAssignmentRepository.existsBySessionIdAndAccountIdAndActiveTrue(sessionId, query.staffAccountId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBookingServiceStarted(BookingSettlementReadinessQuery query) {
        if (query == null) return false;
        return entitlementRepository.findActiveByBookingReference(query.bookingReference()).stream()
                .map(item -> item.getSession().getState())
                .anyMatch(state -> state == SessionState.IN_PROGRESS || state == SessionState.ATTENDANCE_FINALIZED);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketAttendanceOutcome getTicketAttendanceOutcome(TicketAttendanceOutcomeQuery query) {
        if (query == null || query.ticketReference() == null || query.ticketReference().isBlank()) {
            return TicketAttendanceOutcome.pending();
        }
        return bookingAttendanceOutcomeService.evaluateTicket(query.ticketReference(), Instant.now()).outcome();
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

    private OtaBookingCapacityOutcome completeOtaBookingClaim(
            ClaimResult claim, OtaBookingCapacityOutcome outcome) {
        String payload = null;
        if (outcome.status() == OutcomeStatus.ACCEPTED) {
            try {
                payload = objectMapper.writeValueAsString(outcome);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not persist OTA booking outcome", e);
            }
        }
        idempotencyManager.completeClaim(
                claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), payload);
        return outcome;
    }

    private OtaBookingCapacityOutcome reconstructOtaBookingOutcome(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            if (record.getResponsePayload() == null || record.getResponsePayload().isBlank()) {
                throw new IllegalStateException("Completed OTA booking outcome is missing its ticket references");
            }
            try {
                OtaBookingCapacityOutcome outcome = objectMapper.readValue(
                        record.getResponsePayload(), OtaBookingCapacityOutcome.class);
                if (outcome.status() != OutcomeStatus.ACCEPTED || outcome.ticketReferences().isEmpty()) {
                    throw new IllegalStateException("Completed OTA booking outcome is invalid");
                }
                return outcome;
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not read completed OTA booking outcome", e);
            }
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        if (record.getRejectionReason() != null && !record.getRejectionReason().isBlank()) {
            try {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            } catch (IllegalArgumentException ignored) {
                // Keep the boundary outcome safe if an old or malformed record is encountered.
            }
        }
        return OtaBookingCapacityOutcome.rejected(reason);
    }

    private static String otaSubOperationReference(String action, String operationReference) {
        return UUID.nameUUIDFromBytes(("OTA_ACCEPT_" + action + "|" + operationReference)
                .getBytes(StandardCharsets.UTF_8)).toString();
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
