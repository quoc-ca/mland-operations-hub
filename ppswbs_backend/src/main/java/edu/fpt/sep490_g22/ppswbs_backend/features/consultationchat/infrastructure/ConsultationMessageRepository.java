package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationConversation;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationMessage;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationSenderRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {

    List<ConsultationMessage> findByConversationOrderByCreatedAtAsc(ConsultationConversation conversation);

    Optional<ConsultationMessage> findByConversationAndSenderUserIdAndClientMessageReference(
            ConsultationConversation conversation, Long senderUserId, String clientMessageReference
    );

    Optional<ConsultationMessage> findByClientMessageReference(String clientMessageReference);

    long countBySenderUserIdAndCreatedAtAfter(Long senderUserId, Instant after);

    @Query("SELECT COUNT(m) FROM ConsultationMessage m WHERE m.conversation = :conv AND m.senderRole IN :senderRoles AND m.id > :lastReadId")
    int countUnreadMessages(
            @Param("conv") ConsultationConversation conversation,
            @Param("senderRoles") List<ConsultationSenderRole> senderRoles,
            @Param("lastReadId") Long lastReadId
    );

    @Query("SELECT COUNT(m) FROM ConsultationMessage m WHERE m.conversation = :conv AND m.senderRole IN :senderRoles")
    int countAllFromRoles(
            @Param("conv") ConsultationConversation conversation,
            @Param("senderRoles") List<ConsultationSenderRole> senderRoles
    );
}
