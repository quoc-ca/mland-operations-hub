package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecisionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 003.3 T117 (FR-045a, decided 2026-11-14): the Staff/Manager queue of online changes that only a Manager may allow.
 * Staff read the queue of their locations; only a Manager decides, with a reason, an Idempotency-Key and an audit.
 * Approval equals a Manager-granted allowance (FR-054) followed by one counted change; a request expires when it can
 * no longer be carried out.
 */
@Service
@RequiredArgsConstructor
public class TicketChangeRequestService {

    private static final Set<String> STATUSES = Set.of(SettlementTicketChangeRequest.PENDING,
            SettlementTicketChangeRequest.APPROVED, SettlementTicketChangeRequest.REJECTED,
            SettlementTicketChangeRequest.EXPIRED);

    private final SettlementTicketChangeRequestRepository requests;
    private final TicketChangeService changes;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementBusinessAuditRepository audits;

    @Transactional
    public List<ChangeRequestDto> list(CurrentAccountActor actor, String status) {
        authorization.requireManagerOrAnyStaff(actor);
        String wanted = status == null || status.isBlank() ? SettlementTicketChangeRequest.PENDING
                : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUSES.contains(wanted)) {
            throw new WorkshopSettlementValidationException("STATUS_INVALID");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        requests.findByStatusAndExpiresAtLessThanEqual(SettlementTicketChangeRequest.PENDING, now)
                .forEach(stale -> changes.expire(stale, now));
        return requests.findByStatusOrderByCreatedAtAsc(wanted).stream()
                .filter(request -> actor.role() == ActorRole.MANAGER
                        || (request.getLocationReference() != null && actor.activeLocationIds() != null
                        && actor.activeLocationIds().contains(request.getLocationReference())))
                .map(TicketChangeRequestService::toDto)
                .toList();
    }

    @Transactional
    public ChangeRequestDto decide(String requestReference, ChangeRequestDecisionDto body, String idempotencyKey,
                                   String correlationId, CurrentAccountActor actor) {
        changes.requireIdempotencyKey(idempotencyKey);
        authorization.requireManager(actor);
        if (body == null || body.decision() == null) {
            throw new WorkshopSettlementValidationException("DECISION_REQUIRED");
        }
        if (body.reason() == null || body.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("REASON_REQUIRED");
        }
        if (requestReference == null || requestReference.isBlank()) {
            throw new WorkshopSettlementNotFoundException("CHANGE_REQUEST_NOT_FOUND");
        }
        SettlementTicketChangeRequest request = requests.findByRequestReferenceForUpdate(requestReference.trim())
                .orElseThrow(() -> new WorkshopSettlementNotFoundException("CHANGE_REQUEST_NOT_FOUND"));
        String key = idempotencyKey.trim();
        if (key.equals(request.getDecisionIdempotencyKey())) {
            return toDto(request); // replay of the same decision
        }
        if (!SettlementTicketChangeRequest.PENDING.equals(request.getStatus())) {
            throw new WorkshopSettlementConflictException(
                    SettlementTicketChangeRequest.EXPIRED.equals(request.getStatus())
                            ? "CHANGE_REQUEST_EXPIRED" : "CHANGE_REQUEST_NOT_PENDING");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (!request.getExpiresAt().isAfter(now)) {
            // The list marks it EXPIRED; nothing can be decided any more.
            throw new WorkshopSettlementConflictException("CHANGE_REQUEST_EXPIRED");
        }
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        String reason = body.reason().trim();

        if (body.decision() == ChangeRequestDecision.APPROVE) {
            request.setAppliedChangeReference(changes.applyApproved(request, actor, reason, correlation, now));
            request.setStatus(SettlementTicketChangeRequest.APPROVED);
        } else {
            request.setStatus(SettlementTicketChangeRequest.REJECTED);
        }
        request.setDecisionReason(reason);
        request.setDecidedByAccountId(actor.accountId());
        request.setDecidedAt(now);
        request.setDecisionIdempotencyKey(key);
        request.setUpdatedAt(now);
        requests.save(request);
        audits.save(SettlementBusinessAudit.builder()
                .auditReference("SETTLE-CHG-" + UUID.randomUUID())
                .aggregateType("TICKET_CHANGE_REQUEST")
                .aggregateReference(request.getRequestReference())
                .actionCode(body.decision() == ChangeRequestDecision.APPROVE
                        ? "TICKET_CHANGE_REQUEST_APPROVED" : "TICKET_CHANGE_REQUEST_REJECTED")
                .actorReference(String.valueOf(actor.accountId()))
                .correlationReference(correlation)
                .build());
        return toDto(request);
    }

    static ChangeRequestDto toDto(SettlementTicketChangeRequest request) {
        return new ChangeRequestDto(
                request.getRequestReference(),
                request.getTicketCode(),
                request.getGroupCode(),
                TicketChangeType.valueOf(request.getChangeType()),
                Arrays.stream(request.getTargetSessionReferences().split(",")).map(UUID::fromString).toList(),
                Arrays.stream(request.getBlockedRules().split(",")).filter(rule -> !rule.isBlank()).toList(),
                request.getStatus(),
                String.valueOf(request.getPriceIncreaseAmountVnd()),
                request.isBuyerConfirmedIncrease(),
                request.getRequesterRole(),
                request.getChannel(),
                request.getReason(),
                request.getExpiresAt(),
                request.getCreatedAt(),
                request.getDecisionReason(),
                request.getDecidedAt(),
                request.getAppliedChangeReference());
    }
}
