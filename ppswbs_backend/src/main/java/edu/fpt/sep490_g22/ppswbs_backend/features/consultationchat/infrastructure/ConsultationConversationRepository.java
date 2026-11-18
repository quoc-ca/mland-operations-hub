package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationConversation;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationConversationRepository extends JpaRepository<ConsultationConversation, Long> {

    Optional<ConsultationConversation> findByConversationReference(String conversationReference);

    List<ConsultationConversation> findByMemberUserIdOrderByUpdatedAtDesc(Long memberUserId);

    List<ConsultationConversation> findByMemberUserIdAndStatusOrderByUpdatedAtDesc(
            Long memberUserId, ConsultationStatus status
    );

    @Query("SELECT c FROM ConsultationConversation c WHERE (c.assignedLocationId IN :locationIds) ORDER BY c.updatedAt DESC")
    List<ConsultationConversation> findByAssignedLocationIdInOrderByUpdatedAtDesc(
            @Param("locationIds") List<Long> locationIds
    );

    @Query("SELECT c FROM ConsultationConversation c WHERE (c.assignedLocationId IN :locationIds) AND c.status = :status ORDER BY c.updatedAt DESC")
    List<ConsultationConversation> findByAssignedLocationIdInAndStatusOrderByUpdatedAtDesc(
            @Param("locationIds") List<Long> locationIds,
            @Param("status") ConsultationStatus status
    );

    List<ConsultationConversation> findAllByOrderByUpdatedAtDesc();

    List<ConsultationConversation> findByStatusOrderByUpdatedAtDesc(ConsultationStatus status);
}
