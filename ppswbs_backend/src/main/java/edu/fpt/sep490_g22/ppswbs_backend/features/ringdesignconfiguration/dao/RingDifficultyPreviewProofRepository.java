package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyPreviewProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RingDifficultyPreviewProofRepository extends JpaRepository<RingDifficultyPreviewProof, Long> {

    Optional<RingDifficultyPreviewProof> findByProofReferenceHash(String proofReferenceHash);

    void deleteAllByExpiresAtBefore(Instant cutoff);
}
