package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.FacadeCommandIdempotencyRecord;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.IdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.OutcomeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FacadeCommandIdempotencyRecordRepository extends JpaRepository<FacadeCommandIdempotencyRecord, Long> {

    Optional<FacadeCommandIdempotencyRecord> findByFacadeActionAndOperationReference(String facadeAction, String operationReference);

    @Modifying
    @Query("UPDATE FacadeCommandIdempotencyRecord r SET r.lifecycleStatus = :newStatus, r.outcomeStatus = :outcomeStatus, r.rejectionReason = :rejectionReason, r.responsePayload = :responsePayload, r.completedAt = :completedAt, r.updatedAt = :completedAt WHERE r.id = :id AND r.claimVersion = :expectedVersion")
    int completeClaim(
        @Param("id") Long id,
        @Param("expectedVersion") Long expectedVersion,
        @Param("newStatus") IdempotencyLifecycleStatus newStatus,
        @Param("outcomeStatus") OutcomeStatus outcomeStatus,
        @Param("rejectionReason") String rejectionReason,
        @Param("responsePayload") String responsePayload,
        @Param("completedAt") Instant completedAt
    );

    @Modifying
    @Query("UPDATE FacadeCommandIdempotencyRecord r SET r.claimVersion = r.claimVersion + 1, r.leaseExpiresAt = :newLeaseExpiresAt, r.claimedAt = :claimedAt, r.updatedAt = :claimedAt WHERE r.id = :id AND r.claimVersion = :expectedVersion AND r.lifecycleStatus = :expectedStatus AND r.leaseExpiresAt < :now")
    int reclaimExpiredLease(
        @Param("id") Long id,
        @Param("expectedVersion") Long expectedVersion,
        @Param("expectedStatus") IdempotencyLifecycleStatus expectedStatus,
        @Param("now") Instant now,
        @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
        @Param("claimedAt") Instant claimedAt
    );

    @Modifying
    @Query("DELETE FROM FacadeCommandIdempotencyRecord r WHERE r.lifecycleStatus = :completedStatus AND r.completedAt < :cutoff")
    int deleteCompletedBefore(
        @Param("completedStatus") IdempotencyLifecycleStatus completedStatus,
        @Param("cutoff") Instant cutoff
    );
}
