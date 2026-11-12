package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefitPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Settlement parts of an accepted retail checkout (Session 2026-11-12, D-011). Read and changed only after the parent
 * benefit row is locked, so no lock of its own is needed.
 */
public interface AcceptedBenefitPartRepository extends JpaRepository<AcceptedBenefitPart, Long> {

    List<AcceptedBenefitPart> findByAcceptedBenefitIdOrderByPartSeqAsc(Long acceptedBenefitId);
}
