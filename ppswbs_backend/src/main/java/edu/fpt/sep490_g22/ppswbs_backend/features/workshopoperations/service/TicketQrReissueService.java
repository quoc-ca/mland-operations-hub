package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketQrGenerationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.TicketSessionEntitlementRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketQrGeneration;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketValidityState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.TicketSalesChannel;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReissueTicketQrCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketQrReissueOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.FacadeCommandIdempotencyManager.ClaimStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 003.2 FR-033 / T093: revokes a ticket's active QR immediately and issues a new opaque QR for the same
 * entitlements. Refused once any entitlement of the ticket is checked in. The revoked QR is kept so check-in
 * can refuse it explicitly. Neither the old nor the new QR is written to audit, logs or the idempotency ledger.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketQrReissueService {

    static final String ACTION_REISSUE_QR = "REISSUE_TICKET_QR";

    private final AttendanceTicketRepository ticketRepository;
    private final TicketSessionEntitlementRepository entitlementRepository;
    private final TicketQrGenerationRepository qrGenerationRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final FacadeCommandIdempotencyManager idempotencyManager;
    private final ObjectMapper objectMapper;

    @Transactional
    public TicketQrReissueOutcome reissue(ReissueTicketQrCommand command) {
        if (command == null || command.ticketReference() == null || command.reason() == null
                || isBlank(command.operationReference()) || isBlank(command.correlationReference())) {
            return TicketQrReissueOutcome.rejected(command == null ? null : command.ticketReference(),
                    RejectionReason.INVALID_STATE);
        }
        UUID ticketRef = command.ticketReference();
        ClaimResult claim = idempotencyManager.acquireClaim(ACTION_REISSUE_QR, command.operationReference(),
                command.correlationReference(), ticketRef + "|" + command.reason());
        if (claim.getStatus() == ClaimStatus.MISMATCH) {
            return TicketQrReissueOutcome.rejected(ticketRef, RejectionReason.INVALID_STATE);
        }
        if (claim.getStatus() == ClaimStatus.IN_PROGRESS) {
            return TicketQrReissueOutcome.inProgress(ticketRef);
        }
        if (claim.getStatus() == ClaimStatus.ALREADY_COMPLETED) {
            return reconstruct(ticketRef, claim.getCompletedRecord());
        }

        AttendanceTicket ticket = ticketRepository.findByReferenceWithLock(ticketRef.toString()).orElse(null);
        if (ticket == null) {
            return finish(claim, TicketQrReissueOutcome.rejected(ticketRef, RejectionReason.TICKET_NOT_FOUND));
        }
        if (ticket.getSalesChannel() == TicketSalesChannel.OTA) {
            return finish(claim, TicketQrReissueOutcome.rejected(ticketRef,
                    RejectionReason.OTA_TICKET_NOT_SUPPORTED));
        }
        if (ticket.getValidityState() != TicketValidityState.ACTIVE) {
            return finish(claim, TicketQrReissueOutcome.rejected(ticketRef, RejectionReason.INVALID_STATE));
        }
        boolean checkedIn = entitlementRepository.findByTicketId(ticket.getId()).stream()
                .anyMatch(e -> e.getAttendanceState() == AttendanceState.CHECKED_IN);
        if (checkedIn) {
            audit(command.correlationReference(), ticket, "REJECTED_CHECKED_IN");
            return finish(claim, TicketQrReissueOutcome.rejected(ticketRef, RejectionReason.TICKET_ALREADY_CHECKED_IN));
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int currentGeneration = ticket.getQrGeneration() == null ? 1 : ticket.getQrGeneration();
        qrGenerationRepository.save(TicketQrGeneration.builder()
                .ticket(ticket)
                .qrGeneration(currentGeneration)
                .qrReference(ticket.getQrReference())
                .revokeReason(command.reason().name())
                .revokedAt(now)
                .correlationReference(command.correlationReference())
                .build());
        int nextGeneration = currentGeneration + 1;
        ticket.setQrReference(newQrReference());
        ticket.setQrGeneration(nextGeneration);
        ticketRepository.save(ticket);
        audit(command.correlationReference(), ticket, "SUCCESS");
        log.info("Reissued QR for ticket {} to generation {}", ticket.getTicketCode(), nextGeneration);
        return finish(claim, TicketQrReissueOutcome.accepted(ticketRef, nextGeneration, ticket.getQrReference()));
    }

    private TicketQrReissueOutcome finish(ClaimResult claim, TicketQrReissueOutcome outcome) {
        String payload = null;
        if (outcome.qrGeneration() != null) {
            try {
                payload = objectMapper.writeValueAsString(Map.of("qrGeneration", outcome.qrGeneration()));
            } catch (JsonProcessingException ignored) {
                // the generation is re-read from the ticket on replay
            }
        }
        idempotencyManager.completeClaim(claim.getRecordId(), claim.getClaimVersion(), outcome.status(),
                outcome.reason(), payload);
        return outcome;
    }

    /** Replays the original outcome; the QR value is re-read from the ticket, never from the ledger. */
    private TicketQrReissueOutcome reconstruct(UUID ticketRef, FacadeCommandIdempotencyRecord record) {
        if (record.getOutcomeStatus() != OutcomeStatus.ACCEPTED) {
            RejectionReason reason = RejectionReason.INVALID_STATE;
            try {
                if (record.getRejectionReason() != null) {
                    reason = RejectionReason.valueOf(record.getRejectionReason());
                }
            } catch (IllegalArgumentException ignored) {
                // keep INVALID_STATE
            }
            return TicketQrReissueOutcome.rejected(ticketRef, reason);
        }
        int generation = 0;
        try {
            if (record.getResponsePayload() != null) {
                generation = objectMapper.readTree(record.getResponsePayload()).path("qrGeneration").asInt();
            }
        } catch (JsonProcessingException ignored) {
            // fall back to the ticket's current generation
        }
        AttendanceTicket ticket = ticketRepository.findByReference(ticketRef.toString()).orElse(null);
        if (ticket == null) {
            return TicketQrReissueOutcome.rejected(ticketRef, RejectionReason.TICKET_NOT_FOUND);
        }
        int replayGeneration = generation > 0 ? generation : ticket.getQrGeneration();
        String qr = ticket.getQrGeneration() == replayGeneration ? ticket.getQrReference() : null;
        return new TicketQrReissueOutcome(OutcomeStatus.ACCEPTED, ticketRef, replayGeneration, qr, null);
    }

    private String newQrReference() {
        String qr;
        do {
            qr = "QR-" + UUID.randomUUID();
        } while (ticketRepository.existsByQrReference(qr) || qrGenerationRepository.existsByQrReference(qr));
        return qr;
    }

    private void audit(String correlationReference, AttendanceTicket ticket, String outcome) {
        auditEventRepository.save(OperationsAuditEvent.builder()
                .action(ACTION_REISSUE_QR)
                .outcome(outcome)
                .correlationId(correlationReference)
                .metadata("{\"ticketCode\":\"" + ticket.getTicketCode() + "\",\"qrGeneration\":"
                        + ticket.getQrGeneration() + "}")
                .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
