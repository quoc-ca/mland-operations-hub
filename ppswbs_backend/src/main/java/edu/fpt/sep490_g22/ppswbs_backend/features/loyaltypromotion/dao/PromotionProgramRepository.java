package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionProgramRepository extends JpaRepository<PromotionProgram, Long> {

    /** Code keys are unique across all history; an expired or paused programme still owns its code. */
    Optional<PromotionProgram> findByCodeKey(String codeKey);

    boolean existsByCodeKey(String codeKey);

    /** The id only: loading the entity here would put a stale, unlocked copy in the persistence context. */
    @Query("select p.id from PromotionProgram p where p.codeKey = :codeKey")
    Optional<Long> findIdByCodeKey(@Param("codeKey") String codeKey);

    /**
     * Management list, newest first by creation time then ID (stable keyset paging). {@code afterCreatedAt} and
     * {@code afterId} come from the opaque cursor of the previous page.
     */
    @Query("""
            select p from PromotionProgram p
            where (:scope is null or p.scope = :scope)
              and (:status is null or p.status = :status)
              and (:afterCreatedAt is null or p.createdAt < :afterCreatedAt
                   or (p.createdAt = :afterCreatedAt and p.id < :afterId))
            order by p.createdAt desc, p.id desc""")
    List<PromotionProgram> findPage(@Param("scope") TargetScope scope, @Param("status") ProgramStatus status,
                                    @Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") Long afterId,
                                    Pageable page);

    /**
     * Public cards: enabled programmes whose current revision is active at {@code at}, newest first, keyset paging.
     * Product programmes without a public product are filtered afterwards from the owning module's facts.
     */
    @Query("""
            select p from PromotionProgram p, PromotionRevision r
            where r.id = p.currentRevisionId
              and p.status = edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus.ENABLED
              and r.startAt <= :at and r.endAt > :at
              and (:scope is null or p.scope = :scope)
              and (:afterCreatedAt is null or p.createdAt < :afterCreatedAt
                   or (p.createdAt = :afterCreatedAt and p.id < :afterId))
            order by p.createdAt desc, p.id desc""")
    List<PromotionProgram> findPublicPage(@Param("scope") TargetScope scope, @Param("at") Instant at,
                                          @Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") Long afterId,
                                          Pageable page);

    /** Exclusive programme locks in ascending ID order (lock order step 4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionProgram p where p.id in :ids order by p.id asc")
    List<PromotionProgram> lockAllByIdIn(@Param("ids") Collection<Long> ids);
}
