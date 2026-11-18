package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperationsAuditEventRepository extends JpaRepository<OperationsAuditEvent, Long> {

    List<OperationsAuditEvent> findBySessionIdOrderByOccurredAtDesc(Long sessionId);

    List<OperationsAuditEvent> findByActorIdOrderByOccurredAtDesc(Long actorId);

    Optional<OperationsAuditEvent> findTopByActionAndCorrelationIdOrderByOccurredAtDesc(String action, String correlationId);

    boolean existsBySessionIdAndAction(Long sessionId, String action);

    /**
     * 003.2 FR-049/FR-050 (T170): one keyset page for the Manager audit review, newest id first. Every filter but
     * {@code from} is optional; {@code beforeId} is the cursor (rows with a smaller id only), so rows written while
     * the Manager pages neither repeat nor go missing. Callers pass {@code PageRequest.of(0, limit + 1)}.
     */
    @Query("""
            select e from OperationsAuditEvent e
            where (:action is null or e.action = :action)
              and (:outcome is null or e.outcome = :outcome)
              and (:actorId is null or e.actorId = :actorId)
              and (:sessionId is null or e.sessionId = :sessionId)
              and e.occurredAt >= :from
              and (:to is null or e.occurredAt <= :to)
              and (:beforeId is null or e.id < :beforeId)
            order by e.id desc
            """)
    List<OperationsAuditEvent> findPage(@Param("action") String action, @Param("outcome") String outcome,
                                        @Param("actorId") Long actorId, @Param("sessionId") Long sessionId,
                                        @Param("from") Instant from, @Param("to") Instant to,
                                        @Param("beforeId") Long beforeId, Pageable pageable);
}
