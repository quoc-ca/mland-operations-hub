package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupPaymentAttemptRepository extends JpaRepository<GroupPaymentAttempt, Long> {

    List<GroupPaymentAttempt> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<GroupPaymentAttempt> findByMerchantReference(String merchantReference);

    /** FR-032 "One live attempt": a live attempt is `INITIATED` or `PENDING`. */
    /**
     * FR-032 "One live attempt" (T169): a locking read, so that after the group row lock a request sees the attempt a
     * concurrent request committed (MySQL repeatable read would otherwise show the older snapshot).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from GroupPaymentAttempt a where a.group.id = :groupId "
            + "and a.attemptStatus in :statuses order by a.createdAt desc")
    List<GroupPaymentAttempt> lockByGroupIdAndStatuses(
            @org.springframework.data.repository.query.Param("groupId") Long groupId,
            @org.springframework.data.repository.query.Param("statuses")
            java.util.Collection<edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus> statuses);

    boolean existsByGroupIdAndAttemptStatusIn(Long groupId, java.util.Collection<edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus> statuses);

    /** Attempts of groups that are still awaiting their single payment, for the bounded QueryDR checks. */
    @org.springframework.data.jpa.repository.Query("""
            select new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.GroupPaymentRecoveryCandidate(
                a.id, a.attemptReference, a.merchantReference, a.amountVnd, a.providerCreateDate, a.expiresAt,
                g.groupCode, g.groupStatus, g.holdExpiresAt, g.paymentDeadlineAt)
            from GroupPaymentAttempt a join a.group g
            where a.attemptStatus in :statuses and g.groupStatus = :groupStatus
              and g.holdExpiresAt is not null and g.paymentDeadlineAt is not null""")
    List<GroupPaymentRecoveryCandidate> findRecoveryCandidates(
            @org.springframework.data.repository.query.Param("statuses")
            List<edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus> statuses,
            @org.springframework.data.repository.query.Param("groupStatus")
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus groupStatus);

    /**
     * Payments of one location inside one half-open instant range, for the booking-time payment list (T150).
     * Guard (FR-054, Q5, T164): the location is matched on the booking-time copy
     * {@code BookingGroupLineSession.locationReference} only (where the money was collected); never join Operations'
     * current sessions here, so a later ticket move does not move a paid row to another branch.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a from GroupPaymentAttempt a join fetch a.group g
            where a.attemptStatus in :statuses
              and exists (select 1 from BookingGroupLineSession s where s.line.group = g
                          and s.locationReference = :locationId)
              and not exists (select 1 from GroupExcessPayment x where x.merchantReference = a.merchantReference)
              and ((a.attemptStatus = :success and g.groupStatus = :confirmed
                    and g.confirmedAt >= :fromInstant and g.confirmedAt < :toInstant)
                or (not (a.attemptStatus = :success and g.groupStatus = :confirmed)
                    and a.createdAt >= :fromInstant and a.createdAt < :toInstant))
            order by a.createdAt, a.id""")
    List<GroupPaymentAttempt> findBookingTimePayments(
            @org.springframework.data.repository.query.Param("locationId") Long locationId,
            @org.springframework.data.repository.query.Param("statuses")
            List<edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus> statuses,
            @org.springframework.data.repository.query.Param("success")
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus success,
            @org.springframework.data.repository.query.Param("confirmed")
            edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus confirmed,
            @org.springframework.data.repository.query.Param("fromInstant") java.time.Instant fromInstant,
            @org.springframework.data.repository.query.Param("toInstant") java.time.Instant toInstant);
}
