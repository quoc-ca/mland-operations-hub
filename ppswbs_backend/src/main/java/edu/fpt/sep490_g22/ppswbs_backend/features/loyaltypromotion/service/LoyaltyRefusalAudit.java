package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Audit row for a refused write request of a Manager or Member (review 2026-11-10, M3; spec Clarification
 * 2026-11-10). The command itself rolled back, so the refusal is written in a transaction of its own after it:
 * who (actor reference), what (HTTP method and path, which carry only IDs), the error code and the names of the
 * refused fields. Never the body, values, token or personal data. Reads (GET) are not audited, and neither are
 * failures that are not a decision about the request: no identity (401, answered by 001), contention and an
 * unavailable dependency (the outcome is unknown, the client retries with the same key). A failure to write the
 * audit row is logged and never replaces the original answer. Facade refusals (FE-03/FE-08) are audited by their
 * owners.
 */
@Component
public class LoyaltyRefusalAudit {

    public static final String ACTION = "request.refused";
    private static final Logger log = LoggerFactory.getLogger(LoyaltyRefusalAudit.class);
    private static final Set<LoyaltyErrorCode> NOT_A_REFUSAL = EnumSet.of(LoyaltyErrorCode.AUTHENTICATION_REQUIRED,
            LoyaltyErrorCode.RETRYABLE_CONTENTION, LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE);
    private static final int OBJECT_MAX = 128;

    private final LoyaltyAuditService audit;
    private final TransactionTemplate transaction;

    public LoyaltyRefusalAudit(LoyaltyAuditService audit, PlatformTransactionManager transactionManager) {
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Whether a request with this method answered with this code is recorded. */
    public static boolean recorded(String method, LoyaltyErrorCode code) {
        return method != null && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method) && !NOT_A_REFUSAL.contains(code);
    }

    public void record(String method, String path, LoyaltyErrorCode code, List<String> fields) {
        if (!recorded(method, code)) {
            return;
        }
        String actor = ActorContext.getCurrentActor().map(LoyaltyRefusalAudit::actorRef).orElse("anonymous");
        String object = (method.toUpperCase() + " " + (path == null ? "" : path));
        if (object.length() > OBJECT_MAX) {
            object = object.substring(0, OBJECT_MAX);
        }
        String names = fields.stream().distinct().limit(20)
                .map(LoyaltyRefusalAudit::jsonString).collect(Collectors.joining(","));
        String detail = "{\"code\":\"" + code.name() + "\",\"fields\":[" + names + "]}";
        if (detail.length() > LoyaltyAuditService.DETAIL_MAX) {
            detail = "{\"code\":\"" + code.name() + "\",\"fields\":[]}";
        }
        String finalObject = object;
        String finalDetail = detail;
        try {
            transaction.executeWithoutResult(status -> audit.record(actor, ACTION, finalObject, finalDetail));
        } catch (RuntimeException e) {
            log.warn("Could not audit a refused loyalty request: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Same actor reference as the accepted commands of that role: a Manager's account id, {@code member:<id>} for a
     * Member, and {@code account:<id>} for a role that has no FE-06 command (Staff, Admin).
     */
    static String actorRef(CurrentAccountActor actor) {
        if (actor.accountId() == null) {
            return "anonymous";
        }
        if (actor.role() == ActorRole.MANAGER) {
            return actor.accountId().toString();
        }
        return (actor.role() == ActorRole.MEMBER ? "member:" : "account:") + actor.accountId();
    }

    /** Field names come from loyalty validation only; quotes and backslashes are escaped anyway. */
    private static String jsonString(String value) {
        String text = value == null ? "" : value.length() > 80 ? value.substring(0, 80) : value;
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c >= 0x20) {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
