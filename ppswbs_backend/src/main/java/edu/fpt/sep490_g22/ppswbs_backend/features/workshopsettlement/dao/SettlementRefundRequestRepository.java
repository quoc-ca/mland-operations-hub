package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementRefundRequestRepository extends JpaRepository<SettlementRefundRequest, Long> {

    Optional<SettlementRefundRequest> findByRequestReference(String requestReference);

    /** FR-039: the same money is never asked back twice. */
    Optional<SettlementRefundRequest> findByReasonAndSourceReference(RefundReason reason, String sourceReference);

    List<SettlementRefundRequest> findByBookingReferenceOrderByIdAsc(String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SettlementRefundRequest r where r.requestReference = :requestReference")
    Optional<SettlementRefundRequest> findForUpdate(@Param("requestReference") String requestReference);

    /** Requests still waiting for an account, oldest first, for the day 3 and day 7 reminders. */
    List<SettlementRefundRequest> findByStatusOrderByCreatedAtAsc(RefundRequestStatus status);

    @Query("""
            select coalesce(sum(r.amountVnd), 0) from SettlementRefundRequest r
            where r.bookingReference = :bookingReference
              and r.status <> edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus.CLOSED_UNREACHABLE""")
    long sumRefundedOrPending(@Param("bookingReference") String bookingReference);

    @Query("""
            select r from SettlementRefundRequest r
            where r.status = edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus.PENDING_ACCOUNT
              and r.createdAt <= :threshold order by r.createdAt asc""")
    List<SettlementRefundRequest> findUnansweredSince(@Param("threshold") Instant threshold);
}
