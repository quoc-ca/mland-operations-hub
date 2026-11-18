package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.common.notification.NotificationIntentPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.OperationsAuditEventRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopSessionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Alerts the active Managers about one session still {@code CANCELLATION_REQUESTED} 24 hours
 * after its scheduled end (FR-010a, T133, F06). One transaction per session ({@code REQUIRES_NEW}),
 * so a failure affects only that session. The {@code SENT} audit event is the "already alerted"
 * marker and is written only when every Manager intent was accepted; otherwise only a WARN is
 * logged and the next scan retries (the notification module de-duplicates per recipient).
 * The session state is never changed. Addresses are never logged or audited.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnresolvedCancellationAlertTransition {

    public static final String AUDIT_ACTION = "CANCELLATION_UNRESOLVED_ALERT";
    public static final String TEMPLATE_CODE = "WORKSHOP_CANCELLATION_UNRESOLVED_ALERT";
    public static final Duration ALERT_AFTER = Duration.ofHours(24);

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WorkshopSessionRepository sessionRepository;
    private final OperationsAuditEventRepository auditEventRepository;
    private final AccountFacade accountFacade;
    private final NotificationIntentPort notificationPort;

    /** @return true when the {@code SENT} marker was written by this call */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean alertIfStillDue(String reference, Instant now) {
        WorkshopSession session = sessionRepository.findByReferenceWithLock(reference).orElse(null);
        if (session == null
                || session.getState() != SessionState.CANCELLATION_REQUESTED
                || session.getScheduledEndAt() == null
                || session.getScheduledEndAt().plus(ALERT_AFTER).isAfter(now)
                || auditEventRepository.existsBySessionIdAndAction(session.getId(), AUDIT_ACTION)) {
            return false;
        }

        List<String> emails = accountFacade.listActiveManagerEmails().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (emails.isEmpty()) {
            log.warn("No active Manager to alert about workshop session reference={} still awaiting cancellation; retrying on the next scan",
                    reference);
            return false;
        }

        Map<String, Object> snapshot = snapshot(session, now);
        String businessEventReference = reference + ":CANCELLATION_UNRESOLVED";
        int rejected = 0;
        for (String email : emails) {
            if (!notificationPort.submitIntent(businessEventReference, TEMPLATE_CODE, email, snapshot)) {
                rejected++;
            }
        }
        if (rejected > 0) {
            log.warn("Cancellation alert for workshop session reference={} was rejected for {} of {} Managers; no marker written, retrying on the next scan",
                    reference, rejected, emails.size());
            return false;
        }

        auditEventRepository.save(OperationsAuditEvent.builder()
                .sessionId(session.getId())
                .actorId(null)
                .action(AUDIT_ACTION)
                .outcome("SENT")
                .correlationId("scheduler-" + UUID.randomUUID())
                .metadata("{\"recipientCount\":" + emails.size() + "}")
                .build());
        log.info("Alerted {} Managers about workshop session reference={} still awaiting cancellation", emails.size(), reference);
        return true;
    }

    private Map<String, Object> snapshot(WorkshopSession session, Instant now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionReference", session.getReference());
        data.put("sessionName", session.getSlot().getLabel() + " ngày " + session.getWorkshopDate());
        data.put("locationName", session.getLocation().getDisplayName());
        data.put("scheduledEndAt", DISPLAY_FORMAT.format(session.getScheduledEndAt().atZone(DISPLAY_ZONE)));
        data.put("hoursSinceScheduledEnd", Duration.between(session.getScheduledEndAt(), now).toHours());
        return data;
    }
}
