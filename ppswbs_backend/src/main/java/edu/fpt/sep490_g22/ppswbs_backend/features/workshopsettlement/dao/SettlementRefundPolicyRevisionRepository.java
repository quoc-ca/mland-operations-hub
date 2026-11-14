package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundPolicyRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementRefundPolicyRevisionRepository extends JpaRepository<SettlementRefundPolicyRevision, Long> {
    List<SettlementRefundPolicyRevision> findAllByOrderByEffectiveAtAsc();
    boolean existsByEffectiveAt(Instant effectiveAt);
    Optional<SettlementRefundPolicyRevision> findByPolicyReference(String policyReference);
    Optional<SettlementRefundPolicyRevision> findTopByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Instant effectiveAt);
}
