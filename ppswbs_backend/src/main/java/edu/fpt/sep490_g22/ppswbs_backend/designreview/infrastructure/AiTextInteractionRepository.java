package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiTextInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AiTextInteractionRepository extends JpaRepository<AiTextInteraction, Long> {

    List<AiTextInteraction> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Query("SELECT a FROM AiTextInteraction a WHERE a.memberId = :memberId AND a.expiresAt > :now ORDER BY a.createdAt DESC")
    List<AiTextInteraction> findActiveByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AiTextInteraction a WHERE a.expiresAt <= :now")
    int deleteExpiredInteractions(@Param("now") Instant now);
}
