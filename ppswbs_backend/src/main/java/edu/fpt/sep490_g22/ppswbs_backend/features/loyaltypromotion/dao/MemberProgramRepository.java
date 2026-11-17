package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberProgramRepository extends JpaRepository<MemberProgram, Long> {

    Optional<MemberProgram> findByProgramIdAndMemberId(Long programId, Long memberId);

    /** Exclusive lock on one Member's counters (lock order step 5, after the wallet). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberProgram m where m.programId = :programId and m.memberId = :memberId")
    Optional<MemberProgram> lockByProgramIdAndMemberId(@Param("programId") Long programId,
                                                       @Param("memberId") Long memberId);

    /** One Member's saved vouchers, newest save first then programme ID (stable keyset paging). */
    @Query("""
            select m from MemberProgram m
            where m.memberId = :memberId and m.savedAt is not null
              and (:afterSavedAt is null or m.savedAt < :afterSavedAt
                   or (m.savedAt = :afterSavedAt and m.programId < :afterProgramId))
            order by m.savedAt desc, m.programId desc""")
    java.util.List<MemberProgram> findSavedPage(@Param("memberId") Long memberId,
                                                @Param("afterSavedAt") java.time.Instant afterSavedAt,
                                                @Param("afterProgramId") Long afterProgramId,
                                                org.springframework.data.domain.Pageable page);

    /** Highest held + consumed of any Member, read under the programme lock before lowering the member limit. */
    @Query("select coalesce(max(m.heldCount + m.consumedCount), 0) from MemberProgram m where m.programId = :programId")
    long findMaxMemberUsage(@Param("programId") Long programId);
}
