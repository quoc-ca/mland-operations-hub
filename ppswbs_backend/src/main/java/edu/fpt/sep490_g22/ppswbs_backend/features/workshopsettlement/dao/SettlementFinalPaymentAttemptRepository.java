package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalPaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementFinalPaymentAttemptRepository extends JpaRepository<SettlementFinalPaymentAttempt, Long> {
    Optional<SettlementFinalPaymentAttempt> findByAttemptReference(String attemptReference);
    Optional<SettlementFinalPaymentAttempt> findByOperationReference(String operationReference);
    Optional<SettlementFinalPaymentAttempt> findByMerchantReference(String merchantReference);
    Optional<SettlementFinalPaymentAttempt> findFirstByBookingReferenceOrderByCreatedAtDescIdDesc(String bookingReference);
    List<SettlementFinalPaymentAttempt> findTop100ByPaymentUrlSnapshotIsNotNullAndExpiresAtLessThanEqual(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SettlementFinalPaymentAttempt a where a.attemptReference = :reference")
    Optional<SettlementFinalPaymentAttempt> findByAttemptReferenceForUpdate(@Param("reference") String reference);
}
