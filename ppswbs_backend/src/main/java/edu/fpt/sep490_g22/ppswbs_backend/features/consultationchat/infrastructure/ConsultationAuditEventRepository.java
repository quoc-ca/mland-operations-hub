package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationAuditEventRepository extends JpaRepository<ConsultationAuditEvent, Long> {

    List<ConsultationAuditEvent> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
