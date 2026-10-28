package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Revisions are immutable; only inserts and reads. */
public interface PromotionRevisionRepository extends JpaRepository<PromotionRevision, Long> {

    List<PromotionRevision> findByProgramIdOrderByRevisionNoAsc(Long programId);

    @Query("select coalesce(max(r.revisionNo), 0) from PromotionRevision r where r.programId = :programId")
    long findMaxRevisionNo(@Param("programId") Long programId);
}
