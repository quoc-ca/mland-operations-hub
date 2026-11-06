package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingDepositReconciliation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingDepositReconciliationRepository extends JpaRepository<BookingDepositReconciliation, Long> {
    Optional<BookingDepositReconciliation> findByAttemptId(Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookingDepositReconciliation r where r.attempt.id = :attemptId")
    Optional<BookingDepositReconciliation> findLockedByAttemptId(@Param("attemptId") Long attemptId);

    List<BookingDepositReconciliation> findTop100ByLifecycleStatusAndNextQueryAtLessThanEqualOrderByNextQueryAtAsc(
            String lifecycleStatus, Instant now);

    List<BookingDepositReconciliation> findTop100ByLifecycleStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String lifecycleStatus, Instant cutoff);

    @Query("select r from BookingDepositReconciliation r where r.lifecycleStatus = 'UNRESOLVED' " +
            "and r.attempt.expiresAt <= :cutoff order by r.attempt.expiresAt asc")
    List<BookingDepositReconciliation> findDailyUnresolved(@Param("cutoff") Instant cutoff);
}
