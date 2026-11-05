package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Candidate programmes for a quote (T050): enabled automatic product discounts whose current revision is active at
 * the given time, ordered by creation then ID (the tie-break order). Vouchers are looked up by code instead.
 */
public interface PromotionEligibilityRepository extends JpaRepository<PromotionProgram, Long> {

    @Query("""
            select p from PromotionProgram p, PromotionRevision r
            where r.id = p.currentRevisionId
              and p.kind = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind.AUTO_DISCOUNT
              and p.scope = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope.PRODUCT
              and p.status = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED
              and r.startAt <= :at and r.endAt > :at
            order by p.createdAt asc, p.id asc""")
    List<PromotionProgram> findActiveAutoDiscounts(@Param("at") Instant at);
}
