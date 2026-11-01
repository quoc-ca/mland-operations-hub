package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingPaymentAttemptRepository extends JpaRepository<BookingPaymentAttempt, Long> {

    Optional<BookingPaymentAttempt> findByAttemptReference(String attemptReference);

    Optional<BookingPaymentAttempt> findByMerchantReference(String merchantReference);

    List<BookingPaymentAttempt> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    @Query("select new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.DepositPaymentRecoveryCandidate(" +
            "a.id, a.attemptReference, a.merchantReference, a.attemptAmount, a.providerCreateDate, a.createdAt, a.expiresAt, " +
            "b.bookingCode, b.bookingStatus, b.holdExpiresAt) " +
            "from BookingPaymentAttempt a join a.booking b " +
            "where a.status in :statuses and b.bookingStatus = :bookingStatus and b.holdExpiresAt is not null")
    List<DepositPaymentRecoveryCandidate> findRecoveryCandidates(
            @Param("statuses") List<PaymentAttemptStatus> statuses,
            @Param("bookingStatus") BookingStatus bookingStatus);
}
