package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingAuditEvidence;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The single business-alert path of Booking (003.1 FR-040a, T130, decided 2026-11-15): every alert (late-payment
 * review, excess payment, failed expiry release, failed result or confirmation email) goes to the active Managers
 * only, through FE-001 {@code AccountFacade.listActiveManagerEmails()}; never an Admin or Staff. With no active
 * Manager no email is sent: an audit entry and a WARN log are written and the item stays in the Manager work queue,
 * so the first Manager who becomes active sees it. Nothing is decided automatically. Addresses and buyer contact
 * data never reach the log or the audit entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerAlertService {

    public static final String AUDIT_NO_RECIPIENT = "MANAGER_ALERT_NO_RECIPIENT";

    private final ManagerAlertRecipientSource recipients;
    private final GroupNotificationService notifications;
    private final BookingAuditEvidenceRepository auditEvidence;

    /**
     * Queues the alert for every active Manager, once per distinct address.
     *
     * @return how many Managers were alerted; 0 means the no-Manager fallback was applied
     */
    public int alert(BookingGroup group, String notificationKind, String itemReference, String subject, String body,
                     String correlationReference) {
        List<String> addresses = recipients.activeManagerEmails().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .map(EmailMasking::normalize).distinct().sorted().toList();
        if (addresses.isEmpty()) {
            log.warn("Manager alert {} for {} has no active Manager; it waits in the Manager queue",
                    notificationKind, itemReference);
            auditEvidence.save(BookingAuditEvidence.builder()
                    .bookingCode(group.getGroupCode())
                    .locationId(0L)
                    .actorType("SYSTEM")
                    .action(AUDIT_NO_RECIPIENT)
                    .outcome("NO_ACTIVE_MANAGER")
                    .correlationReference(correlationReference)
                    .actionReason(notificationKind + ":" + itemReference)
                    .occurredAt(Instant.now())
                    .build());
            return 0;
        }
        for (String address : addresses) {
            notifications.queue(group, null, notificationKind, address, subject, body, List.of(), null,
                    correlationReference);
        }
        return addresses.size();
    }
}
