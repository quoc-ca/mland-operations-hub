package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCheckoutQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RetailCheckoutQuoteRepository extends JpaRepository<RetailCheckoutQuote, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from RetailCheckoutQuote quote where quote.id = :id and quote.memberAccountId = :memberId")
    Optional<RetailCheckoutQuote> lockOwned(@Param("id") String id, @Param("memberId") Long memberId);
}
