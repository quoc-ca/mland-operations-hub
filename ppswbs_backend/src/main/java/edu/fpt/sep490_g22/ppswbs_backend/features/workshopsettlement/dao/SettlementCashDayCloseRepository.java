package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCashDayClose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SettlementCashDayCloseRepository extends JpaRepository<SettlementCashDayClose, Long> {

    Optional<SettlementCashDayClose> findByBranchIdAndBusinessDay(Long branchId, LocalDate businessDay);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SettlementCashDayClose c where c.branchId = :branchId and c.businessDay = :businessDay")
    Optional<SettlementCashDayClose> findForUpdate(@Param("branchId") Long branchId,
                                                   @Param("businessDay") LocalDate businessDay);
}
