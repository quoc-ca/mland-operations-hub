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
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageReservationPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.AttendanceCorrectionWindowPolicy;
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
    private final GroupCapacityService groupCapacityService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketQrReissueService ticketQrReissueService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketCancellationService ticketCancellationService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketSessionReplacementService ticketSessionReplacementService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.EquivalentReplacementSessionService equivalentReplacementSessionService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.TicketChangeOptionsService ticketChangeOptionsService;
    private final AttendanceTicketService attendanceTicketService;
    private final WorkshopPackageReservationPolicy packageReservationPolicy;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAvailabilityService packageAvailabilityService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.BookingAttendanceOutcomeService bookingAttendanceOutcomeService;
    private final WorkshopAddOnService addOnService;
    private final edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.SessionStaffAssignmentRepository staffAssignmentRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WorkshopLocationRepository locationRepository;

    @Override
    @Transactional(readOnly = true)
    public WorkshopAddOnPriceSnapshot getWorkshopAddOnPrice(WorkshopAddOnPriceQuery query) {
        if (query == null) return null;
        return addOnService.getEligibleSnapshot(query.packageReference(), query.locationReference(), query.addOnReference());
    }

    @Override
    @Transactional(readOnly = true)
    public Long getWorkshopAddOnLocation(String addOnReference) {
        return addOnService.locationOf(addOnReference);
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
    @Transactional(readOnly = true)
    public ActivePackageLocations activeLocationsForPackage(String packageReference) {
        Long packageId = packageReservationPolicy.parsePackageReference(packageReference);
        return new ActivePackageLocations(packageId == null ? java.util.List.of()
                : packageAvailabilityService.activeLocationIds(packageId));
    }

    @Override
    public TicketChangeOptions listTicketChangeOptions(ListTicketChangeOptionsQuery query) {
        return ticketChangeOptionsService.list(query);
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
    public java.util.List<SessionEntitlementProjection> getCancelledTicketSessions(String ticketReference) {
        return attendanceTicketService.getCancelledTicketSessions(ticketReference);
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
        if (session.getState() != SessionState.CANCELLATION_REQUESTED) {
            CancellationCompletionOutcome outcome = CancellationCompletionOutcome.rejected(RejectionReason.INVALID_STATE);
            idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(), outcome.reason(), null);
            return outcome;
        }

        // FR-010a (decided 2026-11-16): a ticket still holding an active entitlement in this session has
        // been neither moved (replaceTicketSessions) nor cancelled (cancelTicket). Reject without any state
        // change and release the claim so the same operationReference can be retried after the last ticket resolves.
        if (entitlementRepository.countActiveConfirmedSeatsForSession(session.getId()) > 0) {
            idempotencyManager.abandonClaim(claim.getRecordId(), claim.getClaimVersion());
            return CancellationCompletionOutcome.rejected(RejectionReason.CANCELLATION_HANDOFF_UNRESOLVED);
        }

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

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkshopLocationSnapshot> findLocationByCode(String code) {
        if (locationRepository == null || code == null || code.isBlank()) return Optional.empty();
        return locationRepository.findByCode(code.trim())
                .map(loc -> new WorkshopLocationSnapshot(loc.getId(), loc.getCode(), loc.getDisplayName(), loc.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkshopLocationSnapshot> findLocationById(Long id) {
        if (locationRepository == null || id == null) return Optional.empty();
        return locationRepository.findById(id)
                .map(loc -> new WorkshopLocationSnapshot(loc.getId(), loc.getCode(), loc.getDisplayName(), loc.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkshopLocationSnapshot> listActiveLocations() {
        if (locationRepository == null) return List.of();
        return locationRepository.findByActiveTrue().stream()
                .map(loc -> new WorkshopLocationSnapshot(loc.getId(), loc.getCode(), loc.getDisplayName(), loc.isActive()))
                .toList();
    }
}
