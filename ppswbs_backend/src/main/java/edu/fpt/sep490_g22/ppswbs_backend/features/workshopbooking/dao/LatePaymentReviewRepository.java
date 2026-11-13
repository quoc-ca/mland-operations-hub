package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.LatePaymentReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LatePaymentReviewRepository extends JpaRepository<LatePaymentReview, Long> {

    Optional<LatePaymentReview> findByVerifiedEvidenceReference(String verifiedEvidenceReference);

    Optional<LatePaymentReview> findByReviewReference(String reviewReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LatePaymentReview r where r.reviewReference = :reference")
    Optional<LatePaymentReview> findByReviewReferenceForUpdate(@Param("reference") String reference);

    /** FR-040a: the Manager queue, earliest session first (sessions within 24 hours come first). */
    @Query("select r from LatePaymentReview r join fetch r.group where r.outcome = :outcome "
            + "order by case when r.earliestSessionAt is null then 1 else 0 end, r.earliestSessionAt, r.resultDueAt, r.id")
    List<LatePaymentReview> findQueue(@Param("outcome") String outcome);
}
