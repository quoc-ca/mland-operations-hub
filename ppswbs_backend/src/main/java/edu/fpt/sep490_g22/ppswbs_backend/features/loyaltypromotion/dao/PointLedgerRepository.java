package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Append-only: only inserts and reads. */
public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    Optional<PointLedger> findByAcceptedBenefitIdAndEntryKind(Long acceptedBenefitId, LedgerEntryKind entryKind);

    List<PointLedger> findByAcceptedBenefitIdOrderByIdAsc(Long acceptedBenefitId);

    /** One Member's history, newest first by time then ID (stable keyset paging after the cursor). */
    @Query("""
            select l from PointLedger l
            where l.memberId = :memberId
              and (:afterOccurredAt is null or l.occurredAt < :afterOccurredAt
                   or (l.occurredAt = :afterOccurredAt and l.id < :afterId))
            order by l.occurredAt desc, l.id desc""")
    List<PointLedger> findMemberPage(@Param("memberId") Long memberId, @Param("afterOccurredAt") Instant afterOccurredAt,
                                     @Param("afterId") Long afterId, Pageable page);
}
