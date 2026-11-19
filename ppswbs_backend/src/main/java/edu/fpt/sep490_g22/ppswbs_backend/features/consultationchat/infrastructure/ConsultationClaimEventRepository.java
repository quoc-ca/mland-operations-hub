package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationClaimEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationClaimEventRepository extends JpaRepository<ConsultationClaimEvent, Long> {

    List<ConsultationClaimEvent> findByConversationOrderByCreatedAtAsc(ConsultationConversation conversation);
}
