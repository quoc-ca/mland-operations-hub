package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundAccountLinkStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAccountLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRefundAccountLinkRepository extends JpaRepository<SettlementRefundAccountLink, Long> {

    Optional<SettlementRefundAccountLink> findByTokenHash(String tokenHash);

    List<SettlementRefundAccountLink> findByRequestIdAndLifecycleStatus(Long requestId,
                                                                        RefundAccountLinkStatus lifecycleStatus);
}
