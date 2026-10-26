package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopSessionRepository extends JpaRepository<WorkshopSession, Long> {

    Optional<WorkshopSession> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkshopSession s WHERE s.reference = :reference")
    Optional<WorkshopSession> findByReferenceWithLock(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkshopSession s WHERE s.reference IN :references")
    List<WorkshopSession> findByReferenceInWithLock(@Param("references") Collection<String> references);

    boolean existsByLocationIdAndWorkshopDateAndSlotId(Long locationId, LocalDate workshopDate, Long slotId);

    List<WorkshopSession> findByLocationIdAndWorkshopDateBetween(Long locationId, LocalDate from, LocalDate to);

    List<WorkshopSession> findByLocationIdAndState(Long locationId, SessionState state);
}
