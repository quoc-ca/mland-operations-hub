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
import java.time.Instant;
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
    @Query("SELECT s FROM WorkshopSession s WHERE s.id = :id")
    Optional<WorkshopSession> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkshopSession s WHERE s.reference IN :references")
    List<WorkshopSession> findByReferenceInWithLock(@Param("references") Collection<String> references);

    boolean existsByLocationIdAndWorkshopDateAndSlotId(Long locationId, LocalDate workshopDate, Long slotId);

    /** T135: true while a session of the location is in a state outside {@code excludedStates}. */
    boolean existsByLocationIdAndStateNotIn(Long locationId, Collection<SessionState> excludedStates);

    List<WorkshopSession> findByWorkshopDateGreaterThanEqualAndStateIn(LocalDate from, Collection<SessionState> states);

    List<WorkshopSession> findByLocationIdAndWorkshopDateBetween(Long locationId, LocalDate from, LocalDate to);

    List<WorkshopSession> findByLocationIdAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
            Long locationId, SessionState state, LocalDate from);

    List<WorkshopSession> findByLocationIdAndStateAndWorkshopDateBetweenOrderByScheduledStartAtAsc(
            Long locationId, SessionState state, LocalDate from, LocalDate to);

    /** 003.2 FR-042/FR-046: sessions at any of the given (sellable) locations, nearest first. */
    List<WorkshopSession> findByLocationIdInAndStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
            Collection<Long> locationIds, SessionState state, LocalDate from);

    List<WorkshopSession> findByStateAndWorkshopDateGreaterThanEqualOrderByScheduledStartAtAsc(
            SessionState state, LocalDate from);

    List<WorkshopSession> findByLocationIdAndState(Long locationId, SessionState state);

    List<WorkshopSession> findByStateAndScheduledStartAtLessThanEqual(SessionState state, Instant cutoff);

    List<WorkshopSession> findByStateInAndScheduledStartAtLessThanEqual(Collection<SessionState> states, Instant cutoff);

    List<WorkshopSession> findByStateAndScheduledEndAtLessThanEqual(SessionState state, Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM WorkshopSession s WHERE s.id IN :ids ORDER BY s.id")
    List<WorkshopSession> findByIdsWithLock(@Param("ids") Collection<Long> ids);

    /** Manager home (013 R-1): sessions of one day, every state, optionally one location. */
    @Query("SELECT COUNT(s) FROM WorkshopSession s WHERE s.workshopDate = :day AND (:locationId IS NULL OR s.location.id = :locationId)")
    long countOfDay(@Param("day") LocalDate day, @Param("locationId") Long locationId);

    /** Manager home (013 R-1): sessions of one day in one state, optionally one location. */
    @Query("SELECT COUNT(s) FROM WorkshopSession s WHERE s.workshopDate = :day AND s.state = :state AND (:locationId IS NULL OR s.location.id = :locationId)")
    long countOfDayInState(@Param("day") LocalDate day, @Param("state") SessionState state, @Param("locationId") Long locationId);
}
