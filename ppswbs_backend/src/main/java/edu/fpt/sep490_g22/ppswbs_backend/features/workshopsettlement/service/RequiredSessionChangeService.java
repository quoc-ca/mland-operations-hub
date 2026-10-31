package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.RequiredSessionChangeCommandDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementCaseProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCase;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopBookingSettlementAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.WorkshopOperationsSettlementAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Orchestrates a complete booking-level required-session replacement through 003.2. */
@Service
@RequiredArgsConstructor
public class RequiredSessionChangeService {
    private final WorkshopBookingSettlementAdapter booking;
    private final WorkshopOperationsSettlementAdapter operations;
    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementIdempotencyService idempotency;
    private final SettlementCaseRepository cases;
    private final SettlementBusinessAuditRepository audits;

    @Transactional(readOnly = true)
    public SettlementCaseProjectionDto read(String bookingReference, String caseReference,
                                            String contactEmail, String contactPhone,
                                            CurrentAccountActor actor) {
        if (bookingReference == null || bookingReference.isBlank()
                || caseReference == null || caseReference.isBlank()) {
            throw new WorkshopSettlementNotFoundException();
        }
        var projection = booking.confirmedProjection(bookingReference.trim());
        if (projection == null) throw new WorkshopSettlementNotFoundException();

        if (actor == null) {
            if ((contactEmail == null || contactEmail.isBlank())
                    == (contactPhone == null || contactPhone.isBlank())) {
                throw new WorkshopSettlementNotFoundException();
            }
            var access = booking.verifyCustomerAccess(bookingReference.trim(), null, contactEmail, contactPhone);
            if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
        } else if (actor.role() == edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole.MEMBER) {
            // Member identity is authoritative; do not let optional guest proof alter access evaluation.
            var access = booking.verifyCustomerAccess(bookingReference.trim(), actor.accountId(), null, null);
            if (access == null || !access.authorized()) throw new WorkshopSettlementNotFoundException();
        } else if (actor.role() == edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole.MANAGER) {
            authorization.requireManager(actor);
        } else if (actor.role() == edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole.STAFF) {
            authorization.requireManagerOrStaff(actor, projection.bookingLocationReference());
        } else {
            throw new WorkshopSettlementNotFoundException();
        }

        SettlementCase settlementCase = cases.findByCaseReference(caseReference.trim())
                .filter(candidate -> bookingReference.trim().equals(candidate.getBookingReference()))
                .filter(candidate -> "REQUIRED_SESSION_CHANGE".equals(candidate.getCaseType()))
                .orElseThrow(WorkshopSettlementNotFoundException::new);
        return SettlementCaseProjectionDto.from(settlementCase);
    }

    @Transactional
    public SettlementCaseProjectionDto open(String bookingReference, String idempotencyKey,
                                            String correlationReference, RequiredSessionChangeCommandDto command,
                                            CurrentAccountActor actor) {
        if (command == null) throw new WorkshopSettlementValidationException("command is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new WorkshopSettlementValidationException("Idempotency-Key is required");
        }
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new WorkshopSettlementValidationException("bookingReference is required");
        }
        var projection = booking.confirmedProjection(bookingReference.trim());
        if (projection == null) throw new WorkshopSettlementNotFoundException();
        authorization.requireManager(actor);

        List<String> targets = command.targetRequiredSessionReferences();
        if (targets == null || targets.isEmpty() || targets.stream().anyMatch(ref -> ref == null || ref.isBlank())) {
            throw new WorkshopSettlementValidationException("targetRequiredSessionReferences is required");
        }
        List<UUID> targetUuids;
        try {
            targetUuids = targets.stream().map(String::trim).map(UUID::fromString).toList();
        } catch (IllegalArgumentException malformed) {
            throw new WorkshopSettlementValidationException("targetRequiredSessionReferences must contain UUID references");
        }
        if (targetUuids.stream().distinct().count() != targetUuids.size()) {
            throw new WorkshopSettlementValidationException("targetRequiredSessionReferences must be distinct");
        }
        if (projection.requiredSessionReferences() == null
                || targetUuids.size() != projection.requiredSessionReferences().size()) {
            throw new WorkshopSettlementValidationException("complete target required-session set is required");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new WorkshopSettlementValidationException("reason is required");
        }
        if (command.cancellationHandoffReference() != null && !command.cancellationHandoffReference().isBlank()) {
            throw new WorkshopSettlementConflictException("MLAND_CANCELLATION_REQUIRES_REMEDY_FLOW");
        }

        String operationReference = operationReference(bookingReference.trim(), idempotencyKey.trim());
        String correlation = correlationReference == null || correlationReference.isBlank()
                ? "CORR-" + UUID.randomUUID() : correlationReference.trim();
        String fingerprint = fingerprint(bookingReference.trim(), targets.stream().map(String::trim).toList(),
                command.reason().trim(), command.cancellationHandoffReference());

        var result = idempotency.execute(operationReference, fingerprint, () -> {
            var outcome = operations.replace(bookingReference.trim(), targets.stream().map(String::trim).toList(),
                    operationReference, correlation);
            if (outcome == null || outcome.status() == OutcomeStatus.IN_PROGRESS) {
                throw new WorkshopSettlementConflictException("REQUIRED_SESSION_CHANGE_IN_PROGRESS");
            }
            if (outcome.status() != OutcomeStatus.ACCEPTED) {
                throw new WorkshopSettlementConflictException("REQUIRED_SESSION_CHANGE_" +
                        (outcome.reason() == null ? "REJECTED" : outcome.reason().name()));
            }

            String caseReference = "SETTLE-CHANGE-" + UUID.randomUUID();
            cases.saveAndFlush(SettlementCase.builder().caseReference(caseReference)
                    .bookingReference(bookingReference.trim()).caseType("REQUIRED_SESSION_CHANGE")
                    .lifecycleStatus("RESOLVED").reasonReference(command.reason().trim())
                    .correlationReference(correlation).createdAt(Instant.now()).updatedAt(Instant.now()).build());
            audits.save(SettlementBusinessAudit.builder().auditReference("SETTLE-AUDIT-" + UUID.randomUUID())
                    .aggregateType("SETTLEMENT_CASE").aggregateReference(caseReference)
                    .actionCode("REQUIRED_SESSION_CHANGE_ACCEPTED")
                    .actorReference(actor.accountId().toString()).correlationReference(correlation)
                    .reasonReference(command.reason().trim()).createdAt(Instant.now()).build());
            return caseReference;
        });

        SettlementCase settlementCase = cases.findByCaseReference(result.outcomeReference())
                .orElseThrow(() -> new WorkshopSettlementConflictException("REQUIRED_SESSION_CHANGE_OUTCOME_MISSING"));
        return SettlementCaseProjectionDto.from(settlementCase);
    }

    private String operationReference(String bookingReference, String idempotencyKey) {
        return "REQUIRED-CHANGE-" + digest(bookingReference + "|" + idempotencyKey).substring(0, 48);
    }

    private String fingerprint(String bookingReference, List<String> targetReferences, String reason,
                               String cancellationHandoffReference) {
        return digest(String.join("|", bookingReference, String.join(",", targetReferences), reason,
                cancellationHandoffReference == null ? "" : cancellationHandoffReference.trim()));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
