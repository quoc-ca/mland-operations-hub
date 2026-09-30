package edu.fpt.sep490_g22.ppswbs_backend.members.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.common.AuditEventWriter;
import edu.fpt.sep490_g22.ppswbs_backend.members.domain.MemberAuthAuditEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaAuditEventWriter implements AuditEventWriter {

    private final MemberAuthAuditEventRepository auditEventRepository;

    @Override
    public void writeEvent(AuditEvent event) {
        MemberAuthAuditEventEntity entity = MemberAuthAuditEventEntity.builder()
                .memberId(event.getMemberId())
                .eventType(event.getEventType())
                .occurredAt(event.getOccurredAt())
                .correlationId(event.getCorrelationId())
                .provider(event.getProvider())
                .safeMetadata(event.getSafeMetadata())
                .build();
        auditEventRepository.save(entity);
    }
}
