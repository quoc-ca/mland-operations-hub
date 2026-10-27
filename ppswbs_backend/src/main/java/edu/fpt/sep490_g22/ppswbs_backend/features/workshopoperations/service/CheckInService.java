package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing normal online QR and exact ticket-code check-in,
 * concurrency atomicity, and generic booking-code ticket lookup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final WorkshopSessionRepository sessionRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final AttendanceActionRepository attendanceActionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;

    /**
     * Executes atomic normal check-in via QR reference or exact ticket-code.
     * Requires:
     * - Online request;
     * - Session in PUBLISHED or IN_PROGRESS;
     * - Check-in window open (now >= checkInOpensAt);
     * - Ticket validityState == ACTIVE;
     * - Valid entitlement for the target session and location;
     * - Actor is actively assigned Staff or Manager operational lead within location scope.
     * Repeated scan returns 200 OK with ALREADY_CHECKED_IN and the original checkedInAt time.
     */
    @Transactional
    public CheckInResult checkIn(String authHeader, String sessionReference, CheckInRequest request) {
        WorkshopSession session = resolveSession(sessionReference);

        AccountSummary actor = authorizationService.requireStaffSessionAssignment(authHeader, session.getId());

        if (session.getState() != SessionState.PUBLISHED && session.getState() != SessionState.IN_PROGRESS) {
            log.warn("Check-in rejected: session {} is in state {}", sessionReference, session.getState());
            throw new WorkshopLifecycleException("Session is in state " + session.getState() + ", check-in requires PUBLISHED or IN_PROGRESS");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (session.getCheckInOpensAt() != null && now.isBefore(session.getCheckInOpensAt())) {
            log.warn("Check-in rejected: session {} check-in opens at {}", sessionReference, session.getCheckInOpensAt());
            throw new WorkshopLifecycleException("Check-in has not opened yet for session " + sessionReference + " (opens at " + session.getCheckInOpensAt() + ")");
        }

        AttendanceTicket ticket = resolveTicket(request);

        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            log.warn("Check-in rejected: ticket {} validity state is {}", ticket.getTicketCode(), ticket.getValidityState());
            throw new WorkshopTicketConflictException("Ticket is " + ticket.getValidityState() + ", check-in rejected");
        }

        // Lock entitlement row for (ticket, session) to guarantee concurrency atomicity
        TicketSessionEntitlement entitlement = entitlementRepository.findByTicketIdAndSessionIdWithLock(ticket.getId(), session.getId())
                .orElseThrow(() -> {
                    log.warn("Check-in rejected: ticket {} not entitled to session {}", ticket.getTicketCode(), sessionReference);
                    return new WorkshopTicketConflictException("Ticket " + ticket.getTicketCode() + " is not entitled to this session and location");
                });

        // Repeat scan idempotent response: return 200 OK with ALREADY_CHECKED_IN and original time
        if (entitlement.getAttendanceState() == AttendanceState.CHECKED_IN) {
            log.info("Ticket {} already checked in at session {}, returning ALREADY_CHECKED_IN",
                    ticket.getTicketCode(), sessionReference);
            return CheckInResult.builder()
                    .ticketCode(ticket.getTicketCode())
                    .sessionId(session.getReference())
                    .locationId(session.getLocation().getId())
                    .validityState(ticket.getValidityState())
                    .attendanceState(AttendanceState.CHECKED_IN)
                    .checkInResult(CheckInResultType.ALREADY_CHECKED_IN)
                    .checkedInAt(entitlement.getCheckedInAt())
                    .build();
        }

        // First valid scan: atomic check-in
        entitlement.setAttendanceState(AttendanceState.CHECKED_IN);
        entitlement.setCheckedInAt(now);
        entitlementRepository.save(entitlement);

        AttendanceAction action = AttendanceAction.builder()
                .ticket(ticket)
                .session(session)
                .actionType(AttendanceActionType.CHECK_IN)
                .actorId(actor.accountId())
                .channel("ONSITE_SCAN")
                .occurredAt(now)
                .build();
        attendanceActionRepository.save(action);

        recordAudit(session.getId(), actor.accountId(), "TICKET_CHECKED_IN", "SUCCESS",
                "{\"ticketCode\":\"" + ticket.getTicketCode() + "\"}");

        log.info("Ticket {} successfully checked in at session {} by actorId={}",
                ticket.getTicketCode(), sessionReference, actor.accountId());

        return CheckInResult.builder()
                .ticketCode(ticket.getTicketCode())
                .sessionId(session.getReference())
                .locationId(session.getLocation().getId())
                .validityState(ticket.getValidityState())
                .attendanceState(AttendanceState.CHECKED_IN)
                .checkInResult(CheckInResultType.CHECKED_IN)
                .checkedInAt(now)
                .build();
    }

    /**
     * Looks up candidate tickets for a booking reference at the current session and location.
     * Returns generic not-found if no eligible tickets exist for this session (FR-003d).
     * Never automatically checks in a ticket.
     */
    @Transactional(readOnly = true)
    public List<Ticket> lookupTicketsByBookingCode(String authHeader, String sessionReference, String bookingCode) {
        WorkshopSession session = resolveSession(sessionReference);

        authorizationService.requireStaffSessionAssignment(authHeader, session.getId());

        List<TicketSessionEntitlement> entitlements = entitlementRepository
                .findActiveEntitlementsBySessionIdAndBookingReference(session.getId(), bookingCode);

        if (entitlements.isEmpty()) {
            throw new WorkshopNotFoundException("No eligible tickets found for the specified booking code and session");
        }

        return entitlements.stream()
                .<Ticket>map(e -> (Ticket) Ticket.builder()
                        .ticketCode(e.getTicket().getTicketCode())
                        .sessionId(session.getReference())
                        .locationId(session.getLocation().getId())
                        .validityState(e.getTicket().getValidityState())
                        .attendanceState(e.getAttendanceState())
                        .checkedInAt(e.getCheckedInAt())
                        .build())
                .toList();
    }

    private WorkshopSession resolveSession(String sessionReference) {
        return sessionRepository.findByReference(sessionReference)
                .or(() -> {
                    try {
                        return sessionRepository.findById(Long.parseLong(sessionReference));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found with reference or ID: " + sessionReference));
    }

    private AttendanceTicket resolveTicket(CheckInRequest request) {
        if (request == null) {
            throw new WorkshopValidationException("Check-in request must not be null");
        }
        if (request.getQrToken() != null && !request.getQrToken().isBlank()) {
            return ticketRepository.findByQrReference(request.getQrToken())
                    .orElseThrow(() -> new WorkshopNotFoundException("Ticket not found for provided QR token"));
        }
        if (request.getTicketCode() != null && !request.getTicketCode().isBlank()) {
            return ticketRepository.findByTicketCode(request.getTicketCode())
                    .orElseThrow(() -> new WorkshopNotFoundException("Ticket not found for ticket code: " + request.getTicketCode()));
        }
        throw new WorkshopValidationException("Either qrToken or ticketCode must be provided");
    }

    private void recordAudit(Long sessionId, Long actorId, String action, String outcome, String metadata) {
        try {
            OperationsAuditEvent event = OperationsAuditEvent.builder()
                    .sessionId(sessionId)
                    .actorId(actorId)
                    .action(action)
                    .outcome(outcome)
                    .correlationId(UUID.randomUUID().toString())
                    .metadata(metadata)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record check-in audit event: {}", e.getMessage());
        }
    }
}
