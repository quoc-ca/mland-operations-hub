package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementAddonRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementAddonRefundRepository extends JpaRepository<SettlementAddonRefund, Long> {
    Optional<SettlementAddonRefund> findByRefundReference(String refundReference);

    Optional<SettlementAddonRefund> findByCaseReference(String caseReference);

    List<SettlementAddonRefund> findAllByBillReferenceOrderByCreatedAtAsc(String billReference);
}

