package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingBuyerVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingBuyerVerificationRepository extends JpaRepository<BookingBuyerVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from BookingBuyerVerification v where v.verificationReference = :reference")
    Optional<BookingBuyerVerification> findByVerificationReferenceForUpdate(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from BookingBuyerVerification v where v.buyerVerificationReference = :reference")
    Optional<BookingBuyerVerification> findByBuyerVerificationReferenceForUpdate(@Param("reference") String reference);

    @Query("select v from BookingBuyerVerification v where v.group.id = :groupId and v.createdAt > :since order by v.createdAt desc")
    List<BookingBuyerVerification> findRecentByGroup(@Param("groupId") Long groupId, @Param("since") Instant since);

    @Query("select v from BookingBuyerVerification v where v.group.id = :groupId and v.status = :status")
    List<BookingBuyerVerification> findByGroupAndStatus(@Param("groupId") Long groupId, @Param("status") String status);
}
