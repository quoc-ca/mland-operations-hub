package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundLinkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SettlementRefundLinkSubmissionRepository extends JpaRepository<SettlementRefundLinkSubmission, Long> {

    Optional<SettlementRefundLinkSubmission> findByTokenHashAndIdempotencyKey(String tokenHash, String idempotencyKey);

    @Modifying
    @Query("delete from SettlementRefundLinkSubmission s where s.retainUntil <= :now")
    int deleteRetainedBefore(@Param("now") Instant now);
}
