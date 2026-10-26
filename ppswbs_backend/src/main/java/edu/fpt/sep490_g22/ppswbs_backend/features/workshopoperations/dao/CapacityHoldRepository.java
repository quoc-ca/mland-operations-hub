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

    List<CapacityHold> findBySessionIdAndState(Long sessionId, CapacityHoldState state);

    @Query("SELECT COALESCE(SUM(ch.requestedTicketCount), 0) FROM CapacityHold ch WHERE ch.session.id = :sessionId AND ch.state = 'ACTIVE' AND ch.expiresAt > :now")
    int sumActiveHoldsForSession(@Param("sessionId") Long sessionId, @Param("now") Instant now);
}
