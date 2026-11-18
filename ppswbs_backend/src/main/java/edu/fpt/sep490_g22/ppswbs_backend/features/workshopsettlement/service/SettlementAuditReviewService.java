package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.AuditActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditPage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.SettlementAuditReviewDtos.SettlementAuditRow;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementBusinessAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 003.2 FR-047 to FR-053 for the Settlement tab, served by 003.3 so Operations never calls into it (module cycle,
 * analyze F1). Only an active global Manager reads; any other caller is refused by
 * {@link WorkshopSettlementAuthorizationService} and recorded as {@code DENIED_<reason>} the way 003.3 already does.
 * A successful read writes nothing. The full reason text is returned to the Manager and is never logged.
 * <p>
 * Deliberately NOT {@code @Transactional}: the denial row is written by the authorization service and must survive
 * the refusal exception instead of being rolled back with an outer transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAuditReviewService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;
    static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
    private static final Pattern ACCOUNT_REFERENCE = Pattern.compile("account:(\\d{1,18})");

    private final WorkshopSettlementAuthorizationService authorization;
    private final SettlementBusinessAuditRepository auditRepository;
    private final SettlementAuditCursorCodec cursorCodec;
    private final AccountFacade accounts;

    public SettlementAuditPage readRecords(CurrentAccountActor actor, String actionCode, String actorId,
                                           String aggregateType, String aggregateReference, String from, String to,
                                           String cursor, String limit) {
        authorization.requireManager(actor);

        int pageSize = pageSize(parseInt("limit", limit));
        Long beforeId = cursorCodec.decode(cursor);
        SettlementAuditFilter filter = filter(actionCode, parseLong("actorId", actorId), aggregateType,
                aggregateReference, parseInstant("from", from), parseInstant("to", to));

        List<SettlementBusinessAudit> rows = auditRepository.findPage(filter.actionCode(), filter.actorReference(),
                filter.aggregateType(), filter.aggregateReference(), filter.from(), filter.to(), beforeId,
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<SettlementBusinessAudit> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<Long, AuditActor> names = new HashMap<>();
        List<SettlementAuditRow> items = page.stream().map(row -> toRow(row, names)).toList();
        String nextCursor = hasMore ? cursorCodec.encode(page.get(page.size() - 1).getId()) : null;
        return new SettlementAuditPage(items, nextCursor);
    }

    private SettlementAuditFilter filter(String actionCode, Long actorId, String aggregateType,
                                         String aggregateReference, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new SettlementAuditQueryException("from", "from must not be after to");
        }
        Instant effectiveFrom = from != null ? from : Instant.now().minus(DEFAULT_WINDOW);
        return new SettlementAuditFilter(text("actionCode", actionCode, 64), actorId == null ? null : "account:" + actorId,
                text("aggregateType", aggregateType, 64), text("aggregateReference", aggregateReference, 128),
                effectiveFrom, to);
    }

    private static String text(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new SettlementAuditQueryException(name, name + " is too long");
        }
        return trimmed;
    }

    private SettlementAuditRow toRow(SettlementBusinessAudit row, Map<Long, AuditActor> names) {
        AuditActor actor = null;
        String reference = row.getActorReference();
        if (reference != null) {
            Matcher matcher = ACCOUNT_REFERENCE.matcher(reference);
            if (matcher.matches()) { // SYSTEM and any other stored value is shown as is, without an actor
                actor = names.computeIfAbsent(Long.valueOf(matcher.group(1)), this::actor);
            }
        }
        return new SettlementAuditRow(row.getId(), row.getCreatedAt(), row.getActionCode(), row.getAggregateType(),
                row.getAggregateReference(), actor, reference, row.getCorrelationReference(),
                row.getEvidenceReference(), row.getReasonReference());
    }

    /** One lookup per distinct account on the page; any failure leaves the name empty (never fails the page). */
    private AuditActor actor(Long accountId) {
        String name = null;
        try {
            name = accounts.findAccountFullName(accountId).filter(value -> !value.isBlank()).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Settlement audit review: display name lookup failed for accountId={}", accountId);
        }
        return new AuditActor(accountId, name);
    }

    private static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new SettlementAuditQueryException("limit", "limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private static Long parseLong(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new SettlementAuditQueryException(name, name + " must be a number");
        }
    }

    private static Integer parseInt(String name, String value) {
        Long parsed = parseLong(name, value);
        if (parsed != null && (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE)) {
            throw new SettlementAuditQueryException(name, name + " is out of range");
        }
        return parsed == null ? null : parsed.intValue();
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new SettlementAuditQueryException(name, name + " must be an ISO-8601 date-time");
        }
    }
}
