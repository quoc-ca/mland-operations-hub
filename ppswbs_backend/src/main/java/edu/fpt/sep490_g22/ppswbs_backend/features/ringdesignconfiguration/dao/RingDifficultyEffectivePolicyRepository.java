package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyEffectivePolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RingDifficultyEffectivePolicyRepository extends JpaRepository<RingDifficultyEffectivePolicy, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM RingDifficultyEffectivePolicy e WHERE e.policyScope = :scope")
    Optional<RingDifficultyEffectivePolicy> findLockedByPolicyScope(@Param("scope") String scope);
}
