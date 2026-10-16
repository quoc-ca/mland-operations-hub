package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopSessionRepository extends JpaRepository<WorkshopSession, Long> {

    List<WorkshopSession> findByPackageIdAndStatus(Long packageId, String status);

    /**
     * Pessimistic write lock for capacity reservation.
     * Must be called within a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkshopSession s WHERE s.id = :id")
    Optional<WorkshopSession> findByIdWithLock(@Param("id") Long id);
}
