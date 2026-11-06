package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonBill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementAddonBillRepository extends JpaRepository<SettlementAddonBill, Long> {
    Optional<SettlementAddonBill> findByQuoteReference(String quoteReference);

    Optional<SettlementAddonBill> findByBillReference(String billReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SettlementAddonBill b where b.billReference = :ref")
    Optional<SettlementAddonBill> findByBillReferenceForUpdate(@Param("ref") String ref);
}
