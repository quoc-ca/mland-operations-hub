package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Targets are immutable per revision; only inserts and reads. */
public interface PromotionTargetRepository extends JpaRepository<PromotionTarget, Long> {

    List<PromotionTarget> findByRevisionIdOrderByDimensionAscTargetKeyAsc(Long revisionId);

    List<PromotionTarget> findByRevisionIdIn(Collection<Long> revisionIds);
}
