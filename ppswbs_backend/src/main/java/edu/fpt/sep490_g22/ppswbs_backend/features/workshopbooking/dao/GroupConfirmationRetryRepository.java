package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupConfirmationRetry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GroupConfirmationRetryRepository extends JpaRepository<GroupConfirmationRetry, Long> {

    Optional<GroupConfirmationRetry> findByVerifiedEvidenceReference(String verifiedEvidenceReference);

    Optional<GroupConfirmationRetry> findFirstByGroupIdAndRetryStatus(Long groupId,
                                                                      GroupConfirmationRetry.Status retryStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GroupConfirmationRetry r where r.id = :id")
    Optional<GroupConfirmationRetry> findByIdForUpdate(@Param("id") Long id);

    /** WAITING rows whose next retry is due, oldest first. */
    @Query("select r.id from GroupConfirmationRetry r where r.retryStatus = :status "
            + "and (r.nextRetryAt is null or r.nextRetryAt <= :now) order by r.createdAt asc")
    List<Long> findDueIds(@Param("status") GroupConfirmationRetry.Status status, @Param("now") Instant now,
                          org.springframework.data.domain.Pageable pageable);
}
