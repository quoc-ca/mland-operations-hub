package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopCartQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkshopCartQuoteRepository extends JpaRepository<WorkshopCartQuote, Long> {

    Optional<WorkshopCartQuote> findByQuoteRef(String quoteRef);

    /** Locks the quote so two concurrent group creations cannot both accept it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from WorkshopCartQuote q where q.quoteRef = :quoteRef")
    Optional<WorkshopCartQuote> findForUpdate(@Param("quoteRef") String quoteRef);
}
