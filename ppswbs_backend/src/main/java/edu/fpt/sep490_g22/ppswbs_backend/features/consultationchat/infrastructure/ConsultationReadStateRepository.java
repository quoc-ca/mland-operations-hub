package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationConversation;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsultationReadStateRepository extends JpaRepository<ConsultationReadState, Long> {

    Optional<ConsultationReadState> findByConversationAndUserId(ConsultationConversation conversation, Long userId);
}
