package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtaReconciliationDecisionRepository extends JpaRepository<OtaReconciliationDecision, Long> {
    Optional<OtaReconciliationDecision> findByActorAccountReferenceAndIdempotencyKeyHash(
            String actorAccountReference, String idempotencyKeyHash);

    Optional<OtaReconciliationDecision> findTopByWorkItem_IdOrderByRecordedAtDescIdDesc(Long workItemId);

    Optional<OtaReconciliationDecision> findTopByWorkItem_IdAndActionCodeOrderByRecordedAtDescIdDesc(
            Long workItemId, String actionCode);

    Optional<OtaReconciliationDecision> findTopByMapping_IdAndActionCodeOrderByRecordedAtDescIdDesc(
            Long mappingId, String actionCode);
}
