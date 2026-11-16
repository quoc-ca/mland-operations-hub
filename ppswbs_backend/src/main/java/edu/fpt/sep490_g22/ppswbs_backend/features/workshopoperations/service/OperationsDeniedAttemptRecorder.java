package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * T131 / FR-007b: writes one {@code OperationsAuditEvent} with {@code outcome = DENIED} when a sensitive action
 * is refused with 403, an authority or eligibility 409, or 429. The row is written in its own transaction so it
 * survives the rollback of the refused command. The metadata holds only the refusal code, the HTTP status and,
 * for the closed-enum refusals, the {@code reason}; never PII, contact data, QR values or ticket codes.
 * <p>
 * 401 (no identity), 503 (dependency) and plain state conflicts (a repeated reversal, finalization or
 * cancellation answered {@code INVALID_STATE}) are NOT audited; the last-Staff-assignment refusal is already
 * audited by {@link WorkshopResponsibilityService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsDeniedAttemptRecorder {
    public static final String OUTCOME_DENIED = "DENIED";

    private final OperationsAuditEventRepository auditRepository;
    private final WorkshopSessionRepository sessionRepository;
    private final WorkshopOperationsAuthorizationService authorizationService;
    private final PlatformTransactionManager transactionManager;

    /** Runs {@code operation}; if it is refused with an auditable denial, records it and rethrows unchanged. */
    public <T> T guard(String action, String sessionReference, String authHeader, String correlationId,
                       Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            Denial denial = classify(ex);
            if (denial != null) {
                recordSafely(action, sessionReference, authHeader, correlationId, denial);
            }
            throw ex;
        }
    }

    /** Same as {@link #guard} for a command without a result. */
    public void guardVoid(String action, String sessionReference, String authHeader, String correlationId,
                          Runnable operation) {
        guard(action, sessionReference, authHeader, correlationId, () -> {
            operation.run();
            return null;
        });
    }

    /** Maps an exception to its denial, or null when the refusal is not part of the audited set. */
    static Denial classify(RuntimeException ex) {
        if (ex instanceof WorkshopForbiddenException) {
            return new Denial("FORBIDDEN", 403, null);
        }
        if (ex instanceof WorkshopLocationScopeException) {
            return new Denial("LOCATION_SCOPE_DENIED", 403, null);
        }
        if (ex instanceof WorkshopStaffNotEligibleException e) {
            return new Denial("STAFF_NOT_ELIGIBLE", 409, e.getReason().name());
        }
        if (ex instanceof WorkshopRateLimitException) {
            return new Denial("RATE_LIMITED", 429, null);
        }
        if (ex instanceof WorkshopTicketResendRefusedException e) {
            return new Denial("INVALID_STATE", 409, e.getReason().name());
        }
        return null;
    }

    private void recordSafely(String action, String sessionReference, String authHeader, String correlationId,
                              Denial denial) {
        try {
            Long sessionId = sessionReference == null ? null
                    : sessionRepository.findByReference(sessionReference).map(WorkshopSession::getId).orElse(null);
            Long actorId = resolveActorId(authHeader);
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(status -> record(action, sessionId, actorId, correlationId, denial));
        } catch (Exception e) {
            log.warn("Failed to record denied-attempt audit: action={}, error={}", action, e.getMessage());
        }
    }

    private Long resolveActorId(String authHeader) {
        try {
            AccountSummary actor = authorizationService.authenticate(authHeader);
            return actor.accountId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void record(String action, Long sessionId, Long actorId, String correlationId, Denial denial) {
        String metadata = "{\"code\":\"" + denial.code() + "\",\"status\":" + denial.status()
                + (denial.reason() != null ? ",\"reason\":\"" + denial.reason() + "\"" : "") + "}";
        auditRepository.save(OperationsAuditEvent.builder()
                .sessionId(sessionId)
                .actorId(actorId)
                .action(action)
                .outcome(OUTCOME_DENIED)
                .correlationId(correlationId != null && !correlationId.isBlank()
                        ? correlationId : UUID.randomUUID().toString())
                .metadata(metadata)
                .build());
    }

    public record Denial(String code, int status, String reason) { }
}
