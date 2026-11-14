package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * The same rows read with a shared lock (FOR SHARE) for an accept. A locking read sees the latest committed
     * status and revision even when the owner's transaction already holds an older REPEATABLE READ snapshot, so an
     * automatic discount paused or edited just before the accept is never applied from that snapshot (review
     * 2026-11-10, L4). Taken only through LoyaltyLockCoordinator, at the programme step.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select p from PromotionProgram p, PromotionRevision r
            where r.id = p.currentRevisionId
              and p.kind = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind.AUTO_DISCOUNT
              and p.scope = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope.PRODUCT
              and p.status = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED
              and r.startAt <= :at and r.endAt > :at
            order by p.createdAt asc, p.id asc""")
    List<PromotionProgram> lockActiveAutoDiscounts(@Param("at") Instant at);

    /**
     * The same two reads for workshop automatic discounts (Session 2026-11-14, FR-044). They are a separate scope,
     * so a workshop booking never sees a product programme and the retail reads keep their own plan.
     */
    @Query("""
            select p from PromotionProgram p, PromotionRevision r
            where r.id = p.currentRevisionId
              and p.kind = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind.AUTO_DISCOUNT
              and p.scope = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope.WORKSHOP
              and p.status = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED
              and r.startAt <= :at and r.endAt > :at
            order by p.createdAt asc, p.id asc""")
    List<PromotionProgram> findActiveWorkshopAutoDiscounts(@Param("at") Instant at);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select p from PromotionProgram p, PromotionRevision r
            where r.id = p.currentRevisionId
              and p.kind = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind.AUTO_DISCOUNT
              and p.scope = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope.WORKSHOP
              and p.status = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED
              and r.startAt <= :at and r.endAt > :at
            order by p.createdAt asc, p.id asc""")
    List<PromotionProgram> lockActiveWorkshopAutoDiscounts(@Param("at") Instant at);
}
