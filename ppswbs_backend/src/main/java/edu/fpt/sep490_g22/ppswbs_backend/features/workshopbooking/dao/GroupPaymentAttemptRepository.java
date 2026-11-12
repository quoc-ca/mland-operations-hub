package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.GroupPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupPaymentAttemptRepository extends JpaRepository<GroupPaymentAttempt, Long> {

    List<GroupPaymentAttempt> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<GroupPaymentAttempt> findByMerchantReference(String merchantReference);

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
}
