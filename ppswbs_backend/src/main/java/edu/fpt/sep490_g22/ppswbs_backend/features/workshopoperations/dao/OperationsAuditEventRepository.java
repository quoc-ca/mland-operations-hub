package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.OperationsAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperationsAuditEventRepository extends JpaRepository<OperationsAuditEvent, Long> {

    List<OperationsAuditEvent> findBySessionIdOrderByOccurredAtDesc(Long sessionId);

    List<OperationsAuditEvent> findByActorIdOrderByOccurredAtDesc(Long actorId);

    Optional<OperationsAuditEvent> findTopByActionAndCorrelationIdOrderByOccurredAtDesc(String action, String correlationId);
}
