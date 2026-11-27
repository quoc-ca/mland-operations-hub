package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashReversal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementCashReversalRepository extends JpaRepository<SettlementCashReversal, Long> {

    Optional<SettlementCashReversal> findByReversalReference(String reversalReference);

    Optional<SettlementCashReversal> findByCollectionId(Long collectionId);

    boolean existsByCollectionId(Long collectionId);

    /** Negative sum of the reversals of one folio; zero when there are none. */
    @Query("select coalesce(sum(r.amountVnd), 0) from SettlementCashReversal r where r.billReference = :billReference")
    long sumByBillReference(@Param("billReference") String billReference);

    /** FR-036b: the rows {@link #sumForBranchDay} adds up. */
    List<SettlementCashReversal> findByBranchIdAndCashBusinessDay(Long branchId, LocalDate cashBusinessDay);

    /** Negative sum of the reversals counting on one branch working day (FR-036 expected cash). */
    @Query("""
            select coalesce(sum(r.amountVnd), 0) from SettlementCashReversal r
            where r.branchId = :branchId and r.cashBusinessDay = :businessDay""")
    long sumForBranchDay(@Param("branchId") Long branchId, @Param("businessDay") LocalDate businessDay);
}
