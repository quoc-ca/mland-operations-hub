package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAbsorbedUndercharge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementAbsorbedUnderchargeRepository extends JpaRepository<SettlementAbsorbedUndercharge, Long> {

    Optional<SettlementAbsorbedUndercharge> findByUnderchargeReference(String underchargeReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SettlementAbsorbedUndercharge u where u.underchargeReference = :reference")
    Optional<SettlementAbsorbedUndercharge> findByUnderchargeReferenceForUpdate(@Param("reference") String reference);

    /** T156: what the ACTIVE records of one ticket already absorb; a voided record frees its amount. */
    @Query("""
            select coalesce(sum(u.amountVnd), 0) from SettlementAbsorbedUndercharge u
            where u.ticketCode = :ticketCode and u.status = 'ACTIVE'""")
    long sumActiveByTicketCode(@Param("ticketCode") String ticketCode);

    /** Branch and period list: records whose recorded time falls in [from, to). */
    @Query("""
            select u from SettlementAbsorbedUndercharge u
            where u.locationReference = :locationId and u.recordedAt >= :from and u.recordedAt < :to
            order by u.recordedAt asc, u.id asc""")
    List<SettlementAbsorbedUndercharge> findForPeriod(@Param("locationId") Long locationId,
                                                      @Param("from") Instant from, @Param("to") Instant to);
}
