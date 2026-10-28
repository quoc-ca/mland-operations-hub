package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RequestBookingTicketRedeliveryCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketRedeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketResendThrottleRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.TicketResendRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketResendThrottle;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketResendService {
    private static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final WorkshopOperationsAuthorizationService authorizationService;
    private final WorkshopSessionRepository sessionRepository;
    private final AttendanceTicketRepository ticketRepository;
    private final TicketResendThrottleRepository throttleRepository;
    private final OperationsAuditEventRepository auditRepository;
    private final WorkshopBookingFacade workshopBookingFacade;

    @Transactional
    public TicketRedeliveryOutcome requestResend(String authHeader, String correlationReference,
                                                  String operationReference, String sessionReference,
                                                  TicketResendRequest request) {
        if (request == null || request.getBookingCode() == null || request.getBookingCode().isBlank()) {
            throw new WorkshopValidationException("bookingCode is required");
        }
        WorkshopSession session = sessionRepository.findByReference(sessionReference)
                .orElseThrow(() -> new WorkshopNotFoundException("Workshop session not found"));
        authorizationService.requireStaffSessionAssignment(authHeader, session.getId());
        if (session.getState() != SessionState.PUBLISHED && session.getState() != SessionState.SALES_PAUSED
                && session.getState() != SessionState.IN_PROGRESS) {
            throw new WorkshopLifecycleException("Ticket resend is unavailable in session state " + session.getState());
        }

        List<AttendanceTicket> tickets = ticketRepository.findByBookingReferenceWithLock(request.getBookingCode());
        List<String> eligibleReferences = tickets.stream()
                .filter(t -> t.getValidityState() != null && "ACTIVE".equals(t.getValidityState().name()))
                .filter(t -> t.getSessionEntitlements().stream().anyMatch(e -> e.getAttendanceState() == AttendanceState.NOT_CHECKED_IN))
                .map(AttendanceTicket::getReference).distinct().toList();
        if (eligibleReferences.isEmpty()) {
            throw new WorkshopValidationException("No active unused tickets are eligible for redelivery");
        }

        Instant now = Instant.now();
        TicketResendThrottle throttle = throttleRepository.findByBookingReferenceWithLock(request.getBookingCode())
                .orElseGet(() -> TicketResendThrottle.builder().bookingReference(request.getBookingCode())
                        .windowStartedAt(now).requestCount(0).updatedAt(now).build());
        if (!now.isBefore(throttle.getWindowStartedAt().plus(WINDOW))) {
            throttle.setWindowStartedAt(now);
            throttle.setRequestCount(0);
        }
        if (throttle.getRequestCount() >= MAX_REQUESTS) {
            throw new WorkshopRateLimitException("At most three ticket resend requests per booking are allowed in 15 minutes");
        }
        throttle.setRequestCount(throttle.getRequestCount() + 1);
        throttle.setUpdatedAt(now);
        throttleRepository.save(throttle);

        String correlation = correlationReference == null || correlationReference.isBlank()
                ? UUID.randomUUID().toString() : correlationReference;
        String operation = operationReference == null || operationReference.isBlank()
                ? UUID.randomUUID().toString() : operationReference;
        TicketRedeliveryOutcome outcome = workshopBookingFacade.requestBookingTicketRedelivery(
                new RequestBookingTicketRedeliveryCommand(request.getBookingCode(), eligibleReferences, operation, correlation));
        auditRepository.save(OperationsAuditEvent.builder().sessionId(session.getId())
                .action("TICKET_REDELIVERY_REQUESTED").outcome(outcome.status().name())
                .correlationId(correlation)
                .metadata("{\"bookingReference\":\"" + request.getBookingCode() + "\",\"ticketCount\":" + eligibleReferences.size() + "}")
                .occurredAt(now).build());
        return outcome;
    }
}
