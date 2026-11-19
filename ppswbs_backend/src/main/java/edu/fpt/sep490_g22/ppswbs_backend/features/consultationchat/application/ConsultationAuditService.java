package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationAuditAction;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationAuditOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure.ConsultationAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationAuditService {

    private final ConsultationAuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(
            Long conversationId,
            Long actorUserId,
            String actorRole,
            ConsultationAuditAction action,
            ConsultationAuditOutcome outcome,
            String contextType,
            Integer messageLength,
            String safeCode,
            String correlationId
    ) {
        try {
            ConsultationAuditEvent event = ConsultationAuditEvent.builder()
                    .conversationId(conversationId)
                    .actorUserId(actorUserId)
                    .actorRole(actorRole)
                    .action(action)
                    .outcome(outcome)
                    .contextType(contextType)
                    .messageLength(messageLength)
                    .safeCode(safeCode)
                    .correlationId(correlationId)
                    .createdAt(Instant.now())
                    .build();

            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record consultation audit event: action={}, actor={}", action, actorUserId, e);
        }
    }
}
