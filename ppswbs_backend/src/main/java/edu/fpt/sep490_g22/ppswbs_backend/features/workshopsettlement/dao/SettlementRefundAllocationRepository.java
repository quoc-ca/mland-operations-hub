package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementRefundAllocationRepository extends JpaRepository<SettlementRefundAllocation, Long> {
    Optional<SettlementRefundAllocation> findByAllocationReference(String allocationReference);

    List<SettlementRefundAllocation> findAllByOriginalPaymentReference(String originalPaymentReference);

    List<SettlementRefundAllocation> findAllByCaseReferenceOrderByCreatedAtAsc(String caseReference);

    List<SettlementRefundAllocation> findTop100ByLifecycleStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String lifecycleStatus, Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SettlementRefundAllocation a where a.allocationReference = :reference")
    Optional<SettlementRefundAllocation> findByAllocationReferenceForUpdate(@Param("reference") String reference);
}
