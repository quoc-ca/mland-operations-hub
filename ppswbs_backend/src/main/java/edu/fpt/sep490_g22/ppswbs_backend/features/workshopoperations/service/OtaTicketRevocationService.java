package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSalesChannel;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OtaTicketRevocationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RevokeOtaTicketsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 003.2 T123 (003.4 Clarifications 2026-11-15, FR-036 amendment): revokes named tickets of an OTA-origin booking
 * after the OTA confirmed their cancellation. Each ticket's future seats are released and its entitlements revoked
 * (the ticket becomes {@code REVOKED}, so a scan answers 409 TICKET_CONFLICT); attendance history is never rewritten.
 * All named tickets or none: a missing, foreign, non-OTA, non-active or already attended ticket refuses the whole
 * command and changes nothing. Idempotent per OTA event reference; a changed ticket set under the same reference
 * is refused as a mismatch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtaTicketRevocationService {

    static final String ACTION_REVOKE_OTA_TICKETS = "REVOKE_OTA_TICKETS";

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final TicketCancellationService ticketCancellationService;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final WorkshopOtaSnapshotService otaSnapshotService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OtaTicketRevocationOutcome revoke(RevokeOtaTicketsCommand command) {
        if (command == null || isBlank(command.bookingReference()) || isBlank(command.operationReference())
                || isBlank(command.correlationReference()) || command.ticketReferences().isEmpty()
                || command.ticketReferences().stream().anyMatch(Objects::isNull)
                || command.ticketReferences().stream().distinct().count() != command.ticketReferences().size()) {
            return OtaTicketRevocationOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        String fingerprint = command.bookingReference() + "|" + command.ticketReferences().stream()
                .map(UUID::toString).sorted().toList();
        ClaimResult claim = idempotencyManager.acquireClaim(ACTION_REVOKE_OTA_TICKETS, command.operationReference(),
                command.correlationReference(), fingerprint);
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return OtaTicketRevocationOutcome.rejected(RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return OtaTicketRevocationOutcome.inProgress();
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstruct(claim.getCompletedRecord());
        }

        List<AttendanceTicket> tickets = new ArrayList<>();
        for (UUID reference : command.ticketReferences().stream().sorted().toList()) {
            AttendanceTicket ticket = ticketRepository.findByReferenceWithLock(reference.toString()).orElse(null);
            if (ticket == null || !command.bookingReference().equals(ticket.getBookingReference())) {
                return finish(claim, OtaTicketRevocationOutcome.rejected(RejectionReason.TICKET_NOT_FOUND));
            }
            tickets.add(ticket);
        }
        for (AttendanceTicket ticket : tickets) {
            if (ticket.getSalesChannel() != TicketSalesChannel.OTA
                    || ticket.getValidityState() != TicketValidityState.ACTIVE) {
                return finish(claim, OtaTicketRevocationOutcome.rejected(RejectionReason.INVALID_STATE));
            }
            boolean attended = entitlementRepository.findByTicketId(ticket.getId()).stream()
                    .anyMatch(e -> e.getCheckedInAt() != null
                            || e.getAttendanceState() != AttendanceState.NOT_CHECKED_IN);
            if (attended) {
                return finish(claim, OtaTicketRevocationOutcome.rejected(RejectionReason.TICKET_ALREADY_CHECKED_IN));
            }
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Map<Long, WorkshopSession> changedSessions = new LinkedHashMap<>();
        int releasedSeats = 0;
        for (AttendanceTicket ticket : tickets) {
            List<UUID> released = ticketCancellationService.releaseFutureSeats(ticket, command.operationReference(),
                    command.operationReference(), command.correlationReference(), now, changedSessions);
            if (released == null) {
                throw new IllegalStateException("OTA ticket revocation met a session without a start time");
            }
            releasedSeats += released.size();
            ticket.setValidityState(TicketValidityState.REVOKED);
            ticketRepository.save(ticket);
        }
        for (WorkshopSession changed : changedSessions.values()) {
            otaSnapshotService.publishForObservableChange(changed, command.operationReference(), now);
        }
        auditEventRepository.save(OperationsAuditEvent.builder()
                .action(ACTION_REVOKE_OTA_TICKETS)
                .outcome("SUCCESS")
                .correlationId(command.correlationReference())
                .metadata("{\"tickets\":" + tickets.size() + ",\"releasedSeats\":" + releasedSeats + "}")
                .build());
        log.info("Revoked {} OTA tickets of one booking and released {} future seats", tickets.size(), releasedSeats);
        return finish(claim, OtaTicketRevocationOutcome.accepted(command.ticketReferences()));
    }

    private OtaTicketRevocationOutcome finish(ClaimResult claim, OtaTicketRevocationOutcome outcome) {
        String payload = null;
        if (outcome.status() == OutcomeStatus.ACCEPTED) {
            try {
                payload = objectMapper.writeValueAsString(
                        outcome.revokedTicketReferences().stream().map(UUID::toString).toList());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not persist OTA revocation outcome", e);
            }
        }
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(),
                outcome.reason(), payload);
        return outcome;
    }

    private OtaTicketRevocationOutcome reconstruct(FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() == OutcomeStatus.ACCEPTED) {
            try {
                List<String> refs = objectMapper.readValue(record.getResponsePayload(), new TypeReference<List<String>>() { });
                return OtaTicketRevocationOutcome.accepted(refs.stream().map(UUID::fromString).toList());
            } catch (JsonProcessingException | RuntimeException e) {
                throw new IllegalStateException("Completed OTA revocation outcome is unreadable", e);
            }
        }
        RejectionReason reason = RejectionReason.INVALID_STATE;
        try {
            if (record.getRejectionReason() != null) {
                reason = RejectionReason.valueOf(record.getRejectionReason());
            }
        } catch (IllegalArgumentException ignored) {
            // keep INVALID_STATE
        }
        return OtaTicketRevocationOutcome.rejected(reason);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
