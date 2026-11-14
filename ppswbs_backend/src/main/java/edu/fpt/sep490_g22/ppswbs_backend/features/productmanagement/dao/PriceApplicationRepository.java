package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceApplicationRepository extends JpaRepository<PriceApplication, Long> {

    Optional<PriceApplication> findByProposalId(Long proposalId);

    List<PriceApplication> findByVariantIdOrderByAppliedSequenceAsc(Long variantId);
}
