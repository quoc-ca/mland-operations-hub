package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementAddonBillRepository extends JpaRepository<SettlementAddonBill, Long> {
    Optional<SettlementAddonBill> findByQuoteReference(String quoteReference);
}
