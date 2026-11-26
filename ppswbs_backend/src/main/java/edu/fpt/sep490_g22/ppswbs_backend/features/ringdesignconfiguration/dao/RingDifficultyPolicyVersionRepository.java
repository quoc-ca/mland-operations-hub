package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyPolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RingDifficultyPolicyVersionRepository extends JpaRepository<RingDifficultyPolicyVersion, Long> {

    Optional<RingDifficultyPolicyVersion> findByRulesVersion(String rulesVersion);

    List<RingDifficultyPolicyVersion> findAllByOrderByPublishedAtDescIdDesc();
}
