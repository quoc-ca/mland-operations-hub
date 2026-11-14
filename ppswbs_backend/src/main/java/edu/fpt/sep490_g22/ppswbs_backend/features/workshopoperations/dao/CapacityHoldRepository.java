package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHold;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CapacityHoldState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CapacityHoldRepository extends JpaRepository<CapacityHold, Long> {

    Optional<CapacityHold> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ch FROM CapacityHold ch WHERE ch.reference IN :references")
    List<CapacityHold> findByReferenceInWithLock(@Param("references") Collection<String> references);

    List<CapacityHold> findByBookingReference(String bookingReference);

    /**
     * The confirmed holds of one booking line in one session (003.2 FR-031): a per-ticket release decrements
     * the line's committed seat count there, so the seat is given back even though the line keeps its hold.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ch FROM CapacityHold ch WHERE ch.bookingReference = :bookingReference "
            + "AND ch.session.id = :sessionId AND ch.state = 'CONFIRMED' ORDER BY ch.id")
    List<CapacityHold> findConfirmedForBookingAndSessionWithLock(@Param("bookingReference") String bookingReference,
                                                                 @Param("sessionId") Long sessionId);

    List<CapacityHold> findBySessionIdAndState(Long sessionId, CapacityHoldState state);

    @Query("SELECT COALESCE(SUM(ch.requestedTicketCount), 0) FROM CapacityHold ch WHERE ch.session.id = :sessionId AND ch.state = 'ACTIVE' AND ch.expiresAt > :now")
    int sumActiveHoldsForSession(@Param("sessionId") Long sessionId, @Param("now") Instant now);

    @Query("SELECT COALESCE(SUM(ch.requestedTicketCount), 0) FROM CapacityHold ch WHERE ch.session.id = :sessionId AND ch.state = 'CONFIRMED'")
    int sumConfirmedHoldsForSession(@Param("sessionId") Long sessionId);

    /**
     * Locking read of a session's active and confirmed holds. Under REPEATABLE READ a plain count may come from the
     * transaction's earlier snapshot; a locking read always sees the latest committed rows (003.2 FR-030 race).
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT ch FROM CapacityHold ch WHERE ch.session.id = :sessionId AND ch.state IN ('ACTIVE', 'CONFIRMED')")
    List<CapacityHold> findCountableHoldsForSessionForShare(@Param("sessionId") Long sessionId);

    @Query("SELECT ch FROM CapacityHold ch WHERE ch.state = 'ACTIVE' AND ch.expiresAt <= :now")
    List<CapacityHold> findExpiredActiveHolds(@Param("now") Instant now);
}

