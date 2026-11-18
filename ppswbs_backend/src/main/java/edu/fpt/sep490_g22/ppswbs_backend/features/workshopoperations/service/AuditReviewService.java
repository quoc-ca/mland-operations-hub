package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.AuditActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.OperationsAuditFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.OperationsAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AuditReviewDtos.OperationsAuditRow;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 003.2 US7 (FR-047 to FR-051, FR-053): the Manager's read of Operations audit rows. Only an active global Manager
 * reads; a refused read is itself audited {@code DENIED} (FR-048, FR-007b). A successful read writes nothing
 * (FR-053). Metadata leaves only through {@link AuditMetadataWhitelist}; the actor is shown as account id plus
 * display name, with the id alone when the lookup finds nothing or fails (it never fails the page).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReviewService {

    /** Action recorded when a non-Manager reads the list (FR-048). */
    public static final String DENIED_ACTION = "OPERATIONS_AUDIT_READ";

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
    private static final Set<String> OUTCOMES = Set.of("SUCCESS", "FAILURE", "DENIED", "SENT");
    private static final int MAX_ACTION_LENGTH = 80;

    private final WorkshopOperationsAuthorizationService authorization;
    private final OperationsDeniedAttemptRecorder deniedAttemptRecorder;
    private final OperationsAuditEventRepository auditRepository;
    private final AuditMetadataWhitelist metadataWhitelist;
    private final AuditCursorCodec cursorCodec;
    private final AccountFacade accounts;

    @Transactional(readOnly = true)
    public OperationsAuditPage readOperationsEvents(String authorization, String correlationId, String action,
                                                    String outcome, String actorId, String sessionId, String from,
                                                    String to, String cursor, String limit) {
        deniedAttemptRecorder.guard(DENIED_ACTION, null, authorization, correlationId,
                () -> this.authorization.requireGlobalManager(authorization));

        // Raw strings are parsed here, after the guard, so a refused caller always gets 403 (and its DENIED row)
        // and every malformed parameter is the existing 400 VALIDATION_ERROR, not a framework conversion error.
        int pageSize = pageSize(parseInt("limit", limit));
        Long beforeId = cursorCodec.decode(cursor);
        OperationsAuditFilter filter = filter(action, outcome, parseLong("actorId", actorId),
                parseLong("sessionId", sessionId), parseInstant("from", from), parseInstant("to", to));

        List<OperationsAuditEvent> rows = auditRepository.findPage(filter.action(), filter.outcome(),
                filter.actorId(), filter.sessionId(), filter.from(), filter.to(), beforeId,
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<OperationsAuditEvent> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<Long, AuditActor> actors = new HashMap<>();
        List<OperationsAuditRow> items = page.stream().map(row -> toRow(row, actors)).toList();
        String nextCursor = hasMore ? cursorCodec.encode(page.get(page.size() - 1).getId()) : null;
        return new OperationsAuditPage(items, nextCursor);
    }

    private static Long parseLong(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new WorkshopValidationException(name + " must be a number");
        }
    }

    private static Integer parseInt(String name, String value) {
        Long parsed = parseLong(name, value);
        if (parsed != null && (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE)) {
            throw new WorkshopValidationException(name + " is out of range");
        }
        return parsed == null ? null : parsed.intValue();
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new WorkshopValidationException(name + " must be an ISO-8601 date-time");
        }
    }

    private static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new WorkshopValidationException("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private OperationsAuditFilter filter(String action, String outcome, Long actorId, Long sessionId,
                                         Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new WorkshopValidationException("from must not be after to");
        }
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        if (normalizedAction != null && normalizedAction.length() > MAX_ACTION_LENGTH) {
            throw new WorkshopValidationException("action is too long");
        }
        String normalizedOutcome = outcome == null || outcome.isBlank() ? null : outcome.trim();
        if (normalizedOutcome != null && !OUTCOMES.contains(normalizedOutcome)) {
            throw new WorkshopValidationException("outcome is not valid");
        }
        // FR-049: the default view is the last 7 days.
        Instant effectiveFrom = from != null ? from : Instant.now().minus(DEFAULT_WINDOW);
        return new OperationsAuditFilter(normalizedAction, normalizedOutcome, actorId, sessionId, effectiveFrom, to);
    }

    private OperationsAuditRow toRow(OperationsAuditEvent row, Map<Long, AuditActor> actors) {
        AuditActor actor = row.getActorId() == null ? null
                : actors.computeIfAbsent(row.getActorId(), this::actor);
        return new OperationsAuditRow(row.getId(), row.getOccurredAt(), actor, row.getAction(), row.getOutcome(),
                row.getSessionId(), row.getCorrelationId(), metadataWhitelist.filter(row.getMetadata()));
    }

    /** One lookup per distinct actor on the page; any failure leaves the name empty. */
    private AuditActor actor(Long accountId) {
        String name = null;
        try {
            name = accounts.findAccountFullName(accountId).filter(value -> !value.isBlank()).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Audit review: display name lookup failed for accountId={}", accountId);
        }
        return new AuditActor(accountId, name);
    }
}
