package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyOptionScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RingDifficultyOptionScoreRepository extends JpaRepository<RingDifficultyOptionScore, Long> {

    List<RingDifficultyOptionScore> findAllByPolicyVersionId(Long policyVersionId);

    List<RingDifficultyOptionScore> findAllByPolicyVersionIdIn(List<Long> policyVersionIds);
}
