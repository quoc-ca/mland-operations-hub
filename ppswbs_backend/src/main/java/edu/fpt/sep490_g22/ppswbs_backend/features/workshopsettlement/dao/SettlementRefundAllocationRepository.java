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

    List<SettlementRefundAllocation> findAllByCaseReferenceOrderByCreatedAtAsc(String caseReference);

    List<SettlementRefundAllocation> findTop100ByLifecycleStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String lifecycleStatus, Instant cutoff);

    @Query("select a from SettlementRefundAllocation a where a.lifecycleStatus in :statuses "
            + "and a.providerRefundTransactionNumber is not null "
            + "and coalesce(a.providerAcceptedAt, a.createdAt) >= :acceptanceCutoff "
            + "and (a.lastQueryAt is null or a.lastQueryAt <= :dueBefore) "
            + "order by a.lastQueryAt asc nulls first")
    List<SettlementRefundAllocation> findDueRefundQueries(@Param("statuses") List<String> lifecycleStatuses,
            @Param("acceptanceCutoff") Instant acceptanceCutoff, @Param("dueBefore") Instant dueBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SettlementRefundAllocation a where a.allocationReference = :reference")
    Optional<SettlementRefundAllocation> findByAllocationReferenceForUpdate(@Param("reference") String reference);
}
