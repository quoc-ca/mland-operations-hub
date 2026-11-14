package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface BookingIdempotencyRecordRepository extends JpaRepository<BookingIdempotencyRecord, Long> {

    Optional<BookingIdempotencyRecord> findByIdempotencyKeyAndCommandAction(String idempotencyKey, String commandAction);

    @Modifying
    @Query("UPDATE BookingIdempotencyRecord r " +
           "SET r.lifecycleStatus = :newStatus, " +
           "    r.completedAt = :completedAt, " +
           "    r.statusCode = :statusCode, " +
           "    r.responsePayload = :responsePayload, " +
           "    r.updatedAt = :now " +
           "WHERE r.id = :id AND r.claimVersion = :expectedVersion")
    int completeClaim(@Param("id") Long id,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("newStatus") BookingIdempotencyLifecycleStatus newStatus,
                      @Param("completedAt") Instant completedAt,
                      @Param("statusCode") Integer statusCode,
                      @Param("responsePayload") String responsePayload,
                      @Param("now") Instant now);

    @Modifying
    @Query("UPDATE BookingIdempotencyRecord r " +
           "SET r.claimVersion = r.claimVersion + 1, " +
           "    r.claimedAt = :now, " +
           "    r.leaseExpiresAt = :newLeaseExpiresAt, " +
           "    r.correlationReference = :newCorrelationReference, " +
           "    r.updatedAt = :now " +
           "WHERE r.id = :id AND r.claimVersion = :expectedVersion")
    int reclaimExpiredLease(@Param("id") Long id,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("newLeaseExpiresAt") Instant newLeaseExpiresAt,
                            @Param("newCorrelationReference") String newCorrelationReference,
                            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM BookingIdempotencyRecord r " +
           "WHERE r.lifecycleStatus = 'COMPLETED' AND r.completedAt < :cutoff")
    int deleteCompletedBefore(@Param("cutoff") Instant cutoff);
}
