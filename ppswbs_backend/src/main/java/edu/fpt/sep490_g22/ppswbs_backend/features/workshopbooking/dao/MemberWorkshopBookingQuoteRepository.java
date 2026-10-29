package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberWorkshopBookingQuoteRepository extends JpaRepository<MemberWorkshopBookingQuote, Long> {
    Optional<MemberWorkshopBookingQuote> findByQuoteRefAndMemberAccountId(String quoteRef, Long memberAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from MemberWorkshopBookingQuote q where q.quoteRef = :quoteRef and q.memberAccountId = :memberId")
    Optional<MemberWorkshopBookingQuote> findOwnedForUpdate(@Param("quoteRef") String quoteRef,
                                                            @Param("memberId") Long memberId);
}
