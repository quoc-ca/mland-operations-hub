package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceActionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInOverrideRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CheckInResultType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceActionType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSessionEntitlement;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * T038: explicit, auditable check-in recovery path. Authority is derived from
 * the public account role/status/location-scope facade only; no capability key,
 * Firebase claim, assignment or operational-lead lookup is used.
 */
@Service
@RequiredArgsConstructor
public class CheckInOverrideService {
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopSessionRepository sessionRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final AttendanceActionRepository attendanceActionRepository;
    private final OperationsAuditEventRepository auditRepository;

    @Transactional
    public CheckInResult overrideCheckIn(String authHeader, String correlationReference,
                                         String sessionReference, CheckInOverrideRequest request) {
        if (request == null || !request.isOneOfProvided()) {
            throw new WorkshopValidationException("Exactly one of qrToken or ticketCode must be provided");
        }
        if (!request.isReasonCodeRecognized() || !request.isTicketRedeliveryEvidenceValid()
                || !request.isOtherRequiresNoteValid()) {
            throw new WorkshopValidationException("A recognized reason and required evidence are mandatory");
        }

        WorkshopSession session = sessionRepository.findByReference(sessionReference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found"));
        AccountSummary actor = authorizationService.requireStaffSessionAssignment(authHeader, session.getId());

        if (session.getState() != SessionState.PUBLISHED
                && session.getState() != SessionState.SALES_PAUSED
                && session.getState() != SessionState.IN_PROGRESS) {
            throw new WorkshopLifecycleException("Check-in override is unavailable in session state " + session.getState());
        }

        AttendanceTicket ticket = resolveLockedTicket(request);
        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            throw new WorkshopTicketConflictException("Ticket is " + ticket.getValidityState() + ", override rejected");
        }

        TicketSessionEntitlement entitlement = entitlementRepository
                .findByTicketIdAndSessionIdWithLock(ticket.getId(), session.getId())
                .orElseThrow(() -> new WorkshopTicketConflictException("Ticket is not entitled to this session and location"));
        if (entitlement.getAttendanceState() != AttendanceState.NOT_CHECKED_IN) {
            throw new WorkshopTicketConflictException("Ticket is already checked in or finalized");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        entitlement.setAttendanceState(AttendanceState.CHECKED_IN);
        entitlement.setCheckedInAt(now);
        entitlementRepository.save(entitlement);

        attendanceActionRepository.save(AttendanceAction.builder()
                .ticket(ticket).session(session).actionType(AttendanceActionType.CHECK_IN_OVERRIDE)
                .actorId(actor.accountId()).channel("MANUAL_OVERRIDE")
                .reasonCode(request.getReasonCode()).reasonNote(request.getReasonNote())
                .evidenceReference(request.getEvidenceReference()).occurredAt(now).build());

        String correlation = correlationReference == null || correlationReference.isBlank()
                ? UUID.randomUUID().toString() : correlationReference;
        auditRepository.save(OperationsAuditEvent.builder().sessionId(session.getId())
                .actorId(actor.accountId()).action("TICKET_CHECK_IN_OVERRIDE").outcome("SUCCESS")
                .correlationId(correlation)
                .metadata("{\"ticketReference\":\"" + ticket.getReference() + "\",\"reasonCode\":\""
                        + request.getReasonCode() + "\"}").occurredAt(now).build());

        return CheckInResult.builder().ticketCode(ticket.getTicketCode())
                .sessionId(session.getReference()).locationId(session.getLocation().getId())
                .validityState(ticket.getValidityState()).attendanceState(AttendanceState.CHECKED_IN)
                .checkInResult(CheckInResultType.CHECKED_IN).checkedInAt(now).build();
    }

    private AttendanceTicket resolveLockedTicket(CheckInOverrideRequest request) {
        Optional<AttendanceTicket> ticket = request.getTicketCode() != null && !request.getTicketCode().isBlank()
                ? ticketRepository.findByTicketCodeWithLock(request.getTicketCode())
                : ticketRepository.findByQrReferenceWithLock(request.getQrToken());
        return ticket.orElseThrow(() -> new WorkshopNotFoundException("Ticket not found"));
    }
}
